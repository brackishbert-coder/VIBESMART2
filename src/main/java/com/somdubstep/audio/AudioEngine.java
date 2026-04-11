
package com.somdubstep.audio;

import javax.sound.sampled.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AudioEngine implements AutoCloseable {
	public static final int SR = 44100;
	private final SourceDataLine line;
	private final ConcurrentLinkedQueue<NoteEvent> queue = new ConcurrentLinkedQueue<>();
	private volatile boolean running = false;

	private final KickNode kick = new KickNode();
	private final SnareNode snare = new SnareNode();
	private final HiHatNode hihat = new HiHatNode();
	private final BassNode bass = new BassNode();
	private final float[] scratch = new float[4096];
	private Thread thread;
	private long renderedSamples = 0;
	private AudioFormat fmt;
	private final float[] temp = new float[8192];
	private float[] mix;
	private Thread audioThread;

	private final java.util.concurrent.locks.ReentrantLock ioLock = new java.util.concurrent.locks.ReentrantLock();

	// AudioEngine.java
	// Add near other fields:
	private final Object activeLock = new Object();

	private static final int BLOCK = 1024;
	private static final int MAX_EVENT_SAMPLES = (int) (0.9 * SR); // ~0.9s hits

	private static final class Active {
		final long startSample; // absolute write-clock start
		final float[] buf; // rendered one-shot
		int pos; // samples already consumed

		Active(long startSample, float[] buf) {
			this.startSample = startSample;
			this.buf = buf;
			this.pos = 0;
		}
	}

	private final java.util.List<Active> active = new java.util.ArrayList<>();
	private long lastLogMs = 0;

	public void clearAllScheduledAndActives() {
		ioLock.lock();
		try {
			queue.clear();
			synchronized (activeLock) {
				active.clear();
			}
		} finally {
			ioLock.unlock();
		}
	}

	public void playOneShotAt(long startSample, float[] mono) {
		ioLock.lock();
		try {
			synchronized (activeLock) {
				active.add(new Active(startSample, java.util.Arrays.copyOf(mono, mono.length)));
			}
		} finally {
			ioLock.unlock();
		}
	}

	// Convenience: “now + preroll”
	public void playOneShotSoon(float[] mono, int prerollSamples) {
		long t = currentSample() + Math.max(0, prerollSamples);
		playOneShotAt(t, mono);
	}

	public AudioEngine() throws Exception {
		fmt = new AudioFormat(SR, 16, 2, true, false); // 2 channels, PCM_SIGNED, little-endian
		line = AudioSystem.getSourceDataLine(fmt);
		line.open(fmt, 4096 * 4);
		Biquad.BYPASS = false;
		try {
			javax.sound.sampled.Control c = line.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN);
			if (c instanceof javax.sound.sampled.FloatControl fc) {
				// Clamp to the control’s legal range; 0 dB is usually audible without clipping.
				float target = Math.min(0f, fc.getMaximum());
				fc.setValue(target);
			}
		} catch (IllegalArgumentException ignored) {
			// MASTER_GAIN not supported; safe to ignore
		}

		// KICK defaults (punchy)
		// KICK (punchy)
		setKickParams(120, 35, 0.20, // f0, f1, dropDur (s)
				0.99, 1200, 1.2, // volume, bodyCut, bodyQ
				0.25, 0.15, // click, drive
				0.001, 0.15, 0.0, 0.08 // ADSR A,D,S,R (s)
		);

		// SNARE (snap + body)
		setSnareParams(0.001, 0.11, 0.0, 0.06, // A,D,S,R
				0.99, // volume
				0.60, // noiseColor
				190, 0.18, // toneFreq, toneMix
				3500, 1.0, // snapFreq, snapQ
				800, 0.9, // bodyCut, bodyQ
				0.20 // drive
		);

		// HIHAT (crisp)
		setHiHatParams(0.99, // volume
				0.0008, 0.045, 0.0, 0.02, // A,D,S,R
				9000, 0.55, // brightness, metallic
				6000, 0.7, // HP cut, HP Q
				0.15 // drive
		);

		// BASS (audible wobble)
		setBassParams(60.0, // freq (Hz)
				0.5, // shape (0=sine..1=saw)
				0.02, // detune
				0.30, // subMix
				0.99, // volume
				200.0, // lpCut (Hz)
				0.90, // lpRes (Q)
				0.50, // envAmt
				4.0, // wobbleRate (Hz)
				0.60, // wobbleAmt
				0.005, // A
				0.15, // D
				0.60, // S
				0.25, // R
				0.25 // drive
		);

	}

	public long currentSample() {
		return renderedSamples;
	}

	public void post(NoteEvent e) {
		queue.add(e);
	}

	public void stop() {
		running = false;
		if (audioThread != null) {
			try {
				audioThread.join(500);
			} catch (InterruptedException ignored) {
			}
			audioThread = null;
		}
		line.stop(); // <<< stop the device cleanly
		line.flush();
	}

	public void start() {
		if (running)
			return;
		running = true;
		line.start(); // *** ensure device actually runs
		audioThread = new Thread(this::renderLoop, "Audio-Render");
		audioThread.setDaemon(true);
		audioThread.start();
	}

	public long playbackHeadSamples() {
	    return line != null ? line.getLongFramePosition() : 0L;
	}

	public int outputLatencySamples() {
	    if (line == null) return (int)(0.030 * SR); // ~30 ms fallback
	    int frameSize = fmt.getFrameSize() <= 0 ? 2 : fmt.getFrameSize();
	    int pendingBytes  = line.getBufferSize() - line.available();
	    int pendingFrames = pendingBytes / frameSize;
	    return pendingFrames; // mono: frames==samples; for stereo you’d multiply by channels
	}
	// === In AudioEngine.java ===
	// AudioEngine.java

	private void renderLoop() {
	    final int N = BLOCK;                       // keep one block size
	    final byte[] outBytes = new byte[N * 4];   // 2 channels * 2 bytes (stereo PCM16)
	    final float[] mix     = new float[N];

	    if (!line.isActive()) {
	        try { line.start(); } catch (Exception ignored) {}
	    }

	    while (running) {
	        // ---- heartbeat once/sec ----
	        long now = System.currentTimeMillis();
	        if (now - lastLogMs > 1000) {
	            System.out.println("[AUDIO] rs=" + renderedSamples +
	                               " queue=" + queue.size() +
	                               " active=" + active.size() +
	                               " line.active=" + line.isActive() +
	                               " avail=" + line.available());
	            lastLogMs = now;
	        }

	        java.util.Arrays.fill(mix, 0f);
	        final long blockStart = renderedSamples;
	        final long blockEnd   = blockStart + N;

	        // ---- pull near-term events (≤ one block ahead) and render one-shots ----
	        for (NoteEvent e; (e = queue.peek()) != null; ) {
	            if (e.startSample >= blockEnd + N) break;  // defer far-future events
	            queue.poll();

	            float[] tmp = new float[MAX_EVENT_SAMPLES];
	            int len = switch (e.instrument) {
	                case KICK  -> kick.renderEvent(tmp, SR, e.velocity);
	                case SNARE -> snare.renderEvent(tmp, SR, e.velocity);
	                case HIHAT -> hihat.renderEvent(tmp, SR, e.velocity);
	                case BASS  -> bass.renderEvent(tmp, SR, e.velocity);
	            };
	            if (len <= 0) continue;
	            float[] one = (len == tmp.length) ? tmp : java.util.Arrays.copyOf(tmp, len);
	            synchronized (activeLock) { active.add(new Active(e.startSample, one)); }
	        }

	        // ---- mix overlap of actives into this block (bounds-safe) ----
	        synchronized (activeLock) {
	            for (int idx = active.size() - 1; idx >= 0; idx--) {
	                Active a = active.get(idx);
	                int remain = a.buf.length - a.pos;
	                if (remain <= 0) { active.remove(idx); continue; }

	                long absSrcStart = a.startSample + a.pos;

	                int localStart = (absSrcStart <= blockStart) ? 0 : (int)Math.min(N, absSrcStart - blockStart);
	                int srcStart   = (absSrcStart <= blockStart) ? (int)Math.min(remain, blockStart - absSrcStart) : 0;
	                int canMix     = Math.min(N - localStart, remain - srcStart);

	                if (canMix > 0) {
	                    int dst = localStart, src = a.pos + srcStart;
	                    for (int i = 0; i < canMix; i++) mix[dst++] += a.buf[src++];
	                    a.pos += srcStart + canMix;
	                }
	                if (a.pos >= a.buf.length) active.remove(idx);
	            }
	        }

	        // ---- optional: block RMS to prove there’s signal ----
	        /*
	        double sum = 0;
	        for (int i = 0; i < N; i++) sum += mix[i] * mix[i];
	        double rms = Math.sqrt(sum / N);
	        if (now - lastLogMs > 1000) System.out.printf("[AUDIO] rms=%.6f%n", rms);
	        */

	        // ---- soft-clip and write: DUPLICATE MONO → STEREO correctly ----
	        for (int i = 0, j = 0; i < N; i++) {
	            float s = (float)Math.tanh(0.9 * mix[i]);
	            int v = Math.max(-32768, Math.min(32767, Math.round(s * 32767f)));
	            // L
	            outBytes[j++] = (byte)(v & 0xFF);
	            outBytes[j++] = (byte)((v >>> 8) & 0xFF);
	            // R
	            outBytes[j++] = (byte)(v & 0xFF);
	            outBytes[j++] = (byte)((v >>> 8) & 0xFF);
	        }

	        line.write(outBytes, 0, outBytes.length);
	        renderedSamples += N;  // frames (per-channel samples)
	    }
	}


	// KICK
	public void setKickParams(double f0, double f1, double dropDur, double volume, double bodyCut, double bodyQ,
			double click, double drive, double A, double D, double S, double R) {
		kick.setParams(f0, f1, dropDur, volume, bodyCut, bodyQ, click, drive, A, D, S, R);
	}

	// SNARE
	public void setSnareParams(double A, double D, double S, double R, double volume, double noiseColor,
			double toneFreq, double toneMix, double snapFreq, double snapQ, double bodyCut, double bodyQ,
			double drive) {
		snare.setParams(A, D, S, R, volume, noiseColor, toneFreq, toneMix, snapFreq, snapQ, bodyCut, bodyQ, drive);
	}

	// HI-HAT
	public void setHiHatParams(double volume, double A, double D, double S, double R, double brightness,
			double metallic, double hpCut, double hpQ, double drive) {
		hihat.setParams(volume, A, D, S, R, brightness, metallic, hpCut, hpQ, drive);
	}

	public void setBassParams(double freq, double shape, double detune, double subMix, double volume, double lpCut,
			double lpRes, double envAmt, double wobbleRate, double wobbleAmt, double A, double D, double S, double R,
			double drive) {
		bass.setParams(freq, shape, detune, subMix, volume, lpCut, lpRes, envAmt, wobbleRate, wobbleAmt, A, D, S, R,
				drive);
	}

	public void debugKick() {
		post(new NoteEvent(NoteEvent.Instrument.KICK, currentSample() + SR, 120));
	}

	// AudioEngine.java
	public void debugKickInOneSecond() {
		post(new NoteEvent(NoteEvent.Instrument.KICK, currentSample() + SR, 120));
	}

//In AudioEngine.java
//Assume you already have: private SourceDataLine line; private AudioFormat fmt;
//And SR = sample rate

	@Override
	public void close() {
		stop();
		line.flush();
		line.close();
	}
}
