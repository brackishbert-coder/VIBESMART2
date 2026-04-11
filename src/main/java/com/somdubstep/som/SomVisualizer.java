
package com.somdubstep.som;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SomVisualizer {
    public static void saveAsPng(SelfOrganizingMap som, int width, int height, String path) throws Exception {
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float cw = width/(float)som.width;
        float ch = height/(float)som.height;
        for(int i=0;i<som.size();i++){
            int x = i % som.width;
            int y = i / som.width;
            
            float[] w = som.getNuronWith(x, y).getWeights();
            int rr = clamp((int)(w[0]*255));
            int gg = clamp((int)(w[1]*255));
            int bb = clamp((int)(w[2]*255));
            float bright = Math.min(1f, Math.max(0f, w[3]));
            g.setColor(new Color(rr,gg,bb));
            g.fillRect(Math.round(x*cw), Math.round(y*ch), Math.round(cw), Math.round(ch));
            g.setColor(new Color(1f,1f,1f, bright));
            g.fillRect(Math.round(x*cw), Math.round(y*ch), Math.round(cw), Math.round(ch));
        }
        g.dispose();
        ImageIO.write(bi, "PNG", new File(path));
    }
    private static int clamp(int v){ return Math.max(0, Math.min(255, v)); }
}
