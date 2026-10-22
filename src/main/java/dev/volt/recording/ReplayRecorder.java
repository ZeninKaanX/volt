package dev.volt.recording;

import dev.volt.*;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/** Encoded disk ring. Neither process I/O nor filesystem access runs on the render thread. */
public final class ReplayRecorder implements AutoCloseable {
    public enum State { OFF, STARTING, BUFFERING, STOPPING, ERROR }
    public volatile State state = State.OFF;
    public volatile String error = "";
    public volatile String encoder = "";
    public volatile boolean audio,nv12;
    public volatile int width, height;
    public volatile FrameMailbox frames;
    public volatile long submitted, duplicated;
    public volatile long originNanos;
    public volatile boolean longRecording;
    public volatile long lastClipMillis;
    private double longStart;
    private final VoltConfig config;
    private final ClipLibrary library;
    private final Path cache;
    private final Consumer<String> notice;
    private final ExecutorService control = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Volt-control").factory());
    private final ExecutorService exports = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Volt-export").factory());
    private final ExecutorService encoding = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Volt-encoder").factory());
    private final AtomicBoolean exporting = new AtomicBoolean();
    private final List<TimedMarker> markers = new CopyOnWriteArrayList<>();
    private volatile Process process;
    private volatile boolean running;
    private volatile Future<?> writer;
    private Path session;
    private double stoppedAt;
    private int sessionFps = 30;
    private FileChannel lockChannel;
    private FileLock cacheLock;
    private volatile double highlightReservation=Double.POSITIVE_INFINITY,exportReservation=Double.POSITIVE_INFINITY;
    private record TimedMarker(double second, String kind) { }
    public record Context(String player, String world, String dimension) { }
    public ReplayRecorder(VoltConfig config, ClipLibrary library, Path cache, Consumer<String> notice) {
        this.config = config; this.library = library; this.cache = cache; this.notice = notice;
    }
    public boolean isExporting() { return exporting.get(); }
    public int fps() { return sessionFps; }
    public double elapsed() { return originNanos == 0 ? 0 : (System.nanoTime() - originNanos) / 1e9; }
    public double availableSeconds() { return state == State.BUFFERING ? Math.min(config.clipSeconds, elapsed()) : 0; }
    public void start(int sourceWidth, int sourceHeight) {
        if (state != State.OFF) return;
        state = State.STARTING;
        control.submit(() -> {
            try {
                Files.createDirectories(cache);
                if (cacheLock == null) {
                    lockChannel = FileChannel.open(cache.resolve("recording.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    cacheLock = lockChannel.tryLock();
                    if (cacheLock == null) throw new IOException("Another Volt recorder is using this game folder");
                    try (var leftovers = Files.list(cache)) {
                        leftovers.filter(p -> Files.isDirectory(p) && (p.getFileName().toString().startsWith("session-") || p.getFileName().toString().startsWith("export-"))).forEach(ReplayRecorder::deleteTree);
                    }
                }
                if (session != null && !exporting.get()) deleteTree(session);
                session = Files.createTempDirectory(cache, "session-");
                encoder = Ffmpeg.selectEncoder(config.ffmpeg, config.encoder, config.vaapiDevice, session.resolve("probe.log"));
                CaptureProfile profile=CaptureProfile.resolve(config,sourceWidth,sourceHeight,!encoder.equals("libx264"));
                width=profile.width();height=profile.height();sessionFps=profile.fps();
                Files.writeString(session.resolve("info.txt"), "Encoder=" + encoder + "\nResolution=" + width + "x" + height + "\nFPS=" + sessionFps + "\nPixelFormat="+(config.gpuColorConversion?"nv12":"bgra")+"\n");
                audio = !config.audioFormat.equals("off") && List.of("pulse", "dshow", "avfoundation").contains(config.audioFormat)
                    && Ffmpeg.probe(List.of(config.ffmpeg, "-hide_banner", "-loglevel", "error", "-f", config.audioFormat,
                    "-i", config.audioDevice, "-t", "0.1", "-f", "null", "-"), session.resolve("audio.log"), 4);
                if (!config.audioFormat.equals("off") && !audio) notice.accept("volt.notice.no_audio");
                nv12=config.gpuColorConversion;
                frames = new FrameMailbox(width * height * (nv12?3:8)/2);
                List<String> cmd = new ArrayList<>(List.of(config.ffmpeg, "-hide_banner", "-loglevel", "warning", "-y", "-filter_threads", "1", "-filter_complex_threads", "1"));
                if (encoder.equals("h264_vaapi")) cmd.addAll(List.of("-vaapi_device", config.vaapiDevice));
                cmd.addAll(List.of("-f", "rawvideo", "-pixel_format", nv12?"nv12":"bgra", "-video_size", width + "x" + height,
                    "-framerate", "" + sessionFps));
                if(nv12)cmd.addAll(List.of("-color_range","tv","-colorspace","bt709"));
                cmd.addAll(List.of("-i", "pipe:0"));
                if (audio) cmd.addAll(List.of("-thread_queue_size", "256", "-f", config.audioFormat, "-i", config.audioDevice));
                cmd.addAll(List.of("-map", "0:v:0"));
                if (audio) cmd.addAll(List.of("-map", "1:a:0", "-c:a", "aac", "-b:a", "128k", "-af", "aresample=async=1:first_pts=0"));
                cmd.addAll(List.of("-c:v", encoder));
                if (encoder.equals("h264_vaapi")) cmd.addAll(List.of("-vf", nv12?"hwupload":"format=nv12,hwupload"));
                else cmd.addAll(List.of("-pix_fmt", "yuv420p"));
                cmd.addAll(List.of("-bf", "0", "-g", "" + sessionFps * 2,
                    "-force_key_frames", "expr:gte(t,n_forced*2)", "-b:v", height >= 1080 ? "10M" : height >= 720 ? "5M" : "3M"));
                if (encoder.equals("libx264")) cmd.addAll(List.of("-preset", "ultrafast", "-tune", "zerolatency", "-threads", "2", "-sc_threshold", "0"));
                if (encoder.equals("h264_nvenc")) cmd.addAll(List.of("-preset", "p1", "-tune", "ll"));
                cmd.addAll(List.of("-shortest", "-f", "segment", "-segment_time", "2", "-reset_timestamps", "1",
                    session.resolve("part-%08d.ts").toString()));
                process = new ProcessBuilder(cmd).redirectError(session.resolve("recording.log").toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                submitted = duplicated = 0; markers.clear(); originNanos = 0;
                running = true;
                writer = encoding.submit(this::writeFrames);
                state = State.BUFFERING;
            } catch (Exception e) { fail(e); }
        });
    }
    private void writeFrames() {
        byte[] current = null;
        long number = 0, cleanup = 0;
        try (OutputStream out = new BufferedOutputStream(process.getOutputStream(), 256 * 1024)) {
            while (running) {
                byte[] next = frames.take();
                if (next != null) { frames.release(current); current = next; submitted++; }
                else if (current != null) duplicated++;
                if (current == null) { LockSupport.parkNanos(1_000_000); continue; }
                if (originNanos == 0) originNanos = System.nanoTime();
                out.write(current); out.flush();
                number++;
                long target = originNanos + number * 1_000_000_000L / sessionFps;
                LockSupport.parkNanos(Math.max(0, target - System.nanoTime()));
                // Do not catch up unboundedly if the encoder stalls: stop with an actionable error.
                if (System.nanoTime() - target > 3_000_000_000L) throw new IOException("Encoder too slow; choose 720p / 30 FPS or hardware encoding");
                if (System.nanoTime() - cleanup > 2_000_000_000L) { prune(); cleanup = System.nanoTime(); }
            }
        } catch (Exception e) { if (running) fail(e); }
        finally { if (frames != null) frames.release(current); }
    }
    private synchronized List<Path> parts(boolean includeLast) throws IOException {
        if (session == null) return List.of();
        List<Path> list;
        try (var files = Files.list(session)) { list = files.filter(p -> p.getFileName().toString().matches("part-\\d+\\.ts")).sorted().toList(); }
        return !includeLast && !list.isEmpty() ? list.subList(0, list.size() - 1) : list;
    }
    private synchronized void prune() throws IOException {
        double oldest = longRecording ? Math.min(longStart, elapsed() - config.clipSeconds - 6) : elapsed() - config.clipSeconds - 6;
        final double cutoff=Math.min(oldest,Math.min(highlightReservation,exportReservation));
        for (Path p : parts(false)) if (partNumber(p) * 2 + 2 < cutoff) Files.deleteIfExists(p);
        markers.removeIf(m -> m.second < cutoff);
        if (Files.getFileStore(session).getUsableSpace() < 256L * 1024 * 1024) throw new IOException("Disk space below 256 MB; recording stopped");
    }
    private static int partNumber(Path p) { return Integer.parseInt(p.getFileName().toString().substring(5, 13)); }
    public void mark(String kind) { if (state == State.BUFFERING) markers.add(new TimedMarker(elapsed(), kind)); }
    public void saveReplay(Context context) { if(longRecording)mark("manual");requestExport(Math.max(0, elapsed() - config.clipSeconds), elapsed(), context, false); }
    public void reserveHighlight(double start){highlightReservation=Math.max(0,start);}
    public void releaseHighlight(){highlightReservation=Double.POSITIVE_INFINITY;}
    public boolean saveHighlight(double start,double end,Context context,String reason,int comboHits,String label){
        return requestExport(start,end,context,false,reason,comboHits,label);
    }
    public void toggleLong(Context context) {
        if (state != State.BUFFERING) return;
        if (!longRecording) { longStart = elapsed(); longRecording = true; notice.accept("volt.notice.long_started"); }
        else { requestExport(longStart, elapsed(), context, true); }
    }
    private boolean requestExport(double start,double end,Context context,boolean longClip){return requestExport(start,end,context,longClip,"",0,"");}
    private boolean requestExport(double start, double end, Context context, boolean longClip,String reason,int comboHits,String label) {
        if (state != State.BUFFERING || end - start < 1) { notice.accept("volt.notice.wait_buffer"); return false; }
        if (!exporting.compareAndSet(false, true)) { notice.accept("volt.notice.busy"); return false; }
        exportReservation=Math.max(0,start);
        Path sourceSession = session;
        notice.accept("volt.notice.saving");
        exports.submit(() -> {
            Path staging = null;
            try {
                // Wait for the segment containing the keypress to close, up to 5 seconds.
                long deadline = System.nanoTime() + 5_000_000_000L;
                while (running && elapsed() < Math.ceil(end / 2) * 2 + .3 && System.nanoTime() < deadline) Thread.sleep(100);
                if (!running && process != null) process.waitFor(5, TimeUnit.SECONDS);
                List<Path> selected;
                synchronized (this) {
                    if (!Objects.equals(sourceSession, session)) throw new IOException("Recording session changed");
                    selected = parts(!running).stream().filter(p -> partNumber(p) * 2 < end && partNumber(p) * 2 + 2 > start).toList();
                    if (selected.isEmpty()) throw new IOException("No complete video segment");
                    staging = Files.createTempDirectory(cache, "export-");
                    for (Path p : selected) {
                        try { Files.createLink(staging.resolve(p.getFileName()), p); }
                        catch (IOException e) { Files.copy(p, staging.resolve(p.getFileName())); }
                    }
                    if (longClip) longRecording = false;
                }
                StringBuilder concat = new StringBuilder("ffconcat version 1.0\n");
                for (Path p : selected) concat.append("file '").append(p.getFileName()).append("'\n");
                Files.writeString(staging.resolve("list.txt"), concat);
                // Copy complete GOPs: include up to 2 seconds of extra history rather than dropping the key moment.
                // Exact frame cuts are available through the separate trim action.
                double actualStart = partNumber(selected.getFirst()) * 2.0;
                Clip clip = new Clip(); clip.id = UUID.randomUUID().toString(); clip.file = clip.id + ".mp4";
                clip.title = context.world + " · " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));
                if(!label.isBlank())clip.title=label+" · "+clip.title;
                clip.captureReason=reason;clip.comboHits=comboHits;
                clip.player = context.player; clip.world = context.world; clip.dimension = context.dimension;
                clip.created = System.currentTimeMillis(); clip.seconds = end - actualStart;
                for (TimedMarker m : markers) if (m.second >= actualStart && m.second <= end) clip.markers.add(new Clip.Marker(m.second - actualStart, m.kind));
                Path output = library.directory().resolve(clip.id + ".partial.mp4");
                Ffmpeg.run(List.of(config.ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i",
                    staging.resolve("list.txt").toString(), "-ss", "" + (actualStart - partNumber(selected.getFirst()) * 2),
                    "-t", "" + clip.seconds, "-c", "copy", "-avoid_negative_ts", "make_zero", "-movflags", "+faststart", output.toString()), staging.resolve("export.log"));
                Files.move(output, clip.path(library.directory()), StandardCopyOption.REPLACE_EXISTING);
                library.save(clip);
                lastClipMillis = System.currentTimeMillis();
                notice.accept("volt.notice.saved");
            } catch (Exception e) {
                error = e.getMessage();
                if (staging != null) try { Files.copy(staging.resolve("export.log"), cache.resolve("last-export.log"), StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }
                notice.accept("volt.notice.export_failed");
            }
            finally { if (staging != null) deleteTree(staging);exportReservation=Double.POSITIVE_INFINITY; exporting.set(false); }
        });
        return true;
    }
    public void stop() {
        if (state == State.OFF || state == State.STOPPING) return;
        state = State.STOPPING;
        control.submit(() -> {
            running = false; stoppedAt = elapsed();
            try {
                if (writer != null) writer.get(2, TimeUnit.SECONDS);
                if (process != null && !process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (Exception e) { if (process != null) process.destroyForcibly(); }
            state = State.OFF; longRecording = false;
            // Keep session files until shutdown so a pending export can safely finish.
        });
    }
    private void fail(Exception e) {
        running = false; error = e.getMessage() == null ? e.toString() : e.getMessage(); state = State.ERROR;
        if (process != null) process.destroyForcibly();
        notice.accept("volt.notice.error");
    }
    public void resetError() { if (state == State.ERROR) { error = ""; state = State.OFF; } }
    public static void deleteTree(Path dir) {
        try (var paths = Files.walk(dir)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } }); }
        catch (IOException ignored) { }
    }
    @Override public void close() {
        running = false;
        if (process != null) process.destroyForcibly();
        control.shutdownNow(); exports.shutdownNow(); encoding.shutdownNow();
        try { if (cacheLock != null) cacheLock.release(); if (lockChannel != null) lockChannel.close(); } catch (IOException ignored) { }
    }
}
