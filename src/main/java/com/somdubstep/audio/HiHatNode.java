package com.somdubstep.audio;

import java.util.Random;

public class HiHatNode implements InstrumentNode {
    private final Random rng = new Random();

    // Envelope
    private final ADSR ampEnv = new ADSR(AudioEngine.SR);
    private double volume = 0.28;

    // Tone shaping
    private double brightness = 9000;    // Hz (mod pitch / shimmer)
    private double metallic = 0.55;      // 0..1 add ring
    private double drive = 0.15;         // 0..1

    private final Biquad hpf = new Biquad(Biquad.Type.HPF, AudioEngine.SR);
    private double hpCut = 6000;         // Hz
    private double hpQ   = 0.7;

    // Internal
    private double ringPhase = 0;

    public HiHatNode(){
        ampEnv.A=0.0008; ampEnv.D=0.045; ampEnv.S=0.0; ampEnv.R=0.02;
        hpf.setCutoff(hpCut); hpf.setQ(hpQ);
    }

    public void setParams(double vol, double A, double D, double S, double R,
                          double brightness, double metallic,
                          double hpCut, double hpQ,
                          double drive){
        this.volume = clamp01(vol);
        ampEnv.A = Math.max(0.0002, A);
        ampEnv.D = Math.max(0.005, D);
        ampEnv.S = clamp01(S);
        ampEnv.R = Math.max(0.005, R);
        this.brightness = Math.max(2000, brightness);
        this.metallic = clamp01(metallic);
        this.hpCut = Math.max(1000, hpCut);
        this.hpQ = Math.max(0.3, hpQ);
        this.drive = clamp01(drive);
        hpf.setCutoff(this.hpCut); hpf.setQ(this.hpQ);
    }

    @Override
    public int renderEvent(float[] out, int sr, int velocity) {
        ampEnv.start();
        int len = Math.min(out.length, (int)(0.15 * sr));
        double vAmp = (velocity/127.0) * volume;

        double ringHz = brightness * 0.5;
        double dphi = 2*Math.PI*ringHz/sr;

        for (int i=0;i<len;i++){
            double env = ampEnv.next();

            // noise
            double n = (rng.nextDouble()*2 - 1);

            // ring / metallic
            ringPhase += dphi;
            double ring = Math.sin(ringPhase);

            double sig = n*(1.0 - metallic) + ring*metallic;

            // high-pass to keep it crisp
            sig = hpf.process(sig);

            if (drive > 0){
                double k = 1.0 + 12.0*drive;
                sig = Math.tanh(k*sig);
            }

            out[i] += (float)(sig * env * vAmp);
        }
        return len;
    }

    private static double clamp01(double v){ return v<0?0:v>1?1:v; }
}
