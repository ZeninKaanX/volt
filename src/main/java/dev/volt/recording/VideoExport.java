package dev.volt.recording;
import java.nio.file.Path;
import java.util.*;
public final class VideoExport {
 private VideoExport(){}
 public record Settings(int height,int fps,double speed,double volume,boolean portrait){
  public Settings{if(!Set.of(0,540,720,1080).contains(height)||!Set.of(0,24,30,60).contains(fps)||!Double.isFinite(speed)||speed<.5||speed>2||!Double.isFinite(volume)||volume<0||volume>1)throw new IllegalArgumentException("Invalid export settings");}
  public static Settings original(){return new Settings(0,0,1,1,false);}
 }
 public static List<String> command(String ffmpeg,Path input,Path output,double start,double end,Settings s){
  var a=new ArrayList<>(List.of(ffmpeg,"-hide_banner","-loglevel","error","-y","-filter_threads","1","-ss",""+start,"-t",""+(end-start),"-i",input.toString(),"-map","0:v:0","-map","0:a?"));
  List<String> filters=new ArrayList<>();
  if(s.portrait)filters.add("crop='min(iw,ih*9/16)':ih");
  if(s.height>0)filters.add("scale=-2:'min(ih,"+s.height+")'");
  if(s.speed!=1)filters.add("setpts=PTS/"+s.speed);
  if(!filters.isEmpty())a.addAll(List.of("-vf",String.join(",",filters)));
  if(s.fps>0)a.addAll(List.of("-r",""+s.fps));
  a.addAll(List.of("-af","volume="+s.volume+",atempo="+s.speed,"-c:v","libx264","-preset","veryfast","-threads","2","-crf","20","-c:a","aac","-movflags","+faststart",output.toString()));return a;
 }
}
