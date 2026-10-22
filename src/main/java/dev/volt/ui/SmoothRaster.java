package dev.volt.ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryUtil;

/** Scale-aware antialiased UI assets. Cached, never a full-screen CPU repaint each frame. */
public final class SmoothRaster {
    private record Asset(Identifier id, int width, int height, long bytes) { }
    private static final Map<String, Asset> CACHE = new LinkedHashMap<>(128, .75f, true);
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final Font BASE = loadFont();
    private static long serial, bytes;
    private static Font loadFont() {
        try (var in = SmoothRaster.class.getResourceAsStream("/assets/volt/font/ui.ttf")) { return Font.createFont(Font.TRUETYPE_FONT, in); }
        catch (Exception e) { return new Font(Font.SANS_SERIF, Font.PLAIN, 12); }
    }
    public static int width(String text, int size, boolean bold) {
        return (int) Math.ceil(BASE.deriveFont(bold ? Font.BOLD : Font.PLAIN, size).getStringBounds(text, METRICS).getWidth());
    }
    private static int scale(GuiGraphics g) {
        double density = Math.abs(g.pose().m00()) * Minecraft.getInstance().getWindow().getGuiScale();
        return Math.clamp((int) Math.ceil(density * 1.5), 2, 6);
    }
    private static Asset asset(String key, int w, int h, int scale, Consumer<Graphics2D> painter) {
        Asset existing = CACHE.get(key);
        if (existing != null) return existing;
        BufferedImage raster = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = raster.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.scale(scale, scale); painter.accept(g); g.dispose();
        int[] argb = raster.getRGB(0, 0, raster.getWidth(), raster.getHeight(), null, 0, raster.getWidth());
        for (int i = 0; i < argb.length; i++) { int c = argb[i]; argb[i] = (c & 0xff00ff00) | (c >> 16 & 255) | ((c & 255) << 16); }
        NativeImage image = new NativeImage(raster.getWidth(), raster.getHeight(), false);
        MemoryUtil.memIntBuffer(image.getPointer(), argb.length).put(argb);
        Identifier id = Identifier.fromNamespaceAndPath("volt", "smooth/" + serial++);
        Minecraft.getInstance().getTextureManager().register(id, new SmoothTexture("Volt antialiased UI", image));
        Asset result = new Asset(id, w, h, (long) argb.length * 4); CACHE.put(key, result); bytes += result.bytes;
        while (CACHE.size() > 384 || bytes > 32L * 1024 * 1024) {
            String oldest = CACHE.keySet().iterator().next(); Asset removed = CACHE.remove(oldest); bytes -= removed.bytes;
            Minecraft.getInstance().getTextureManager().release(removed.id);
        }
        return result;
    }
    public static void text(GuiGraphics g, String text, int x, int y, int size, int color, boolean bold) {
        if (text.isEmpty()) return;
        int s = scale(g), w = Math.max(1, width(text, size, bold)) + 4, h = size * 2 + 4;
        String key = "t:" + size + ":" + color + ":" + bold + ":" + s + ":" + text;
        Asset asset = asset(key, w, h, s, canvas -> {
            Font f = BASE.deriveFont(bold ? Font.BOLD : Font.PLAIN, size); canvas.setFont(f); canvas.setColor(new Color(color, true));
            canvas.drawString(text, 2, 2 + canvas.getFontMetrics().getAscent());
        });
        g.blit(asset.id, x - 2, y - 2, x - 2 + w, y - 2 + h, 0, 1, 0, 1);
    }
    public static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 1 || h < 1) return;
        int s = scale(g);
        Asset asset = asset("r:" + color + ":" + s, 32, 32, s, canvas -> { canvas.setColor(new Color(color, true)); canvas.fillRoundRect(0, 0, 32, 32, 16, 16); });
        int corner = Math.min(8, Math.min(w, h) / 2);
        int[] xs = {x, x + corner, x + w - corner, x + w}, ys = {y, y + corner, y + h - corner, y + h};
        float[] uv = {0, .25f, .75f, 1};
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
            if (xs[col+1] > xs[col] && ys[row+1] > ys[row]) g.blit(asset.id, xs[col], ys[row], xs[col+1], ys[row+1], uv[col], uv[col+1], uv[row], uv[row+1]);
    }
    /** Small vector paths rasterized once at the active screen density. */
    public static void icon(GuiGraphics g,String name,int x,int y,int size,int color){
        int density=scale(g);
        Asset a=asset("i:"+name+":"+color+":"+density,24,24,density,c->{
            c.setColor(new Color(color,true));c.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float p=new java.awt.geom.Path2D.Float();
            switch(name){
                case "select"->{c.drawOval(3,3,18,18);}
                case "home"->{p.moveTo(3,10);p.lineTo(12,3);p.lineTo(21,10);p.lineTo(19,10);p.lineTo(19,21);p.lineTo(14,21);p.lineTo(14,14);p.lineTo(10,14);p.lineTo(10,21);p.lineTo(5,21);p.lineTo(5,10);p.closePath();c.draw(p);}
                case "clips"->{c.drawRoundRect(4,7,16,14,2,2);c.drawLine(4,12,20,12);c.drawLine(4,7,7,3);c.drawLine(10,7,13,3);c.drawLine(16,7,19,3);}
                case "folder"->{p.moveTo(3,7);p.lineTo(10,7);p.lineTo(12,10);p.lineTo(22,10);p.lineTo(19,21);p.lineTo(3,21);p.closePath();c.draw(p);c.drawLine(3,10,3,5);c.drawLine(3,5,10,5);}
                case "user"->{c.drawOval(8,3,8,8);c.drawArc(4,14,16,14,0,180);}
                case "settings"->{c.drawOval(5,5,14,14);c.drawOval(9,9,6,6);for(int i=0;i<8;i++){double a1=i*Math.PI/4;c.drawLine((int)(12+8*Math.cos(a1)),(int)(12+8*Math.sin(a1)),(int)(12+10*Math.cos(a1)),(int)(12+10*Math.sin(a1)));}}
                case "search"->{c.drawOval(3,3,12,12);c.drawLine(14,14,21,21);}
                case "star"->{for(int i=0;i<10;i++){double a1=-Math.PI/2+i*Math.PI/5,r=i%2==0?10:4.5;float px=(float)(12+r*Math.cos(a1)),py=(float)(12+r*Math.sin(a1));if(i==0)p.moveTo(px,py);else p.lineTo(px,py);}p.closePath();c.draw(p);}
                case "play"->{p.moveTo(7,4);p.lineTo(20,12);p.lineTo(7,20);p.closePath();c.fill(p);}
                case "pause"->{c.fillRoundRect(6,4,4,16,1,1);c.fillRoundRect(14,4,4,16,1,1);}
                case "link"->{c.rotate(-Math.PI/4,12,12);c.drawRoundRect(4,2,16,9,7,7);c.drawRoundRect(4,13,16,9,7,7);c.drawLine(12,8,12,16);}
                case "more"->{for(int i=0;i<3;i++)c.fillOval(4+i*7,10,3,3);}
                case "close"->{c.drawLine(6,6,18,18);c.drawLine(18,6,6,18);}
                case "back"->{c.drawLine(18,12,5,12);c.drawLine(5,12,11,6);c.drawLine(5,12,11,18);}
                case "next"->{c.drawLine(6,12,19,12);c.drawLine(19,12,13,6);c.drawLine(19,12,13,18);}
                case "down"->{c.drawLine(6,9,12,15);c.drawLine(12,15,18,9);}
                case "check"->{c.drawLine(4,12,9,17);c.drawLine(9,17,20,6);}
                case "record"->{c.drawOval(3,3,18,18);c.fillOval(7,7,10,10);}
                case "mic"->{c.drawRoundRect(9,2,6,13,6,6);c.drawArc(5,7,14,12,180,180);c.drawLine(12,19,12,23);}
                case "mute"->{p.moveTo(3,9);p.lineTo(7,9);p.lineTo(12,5);p.lineTo(12,19);p.lineTo(7,15);p.lineTo(3,15);p.closePath();c.draw(p);c.drawLine(17,9,22,15);c.drawLine(22,9,17,15);}
                case "game"->{c.drawRoundRect(2,7,20,13,7,7);c.drawLine(6,13,10,13);c.drawLine(8,11,8,15);c.fillOval(15,11,2,2);c.fillOval(18,14,2,2);}
                case "grid"->{for(int i=0;i<4;i++)c.drawRoundRect(3+(i%2)*11,3+(i/2)*11,7,7,1,1);}
                case "list"->{for(int i=0;i<3;i++){c.drawLine(8,5+i*7,21,5+i*7);c.fillOval(2,4+i*7,2,2);}}
                case "upload"->{c.drawLine(12,17,12,3);c.drawLine(6,9,12,3);c.drawLine(18,9,12,3);c.drawLine(3,16,3,21);c.drawLine(3,21,21,21);c.drawLine(21,21,21,16);}
                case "cut"->{c.drawOval(3,3,6,6);c.drawOval(3,15,6,6);c.drawLine(8,8,20,20);c.drawLine(8,16,20,4);}
                case "clock"->{c.drawOval(3,3,18,18);c.drawLine(12,6,12,12);c.drawLine(12,12,16,15);}
                case "auto"->{p.moveTo(13,2);p.lineTo(4,14);p.lineTo(11,14);p.lineTo(10,23);p.lineTo(21,9);p.lineTo(14,9);p.closePath();c.fill(p);}
                case "refresh"->{c.drawArc(3,3,18,18,30,280);c.drawLine(20,3,20,9);c.drawLine(20,9,14,9);}
                default->{c.drawOval(3,3,18,18);c.drawLine(12,10,12,17);c.fillOval(11,6,2,2);}
            }
        });
        g.blit(a.id,x,y,x+size,y+size,0,1,0,1);
    }
    public static void clear() {
        for (Asset asset : CACHE.values()) Minecraft.getInstance().getTextureManager().release(asset.id);
        CACHE.clear(); bytes = 0;
    }
}
