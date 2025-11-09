package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GradleWrapperMain {
    private static final int BUFFER_SIZE = 16 * 1024;

    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get("").toAbsolutePath();
        Properties properties = loadWrapperProperties(projectDir);
        String distributionUrl = extractDistributionUrl(properties);

        Path gradleUserHome = findGradleUserHome();
        Path distributionDir = prepareDistribution(distributionUrl, gradleUserHome);
        Path gradleHome = ensureDistributionUnpacked(distributionDir);

        int exitCode = runGradle(projectDir, gradleHome, gradleUserHome, args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static Properties loadWrapperProperties(Path projectDir) throws IOException {
        Path propertiesPath = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        if (!Files.exists(propertiesPath)) {
            throw new IOException("Missing gradle wrapper properties at " + propertiesPath);
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            properties.load(in);
        }
        return properties;
    }

    private static String extractDistributionUrl(Properties properties) throws IOException {
        String distributionUrl = properties.getProperty("distributionUrl");
        if (distributionUrl == null || distributionUrl.isEmpty()) {
            throw new IOException("gradle-wrapper.properties is missing distributionUrl");
        }
        return distributionUrl.replace("\\:", ":");
    }

    private static Path findGradleUserHome() {
        String custom = System.getenv("GRADLE_USER_HOME");
        if (custom != null && !custom.isEmpty()) {
            return Paths.get(custom);
        }
        return Paths.get(System.getProperty("user.home"), ".gradle");
    }

    private static Path prepareDistribution(String distributionUrl, Path gradleUserHome) throws IOException {
        String archiveName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String baseName = archiveName.replaceFirst("\\.zip$", "");
        Path distsDir = gradleUserHome.resolve(Paths.get("wrapper", "dists", baseName));
        Files.createDirectories(distsDir);

        Path archivePath = distsDir.resolve(archiveName);
        if (Files.notExists(archivePath)) {
            downloadArchive(distributionUrl, archivePath);
        }
        return distsDir;
    }

    private static void downloadArchive(String distributionUrl, Path destination) throws IOException {
        System.out.println("Downloading Gradle distribution from " + distributionUrl);
        Files.createDirectories(destination.getParent());
        URL url = URI.create(distributionUrl).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        connection.setReadTimeout((int) Duration.ofMinutes(5).toMillis());
        connection.setInstanceFollowRedirects(true);

        try (InputStream raw = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = raw.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Path ensureDistributionUnpacked(Path distributionDir) throws IOException {
        Path marker = distributionDir.resolve("gradle-home");
        Path gradleBinary = marker.resolve(Paths.get("bin", isWindows() ? "gradle.bat" : "gradle"));
        if (Files.exists(gradleBinary)) {
            return marker;
        }

        Path archivePath = findArchive(distributionDir);
        unpackZip(archivePath, distributionDir);
        Path extracted = findExtractedDirectory(distributionDir);
        Files.move(extracted, marker, StandardCopyOption.REPLACE_EXISTING);

        if (!isWindows()) {
            Path script = marker.resolve(Paths.get("bin", "gradle"));
            Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE
            );
            Files.setPosixFilePermissions(script, perms);
        }

        return marker;
    }

    private static Path findArchive(Path distributionDir) throws IOException {
        try {
            return Files.list(distributionDir)
                .filter(path -> path.getFileName().toString().endsWith(".zip"))
                .findFirst()
                .orElseThrow(() -> new IOException("No Gradle distribution archive found in " + distributionDir));
        } catch (IOException ex) {
            throw ex;
        }
    }

    private static void unpackZip(Path archivePath, Path distributionDir) throws IOException {
        System.out.println("Unpacking Gradle distribution " + archivePath.getFileName());
        try (InputStream fileIn = Files.newInputStream(archivePath);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(fileIn))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = distributionDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream out = Files.newOutputStream(target)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static Path findExtractedDirectory(Path distributionDir) throws IOException {
        try {
            return Files.list(distributionDir)
                .filter(path -> Files.isDirectory(path) && path.getFileName().toString().startsWith("gradle-"))
                .findFirst()
                .orElseThrow(() -> new IOException("Failed to locate unpacked Gradle directory"));
        } catch (IOException ex) {
            throw ex;
        }
    }

    private static int runGradle(Path projectDir, Path gradleHome, Path gradleUserHome, String[] args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        Path executable = gradleHome.resolve(Paths.get("bin", isWindows() ? "gradle.bat" : "gradle"));
        command.add(executable.toString());
        command.addAll(Arrays.asList(args));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectDir.toFile());
        builder.inheritIO();

        Map<String, String> env = builder.environment();
        env.putIfAbsent("GRADLE_USER_HOME", gradleUserHome.toString());
        env.putIfAbsent("ORG_GRADLE_PROJECT_appName", "gradlew");

        Process process = builder.start();
        return process.waitFor();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }
}
