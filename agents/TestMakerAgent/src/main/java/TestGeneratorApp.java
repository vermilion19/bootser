import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class TestGeneratorApp {

    // API 키: 환경 변수에서 로드
    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");

    // 모델명: 1.5 Flash (가성비/속도 최적). 404 에러시 자가 진단 로직 작동함.
    private static final String MODEL_NAME = "gemini-3-flash-preview";

    // 패키지 경로: 프로젝트 구조에 맞게 수정 가능
    private static final String FIXED_PACKAGE_PATH = "src/main/java/com/booster";

    // 모듈 루트 경로 (DTO 파일을 찾기 위해 사용)
    private static Path MODULE_ROOT_PATH;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        forceUtf8Console(); // 한글 깨짐 방지

        // 1. API 키 검증
        if (GEMINI_API_KEY == null || GEMINI_API_KEY.isEmpty()) {
            System.err.println("[오류] 'GEMINI_API_KEY' 환경 변수가 없습니다.");
            System.err.println("Run Configuration -> Environment variables에 추가해주세요.");
            return;
        }

        // 2. 인자 검증
        if (args.length == 0) {
            System.err.println("[사용법] [모듈경로] (선택:클래스명)");
            System.err.println("예시: apps/order-service OrderController");
            return;
        }

        String modulePathStr = args[0];
        String specificFileName = (args.length > 1) ? args[1] : null;

        // 모듈 루트 저장 (나중에 DTO 찾을 때 씀)
        MODULE_ROOT_PATH = Paths.get(modulePathStr);
        Path scanStartPath = MODULE_ROOT_PATH.resolve(FIXED_PACKAGE_PATH);

        if (!Files.exists(scanStartPath)) {
            System.err.println("경로를 찾을 수 없습니다: " + scanStartPath.toAbsolutePath());
            return;
        }

        System.out.println("==========================================");
        System.out.println("AI 테스트 에이전트 가동 (DTO 참조 기능 탑재)");
        System.out.println("대상 모듈: " + modulePathStr);
        System.out.println("모델: " + MODEL_NAME);
        if (specificFileName != null) System.out.println("타겟 모드: Only '" + specificFileName + "'");
        System.out.println("==========================================\n");

        // 3. 파일 탐색 및 처리
        try (Stream<Path> paths = Files.walk(scanStartPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().endsWith("Application.java")) // 메인 클래스 제외
                    .filter(p -> {
                        // 특정 파일만 처리하는 로직
                        if (specificFileName == null) return true;
                        String fileName = p.getFileName().toString();
                        return fileName.equals(specificFileName) || fileName.equals(specificFileName + ".java");
                    })
                    .forEach(TestGeneratorApp::processFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\n모든 작업이 완료되었습니다.");
    }

    private static void processFile(Path sourcePath) {
        try {
            Path testPath = resolveTestPath(sourcePath);

            // 이미 테스트가 있으면 건너뜀
            if (Files.exists(testPath)) {
                System.out.println("⏭[SKIP] " + sourcePath.getFileName());
                return;
            }

            System.out.print("[GENERATE] " + sourcePath.getFileName() + " (문맥 분석 중...) ");

            String sourceCode = Files.readString(sourcePath);

            // 핵심 기능] 관련된 DTO/Request/Response 코드를 긁어옴 (RAG Lite)
            String relatedCode = collectRelatedCode(sourceCode);

            // 프롬프트 구성: 타겟 코드 + 참조 코드
            String fullContext = "Target Code:\n" + sourceCode + "\n\n" +
                    "Reference Context (DTOs/VOs):\n" + relatedCode;

            // AI 호출
            String generatedCode = callGeminiApi(fullContext);

            saveTestFile(testPath, generatedCode);
            System.out.println("DONE");

            // Rate Limit 방지
            Thread.sleep(1000);

        } catch (Exception e) {
            System.out.println("FAIL");

            // 에러 내용을 출력하되, 프로그램을 죽이지 않음 (Gradle 에러 방지)
            System.err.println("   └─ 이유: " + e.getMessage());

            // 404 에러 발생 시에만 모델 목록 조회 힌트 제공
            if (e.getMessage().contains("404") || e.getMessage().contains("not found")) {
                printAvailableModels();
            }
        }
    }

    // 소스코드의 import 문을 분석하여 DTO 파일 내용을 가져오는 메소드
    private static String collectRelatedCode(String sourceCode) {
        StringBuilder sb = new StringBuilder();
        // com.booster 패키지 내의 Dto, Request, Response 로 끝나는 클래스만 탐색
        Pattern pattern = Pattern.compile("import\\s+(com\\.booster\\..*?(Dto|Request|Response));");
        Matcher matcher = pattern.matcher(sourceCode);

        while (matcher.find()) {
            String fullClassName = matcher.group(1); // 예: com.booster.order.dto.OrderRequest
            try {
                // 패키지명 -> 파일 경로 변환
                String relativePath = "src/main/java/" + fullClassName.replace(".", "/") + ".java";
                Path dtoPath = MODULE_ROOT_PATH.resolve(relativePath);

                if (Files.exists(dtoPath)) {
                    sb.append("// --- File: ").append(dtoPath.getFileName()).append(" ---\n");
                    sb.append(Files.readString(dtoPath)).append("\n\n");
                }
            } catch (Exception e) {
                // DTO 읽기 실패는 치명적이지 않으므로 무시
            }
        }
        return sb.toString();
    }

    private static String callGeminiApi(String inputContent) throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + GEMINI_API_KEY;

        // PromptTemplate 사용
        Map<String, Object> systemInstruction = Map.of(
                "parts", Map.of("text", PromptTemplate.getSystemPrompt())
        );
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", PromptTemplate.getUserPrompt(inputContent)))
        );
        Map<String, Object> generationConfig = Map.of("temperature", 0.2);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("systemInstruction", systemInstruction);
        requestBody.put("contents", List.of(userContent));
        requestBody.put("generationConfig", generationConfig);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("API Error (" + response.statusCode() + "): " + response.body());
        }

        JsonNode rootNode = objectMapper.readTree(response.body());
        JsonNode candidates = rootNode.path("candidates");

        if (candidates.isEmpty()) {
            throw new RuntimeException("AI가 코드를 생성하지 못했습니다. (응답 비어있음)");
        }

        String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
        // 마크다운 제거
        return content.replaceAll("```java", "").replaceAll("```", "").trim();
    }

    // 404 에러 시 사용 가능한 모델 목록 조회 (디버깅용)
    private static void printAvailableModels() {
        System.out.println("\n🚑 [긴급 진단] 사용 가능한 모델 목록 조회 중...");
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + GEMINI_API_KEY;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            if (root.has("models")) {
                System.out.println("👇 아래 모델명 중 하나를 MODEL_NAME 상수에 복사하세요:");
                for (JsonNode model : root.get("models")) {
                    if (model.toString().contains("generateContent")) {
                        System.out.println("   ✅ " + model.get("name").asText().replace("models/", ""));
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("   (목록 조회 실패: " + ex.getMessage() + ")");
        }
        System.out.println();
    }

    private static Path resolveTestPath(Path sourcePath) {
        String sourcePathStr = sourcePath.toString();
        // src/main/java -> src/test/java
        String testPathStr = sourcePathStr
                .replace("src\\main\\java", "src\\test\\java")
                .replace("src/main/java", "src/test/java");

        // Service.java -> ServiceTests.java
        if (testPathStr.endsWith(".java")) {
            testPathStr = testPathStr.substring(0, testPathStr.length() - 5) + "Tests.java";
        }
        return Paths.get(testPathStr);
    }

    private static void saveTestFile(Path testPath, String testCode) throws IOException {
        if (testPath.getParent() != null) {
            Files.createDirectories(testPath.getParent());
        }
        Files.writeString(testPath, testCode);
    }

    private static void forceUtf8Console() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        } catch (Exception e) {}
    }
}