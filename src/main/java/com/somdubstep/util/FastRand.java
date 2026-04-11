
package com.somdubstep.util;

public class FastRand {
    private long s;
    public FastRand(long seed){ s=seed==0?0x9E3779B97F4A7C15L:seed; }
    public int nextInt(){ s ^= (s<<13); s ^= (s>>>7); s ^= (s<<17); return (int)s; }
    public float nextFloat(){ return (nextInt()>>>1)/ (float)(1<<30); }
    public double nextDouble(){ return (nextInt()>>>1)/ (double)(1<<30); }
}
