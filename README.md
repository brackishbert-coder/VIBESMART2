# VIBESMART2 — SOM Dubstep Generator

Turn **images into generative beats**. VIBESMART2 vectorizes a picture, lets a
**Self-Organizing Map (SOM)** learn that picture's structure, and uses the organized map to
drive a from-scratch software synth and beat sequencer. The visual is the seed; the music is
what grows out of it.

A **JavaFX** desktop app (`com.somdubstep.ui.AppFX`) with its own audio engine — no samples,
just synthesis. First foray into "vibe programming"; the noise is intentional.

---

## The pipeline

1. **Vectorize.** Each image is sliced into a grid of blocks; every block becomes a
   **5-dimensional feature vector** — mean R, mean G, mean B, brightness, and brightness
   variance (local contrast/texture). A picture becomes a cloud of vectors. Multiple images
   can be combined into one dataset.
2. **Self-organize.** A **Self-Organizing Map** (configurable grid size + learning rate)
   trains on those vectors, folding the high-dimensional cloud onto a 2-D map where similar
   visual textures land near each other. You can reset it or continue training without
   re-initializing.
3. **Generate.** The organized map drives a **pattern generator** that composes a beat at a
   chosen **BPM** and **complexity** (simple / medium / complex).
4. **Synthesize.** A custom **audio engine + sequencer** renders the pattern live: kick,
   snare, hi-hat, and bass voices built from **ADSR envelopes, an LFO, a biquad filter, and
   scratch FX**.
5. **Keep & export.** Audition beats, hold a history, swap/rename/delete, and export patterns
   to `.json` (or images to `.png`).

---

## Requirements

- **JDK 21** (the project targets `maven.compiler.release = 21`).
- **Maven** — it pulls everything else:
  - JavaFX 21.0.2 (`javafx-controls`, `-fxml`, `-swing`),
  - `com.github.sarxos:webcam-capture` (camera input support).
- Audio output device (the engine renders live PCM).

---

## Build & run

```bash
git clone https://github.com/brackishbert-coder/VIBESMART2
cd VIBESMART2

mvn compile
mvn javafx:run        # launches the GUI (com.somdubstep.ui.AppFX)
```

The `javafx-maven-plugin` is preconfigured with the main class, so `mvn javafx:run` handles
the JavaFX module path for you.

You can also import the folder into **Eclipse** (the `.project` / `.classpath` are included)
and run `AppFX` as a Java Application.

### Command-line options (batch layer)

The codebase includes an argument parser intended for headless/batch generation. The shipped
entry point opens the GUI, but the documented flags are:

```
--image <path>          image file to analyze
--bpm <int>             tempo (default 140)
--complexity <s|m|c>    simple | medium | complex (default medium)
--steps <int>           override number of steps (0 = auto by complexity)
--export <files...>     any .png and/or .json files to write
--play                  play audio
--no-train              skip training the SOM (use initial weights)
--seed <long>           RNG seed
--help                  show help
```

---

## Using the app

- **Image dataset** — `Load Image` / `Add Image` (PNG, JPG, BMP, GIF), `Clear Dataset`.
- **SOM** — set `SOM Grid Size` and `Learning Rate`, then `Train SOM`; `Continue training`
  (don't re-init) or `Reset SOM`.
- **Generation** — choose `Pattern Complexity` and `BPM`, then `Generate Beats`.
- **Playback** — `Play` / `Stop`, `Test Scratch`, `Visual Offset (ms)` to sync the visuals.
- **Beat history** — `Preview`, `Swap In`, `Rename`, `Delete`, `Export Selected…`.
- A beat-grid view with a playhead visualizes the running pattern.

---

## Project layout

```
VIBESMART2/
├── pom.xml                                  Maven (groupId: THE, JDK 21, JavaFX 21)
├── src/main/java/com/somdubstep/
│   ├── ui/        AppFX.java                 the JavaFX app (entry point)
│   │              BeatGridView.java          beat-grid + playhead visualization
│   ├── som/       ImageFeatureExtractor.java image → 5-D feature vectors
│   │              SelfOrganizingMap.java     the SOM
│   │              SomVisualizer.java         SOM rendering
│   ├── audio/     AudioEngine.java           live synthesis engine
│   │              Sequencer.java             step sequencer
│   │              Kick/Snare/HiHat/BassNode  synth voices
│   │              ADSR / LFO / Biquad / ScratchFX
│   ├── pattern/   PatternGenerator.java      map → beat pattern
│   │              BeatPattern.java
│   └── util/      ArgParser.java, FastRand.java
├── .project / .classpath                     Eclipse project files
└── README.md
```

---

*Part of the broader experimental ecosystem — a small instrument for turning seeing into
hearing.*
