
package com.somdubstep.pattern;

import com.somdubstep.som.SelfOrganizingMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PatternGenerator {
    private final SelfOrganizingMap som;
    private final Random rng;
    public PatternGenerator(SelfOrganizingMap som, long seed){
        this.som = som; this.rng = new Random(seed);
    }

    public BeatPattern generate(int steps){
        BeatPattern p = new BeatPattern(steps);
        fill(p.kick, "kick");
        fill(p.snare, "snare");
        fill(p.hihat, "hihat");
        fill(p.bass, "bass");
        return p;
    }

    private void fill(boolean[] pattern, String instrument){
        List<Integer> indices = new ArrayList<>();
        for(int i=0;i<som.size();i++){
        	int x = i % som.width;
            int y = i / som.width;
            float[] w = som.getNuronWith(x, y).getWeights();
            switch (instrument){
                case "kick": if(w[3] > 0.3f) indices.add(i); break;
                case "snare": if(w[4] > 0.4f) indices.add(i); break;
                case "hihat": if(w[2] > 0.5f) indices.add(i); break;
                case "bass": if(w[0] > 0.4f) indices.add(i); break;
            }
        }
        if(indices.isEmpty()){
            for(int i=0;i<som.size();i++) indices.add(i);
        }
        for(int i=0;i<pattern.length;i++){
            int idx = (int)Math.floor((i/(double)pattern.length) * indices.size());
            int x = i % som.width;
            int y = i / som.width;
            float[] w = som.getNuronWith(x, y).getWeights();
            double prob = 0;
            switch (instrument){
                case "kick":  prob = w[3]*0.7 + ((i%4==0)?0.3:0); break;
                case "snare": prob = w[4]*0.5 + ((i%8==4)?0.4:0); break;
                case "hihat": prob = w[2]*0.6 + 0.2; break;
                case "bass":  prob = w[0]*0.4 + ((i%2==0)?0.2:0); break;
            }
            pattern[i] = rng.nextDouble() < Math.min(1.0, Math.max(0.0, prob));
        }
    }
}
