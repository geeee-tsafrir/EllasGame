package com.ellasgame.desktop;

import com.ellasgame.core.AppSettings;
import com.ellasgame.core.AppSettingsJsonCodec;
import com.ellasgame.core.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsJsonStore {
    private final AppSettingsJsonCodec codec;
    private final Path path;

    public SettingsJsonStore(Path path) {
        this.codec = new AppSettingsJsonCodec();
        this.path = path;
    }

    public Result<AppSettings, String> load() {
        if (!Files.exists(path)) {
            return Result.success(AppSettings.defaults());
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return codec.fromJson(json);
        } catch (IOException exception) {
            return Result.failure("Could not read settings.json.");
        }
    }

    public Result<AppSettings, String> save(AppSettings settings) {
        return codec.toJson(settings).flatMap(json -> {
            try {
                Files.writeString(path, json, StandardCharsets.UTF_8);
                return Result.success(settings);
            } catch (IOException exception) {
                return Result.failure("Could not write settings.json.");
            }
        });
    }
}
