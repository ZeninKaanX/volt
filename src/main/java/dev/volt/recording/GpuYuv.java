package dev.volt.recording;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Pack limited-range BT.709 NV12 on GPU, cutting readback bytes by 62.5%. */
final class GpuYuv implements AutoCloseable {
    private int fbo,texture,program,vao,sourceUniform,sizeUniform;
    GpuYuv(int w,int h){
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try{
            texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_R8,w,h*3/2,0,GL11.GL_RED,GL11.GL_UNSIGNED_BYTE,0L);
            fbo=GL30.glGenFramebuffers();GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
            if(GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("NV12 framebuffer incomplete");
            int vs=shader(GL20.GL_VERTEX_SHADER,"#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}");
            int fs=shader(GL20.GL_FRAGMENT_SHADER,"""
                #version 330 core
                uniform sampler2D source;uniform vec2 size;out float value;
                vec3 rgb(vec2 pixel){return texture(source,(pixel+vec2(.5))/size).rgb;}
                void main(){
                    vec2 p=floor(gl_FragCoord.xy);
                    if(p.y<size.y){value=16.0/255.0+dot(rgb(p),vec3(.182586,.614231,.062007));}
                    else{
                        vec2 q=vec2(floor(p.x/2.0)*2.0,(p.y-size.y)*2.0);
                        vec3 c=(rgb(q)+rgb(q+vec2(1,0))+rgb(q+vec2(0,1))+rgb(q+vec2(1,1)))*.25;
                        value=128.0/255.0+dot(c,mod(p.x,2.0)<.5?vec3(-.100644,-.338572,.439216):vec3(.439216,-.398942,-.040274));
                    }
                }
                """);
            program=GL20.glCreateProgram();GL20.glAttachShader(program,vs);GL20.glAttachShader(program,fs);GL20.glLinkProgram(program);GL20.glDeleteShader(vs);GL20.glDeleteShader(fs);
            if(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            sourceUniform=GL20.glGetUniformLocation(program,"source");sizeUniform=GL20.glGetUniformLocation(program,"size");
            vao=GL30.glGenVertexArrays();
        }catch(RuntimeException e){close();throw e;}finally{GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);}
    }
    private static int shader(int type,String source){int s=GL20.glCreateShader(type);GL20.glShaderSource(s,source);GL20.glCompileShader(s);if(GL20.glGetShaderi(s,GL20.GL_COMPILE_STATUS)==0){String error=GL20.glGetShaderInfoLog(s);GL20.glDeleteShader(s);throw new IllegalStateException(error);}return s;}
    int render(int input,int w,int h){
        int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),oldVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),sampler=GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        try(var stack=MemoryStack.stackPush()){
            var viewport=stack.mallocInt(4);GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);var mask=stack.malloc(4);GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK,mask);
            try{
                GL11.glDisable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glColorMask(true,true,true,true);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,fbo);GL11.glViewport(0,0,w,h*3/2);GL20.glUseProgram(program);
                GL20.glUniform1i(sourceUniform,0);GL20.glUniform2f(sizeUniform,w,h);
                GL33.glBindSampler(0,0);GL11.glBindTexture(GL11.GL_TEXTURE_2D,input);GL30.glBindVertexArray(vao);GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);
            }finally{
                GL11.glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
                GL20.glUseProgram(oldProgram);GL30.glBindVertexArray(oldVao);GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);GL33.glBindSampler(0,sampler);GL13.glActiveTexture(active);
                if(blend)GL11.glEnable(GL11.GL_BLEND);if(depth)GL11.glEnable(GL11.GL_DEPTH_TEST);if(cull)GL11.glEnable(GL11.GL_CULL_FACE);
            }
        }
        return fbo;
    }
    public void close(){if(vao!=0)GL30.glDeleteVertexArrays(vao);if(program!=0)GL20.glDeleteProgram(program);if(fbo!=0)GL30.glDeleteFramebuffers(fbo);if(texture!=0)GL11.glDeleteTextures(texture);vao=program=fbo=texture=0;}
}
