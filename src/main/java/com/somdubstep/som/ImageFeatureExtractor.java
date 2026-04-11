
package com.somdubstep.som;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageFeatureExtractor {
    public static float[][] extract(String path, int block) throws Exception {
        BufferedImage img = ImageIO.read(new File(path));
        if(img==null) throw new IllegalArgumentException("Cannot read image: "+path);
        int W = img.getWidth(), H = img.getHeight();
        List<float[]> list = new ArrayList<>();
        for(int y=0; y<=H-block; y+=block){
            for(int x=0; x<=W-block; x+=block){
                list.add(blockFeatures(img, x, y, block));
            }
        }
        float[][] out = new float[list.size()][];
        for(int i=0;i<out.length;i++) out[i]=list.get(i);
        return out;
    }

    private static float[] blockFeatures(BufferedImage img, int sx, int sy, int B){
        double r=0,g=0,b=0, bright=0;
        int n=B*B;
        double[] vals = new double[n];
        int idx=0;
        for(int y=0;y<B;y++){
            for(int x=0;x<B;x++){
                int rgb = img.getRGB(sx+x, sy+y);
                int rr = (rgb>>16)&255, gg=(rgb>>8)&255, bb=rgb&255;
                r+=rr; g+=gg; b+=bb;
                double br = (rr+gg+bb)/3.0;
                bright += br;
                vals[idx++] = br;
            }
        }
        double mean = bright / n;
        double var=0;
        for(double v: vals){ double dv=v-mean; var+=dv*dv; }
        var/=n;
        double std = Math.sqrt(var);

        return new float[]{
            (float)(r/(255.0*n)),
            (float)(g/(255.0*n)),
            (float)(b/(255.0*n)),
            (float)(bright/(255.0*n)),
            (float)(std/255.0)
        };
    }
}
