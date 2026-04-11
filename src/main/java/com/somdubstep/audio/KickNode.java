package com.somdubstep.audio;

public class KickNode implements InstrumentNode {
    // Core pitch drop
    private double f0 = 120, f1 = 35;     // Hz
    private double dropDur = 0.20;        // s (pitch envelope time)

    // Amp envelope
    private final ADSR ampEnv = new ADSR(AudioEngine.SR);
    private double volume = 0.9;          // 0..1

    // Body filter (adds weight / punch)
    private final Biquad bodyLP = new Biquad(Biquad.Type.LPF, AudioEngine.SR);
    private double bodyCut = 1200;        // Hz
    private double bodyQ   = 1.2;

    // Extras
    private double click = 0.25;          // 0..1 short HF transient
    private double drive = 0.15;          // 0..1 tanh shaper

    // Internal
    private double phase = 0;

    public KickNode(){
        // default ADSR for a punchy kick
        ampEnv.A = 0.001; ampEnv.D = 0.15; ampEnv.S = 0.0; ampEnv.R = 0.08;
        bodyLP.setCutoff(bodyCut); bodyLP.setQ(bodyQ);
    }

    /** Set all parameters (call from AudioEngine). */
    public void setParams(double f0, double f1, double dropDur,
                          double vol, double bodyCut, double bodyQ,
                          double click, double drive,
                          double A, double D, double S, double R){
        this.f0 = Math.max(10, f0);
        this.f1 = Math.max(1, Math.min(this.f0, f1));
        this.dropDur = Math.max(0.01, dropDur);
        this.volume = clamp01(vol);
        this.bodyCut = Math.max(100, bodyCut);
        this.bodyQ = Math.max(0.2, bodyQ);
        this.click = clamp01(click);
        this.drive = clamp01(drive);
        bodyLP.setCutoff(this.bodyCut); bodyLP.setQ(this.bodyQ);
        ampEnv.A=A; ampEnv.D=D; ampEnv.S=S; ampEnv.R=R;
    }

    @Override
    public int renderEvent(float[] out, int sr, int velocity) {
        ampEnv.start();
        int len = Math.min(out.length, (int)(0.60 * sr));
        double vel = (velocity/127.0);
        double vAmp = volume * vel;

        for (int i=0;i<len;i++){
            double t = i/(double)sr;

            // pitch sweeps from f0 -> f1 over dropDur
            double f = f0 * Math.pow(f1/f0, Math.min(1.0, t/dropDur));
            phase += 2*Math.PI*f/sr;
            double body = Math.sin(phase);

            // short HF click (8 ms)
            double clickEnv = Math.max(0, 1.0 - t/0.008);
            double hf = Math.sin(2*Math.PI*6000*t) * click * clickEnv;

            // filter body for weight
            double y = bodyLP.process(body + hf);

            // drive
            if (drive > 0){
                double k = 1.0 + 10.0*drive;
                y = Math.tanh(k*y);
            }

            out[i] += (float)(y * ampEnv.next() * vAmp);
        }
        return len;
    }

    private static double clamp01(double v){ return v<0?0:v>1?1:v; }
}
