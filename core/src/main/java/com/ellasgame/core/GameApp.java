package com.ellasgame.core;

public final class GameApp {
    private boolean running;

    public void start() {
        running = true;
    }

    public void update(float deltaSeconds) {
        if (!running) {
            return;
        }

        // Shared game simulation updates will live here.
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}
