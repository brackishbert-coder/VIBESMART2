package com.somdubstep.audio;

import java.util.Random;

public final class ScratchFX {
    public static final class Params {
        public double durSec = 0.180;   // total length
        public double startHz = 8000;   // sweep start
        public double endHz   = 1200;   // sweep end
        public double noiseMix = 0.8;   // 0..1  (1=noise, 0=tonal rub)
        public double drive = 0.25;     // 0..1
        public double q = 0.9;          // band-pass resonance
        public double level = 0.9;      // 0..1
    }

    public static float[] render(int sr, Params p) {
        int n = Math.max(1, (int)Math.round(p.durSec * sr));
        float[] out = new float[n];

        Biquad bp = new Biquad(Biquad.Type.BPF, sr);
        bp.setQ(p.q);

        Random rng = new Random();
        for (int i=0; i<n; i++) {
            double t01 = i / (double)(n - 1 + 1e-9); // 0..1
            double hz = p.startHz * Math.pow(p.endHz / p.startHz, t01);
            bp.setCutoff(hz);

            // tonal “rub” chirp
            double rubEnv = Math.sin(Math.PI * t01);
            double rubHz = 200 + 1600 * t01;
            double rub = Math.sin(2*Math.PI * rubHz * (i/(double)sr)) * rubEnv;

            // colored noise through BPF
            double white = (rng.nextDouble()*2 - 1);
            double noise = bp.process(white);

            double sig = p.noiseMix * noise + (1.0 - p.noiseMix) * rub;

            // soft drive
            double k = 1.0 + 10.0 * p.drive;
            sig = Math.tanh(k * sig);

            out[i] = (float)(sig * p.level);
        }
        return out;
    }
}

