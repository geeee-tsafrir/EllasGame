package com.ellasgame.android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.ellasgame.core.ArabicGuessComparison;
import com.ellasgame.core.GameApp;
import com.ellasgame.core.QuestionPrompt;
import com.ellasgame.core.SessionStatistics;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public final class AndroidLauncher extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String SETTINGS_NAME = "ellasgame_settings";
    private static final String CAMERA_KEY = "camera";
    private static final String BACK_CAMERA = "Back camera";
    private static final String FRONT_CAMERA = "Front camera";
    private static final int BACKGROUND = Color.rgb(19, 25, 33);
    private static final int PANEL = Color.rgb(35, 42, 54);
    private static final int TEXT = Color.rgb(235, 240, 246);
    private static final int MUTED_TEXT = Color.rgb(165, 176, 190);
    private static final int BRASS = Color.rgb(158, 111, 52);
    private static final int FEEDBACK_BLUE = Color.rgb(84, 166, 255);
    private static final int FEEDBACK_RED = Color.rgb(255, 91, 91);

    private final GameApp gameApp = new GameApp();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = command -> mainHandler.post(command);
    private final Random random = new Random();
    private final SessionStatistics sessionStatistics = new SessionStatistics();

    private PreviewView cameraPreview;
    private ProcessCameraProvider cameraProvider;
    private boolean cameraConnected;
    private String selectedCamera = BACK_CAMERA;
    private WordChallenge currentChallenge;
    private TextToSpeech textToSpeech;
    private boolean speechReady;
    private boolean speechWarningShown;
    private String pendingSpeechText;
    private Locale pendingSpeechLocale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        selectedCamera = loadCameraSetting();
        initializeTextToSpeech();
        gameApp.start();
        showMenuScreen();
    }

    @Override
    protected void onDestroy() {
        stopCamera();
        shutdownTextToSpeech();
        gameApp.stop();
        super.onDestroy();
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                showSpeechWarning();
                return;
            }
            mainHandler.post(this::configureSpeech);
        });
    }

    private void configureSpeech() {
        if (textToSpeech == null) {
            showSpeechWarning();
            return;
        }

        speechReady = true;
        if (pendingSpeechText != null) {
            String text = pendingSpeechText;
            Locale locale = pendingSpeechLocale;
            pendingSpeechText = null;
            pendingSpeechLocale = null;
            speakText(text, locale);
        }
    }

    private void shutdownTextToSpeech() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }

    private void showSpeechWarning() {
        if (!speechWarningShown) {
            speechWarningShown = true;
            new AlertDialog.Builder(this)
                    .setTitle("Speech unavailable")
                    .setMessage("Text-to-speech is not available on this device. The words are still shown on screen.")
                    .setPositiveButton("Install voices", (dialog, which) -> openTextToSpeechInstaller())
                    .setNegativeButton("OK", null)
                    .show();
        }
    }

    private void openTextToSpeechInstaller() {
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (RuntimeException exception) {
            Toast.makeText(this, "Could not open text-to-speech voice installer.", Toast.LENGTH_LONG).show();
        }
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 21, 28));
        window.setNavigationBarColor(Color.rgb(16, 21, 28));
    }

    private void showMenuScreen() {
        stopCamera();
        LinearLayout menu = fullPanel();
        menu.setGravity(Gravity.CENTER);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        actions.addView(menuActionRow(R.drawable.settings, "Settings", view -> showSettingsDialog()));
        actions.addView(spacer(24));
        actions.addView(menuActionRow(R.drawable.play, "Play", view -> startSession()));
        menu.addView(actions);
        setContentView(menu);
    }

    private LinearLayout menuActionRow(int iconResource, String text, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumWidth(dp(300));
        row.setPadding(0, dp(6), 0, dp(6));

        ImageButton button = iconButton(iconResource, text);
        button.setOnClickListener(listener);
        row.addView(button, new LinearLayout.LayoutParams(dp(92), dp(92)));

        TextView label = textLabel(text, 28f, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(dp(18), 0, 0, 0);
        row.addView(label, labelParams);
        return row;
    }

    private void startSession() {
        sessionStatistics.clear();
        startChallenge();
    }

    private void startChallenge() {
        currentChallenge = WordChallenge.random(random);
        showChoiceScreen();
    }

    private void showChoiceScreen() {
        stopCamera();
        LinearLayout screen = fullPanel();
        screen.setGravity(Gravity.CENTER);

        LinearLayout content = contentPanel();
        content.addView(hebrewQuestionLabel(QuestionPrompt.prefix(), 20f));
        content.addView(spacer(14));
        content.addView(hebrewQuestionLabel(currentChallenge.hebrew(), 46f));
        content.addView(spacer(30));
        content.addView(choiceActionsRow());
        screen.addView(content);
        setContentView(screen);
        speakHebrew(QuestionPrompt.spoken(currentChallenge.hebrew()));
    }

    private void speakHebrew(String text) {
        speakText(text, new Locale("he", "IL"));
    }

    private void speakArabic(String text) {
        speakText(text, new Locale("ar"));
    }

    private void speakText(String text, Locale locale) {
        if (!speechReady || textToSpeech == null) {
            pendingSpeechText = text;
            pendingSpeechLocale = locale;
            return;
        }

        int languageStatus = textToSpeech.setLanguage(locale);
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA
                || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
            showSpeechWarning();
            return;
        }
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speech-" + System.nanoTime());
    }

    private LinearLayout choiceActionsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.addView(choiceButton(R.drawable.camera_steampunk, "Camera", view -> showCameraScreen()));
        row.addView(horizontalSpacer(6));
        row.addView(choiceButton(R.drawable.keyboard_steampunk, "Keyboard", view -> showKeyboardDialog()));
        row.addView(horizontalSpacer(6));
        row.addView(choiceButton(R.drawable.sketchpad_steampunk, "Sketchpad", view -> showSketchpadScreen()));
        return row;
    }

    private void showKeyboardDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.BLACK);
        new AlertDialog.Builder(this)
                .setTitle("Arabic word")
                .setView(input)
                .setPositiveButton("Ready", (dialog, which) -> showResult(input.getText().toString(), new Rect()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCameraScreen() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        LinearLayout screen = fullPanel();
        screen.setPadding(0, 0, 0, 0);
        screen.setOrientation(LinearLayout.VERTICAL);

        FrameLayout previewFrame = new FrameLayout(this);
        cameraPreview = new PreviewView(this);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        previewFrame.addView(cameraPreview, matchParent());
        screen.addView(previewFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        screen.addView(bottomBar(textButton("Capture", view -> capturePreviewBitmap())));
        setContentView(screen);
        startCamera();
    }

    private void showSketchpadScreen() {
        SketchpadView sketchpadView = new SketchpadView(this);

        LinearLayout screen = fullPanel();
        screen.setPadding(0, 0, 0, 0);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.addView(sketchpadView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        screen.addView(bottomBar(sketchpadControls(sketchpadView)));
        setContentView(screen);
    }

    private void capturePreviewBitmap() {
        if (cameraPreview == null || cameraPreview.getBitmap() == null) {
            Toast.makeText(this, "No camera frame is available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap snapshot = cameraPreview.getBitmap();
        stopCamera();
        showRegionScreen(snapshot);
    }

    private void showRegionScreen(Bitmap snapshot) {
        LinearLayout screen = fullPanel();
        screen.setPadding(0, 0, 0, 0);
        screen.setOrientation(LinearLayout.VERTICAL);

        RegionSelectionView regionView = new RegionSelectionView(this, snapshot);
        screen.addView(regionView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        screen.addView(bottomBar(textButton("Ready", view -> {
            Rect selectedRegion = regionView.selectedRegionInBitmapCoordinates();
            String arabicWord = processCapturedRegion(currentChallenge, snapshot, selectedRegion);
            showResult(arabicWord, selectedRegion);
        })));
        setContentView(screen);
    }

    private String processCapturedRegion(WordChallenge challenge, Bitmap snapshot, Rect selectedRegion) {
        return challenge.expectedArabic();
    }

    private String processSketchpadDrawing(WordChallenge challenge, SketchpadView sketchpadView) {
        return challenge.expectedArabic();
    }

    private void showResult(String arabicWord, Rect selectedRegion) {
        stopCamera();
        ArabicGuessComparison.GuessComparison result = ArabicGuessComparison.compare(
                currentChallenge.expectedArabic(),
                arabicWord);
        sessionStatistics.record(currentChallenge.hebrew(), currentChallenge.expectedArabic(), result);

        LinearLayout screen = fullPanel();
        screen.setGravity(Gravity.CENTER);
        LinearLayout content = contentPanel();
        content.addView(textLabel(currentChallenge.hebrew(), 44f, TEXT, Typeface.BOLD));
        content.addView(spacer(16));
        content.addView(feedbackRow("Expected:", expectedFeedbackLabel(result)));
        content.addView(spacer(8));
        content.addView(feedbackRow("User:", userFeedbackLabel(result)));
        content.addView(spacer(8));
        content.addView(textLabel(result.resultText(), 20f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(8));
        content.addView(textLabel("Base letters: " + result.baseLetterErrors() + " errors", 16f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(8));
        content.addView(textLabel("Signs: " + result.signErrors() + " errors", 16f, MUTED_TEXT, Typeface.BOLD));
        if (!selectedRegion.isEmpty()) {
            content.addView(spacer(8));
            content.addView(textLabel("Region: " + selectedRegion.width() + "x" + selectedRegion.height(), 16f, MUTED_TEXT, Typeface.BOLD));
        }
        content.addView(spacer(24));
        content.addView(textButton("Again", view -> startChallenge()));
        content.addView(spacer(12));
        content.addView(textButton("Finish", view -> showSummaryScreen()));
        screen.addView(content);
        setContentView(screen);
        speakArabic(currentChallenge.spokenArabic());
    }

    private void showSummaryScreen() {
        SessionStatistics.Summary summary = sessionStatistics.summary();

        LinearLayout screen = fullPanel();
        screen.setGravity(Gravity.CENTER);
        LinearLayout content = contentPanel();
        content.addView(textLabel("סיכום", 44f, TEXT, Typeface.BOLD));
        content.addView(spacer(16));
        content.addView(textLabel("Words: " + summary.wordCount(), 18f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(8));
        content.addView(textLabel("Perfect scores: " + summary.perfectScores(), 18f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(8));
        content.addView(textLabel("Words with mistakes: " + summary.wordsWithMistakes(), 18f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(8));
        content.addView(textLabel("Average mistakes: " + String.format("%.2f", summary.averageMistakeCount()), 18f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(8));
        content.addView(textLabel("Lead error: " + summary.leadingErrorType(), 18f, MUTED_TEXT, Typeface.BOLD));
        content.addView(spacer(16));
        content.addView(textLabel("Most errors", 20f, MUTED_TEXT, Typeface.BOLD));
        if (summary.wordsWithMostErrors().isEmpty()) {
            content.addView(spacer(8));
            content.addView(textLabel("None", 18f, MUTED_TEXT, Typeface.BOLD));
        } else {
            for (SessionStatistics.WordErrorSummary wordSummary : summary.wordsWithMostErrors()) {
                content.addView(spacer(8));
                content.addView(textLabel(wordSummary.promptWord() + " / " + wordSummary.expectedWord()
                        + ": " + wordSummary.totalErrors() + " errors", 16f, MUTED_TEXT, Typeface.BOLD));
            }
        }
        content.addView(spacer(24));
        content.addView(textButton("Menu", view -> {
            sessionStatistics.clear();
            showMenuScreen();
        }));
        screen.addView(content);
        setContentView(screen);
    }

    private void showSettingsDialog() {
        String[] cameraOptions = availableCameraOptions();
        selectedCamera = validCameraOrDefault(selectedCamera, cameraOptions);

        LinearLayout settingsLayout = new LinearLayout(this);
        settingsLayout.setOrientation(LinearLayout.VERTICAL);
        settingsLayout.setPadding(dp(20), dp(8), dp(20), 0);

        LinearLayout cameraRow = new LinearLayout(this);
        cameraRow.setOrientation(LinearLayout.HORIZONTAL);
        cameraRow.setGravity(Gravity.CENTER_VERTICAL);
        settingsLayout.addView(cameraRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cameraLabel = new TextView(this);
        cameraLabel.setText("Camera");
        cameraLabel.setTextSize(16f);
        cameraRow.addView(cameraLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Spinner cameraSpinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                cameraOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cameraSpinner.setAdapter(adapter);
        cameraSpinner.setSelection(indexOf(cameraOptions, selectedCamera));
        cameraRow.addView(cameraSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f));

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(settingsLayout)
                .setPositiveButton("Save", (dialog, which) -> {
                    selectedCamera = (String) cameraSpinner.getSelectedItem();
                    saveCameraSetting(selectedCamera);
                    if (cameraConnected) {
                        stopCamera();
                        startCamera();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startCamera() {
        if (cameraPreview == null) {
            return;
        }

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                selectedCamera = validCameraOrDefault(selectedCamera, availableCameraOptions());
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelectorFor(selectedCamera), preview);
                cameraConnected = true;
            } catch (ExecutionException | InterruptedException exception) {
                Thread.currentThread().interrupt();
                showCameraError();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                showCameraError();
            }
        }, mainExecutor);
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        cameraConnected = false;
    }

    private void showCameraError() {
        cameraConnected = false;
        Toast.makeText(this, "Could not start Android camera.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            showCameraScreen();
        }
    }

    private ImageButton iconButton(int drawableResource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableResource);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private LinearLayout choiceButton(int drawableResource, String text, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);

        ImageView image = new ImageView(this);
        image.setImageResource(drawableResource);
        image.setContentDescription(text);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.addView(image, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView label = textLabel(text, 14f, TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(8), 0, 0);
        button.addView(label, labelParams);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(84), dp(116)));
        return button;
    }

    private TextView textButton(String text, View.OnClickListener listener) {
        TextView button = textLabel(text, 18f, TEXT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(22), dp(10), dp(22), dp(10));
        button.setMinWidth(dp(170));
        button.setBackground(buttonBackground());
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable buttonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.rgb(77, 55, 34));
        drawable.setStroke(dp(2), BRASS);
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private LinearLayout fullPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(BACKGROUND);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setLayoutParams(matchParent());
        panel.setFitsSystemWindows(true);
        return panel;
    }

    private LinearLayout contentPanel() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(30), dp(30), dp(30), dp(30));
        content.setBackgroundColor(PANEL);
        return content;
    }

    private LinearLayout bottomBar(View button) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(14), dp(14), dp(14), dp(14));
        bar.setBackgroundColor(Color.rgb(24, 29, 38));
        bar.addView(button);
        return bar;
    }

    private LinearLayout sketchpadControls(SketchpadView sketchpadView) {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.addView(textButton("Clear", view -> sketchpadView.clear()));
        controls.addView(horizontalSpacer(12));
        controls.addView(textButton("Ready", view ->
                showResult(processSketchpadDrawing(currentChallenge, sketchpadView), new Rect())));
        return controls;
    }

    private TextView textLabel(String text, float size, int color, int typefaceStyle) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setTypeface(Typeface.DEFAULT, typefaceStyle);
        label.setGravity(Gravity.CENTER);
        return label;
    }

    private TextView hebrewQuestionLabel(String text, float size) {
        TextView label = textLabel(text, size, TEXT, Typeface.BOLD);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(true);
        label.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return label;
    }

    private TextView expectedFeedbackLabel(ArabicGuessComparison.GuessComparison result) {
        TextView label = textLabel(String.join("", result.expectedCharacters()), 22f, FEEDBACK_BLUE, Typeface.BOLD);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return label;
    }

    private LinearLayout feedbackRow(String labelText, TextView feedback) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        TextView label = textLabel(labelText, 16f, MUTED_TEXT, Typeface.BOLD);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                dp(92),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 0, dp(8), 0);
        row.addView(label, labelParams);

        LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(
                dp(140),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(feedback, feedbackParams);
        return row;
    }

    private TextView userFeedbackLabel(ArabicGuessComparison.GuessComparison result) {
        StringBuilder text = new StringBuilder();
        for (ArabicGuessComparison.UserCharacterFeedback character : result.userCharacters()) {
            text.append(character.baseText());
            for (ArabicGuessComparison.UserSignFeedback sign : character.signs()) {
                text.append(sign.text());
            }
        }

        SpannableString feedback = new SpannableString(text.toString());
        int offset = 0;
        for (ArabicGuessComparison.UserCharacterFeedback character : result.userCharacters()) {
            int end = offset + character.baseText().length();
            feedback.setSpan(
                    new ForegroundColorSpan(character.baseCorrect() ? FEEDBACK_BLUE : FEEDBACK_RED),
                    offset,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            offset = end;
            for (ArabicGuessComparison.UserSignFeedback sign : character.signs()) {
                end = offset + sign.text().length();
                feedback.setSpan(
                        new ForegroundColorSpan(sign.correct() ? FEEDBACK_BLUE : FEEDBACK_RED),
                        offset,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                offset = end;
            }
        }

        TextView label = textLabel("", 22f, MUTED_TEXT, Typeface.BOLD);
        label.setText(feedback);
        label.setTextDirection(View.TEXT_DIRECTION_RTL);
        label.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        return label;
    }

    private View spacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return spacer;
    }

    private View horizontalSpacer(int widthDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return spacer;
    }

    private CameraSelector cameraSelectorFor(String camera) {
        if (FRONT_CAMERA.equals(camera)) {
            return CameraSelector.DEFAULT_FRONT_CAMERA;
        }
        return CameraSelector.DEFAULT_BACK_CAMERA;
    }

    private String[] availableCameraOptions() {
        PackageManager packageManager = getPackageManager();
        List<String> options = new ArrayList<>();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            options.add(BACK_CAMERA);
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            options.add(FRONT_CAMERA);
        }
        if (options.isEmpty()) {
            options.add(BACK_CAMERA);
        }
        return options.toArray(new String[0]);
    }

    private String validCameraOrDefault(String camera, String[] cameraOptions) {
        for (String cameraOption : cameraOptions) {
            if (cameraOption.equals(camera)) {
                return camera;
            }
        }
        return cameraOptions[0];
    }

    private int indexOf(String[] values, String value) {
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(value)) {
                return index;
            }
        }
        return 0;
    }

    private String loadCameraSetting() {
        SharedPreferences preferences = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE);
        return preferences.getString(CAMERA_KEY, BACK_CAMERA);
    }

    private void saveCameraSetting(String camera) {
        getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE)
                .edit()
                .putString(CAMERA_KEY, camera)
                .apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static ViewGroup.LayoutParams matchParent() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private record WordChallenge(String hebrew, String expectedArabic, String spokenArabic) {
        private static final List<WordChallenge> WORDS = List.of(
                new WordChallenge("שלום", "سَلَام", "سَلَامْ"),
                new WordChallenge("בית", "بَيْت", "بَيْتْ"),
                new WordChallenge("כלב", "كَلْب", "كَلْبْ"),
                new WordChallenge("ספר", "كِتَاب", "كِتَابْ"),
                new WordChallenge("שמש", "شَمْس", "شَمْسْ"));

        static WordChallenge random(Random random) {
            return WORDS.get(random.nextInt(WORDS.size()));
        }
    }

    private final class RegionSelectionView extends View {
        private final Bitmap bitmap;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageBounds = new RectF();
        private final RectF selectedRegion = new RectF();
        private PointF dragStart;

        RegionSelectionView(ComponentActivity activity, Bitmap bitmap) {
            super(activity);
            this.bitmap = bitmap;
        }

        Rect selectedRegionInBitmapCoordinates() {
            if (selectedRegion.isEmpty() || imageBounds.isEmpty()) {
                return new Rect();
            }

            float xScale = bitmap.getWidth() / imageBounds.width();
            float yScale = bitmap.getHeight() / imageBounds.height();
            return new Rect(
                    Math.round((selectedRegion.left - imageBounds.left) * xScale),
                    Math.round((selectedRegion.top - imageBounds.top) * yScale),
                    Math.round((selectedRegion.right - imageBounds.left) * xScale),
                    Math.round((selectedRegion.bottom - imageBounds.top) * yScale));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(12, 16, 22));

            float scale = Math.min((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float left = (getWidth() - width) / 2f;
            float top = (getHeight() - height) / 2f;
            imageBounds.set(left, top, left + width, top + height);
            canvas.drawBitmap(bitmap, null, imageBounds, paint);

            if (!selectedRegion.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(125, 20, 24, 30));
                canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, selectedRegion.top, paint);
                canvas.drawRect(imageBounds.left, selectedRegion.bottom, imageBounds.right, imageBounds.bottom, paint);
                canvas.drawRect(imageBounds.left, selectedRegion.top, selectedRegion.left, selectedRegion.bottom, paint);
                canvas.drawRect(selectedRegion.right, selectedRegion.top, imageBounds.right, selectedRegion.bottom, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setColor(Color.rgb(242, 194, 90));
                canvas.drawRect(selectedRegion, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            PointF point = clampToImage(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dragStart = point;
                selectedRegion.set(point.x, point.y, point.x, point.y);
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && dragStart != null) {
                selectedRegion.set(
                        Math.min(dragStart.x, point.x),
                        Math.min(dragStart.y, point.y),
                        Math.max(dragStart.x, point.x),
                        Math.max(dragStart.y, point.y));
                invalidate();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                dragStart = null;
                return true;
            }
            return super.onTouchEvent(event);
        }

        private PointF clampToImage(float x, float y) {
            return new PointF(
                    Math.max(imageBounds.left, Math.min(x, imageBounds.right)),
                    Math.max(imageBounds.top, Math.min(y, imageBounds.bottom)));
        }
    }

    private final class SketchpadView extends View {
        private final List<Path> strokes = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Path currentStroke;

        SketchpadView(ComponentActivity activity) {
            super(activity);
            paint.setColor(Color.rgb(47, 32, 20));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            setBackgroundColor(Color.rgb(239, 216, 167));
            setPadding(dp(12), dp(12), dp(12), dp(12));
        }

        void clear() {
            strokes.clear();
            currentStroke = null;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (Path stroke : strokes) {
                canvas.drawPath(stroke, paint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    currentStroke = new Path();
                    currentStroke.moveTo(event.getX(), event.getY());
                    strokes.add(currentStroke);
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE -> {
                    if (currentStroke != null) {
                        currentStroke.lineTo(event.getX(), event.getY());
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentStroke != null) {
                        currentStroke.lineTo(event.getX(), event.getY());
                        currentStroke = null;
                        invalidate();
                    }
                    return true;
                }
                default -> {
                    return super.onTouchEvent(event);
                }
            }
        }
    }
}
