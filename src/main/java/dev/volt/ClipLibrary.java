package dev.volt;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ClipLibrary {
    private final Path directory;
    public ClipLibrary(Path directory) throws IOException { this.directory = directory; Files.createDirectories(directory); }
    public Path directory() { return directory; }
    public synchronized List<Clip> list() {
        List<Clip> result = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    Clip c = VoltConfig.JSON.fromJson(Files.readString(p), Clip.class);
                    if (c != null && c.id != null && c.id.matches("[a-zA-Z0-9-]+") && c.file != null && c.file.equals(c.id + ".mp4") &&
                            c.title != null && c.world != null && c.player != null && Files.isRegularFile(c.path(directory))) {
                        if (c.markers == null) c.markers = new ArrayList<>();
                        if (c.shareUrl == null) c.shareUrl = "";
                        if (c.captureReason == null) c.captureReason = "";
                        result.add(c);
                    }
                } catch (IOException | RuntimeException ignored) { }
            });
        } catch (IOException ignored) { }
        result.sort(Comparator.comparingLong((Clip c) -> c.created).reversed());
        return List.copyOf(result);
    }
    public synchronized void save(Clip clip) throws IOException {
        if (!clip.id.matches("[a-zA-Z0-9-]+")) throw new IOException("Invalid clip ID");
        Path target = directory.resolve(clip.id + ".json");
        Path temp = directory.resolve(clip.id + ".json.tmp");
        Files.writeString(temp, VoltConfig.JSON.toJson(clip));
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
