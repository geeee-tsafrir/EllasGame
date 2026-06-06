package com.ellasgame.desktop;

import com.ellasgame.core.GameApp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
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
        SettingsJsonStore settingsStore = new SettingsJsonStore(Path.of("settings.json"));
        settingsStore.load().map(settings -> {
            com.ellasgame.core.ApplicationSettings.replace(settings);
            return settings;
        });

        GameApp gameApp = new GameApp();
        gameApp.start();

        JFrame frame = new JFrame("EllasGame");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(createMainPanel(frame, settingsStore));
        frame.setSize(960, 720);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static JPanel createMainPanel(JFrame owner, SettingsJsonStore settingsStore) {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        JPanel sidePanel = createSidePanel(owner, settingsStore);
        JPanel contentPanel = createContentPanel();

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1.0;

        constraints.gridx = 0;
        constraints.weightx = 0.0;
        mainPanel.add(sidePanel, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        mainPanel.add(contentPanel, constraints);

        return mainPanel;
    }

    private static JPanel createContentPanel() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        JPanel topPanel = createPanel(new Color(35, 42, 54));
        JPanel bottomPanel = createPanel(new Color(55, 62, 76));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;

        constraints.gridy = 0;
        constraints.weighty = 2.0;
        contentPanel.add(topPanel, constraints);

        constraints.gridy = 1;
        constraints.weighty = 1.0;
        contentPanel.add(bottomPanel, constraints);

        return contentPanel;
    }

    private static JPanel createSidePanel(JFrame owner, SettingsJsonStore settingsStore) {
        JPanel sidePanel = new JPanel(new GridBagLayout());
        sidePanel.setBackground(new Color(24, 29, 38));
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(18, 22, 30)));
        sidePanel.setPreferredSize(new Dimension(72, 0));

        JButton settingsButton = createSettingsButton(owner, settingsStore);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weighty = 0.0;
        constraints.insets = new Insets(12, 0, 0, 0);
        sidePanel.add(settingsButton, constraints);

        constraints.gridy = 1;
        constraints.weighty = 1.0;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        sidePanel.add(filler, constraints);

        return sidePanel;
    }

    private static JButton createSettingsButton(JFrame owner, SettingsJsonStore settingsStore) {
        ImageIcon icon = new ImageIcon(DesktopLauncher.class.getResource("/icons/settings.png"));
        JButton button = new JButton(icon);
        button.setPreferredSize(new Dimension(56, 56));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setToolTipText("Settings");
        button.addActionListener(event -> SettingsWindow.show(owner, new DesktopCameraOptions(), settingsStore));
        return button;
    }

    private static JPanel createPanel(Color backgroundColor) {
        JPanel panel = new JPanel();
        panel.setBackground(backgroundColor);
        panel.setBorder(BorderFactory.createLineBorder(new Color(18, 22, 30)));
        return panel;
    }
}
