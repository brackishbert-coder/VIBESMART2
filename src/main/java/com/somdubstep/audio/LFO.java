package com.somdubstep.audio;

public final class LFO {
    private final int sr; private double freq=2.0, phase=0;
    public LFO(int sr){ this.sr=sr; }
    public void setFreq(double f){ freq=Math.max(0.01,f); }
    public double next(){ phase += 2*Math.PI*freq/sr; if (phase>1e9) phase-=1e9; return Math.sin(phase); }
}
