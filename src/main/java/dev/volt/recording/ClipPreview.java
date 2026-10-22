package dev.volt.recording;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** On-demand, bounded silent preview decoder. Never runs during ordinary gameplay. */
public final class ClipPreview implements AutoCloseable {
    public record Frame(byte[] data,FrameMailbox owner){public void release(){owner.release(data);}}
    private final String ffmpeg;
    public final int width,height,fps;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Volt-preview").factory());
    private volatile FrameMailbox mailbox;
    private volatile Process process;
    private volatile long generation;
    private volatile boolean closed;
    private Path file;
    private double duration;
    public volatile double position;
    public volatile boolean playing,loading;
    public volatile String error="";
    public ClipPreview(String ffmpeg){this(ffmpeg,960,540,24);}
    public ClipPreview(String ffmpeg,int width,int height,int fps){this.ffmpeg=ffmpeg;this.width=width;this.height=height;this.fps=fps;}
    public synchronized void open(Path file,double duration){stop();this.file=file;this.duration=Math.max(.01,duration);position=0;error="";mailbox=null;}
    public synchronized void play(){if(file!=null&&!closed)decode(true,position>=duration-.05?0:position);}
    public synchronized void pause(){stop();}
    public synchronized void seek(double second){if(file!=null&&!closed)decode(playing,Math.clamp(second,0,Math.max(0,duration-.01)));}
    public synchronized void toggle(){if(playing)pause();else play();}
    private void stop(){generation++;playing=false;loading=false;Process old=process;process=null;if(old!=null)old.destroy();}
    private void decode(boolean continuous,double start){
        stop();long token=generation;playing=continuous;loading=true;error="";position=start;
        Path source=file;FrameMailbox target=new FrameMailbox(width*height*4);mailbox=target;
        worker.submit(()->{
            if(token!=generation||closed)return;
            Process active=null;
            try{
                List<String> args=new ArrayList<>(List.of(ffmpeg,"-hide_banner","-loglevel","error","-nostdin"));
                if(continuous)args.add("-re");
                args.addAll(List.of("-ss",""+start,"-threads","1","-i",source.toString(),"-an","-sn","-dn","-vf",
                    "fps="+fps+",scale="+width+":"+height+":force_original_aspect_ratio=decrease,pad="+width+":"+height+":(ow-iw)/2:(oh-ih)/2",
                    "-threads","1","-filter_threads","1"));
                if(!continuous)args.addAll(List.of("-frames:v","1"));
                args.addAll(List.of("-f","rawvideo","-pix_fmt","rgba","pipe:1"));
                active=new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                synchronized(this){if(token!=generation||closed){active.destroy();return;}process=active;}
                long count=0;
                try(var input=active.getInputStream()){
                    while(token==generation&&!closed){
                        byte[] data=target.acquire();if(data==null){Thread.sleep(10);continue;}
                        int n;
                        try{n=input.readNBytes(data,0,data.length);}catch(Exception e){target.release(data);throw e;}
                        if(n!=data.length){target.release(data);break;}
                        if(token!=generation){target.release(data);break;}
                        target.offer(data);position=Math.min(duration,start+(count++)/(double)fps);loading=false;
                    }
                }
                if(token==generation){
                    if(!active.waitFor(3,TimeUnit.SECONDS)||active.exitValue()!=0)error="Preview decoder failed";
                    else if(continuous)position=duration;
                }
            }catch(Exception e){if(token==generation)error=e.getMessage()==null?"Preview decoder failed":e.getMessage();}
            finally{
                if(active!=null&&active.isAlive())active.destroyForcibly();
                synchronized(this){if(token==generation){process=null;playing=false;loading=false;}}
            }
        });
    }
    public Frame take(){FrameMailbox m=mailbox;if(m==null)return null;byte[] data=m.take();return data==null?null:new Frame(data,m);}
    @Override public synchronized void close(){closed=true;stop();worker.shutdownNow();mailbox=null;}
}
