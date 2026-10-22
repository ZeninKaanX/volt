package dev.volt.recording;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.*;

/** Bounded asynchronous readback: persistent maps where supported, newest ready frame only. */
public final class GpuCapture implements AutoCloseable {
    private int fbo,color,width,height;
    private final int[] buffers=new int[3];
    private final long[] fences=new long[3],serial=new long[3];
    private final ByteBuffer[] mapped=new ByteBuffer[3];
    private long nextFrame,sequence;
    private GpuYuv yuv;
    private boolean nv12;
    private int byteCount;
    public long missed,captured,discarded;
    public double lastCaptureMillis,averageCaptureMillis;
    public boolean persistentReadback;
    public void capture(ReplayRecorder recorder,int sourceWidth,int sourceHeight){
        if(recorder.state!=ReplayRecorder.State.BUFFERING||recorder.frames==null){if(fbo!=0)close();return;}
        long now=System.nanoTime();if(now<nextFrame||sourceWidth<=0||sourceHeight<=0)return;
        nextFrame=now+1_000_000_000L/recorder.fps();
        int read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int pack=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING),alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int row=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH),skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS),skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int renderbuffer=GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try{
            if(fbo==0||width!=recorder.width||height!=recorder.height||nv12!=recorder.nv12)allocate(recorder.width,recorder.height,recorder.nv12);
            int newest=-1,free=-1;
            for(int i=0;i<3;i++){
                if(fences[i]!=0){
                    int status=GL32.glClientWaitSync(fences[i],0,0);
                    if(status==GL32.GL_WAIT_FAILED)throw new IllegalStateException("GPU readback failed");
                    if(status==GL32.GL_ALREADY_SIGNALED||status==GL32.GL_CONDITION_SATISFIED){
                        if(newest<0||serial[i]>serial[newest]){if(newest>=0)discarded++;newest=i;}else discarded++;
                        GL32.glDeleteSync(fences[i]);fences[i]=0;
                    }
                }
                if(fences[i]==0)free=i;
            }
            if(newest>=0){
                byte[] bytes=recorder.frames.acquire();
                if(bytes!=null){
                    boolean offered=false;
                    try{
                        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,buffers[newest]);
                        ByteBuffer data=mapped[newest];
                        if(data==null)data=GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER,0,byteCount,GL30.GL_MAP_READ_BIT);
                        if(data!=null){data.get(0,bytes,0,bytes.length);if(mapped[newest]==null)GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);recorder.frames.offer(bytes);offered=true;captured++;}
                    }finally{if(!offered)recorder.frames.release(bytes);}
                }else missed++;
            }
            if(free<0){missed++;return;}
            GL11.glDisable(GL11.GL_SCISSOR_TEST);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);
            GL30.glBlitFramebuffer(0,0,sourceWidth,sourceHeight,0,height,width,0,GL11.GL_COLOR_BUFFER_BIT,GL11.GL_LINEAR);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,nv12?yuv.render(color,width,height):fbo);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,buffers[free]);
            GL11.glReadPixels(0,0,width,nv12?height*3/2:height,nv12?GL11.GL_RED:GL12.GL_BGRA,nv12?GL11.GL_UNSIGNED_BYTE:GL12.GL_UNSIGNED_INT_8_8_8_8_REV,0L);
            fences[free]=GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE,0);serial[free]=++sequence;
        }finally{
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pack);GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,row);GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER,renderbuffer);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);if(scissor)GL11.glEnable(GL11.GL_SCISSOR_TEST);
            lastCaptureMillis=(System.nanoTime()-now)/1e6;averageCaptureMillis=averageCaptureMillis==0?lastCaptureMillis:averageCaptureMillis*.95+lastCaptureMillis*.05;
        }
    }
    private void allocate(int w,int h,boolean useNv12){
        close();width=w;height=h;nv12=useNv12;byteCount=w*h*(nv12?3:8)/2;
        var caps=GL.getCapabilities();persistentReadback=caps.OpenGL44||caps.GL_ARB_buffer_storage;
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),unpack=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
        try{
        fbo=GL30.glGenFramebuffers();color=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,0L);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Capture framebuffer incomplete");
        if(nv12)yuv=new GpuYuv(w,h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);
        for(int i=0;i<3;i++){
            buffers[i]=GL15.glGenBuffers();GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,buffers[i]);
            if(persistentReadback){
                int flags=GL30.GL_MAP_READ_BIT|GL44.GL_MAP_PERSISTENT_BIT|GL44.GL_MAP_COHERENT_BIT;
                GL44.glBufferStorage(GL21.GL_PIXEL_PACK_BUFFER,byteCount,flags);
                mapped[i]=GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER,0,byteCount,flags);
                if(mapped[i]==null)throw new IllegalStateException("Persistent capture mapping failed");
            }else GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER,byteCount,GL15.GL_STREAM_READ);
        }
        }finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,unpack);}
    }
    @Override public void close(){
        int previous=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        for(int i=0;i<3;i++){
            if(fences[i]!=0)GL32.glDeleteSync(fences[i]);
            if(mapped[i]!=null){GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,buffers[i]);GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);mapped[i]=null;}
            if(buffers[i]!=0){if(previous==buffers[i])previous=0;GL15.glDeleteBuffers(buffers[i]);}fences[i]=0;buffers[i]=0;
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,previous);
        if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(color!=0)GL11.glDeleteTextures(color);if(yuv!=null){yuv.close();yuv=null;}fbo=color=0;
    }
}
