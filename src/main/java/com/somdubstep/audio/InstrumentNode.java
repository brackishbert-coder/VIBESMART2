
package com.somdubstep.audio;

public interface InstrumentNode {
    int renderEvent(float[] out, int sampleRate, int velocity);
}
