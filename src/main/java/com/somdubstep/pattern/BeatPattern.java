
package com.somdubstep.pattern;

import java.util.LinkedHashMap;
import java.util.Map;

public class BeatPattern {
    public final boolean[] kick, snare, hihat, bass;
    public BeatPattern(int steps){
        kick = new boolean[steps];
        snare = new boolean[steps];
        hihat = new boolean[steps];
        bass = new boolean[steps];
    }
    public int steps(){ return kick.length; }

    public Map<String, boolean[]> asMap(){
        Map<String, boolean[]> m = new LinkedHashMap<>();
        m.put("kick", kick); m.put("snare", snare); m.put("hihat", hihat); m.put("bass", bass);
        return m;
    }

    public String toAscii(){
        StringBuilder sb = new StringBuilder();
        for(var e : asMap().entrySet()){
            sb.append(String.format("%-6s", e.getKey()+":"));
            boolean[] arr = e.getValue();
            for(int i=0;i<arr.length;i++){
                sb.append( arr[i] ? (i%4==0 ? "X" : "x") : (i%4==0 ? "." : "-") );
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
