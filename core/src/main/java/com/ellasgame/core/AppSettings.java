package com.ellasgame.core;

import java.io.Serializable;

public record AppSettings(String camera) implements Serializable {
    public static final String DEFAULT_CAMERA = "Default camera";

    public AppSettings {
        if (camera == null || camera.isBlank()) {
            camera = DEFAULT_CAMERA;
        }
    }

    public static AppSettings defaults() {
        return new AppSettings(DEFAULT_CAMERA);
    }

    public AppSettings withCamera(String newCamera) {
        return new AppSettings(newCamera);
    }
}
