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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicComboBoxUI;

public final class SettingsWindow {
    private static JDialog settingsDialog;

    private SettingsWindow() {
    }

    public static void show(
            JFrame owner,
            DesktopCameraOptions cameraOptions,
            SettingsJsonStore settingsStore,
            List<String> vocabularyGroups,
            List<String> vocabularyPages,
            Map<String, Integer> vocabularyPageWordCounts,
            Runnable onSettingsChanged) {
        if (settingsDialog != null && settingsDialog.isDisplayable()) {
            settingsDialog.toFront();
            settingsDialog.requestFocus();
            return;
        }

        AppSettings loadedSettings = loadSettings(settingsStore);
        Result<List<String>, String> currentCameraOptions = cameraOptions.findCameraOptions();
        SettingsContent settingsContent = createContent(
                currentCameraOptions,
                loadedSettings,
                cameraOptions,
                vocabularyGroups,
                vocabularyPages,
                vocabularyPageWordCounts);

        settingsDialog = new JDialog(owner, "Settings", Dialog.ModalityType.APPLICATION_MODAL);
        settingsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        settingsDialog.setContentPane(settingsContent.panel());
        settingsDialog.setSize(440, 430);
        settingsDialog.setMinimumSize(new Dimension(380, 360));
        settingsDialog.setLocationRelativeTo(owner);
        settingsDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                AppSettings updatedSettings = settingsContent.toAppSettings();
                settingsStore.save(updatedSettings).map(settings -> {
                    ApplicationSettings.replace(settings);
                    onSettingsChanged.run();
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
            DesktopCameraOptions cameraValidator,
            List<String> vocabularyGroups,
            List<String> vocabularyPages,
            Map<String, Integer> vocabularyPageWordCounts) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(35, 42, 54));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Settings");
        title.setForeground(new Color(235, 240, 246));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JComboBox<String> cameraSelector = createCameraSelector(cameraOptions, settings.camera());
        VocabularyCheckboxSelector vocabularyGroupSelector = createVocabularyCheckboxSelector(
                "All groups",
                vocabularyGroups,
                settings.vocabularyGroups(),
                Map.of());
        VocabularyCheckboxSelector vocabularyPageSelector = createVocabularyCheckboxSelector(
                "All days",
                vocabularyPages,
                settings.vocabularyPages(),
                vocabularyPageWordCounts);
        JPanel settingsTable = createSettingsTable(cameraSelector, vocabularyGroupSelector.panel(), vocabularyPageSelector.panel());

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

        return new SettingsContent(panel, cameraSelector, cameraValidator, vocabularyGroupSelector, vocabularyPageSelector);
    }

    private static JPanel createSettingsTable(JComboBox<String> cameraSelector, Component groupSelector, Component pageSelector) {
        JPanel table = new JPanel(new GridBagLayout());
        table.setOpaque(false);

        addSettingsRow(table, 0, "Camera", cameraSelector);
        addSettingsRow(table, 1, "Groups", groupSelector);
        addSettingsRow(table, 2, "Days", pageSelector);

        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 0;
        fillerConstraints.gridy = 3;
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

    private static VocabularyCheckboxSelector createVocabularyCheckboxSelector(
            String allLabel,
            List<String> values,
            List<String> selectedValues,
            Map<String, Integer> valueWordCounts) {
        JCheckBox allValues = styledCheckBox(allLabel, selectedValues == null || selectedValues.isEmpty());
        JPanel checkboxPanel = new JPanel();
        checkboxPanel.setOpaque(false);
        checkboxPanel.setLayout(new BoxLayout(checkboxPanel, BoxLayout.Y_AXIS));
        checkboxPanel.add(allValues);

        List<ValueCheckbox> valueCheckboxes = new ArrayList<>();
        Set<String> selected = selectedValues == null ? Set.of() : new LinkedHashSet<>(selectedValues);
        for (String value : values) {
            JCheckBox checkbox = styledCheckBox(value, allValues.isSelected() || selected.contains(value));
            valueCheckboxes.add(new ValueCheckbox(value, checkbox));
            checkboxPanel.add(valueRow(value, checkbox, valueWordCounts));
        }

        boolean[] updatingValueCheckboxes = {false};
        allValues.addActionListener(event -> {
            if (updatingValueCheckboxes[0]) {
                return;
            }
            boolean selectedAll = allValues.isSelected();
            for (ValueCheckbox valueCheckbox : valueCheckboxes) {
                valueCheckbox.checkbox().setSelected(selectedAll);
            }
        });
        for (ValueCheckbox valueCheckbox : valueCheckboxes) {
            valueCheckbox.checkbox().addActionListener(event -> {
                if (updatingValueCheckboxes[0] || !allValues.isSelected()) {
                    return;
                }

                updatingValueCheckboxes[0] = true;
                allValues.setSelected(false);
                updatingValueCheckboxes[0] = false;
            });
        }

        JScrollPane scrollPane = new JScrollPane(checkboxPanel);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(18, 22, 30)));
        scrollPane.setPreferredSize(new Dimension(280, 96));
        scrollPane.getViewport().setBackground(new Color(24, 29, 38));
        return new VocabularyCheckboxSelector(scrollPane, allValues, valueCheckboxes);
    }

    private static Component valueRow(String value, JCheckBox checkbox, Map<String, Integer> valueWordCounts) {
        Integer wordCount = valueWordCounts.get(value);
        if (wordCount == null) {
            return checkbox;
        }

        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(true);
        row.setBackground(new Color(24, 29, 38));

        GridBagConstraints checkboxConstraints = new GridBagConstraints();
        checkboxConstraints.gridx = 0;
        checkboxConstraints.gridy = 0;
        checkboxConstraints.fill = GridBagConstraints.HORIZONTAL;
        checkboxConstraints.weightx = 1.0;
        row.add(checkbox, checkboxConstraints);

        JLabel countLabel = new JLabel(wordCount + " words");
        countLabel.setForeground(new Color(165, 176, 190));
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD, 12f));
        countLabel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 8));

        GridBagConstraints countConstraints = new GridBagConstraints();
        countConstraints.gridx = 1;
        countConstraints.gridy = 0;
        countConstraints.anchor = GridBagConstraints.EAST;
        row.add(countLabel, countConstraints);

        return row;
    }

    private static JCheckBox styledCheckBox(String text, boolean selected) {
        JCheckBox checkbox = new JCheckBox(text, selected);
        checkbox.setOpaque(true);
        checkbox.setBackground(new Color(24, 29, 38));
        checkbox.setForeground(new Color(235, 240, 246));
        checkbox.setFont(checkbox.getFont().deriveFont(Font.BOLD, 13f));
        checkbox.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return checkbox;
    }

    private record SettingsContent(
            JPanel panel,
            JComboBox<String> cameraSelector,
            DesktopCameraOptions cameraValidator,
            VocabularyCheckboxSelector vocabularyGroupSelector,
            VocabularyCheckboxSelector vocabularyPageSelector) {
        AppSettings toAppSettings() {
            Object selectedCamera = cameraSelector.getSelectedItem();
            if (selectedCamera == null) {
                return AppSettings.defaults();
            }

            String validCameraOption = cameraValidator.validCameraOrDefault(selectedCamera.toString());
            return ApplicationSettings.current()
                    .withCamera(validCameraOption)
                    .withVocabularyGroups(vocabularyGroupSelector.selectedValues())
                    .withVocabularyPages(vocabularyPageSelector.selectedValues());
        }
    }

    private record VocabularyCheckboxSelector(
            Component panel,
            JCheckBox allValues,
            List<ValueCheckbox> valueCheckboxes) {
        List<String> selectedValues() {
            if (allValues.isSelected()) {
                return List.of();
            }

            List<String> selectedValues = new ArrayList<>();
            for (ValueCheckbox valueCheckbox : valueCheckboxes) {
                if (valueCheckbox.checkbox().isSelected()) {
                    selectedValues.add(valueCheckbox.value());
                }
            }
            return selectedValues;
        }
    }

    private record ValueCheckbox(String value, JCheckBox checkbox) {
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
