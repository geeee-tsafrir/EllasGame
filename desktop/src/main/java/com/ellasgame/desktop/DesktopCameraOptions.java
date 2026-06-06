package com.ellasgame.desktop;

import com.ellasgame.core.AppSettings;
import com.ellasgame.core.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DesktopCameraOptions {
    private static final List<String> FALLBACK_OPTIONS = List.of(AppSettings.DEFAULT_CAMERA);

    public Result<List<String>, String> findCameraOptions() {
        List<String> options = new ArrayList<>();
        options.addAll(findPlatformCameraOptions());

        List<String> distinctOptions = options.stream()
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
        if (distinctOptions.isEmpty()) {
            return Result.success(FALLBACK_OPTIONS);
        }

        return Result.success(distinctOptions);
    }

    public String validCameraOrDefault(String camera) {
        return selectedCameraOrDefault(camera).name();
    }

    public SelectedCamera selectedCameraOrDefault(String camera) {
        Result<List<String>, String> cameraOptions = findCameraOptions();
        if (cameraOptions instanceof Result.Success<List<String>, String> success) {
            return selectedCameraOrDefault(success.value(), camera);
        }

        return new SelectedCamera(AppSettings.DEFAULT_CAMERA, 0);
    }

    public static String validCameraOrDefault(List<String> cameraOptions, String camera) {
        return selectedCameraOrDefault(cameraOptions, camera).name();
    }

    public static SelectedCamera selectedCameraOrDefault(List<String> cameraOptions, String camera) {
        if (cameraOptions.isEmpty()) {
            return new SelectedCamera(AppSettings.DEFAULT_CAMERA, 0);
        }

        int selectedIndex = cameraOptions.indexOf(camera);
        if (selectedIndex >= 0) {
            return new SelectedCamera(camera, selectedIndex);
        }

        return new SelectedCamera(cameraOptions.get(0), 0);
    }

    private List<String> findPlatformCameraOptions() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return findMacCameraOptions();
        }

        return List.of();
    }

    private List<String> findMacCameraOptions() {
        List<String> options = new ArrayList<>();
        runCommand(List.of("system_profiler", "SPCameraDataType"))
                .map(this::parseSystemProfilerCameraOptions)
                .map(foundOptions -> {
                    options.addAll(foundOptions);
                    return foundOptions;
                });

        if (!options.isEmpty()) {
            return options;
        }

        runCommand(List.of("ioreg", "-r", "-c", "AppleH13CamIn", "-l"))
                .map(output -> {
                    if (!output.isBlank()) {
                        options.add("Built-in camera");
                    }
                    return output;
                });

        return options;
    }

    private Result<String, String> runCommand(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Result.failure("Camera detection timed out.");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                return Result.failure("Camera detection command failed.");
            }

            return Result.success(output);
        } catch (IOException exception) {
            return Result.failure("Camera detection command is not available.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Result.failure("Camera detection was interrupted.");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private List<String> parseSystemProfilerCameraOptions(String output) {
        List<String> options = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (line.startsWith("    ")
                    && !line.startsWith("      ")
                    && trimmed.endsWith(":")
                    && !"Camera:".equals(trimmed)) {
                options.add(trimmed.substring(0, trimmed.length() - 1));
            }
        }
        return options;
    }
}
