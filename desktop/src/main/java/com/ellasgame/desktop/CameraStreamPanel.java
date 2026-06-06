package com.ellasgame.desktop;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class CameraStreamPanel extends JPanel {
    private static final int REQUESTED_CAMERA_WIDTH = Integer.getInteger("ellasgame.camera.width", 1280);
    private static final int REQUESTED_CAMERA_HEIGHT = Integer.getInteger("ellasgame.camera.height", 720);
    private static final double REQUESTED_CAMERA_FPS = cameraFps();
    private static final String[] MAC_PIXEL_FORMATS = cameraPixelFormats();

    private BufferedImage currentFrame;
    private FFmpegFrameGrabber frameGrabber;
    private Java2DFrameConverter frameConverter;
    private Thread captureThread;
    private volatile boolean connected;
    private double framesPerSecond;
    private volatile double grabMillis;
    private volatile double convertMillis;
    private volatile double paintQueueMillis;
    private volatile double sourceFramesPerSecond;
    private volatile double grabberFramesPerSecond;
    private volatile String captureModeText = "";

    public CameraStreamPanel() {
        setBackground(new Color(12, 16, 22));
        setPreferredSize(new Dimension(640, 360));
    }

    public boolean isConnected() {
        return connected;
    }

    public BufferedImage snapshot() {
        if (currentFrame == null) {
            return null;
        }

        ColorModel colorModel = currentFrame.getColorModel();
        WritableRaster raster = currentFrame.copyData(null);
        return new BufferedImage(
                colorModel,
                raster,
                colorModel.isAlphaPremultiplied(),
                null);
    }

    public void connect(SelectedCamera camera) {
        if (connected) {
            return;
        }

        if (!openCamera(camera)) {
            return;
        }

        connected = true;
        startCaptureLoop();
    }

    public void disconnect() {
        connected = false;
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
        closeCamera();
        currentFrame = null;
        framesPerSecond = 0.0;
        grabMillis = 0.0;
        convertMillis = 0.0;
        paintQueueMillis = 0.0;
        sourceFramesPerSecond = 0.0;
        grabberFramesPerSecond = 0.0;
        captureModeText = "";
        repaint();
    }

    private boolean openCamera(SelectedCamera camera) {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return openMacCamera(camera);
        }

        try {
            frameGrabber = new FFmpegFrameGrabber(camera.name());
            configureCameraFormat(frameGrabber, null);
            frameGrabber.start();
            captureModeText = describeCaptureMode("default");
            frameConverter = new Java2DFrameConverter();
            return true;
        } catch (org.bytedeco.javacv.FrameGrabber.Exception | LinkageError exception) {
            closeCamera();
            return false;
        }
    }

    private boolean openMacCamera(SelectedCamera camera) {
        for (String pixelFormat : MAC_PIXEL_FORMATS) {
            if (pixelFormat.isBlank()) {
                continue;
            }

            try {
                frameGrabber = new FFmpegFrameGrabber(camera.index() + ":none");
                frameGrabber.setFormat("avfoundation");
                configureCameraFormat(frameGrabber, pixelFormat);
                frameGrabber.start();
                captureModeText = describeCaptureMode(pixelFormat);
                System.out.println("Camera started: " + camera.name() + " using " + captureModeText);
                frameConverter = new Java2DFrameConverter();
                return true;
            } catch (org.bytedeco.javacv.FrameGrabber.Exception | LinkageError exception) {
                closeCamera();
            }
        }

        return false;
    }

    private void configureCameraFormat(FFmpegFrameGrabber grabber, String pixelFormat) {
        grabber.setImageWidth(REQUESTED_CAMERA_WIDTH);
        grabber.setImageHeight(REQUESTED_CAMERA_HEIGHT);
        grabber.setFrameRate(REQUESTED_CAMERA_FPS);
        grabber.setOption("video_size", REQUESTED_CAMERA_WIDTH + "x" + REQUESTED_CAMERA_HEIGHT);
        grabber.setOption("framerate", Double.toString(REQUESTED_CAMERA_FPS));
        grabber.setOption("drop_late_frames", "false");
        if (pixelFormat != null) {
            grabber.setOption("pixel_format", pixelFormat);
        }
    }

    private static double cameraFps() {
        return Double.parseDouble(System.getProperty("ellasgame.camera.fps", "30.0"));
    }

    private static String[] cameraPixelFormats() {
        String pixelFormats = System.getProperty("ellasgame.camera.pixelFormats", "nv12,bgr0,uyvy422,yuyv422");
        return pixelFormats.split("\\s*,\\s*");
    }

    private String describeCaptureMode(String pixelFormat) {
        return "%dx%d@%.1f %s".formatted(
                frameGrabber.getImageWidth(),
                frameGrabber.getImageHeight(),
                frameGrabber.getFrameRate(),
                pixelFormat);
    }

    private void closeCamera() {
        if (frameGrabber != null) {
            try {
                frameGrabber.stop();
                frameGrabber.release();
            } catch (org.bytedeco.javacv.FrameGrabber.Exception exception) {
                // Nothing useful to do during shutdown.
            }
        }
        frameGrabber = null;
        frameConverter = null;
    }

    private void startCaptureLoop() {
        captureThread = new Thread(this::captureLoop, "camera-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void captureLoop() {
        long previousFrameTime = System.nanoTime();
        long previousFrameTimestampMicros = Long.MIN_VALUE;
        long previousGrabberTimestampMicros = Long.MIN_VALUE;
        int diagnosticFrameCount = 0;
        while (connected && frameGrabber != null && frameConverter != null) {
            try {
                long grabStart = System.nanoTime();
                Frame grabbedFrame = frameGrabber.grabImage();
                long grabEnd = System.nanoTime();
                grabMillis = nanosToMillis(grabEnd - grabStart);

                if (grabbedFrame == null) {
                    continue;
                }

                long frameTimestampMicros = grabbedFrame.timestamp;
                if (previousFrameTimestampMicros != Long.MIN_VALUE) {
                    long elapsedMicros = frameTimestampMicros - previousFrameTimestampMicros;
                    if (elapsedMicros > 0) {
                        sourceFramesPerSecond = 1_000_000.0 / elapsedMicros;
                        if (diagnosticFrameCount < 20) {
                            System.out.printf(
                                    Locale.ROOT,
                                    "Camera frame %02d: frame timestamp delta=%d us, source=%.1f FPS, grab=%.1f ms%n",
                                    diagnosticFrameCount + 1,
                                    elapsedMicros,
                                    sourceFramesPerSecond,
                                    grabMillis);
                            diagnosticFrameCount++;
                        }
                    }
                }
                previousFrameTimestampMicros = frameTimestampMicros;

                long grabberTimestampMicros = frameGrabber.getTimestamp();
                if (previousGrabberTimestampMicros != Long.MIN_VALUE) {
                    long elapsedMicros = grabberTimestampMicros - previousGrabberTimestampMicros;
                    if (elapsedMicros > 0) {
                        grabberFramesPerSecond = 1_000_000.0 / elapsedMicros;
                    }
                }
                previousGrabberTimestampMicros = grabberTimestampMicros;

                long convertStart = System.nanoTime();
                BufferedImage frame = frameConverter.convert(grabbedFrame);
                long convertEnd = System.nanoTime();
                convertMillis = nanosToMillis(convertEnd - convertStart);

                long currentFrameTime = System.nanoTime();
                long elapsedNanos = currentFrameTime - previousFrameTime;
                previousFrameTime = currentFrameTime;

                if (elapsedNanos > 0) {
                    framesPerSecond = 1_000_000_000.0 / elapsedNanos;
                }

                long queuedAt = System.nanoTime();
                SwingUtilities.invokeLater(() -> {
                    paintQueueMillis = nanosToMillis(System.nanoTime() - queuedAt);
                    currentFrame = frame;
                    repaint();
                });
            } catch (org.bytedeco.javacv.FrameGrabber.Exception exception) {
                SwingUtilities.invokeLater(this::disconnect);
                return;
            }
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (currentFrame == null) {
            paintEmptyState(graphics2D);
        } else {
            paintFrame(graphics2D);
            paintOverlay(graphics2D);
        }

        graphics2D.dispose();
    }

    private void paintFrame(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int imageWidth = currentFrame.getWidth();
        int imageHeight = currentFrame.getHeight();
        double scale = Math.min((double) panelWidth / imageWidth, (double) panelHeight / imageHeight);
        int scaledWidth = (int) Math.round(imageWidth * scale);
        int scaledHeight = (int) Math.round(imageHeight * scale);
        int x = (panelWidth - scaledWidth) / 2;
        int y = (panelHeight - scaledHeight) / 2;
        graphics.drawImage(currentFrame, x, y, scaledWidth, scaledHeight, null);
    }

    private void paintOverlay(Graphics2D graphics) {
        String[] overlayLines = {
                "FPS %.1f".formatted(framesPerSecond),
                "source %.1f".formatted(sourceFramesPerSecond),
                "grabber %.1f".formatted(grabberFramesPerSecond),
                "%dx%d".formatted(currentFrame.getWidth(), currentFrame.getHeight()),
                captureModeText,
                "grab %.1f ms".formatted(grabMillis),
                "convert %.1f ms".formatted(convertMillis),
                "queue %.1f ms".formatted(paintQueueMillis)
        };
        graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics metrics = graphics.getFontMetrics();
        int width = 0;
        for (String line : overlayLines) {
            width = Math.max(width, metrics.stringWidth(line));
        }
        width += 14;
        int height = metrics.getHeight() * overlayLines.length + 10;

        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRoundRect(10, 10, width, height, 8, 8);
        graphics.setColor(new Color(240, 245, 250));
        int y = 10 + metrics.getAscent() + 3;
        for (String line : overlayLines) {
            graphics.drawString(line, 17, y);
            y += metrics.getHeight();
        }
    }

    private void paintEmptyState(Graphics2D graphics) {
        String text = connected ? "Waiting for camera frame" : "Camera disconnected";
        graphics.setColor(new Color(118, 130, 148));
        graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 16f));
        FontMetrics metrics = graphics.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(text)) / 2;
        int y = (getHeight() + metrics.getAscent()) / 2;
        graphics.drawString(text, x, y);
    }
}
