package com.ellasgame.core;

public final class ApplicationSettings {
    private static volatile AppSettings current = AppSettings.defaults();

    private ApplicationSettings() {
    }

    public static AppSettings current() {
        return current;
    }

    public static void replace(AppSettings settings) {
        current = settings;
    }
}
