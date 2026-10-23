#!/usr/bin/env python3
"""Generate explicit Minecraft API adaptations; never changes the main sources."""
import argparse
import shutil
import re
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGETS = json.loads((ROOT / 'minecraft-targets.json').read_text())
LEGACY_INPUT = {version for version, target in TARGETS.items() if target['adapter'] in ['legacy-input', 'legacy-pose']}
POSE_STACK = {version for version, target in TARGETS.items() if target['adapter'] == 'legacy-pose'}


def java17(text):
    text = text.replace('Math.clamp(', 'dev.volt.compat.LegacyMath.clamp(').replace('.getFirst()', '.get(0)')
    text = re.sub(r'Thread\.ofPlatform\(\)\.daemon\(\)\.name\(("[^"]+")\)\.factory\(\)',
                  r'dev.volt.compat.LegacyThreads.factory(\1)', text)
    text = re.sub(r'Thread\.ofVirtual\(\)\.name\(("[^"]+")\)\.start\(',
                  r'dev.volt.compat.LegacyThreads.start(\1,', text)
    text = text.replace('try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build()) {',
                        '{ HttpClient client = dev.volt.compat.LegacyHttp.CLIENT;')
    return text


def named_source(text, filename, mc):
    text = text.replace('GuiGraphics', 'GuiGraphicsExtractor')
    text = text.replace('renderBackground(', 'extractBackground(')
    text = text.replace('renderContents(', 'extractContents(')
    text = text.replace('renderWidget(', 'extractWidgetRenderState(')
    text = text.replace('net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper',
                        'net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper')
    text = text.replace('KeyBindingHelper.registerKeyBinding(', 'KeyMappingHelper.registerKeyMapping(')
    text = text.replace('ScreenEvents.afterRender(', 'ScreenEvents.afterExtract(')
    text = text.replace('Screens.getButtons(', 'Screens.getWidgets(')
    text = text.replace('g.drawString(', 'g.text(')
    if filename in ['VoltScreen.java', 'SmokeClient.java']:
        text = text.replace('public void render(', 'public void extractRenderState(')
        text = text.replace('super.render(', 'super.extractRenderState(')
    text = text.replace('launcher.render(', 'launcher.extractRenderState(')
    if filename == 'SmokeClient.java':
        text = text.replace('mc.resizeDisplay()', 'mc.resizeGui()')
        text = text.replace('false, Difficulty.PEACEFUL, true,\n                    new net.minecraft.world.level.gamerules.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT)',
            'new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false), true, WorldDataConfiguration.DEFAULT)')
    if mc in ['26.2', '26.3']:
        text = text.replace('.setScreen(', '.gui.setScreen(')
        text = re.sub(r'\b(mc|minecraft)\.screen\b', r'\1.gui.screen()', text)
        text = text.replace('.getMainRenderTarget()', '.gameRenderer.mainRenderTarget()')
    if mc == '26.3':
        text = text.replace('com.mojang.blaze3d.textures.FilterMode', 'com.mojang.renderpearl.api.textures.FilterMode')
    return text


def legacy_input(text):
    text = text.replace('net.minecraft.client.input.', 'dev.volt.compat.input.')
    text = text.replace('k.getCategory().id().getPath().equals("debug")',
                        'k.getName().startsWith("key.debug.")')
    text = text.replace('KeyMapping.Category category = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath("volt", "controls"));',
                        'String category = "Volt";')
    text = text.replace('(s,event)->!launcher.mouseClicked(event,false)',
                        '(s,x,y,button)->!launcher.mouseClicked(x,y,button)')
    text = text.replace('FontDescription FONT = new FontDescription.Resource(ResourceLocation.fromNamespaceAndPath("volt", "ui"))',
                        'ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("volt", "ui")')
    text = text.replace('.getWindow().handle()', '.getWindow().getWindow()')
    event = 'dev.volt.compat.input.MouseButtonEvent'
    info = 'dev.volt.compat.input.MouseButtonInfo'
    key = 'dev.volt.compat.input.KeyEvent'
    point = f'new {event}(x,y,new {info}(button,0))'
    wrappers = {
        'onClick': ('public void', 'double x,double y', f'onClick(new {event}(x,y,new {info}(0,0)),false);'),
        'onDrag': ('protected void', 'double x,double y,double dx,double dy', f'onDrag(new {event}(x,y,new {info}(0,0)),dx,dy);'),
        'mouseClicked': ('public boolean', 'double x,double y,int button', f'return mouseClicked({point},false);'),
        'mouseReleased': ('public boolean', 'double x,double y,int button', f'return mouseReleased({point});'),
        'mouseDragged': ('public boolean', 'double x,double y,int button,double dx,double dy', f'return mouseDragged({point},dx,dy);'),
        'keyPressed': ('public boolean', 'int code,int scan,int modifiers', f'return keyPressed(new {key}(code,scan,modifiers));'),
    }
    def bridge(match):
        header = match[0]
        if 'MouseButtonEvent' not in header and 'KeyEvent' not in header:
            return header
        signature, params, body = wrappers[match['name']]
        return f'@Override {signature} {match["name"]}({params}){{{body}}}\n    ' + header.replace('@Override ', '', 1)
    pattern = r'@Override\s+(?:public|protected)\s+(?:void|boolean)\s+(?P<name>onClick|onDrag|mouseClicked|mouseReleased|mouseDragged|keyPressed)\([^)]*\)'
    text = re.sub(pattern, bridge, text)
    text = re.sub(r'super\.onClick\((event|e),\s*(?:doubleClick|dbl)\)', r'super.onClick(\1.x(),\1.y())', text)
    text = text.replace('.mouseClicked(e,dbl)', '.mouseClicked(e.x(),e.y(),e.button())')
    text = text.replace('super.mouseReleased(e)', 'super.mouseReleased(e.x(),e.y(),e.button())')
    text = text.replace('super.mouseReleased(scaled(e))', 'super.mouseReleased(e.x()/uiScale,e.y()/uiScale,e.button())')
    text = text.replace('super.mouseDragged(scaled(e),dx/uiScale,dy/uiScale)', 'super.mouseDragged(e.x()/uiScale,e.y()/uiScale,e.button(),dx/uiScale,dy/uiScale)')
    text = text.replace('super.keyPressed(key)', 'super.keyPressed(key.key(),key.scancode(),key.modifiers())')
    return text


def legacy_pose(text):
    text = text.replace('g.pose().pushMatrix()', 'g.pose().pushPose()').replace('g.pose().popMatrix()', 'g.pose().popPose()')
    text = text.replace('g.pose().scale(uiScale,uiScale)', 'g.pose().scale(uiScale,uiScale,1)')
    text = text.replace('g.pose().scale(scale,scale)', 'g.pose().scale(scale,scale,1)')
    text = text.replace('g.pose().translate((float)getX(),(float)getY())', 'g.pose().translate((float)getX(),(float)getY(),0)')
    text = text.replace('g.pose().scale(1f/mc.getWindow().getGuiScale(),1f/mc.getWindow().getGuiScale())',
                        'g.pose().scale((float)(1f/mc.getWindow().getGuiScale()),(float)(1f/mc.getWindow().getGuiScale()),1)')
    text = text.replace('g.pose().m00()', 'g.pose().last().pose().m00()')
    text = text.replace('float scale=1f/mc.getWindow().getGuiScale()', 'float scale=(float)(1f/mc.getWindow().getGuiScale())')
    text = text.replace('float scale=1f/Minecraft.getInstance().getWindow().getGuiScale()', 'float scale=(float)(1f/Minecraft.getInstance().getWindow().getGuiScale())')
    text = text.replace('f.setTextShadow(false);', '')
    text = text.replace('mc.getMainRenderTarget(), 1, c ->', 'mc.getMainRenderTarget(), c ->')
    text = text.replace('player.hurtServer(player.level(),', 'player.hurtServer((net.minecraft.server.level.ServerLevel)player.level(),')
    text = text.replace('import com.mojang.blaze3d.textures.FilterMode;\n', '')
    text = text.replace('import com.mojang.blaze3d.systems.RenderSystem;\n', '')
    text = text.replace('g.blit(', 'dev.volt.compat.LegacyGui.blit(g,')
    text = text.replace('import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;',
                        'import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;')
    text = text.replace('HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath("volt", "status"),',
                        'HudRenderCallback.EVENT.register(')
    return text


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('minecraft', choices=[v for v in TARGETS if v != '1.21.11'])
    args = parser.parse_args()
    output = ROOT / 'build/ports' / args.minecraft / 'java'
    if output.exists():
        shutil.rmtree(output)
    for source in (ROOT / 'src/main/java').rglob('*.java'):
        target = output / source.relative_to(ROOT / 'src/main/java')
        target.parent.mkdir(parents=True, exist_ok=True)
        if TARGETS[args.minecraft]['adapter'] == 'named':
            target.write_text(named_source(source.read_text(), source.name, args.minecraft))
            continue
        text = source.read_text().replace('Identifier', 'ResourceLocation')
        text = text.replace('net.minecraft.util.Util', 'net.minecraft.Util')
        text = text.replace('.dimension().identifier()', '.dimension().location()')
        text = text.replace('k.getCategory().equals(KeyMapping.Category.DEBUG)',
                            'k.getCategory().id().getPath().equals("debug")')
        text = text.replace('renderContents(', 'renderWidget(')
        text = text.replace('void resize(int w,int h){physicalWidth=w;physicalHeight=h;super.resize(w,h);}',
                            'void resize(Minecraft client,int w,int h){physicalWidth=w;physicalHeight=h;super.resize(client,w,h);}')
        text = text.replace('this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);',
                            'setFilter(true, false); setClamp(true);')
        if args.minecraft in LEGACY_INPUT:
            text = legacy_input(text)
        if args.minecraft in POSE_STACK:
            text = legacy_pose(text)
        if TARGETS[args.minecraft].get('legacy_window'):
            text = text.replace('mc.getWindow().isMinimized()',
                '(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_ICONIFIED)!=0)')
            text = text.replace('setClamp(true);',
                'bind(); org.lwjgl.opengl.GL11.glTexParameteri(3553,10242,33071); org.lwjgl.opengl.GL11.glTexParameteri(3553,10243,33071);')
            text = text.replace('g.enableScissor(', 'dev.volt.compat.LegacyGui.enableScissor(g,')
        if TARGETS[args.minecraft].get('legacy_texture'):
            text = text.replace('super(() -> label, image);', 'super(image);')
            text = text.replace('image.getPointer()', 'dev.volt.compat.NativePixels.address(image)')
            text = text.replace('pixels.getPointer()', 'dev.volt.compat.NativePixels.address(pixels)')
        if TARGETS[args.minecraft].get('legacy_identifier'):
            text = text.replace('ResourceLocation.fromNamespaceAndPath(', 'new ResourceLocation(')
            text = text.replace('net.minecraft.client.gui.screens.options.controls.KeyBindsScreen',
                                'net.minecraft.client.gui.screens.controls.KeyBindsScreen')
        if args.minecraft in ['1.20', '1.20.1']:
            text = text.replace('void renderBackground(GuiGraphics g,int mx,int my,float delta)',
                                'void renderBackground(GuiGraphics g)')
            text = text.replace('mouseScrolled(double x,double y,double dx,double dy)',
                                'mouseScrolled(double x,double y,double dy)')
            text = text.replace('super.mouseScrolled(x/uiScale,y/uiScale,dx,dy)',
                                'super.mouseScrolled(x/uiScale,y/uiScale,dy)')
        if TARGETS[args.minecraft]['java'] == 17:
            text = java17(text)
        target.write_text(text)
    if args.minecraft in LEGACY_INPUT:
        folder = output / 'dev/volt/compat/input'
        folder.mkdir(parents=True, exist_ok=True)
        for name, declaration in {
            'MouseButtonInfo': 'public record MouseButtonInfo(int button,int modifiers) {}',
            'MouseButtonEvent': 'public record MouseButtonEvent(double x,double y,MouseButtonInfo buttonInfo) { public int button(){return buttonInfo.button();} }',
            'KeyEvent': 'public record KeyEvent(int key,int scancode,int modifiers) {}',
        }.items():
            (folder / (name + '.java')).write_text('package dev.volt.compat.input;\n' + declaration + '\n')
    if args.minecraft in POSE_STACK:
        (output / 'dev/volt/compat/LegacyGui.java').write_text('''package dev.volt.compat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
/** Normalize UV coordinates without texture queries or per-frame allocations. */
public final class LegacyGui {
 public static void blit(GuiGraphics g,ResourceLocation id,int x0,int y0,int x1,int y1,float u0,float u1,float v0,float v1){
  int grid=65536;
  g.blit(RenderType::guiTextured,id,x0,y0,u0*grid,v0*grid,x1-x0,y1-y0,Math.round((u1-u0)*grid),Math.round((v1-v0)*grid),grid,grid);
 }
}
''')
        if TARGETS[args.minecraft].get('direct_gui'):
            gui = output / 'dev/volt/compat/LegacyGui.java'
            gui.write_text(gui.read_text().replace('import net.minecraft.client.renderer.RenderType;\n', '')
                .replace('g.blit(RenderType::guiTextured,id,x0,y0,u0*grid,v0*grid,x1-x0,y1-y0,Math.round((u1-u0)*grid),Math.round((v1-v0)*grid),grid,grid);',
                    '''com.mojang.blaze3d.systems.RenderSystem.enableBlend();
  com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
  try { g.blit(id,x0,y0,x1-x0,y1-y0,u0*grid,v0*grid,Math.round((u1-u0)*grid),Math.round((v1-v0)*grid),grid,grid); }
  finally { com.mojang.blaze3d.systems.RenderSystem.disableBlend(); }'''))
        if TARGETS[args.minecraft].get('legacy_window'):
            gui = output / 'dev/volt/compat/LegacyGui.java'
            gui.write_text(gui.read_text().replace('\n}\n', '''
 /** Pre-1.21.4 clipping ignores the active GUI pose; transform its bounds once. */
 public static void enableScissor(GuiGraphics g,int x0,int y0,int x1,int y1){
  var pose=g.pose().last().pose();
  g.enableScissor((int)Math.floor(x0*pose.m00()+pose.m30()),(int)Math.floor(y0*pose.m11()+pose.m31()),
    (int)Math.ceil(x1*pose.m00()+pose.m30()),(int)Math.ceil(y1*pose.m11()+pose.m31()));
 }
}
'''))
    if TARGETS[args.minecraft].get('legacy_texture'):
        (output / 'dev/volt/mixin/NativeImageAccess.java').write_text('''package dev.volt.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(NativeImage.class)
public interface NativeImageAccess { @Accessor("pixels") long volt$pixels(); }
''')
        (output / 'dev/volt/compat/NativePixels.java').write_text('''package dev.volt.compat;
import com.mojang.blaze3d.platform.NativeImage;
import dev.volt.mixin.NativeImageAccess;
public final class NativePixels { public static long address(NativeImage image){return ((NativeImageAccess)(Object)image).volt$pixels();} }
''')
    if TARGETS[args.minecraft]['java'] == 17:
        (output / 'dev/volt/compat/LegacyMath.java').write_text('''package dev.volt.compat;
public final class LegacyMath {
 public static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
 public static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
 public static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}
''')
        (output / 'dev/volt/compat/LegacyThreads.java').write_text('''package dev.volt.compat;
import java.util.concurrent.ThreadFactory;
/** Java 17 daemon workers with the same ownership and lifecycle as newer builds. */
public final class LegacyThreads {
 public static ThreadFactory factory(String name){return task->{Thread thread=new Thread(task,name);thread.setDaemon(true);return thread;};}
 public static Thread start(String name,Runnable task){Thread thread=factory(name).newThread(task);thread.start();return thread;}
}
''')
        (output / 'dev/volt/compat/LegacyHttp.java').write_text('''package dev.volt.compat;
import java.net.http.HttpClient;
import java.time.Duration;
/** Java 17 HttpClient is not AutoCloseable; reuse one client for the process. */
public final class LegacyHttp {
 public static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
}
''')
    smoke = output.parent / 'smoke'
    if smoke.exists():
        shutil.rmtree(smoke)
    for source in (ROOT / 'src/smoke/java').rglob('*.java'):
        target = smoke / source.relative_to(ROOT / 'src/smoke/java')
        target.parent.mkdir(parents=True, exist_ok=True)
        if TARGETS[args.minecraft]['adapter'] == 'named':
            target.write_text(named_source(source.read_text(), source.name, args.minecraft))
            continue
        text = source.read_text().replace('net.minecraft.world.level.gamerules.GameRules', 'net.minecraft.world.level.GameRules')
        if args.minecraft in LEGACY_INPUT:
            text = legacy_input(text)
            for name in ['mouseClicked', 'mouseDragged', 'mouseReleased', 'keyPressed']:
                text = text.replace('mc.screen.' + name + '(', 'LegacyEvents.' + name + '(mc.screen,')
            text = text.replace('range.mouseDragged(', 'LegacyEvents.mouseDragged(range,')
            text = text.replace('net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen,', 'LegacyEvents.allow(screen,')
        if args.minecraft in POSE_STACK:
            text = legacy_pose(text)
        if TARGETS[args.minecraft].get('direct_gui'):
            text = text.replace('player.hurtServer((net.minecraft.server.level.ServerLevel)player.level(),', 'player.hurt(')
            text = text.replace('mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);', '')
            text = text.replace('mc.getToastManager()', 'mc.getToasts()')
            text = text.replace('new net.minecraft.world.level.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures())',
                                'new net.minecraft.world.level.GameRules()')
            text = text.replace('WorldPresets::createFlatWorldDimensions',
                'access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions()')
        if args.minecraft in ['1.20', '1.20.1', '1.20.2']:
            text = text.replace('.createWorldDimensions(), new TitleScreen());', '.createWorldDimensions());')
        if TARGETS[args.minecraft]['java'] == 17:
            text = java17(text)
        target.write_text(text)
    if args.minecraft in LEGACY_INPUT:
        (smoke / 'dev/volt/smoke/LegacyEvents.java').write_text('''package dev.volt.smoke;
import dev.volt.compat.input.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
final class LegacyEvents {
 static boolean mouseClicked(GuiEventListener v,MouseButtonEvent e,boolean dbl){return v.mouseClicked(e.x(),e.y(),e.button());}
 static boolean mouseDragged(GuiEventListener v,MouseButtonEvent e,double dx,double dy){return v.mouseDragged(e.x(),e.y(),e.button(),dx,dy);}
 static boolean mouseReleased(GuiEventListener v,MouseButtonEvent e){return v.mouseReleased(e.x(),e.y(),e.button());}
 static boolean keyPressed(GuiEventListener v,KeyEvent e){return v.keyPressed(e.key(),e.scancode(),e.modifiers());}
 static boolean allow(Screen s,MouseButtonEvent e){return net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(s).invoker().allowMouseClick(s,e.x(),e.y(),e.button());}
}
''')
    print('Prepared source adaptation for', args.minecraft)


if __name__ == '__main__':
    main()
