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
}
