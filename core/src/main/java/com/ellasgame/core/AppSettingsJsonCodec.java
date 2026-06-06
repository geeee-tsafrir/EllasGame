package com.ellasgame.core;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonDataException;
import com.squareup.moshi.Moshi;

import java.io.IOException;

public final class AppSettingsJsonCodec {
    private final JsonAdapter<AppSettings> adapter;

    public AppSettingsJsonCodec() {
        Moshi moshi = new Moshi.Builder().build();
        adapter = moshi.adapter(AppSettings.class).indent("  ");
    }

    public Result<AppSettings, String> fromJson(String json) {
        try {
            AppSettings settings = adapter.fromJson(json);
            if (settings == null) {
                return Result.failure("settings.json is empty.");
            }

            return Result.success(settings);
        } catch (IOException | JsonDataException exception) {
            return Result.failure("settings.json is invalid.");
        }
    }

    public Result<String, String> toJson(AppSettings settings) {
        try {
            return Result.success(adapter.toJson(settings));
        } catch (JsonDataException exception) {
            return Result.failure("Could not serialize settings.");
        }
    }
}
