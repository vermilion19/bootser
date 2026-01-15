import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

//Get-ChildItem -Recurse -Filter *.java | Where-Object { $_.FullName -notmatch "build" } | Get-Content | Where-Object { $_.Trim() -ne "" } | Measure-Object -Line 대용

public class LineCounter {

    // 제외할 폴더 목록
    private static final List<String> EXCLUDE_DIRS = List.of(
            "build", ".gradle", ".idea", ".git", "out", "gradle"
    );

    public static void main(String[] args) {
        // 1. Get current execution path (could be 'booster' or 'Agent')
        Path startPath = Paths.get(System.getProperty("user.dir"));

        // 2. [Core] Find 'booster' root (where settings.gradle is located)
        Path projectRoot = findProjectRoot(startPath);

        if (projectRoot == null) {
            System.err.println("❌ Cannot find project root. (settings.gradle missing)");
            return;
        }

        // 3. Verification (Optional): Check if folder name is 'booster'
        if (!projectRoot.getFileName().toString().equals("booster")) {
            System.out.println("⚠️ Warning: Detected root folder name is not 'booster': " + projectRoot.getFileName());
            // Proceeding anyway as settings.gradle exists
        }

        System.out.println("✅ Analysis Root Path: " + projectRoot.toAbsolutePath());
        System.out.println("Project Root Detected: " + projectRoot.toAbsolutePath());
        System.out.println("Analyzing code...");

        try {
            long totalLines = countLinesInProject(projectRoot);
            System.out.println("------------------------------------------------");
            System.out.println("Total Java LOC: " + String.format("%,d", totalLines) + " lines");
            System.out.println("------------------------------------------------");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 현재 위치부터 상위로 올라가며 settings.gradle이 있는 곳(루트)을 찾음
     */
    private static Path findProjectRoot(Path current) {
        Path path = current.toAbsolutePath();
        while (path != null) {
            // 루트를 식별하는 기준 파일 (settings.gradle 혹은 gradlew)
            if (Files.exists(path.resolve("settings.gradle"))) {
                return path;
            }
            path = path.getParent(); // 상위 폴더로 이동
        }
        return null; // 못 찾음
    }

    private static long countLinesInProject(Path rootPath) throws IOException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(LineCounter::isJavaFile)
                    .filter(p -> isNotExcluded(p, rootPath)) // 루트 기준 상대 경로로 체크
                    .mapToLong(LineCounter::countLines)
                    .sum();
        }
    }

    private static boolean isJavaFile(Path path) {
        return path.toString().endsWith(".java");
    }

    private static boolean isNotExcluded(Path path, Path rootPath) {
        // 절대 경로를 루트 기준 상대 경로로 변환 (예: apps/restaurant/build/...)
        String relativePath = rootPath.relativize(path).toString().replace("\\", "/");

        for (String exclude : EXCLUDE_DIRS) {
            // 경로 중간에 제외 폴더가 포함되어 있거나, 제외 폴더로 시작하는 경우
            if (relativePath.contains("/" + exclude + "/") || relativePath.startsWith(exclude + "/")) {
                return false;
            }
        }
        return true;
    }

    private static long countLines(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            // 빈 줄(공백만 있는 줄) 제외하고 카운트하는 로직 추가됨
            return lines.filter(line -> !line.trim().isEmpty()).count();
        } catch (IOException e) {
            return 0;
        }
    }
}
