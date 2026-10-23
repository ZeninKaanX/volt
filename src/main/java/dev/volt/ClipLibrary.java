package dev.volt;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ClipLibrary {
    private final Path directory;
    private record MoveIntent(String operation, Clip clip) { }
    public ClipLibrary(Path directory) throws IOException { this.directory = directory; Files.createDirectories(directory); reconcileMoves(); }
    public Path directory() { return directory; }
    public synchronized List<Clip> list() {
        reconcileMoves();
        List<Clip> result = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                Clip c = read(p); if (c != null) result.add(c);
            });
        } catch (IOException ignored) { }
        result.sort(Comparator.comparingLong((Clip c) -> c.created).reversed());
        return List.copyOf(result);
    }
    private static Clip read(Path metadata) {
        try {
            Clip c = VoltConfig.JSON.fromJson(Files.readString(metadata), Clip.class);
            if (c == null || c.id == null || !c.id.matches("[a-zA-Z0-9-]+") || !metadata.getFileName().toString().equals(c.id + ".json") ||
                c.file == null || !c.file.equals(c.id + ".mp4") || c.title == null || c.world == null || c.player == null ||
                !Files.isRegularFile(c.path(metadata.getParent()))) return null;
            if (c.markers == null) c.markers = new ArrayList<>();
            if (c.shareUrl == null) c.shareUrl = "";
            if (c.captureReason == null) c.captureReason = "";
            return c;
        } catch (IOException | RuntimeException e) { return null; }
    }
    public Path trashDirectory() { return directory.resolve(".trash"); }
    private Path trashSlot(Clip clip) throws IOException {
        if (clip.id == null || !clip.id.matches("[a-zA-Z0-9-]+") || !Objects.equals(clip.file, clip.id + ".mp4"))
            throw new IOException("Invalid clip ID");
        return trashDirectory().resolve(clip.id);
    }
    /** Resolve previews in either local collection; no media is copied into memory. */
    public synchronized Path path(Clip clip) {
        Path active = clip.path(directory);
        try { return Files.isRegularFile(active) ? active : trashSlot(clip).resolve(clip.file); }
        catch (IOException e) { return active; }
    }
    public synchronized List<Clip> trash() {
        reconcileMoves();
        List<Clip> result = new ArrayList<>();
        if (!Files.isDirectory(trashDirectory())) return List.of();
        try (var slots = Files.list(trashDirectory())) {
            for (Path slot : slots.filter(Files::isDirectory).toList()) {
                if (!slot.getFileName().toString().matches("[a-zA-Z0-9-]+")) continue;
                Clip c = read(slot.resolve(slot.getFileName() + ".json"));
                if (c != null) result.add(c);
            }
        } catch (IOException ignored) { }
        result.sort(Comparator.comparingLong((Clip c) -> c.trashedAt).reversed());
        return List.copyOf(result);
    }
    public synchronized void trash(Clip clip) throws IOException {
        Path slot = trashSlot(clip); Files.createDirectories(slot);
        if (Files.exists(slot.resolve(clip.file)) || Files.exists(slot.resolve(clip.id + ".json")))
            throw new FileAlreadyExistsException("Clip already in recently deleted");
        long previous = clip.trashedAt;
        clip.trashedAt = System.currentTimeMillis();
        try {
            save(clip);
            writeIntent(slot, "trash", clip);
            movePair(directory, slot, clip);
            Files.deleteIfExists(slot.resolve(".move.json"));
        } catch (IOException e) { clip.trashedAt = previous; throw e; }
    }
    public synchronized void restore(Clip clip) throws IOException {
        Path slot = trashSlot(clip);
        if (Files.exists(clip.path(directory)) || Files.exists(directory.resolve(clip.id + ".json")))
            throw new FileAlreadyExistsException("Restore would overwrite an existing clip");
        writeIntent(slot, "restore", clip);
        movePair(slot, directory, clip);
        clip.trashedAt = 0; save(clip);
        Files.deleteIfExists(slot.resolve(".move.json"));
        removeEmptySlot(slot);
    }
    private static void movePair(Path source, Path target, Clip clip) throws IOException {
        Path video = source.resolve(clip.file), destination = target.resolve(clip.file);
        Files.move(video, destination);
        try { Files.move(source.resolve(clip.id + ".json"), target.resolve(clip.id + ".json")); }
        catch (IOException e) { Files.move(destination, video); throw e; }
    }
    private static void writeIntent(Path slot, String operation, Clip clip) throws IOException {
        Files.createDirectories(slot);
        Path temporary = slot.resolve(".move.json.tmp"), target = slot.resolve(".move.json");
        Files.writeString(temporary, VoltConfig.JSON.toJson(new MoveIntent(operation, clip)));
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    /** Complete interrupted two-file moves without overwriting either recording. */
    private void reconcileMoves() {
        if (!Files.isDirectory(trashDirectory())) return;
        try (var slots = Files.list(trashDirectory())) {
            for (Path slot : slots.filter(Files::isDirectory).toList()) {
                Path intent = slot.resolve(".move.json");
                if (!Files.isRegularFile(intent)) continue;
                try {
                    MoveIntent move = VoltConfig.JSON.fromJson(Files.readString(intent), MoveIntent.class);
                    if (move == null || move.clip == null || !Set.of("trash", "restore").contains(move.operation) ||
                        !slot.equals(trashSlot(move.clip))) continue;
                    Path source = move.operation.equals("trash") ? directory : slot;
                    Path target = move.operation.equals("trash") ? slot : directory;
                    for (String filename : List.of(move.clip.file, move.clip.id + ".json")) {
                        Path from = source.resolve(filename), to = target.resolve(filename);
                        if (Files.exists(from) && Files.exists(to)) throw new FileAlreadyExistsException(filename);
                        if (Files.exists(from)) Files.move(from, to);
                        else if (!Files.isRegularFile(to)) throw new NoSuchFileException(filename);
                    }
                    if (move.operation.equals("restore")) {
                        Clip restored = read(target.resolve(move.clip.id + ".json"));
                        if (restored == null) continue;
                        restored.trashedAt = 0; save(restored);
                    }
                    Files.delete(intent);
                    if (move.operation.equals("restore")) removeEmptySlot(slot);
                } catch (IOException | RuntimeException ignored) { /* Preserve conflicting or incomplete transactions. */ }
            }
        } catch (IOException ignored) { }
    }
    private static void removeEmptySlot(Path slot) {
        try (var files = Files.list(slot)) {
            if (files.findAny().isEmpty()) Files.deleteIfExists(slot);
        } catch (IOException ignored) { }
    }
    public synchronized void save(Clip clip) throws IOException {
        if (!clip.id.matches("[a-zA-Z0-9-]+")) throw new IOException("Invalid clip ID");
        Path target = directory.resolve(clip.id + ".json");
        Path temp = directory.resolve(clip.id + ".json.tmp");
        Files.writeString(temp, VoltConfig.JSON.toJson(clip));
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
