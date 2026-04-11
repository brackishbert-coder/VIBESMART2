
package com.somdubstep.ui;

import com.somdubstep.audio.AudioEngine;
import com.somdubstep.audio.Sequencer;
import com.somdubstep.pattern.BeatPattern;
import com.somdubstep.pattern.PatternGenerator;
import com.somdubstep.som.ImageFeatureExtractor;
import com.somdubstep.som.SelfOrganizingMap;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class AppFX extends Application {
	private Slider bpmSlider = null;
	private int currentBpm = 140;
	private Canvas imageCanvas = new Canvas(300, 300);
	private Canvas somCanvas = new Canvas(300, 300);
	private BeatGridView gridView = new BeatGridView();
	private Slider vizOffsetMs = slider(-100, 100, 0);
	private Slider gridSize = slider(5, 20, 10);
	private Slider learningRate = slider(0.01, 1.0, 0.3);
	private Slider bpm = slider(120, 180, 140);
	private ComboBox<String> complexity = new ComboBox<>();
	private Button btnLoad = new Button("📸 Load Image");
	private Button btnTrain = new Button("🧠 Train SOM");
	private Button btnGenerate = new Button("🎵 Generate Beats");
	private Button btnPlay = new Button("▶️ Play");
	private Button btnStop = new Button("⏹ Stop");

	private float[][] features = null;
	private SelfOrganizingMap som = null;
	private BeatPattern pattern = null;
	private AudioEngine audio = null;
	private Sequencer seq = null;

	private AtomicBoolean playing = new AtomicBoolean(false);
	private int playHead = 0;
	// --- Playhead animation state ---
	private javafx.animation.AnimationTimer playheadTimer;
	private volatile long transportZeroSamples = 0L; // where step 0 started this transport
	private volatile int  uiSteps = 32;              // updated to currentPattern.steps()
	private volatile int  uiStepSamples = 2205;      // updated to seq.stepSamples()
	private volatile boolean animate = false;

	// Optional UI offset (ms) if you have a slider; else leave 0.
	private volatile int uiOffsetMs = 0;

	private volatile long firstCycleStartWriteClock = -1;
	private Thread loopScheduler;

	private java.util.List<float[]> corpus = new java.util.ArrayList<>();
	private int imagesInCorpus = 0;

	// UI widgets for incremental training
	private CheckBox accumulateToggle = new CheckBox("Continue training (don’t re-init)");
	private ToggleGroup trainScopeGroup = new ToggleGroup();
	private RadioButton trainLatestOnly = new RadioButton("Latest");
	private RadioButton trainAllSoFar = new RadioButton("All so far");
	private Button addImageBtn = new Button("➕ Add Image");
	private Button resetSomBtn = new Button("♻ Reset SOM");
	private Button clearDatasetBtn = new Button("🧹 Clear Dataset");
	private Label corpusInfo = new Label("Dataset: 0 images, 0 vectors");
	// AppFX.java (fields)
	private final com.somdubstep.audio.ScratchFX.Params scratchParams = new com.somdubstep.audio.ScratchFX.Params();
	boolean withScratch = true;
	
	
	// AppFX.java (fields)
	private static final int MAX_BEAT_HISTORY = 50;

	private final javafx.collections.ObservableList<BeatHistoryItem> beatHistory =
	        javafx.collections.FXCollections.observableArrayList();

	public static final class BeatHistoryItem {
	    public final long   id = System.nanoTime();
	    public String       name;
	    public final int    bpm;
	    public final BeatPattern pattern;
	    public final java.time.Instant createdAt = java.time.Instant.now();
	    BeatHistoryItem(String name, int bpm, BeatPattern p){ this.name=name; this.bpm=bpm; this.pattern=p; }
	}
	private javafx.scene.control.ListView<BeatHistoryItem> historyList;
	private Stage stage;
	private BeatPattern currentPattern;
	private PatternGenerator gen;
	private int steps=32;
	
	private java.util.concurrent.ScheduledExecutorService schedExec;
	private volatile boolean schedulerRunning = false;
	private volatile long nextStartSample = 0;
	private int lookaheadSamples = 0;
	

	// optional: if you have a BPM slider/control, keep currentBpm in sync
	
	
	
	private int getCurrentBpm() {
	    if (bpmSlider != null) {
	        int v = (int)Math.round(bpmSlider.getValue());
	        currentBpm = v; // keep field in sync
	    }
	    return currentBpm;
	}
	
	
	private void startPlayheadTimer() {
	    if (playheadTimer != null) return;

	    playheadTimer = new javafx.animation.AnimationTimer() {
	        private int lastStep = -1;
	        @Override public void handle(long nowNanos) {
	            if (!animate || audio == null || uiSteps <= 0 || uiStepSamples <= 0) return;

	            long head = audio.playbackHeadSamples() + audio.outputLatencySamples();
	            long t = head - transportZeroSamples + (long)(uiOffsetMs * (AudioEngine.SR / 1000.0));
	            if (t < 0) t = 0;

	            // Convert absolute time to step index [0 .. uiSteps-1]
	            long step = (t / uiStepSamples) % uiSteps;
	            int s = (int) step;

	            if (s != lastStep) {
	                lastStep = s;
	                // Update your grid; rename if your API is different:
	                gridView.highlight(s);
	            }
	        }
	    };
	    playheadTimer.start();
	}

	private void stopPlayheadTimer() {
	    animate = false;
	    if (playheadTimer != null) {
	        playheadTimer.stop();
	        playheadTimer = null;
	    }
	}

	
	
	
	@Override
	public void start(Stage stage) throws Exception {

		this.stage = stage;
		audio = new AudioEngine();
		complexity.getItems().addAll("simple", "medium", "complex");
		complexity.getSelectionModel().select("medium");

		btnTrain.setDisable(true);
		btnGenerate.setDisable(true);
		btnPlay.setDisable(true);
		btnStop.setDisable(true);

		btnLoad.setOnAction(e -> loadImage(stage));
		btnTrain.setOnAction(e -> trainSOM());
		btnGenerate.setOnAction(e -> { 
		    generatePattern();
		    stop1();
btnPlay.setDisable(true);
btnStop.setDisable(false);
		    handlePlay();              // keep this
		});
		btnPlay.setOnAction(e -> {handlePlay();btnPlay.setDisable(true);
		btnStop.setDisable(false);});   // <— replace: was audio.start(); play();
		btnStop.setOnAction(e -> {handleStop();btnPlay.setDisable(false);
		btnStop.setDisable(true);});

		GridPane controls = new GridPane();
		controls.setHgap(10);
		controls.setVgap(8);
		controls.add(new Label("SOM Grid Size"), 0, 0);
		controls.add(gridSize, 1, 0);
		controls.add(new Label("Learning Rate"), 0, 1);
		controls.add(learningRate, 1, 1);
		controls.add(new Label("BPM"), 0, 2);
		controls.add(bpm, 1, 2);
		controls.add(new Label("Pattern Complexity"), 0, 3);
		controls.add(complexity, 1, 3);
		controls.add(new Label("Visual Offset (ms)"), 0, 4);
		controls.add(vizOffsetMs, 1, 4);

		trainLatestOnly.setToggleGroup(trainScopeGroup);
		trainAllSoFar.setToggleGroup(trainScopeGroup);
		trainAllSoFar.setSelected(true); // default: train over all seen data
		accumulateToggle.setSelected(true); // default: keep training the same SOM

		addImageBtn.setOnAction(e -> loadImage(stage)); // reuse your image loader
		resetSomBtn.setOnAction(e -> {
			som = null;
			renderSom(); // clears the canvas
			btnGenerate.setDisable(true);
		});
		clearDatasetBtn.setOnAction(e -> {
			corpus.clear();
			imagesInCorpus = 0;
			updateCorpusLabel();
		});

		HBox buttons = new HBox(10, btnLoad, btnTrain, btnGenerate, btnPlay, btnStop, trainLatestOnly, trainAllSoFar,
				accumulateToggle, addImageBtn, resetSomBtn, clearDatasetBtn);
		updateCorpusLabel();
		buttons.setAlignment(Pos.CENTER);

		VBox left = new VBox(10, titled("Original Image", imageCanvas), titled("SOM Neural Map", somCanvas));
		left.setPrefWidth(320);
		HBox trainScopeBox = new HBox(12, new Label("Train on:"), trainLatestOnly, trainAllSoFar);
		trainScopeBox.setAlignment(Pos.CENTER_LEFT);

		HBox datasetButtons = new HBox(10, addImageBtn, resetSomBtn, clearDatasetBtn);
		datasetButtons.setAlignment(Pos.CENTER_LEFT);

		VBox trainingControls = new VBox(6, accumulateToggle, trainScopeBox, datasetButtons, corpusInfo);
		trainingControls.setPadding(new Insets(6, 0, 0, 0));
		
		
		// Then include trainingControls under your existing “buttons”:
		VBox right = new VBox(10, controls, buttons, trainingControls, // <— new line
				titled("Generated Beat Pattern", gridView), titled("Sound Controls", buildInstrumentAccordion()));

		right.setPrefWidth(540);
		left.setPadding(new Insets(10));
		right.setPadding(new Insets(10));

		HBox root = new HBox(20, left, right);
		root.setPadding(new Insets(16));
		Scene scene = new Scene(root, 900, 640);
		stage.setTitle("SOM Dubstep Generator (JavaFX)");
		HBox rootColumns = new HBox(12,
			                    left,
			                    	right,
			    buildBeatHistoryPane());       // NEW right column

			HBox.setHgrow(rootColumns.getChildren().get(1), Priority.ALWAYS); // center grows
			scene.setRoot(rootColumns);
		stage.setScene(scene);
		stage.show();
		
	}

	private void updateCorpusLabel() {
		int vecs = corpus.size();
		corpusInfo.setText(
				"Dataset: " + imagesInCorpus + " image" + (imagesInCorpus == 1 ? "" : "s") + ", " + vecs + " vectors");
	}
	private TitledPane buildScratchPane() {
	    // sliders
	    Slider dur   = mkSlider(0.05, 0.60, scratchParams.durSec, 0.01, "sec");
	    Slider start = mkSlider( 800, 12000, scratchParams.startHz, 100, "Hz");
	    Slider end   = mkSlider( 100,  6000, scratchParams.endHz,   50, "Hz");
	    Slider mix   = mkSlider(0, 1, scratchParams.noiseMix, 0.01, "");
	    Slider drive = mkSlider(0, 1, scratchParams.drive,    0.01, "");
	    Slider q     = mkSlider(0.3, 2.0, scratchParams.q,    0.01, "");
	    Slider level = mkSlider(0, 1, scratchParams.level,    0.01, "");

	    // bind live
	    dur.valueProperty().addListener((obs, o, v) -> scratchParams.durSec   = v.doubleValue());
	    start.valueProperty().addListener((obs, o, v) -> scratchParams.startHz= v.doubleValue());
	    end.valueProperty().addListener((obs, o, v) -> scratchParams.endHz    = v.doubleValue());
	    mix.valueProperty().addListener((obs, o, v) -> scratchParams.noiseMix = v.doubleValue());
	    drive.valueProperty().addListener((obs, o, v) -> scratchParams.drive  = v.doubleValue());
	    q.valueProperty().addListener((obs, o, v)    -> scratchParams.q       = v.doubleValue());
	    level.valueProperty().addListener((obs, o, v)-> scratchParams.level   = v.doubleValue());

	    Button test = new Button("Test Scratch");
	    test.setOnAction(e -> {
	        float[] one = com.somdubstep.audio.ScratchFX.render(com.somdubstep.audio.AudioEngine.SR, scratchParams);
	        audio.playOneShotSoon(one, 0); // audition immediately
	    });

	    GridPane gp = new GridPane();
	    gp.setHgap(8); gp.setVgap(6);
	    int r=0;
	    addRow(gp, r++, "Duration", dur);
	    addRow(gp, r++, "Sweep Start", start);
	    addRow(gp, r++, "Sweep End", end);
	    addRow(gp, r++, "Noise Mix", mix);
	    addRow(gp, r++, "Drive", drive);
	    addRow(gp, r++, "Resonance (Q)", q);
	    addRow(gp, r++, "Level", level);
	    gp.add(test, 0, r, 2, 1);

	    TitledPane tp = new TitledPane("Record Scratch", gp);
	    tp.setExpanded(false);
	    return tp;
	}

	// small helpers:
	private Slider mkSlider(double min, double max, double val, double majorTick, String units){
	    Slider s = new Slider(min, max, val);
	    s.setShowTickMarks(true);
	    s.setShowTickLabels(true);
	    s.setMajorTickUnit(majorTick);
	    s.setBlockIncrement(majorTick);
	    s.setSnapToTicks(false);
	    return s;
	}
	private void addRow(GridPane gp, int r, String label, Node node){
	    gp.add(new Label(label), 0, r);
	    gp.add(node, 1, r);
	}

	private void loadImage(Stage stage) {
		FileChooser fc = new FileChooser();
		fc.setTitle("Choose image");
		fc.getExtensionFilters()
				.add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
		File f = fc.showOpenDialog(stage);
		if (f == null)
			return;
		drawImageOnCanvas(imageCanvas, f);
		try {
			features = ImageFeatureExtractor.extract(f.getAbsolutePath(), 10);
			// After you set this.features from the selected image:
			if (features != null && features.length > 0) {
				for (float[] fv : features)
					corpus.add(fv); // add to global corpus
				imagesInCorpus++;
				updateCorpusLabel();
			}

			btnTrain.setDisable(false);
		} catch (Exception ex) {
			ex.printStackTrace();
			alert("Failed to read image: " + ex.getMessage());
		}
	}

	private void trainSOM() {
		// Choose training set
		float[][] trainData;
		if (trainLatestOnly.isSelected() && features != null && features.length > 0) {
			trainData = features; // just the last loaded image
		} else {
			// all so far
			if (corpus.isEmpty())
				return;
			trainData = corpus.toArray(new float[corpus.size()][]);
		}

		int g = (int) Math.round(gridSize.getValue());
		double lr = learningRate.getValue();

		// Init SOM if needed; otherwise keep training the existing map
		if (som == null || som.width != g || som.height != g) {
			som = new SelfOrganizingMap(g, g, 5, 12345); // you had a seeded ctor earlier
		}
		som.learningRate = (float) lr;

		btnTrain.setDisable(true);
		btnTrain.setText("🧠 Training...");
		Task<Void> task = new Task<>() {
			@Override
			protected Void call() {
				som.train(trainData, 200);
				return null;
			}

			@Override
			protected void succeeded() {
				btnTrain.setText("🧠 Train SOM");
				btnTrain.setDisable(false);
				AppFX.this.visualizeSOM(); // <— qualify it here
				btnGenerate.setDisable(false);
			}

			@Override
			protected void failed() {
				btnTrain.setText("🧠 Train SOM");
				btnTrain.setDisable(false);
				AppFX.this.alert("Training failed: " + getException().getMessage());
			}
		};
		Thread t = new Thread(task, "SOM-Train");
		t.setDaemon(true);
		t.start();
	}

//inside AppFX.java
	private void visualizeSOM() {
		if (som == null)
			return;

		GraphicsContext ctx = somCanvas.getGraphicsContext2D();
		ctx.clearRect(0, 0, somCanvas.getWidth(), somCanvas.getHeight());

		double cellW = somCanvas.getWidth() / som.width;
		double cellH = somCanvas.getHeight() / som.height;

		for (int i = 0; i < som.neurons.size(); i++) {
			var n = som.neurons.get(i);
			int x = n.x;
			int y = n.y;

			// map weight[0..2] to RGB
			int r = (int) (n.weights[0] * 255);
			int g = (int) (n.weights[1] * 255);
			int b = (int) (n.weights[2] * 255);
			Color c = Color.rgb(r, g, b);

			ctx.setFill(c);
			ctx.fillRect(x * cellW, y * cellH, cellW, cellH);

			// overlay brightness as alpha tint
			double brightness = n.weights[3];
			ctx.setFill(new Color(1, 1, 1, brightness));
			ctx.fillRect(x * cellW, y * cellH, cellW, cellH);
		}
	}

// Optional: if you show the list in a ListView elsewhere
	private void addToHistory(BeatPattern p, int bpm) {
	    if (p == null) return;
	    String defaultName = "Beat " + java.time.LocalTime.now().withNano(0);
	    beatHistory.add(0, new BeatHistoryItem(defaultName, bpm, p));
	    if (beatHistory.size() > MAX_BEAT_HISTORY) {
	        beatHistory.remove(beatHistory.size() - 1);
	    }
	}
// Ensure we have a PatternGenerator (creates one if missing)
	private void ensurePatternGenerator() {
	    if (gen == null) {
	        long seed = 12345L; // or read from your UI
	        if (som == null) {
	            throw new IllegalStateException("SOM not ready (load an image & Train first)");
	        }
	        gen = new PatternGenerator(som, seed);
	    }
	}

// --- THE method you asked for: drop-in replacement ---
	private void generatePattern() {
	    try {
	        ensurePatternGenerator();
	        int s = (steps > 0 ? steps : 32);

	        BeatPattern newPattern = gen.generate(s);
	        System.out.println("[GENERATE]\n" + newPattern.toAscii());
	        if (gridView != null) gridView.setPattern(newPattern);

	        currentPattern = newPattern;
	        pattern = newPattern;
	        if (playing.get() && seq != null) {
	            boolean withScratch = true;
	            seq.switchPattern(newPattern, withScratch, scratchParams);
	            uiSteps = currentPattern.steps();
	            uiStepSamples = seq.stepSamples();
	            // IMPORTANT: keep the rolling scheduler aligned with the new cycle length
	            nextStartSample = Math.max(nextStartSample, audio.currentSample() + lookaheadSamples);
	        }

	         addToHistory(newPattern, getCurrentBpm()); // if you implemented history

	         if (btnPlay != null) btnPlay.setDisable(false);
	    } catch (Exception ex) {
	        ex.printStackTrace();
	    }
	}

	// AppFX.java
	private javafx.scene.layout.VBox buildBeatHistoryPane() {
	    Label title = new Label("Previous Beats");
	    title.getStyleClass().add("section-title");

	    historyList = new ListView<>(beatHistory);
	    historyList.setCellFactory(lv -> new ListCell<BeatHistoryItem>() {
	        private final Label name = new Label();
	        private final Label meta = new Label();
	        private final TextArea ascii = new TextArea();
	        private final Button btnPreview = new Button("Preview");
	        private final Button btnSwap    = new Button("Swap In");
	        private final Button btnRename  = new Button("Rename");
	        private final Button btnDelete  = new Button("Delete");
	        private final HBox   buttons    = new HBox(8, btnPreview, btnSwap, btnRename, btnDelete);
	        private final VBox   root       = new VBox(6, name, meta, ascii, buttons);

	        {   // init cell UI
	            ascii.setEditable(false);
	            ascii.setWrapText(false);
	            ascii.setPrefRowCount(4);
	            ascii.setStyle("-fx-font-family: 'Courier New', monospace; -fx-font-size: 11px;");
	        }

	        @Override protected void updateItem(BeatHistoryItem it, boolean empty) {
	            super.updateItem(it, empty);
	            if (empty || it == null) { setGraphic(null); return; }

	            name.setText(it.name);
	            meta.setText("BPM " + it.bpm + " · " + java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(
	                    java.time.ZonedDateTime.ofInstant(it.createdAt, java.time.ZoneId.systemDefault())));

	            // Show ASCII pattern for quick glance (uses BeatPattern.toAscii()).
	            ascii.setText(it.pattern.toAscii()); // ← uses your method:contentReference[oaicite:2]{index=2}

	            // Wire buttons
	            btnPreview.setOnAction(e -> previewBeat(it));
	            btnSwap.setOnAction(e    -> swapBeatIn(it));
	            btnRename.setOnAction(e  -> {
	                TextInputDialog d = new TextInputDialog(it.name);
	                d.setHeaderText("Rename Beat"); d.setContentText("Name:");
	                d.showAndWait().ifPresent(newName -> { it.name = newName; // refresh
	                    int idx = getIndex();
	                    beatHistory.set(idx, it);
	                });
	            });
	            btnDelete.setOnAction(e  -> beatHistory.remove(it));

	            setGraphic(root);
	        }
	    });

	    Button exportBtn = new Button("Export Selected…");
	    exportBtn.setOnAction(e -> exportSelectedBeat());

	    VBox box = new VBox(10, title, historyList, exportBtn);
	    box.setPadding(new Insets(10));
	    VBox.setVgrow(historyList, Priority.ALWAYS);
	    return box;
	}
	// AppFX.java
	private void previewBeat(BeatHistoryItem it) {
	    if (audio == null) return;
	    // Schedule one cycle to audition, without replacing the main loop.
	    try {
	        // Local one-shot sequencer
	        Sequencer tmp = new Sequencer(audio, (int)bpm.getValue(), it.pattern,gridView.isMuted("kick"), gridView.isMuted("snare"),
					gridView.isMuted("hihat"), gridView.isMuted("bass"));
	        long start = audio.currentSample() + tmp.stepSamples(); // tiny preroll
	        tmp.scheduleCycle(start, /*muteKick*/false, /*muteSnare*/false, /*muteHiHat*/false, /*muteBass*/false);
	    } catch (Exception ex) { ex.printStackTrace(); }
	}

	private void swapBeatIn(BeatHistoryItem it) {
		if (it == null) return;
	    currentPattern = it.pattern;           // NEW
	    pattern = it.pattern;                  // NEW (keep legacy field in sync)
	    gridView.setPattern(it.pattern);  
		if (seq == null) return;
	    boolean withScratch = true;
	    seq.switchPattern(it.pattern, withScratch, scratchParams); // uses earlier method we added
	    uiSteps = currentPattern.steps();
	    uiStepSamples = seq.stepSamples();
	}
	private void exportSelectedBeat() {
	    BeatHistoryItem it = historyList.getSelectionModel().getSelectedItem();
	    if (it == null) return;
	    FileChooser fc = new FileChooser();
	    fc.setTitle("Export Pattern as JSON");
	    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
	    File out = fc.showSaveDialog(stage);
	    if (out == null) return;

	    try (java.io.FileWriter fw = new java.io.FileWriter(out)) {
	        // simple JSON like your Main writer:contentReference[oaicite:5]{index=5} (trimmed for UI)
	        StringBuilder sb = new StringBuilder();
	        sb.append("{\n  \"name\": \"").append(it.name).append("\",\n");
	        sb.append("  \"bpm\": ").append(it.bpm).append(",\n");
	        sb.append("  \"steps\": ").append(it.pattern.steps()).append(",\n");
	        sb.append("  \"tracks\": {\n");
	        int k=0;
	        for (var e : it.pattern.asMap().entrySet()) { // from BeatPattern:contentReference[oaicite:6]{index=6}
	            sb.append("    \"").append(e.getKey()).append("\": [");
	            boolean[] arr = e.getValue();
	            for (int i=0;i<arr.length;i++) { sb.append(arr[i]?"1":"0"); if (i<arr.length-1) sb.append(","); }
	            sb.append("]"); if (++k<4) sb.append(","); sb.append("\n");
	        }
	        sb.append("  }\n}\n");
	        fw.write(sb.toString());
	    } catch (Exception ex) { ex.printStackTrace(); }
	}

	private void play() {
		if (pattern == null || playing.get())
			return;
		playing.set(true);
		btnPlay.setDisable(true);
		btnStop.setDisable(false);
		audio.start();
		audio.debugKick();
		audio.debugKickInOneSecond();

		int tempo = (int) Math.round(bpm.getValue());
		if (seq == null) {
		
			
			seq = new Sequencer(audio, tempo, pattern, gridView.isMuted("kick"), gridView.isMuted("snare"),
					gridView.isMuted("hihat"), gridView.isMuted("bass"));

		}else {
			
			seq.switchPattern(pattern, withScratch, scratchParams);
			uiSteps = currentPattern.steps();
			uiStepSamples = seq.stepSamples();
		}
		
		// schedule cycles in a loop (as you already do)
		Thread loopScheduler = new Thread(() -> {
			try {
				long nextStart = audio.currentSample() + seq.prerollSamples();
				firstCycleStartWriteClock = nextStart;
				while (playing.get()) {
					// re-read mutes so they apply next cycle (optional)
					boolean mk = gridView.isMuted("kick");
					boolean ms = gridView.isMuted("snare");
					boolean mh = gridView.isMuted("hihat");
					boolean mb = gridView.isMuted("bass");

					seq.scheduleCycle(nextStart, mk, ms, mh, mb);

					// wait roughly one cycle before scheduling the next
					long sleepMs = (long) Math.max(1, seq.cycleMillis() * 0.95);
					Thread.sleep(sleepMs);
					nextStart += seq.cycleSamples();
				}
			} catch (InterruptedException ignore) {
			}
		}, "Audio-LoopScheduler");
		seq.switchPattern(pattern, withScratch, scratchParams);
		uiSteps = currentPattern.steps();
		uiStepSamples = seq.stepSamples();
		loopScheduler.setDaemon(true);
		loopScheduler.start();

		// NEW: sample-accurate playhead driven by audio.currentSample()
		playheadTimer = new javafx.animation.AnimationTimer() {
			@Override
			public void handle(long now) {
				if (firstCycleStartWriteClock <= 0) {
					gridView.highlight(-1);
					return;
				}

				// Convert WRITE clock to what the EARS will hear:
				// playback head = samples actually played out the speakers
				long playHead = audio.playbackHeadSamples();

				// The first audible step will happen when playback head reaches:
				long firstAudibleStart = firstCycleStartWriteClock; // scheduled on the write timeline
				// The device still holds some audio in its buffer; when we scheduled we were
				// ahead by that buffer.
				// So to align the UI with sound, advance by current output latency:
				long latency = audio.outputLatencySamples();
				long visualOffset = (long) Math
						.round((vizOffsetMs.getValue() / 1000.0) * com.somdubstep.audio.AudioEngine.SR);

				long aligned = playHead + latency + visualOffset;

				if (aligned < firstAudibleStart) {
					gridView.highlight(-1);
					return;
				}

				long intoCycle = (aligned - firstAudibleStart) % seq.cycleSamples();
				int step = (int) (intoCycle / seq.stepSamples());
				if (step < 0)
					step = 0;
				if (step >= pattern.steps())
					step = pattern.steps() - 1;
				gridView.highlight(step);
			}
		};
		playheadTimer.start();

		// keep references so we can stop/interrupt later
		this.loopScheduler = loopScheduler;
	}
	
	// AppFX.java
	private void handlePlay() {
	    try {
	        if (audio == null) audio = new AudioEngine();
	        audio.start();

	        if (currentPattern == null) {
	            // create something to play if user hasn’t generated yet
	            ensurePatternGenerator();
	            currentPattern = gen.generate(steps > 0 ? steps : 32);
	            if (gridView != null) gridView.setPattern(currentPattern);
	        }

	        if (seq == null) {
				seq = new Sequencer(audio, getCurrentBpm(), currentPattern, gridView.isMuted("kick"),
						gridView.isMuted("snare"), gridView.isMuted("hihat"), gridView.isMuted("bass"));
	        } else {
	            seq.setBpm(getCurrentBpm());
	        }

	        // ---- continuous scheduler ----
	        lookaheadSamples = Math.max(seq.stepSamples(), 2048); // ~ one step of preroll
	        long now = audio.currentSample();
	        nextStartSample = now + lookaheadSamples;

	        // schedule the very first cycle immediately
	        seq.scheduleCycle(nextStartSample,
	                gridView.isMuted("kick"), gridView.isMuted("snare"),
	                gridView.isMuted("hihat"), gridView.isMuted("bass"));
	        nextStartSample += seq.cycleSamples();
	        uiSteps = currentPattern.steps();                              // NEW
	        uiStepSamples = seq.stepSamples();                              // NEW
	        transportZeroSamples = audio.playbackHeadSamples()             // NEW
	                + audio.outputLatencySamples();                         // NEW
	        animate = true;                                                 // NEW
	        startPlayheadTimer(); 
	        // run a tiny scheduler that keeps posting new cycles as we approach them
	        schedulerRunning = true;
	        if (schedExec == null || schedExec.isShutdown()) {
	            schedExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
	                Thread t = new Thread(r, "SeqScheduler");
	                t.setDaemon(true);
	                return t;
	            });
	        }
	        schedExec.scheduleAtFixedRate(() -> {
	            if (!schedulerRunning || !playing.get() || seq == null || audio == null) return;
	            long playhead = audio.playbackHeadSamples() + audio.outputLatencySamples();
	            if (nextStartSample - playhead <= lookaheadSamples) {
	                seq.scheduleCycle(nextStartSample, false, false, false, false);
	                nextStartSample += seq.cycleSamples();
	            }
	        }, 0, 5, java.util.concurrent.TimeUnit.MILLISECONDS);

	        playing.set(true);
	        // (optional) UI button states:
	        // btnPlay.setDisable(true); btnStop.setDisable(false);

	    } catch (Exception ex) {
	        ex.printStackTrace();
	    }
	}

	// AppFX.java
	private void handleStop() {
	    playing.set(false);
	    schedulerRunning = false;
	    if (schedExec != null) { schedExec.shutdownNow(); schedExec = null; }
	    if (audio != null) audio.stop();

	    stopPlayheadTimer();                    // <— add this
	    btnPlay.setDisable(false); btnStop.setDisable(true);
	}



	private void stop1() {
		if (!playing.get())
			return;
		playing.set(false);
		btnPlay.setDisable(false);
		btnStop.setDisable(true);

		if (playheadTimer != null) {
			playheadTimer.stop();
			playheadTimer = null;
		}
		if (loopScheduler != null) {
			loopScheduler.interrupt();
			loopScheduler = null;
		}

		audio.stop();
		gridView.highlight(-1);
		firstCycleStartWriteClock = -1;
	}

	private void renderSom() {
		if (som == null)
			return;
		GraphicsContext g = somCanvas.getGraphicsContext2D();
		g.setFill(Color.BLACK);
		g.fillRect(0, 0, somCanvas.getWidth(), somCanvas.getHeight());
		double cw = somCanvas.getWidth() / som.width;
		double ch = somCanvas.getHeight() / som.height;
		for (int i = 0; i < som.size(); i++) {
			int x = i % som.width;
			int y = i / som.width;
			float[] w = som.getNuronWith(x, y).getWeights();
			int rr = (int) Math.max(0, Math.min(255, (int) (w[0] * 255)));
			int gg = (int) Math.max(0, Math.min(255, (int) (w[1] * 255)));
			int bb = (int) Math.max(0, Math.min(255, (int) (w[2] * 255)));
			double bright = Math.max(0, Math.min(1, w[3]));
			g.setFill(Color.rgb(rr, gg, bb));
			g.fillRect(Math.round(x * cw), Math.round(y * ch), Math.round(cw), Math.round(ch));
			g.setFill(new Color(1, 1, 1, bright));
			g.fillRect(Math.round(x * cw), Math.round(y * ch), Math.round(cw), Math.round(ch));
		}
	}

	private static TitledPane titled(String title, javafx.scene.Node content) {
		TitledPane tp = new TitledPane(title, content);
		tp.setCollapsible(false);
		return tp;
	}

	private static Slider slider(double min, double max, double val) {
		Slider s = new Slider(min, max, val);
		s.setShowTickMarks(true);
		s.setShowTickLabels(true);
		return s;
	}

	private static void drawImageOnCanvas(Canvas canvas, File imgFile) {
		try {
			Image img = new Image(imgFile.toURI().toString());
			double w = canvas.getWidth(), h = canvas.getHeight();
			GraphicsContext g = canvas.getGraphicsContext2D();
			g.setFill(Color.BLACK);
			g.fillRect(0, 0, w, h);
			double scale = Math.min(w / img.getWidth(), h / img.getHeight());
			double dx = (w - img.getWidth() * scale) / 2.0;
			double dy = (h - img.getHeight() * scale) / 2.0;
			g.drawImage(img, dx, dy, img.getWidth() * scale, img.getHeight() * scale);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void alert(String msg) {
		Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK).showAndWait());
	}

	private Accordion buildInstrumentAccordion() {
		// ensure audio != null (as we discussed previously)
		if (audio == null) {
			try {
				audio = new com.somdubstep.audio.AudioEngine();
			} catch (Exception ex) {
				alert("Audio engine unavailable: " + ex.getMessage());
				return new Accordion();
			}
		}

		Accordion acc = new Accordion();

		// KICK
		TitledPane kickPane = new TitledPane();
		kickPane.setText("Kick");
		GridPane k = new GridPane();
		k.setHgap(10);
		k.setVgap(6);
		Slider kVol = slider(0, 1, 0.9), kF0 = slider(30, 200, 120), kF1 = slider(10, 120, 35),
				kDrop = slider(0.03, 0.6, 0.20), kBodyCut = slider(200, 4000, 1200), kBodyQ = slider(0.3, 2.5, 1.2),
				kClick = slider(0, 1, 0.25), kDrive = slider(0, 1, 0.15), kA = slider(0.0005, 0.02, 0.001),
				kD = slider(0.03, 0.4, 0.15), kS = slider(0, 1, 0.0), kR = slider(0.02, 0.3, 0.08);
		addRow(k, 0, "Volume", kVol);
		addRow(k, 1, "Fundamental Freq (Hz)", kF0);
		addRow(k, 2, "Final Freq (Hz)", kF1);
		addRow(k, 3, "Pitch Drop Duration (s)", kDrop);
		addRow(k, 4, "Body LPF Cutoff (Hz)", kBodyCut);
		addRow(k, 5, "Body LPF Resonance (Q)", kBodyQ);
		addRow(k, 6, "Click Mix", kClick);
		addRow(k, 7, "Drive", kDrive);
		addRow(k, 8, "Amp Env Attack (s)", kA);
		addRow(k, 9, "Amp Env Decay (s)", kD);
		addRow(k, 10, "Amp Env Sustain", kS);
		addRow(k, 11, "Amp Env Release (s)", kR);

		kickPane.setContent(k);
		acc.getPanes().add(kickPane);

		// SNARE
		TitledPane snarePane = new TitledPane();
		snarePane.setText("Snare");
		GridPane s = new GridPane();
		s.setHgap(10);
		s.setVgap(6);
		Slider sVol=slider(0,1,0.55),
			       sA=slider(0.0005,0.02,0.001), sD=slider(0.03,0.4,0.11), sS=slider(0,1,0.0), sR=slider(0.02,0.3,0.06),
			       sColor=slider(0,1,0.6),
			       sTone=slider(100,600,190), sMix=slider(0,1,0.18),
			       sSnapF=slider(1000,8000,3500), sSnapQ=slider(0.3,3.0,1.0),
			       sBodyCut=slider(200,2000,800), sBodyQ=slider(0.3,2.0,0.9),
			       sDrive=slider(0,1,0.2);
		addRow(s, 0, "Volume", sVol);
        addRow(s, 1, "Amp Env Attack (s)", sA);
        addRow(s, 2, "Amp Env Decay (s)", sD);
        addRow(s, 3, "Amp Env Sustain", sS);
        addRow(s, 4, "Amp Env Release (s)", sR);
        addRow(s, 5, "Noise Color", sColor);
        addRow(s, 6, "Tone Freq (Hz)", sTone);
        addRow(s, 7, "Tone Mix", sMix);
        addRow(s, 8, "Snap Freq (Hz)", sSnapF);
        addRow(s, 9, "Snap Q", sSnapQ);
        addRow(s, 10, "Body Cutoff (Hz)", sBodyCut);
        addRow(s, 11, "Body Q", sBodyQ);
        addRow(s, 12, "Drive", sDrive);
        
		snarePane.setContent(s);
		acc.getPanes().add(snarePane);

		// HI-HAT
		TitledPane hhPane = new TitledPane();
		hhPane.setText("Hi-hat");
		GridPane h = new GridPane();
		h.setHgap(10);
		h.setVgap(6);
		Slider hVol=slider(0,1,0.28),
			       hA=slider(0.0002,0.02,0.0008), hD=slider(0.005,0.2,0.045), hS=slider(0,1,0.0), hR=slider(0.005,0.2,0.02),
			       hBright=slider(2000,16000,9000), hMetal=slider(0,1,0.55),
			       hHpCut=slider(2000,12000,6000), hHpQ=slider(0.3,2.0,0.7),
			       hDrive=slider(0,1,0.15);
		addRow(h, 0, "Volume", hVol);
		addRow(h, 1, "Amp Env Attack (s)", hA);
		addRow(h, 2, "Amp Env Decay (s)", hD);
		addRow(h, 3, "Amp Env Sustain", hS);
		addRow(h, 4, "Amp Env Release (s)", hR);
		addRow(h, 5, "Brightness (Hz)", hBright);
		addRow(h, 6, "Metallic Mix", hMetal);
		addRow(h, 7, "HPF Cutoff (Hz)", hHpCut);
		addRow(h, 8, "HPF Q", hHpQ);
		addRow(h, 9, "Drive", hDrive);
		hhPane.setContent(h);
		acc.getPanes().add(hhPane);

		// BASS
		TitledPane bassPane = new TitledPane();
		bassPane.setText("Bass");
		GridPane b = new GridPane();
		b.setHgap(10);
		b.setVgap(6);
		Slider bVol = slider(0, 1, 0.7), bFreq = slider(30, 200, 55), bShape = slider(0, 1, 0.6),
				bDetune = slider(-30, 30, 0), bSub = slider(0, 1, 0.5), bCut = slider(50, 5000, 800),
				bRes = slider(0.2, 2.5, 0.8), bEnvAmt = slider(0, 3000, 600), bLfoRate = slider(0.1, 12, 2.0),
				bLfoAmt = slider(0, 3000, 300), bA = slider(0.0005, 0.05, 0.002), bD = slider(0.01, 1.0, 0.10),
				bS = slider(0, 1, 0.0), bR = slider(0.01, 1.0, 0.05), bDrive = slider(0, 1, 0.2);
		addRow(b, 0, "Volume", bVol);
		addRow(b, 1, "Fundamental Freq (Hz)", bFreq);
		addRow(b, 2, "Wave Shape", bShape);
		addRow(b, 3, "Detune (cents)", bDetune);
		addRow(b, 4, "Sub Mix", bSub);
		addRow(b, 5, "LPF Cutoff (Hz)", bCut);
		addRow(b, 6, "LPF Resonance (Q)", bRes);
		addRow(b, 7, "Filter Env Amt (Hz)", bEnvAmt);
		addRow(b, 8, "Wobble Rate (Hz)", bLfoRate);
		addRow(b, 9, "Wobble Amt (Hz)", bLfoAmt);
		addRow(b, 10, "Amp Env Attack (s)", bA);
		addRow(b, 11, "Amp Env Decay (s)", bD);
		addRow(b, 12, "Amp Env Sustain", bS);
		addRow(b, 13, "Amp Env Release (s)", bR);
		addRow(b, 14, "Drive", bDrive);

		bassPane.setContent(b);
		acc.getPanes().add(bassPane);
		acc.getPanes().add(buildScratchPane());

		// Wire up
		Runnable applyKick = () -> {
			double value = kVol.getValue();
			System.out.println("Kick vol: " + value);
			audio.setKickParams(kF0.getValue(), kF1.getValue(), kDrop.getValue(),
					value, kBodyCut.getValue(), kBodyQ.getValue(), kClick.getValue(), kDrive.getValue(),
					kA.getValue(), kD.getValue(), kS.getValue(), kR.getValue());
		};
		Runnable applySnare = () -> {
			double value = sVol.getValue();
			System.out.println("Snare vol: " + value);
			audio.setSnareParams(
				    sA.getValue(), sD.getValue(), sS.getValue(), sR.getValue(),
				    value,
				    sColor.getValue(),
				    sTone.getValue(), sMix.getValue(),
				    sSnapF.getValue(), sSnapQ.getValue(),
				    sBodyCut.getValue(), sBodyQ.getValue(),
				    sDrive.getValue()
				);
		};
		Runnable applyHat = () -> audio.setHiHatParams(
			    hVol.getValue(), hA.getValue(), hD.getValue(), hS.getValue(), hR.getValue(),
			    hBright.getValue(), hMetal.getValue(),
			    hHpCut.getValue(), hHpQ.getValue(),
			    hDrive.getValue()
			);
		Runnable applyBass = () -> {
			double value = bVol.getValue();
			System.out.println("Bass vol: " + value);
			audio.setBassParams(bFreq.getValue(), bShape.getValue(), bDetune.getValue(),
					bSub.getValue(), value, bCut.getValue(), bRes.getValue(), bEnvAmt.getValue(),
					bLfoRate.getValue(), bLfoAmt.getValue(), bA.getValue(), bD.getValue(), bS.getValue(), bR.getValue(),
					bDrive.getValue());
		};

		kVol.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kF0.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kF1.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kDrop.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kD.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kClick.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kDrive.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kBodyCut.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kBodyQ.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kA.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kS.valueProperty().addListener((o, ov, nv) -> applyKick.run());
		kR.valueProperty().addListener((o, ov, nv) -> applyKick.run());

		sVol.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sTone.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sMix.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sColor.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sDrive.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sSnapF.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sSnapQ.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sBodyCut.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sBodyQ.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sA.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sD.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sS.valueProperty().addListener((o, ov, nv) -> applySnare.run());
		sR.valueProperty().addListener((o, ov, nv) -> applySnare.run());
			
		hVol.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hBright.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hMetal.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hDrive.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hHpCut.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hHpQ.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hA.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hD.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hS.valueProperty().addListener((o, ov, nv) -> applyHat.run());
		hR.valueProperty().addListener((o, ov, nv) -> applyHat.run());
			
		bVol.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bD.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bFreq.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bShape.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bCut.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bDrive.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bDetune.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bSub.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bRes.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bEnvAmt.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bLfoRate.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bLfoAmt.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bA.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bS.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		bR.valueProperty().addListener((o, ov, nv) -> applyBass.run());
		// initialize engine with current UI values
		
		applyKick.run();
		
	applySnare.run();

applyHat.run();

		applyBass.run();
		return acc;
	}



	@Override
	public void stop() {
		if (!playing.get())
			return;
		playing.set(false);
		btnPlay.setDisable(false);
		btnStop.setDisable(true);

		if (playheadTimer != null) {
			playheadTimer.stop();
			playheadTimer = null;
		}
		if (loopScheduler != null) {
			loopScheduler.interrupt();
			loopScheduler = null;
		}

		audio.stop();
		gridView.highlight(-1);
		firstCycleStartWriteClock = -1;
	}

	public static void main(String[] args) {
		launch(args);
	}
}
