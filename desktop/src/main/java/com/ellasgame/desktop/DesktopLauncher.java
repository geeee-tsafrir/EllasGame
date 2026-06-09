package com.ellasgame.desktop;

import com.ellasgame.core.ArabicGuessComparison;
import com.ellasgame.core.ApplicationSettings;
import com.ellasgame.core.GameApp;
import com.ellasgame.core.QuestionPrompt;
import com.ellasgame.core.Result;
import com.ellasgame.core.SessionStatistics;
import com.ellasgame.core.VocabularyDictionary;
import com.ellasgame.core.VocabularyEntry;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class DesktopLauncher {
    private static final String MENU_CARD = "menu";
    private static final String GAME_CARD = "game";
    private static final Color BACKGROUND = new Color(19, 25, 33);
    private static final Color PANEL = new Color(35, 42, 54);
    private static final Color TEXT = new Color(235, 240, 246);
    private static final Color MUTED_TEXT = new Color(165, 176, 190);
    private static final Color BRASS = new Color(158, 111, 52);
    private static final Color FEEDBACK_BLUE = new Color(84, 166, 255);
    private static final Color FEEDBACK_RED = new Color(255, 91, 91);

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DesktopLauncher::showWindow);
    }

    private static void showWindow() {
        SettingsJsonStore settingsStore = new SettingsJsonStore(Path.of("settings.json"));
        settingsStore.load().map(settings -> {
            ApplicationSettings.replace(settings);
            return settings;
        });

        GameApp gameApp = new GameApp();
        gameApp.start();

        CameraStreamPanel cameraStreamPanel = new CameraStreamPanel();
        DesktopCameraOptions cameraOptions = new DesktopCameraOptions();
        VocabularyDictionary vocabularyDictionary = loadVocabularyDictionary();
        AppFlow flow = new AppFlow(settingsStore, cameraOptions, cameraStreamPanel, vocabularyDictionary);

        JFrame frame = new JFrame("EllasGame");
        flow.attachFrame(frame);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(flow.createRootPanel());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cameraStreamPanel.disconnect();
                gameApp.stop();
            }
        });
        frame.setMinimumSize(new Dimension(860, 620));
        frame.setSize(1000, 740);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static VocabularyDictionary loadVocabularyDictionary() {
        Result<VocabularyDictionary, String> loadedDictionary = VocabularyDictionary.loadDefault();
        if (loadedDictionary instanceof Result.Success<VocabularyDictionary, String> success) {
            return success.value();
        }

        if (loadedDictionary instanceof Result.Failure<VocabularyDictionary, String> failure) {
            System.err.println(failure.error());
        }
        return new VocabularyDictionary(List.of(new VocabularyEntry(
                "שמות עצם",
                VocabularyEntry.NumberForm.SINGLE,
                "בית",
                "بَيْت")));
    }

    private static final class AppFlow {
        private final SettingsJsonStore settingsStore;
        private final DesktopCameraOptions cameraOptions;
        private final CameraStreamPanel cameraStreamPanel;
        private final VocabularyDictionary vocabularyDictionary;
        private final CardLayout cards = new CardLayout();
        private final JPanel rootPanel = new JPanel(cards);
        private final Random random = new Random();
        private final SessionStatistics sessionStatistics = new SessionStatistics();
        private JFrame frame;
        private VocabularyEntry currentChallenge;

        AppFlow(
                SettingsJsonStore settingsStore,
                DesktopCameraOptions cameraOptions,
                CameraStreamPanel cameraStreamPanel,
                VocabularyDictionary vocabularyDictionary) {
            this.settingsStore = settingsStore;
            this.cameraOptions = cameraOptions;
            this.cameraStreamPanel = cameraStreamPanel;
            this.vocabularyDictionary = vocabularyDictionary;
        }

        void attachFrame(JFrame frame) {
            this.frame = frame;
        }

        JPanel createRootPanel() {
            rootPanel.add(createMenuPanel(), MENU_CARD);
            rootPanel.add(createGamePanel(), GAME_CARD);
            cards.show(rootPanel, MENU_CARD);
            return rootPanel;
        }

        private JPanel createMenuPanel() {
            JPanel menu = fullPanel();
            menu.setLayout(new GridBagLayout());

            JPanel actions = new JPanel();
            actions.setOpaque(false);
            actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
            actions.add(createLargeActionButton("/icons/settings.png", "Settings", () ->
                    SettingsWindow.show(frame, cameraOptions, settingsStore, vocabularyDictionary.groups(), vocabularyDictionary.pages())));
            actions.add(Box.createVerticalStrut(24));
            actions.add(createLargeActionButton("/icons/play.png", "Play", this::startNewSession));

            JPanel window = centeredContent();
            window.add(actions);
            menu.add(window, new GridBagConstraints());
            return menu;
        }

        private JPanel createGamePanel() {
            JPanel gamePanel = fullPanel();
            gamePanel.setLayout(new BorderLayout());
            gamePanel.add(createWordChoicePanel(), BorderLayout.CENTER);
            return gamePanel;
        }

        private void startNewSession() {
            sessionStatistics.clear();
            startNewChallenge();
        }

        private void startNewChallenge() {
            currentChallenge = randomChallenge();
            cameraStreamPanel.disconnect();
            showGameContent(createWordChoicePanel());
            cards.show(rootPanel, GAME_CARD);
        }

        private VocabularyEntry randomChallenge() {
            List<String> selectedGroups = ApplicationSettings.current().vocabularyGroups();
            List<String> selectedPages = ApplicationSettings.current().vocabularyPages();
            VocabularyEntry nextChallenge = vocabularyDictionary.randomEntry(random, selectedGroups, selectedPages);
            if (currentChallenge == null) {
                return nextChallenge;
            }

            List<VocabularyEntry> candidates = vocabularyDictionary.entriesFor(selectedGroups, selectedPages);
            if (candidates.size() <= 1) {
                return nextChallenge;
            }

            while (sameChallenge(currentChallenge, nextChallenge)) {
                nextChallenge = vocabularyDictionary.randomEntry(random, selectedGroups, selectedPages);
            }
            return nextChallenge;
        }

        private boolean sameChallenge(VocabularyEntry first, VocabularyEntry second) {
            return first.hebrew().equals(second.hebrew())
                    && first.number() == second.number()
                    && first.gender() == second.gender()
                    && first.arabic().equals(second.arabic())
                    && first.arabicAlias().equals(second.arabicAlias());
        }

        private JPanel createWordChoicePanel() {
            JPanel panel = fullPanel();
            panel.setLayout(new GridBagLayout());

            JPanel content = centeredContent();
            JLabel prompt = valueLabel(currentChallenge == null ? "" : QuestionPrompt.prefix());
            prompt.setForeground(TEXT);
            prompt.setFont(prompt.getFont().deriveFont(Font.BOLD, 22f));
            content.add(prompt);
            content.add(Box.createVerticalStrut(16));
            JLabel word = titleLabel(currentChallenge == null ? "" : currentChallenge.hebrew());
            content.add(word);
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel(currentChallenge == null ? "" : challengeDetailText(currentChallenge)));
            content.add(Box.createVerticalStrut(34));
            content.add(createChoiceActionsRow());

            panel.add(content, new GridBagConstraints());
            return panel;
        }

        private JPanel createChoiceActionsRow() {
            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.CENTER_ALIGNMENT);

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridy = 0;
            constraints.insets = new Insets(0, 0, 0, 18);
            row.add(createChoiceActionButton("/icons/camera-steampunk.png", "Camera", null), constraints);

            constraints.insets = new Insets(0, 0, 0, 18);
            row.add(createChoiceActionButton("/icons/keyboard-steampunk.png", "Keyboard", this::showKeyboardEntry), constraints);

            constraints.insets = new Insets(0, 0, 0, 0);
            row.add(createChoiceActionButton("/icons/sketchpad-steampunk.png", "Sketchpad", null), constraints);
            return row;
        }

        private void showKeyboardEntry() {
            JTextField field = new JTextField(18);
            int result = JOptionPane.showConfirmDialog(
                    frame,
                    field,
                    "Arabic word",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                showResult(field.getText().trim(), new Rectangle());
            }
        }

        private void showCameraCapturePanel() {
            SelectedCamera selectedCamera = cameraOptions.selectedCameraOrDefault(ApplicationSettings.current().camera());
            cameraStreamPanel.connect(selectedCamera);

            JPanel panel = fullPanel();
            panel.setLayout(new BorderLayout());
            panel.add(cameraStreamPanel, BorderLayout.CENTER);
            panel.add(bottomBar(createTextButton("Capture", this::captureCameraFrame)), BorderLayout.SOUTH);
            showGameContent(panel);
        }

        private void showSketchpadPanel() {
            SketchpadPanel sketchpadPanel = new SketchpadPanel();

            JPanel panel = fullPanel();
            panel.setLayout(new BorderLayout());
            panel.add(sketchpadPanel, BorderLayout.CENTER);
            panel.add(bottomBar(sketchpadControls(sketchpadPanel)), BorderLayout.SOUTH);
            showGameContent(panel);
        }

        private void captureCameraFrame() {
            BufferedImage snapshot = cameraStreamPanel.snapshot();
            if (snapshot == null) {
                JOptionPane.showMessageDialog(frame, "No camera frame is available yet.");
                return;
            }

            cameraStreamPanel.disconnect();
            RegionSelectionPanel regionSelectionPanel = new RegionSelectionPanel(snapshot);

            JPanel panel = fullPanel();
            panel.setLayout(new BorderLayout());
            panel.add(regionSelectionPanel, BorderLayout.CENTER);
            panel.add(bottomBar(createTextButton("Ready", () -> {
                Rectangle selectedRegion = regionSelectionPanel.selectedRegionInImageCoordinates();
                String arabicWord = processCapturedRegion(currentChallenge, snapshot, selectedRegion);
                showResult(arabicWord, selectedRegion);
            })), BorderLayout.SOUTH);
            showGameContent(panel);
        }

        private String processCapturedRegion(
                VocabularyEntry challenge,
                BufferedImage snapshot,
                Rectangle selectedRegion) {
            return challenge.arabic();
        }

        private String processSketchpadDrawing(VocabularyEntry challenge, SketchpadPanel sketchpadPanel) {
            return challenge.arabic();
        }

        private void showResult(String arabicWord, Rectangle selectedRegion) {
            ArabicGuessComparison.GuessComparison result = ArabicGuessComparison.compare(currentChallenge, arabicWord);
            sessionStatistics.record(challengeDisplayText(currentChallenge), result.expectedText(), result);

            JPanel panel = fullPanel();
            panel.setLayout(new GridBagLayout());
            JPanel content = centeredContent();
            content.add(titleLabel(challengeDisplayText(currentChallenge)));
            content.add(Box.createVerticalStrut(16));
            content.add(htmlValueLabel(expectedFeedbackHtml(result)));
            content.add(Box.createVerticalStrut(10));
            content.add(htmlValueLabel(userFeedbackHtml(result)));
            content.add(Box.createVerticalStrut(10));
            content.add(valueLabel(result.resultText()));
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel("Base letters: " + result.baseLetterErrors() + " errors"));
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel("Signs: " + result.signErrors() + " errors"));
            if (!selectedRegion.isEmpty()) {
                content.add(Box.createVerticalStrut(10));
                content.add(valueLabel("Region: " + selectedRegion.width + "x" + selectedRegion.height));
            }
            content.add(Box.createVerticalStrut(28));
            content.add(createTextButton("Again", this::startNewChallenge));
            content.add(Box.createVerticalStrut(12));
            content.add(createTextButton("Finish", () -> {
                cameraStreamPanel.disconnect();
                showGameContent(createSummaryPanel());
            }));

            panel.add(content, new GridBagConstraints());
            showGameContent(panel);
        }

        private JPanel createSummaryPanel() {
            SessionStatistics.Summary summary = sessionStatistics.summary();

            JPanel panel = fullPanel();
            panel.setLayout(new GridBagLayout());
            JPanel content = centeredContent();
            content.add(titleLabel("סיכום"));
            content.add(Box.createVerticalStrut(16));
            content.add(valueLabel("Words: " + summary.wordCount()));
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel("Perfect scores: " + summary.perfectScores()));
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel("Words with mistakes: " + summary.wordsWithMistakes()));
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel("Average mistakes: " + "%.2f".formatted(summary.averageMistakeCount())));
            content.add(Box.createVerticalStrut(8));
            content.add(valueLabel("Lead error: " + summary.leadingErrorType()));
            content.add(Box.createVerticalStrut(16));
            content.add(valueLabel("Most errors"));
            if (summary.wordsWithMostErrors().isEmpty()) {
                content.add(Box.createVerticalStrut(8));
                content.add(valueLabel("None"));
            } else {
                for (SessionStatistics.WordErrorSummary wordSummary : summary.wordsWithMostErrors()) {
                    content.add(Box.createVerticalStrut(8));
                    content.add(valueLabel(wordSummary.promptWord() + " / " + wordSummary.expectedWord()
                            + ": " + wordSummary.totalErrors() + " errors"));
                }
            }
            content.add(Box.createVerticalStrut(28));
            content.add(createTextButton("Menu", () -> {
                sessionStatistics.clear();
                cameraStreamPanel.disconnect();
                cards.show(rootPanel, MENU_CARD);
            }));

            panel.add(content, new GridBagConstraints());
            return panel;
        }

        private void showGameContent(Component component) {
            JPanel gamePanel = (JPanel) rootPanel.getComponent(1);
            gamePanel.removeAll();
            gamePanel.add(component, BorderLayout.CENTER);
            gamePanel.revalidate();
            gamePanel.repaint();
        }

        private String challengeDisplayText(VocabularyEntry challenge) {
            return challenge.hebrew() + " (" + challengeDetailText(challenge) + ")";
        }

        private String challengeDetailText(VocabularyEntry challenge) {
            if (challenge.gender().isApplicable()) {
                return challenge.number().visibleHebrew() + ", " + challenge.gender().visibleHebrew();
            }
            return challenge.number().visibleHebrew();
        }

        private JPanel createLargeActionButton(String iconPath, String text, Runnable action) {
            JPanel row = new JPanel(new GridBagLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            row.setPreferredSize(new Dimension(360, 112));
            row.setMaximumSize(new Dimension(360, 112));

            JButton button = iconButton(iconPath, 96, action);
            JLabel label = valueLabel(text);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = 0;
            constraints.anchor = GridBagConstraints.CENTER;
            row.add(button, constraints);

            constraints.gridx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1.0;
            constraints.insets = new Insets(0, 18, 0, 0);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            row.add(label, constraints);
            return row;
        }

        private JButton createChoiceActionButton(String iconPath, String text, Runnable action) {
            JButton button = new JButton(text, scaledIcon(iconPath, 118, 118));
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setPreferredSize(new Dimension(154, 172));
            button.setMaximumSize(new Dimension(154, 172));
            button.setFocusPainted(false);
            button.setForeground(TEXT);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
            if (action == null) {
                button.setEnabled(false);
            } else {
                button.addActionListener(event -> action.run());
            }
            return button;
        }

        private JButton iconButton(String iconPath, int size, Runnable action) {
            JButton button = new JButton(scaledIcon(iconPath, size, size));
            button.setPreferredSize(new Dimension(size, size));
            button.setMaximumSize(new Dimension(size, size));
            button.setFocusPainted(false);
            button.setOpaque(false);
            button.setContentAreaFilled(false);
            button.setBorder(BorderFactory.createEmptyBorder());
            button.addActionListener(event -> action.run());
            return button;
        }

        private ImageIcon scaledIcon(String path, int width, int height) {
            ImageIcon icon = new ImageIcon(DesktopLauncher.class.getResource(path));
            Image scaled = icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        }

        private JButton createTextButton(String text, Runnable action) {
            JButton button = new JButton(text);
            button.setAlignmentX(Component.CENTER_ALIGNMENT);
            button.setFocusPainted(false);
            button.setForeground(TEXT);
            button.setBackground(new Color(77, 55, 34));
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BRASS, 2),
                    BorderFactory.createEmptyBorder(10, 22, 10, 22)));
            button.setFont(button.getFont().deriveFont(Font.BOLD, 18f));
            button.addActionListener(event -> action.run());
            return button;
        }

        private JPanel sketchpadControls(SketchpadPanel sketchpadPanel) {
            JPanel controls = new JPanel(new GridBagLayout());
            controls.setOpaque(false);

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.insets = new Insets(0, 0, 0, 12);
            controls.add(createTextButton("Clear", sketchpadPanel::clear), constraints);

            constraints.gridx = 1;
            constraints.insets = new Insets(0, 0, 0, 0);
            controls.add(createTextButton("Ready", () ->
                    showResult(processSketchpadDrawing(currentChallenge, sketchpadPanel), new Rectangle())), constraints);
            return controls;
        }

        private JPanel bottomBar(Component content) {
            JPanel bar = new JPanel(new GridBagLayout());
            bar.setBackground(new Color(24, 29, 38));
            bar.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            bar.add(content, new GridBagConstraints());
            return bar;
        }

        private JPanel fullPanel() {
            JPanel panel = new JPanel();
            panel.setBackground(BACKGROUND);
            panel.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
            return panel;
        }

        private JPanel centeredContent() {
            JPanel content = new JPanel();
            content.setOpaque(true);
            content.setBackground(PANEL);
            content.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(18, 22, 30), 1),
                    BorderFactory.createEmptyBorder(34, 48, 34, 48)));
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            return content;
        }

        private JLabel titleLabel(String text) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            label.setForeground(TEXT);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 46f));
            return label;
        }

        private JLabel valueLabel(String text) {
            JLabel label = new JLabel(text, SwingConstants.CENTER);
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            label.setForeground(MUTED_TEXT);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
            return label;
        }

        private JLabel htmlValueLabel(String html) {
            JLabel label = valueLabel("");
            label.setText(html);
            return label;
        }

        private String expectedFeedbackHtml(ArabicGuessComparison.GuessComparison result) {
            return "<html><table><tr><td align='right' width='86'><span style='color:" + htmlColor(MUTED_TEXT) + "'>Expected:</span></td>"
                    + "<td align='right' width='260'><span dir='rtl' style='color:" + htmlColor(FEEDBACK_BLUE) + "'>"
                    + escapeHtml(result.expectedText())
                    + "</span></td></tr></table></html>";
        }

        private String userFeedbackHtml(ArabicGuessComparison.GuessComparison result) {
            StringBuilder html = new StringBuilder("<html><table><tr><td align='right' width='86'><span style='color:")
                    .append(htmlColor(MUTED_TEXT))
                    .append("'>User:</span></td><td align='right' width='140'><span dir='rtl'>");
            for (ArabicGuessComparison.UserCharacterFeedback character : result.userCharacters()) {
                html.append("<span style='color:")
                        .append(htmlColor(feedbackColor(character)))
                        .append("'>")
                        .append(escapeHtml(character.text()))
                        .append("</span>");
            }
            html.append("</span></td></tr></table></html>");
            return html.toString();
        }

        private Color feedbackColor(ArabicGuessComparison.UserCharacterFeedback character) {
            if (!character.baseCorrect()) {
                return FEEDBACK_RED;
            }
            for (ArabicGuessComparison.UserSignFeedback sign : character.signs()) {
                if (!sign.correct()) {
                    return FEEDBACK_RED;
                }
            }
            return FEEDBACK_BLUE;
        }

        private String htmlColor(Color color) {
            return "#%02x%02x%02x".formatted(color.getRed(), color.getGreen(), color.getBlue());
        }

        private String escapeHtml(String text) {
            return text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }

    private static final class SketchpadPanel extends JPanel {
        private final List<List<Point>> strokes = new ArrayList<>();
        private List<Point> currentStroke;

        SketchpadPanel() {
            setBackground(new Color(239, 216, 167));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BRASS, 4),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)));

            MouseAdapter drawingHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    currentStroke = new ArrayList<>();
                    currentStroke.add(event.getPoint());
                    strokes.add(currentStroke);
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (currentStroke != null) {
                        currentStroke.add(event.getPoint());
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (currentStroke != null) {
                        currentStroke.add(event.getPoint());
                        currentStroke = null;
                        repaint();
                    }
                }
            };
            addMouseListener(drawingHandler);
            addMouseMotionListener(drawingHandler);
        }

        void clear() {
            strokes.clear();
            currentStroke = null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics2D.setColor(new Color(47, 32, 20));
                for (List<Point> stroke : strokes) {
                    paintStroke(graphics2D, stroke);
                }
            } finally {
                graphics2D.dispose();
            }
        }

        private void paintStroke(Graphics2D graphics2D, List<Point> stroke) {
            for (int index = 1; index < stroke.size(); index++) {
                Point from = stroke.get(index - 1);
                Point to = stroke.get(index);
                graphics2D.drawLine(from.x, from.y, to.x, to.y);
            }
        }
    }

}
