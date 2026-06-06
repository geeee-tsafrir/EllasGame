# Profiling

## Java Flight Recorder

Run the desktop app with Java Flight Recorder enabled:

```bash
gradle :desktop:runDesktopJfr
```

Use the app normally, connect the camera, reproduce the slow FPS case, then close the app.

The recording is written to:

```text
profiling/ellasgame-camera.jfr
```

Open the recording with Java Mission Control:

```bash
jmc profiling/ellasgame-camera.jfr
```

If `jmc` is not installed, install a JDK distribution or package that includes Java Mission Control.

Useful JFR areas for camera performance:

- Method profiling: time spent in camera grabbing, frame conversion, and Swing repaint code.
- Threads: whether the camera capture thread blocks inside FFmpeg/AVFoundation.
- Allocation: whether frame conversion is creating excessive temporary objects.
- Events: pauses, blocking, or native calls that line up with FPS drops.
