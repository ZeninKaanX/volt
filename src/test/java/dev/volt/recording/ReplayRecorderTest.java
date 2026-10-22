package dev.volt.recording;

import dev.volt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class ReplayRecorderTest {
    @TempDir Path root;
    @Test void frameMailboxDropsStaleFramesWithoutWaiting() {
        FrameMailbox box = new FrameMailbox(16);
        byte[] first = box.acquire(), second = box.acquire(), third = box.acquire();
        assertNull(box.acquire());
        box.offer(first); box.offer(second);
        assertSame(second, box.take());
        assertSame(first, box.acquire());
        box.release(third);
    }
    @Test void producesPlayableMp4WithWorldAndEventMetadata() throws Exception {
        Assumptions.assumeTrue(Ffmpeg.probe(List.of("ffmpeg", "-version"), root.resolve("probe.log"), 5));
        VoltConfig config = new VoltConfig(); config.audioFormat = "off"; config.encoder = "libx264"; config.clipSeconds = 15;
        ClipLibrary library = new ClipLibrary(root.resolve("klipler ü"));
        List<String> notices = new CopyOnWriteArrayList<>();
        try (ReplayRecorder recorder = new ReplayRecorder(config, library, root.resolve("cache"), notices::add)) {
            recorder.start(64, 64);
            await(() -> recorder.state != ReplayRecorder.State.STARTING, 15000);
            assertEquals(ReplayRecorder.State.BUFFERING, recorder.state, recorder.error);
            ScheduledExecutorService feeder = Executors.newSingleThreadScheduledExecutor();
            feeder.scheduleAtFixedRate(() -> {
                byte[] frame = recorder.frames.acquire();
                if (frame != null) { Arrays.fill(frame, (byte) 170); recorder.frames.offer(frame); }
            }, 0, 33, TimeUnit.MILLISECONDS);
            try {
                await(() -> recorder.elapsed() > 4 && recorder.originNanos != 0, 8000);
                recorder.mark("death"); recorder.saveReplay(new ReplayRecorder.Context("VoltTester", "Dünya ü", "minecraft:overworld"));
                await(() -> !recorder.isExporting(), 10000);
                List<Clip> clips = library.list(); assertEquals(1, clips.size(), () -> notices + " " + recorder.error);
                Clip clip = clips.getFirst(); assertEquals("Dünya ü", clip.world); assertEquals("death", clip.markers.getFirst().kind());
                assertTrue(Files.size(clip.path(library.directory())) > 1000);
                assertTrue(Ffmpeg.probe(List.of("ffmpeg", "-v", "error", "-i", clip.path(library.directory()).toString(), "-f", "null", "-"), root.resolve("decode.log"), 10));
                var context = new ReplayRecorder.Context("VoltTester", "Dünya ü", "minecraft:overworld");
                double longStart = recorder.elapsed();
                recorder.toggleLong(context); assertTrue(recorder.longRecording);
                await(() -> recorder.elapsed() > longStart + 2.1, 5000);
                recorder.toggleLong(context);
                await(() -> !recorder.isExporting(), 10000);
                assertFalse(recorder.longRecording); assertEquals(2, library.list().size(), recorder.error);
                recorder.reserveHighlight(0); config.clipSeconds = 1;
                await(() -> recorder.elapsed() > 14, 12000);
                assertTrue(recorder.saveHighlight(0, recorder.elapsed(), context, "combo", 5, "5-hit combo"));
                recorder.releaseHighlight();
                await(() -> !recorder.isExporting(), 10000);
                assertEquals(3, library.list().size(), recorder.error);
                Clip automatic = library.list().stream().filter(c -> "combo".equals(c.captureReason)).findFirst().orElseThrow();
                assertEquals(5, automatic.comboHits); assertTrue(automatic.title.contains("5-hit combo"));
                assertTrue(automatic.seconds >= 13, "Reserved pre-roll must survive pruning");
                assertTrue(Ffmpeg.probe(List.of("ffmpeg", "-v", "error", "-i", automatic.path(library.directory()).toString(), "-f", "null", "-"), root.resolve("auto-decode.log"), 10));
                recorder.stop(); await(() -> recorder.state == ReplayRecorder.State.OFF, 8000);
            } finally { feeder.shutdownNow(); }
        }
    }
    static void await(java.util.function.BooleanSupplier condition, int millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(30);
        assertTrue(condition.getAsBoolean(), "Timed out");
    }
}
