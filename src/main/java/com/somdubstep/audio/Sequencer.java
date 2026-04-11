package com.somdubstep.audio;

// Sequencer.java
import java.util.concurrent.atomic.AtomicReference;

import com.somdubstep.pattern.BeatPattern;

public class Sequencer {
	private final AudioEngine engine;
	private final AtomicReference<BeatPattern> current = new AtomicReference<>();
	private volatile int bpm;
	private volatile int stepSamples;
	private volatile int cycleSamples;

	// generation gate: bump this to cancel any in-flight scheduling
	private volatile long gen = 0;

	// mutes you already have (if any)
	public static boolean muteKick;
	public static boolean muteSnare;
	public static boolean muteHiHat;
	public static boolean muteBass;
	
	private int prerollSamples;

	public Sequencer(AudioEngine engine, int bpm, BeatPattern pattern,
            boolean muteKick, boolean muteSnare, boolean muteHiHat, boolean muteBass){
this.engine=engine; this.bpm=bpm; this.current.set(pattern);
Sequencer.muteKick=muteKick; Sequencer.muteSnare=muteSnare; Sequencer.muteHiHat=muteHiHat; Sequencer.muteBass=muteBass;
double stepSec = 60.0 / bpm / 4.0; // 16th
this.stepSamples = (int)Math.round(stepSec * AudioEngine.SR);
this.cycleSamples = this.stepSamples * pattern.steps();
this.prerollSamples = this.stepSamples;
}
	public void setBpm(int newBpm) {
		this.bpm = newBpm;
		this.stepSamples = (int) Math.round(AudioEngine.SR * (60.0 / newBpm) / 4.0);
		this.cycleSamples = stepSamples * current.get().steps();
	}

    public int stepSamples() { return stepSamples; }

    /** Samples for one full pattern cycle. */
    public int cycleSamples() { return cycleSamples; }

    /** Preroll samples before first scheduled event. */
    public long prerollSamples() { return prerollSamples; }
    public void schedulingLoop() {
        long myGen = gen;
        long start = engine.currentSample() + prerollSamples;
        while (myGen == gen) {
            scheduleCycle(start, muteKick, muteSnare, muteHiHat, muteBass);
            start += cycleSamples;
        }
        // exits immediately if gen changes
    }
    /** Milliseconds for one full pattern cycle. */
    public double cycleMillis() { return (cycleSamples * 1000.0) / AudioEngine.SR; }


	// ----- single-cycle scheduler; guarded by 'gen' -----
	public void scheduleCycle(long startSample, boolean muteKick, boolean muteSnare, boolean muteHiHat,
			boolean muteBass) {
		long myGen = gen; // capture
		BeatPattern p = current.get(); // always read latest here

		// post each step; abort immediately if a swap happened
		for (int i = 0; i < p.steps(); i++) {
			if (gen != myGen)
				return; // swap occurred → stop posting
			long t = startSample + (long) i * stepSamples;

			if (p.kick[i] && !muteKick)
				engine.post(new NoteEvent(NoteEvent.Instrument.KICK, t, 110));
			if (p.snare[i] && !muteSnare)
				engine.post(new NoteEvent(NoteEvent.Instrument.SNARE, t, 100));
			if (p.hihat[i] && !muteHiHat)
				engine.post(new NoteEvent(NoteEvent.Instrument.HIHAT, t, 92));
			if (p.bass[i] && !muteBass)
				engine.post(new NoteEvent(NoteEvent.Instrument.BASS, t, 120));
		}
	}

	// ----- hot swap with optional scratch -----
	public void switchPattern(BeatPattern newPattern, boolean withScratch,
			com.somdubstep.audio.ScratchFX.Params scratchParams) {
		// 1) cancel any in-flight posting immediately
		gen++; // invalidate any schedule in progress

		// 2) clear anything already queued / ringing
		engine.clearAllScheduledAndActives();

		// 3) install the new pattern atomically and update cycle length
		current.set(newPattern);
		this.cycleSamples = stepSamples * newPattern.steps();

		// 4) choose start time
		long start = engine.currentSample() + stepSamples; // tiny preroll

		// 5) optional scratch one-shot placed just before downbeat
		if (withScratch && scratchParams != null) {
			float[] scratch = com.somdubstep.audio.ScratchFX.render(AudioEngine.SR, scratchParams);
			engine.playOneShotAt(start, scratch);
			start += scratch.length; // start new loop right after scratch
		}

		// 6) post the very first cycle of the NEW pattern
		scheduleCycle(start, muteKick, muteSnare, muteHiHat, muteBass);
	}

	public void scheduleOnce() {
		int steps = current.get().steps();
		double stepSec = 60.0 / bpm / 4.0;
		int stepSamples = (int) Math.round(stepSec * AudioEngine.SR);
		long start = engine.currentSample() + stepSamples;
		for (int i = 0; i < steps; i++) {
			long t = start + (long) i * stepSamples;
			if (current.get().kick[i] && !muteKick)
				engine.post(new NoteEvent(NoteEvent.Instrument.KICK, t, 110));
			if (current.get().snare[i] && !muteSnare)
				engine.post(new NoteEvent(NoteEvent.Instrument.SNARE, t, 100));
			if (current.get().hihat[i] && !muteHiHat)
				engine.post(new NoteEvent(NoteEvent.Instrument.HIHAT, t, 90));
			if (current.get().bass[i] && !muteBass)
				engine.post(new NoteEvent(NoteEvent.Instrument.BASS, t, 120));
		}
	}	

}
