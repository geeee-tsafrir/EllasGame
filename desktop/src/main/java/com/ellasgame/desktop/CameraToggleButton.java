package com.ellasgame.desktop;

import com.ellasgame.core.ApplicationSettings;

import javax.swing.ImageIcon;
import javax.swing.JButton;

public final class CameraToggleButton {
    private final JButton button;
    private final CameraStreamPanel cameraStreamPanel;
    private final DesktopCameraOptions cameraOptions;
    private final ImageIcon connectedIcon;
    private final ImageIcon disconnectedIcon;

    public CameraToggleButton(CameraStreamPanel cameraStreamPanel, DesktopCameraOptions cameraOptions) {
        this.cameraStreamPanel = cameraStreamPanel;
        this.cameraOptions = cameraOptions;
        connectedIcon = new ImageIcon(CameraToggleButton.class.getResource("/icons/camera-connected.png"));
        disconnectedIcon = new ImageIcon(CameraToggleButton.class.getResource("/icons/camera-disconnected.png"));
        button = createButton();
    }

    public JButton button() {
        return button;
    }

    private JButton createButton() {
        JButton toggleButton = new JButton(disconnectedIcon);
        toggleButton.setPreferredSize(DesktopButtonStyle.BUTTON_SIZE);
        toggleButton.setFocusPainted(false);
        toggleButton.setBorder(DesktopButtonStyle.EMPTY_BORDER);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setOpaque(false);
        toggleButton.setToolTipText("Connect camera");
        toggleButton.addActionListener(event -> toggleCamera());
        return toggleButton;
    }

    private void toggleCamera() {
        if (cameraStreamPanel.isConnected()) {
            cameraStreamPanel.disconnect();
        } else {
            SelectedCamera selectedCamera = cameraOptions.selectedCameraOrDefault(ApplicationSettings.current().camera());
            cameraStreamPanel.connect(selectedCamera);
        }

        updateState();
    }

    private void updateState() {
        boolean connected = cameraStreamPanel.isConnected();
        button.setIcon(connected ? connectedIcon : disconnectedIcon);
        button.setToolTipText(connected ? "Disconnect camera" : "Connect camera");
    }
}
