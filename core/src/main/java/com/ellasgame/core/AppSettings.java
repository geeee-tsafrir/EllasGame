package com.ellasgame.core;

import java.io.Serializable;
import java.util.Objects;

public record AppSettings(String camera) implements Serializable {
    public static final String DEFAULT_CAMERA = "Default camera";

    public AppSettings {
        Objects.requireNonNull(camera, "camera");
    }

    public static AppSettings defaults() {
        return new AppSettings(DEFAULT_CAMERA);
    }

    public AppSettings withCamera(String newCamera) {
        return new AppSettings(newCamera);
    }
}
