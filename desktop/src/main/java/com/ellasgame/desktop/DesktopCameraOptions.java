package com.ellasgame.desktop;

import com.ellasgame.core.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DesktopCameraOptions {
    private static final List<String> FALLBACK_OPTIONS = List.of("Default camera");

    public Result<List<String>, String> findCameraOptions() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        if (osName.contains("mac")) {
            return runCommand(List.of("system_profiler", "SPCameraDataType"))
                    .map(this::parseMacCameraOptions)
                    .map(this::withFallback);
        }

        if (osName.contains("win")) {
            return runCommand(List.of(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Get-CimInstance Win32_PnPEntity | "
                            + "Where-Object { $_.Name -match 'Camera|Webcam' } | "
                            + "Select-Object -ExpandProperty Name"))
                    .map(this::parseLineOptions)
                    .map(this::withFallback);
        }

        return runCommand(List.of("v4l2-ctl", "--list-devices"))
                .map(this::parseLinuxCameraOptions)
                .map(this::withFallback);
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

    private List<String> parseMacCameraOptions(String output) {
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

    private List<String> parseLinuxCameraOptions(String output) {
        List<String> options = new ArrayList<>();
        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("/dev/")) {
                options.add(trimmed.endsWith(":") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
            }
        }
        return options;
    }

    private List<String> parseLineOptions(String output) {
        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private List<String> withFallback(List<String> options) {
        if (options.isEmpty()) {
            return FALLBACK_OPTIONS;
        }

        return List.copyOf(options);
    }
}
