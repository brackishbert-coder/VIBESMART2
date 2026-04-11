
package com.somdubstep.util;

import java.util.*;

public class ArgParser {
    private final Map<String, List<String>> map = new HashMap<>();
    public ArgParser(String[] args) {
        String current = null;
        for (String a : args) {
            if (a.startsWith("--")) {
                current = a.substring(2);
                map.putIfAbsent(current, new ArrayList<>());
            } else {
                if (current == null) continue;
                map.get(current).add(a);
            }
        }
    }
    public boolean has(String key){ return map.containsKey(key); }
    public String getOne(String key, String def){
        List<String> v = map.get(key);
        return (v==null || v.isEmpty()) ? def : v.get(0);
    }
    public int getInt(String key, int def){
        try { return Integer.parseInt(getOne(key, Integer.toString(def))); } catch(Exception e){ return def; }
    }
    public double getDouble(String key, double def){
        try { return Double.parseDouble(getOne(key, Double.toString(def))); } catch(Exception e){ return def; }
    }
    public long getLong(String key, long def){
        try { return Long.parseLong(getOne(key, Long.toString(def))); } catch(Exception e){ return def; }
    }
    public List<String> getAll(String key){ return map.getOrDefault(key, List.of()); }
    public static void printHelp(){
        System.out.println(
"Usage:\n" +
"  --image <path>             Image file to analyze\n" +
"  --bpm <int>                Tempo (default 140)\n" +
"  --complexity <s|m|c>       simple|medium|complex (default medium)\n" +
"  --steps <int>              override number of steps (default 0=auto by complexity)\n" +
"  --export <files...>        any .png and/or .json files to write\n" +
"  --play                     play audio\n" +
"  --no-train                 skip training SOM (use initial weights)\n" +
"  --seed <long>              RNG seed\n" +
"  --help                     show this help\n"
        );
    }
}
