package com.ellasgame.desktop;

import com.ellasgame.core.AppSettings;
import com.ellasgame.core.ApplicationSettings;
import com.ellasgame.core.Result;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.plaf.basic.BasicComboBoxUI;

public final class SettingsWindow {
    private static JDialog settingsDialog;

    private SettingsWindow() {
    }

    public static void show(JFrame owner, DesktopCameraOptions cameraOptions, SettingsJsonStore settingsStore) {
        if (settingsDialog != null && settingsDialog.isDisplayable()) {
            settingsDialog.toFront();
            settingsDialog.requestFocus();
            return;
        }

        AppSettings loadedSettings = loadSettings(settingsStore);
        Result<List<String>, String> currentCameraOptions = cameraOptions.findCameraOptions();
        SettingsContent settingsContent = createContent(currentCameraOptions, loadedSettings, cameraOptions);

        settingsDialog = new JDialog(owner, "Settings", Dialog.ModalityType.APPLICATION_MODAL);
        settingsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        settingsDialog.setContentPane(settingsContent.panel());
        settingsDialog.setSize(420, 320);
        settingsDialog.setMinimumSize(new Dimension(360, 260));
        settingsDialog.setLocationRelativeTo(owner);
        settingsDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                AppSettings updatedSettings = settingsContent.toAppSettings();
                settingsStore.save(updatedSettings).map(settings -> {
                    ApplicationSettings.replace(settings);
                    return settings;
                });
            }

            @Override
            public void windowClosed(WindowEvent event) {
                settingsDialog = null;
            }
        });
        settingsDialog.setVisible(true);
    }

    private static AppSettings loadSettings(SettingsJsonStore settingsStore) {
        Result<AppSettings, String> loadedSettings = settingsStore.load();
        if (loadedSettings instanceof Result.Success<AppSettings, String> success) {
            ApplicationSettings.replace(success.value());
            return success.value();
        }

        AppSettings defaultSettings = AppSettings.defaults();
        ApplicationSettings.replace(defaultSettings);
        return defaultSettings;
    }

    private static SettingsContent createContent(
            Result<List<String>, String> cameraOptions,
            AppSettings settings,
            DesktopCameraOptions cameraValidator) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(35, 42, 54));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Settings");
        title.setForeground(new Color(235, 240, 246));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JComboBox<String> cameraSelector = createCameraSelector(cameraOptions, settings.camera());
        JPanel settingsTable = createSettingsTable(cameraSelector);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(0, 0, 14, 0);
        panel.add(title, constraints);

        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.insets = new Insets(0, 0, 0, 0);
        panel.add(settingsTable, constraints);

        return new SettingsContent(panel, cameraSelector, cameraValidator);
    }

    private static JPanel createSettingsTable(JComboBox<String> cameraSelector) {
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);

        addSettingsRow(table, 0, "Camera", cameraSelector);

        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 0;
        fillerConstraints.gridy = 1;
        fillerConstraints.gridwidth = 2;
        fillerConstraints.weighty = 1.0;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        table.add(filler, fillerConstraints);

        return table;
    }

    private static void addSettingsRow(JPanel table, int row, String name, Component value) {
        JLabel label = new JLabel(name);
        label.setForeground(new Color(235, 240, 246));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));

        GridBagConstraints nameConstraints = new GridBagConstraints();
        nameConstraints.gridx = 0;
        nameConstraints.gridy = row;
        nameConstraints.anchor = GridBagConstraints.NORTHWEST;
        nameConstraints.insets = new Insets(4, 0, 0, 18);
        table.add(label, nameConstraints);

        GridBagConstraints valueConstraints = new GridBagConstraints();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.fill = GridBagConstraints.HORIZONTAL;
        valueConstraints.weightx = 1.0;
        valueConstraints.weighty = 0.0;
        table.add(value, valueConstraints);
    }

    private static JComboBox<String> createCameraSelector(Result<List<String>, String> cameraOptions, String selectedCamera) {
        JComboBox<String> comboBox = new JComboBox<>(createCameraComboBoxModel(cameraOptions));
        comboBox.setUI(new BasicComboBoxUI());
        comboBox.setOpaque(true);
        comboBox.setBackground(new Color(24, 29, 38));
        comboBox.setForeground(new Color(235, 240, 246));
        comboBox.setBorder(BorderFactory.createLineBorder(new Color(18, 22, 30)));
        comboBox.setMaximumRowCount(8);
        comboBox.setPreferredSize(new Dimension(280, 32));
        comboBox.setRenderer(new CameraOptionRenderer());
        if (comboBox.getItemCount() > 0) {
            comboBox.setSelectedItem(validCameraOption(comboBox, selectedCamera));
        }

        return comboBox;
    }

    private static DefaultComboBoxModel<String> createCameraComboBoxModel(Result<List<String>, String> cameraOptions) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();

        if (cameraOptions instanceof Result.Success<List<String>, String> success) {
            success.value().forEach(model::addElement);
        }

        if (model.getSize() == 0) {
            model.addElement(AppSettings.DEFAULT_CAMERA);
        }

        return model;
    }

    private static String validCameraOption(JComboBox<String> comboBox, String camera) {
        for (int index = 0; index < comboBox.getItemCount(); index++) {
            if (comboBox.getItemAt(index).equals(camera)) {
                return camera;
            }
        }

        if (comboBox.getItemCount() == 0) {
            return AppSettings.DEFAULT_CAMERA;
        }

        return comboBox.getItemAt(0);
    }

    private record SettingsContent(
            JPanel panel,
            JComboBox<String> cameraSelector,
            DesktopCameraOptions cameraValidator) {
        AppSettings toAppSettings() {
            Object selectedCamera = cameraSelector.getSelectedItem();
            if (selectedCamera == null) {
                return AppSettings.defaults();
            }

            String validCameraOption = cameraValidator.validCameraOrDefault(selectedCamera.toString());
            return ApplicationSettings.current().withCamera(validCameraOption);
        }
    }

    private static final class CameraOptionRenderer extends DefaultListCellRenderer {
        private static final Color BACKGROUND = new Color(24, 29, 38);
        private static final Color SELECTED_BACKGROUND = new Color(73, 100, 133);
        private static final Color TEXT = new Color(235, 240, 246);

        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list,
                    value,
                    index,
                    isSelected,
                    cellHasFocus);
            label.setOpaque(true);
            label.setText(value == null ? "" : value.toString());
            label.setForeground(TEXT);
            label.setBackground(isSelected ? SELECTED_BACKGROUND : BACKGROUND);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return label;
        }
    }
}
