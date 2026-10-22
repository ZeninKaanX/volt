package dev.volt.recording;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VideoExportTest {
 @TempDir Path dir;
 @Test void rendersPortraitTrimAtDoubleSpeedWithAudio()throws Exception{
  Assumptions.assumeTrue(Ffmpeg.probe(List.of("ffmpeg","-version"),dir.resolve("probe.log"),5));Path input=dir.resolve("in.mp4"),output=dir.resolve("out.mp4");
  Ffmpeg.run(List.of("ffmpeg","-y","-v","error","-f","lavfi","-i","testsrc2=size=320x180:rate=30","-f","lavfi","-i","sine=frequency=500","-t","4","-c:v","libx264","-threads","1","-c:a","aac",input.toString()),dir.resolve("source.log"));
  Ffmpeg.run(VideoExport.command("ffmpeg",input,output,1,3,new VideoExport.Settings(720,30,2,.25,true)),dir.resolve("export.log"));
  Process p=new ProcessBuilder("ffprobe","-v","error","-show_entries","format=duration:stream=codec_type,width,height","-of","json",output.toString()).start();String result=new String(p.getInputStream().readAllBytes());assertEquals(0,p.waitFor());
  var json=com.google.gson.JsonParser.parseString(result).getAsJsonObject();double seconds=json.getAsJsonObject("format").get("duration").getAsDouble();assertTrue(seconds>.9&&seconds<1.2,result);
  var video=json.getAsJsonArray("streams").get(0).getAsJsonObject();assertEquals(180,video.get("height").getAsInt());assertTrue(video.get("width").getAsInt()<110);assertTrue(result.contains("audio"));
 }
}
