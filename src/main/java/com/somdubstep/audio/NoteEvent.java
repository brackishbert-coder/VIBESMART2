
package com.somdubstep.audio;

public class NoteEvent {
    public enum Instrument { KICK, SNARE, HIHAT, BASS }
    public final Instrument instrument;
    public final long startSample;
    public final int velocity; // 0..127
    public NoteEvent(Instrument inst, long startSample, int velocity){
        this.instrument=inst; this.startSample=startSample; this.velocity=velocity;
    }
}
