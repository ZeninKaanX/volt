package dev.volt.ui;

import dev.volt.*;
import dev.volt.recording.ClipPreview;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;

/** Texture upload runs on the render thread; decoding stays on its own bounded worker. */
public final class PreviewTexture implements AutoCloseable {
    public final ClipPreview decoder;
    private final Identifier id=Identifier.fromNamespaceAndPath("volt","preview/"+Integer.toHexString(System.identityHashCode(this)));
    private String clip="";
    private SmoothTexture texture;
    private boolean hasFrame;
    public PreviewTexture(String ffmpeg){decoder=new ClipPreview(ffmpeg);}
    public void select(Clip value){if(clip.equals(value.id))return;clip=value.id;hasFrame=false;decoder.open(value.path(VoltClient.INSTANCE.library.directory()),value.seconds);}
    public Identifier frame(){
        var next=decoder.take();
        if(next!=null){
            try{
                if(texture==null){texture=new SmoothTexture("Volt clip playback",new NativeImage(decoder.width,decoder.height,false));Minecraft.getInstance().getTextureManager().register(id,texture);}
                NativeImage pixels=texture.getPixels();MemoryUtil.memByteBuffer(pixels.getPointer(),next.data().length).put(next.data());texture.upload();hasFrame=true;
            }finally{next.release();}
        }
        return hasFrame?id:null;
    }
    @Override public void close(){decoder.close();if(texture!=null)Minecraft.getInstance().getTextureManager().release(id);texture=null;}
}
