package com.somdubstep.audio;

public class BassNode implements InstrumentNode {
    // Osc & shaping
    private double freq = 55, shape = 0.6;     // 0=sine..1=saw
    private double subMix = 0.5;               // add sub sine one octave down
    private double detune = 0.0;               // second osc detune in cents

    // Amp & filter
    private double volume = 0.7;
    private final ADSR ampEnv;
    private final ADSR filEnv;
    private final Biquad lpf;
    private double lpCut = 800, lpRes = 0.8;   // cutoff Hz, resonance (Q)
    private double envAmt = 600;               // filter env amount (Hz)

    // Modulation
    private final LFO lfo;
    private double wobbleRate = 2.0, wobbleAmt = 300; // LFO→cutoff

    // Drive
    private double drive = 0.2;                // 0..1

    private double ph0=0, ph1=0, phSub=0;

    public BassNode(){
        int sr = AudioEngine.SR;
        ampEnv = new ADSR(sr);
        filEnv = new ADSR(sr);
        filEnv.A = 0.005; filEnv.D = 0.10; filEnv.S = 0.0; filEnv.R = 0.05;
        lpf = new Biquad(Biquad.Type.LPF, sr);
        lpf.setCutoff(lpCut); lpf.setQ(lpRes);
        lfo = new LFO(sr);
    }

    public void setParams(double freq, double shape, double detune,
                          double subMix, double volume,
                          double lpCut, double lpRes, double envAmt,
                          double wobbleRate, double wobbleAmt,
                          double A, double D, double S, double R,
                          double drive){
        this.freq = Math.max(20, freq);
        this.shape = clamp01(shape);
        this.detune = detune;        // ± cents
        this.subMix = clamp01(subMix);
        this.volume = clamp01(volume);
        this.lpCut = Math.max(40, lpCut);
        this.lpRes = Math.max(0.2, lpRes);
        this.envAmt = Math.max(0, envAmt);
        this.wobbleRate = Math.max(0.01, wobbleRate);
        this.wobbleAmt = Math.max(0, wobbleAmt);
        this.drive = clamp01(drive);

        lpf.setCutoff(this.lpCut); lpf.setQ(this.lpRes);
        lfo.setFreq(this.wobbleRate);
        ampEnv.A=A; ampEnv.D=D; ampEnv.S=S; ampEnv.R=R;
    }

    @Override
    public int renderEvent(float[] out, int sr, int velocity) {
        // begin envelopes per hit
        ampEnv.start(); filEnv.start();

        int len = Math.min(out.length, (int)(0.70 * sr));
        double vAmp = (velocity/127.0) * volume;

        double dphi0 = 2*Math.PI*freq/sr;
        double dphi1 = 2*Math.PI*(freq * Math.pow(2, detune/1200.0))/sr;
        double dphiSub = 2*Math.PI*(freq/2)/sr;

        for (int i=0;i<len;i++){
            double envA = ampEnv.next();
            double envF = filEnv.next();

            // 2 osc blend
            double s0 = oscMix(ph0, shape); ph0 += dphi0;
            double s1 = oscMix(ph1, shape); ph1 += dphi1;
            double sSub = Math.sin(phSub);   phSub += dphiSub;
            double sig = 0.5*(s0+s1) + subMix*sSub;

            // Filter: base + env + LFO wobble
            double lfoVal = lfo.next(); // -1..1
            double cut = lpCut + envAmt*envF + wobbleAmt*lfoVal;
            if (cut < 40) cut = 40;
            lpf.setCutoff(cut);

            double y = lpf.process(sig);

            // Drive (soft clip)
            double k = 1.0 + 8.0*drive;
            y = Math.tanh(k * y);

            out[i] += (float)(y * envA * vAmp);
        }
        // natural release (one-shots): no release() call required
        return len;
    }

    private static double oscMix(double ph, double shape){
        double sine = Math.sin(ph);
        double saw = 2.0*((ph/(2*Math.PI))%1.0)-1.0;
        return (1.0-shape)*sine + shape*saw;
    }
    private static double clamp01(double v){ return v<0?0:v>1?1:v; }
}
