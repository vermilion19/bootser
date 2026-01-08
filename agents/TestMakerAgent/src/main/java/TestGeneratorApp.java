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
import java.util.stream.Stream;

public class TestGeneratorApp {

    private static final String GEMINI_API_KEY = "AIzaSyDojL9DKhB69KbThIBBdsnHvVyxoOznsh8"; // 여기에 키 입력

    // 1. 우선 이 모델명으로 시도합니다.
    // 만약 에러가 나면, 콘솔에 "사용 가능한 모델 목록"이 출력될 것입니다. 그 중 하나로 여기를 바꾸세요.
    private static final String MODEL_NAME = "gemini-2.5-flash";
    // 추천 대체 후보: "gemini-pro", "gemini-1.5-flash-001", "gemini-1.0-pro"

    private static final String FIXED_PACKAGE_PATH = "src/main/java/com/booster";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) {
        forceUtf8Console(); // 한글 깨짐 방지

        if (args.length == 0) {
            System.err.println("❌ 사용법 오류: [모듈경로] (선택:클래스명)");
            System.err.println("👉 예시: apps/order-service OrderController");
            return;
        }

        String modulePathStr = args[0];
        String specificFileName = (args.length > 1) ? args[1] : null;

        Path scanStartPath = Paths.get(modulePathStr, FIXED_PACKAGE_PATH);

        if (!Files.exists(scanStartPath)) {
            System.err.println("❌ 경로를 찾을 수 없습니다: " + scanStartPath.toAbsolutePath());
            return;
        }

        System.out.println("==========================================");
        System.out.println("🤖 AI 테스트 생성 에이전트 가동");
        System.out.println("🔑 모델: " + MODEL_NAME);
        System.out.println("==========================================\n");

        try (Stream<Path> paths = Files.walk(scanStartPath)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().endsWith("Application.java"))
                    .filter(p -> {
                        if (specificFileName == null) return true;
                        String fileName = p.getFileName().toString();
                        return fileName.equals(specificFileName) || fileName.equals(specificFileName + ".java");
                    })
                    .forEach(TestGeneratorApp::processFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("\n✅ 작업 종료");
    }

    private static void processFile(Path sourcePath) {
        try {
            Path testPath = resolveTestPath(sourcePath);
            if (Files.exists(testPath)) {
                System.out.println("⏭️ [SKIP] 이미 존재함: " + sourcePath.getFileName());
                return;
            }

            System.out.print("⏳ [GENERATE] " + sourcePath.getFileName() + " 분석 중... ");

            String sourceCode = Files.readString(sourcePath);
            String generatedCode = callGeminiApi(sourceCode);

            saveTestFile(testPath, generatedCode);
            System.out.println("DONE ✅");
            Thread.sleep(1000);

        } catch (Exception e) {
            System.out.println("FAIL ❌");
            System.err.println("   └─ 에러: " + e.getMessage());

            // 🚨 404 에러 발생 시, 사용 가능한 모델 목록을 조회해서 알려줌
            if (e.getMessage().contains("404") || e.getMessage().contains("not found")) {
                printAvailableModels();
                System.exit(1); // 더 이상 진행하지 않고 종료
            }
        }
    }

    // 모델 목록 조회 (디버깅용)
    private static void printAvailableModels() {
        System.out.println("\n--------------------------------------------------");
        System.out.println("🚑 [긴급 진단] 현재 API 키로 사용 가능한 모델 목록을 조회합니다...");
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + GEMINI_API_KEY;
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            System.out.println("👇 아래 모델 이름(name) 중 하나를 복사해서 MODEL_NAME 변수에 넣으세요:");
            if (root.has("models")) {
                for (JsonNode model : root.get("models")) {
                    // "generateContent" 기능을 지원하는 모델만 출력
                    if (model.toString().contains("generateContent")) {
                        String fullName = model.get("name").asText();
                        // "models/gemini-1.5-flash" -> "gemini-1.5-flash" 만 추출
                        String shortName = fullName.replace("models/", "");
                        System.out.println("   ✅ " + shortName);
                    }
                }
            } else {
                System.out.println("   (목록 조회 실패: " + response.body() + ")");
            }
        } catch (Exception ex) {
            System.out.println("   (진단 실패: " + ex.getMessage() + ")");
        }
        System.out.println("--------------------------------------------------\n");
    }

    private static String callGeminiApi(String sourceCode) throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent?key=" + GEMINI_API_KEY;

        Map<String, Object> systemInstruction = Map.of(
                "parts", Map.of("text", PromptTemplate.getSystemPrompt())
        );
        Map<String, Object> userContent = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", PromptTemplate.getUserPrompt(sourceCode)))
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
            throw new RuntimeException("생성된 코드가 없습니다.");
        }
        String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
        return content.replaceAll("```java", "").replaceAll("```", "").trim();
    }

    private static void forceUtf8Console() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        } catch (Exception e) {}
    }

    private static Path resolveTestPath(Path sourcePath) {
        String sourcePathStr = sourcePath.toString();
        String testPathStr = sourcePathStr
                .replace("src\\main\\java", "src\\test\\java")
                .replace("src/main/java", "src/test/java");
        if (testPathStr.endsWith(".java")) {
            testPathStr = testPathStr.substring(0, testPathStr.length() - 5) + "Tests.java";
        }
        return Paths.get(testPathStr);
    }

    private static void saveTestFile(Path testPath, String testCode) throws IOException {
        if (testPath.getParent() != null) Files.createDirectories(testPath.getParent());
        Files.writeString(testPath, testCode);
    }
}