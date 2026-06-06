package com.ellasgame.desktop;

import com.ellasgame.core.GameApp;

public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        GameApp gameApp = new GameApp();
        gameApp.start();
        gameApp.update(0.016f);
        gameApp.stop();

        System.out.println("EllasGame desktop launcher started.");
    }
}
