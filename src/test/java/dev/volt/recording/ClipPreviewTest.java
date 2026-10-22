package dev.volt.recording;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClipPreviewTest {
    @TempDir Path directory;
    @Test void actualVideoCanPlayPauseSeekAndReleaseItsDecoder() throws Exception {
        Assumptions.assumeTrue(Ffmpeg.probe(List.of("ffmpeg","-version"),directory.resolve("probe.log"),5));
        Path video=directory.resolve("preview.mp4");
        Ffmpeg.run(List.of("ffmpeg","-y","-f","lavfi","-i","testsrc2=size=64x36:rate=24","-t","3","-threads","1","-c:v","libx264",video.toString()),directory.resolve("create.log"));
        try(ClipPreview decoder=new ClipPreview("ffmpeg",64,36,24)){
            decoder.open(video,3);decoder.play();
            ReplayRecorderTest.await(()->decoder.position>.1,5000);
            var frame=decoder.take();assertNotNull(frame);assertEquals(64*36*4,frame.data().length);frame.release();
            decoder.pause();double at=decoder.position;Thread.sleep(150);assertEquals(at,decoder.position,.05);assertFalse(decoder.playing);
            decoder.seek(1.25);ReplayRecorderTest.await(()->!decoder.loading,5000);
            assertEquals("",decoder.error);assertEquals(1.25,decoder.position,.05);assertFalse(decoder.playing);
            frame=decoder.take();assertNotNull(frame);frame.release();
            decoder.play();ReplayRecorderTest.await(()->decoder.position>1.35,5000);
            assertTrue(decoder.playing);decoder.pause();
        }
    }
}
