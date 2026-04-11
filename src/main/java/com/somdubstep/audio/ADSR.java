package com.somdubstep.audio;

/** Simple per-note ADSR. Call start() before render; call next() for each sample. */
public final class ADSR {
    public double A = 0.002, D = 0.08, S = 0.4, R = 0.08; // seconds, sustain 0..1
    private int sr; private double env = 0, gate = 0; private Phase phase = Phase.IDLE;
    private enum Phase { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

    public ADSR(int sr){ this.sr = sr; }

    public void start(){ phase = Phase.ATTACK; gate = 1.0; }      // key on
    public void release(){ phase = Phase.RELEASE; gate = 0.0; }   // key off (not used for one-shots)
    public void reset(){ phase = Phase.IDLE; env = 0; gate = 0; }

    public double next(){
        switch (phase){
            case ATTACK -> {
                env += 1.0 / Math.max(1, (int)(A * sr));
                if (env >= 1.0){ env = 1.0; phase = Phase.DECAY; }
            }
            case DECAY -> {
                double step = (1.0 - S) / Math.max(1, (int)(D * sr));
                env -= step;
                if (env <= S){ env = S; phase = Phase.SUSTAIN; }
            }
            case SUSTAIN -> { /* hold at S until release() */ }
            case RELEASE -> {
                double step = S / Math.max(1, (int)(R * sr));
                env -= step;
                if (env <= 0){ env = 0; phase = Phase.IDLE; }
            }
            case IDLE -> { env = 0; }
        }
        return env * gate;
    }
}
