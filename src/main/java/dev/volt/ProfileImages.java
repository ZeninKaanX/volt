package dev.volt;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.*;

/** Imported profile assets stay on disk locally. Only video is uploaded when sharing. */
public final class ProfileImages {
    public static final Identifier AVATAR = Identifier.fromNamespaceAndPath("volt", "custom/avatar");
    public static final Identifier BANNER = Identifier.fromNamespaceAndPath("volt", "custom/banner");
    public volatile boolean avatarReady, bannerReady;
    private boolean loading;
    private final Identifier[] defaults = new Identifier[11];
    private final VoltClient v;
    private final Path root;
    public ProfileImages(VoltClient v) { this.v = v; root = v.configPath.getParent().resolve("volt"); }
    public void load() {
        if (loading) return; loading = true;
        v.io.submit(() -> {
            for (int i = 0; i < 11; i++) {
                int index = i;
                try (InputStream input = Minecraft.getInstance().getResourceManager().open(Identifier.fromNamespaceAndPath("volt", "textures/avatars/" + i + ".png"))) {
                    BufferedImage source = ImageIO.read(input);
                    BufferedImage small = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = small.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setClip(new java.awt.geom.Ellipse2D.Double(0, 0, 256, 256));
                    int side = Math.min(source.getWidth(), source.getHeight());
                    int x = (source.getWidth() - side) / 2, y = (source.getHeight() - side) / 2;
                    g.drawImage(source, 0, 0, 256, 256, x, y, x + side, y + side, null); g.dispose();
                    ByteArrayOutputStream encoded = new ByteArrayOutputStream(); ImageIO.write(small, "PNG", encoded);
                    NativeImage image = NativeImage.read(new ByteArrayInputStream(encoded.toByteArray()));
                    Minecraft.getInstance().execute(() -> {
                        Identifier id = Identifier.fromNamespaceAndPath("volt", "profile/default_" + index);
                        Minecraft.getInstance().getTextureManager().register(id, new dev.volt.ui.SmoothTexture("Volt avatar", image)); defaults[index] = id;
                    });
                } catch (Exception ignored) { }
            }
            try { if (Files.isRegularFile(root.resolve("avatar.png"))) register(root.resolve("avatar.png"), false); } catch (Exception ignored) { }
            try { if (Files.isRegularFile(root.resolve("banner.png"))) register(root.resolve("banner.png"), true); } catch (Exception ignored) { }
        });
    }
    public Identifier defaultAvatar(int index) { return defaults[Math.floorMod(index, 11)]; }
    public void choose(boolean banner) {
        v.io.submit(() -> {
            try {
                String selected = TinyFileDialogs.tinyfd_openFileDialog(banner ? "Volt - Cover image" : "Volt - Avatar", "", null, "PNG / JPEG image", false);
                if (selected == null) return;
                Path source = Path.of(selected);
                if (!Files.isRegularFile(source) || Files.size(source) > 8 * 1024 * 1024) throw new IOException("Image exceeds 8 MB");
                BufferedImage input;
                try (ImageInputStream stream = ImageIO.createImageInputStream(source.toFile())) {
                    var readers = ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw new IOException("Unsupported image");
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(stream);
                        int w = reader.getWidth(0), h = reader.getHeight(0);
                        if (w < 1 || h < 1 || w > 4096 || h > 4096) throw new IOException("Maximum image dimensions: 4096x4096");
                        input = reader.read(0);
                    } finally { reader.dispose(); }
                }
                int width = banner ? 1200 : 256, height = banner ? 300 : 256;
                BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = output.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                double scale = Math.max((double) width / input.getWidth(), (double) height / input.getHeight());
                int w = (int) Math.ceil(input.getWidth() * scale), h = (int) Math.ceil(input.getHeight() * scale);
                g.drawImage(input, (width-w)/2, (height-h)/2, w, h, null); g.dispose();
                Files.createDirectories(root); Path path = root.resolve(banner ? "banner.png" : "avatar.png");
                Path temp = root.resolve(banner ? "banner.tmp" : "avatar.tmp"); ImageIO.write(output, "PNG", temp.toFile()); Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                register(path, banner);
                if (banner) v.config.customBanner = true; else v.config.customAvatar = true;
                v.saveConfig(); v.notify("volt.notice.profile_saved");
            } catch (Exception e) { v.notify("volt.notice.image_failed"); }
        });
    }
    private void register(Path path, boolean banner) throws IOException {
        NativeImage image;
        try (InputStream stream = Files.newInputStream(path)) { image = NativeImage.read(stream); }
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().getTextureManager().register(banner ? BANNER : AVATAR, new dev.volt.ui.SmoothTexture("Volt profile", image));
            if (banner) bannerReady = true; else avatarReady = true;
        });
    }
}
