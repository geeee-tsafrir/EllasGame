package com.ellasgame.desktop;

import com.ellasgame.core.GameApp;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DesktopLauncher::showWindow);
    }

    private static void showWindow() {
        GameApp gameApp = new GameApp();
        gameApp.start();

        JFrame frame = new JFrame("EllasGame");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(createMainPanel());
        frame.setSize(960, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new GridBagLayout());

        JPanel topPanel = createPanel(new Color(35, 42, 54));
        JPanel bottomPanel = createPanel(new Color(55, 62, 76));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;

        constraints.gridy = 0;
        constraints.weighty = 2.0;
        mainPanel.add(topPanel, constraints);

        constraints.gridy = 1;
        constraints.weighty = 1.0;
        mainPanel.add(bottomPanel, constraints);

        return mainPanel;
    }

    private static JPanel createPanel(Color backgroundColor) {
        JPanel panel = new JPanel();
        panel.setBackground(backgroundColor);
        panel.setBorder(BorderFactory.createLineBorder(new Color(18, 22, 30)));
        return panel;
    }
}
