package com.ellasgame.android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
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

import com.ellasgame.core.GameApp;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public final class AndroidLauncher extends ComponentActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String SETTINGS_NAME = "ellasgame_settings";
    private static final String CAMERA_KEY = "camera";
    private static final String BACK_CAMERA = "Back camera";
    private static final String FRONT_CAMERA = "Front camera";

    private final GameApp gameApp = new GameApp();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = command -> mainHandler.post(command);

    private PreviewView cameraPreview;
    private TextView cameraOverlay;
    private ImageButton cameraButton;
    private ProcessCameraProvider cameraProvider;
    private boolean cameraConnected;
    private String selectedCamera = BACK_CAMERA;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureSystemBars();
        selectedCamera = loadCameraSetting();
        gameApp.start();
        setContentView(createLayout());
    }

    @Override
    protected void onDestroy() {
        stopCamera();
        gameApp.stop();
        super.onDestroy();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(16, 21, 28));
        window.setNavigationBarColor(Color.rgb(16, 21, 28));
    }

    private LinearLayout createLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.rgb(14, 18, 24));
        root.setLayoutParams(matchParent());
        root.setFitsSystemWindows(true);

        int sideWidth = sidePanelWidth();
        LinearLayout sidePanel = new LinearLayout(this);
        sidePanel.setOrientation(LinearLayout.VERTICAL);
        sidePanel.setGravity(Gravity.CENTER_HORIZONTAL);
        sidePanel.setPadding(0, dp(10), 0, dp(10));
        sidePanel.setBackgroundColor(Color.rgb(24, 31, 40));
        root.addView(sidePanel, new LinearLayout.LayoutParams(sideWidth, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageButton settingsButton = sideIconButton(R.drawable.settings, "Settings");
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        sidePanel.addView(settingsButton);

        cameraButton = sideIconButton(R.drawable.camera_disconnected, "Connect camera");
        cameraButton.setOnClickListener(view -> toggleCamera());
        sidePanel.addView(cameraButton);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout topPanel = new FrameLayout(this);
        topPanel.setBackgroundColor(Color.rgb(12, 16, 22));
        content.addView(topPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f));

        cameraPreview = new PreviewView(this);
        cameraPreview.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        topPanel.addView(cameraPreview, matchParent());

        cameraOverlay = new TextView(this);
        cameraOverlay.setText("Camera disconnected");
        cameraOverlay.setTextColor(Color.rgb(240, 245, 250));
        cameraOverlay.setTextSize(14f);
        cameraOverlay.setTypeface(Typeface.DEFAULT_BOLD);
        cameraOverlay.setBackgroundColor(Color.argb(150, 0, 0, 0));
        cameraOverlay.setPadding(dp(8), dp(6), dp(8), dp(6));
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        overlayParams.setMargins(dp(10), dp(10), 0, 0);
        topPanel.addView(cameraOverlay, overlayParams);

        TextView bottomPanel = new TextView(this);
        bottomPanel.setText("Bottom panel");
        bottomPanel.setTextColor(Color.rgb(165, 176, 190));
        bottomPanel.setGravity(Gravity.CENTER);
        bottomPanel.setBackgroundColor(Color.rgb(19, 25, 33));
        content.addView(bottomPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private ImageButton sideIconButton(int drawableResource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableResource);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackground(sideButtonBackground());

        int sideWidth = sidePanelWidth();
        int buttonSize = Math.max(dp(44), sideWidth - dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private GradientDrawable sideButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.rgb(77, 55, 34));
        drawable.setStroke(dp(1), Color.rgb(169, 122, 67));
        drawable.setCornerRadius(dp(8));
        return drawable;
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

    private void toggleCamera() {
        if (cameraConnected) {
            stopCamera();
            return;
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }

        startCamera();
    }

    private void startCamera() {
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
                cameraButton.setImageResource(R.drawable.camera_connected);
                cameraButton.setContentDescription("Disconnect camera");
                cameraOverlay.setText("Android CameraX\n" + selectedCamera);
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
        if (cameraButton != null) {
            cameraButton.setImageResource(R.drawable.camera_disconnected);
            cameraButton.setContentDescription("Connect camera");
        }
        if (cameraOverlay != null) {
            cameraOverlay.setText("Camera disconnected");
        }
    }

    private void showCameraError() {
        cameraConnected = false;
        cameraButton.setImageResource(R.drawable.camera_disconnected);
        cameraOverlay.setText("Camera failed");
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
            startCamera();
        }
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

    private int sidePanelWidth() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(56), Math.min(dp(80), screenWidth / 7));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static ViewGroup.LayoutParams matchParent() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
