package dev.volt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;

public final class VoltConfig {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = true;
    public boolean performanceMode = true;
    public boolean gpuColorConversion = true;
    // One-shot explicit user request, also applied after an older running client saves its keys.
    public boolean resetClipKeyToF8;
    public int clipSeconds = 30;
    public int fps = 30;
    public int height = 720;
    public String encoder = "auto";
    public String vaapiDevice = "/dev/dri/renderD128";
    public String ffmpeg = "ffmpeg";
    public String clipDirectory = "volt/clips";
    public int avatar = 0;
    public boolean customAvatar;
    public boolean customBanner;
    public String bio = "";
    public int accent = 0;
    public boolean autoDeathClip = false;
    public boolean autoHighlights = true;
    public boolean autoComboClip = true;
    public boolean autoAdventureClips = true;
    public boolean autoCombatClips = true;
    public int comboHits = 4;
    public int autoClipsPerMinute = 3;
    public boolean hud = true;
    public boolean notificationAnimation = true;
    public int notificationVolume = 50;
    public int buttonVolume = 35;
    public int hudAnchor = 3;
    public String audioFormat = System.getProperty("os.name").toLowerCase().contains("linux") ? "pulse" : "off";
    public String audioDevice = "@DEFAULT_MONITOR@";
    public String uploadEndpoint = "";
    // A Volt upload token only. Never use a Minecraft/Microsoft session token.
    public String uploadToken = "";
    public boolean discordPresence = true;
    public String discordApplicationId = "";

    public static VoltConfig load(Path path) {
        try {
            VoltConfig c = JSON.fromJson(Files.readString(path), VoltConfig.class);
            if (c == null) return new VoltConfig();
            c.clipSeconds = Math.clamp(c.clipSeconds, 15, 120);
            c.fps = c.fps == 60 ? 60 : 30;
            c.height = c.height == 1080 ? 1080 : 720;
            c.avatar = Math.floorMod(c.avatar, 11);
            c.buttonVolume = Math.clamp(c.buttonVolume, 0, 100);
            c.notificationVolume = Math.clamp(c.notificationVolume, 0, 100);
            c.hudAnchor = Math.clamp(c.hudAnchor, 0, 3);
            c.comboHits = Math.clamp(c.comboHits, 3, 8);
            c.autoClipsPerMinute = Math.clamp(c.autoClipsPerMinute, 1, 6);
            if (c.ffmpeg == null || c.ffmpeg.isBlank()) c.ffmpeg = "ffmpeg";
            if (c.clipDirectory == null || c.clipDirectory.isBlank()) c.clipDirectory = "volt/clips";
            if (c.encoder == null) c.encoder = "auto";
            if (c.vaapiDevice == null) c.vaapiDevice = "/dev/dri/renderD128";
            if (c.audioFormat == null) c.audioFormat = "off";
            if (c.audioDevice == null) c.audioDevice = "@DEFAULT_MONITOR@";
            if (c.bio == null) c.bio = "";
            if (c.uploadEndpoint == null) c.uploadEndpoint = "";
            if (c.uploadToken == null) c.uploadToken = "";
            if (c.discordApplicationId == null) c.discordApplicationId = "";
            return c;
        } catch (IOException | RuntimeException e) { return new VoltConfig(); }
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temp, JSON.toJson(this));
        try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
        try { Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException ignored) { }
    }
}
