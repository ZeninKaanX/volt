package dev.volt.recording;

import dev.volt.Clip;
import dev.volt.ClipLibrary;
import dev.volt.VoltConfig;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Durable long-recording checkpoints. Only recorder workers access the filesystem. */
public final class RecordingRecovery {
    private RecordingRecovery() { }
    private record Checkpoint(String id, ReplayRecorder.Context context, long created,
                              double start, double end, List<Clip.Marker> markers,
                              String reason, int comboHits, String label) { }

    public static final class Journal {
        public final String id = UUID.randomUUID().toString();
        private final Path session, file;
        private final ReplayRecorder.Context context;
        private final double start;
        private final long created = System.currentTimeMillis();
        private final String reason, label;
        private final int comboHits;
        private boolean completed;

        public Journal(Path session, ReplayRecorder.Context context, double start) {
            this(session, context, start, "", 0, "");
        }

        public Journal(Path session, ReplayRecorder.Context context, double start, String reason, int comboHits, String label) {
            this.session = session;
            this.context = context;
            this.start = start;
            this.reason = reason; this.comboHits = comboHits; this.label = label;
            this.file = session.resolve("long-" + id + ".json");
        }

        public synchronized void checkpoint(double end, List<Clip.Marker> markers) throws IOException {
            if (completed) return;
            Files.createDirectories(session);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, VoltConfig.JSON.toJson(new Checkpoint(id, context, created,
                start, Math.max(start, end), List.copyOf(markers), reason, comboHits, label)));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        }

        public synchronized void complete() throws IOException {
            completed = true;
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".tmp"));
        }
    }

    public static boolean hasJournals(Path session) {
        if (!Files.isDirectory(session)) return false;
        try (var files = Files.list(session)) {
            return files.anyMatch(p -> p.getFileName().toString().matches("long-[a-fA-F0-9-]+\\.json"));
        } catch (IOException e) { return true; } // Never delete a folder whose recovery state cannot be read.
    }

    /** Called only after the old encoder has stopped, and after queued exports have drained. */
    public static int recover(Path session, ClipLibrary library, String ffmpeg) throws IOException, InterruptedException {
        List<Path> journals;
        try (var files = Files.list(session)) {
            journals = files.filter(p -> p.getFileName().toString().matches("long-[a-fA-F0-9-]+\\.json")).sorted().toList();
        }
        int count = 0;
        Set<String> committed = new HashSet<>(library.list().stream().map(c -> c.id).toList());
        for (Path file : journals) {
            Checkpoint checkpoint;
            try { checkpoint = VoltConfig.JSON.fromJson(Files.readString(file), Checkpoint.class); }
            catch (RuntimeException e) { throw new IOException("Unreadable recording checkpoint; source segments preserved", e); }
            if (checkpoint == null || checkpoint.id == null || !checkpoint.id.matches("[a-fA-F0-9-]{36}") ||
                !file.getFileName().toString().equals("long-" + checkpoint.id + ".json") || checkpoint.context == null ||
                checkpoint.context.player() == null || checkpoint.context.world() == null ||
                !Double.isFinite(checkpoint.start) || !Double.isFinite(checkpoint.end) ||
                checkpoint.start < 0 || checkpoint.end < checkpoint.start)
                throw new IOException("Invalid recording checkpoint; source segments preserved");
            if (committed.contains(checkpoint.id)) {
                Files.deleteIfExists(file); // A crash after commit must not create a duplicate clip.
                continue;
            }
            List<Path> parts;
            try (var files = Files.list(session)) {
                parts = files.filter(p -> p.getFileName().toString().matches("part-\\d{8}\\.ts"))
                    .filter(p -> number(p) * 2 < checkpoint.end && number(p) * 2 + 2 > checkpoint.start)
                    .sorted().toList();
            }
            if (parts.isEmpty() || checkpoint.end - checkpoint.start < .1) {
                if (checkpoint.end - checkpoint.start < 1) { Files.deleteIfExists(file); continue; }
                throw new IOException("No recoverable video segments; checkpoint preserved");
            }
            double actualStart = number(parts.getFirst()) * 2.0;
            Clip clip = new Clip();
            clip.id = checkpoint.id; clip.file = clip.id + ".mp4";
            clip.player = checkpoint.context.player(); clip.world = checkpoint.context.world();
            clip.dimension = checkpoint.context.dimension(); clip.created = checkpoint.created;
            clip.seconds = checkpoint.end - actualStart; clip.recovered = true;
            clip.captureReason = checkpoint.reason == null ? "" : checkpoint.reason;
            clip.comboHits = checkpoint.comboHits;
            clip.title = clip.world + " · " + LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(clip.created),
                java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));
            if (checkpoint.label != null && !checkpoint.label.isBlank()) clip.title = checkpoint.label + " · " + clip.title;
            for (Clip.Marker marker : checkpoint.markers == null ? List.<Clip.Marker>of() : checkpoint.markers)
                if (marker != null && marker.kind() != null && Double.isFinite(marker.second()) && marker.second() >= actualStart && marker.second() <= checkpoint.end)
                    clip.markers.add(new Clip.Marker(marker.second() - actualStart, marker.kind()));
            Path output = library.directory().resolve(clip.id + ".partial.mp4");
            Path concat = session.resolve("recovery-" + clip.id + ".ffconcat");
            try {
                if (!Files.isRegularFile(clip.path(library.directory()))) {
                    StringBuilder list = new StringBuilder("ffconcat version 1.0\n");
                    for (Path part : parts) list.append("file '").append(part.getFileName()).append("'\n");
                    Files.writeString(concat, list);
                    Ffmpeg.run(List.of(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0",
                        "-i", concat.toString(), "-t", Double.toString(clip.seconds), "-c", "copy", "-avoid_negative_ts",
                        "make_zero", "-movflags", "+faststart", output.toString()), session.resolve("recovery.log"));
                    Files.move(output, clip.path(library.directory()), StandardCopyOption.REPLACE_EXISTING);
                }
                library.save(clip);
                committed.add(clip.id);
                Files.delete(file);
                count++;
            } finally {
                Files.deleteIfExists(concat);
                Files.deleteIfExists(output);
            }
        }
        return count;
    }

    private static int number(Path part) { return Integer.parseInt(part.getFileName().toString().substring(5, 13)); }
}
