package com.somdubstep.audio;

import java.util.Random;

public class SnareNode implements InstrumentNode {
    private final Random rng = new Random();

    // Noise & body
    private double noiseColor = 0.6;     // 0=dark .. 1=bright
    private double tone = 190;           // Hz body
    private double toneMix = 0.18;       // 0..1

    // Envelopes
    private final ADSR ampEnv = new ADSR(AudioEngine.SR);
    private double volume = 0.55;

    // Filters
    private final Biquad snapBPF = new Biquad(Biquad.Type.BPF, AudioEngine.SR); // “crack”
    private double snapFreq = 3500;      // Hz
    private double snapQ = 1.0;
    private final Biquad bodyLP = new Biquad(Biquad.Type.LPF, AudioEngine.SR);  // thump
    private double bodyCut = 800;        // Hz
    private double bodyQ = 0.9;

    // Drive
    private double drive = 0.2;

    // Internal
    private double bodyPhase = 0;

    public SnareNode(){
        ampEnv.A=0.001; ampEnv.D=0.11; ampEnv.S=0.0; ampEnv.R=0.06;
        snapBPF.setCutoff(snapFreq); snapBPF.setQ(snapQ);
        bodyLP.setCutoff(bodyCut); bodyLP.setQ(bodyQ);
    }

    public void setParams(double decayA, double decayD, double decayS, double decayR,
                          double volume,
                          double noiseColor,
                          double toneFreq, double toneMix,
                          double snapFreq, double snapQ,
                          double bodyCut, double bodyQ,
                          double drive){
        ampEnv.A = Math.max(0.0005, decayA);
        ampEnv.D = Math.max(0.01, decayD);
        ampEnv.S = clamp01(decayS);
        ampEnv.R = Math.max(0.01, decayR);
        this.volume = clamp01(volume);
        this.noiseColor = clamp01(noiseColor);
        this.tone = Math.max(80, toneFreq);
        this.toneMix = clamp01(toneMix);
        this.snapFreq = Math.max(500, snapFreq); this.snapQ = Math.max(0.3, snapQ);
        this.bodyCut = Math.max(200, bodyCut);   this.bodyQ = Math.max(0.3, bodyQ);
        this.drive = clamp01(drive);
        snapBPF.setCutoff(this.snapFreq); snapBPF.setQ(this.snapQ);
        bodyLP.setCutoff(this.bodyCut);   bodyLP.setQ(this.bodyQ);
    }

    @Override
    public int renderEvent(float[] out, int sr, int velocity) {
        ampEnv.start();
        int len = Math.min(out.length, (int)(0.40 * sr));
        double vAmp = (velocity/127.0) * volume;

        double dphi = 2*Math.PI*tone/sr;
        double lp = 0, alpha = 0.08 + 0.9*noiseColor; // noise tilt

        for(int i=0;i<len;i++){
            double env = ampEnv.next();

            // colored noise
            double white = (rng.nextDouble()*2 - 1);
            lp += alpha * (white - lp);
            double colored = (1.0 - noiseColor)*lp + noiseColor*white;

            // snap (bandpass the noise a bit)
            double snap = snapBPF.process(colored);

            // body tone
            bodyPhase += dphi;
            double body = Math.sin(bodyPhase);
            body = bodyLP.process(body);

            // mix
            double sig = snap*(1.0 - toneMix) + body*toneMix;

            // drive
            if (drive > 0){
                double k = 1.0 + 10.0*drive;
                sig = Math.tanh(k*sig);
            }

            out[i] += (float)(sig * env * vAmp);
        }
        return len;
    }

    private static double clamp01(double v){ return v<0?0:v>1?1:v; }
}
