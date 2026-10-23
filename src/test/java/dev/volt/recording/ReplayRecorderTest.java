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
                double finalStart = recorder.elapsed();
                recorder.toggleLong(context);
                await(() -> recorder.elapsed() > finalStart + 2.1, 5000);
                recorder.stop(); await(() -> recorder.state == ReplayRecorder.State.OFF, 10000);
                assertEquals(4, library.list().size(), "Leaving a world must save an active long recording");
                Clip finalSession = library.list().stream().filter(c -> c.recovered).findFirst().orElseThrow();
                assertTrue(finalSession.seconds >= 2);
                assertEquals(context.world(), finalSession.world);
                assertTrue(Ffmpeg.probe(List.of("ffmpeg", "-v", "error", "-i", finalSession.path(library.directory()).toString(), "-f", "null", "-"), root.resolve("session-decode.log"), 10));
            } finally { feeder.shutdownNow(); }
        }
    }
    @Test void capturesCorrectBgraColorsWhenGpuConversionIsUnavailable() throws Exception {
        Assumptions.assumeTrue(Ffmpeg.probe(List.of("ffmpeg", "-version"), root.resolve("probe.log"), 5));
        VoltConfig config = new VoltConfig(); config.audioFormat = "off"; config.encoder = "libx264";
        ClipLibrary library = new ClipLibrary(root.resolve("fallback-clips"));
        try (ReplayRecorder recorder = new ReplayRecorder(config, library, root.resolve("fallback-cache"), ignored -> { })) {
            recorder.start(64, 64, false);
            await(() -> recorder.state != ReplayRecorder.State.STARTING, 15000);
            assertEquals(ReplayRecorder.State.BUFFERING, recorder.state, recorder.error);
            assertFalse(recorder.nv12, "Unsupported GPU conversion must fall back without changing the preference");
            assertTrue(config.gpuColorConversion);
            ScheduledExecutorService feeder = Executors.newSingleThreadScheduledExecutor();
            feeder.scheduleAtFixedRate(() -> {
                byte[] pixels = recorder.frames.acquire();
                if (pixels != null) {
                    for (int i = 0; i < pixels.length; i += 4) { pixels[i] = 20; pixels[i+1] = 50; pixels[i+2] = (byte)200; pixels[i+3] = (byte)255; }
                    recorder.frames.offer(pixels);
                }
            }, 0, 33, TimeUnit.MILLISECONDS);
            try {
                await(() -> recorder.originNanos != 0 && recorder.elapsed() > 2.2, 8000);
                recorder.saveReplay(new ReplayRecorder.Context("VoltTester", "Fallback", "minecraft:overworld"));
                await(() -> !recorder.isExporting(), 10000);
                Clip clip = library.list().getFirst();
                Path frame = root.resolve("fallback.png");
                Ffmpeg.run(List.of("ffmpeg", "-v", "error", "-y", "-i", clip.path(library.directory()).toString(),
                    "-frames:v", "1", "-threads", "1", frame.toString()), root.resolve("fallback-decode.log"));
                java.awt.Color color = new java.awt.Color(javax.imageio.ImageIO.read(frame.toFile()).getRGB(20,20));
                assertTrue(color.getRed() > 150 && color.getGreen() < 100 && color.getBlue() < 60, color.toString());
            } finally { feeder.shutdownNow(); }
        }
    }
    static void await(java.util.function.BooleanSupplier condition, int millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(30);
        assertTrue(condition.getAsBoolean(), "Timed out");
    }
}
