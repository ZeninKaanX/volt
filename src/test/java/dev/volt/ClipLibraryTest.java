package dev.volt;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;

class ClipLibraryTest {
    @TempDir Path root;
    @Test void ignoresMalformedAndPathTraversalMetadata() throws Exception {
        ClipLibrary library = new ClipLibrary(root.resolve("clips"));
        Files.writeString(root.resolve("secret.mp4"), "private");
        Files.writeString(library.directory().resolve("bad.json"), "{invalid");
        Files.writeString(library.directory().resolve("traversal.json"), "{\"id\":\"traversal\",\"file\":\"../secret.mp4\",\"title\":\"x\",\"world\":\"x\",\"player\":\"x\"}");
        assertTrue(library.list().isEmpty());
    }
    @Test void savesProfilePreferencesAndClampsCorruptValues() throws Exception {
        Path path = root.resolve("config/volt.json");
        VoltConfig config = new VoltConfig(); config.bio = "Türkçe profil"; config.avatar = 10; config.save(path);
        assertEquals("Türkçe profil", VoltConfig.load(path).bio);
        assertEquals(10, VoltConfig.load(path).avatar);
        Files.writeString(path, "{\"fps\":999,\"height\":99999,\"clipSeconds\":999999}");
        config = VoltConfig.load(path); assertEquals(30, config.fps); assertEquals(720, config.height); assertEquals(120, config.clipSeconds);
    }
    @Test void trashAndRestorePreserveVideoMarkersAndSharingMetadata() throws Exception {
        ClipLibrary library = new ClipLibrary(root.resolve("clips"));
        Clip c = fixture(library);
        byte[] original = Files.readAllBytes(c.path(library.directory()));
        library.trash(c);
        assertTrue(library.list().isEmpty());
        Clip deleted = library.trash().getFirst();
        assertTrue(deleted.trashedAt > 0);
        assertArrayEquals(original, Files.readAllBytes(library.path(deleted)));
        assertEquals(c.shareUrl, deleted.shareUrl);
        assertEquals(c.markers, deleted.markers);
        library.restore(deleted);
        assertTrue(library.trash().isEmpty());
        Clip restored = library.list().getFirst();
        assertEquals(0, restored.trashedAt);
        assertArrayEquals(original, Files.readAllBytes(restored.path(library.directory())));
        assertEquals(c.markers, restored.markers);
    }
    @Test void restoringNeverOverwritesAnExistingRecording() throws Exception {
        ClipLibrary library = new ClipLibrary(root.resolve("clips"));
        Clip c = fixture(library); library.trash(c);
        Files.writeString(c.path(library.directory()), "existing recording");
        assertThrows(FileAlreadyExistsException.class, () -> library.restore(c));
        assertEquals("existing recording", Files.readString(c.path(library.directory())));
        assertEquals(1, library.trash().size());
        assertEquals("original video", Files.readString(library.trashDirectory().resolve(c.id).resolve(c.file)));
    }
    @Test void trashRejectsPathsOutsideTheLibrary() throws Exception {
        ClipLibrary library = new ClipLibrary(root.resolve("clips"));
        Path outside = root.resolve("outside.mp4"); Files.writeString(outside, "private");
        Clip c = new Clip(); c.id = "../outside"; c.file = "../outside.mp4";
        assertThrows(java.io.IOException.class, () -> library.trash(c));
        assertEquals("private", Files.readString(outside));
    }
    @Test void interruptedTrashAndRestoreMovesAreCompletedOnReopen() throws Exception {
        Path directory = root.resolve("clips"); ClipLibrary library = new ClipLibrary(directory);
        Clip c = fixture(library); c.trashedAt = 42; library.save(c);
        Path slot = library.trashDirectory().resolve(c.id); Files.createDirectories(slot);
        Files.writeString(slot.resolve(".move.json"), "{\"operation\":\"trash\",\"clip\":" + VoltConfig.JSON.toJson(c) + "}");
        Files.move(c.path(directory), slot.resolve(c.file)); // Process dies between video and metadata moves.
        library = new ClipLibrary(directory);
        assertTrue(Files.isRegularFile(slot.resolve(c.id + ".json")));
        assertTrue(library.list().isEmpty()); assertEquals(1, library.trash().size());
        Files.writeString(slot.resolve(".move.json"), "{\"operation\":\"restore\",\"clip\":" + VoltConfig.JSON.toJson(c) + "}");
        Files.move(slot.resolve(c.file), c.path(directory)); // Process dies during restore.
        library = new ClipLibrary(directory);
        assertEquals("original video", Files.readString(c.path(directory)));
        assertTrue(library.trash().isEmpty()); assertEquals(0, library.list().getFirst().trashedAt);
        assertEquals(c.markers, library.list().getFirst().markers);
    }
    private Clip fixture(ClipLibrary library) throws Exception {
        Clip c = new Clip(); c.id = "clip-1"; c.file = c.id + ".mp4";
        c.title = "Clip"; c.world = "World"; c.player = "VoltTester";
        c.shareUrl = "https://example.com/clip"; c.markers.add(new Clip.Marker(1.5, "combo"));
        Files.writeString(c.path(library.directory()), "original video"); library.save(c);
        return c;
    }
}
