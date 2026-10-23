package dev.volt.recording;

import dev.volt.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecordingRecoveryTest {
    @TempDir Path root;

    @Test void recoversOrphanedSegmentsWithMetadataAndDoesNotDuplicate() throws Exception {
        Assumptions.assumeTrue(Ffmpeg.probe(List.of("ffmpeg", "-version"), root.resolve("probe.log"), 5));
        Path session = Files.createDirectory(root.resolve("session-orphan"));
        Ffmpeg.run(List.of("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=64x64:rate=30",
            "-t", "4", "-c:v", "libx264", "-threads", "1", "-g", "60", "-sc_threshold", "0", "-f", "segment",
            "-segment_time", "2", "-reset_timestamps", "1", session.resolve("part-%08d.ts").toString()), root.resolve("fixture.log"));
        var journal = new RecordingRecovery.Journal(session,
            new ReplayRecorder.Context("VoltTester", "Dünya ü", "minecraft:overworld"), 1.1, "combo", 5, "5-hit combo");
        journal.checkpoint(3.5, List.of(new Clip.Marker(1.5, "combo")));
        ClipLibrary library = new ClipLibrary(root.resolve("clips"));
        assertEquals(1, RecordingRecovery.recover(session, library, "ffmpeg"));
        Clip clip = library.list().getFirst();
        assertEquals(journal.id, clip.id); assertTrue(clip.recovered);
        assertEquals("Dünya ü", clip.world); assertEquals("combo", clip.captureReason);
        assertEquals(5, clip.comboHits); assertTrue(clip.title.startsWith("5-hit combo"));
        assertEquals(1.5, clip.markers.getFirst().second(), .001);
        assertTrue(Ffmpeg.probe(List.of("ffmpeg", "-v", "error", "-i", clip.path(library.directory()).toString(),
            "-f", "null", "-"), root.resolve("decode.log"), 10));
        assertEquals(0, RecordingRecovery.recover(session, library, "ffmpeg"));
        assertEquals(1, library.list().size());
        assertFalse(RecordingRecovery.hasJournals(session));
        Files.writeString(library.directory().resolve(clip.id + ".json"), "{broken metadata");
        journal.checkpoint(3.5, List.of(new Clip.Marker(1.5, "combo")));
        assertEquals(1, RecordingRecovery.recover(session, library, "ffmpeg"));
        assertEquals(clip.id, library.list().getFirst().id);
        assertEquals("Dünya ü", library.list().getFirst().world);
    }

    @Test void invalidCheckpointPreservesSourceInsteadOfDeletingIt() throws Exception {
        Path session = Files.createDirectory(root.resolve("session-corrupt"));
        Path source = session.resolve("part-00000000.ts"); Files.writeString(source, "preserve");
        Files.writeString(session.resolve("long-12345678-1234-1234-1234-123456789012.json"), "{\"id\":\"../../bad\"}");
        ClipLibrary library = new ClipLibrary(root.resolve("clips"));
        assertThrows(java.io.IOException.class, () -> RecordingRecovery.recover(session, library, "ffmpeg"));
        assertEquals("preserve", Files.readString(source));
        assertTrue(RecordingRecovery.hasJournals(session));
        assertTrue(library.list().isEmpty());
    }
}
