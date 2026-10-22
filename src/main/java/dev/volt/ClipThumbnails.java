package dev.volt;

import com.mojang.blaze3d.platform.NativeImage;
import dev.volt.recording.Ffmpeg;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Lazy previews, capped at 16 GPU textures. Work begins only when the library is viewed. */
public final class ClipThumbnails {
    private final VoltClient v;
    private final Map<String, Identifier> loaded = new LinkedHashMap<>(16, .75f, true);
    private final Map<String,Float> aspects=new HashMap<>();
    public float aspect(Clip clip){return aspects.getOrDefault(clip.id,16f/9);}
    private final Map<String,Long> pending = new ConcurrentHashMap<>();
    private volatile long generation;
    public void suspend(){generation++;pending.clear();}
    public ClipThumbnails(VoltClient v) { this.v = v; }
    public Identifier get(Clip clip) { return get(clip,-1); }
    public Identifier getFrame(Clip clip,int index){return get(clip,Math.clamp(index,0,5));}
    private Identifier get(Clip clip,int frame) {
        String key=clip.id+(frame<0?"":"/strip_"+frame);
        Identifier existing = loaded.get(key);
        long token=generation;
        if (existing != null || pending.putIfAbsent(key,token)!=null) return existing;
        v.io.submit(() -> {
            try {
                if(token!=generation){pending.remove(key,token);return;}
                Path directory = v.library.directory().resolve("thumbs"); Files.createDirectories(directory);
                Path path = directory.resolve(clip.id + (frame<0?".640.png":".strip-"+frame+".png"));
                if (!Files.isRegularFile(path)) Ffmpeg.run(List.of(v.config.ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
                    "-ss",String.valueOf(frame<0?0:Math.max(0,clip.seconds-.1)*frame/5),"-threads","1",
                    "-i", clip.path(v.library.directory()).toString(), "-frames:v", "1", "-vf", frame<0?"scale=640:-2":"scale=240:-2", "-threads", "1", path.toString()), directory.resolve("thumbnail.log"));
                if(token!=generation){pending.remove(key,token);return;}
                NativeImage image;
                try (InputStream in = Files.newInputStream(path)) { image = NativeImage.read(in); }
                Minecraft.getInstance().execute(() -> {
                    if(token!=generation){image.close();pending.remove(key,token);return;}
                    Identifier id = Identifier.fromNamespaceAndPath("volt", "clip/" + key);
                    Minecraft.getInstance().getTextureManager().register(id, new dev.volt.ui.SmoothTexture("Volt clip preview", image)); loaded.put(key, id); aspects.put(clip.id,(float)image.getWidth()/image.getHeight());
                    while (loaded.size() > 16) { String oldest = loaded.keySet().iterator().next(); Minecraft.getInstance().getTextureManager().release(loaded.remove(oldest)); }
                    pending.remove(key,token);
                });
            } catch (Exception ignored) { /* Keep failed IDs pending until restart to avoid repeated decoder work. */ }
        });
        return null;
    }
}
