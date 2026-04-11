package com.somdubstep.audio;

public final class Biquad {
    public enum Type { LPF, HPF, BPF }
    private Type type; private int sr;
    private double cutoff = 1000, Q = 0.707;
    private double a0,a1,a2,b1,b2, z1=0, z2=0;

    public Biquad(Type type, int sr){ this.type = type; this.sr = sr; compute(); }
    public void set(Type t){ this.type=t; compute(); }
    public void setCutoff(double hz){ cutoff=Math.max(10,hz); compute(); }
    public void setQ(double q){ Q=Math.max(0.1,q); compute(); }
    public void reset(){ z1=z2=0; }
    public static volatile boolean BYPASS = false; 
    public double process(double x){
    	if (BYPASS) return x;  
    	double y = a0*x + z1;
        z1 = a1*x + z2 - b1*y;
        z2 = a2*x - b2*y;
        return y;
    }

private void compute(){
    double w0   = 2 * Math.PI * cutoff / sr;
    double cosw = Math.cos(w0), sinw = Math.sin(w0);
    double alpha = sinw / (2 * Q);

    double b0n, b1n, b2n, a0d, a1d, a2d;

    switch (type) {
        case LPF -> {
            b0n = (1 - cosw) / 2.0;
            b1n = 1 - cosw;
            b2n = (1 - cosw) / 2.0;
            a0d = 1 + alpha;
            a1d = -2 * cosw;
            a2d = 1 - alpha;
        }
        case HPF -> {
            b0n = (1 + cosw) / 2.0;
            b1n = -(1 + cosw);
            b2n = (1 + cosw) / 2.0;
            a0d = 1 + alpha;
            a1d = -2 * cosw;
            a2d = 1 - alpha;
        }
        default /* BPF (constant skirt) */ -> {
            b0n =  sinw / 2.0;
            b1n =  0.0;
            b2n = -sinw / 2.0;
            a0d = 1 + alpha;
            a1d = -2 * cosw;
            a2d = 1 - alpha;
        }
    }

    // normalize so a0 = 1 in the Direct Form I/II implementation
    a0 = b0n / a0d;
    a1 = b1n / a0d;
    a2 = b2n / a0d;
    b1 = a1d / a0d;
    b2 = a2d / a0d;
}

}
