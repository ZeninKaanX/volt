package dev.volt.smoke;

import dev.volt.*;
import dev.volt.ui.*;
import dev.volt.recording.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.client.input.*;
import java.nio.file.*;

/** Developer-only runtime smoke test, excluded from the distributed jar. */
public final class SmokeClient implements ClientModInitializer {
    private int stage, ticks;
    private long recordingSince;
    private boolean exportRequested;
    private long sceneSince;
    private boolean automaticTriggered;
    private int originalClipCount;
    private int librarySafetyCount;
    private boolean sessionLongStarted,sessionDisconnected;
    private double sessionLongStart;
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (stage == 0 && mc.screen != null && !(mc.screen instanceof TitleScreen) && ++ticks > 80) {
                System.out.println("VOLT_SMOKE startup screen " + mc.screen.getClass().getName());
                mc.setScreen(new TitleScreen()); ticks = 0;
            }
            if (stage == 0 && mc.screen instanceof TitleScreen) {
                VoltClient.INSTANCE.config.audioFormat = "off";
                if(System.getProperty("volt.testFfmpeg")!=null)VoltClient.INSTANCE.config.ffmpeg=System.getProperty("volt.testFfmpeg");
                VoltClient.INSTANCE.config.encoder = "auto";
                VoltClient.INSTANCE.config.enabled = false;
                mc.options.pauseOnLostFocus = false;mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                org.lwjgl.glfw.GLFW.glfwSetWindowAttrib(mc.getWindow().handle(),org.lwjgl.glfw.GLFW.GLFW_AUTO_ICONIFY,org.lwjgl.glfw.GLFW.GLFW_FALSE);
                if(Boolean.getBoolean("volt.secondaryDisplay")){
                    var monitors=org.lwjgl.glfw.GLFW.glfwGetMonitors();
                    if(monitors==null||monitors.remaining()<2)throw new IllegalStateException("Secondary display required for isolated fullscreen QA");
                    int bestX=Integer.MIN_VALUE,bestY=0;
                    try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
                        var x=stack.mallocInt(1);var y=stack.mallocInt(1);
                        for(int i=0;i<monitors.remaining();i++){
                            org.lwjgl.glfw.GLFW.glfwGetMonitorPos(monitors.get(i),x,y);
                            if(x.get(0)>bestX){bestX=x.get(0);bestY=y.get(0);}
                        }
                    }
                    org.lwjgl.glfw.GLFW.glfwSetWindowPos(mc.getWindow().handle(),bestX+100,bestY+100);
                }
                if(Boolean.getBoolean("volt.fullscreen")&&!mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();
                System.out.println("VOLT_RUNTIME fullscreen="+mc.getWindow().isFullscreen()+" framebuffer="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight());
                mc.options.enableVsync().set(false); mc.options.framerateLimit().set(Integer.getInteger("volt.testFrameLimit",260));
                mc.options.renderDistance().set(8); mc.options.simulationDistance().set(5);
                if(System.getProperty("volt.uiScale")!=null){mc.options.guiScale().set(Integer.parseInt(System.getProperty("volt.uiScale")));mc.resizeDisplay();}
                mc.options.languageCode = "tr_tr"; mc.getLanguageManager().setSelected("tr_tr"); mc.reloadResourcePacks();
                originalClipCount=VoltClient.INSTANCE.library.list().size();
                stage = Boolean.getBoolean("volt.perfTest")?2:1; ticks = 0;
            } else if (stage == 1 && ++ticks == 100) { mc.setScreen(new VoltScreen(null)); }
            else if (stage == 1 && ticks == 140) { snap(mc, "01-clips.png"); if(Boolean.getBoolean("volt.editorOnly")){click(mc,"Aç");click(mc,"Videoyu oynat");}else click(mc, "Profil"); }
            else if (stage == 1 && ticks == 180) { if(Boolean.getBoolean("volt.editorOnly")){snap(mc,"studio-player.png");click(mc,"Dışa aktar");}else {snap(mc, "02-profile.png"); click(mc, "Ayarlar");} }
            else if (stage == 1 && ticks == 220) { if(Boolean.getBoolean("volt.editorOnly")){snap(mc,"studio-export.png");System.out.println("VOLT_STUDIO editor visual check completed");mc.stop();stage=9;return;}snap(mc, "03-settings.png"); stage = Boolean.getBoolean("volt.menuTest")?7:Boolean.getBoolean("volt.uiOnly")?6:2; ticks = Boolean.getBoolean("volt.feedbackTest")?739:0; }
            else if(stage==6){
                if(Boolean.getBoolean("volt.librarySafetyTest")){librarySafetyTest(mc);return;}
                if(Boolean.getBoolean("volt.studioTest")){studioTest(mc);return;}
                ticks++;
                mc.getToastManager().clear();
                if(ticks==1)click(mc,"Paylaşım");
                if(ticks==40){snap(mc,"05-sharing.png");click(mc,"Discord");}
                if(ticks==80){snap(mc,"06-discord.png");click(mc,"Otomatik klipler");}
                if(ticks==100){snap(mc,"10-auto-settings.png");click(mc,"Klipler");}
                if(ticks==120)click(mc,"Aç");
                if(ticks==135&&mc.screen!=null)for(var child:mc.screen.children())if(child instanceof TrimRange range){
                    range.onClick(new MouseButtonEvent(range.getX()+range.getWidth()*.2,range.getY()+12,new MouseButtonInfo(0,0)),false);
                    range.mouseDragged(new MouseButtonEvent(range.getX()+range.getWidth()*.3,range.getY()+12,new MouseButtonInfo(0,0)),range.getWidth()*.1,0);
                    range.onClick(new MouseButtonEvent(range.getX()+range.getWidth()*.8,range.getY()+12,new MouseButtonInfo(0,0)),false);
                    System.out.println("VOLT_SMOKE range handles dragged");
                }
                if(ticks==160){snap(mc,"07-detail.png");click(mc,"Geri");}
                if(ticks==200){click(mc,"Klipler");click(mc,"Izgara görünümü");}
                if(ticks==240){snap(mc,"08-list.png");mc.stop();stage=9;}
            }
            else if (stage == 2 && ++ticks == 30) {
                mc.createWorldOpenFlows().createFreshLevel("Volt-Smoke-" + System.currentTimeMillis(), new LevelSettings("Volt Smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new net.minecraft.world.level.gamerules.GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(42, false, false), WorldPresets::createFlatWorldDimensions, new TitleScreen());
                stage = 3;
            } else if (stage == 3 && mc.level != null && mc.player != null) {
                mc.setScreen(null); sceneSince = System.currentTimeMillis(); stage = 4; ticks = 0;
            } else if (stage == 4) {
                long sceneAge = System.currentTimeMillis() - sceneSince;
                if(Boolean.getBoolean("volt.sessionSafetyTest")){sessionSafetyTest(mc,sceneAge);return;}
                if(Boolean.getBoolean("volt.autoTest")){automaticTest(mc,sceneAge);return;}
                if (sceneAge < 10000) return;
                if (sceneAge < 15000) { SmokeMetrics.phase = 1; return; }
                if (!VoltClient.INSTANCE.config.enabled) { SmokeMetrics.phase = 0; VoltClient.INSTANCE.config.enabled = true; }
                if (VoltClient.INSTANCE.recorder.state == ReplayRecorder.State.ERROR) { write(mc, "ERROR " + VoltClient.INSTANCE.recorder.error); mc.stop(); stage = 9; return; }
                if (VoltClient.INSTANCE.recorder.originNanos > 0 && recordingSince == 0) recordingSince = System.currentTimeMillis();
                if (recordingSince > 0) {
                    if (System.currentTimeMillis() - recordingSince > 3000) SmokeMetrics.phase = 2;
                    if (System.currentTimeMillis() - recordingSince > 10000 && !exportRequested) {
                        VoltClient.INSTANCE.recorder.mark("manual"); VoltClient.INSTANCE.recorder.saveReplay(VoltClient.INSTANCE.context()); exportRequested = true;
                    }
                    if (exportRequested && !VoltClient.INSTANCE.recorder.isExporting()) {
                        write(mc, "Recorder=" + VoltClient.INSTANCE.recorder.state + " Encoder=" + VoltClient.INSTANCE.recorder.encoder + " GPUms=" + VoltClient.INSTANCE.capture.lastCaptureMillis + captureDiagnostics()+" Missed=" + VoltClient.INSTANCE.capture.missed + " Clips=" + VoltClient.INSTANCE.library.list().size());
                        snap(mc, "04-recording.png"); SmokeMetrics.phase = 0; VoltClient.INSTANCE.config.enabled = false;
                        stage = 5; sceneSince = System.currentTimeMillis(); ticks = 0;
                    }
                }
            } else if(stage==7){
                ticks++;
                mc.getToastManager().clear();
                if(ticks==40){snap(mc,"09-auto-clips.png");click(mc,"Ayarlar");click(mc,"Otomatik klipler");}
                if(ticks==80){
                    snap(mc,"10-auto-settings.png");
                    if(Boolean.getBoolean("volt.compactTest"))openAac(mc,false);else {mc.stop();stage=9;}
                }
                if(ticks==120)snap(mc,"12-aac-menu.png");
                if(ticks==160){openAac(mc,true);dragAac(mc);}
                if(ticks==200){snap(mc,"13-aac-position.png");mc.setScreen(pauseScreen(mc));}
                if(ticks==240)snap(mc,"14-menu-icons.png");
                if(ticks==260){
                    var screen=mc.screen;
                    double gui=mc.getWindow().getGuiScale();
                    boolean passed=net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen,new MouseButtonEvent(screen.width-64/gui,35/gui,new MouseButtonInfo(0,0)));
                    if(passed||!(mc.screen instanceof VoltScreen))throw new IllegalStateException("Volt Atlas launcher failed");
                    System.out.println("VOLT_COMPAT Atlas Volt launcher clicked");
                }
                if(ticks==280)mc.setScreen(pauseScreen(mc));
                if(ticks==300){
                    var screen=mc.screen;double gui=mc.getWindow().getGuiScale();
                    boolean passed=net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen,new MouseButtonEvent(64/gui,35/gui,new MouseButtonInfo(0,0)));
                    if(passed||!mc.screen.getClass().getName().equals("dev.aac.observer.ObserverScreen"))throw new IllegalStateException("AAC Atlas launcher failed");
                    System.out.println("VOLT_COMPAT Atlas AAC launcher clicked");
                }
                if(ticks==340){mc.stop();stage=9;}
            } else if (stage == 10 && ++ticks == 40) {
                snap(mc,"v7-session-saved.png");mc.stop();stage=9;
            } else if (stage == 5) {
                long age = System.currentTimeMillis() - sceneSince;
                if (age > 3000) SmokeMetrics.phase = 3;
                if (age > 8000) { try { Files.writeString(mc.gameDirectory.toPath().resolve("benchmark.txt"), SmokeMetrics.report()); } catch (Exception e) { throw new RuntimeException(e); } mc.stop(); stage = 9; }
            }
        });
    }
    private void sessionSafetyTest(Minecraft mc,long sceneAge){
        var v=VoltClient.INSTANCE;
        if(sceneAge>60000)throw new IllegalStateException("Session safety test timed out: "+v.recorder.state+" "+v.recorder.error);
        if(sceneAge<2500)return;
        if(!sessionDisconnected)v.config.enabled=true;
        if(v.recorder.state==ReplayRecorder.State.ERROR)throw new IllegalStateException(v.recorder.error);
        if(!sessionLongStarted&&v.recorder.originNanos!=0&&v.recorder.elapsed()>2){
            v.recorder.toggleLong(v.context());sessionLongStarted=true;sessionLongStart=v.recorder.elapsed();
        }
        if(sessionLongStarted&&!sessionDisconnected&&v.recorder.elapsed()>sessionLongStart+3.2){
            if(!mc.getWindow().isFullscreen())throw new IllegalStateException("Session safety QA must be fullscreen");
            sessionDisconnected=true;
            mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("Volt session QA"));
        }
        if(sessionDisconnected&&mc.level==null&&v.recorder.state==ReplayRecorder.State.OFF){
            var clips=v.library.list();
            if(clips.size()<originalClipCount+1)throw new IllegalStateException("Disconnected session was not saved: "+clips.size()+" / "+originalClipCount);
            var clip=clips.stream().filter(c->c.recovered&&c.created>=sceneSince).findFirst().orElseThrow();
            if(!clip.world.contains("Volt Smoke")||clip.seconds<3)throw new IllegalStateException("Session context or duration lost");
            System.out.println("VOLT_SESSION_SAFETY saved after disconnect, world="+clip.world+" seconds="+clip.seconds+" encoder="+v.recorder.encoder);
            write(mc,"SESSION_SAVED "+clip.id+" "+clip.world+" "+clip.seconds);
            mc.setScreen(new VoltScreen(null));stage=10;ticks=0;
        }
    }
    private void librarySafetyTest(Minecraft mc){
        ticks++;
        if(ticks==1){
            librarySafetyCount=VoltClient.INSTANCE.library.list().size();
            if(librarySafetyCount<1||librarySafetyCount>50||!VoltClient.INSTANCE.library.trash().isEmpty())throw new IllegalStateException("Expected a clean isolated library fixture");
            if(!mc.getWindow().isFullscreen())throw new IllegalStateException("Library QA must be fullscreen");
            mc.setScreen(new VoltScreen(null));
        }
        if(ticks==20){mc.screen.keyPressed(new KeyEvent(65,0,2));click(mc,"Çöp kutusuna taşı");}
        if(ticks==60){
            if(!VoltClient.INSTANCE.library.list().isEmpty()||VoltClient.INSTANCE.library.trash().size()!=librarySafetyCount)throw new IllegalStateException("Trash operation did not complete");
            click(mc,"Son silinenler");
        }
        if(ticks==80){snap(mc,"v7-recently-deleted.png");mc.screen.keyPressed(new KeyEvent(65,0,2));click(mc,"Seçileni geri yükle");}
        if(ticks==120){
            if(VoltClient.INSTANCE.library.list().size()!=librarySafetyCount||!VoltClient.INSTANCE.library.trash().isEmpty())throw new IllegalStateException("Restore operation did not complete");
            click(mc,"Tüm klipler");
        }
        if(ticks==150){snap(mc,"v7-library-restored.png");mc.screen.keyPressed(new KeyEvent(65,0,2));mc.screen.keyPressed(new KeyEvent(261,0,0));}
        if(ticks==180){if(!VoltClient.INSTANCE.library.list().isEmpty())throw new IllegalStateException("Delete shortcut failed");mc.screen.keyPressed(new KeyEvent(90,0,2));}
        if(ticks==220){if(VoltClient.INSTANCE.library.list().size()!=librarySafetyCount||!VoltClient.INSTANCE.library.trash().isEmpty())throw new IllegalStateException("Undo shortcut failed");snap(mc,"v7-library-undo.png");click(mc,"Ayarlar");click(mc,"Bildirimler ve ses");}
        if(ticks==250){snap(mc,"v7-feedback-styles.png");System.out.println("VOLT_LIBRARY_SAFETY fullscreen trash/restore and feedback UI passed, clips="+librarySafetyCount);mc.stop();stage=9;}
    }
    private void automaticTest(Minecraft mc,long sceneAge){
        var v=VoltClient.INSTANCE;
        if(sceneAge>60000)throw new IllegalStateException("Automatic clip timed out: "+v.recorder.state+" "+v.recorder.error);
        if(sceneAge<6000)return;
        v.config.enabled=true;
        if(v.recorder.state==ReplayRecorder.State.ERROR)throw new IllegalStateException(v.recorder.error);
        if(v.recorder.originNanos!=0&&v.recorder.elapsed()>5&&!automaticTriggered){
            automaticTriggered=true;
            if(Boolean.getBoolean("volt.fullscreen")&&!mc.getWindow().isFullscreen())throw new IllegalStateException("Fullscreen not active");
            System.out.println("VOLT_COMPAT fullscreen="+mc.getWindow().isFullscreen()+" framebuffer="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+" guiScale="+mc.getWindow().getGuiScale());
            mc.getToastManager().clear();
            snap(mc,"11-hud-fullscreen.png");
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();
                var player=server.getPlayerList().getPlayerByName("VoltTester");
                player.setGameMode(GameType.SURVIVAL);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
                player.hurtServer(player.level(),player.damageSources().generic(),100f);
                System.out.println("VOLT_AUTO server totem triggered");
            });
        }
        if(automaticTriggered&&v.library.list().size()>originalClipCount&&!v.recorder.isExporting()){
            Clip clip=v.library.list().getFirst();
            if(!"totem".equals(clip.captureReason))throw new IllegalStateException("Unexpected reason "+clip.captureReason);
            if(clip.markers.stream().noneMatch(m->m.kind().equals("totem")))throw new IllegalStateException("Missing totem marker");
            write(mc,"PASS real integrated-server totem -> client event -> post-roll -> MP4. reason="+clip.captureReason+" seconds="+clip.seconds+" encoder="+v.recorder.encoder+" file="+clip.file);
            v.config.enabled=false;mc.setScreen(new VoltScreen(null));stage=7;ticks=0;
        }
    }
    private net.minecraft.client.gui.screens.Screen pauseScreen(Minecraft mc){
        try{return (net.minecraft.client.gui.screens.Screen)Class.forName("me.koraizen.atlasclient.menu.AtlasPauseScreen").getConstructor().newInstance();}
        catch(Exception e){return new net.minecraft.client.gui.screens.PauseScreen(true);}
    }
    private void openAac(Minecraft mc,boolean editor){
        try{
            String name="dev.aac.observer."+(editor?"HudPositionScreen":"ObserverScreen");
            var type=Class.forName(name);
            var screen=editor?type.getConstructor(net.minecraft.client.gui.screens.Screen.class).newInstance(new Object[]{null}):type.getConstructor().newInstance();
            mc.setScreen((net.minecraft.client.gui.screens.Screen)screen);
        }catch(Exception e){throw new IllegalStateException("AAC screen failed",e);}
    }
    private void dragAac(Minecraft mc){
        try{
            var type=Class.forName("dev.aac.observer.HudOverlay");
            int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            double x=((Number)type.getMethod("x",int.class).invoke(null,w)).doubleValue()+2;
            double y=((Number)type.getMethod("y",int.class).invoke(null,h)).doubleValue()+2;
            var key=new MouseButtonInfo(0,0);
            if(!mc.screen.mouseClicked(new MouseButtonEvent(x,y,key),false))throw new IllegalStateException("AAC compact drag did not start");
            if(!mc.screen.mouseDragged(new MouseButtonEvent(w*.7,h*.3,key),w*.7-x,h*.3-y))throw new IllegalStateException("AAC compact drag failed");
            mc.screen.mouseReleased(new MouseButtonEvent(w*.7,h*.3,key));
            System.out.println("VOLT_COMPAT AAC compact HUD drag passed");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private void studioTest(Minecraft mc){
        ticks++;
        mc.getToastManager().clear();
        if(ticks==1)click(mc,"Paylaşım");
        if(ticks==30)snap(mc,"studio-sharing.png");
        if(ticks==40)click(mc,"Discord");
        if(ticks==70){
            if(Boolean.getBoolean("volt.requireDiscord")&&!VoltClient.INSTANCE.discord.status.equals("volt.discord.connected"))throw new IllegalStateException("Native Discord did not acknowledge activity: "+VoltClient.INSTANCE.discord.status);
            System.out.println("VOLT_STUDIO native Discord="+VoltClient.INSTANCE.discord.status);
            snap(mc,"studio-discord.png");
        }
        if(ticks==80)click(mc,"Otomatik klipler");
        if(ticks==110)snap(mc,"studio-auto.png");
        if(ticks==120)click(mc,"Profil");
        if(ticks==150)snap(mc,"studio-profile.png");
        if(ticks==160)click(mc,"Profili düzenle");
        if(ticks==190)snap(mc,"studio-profile-edit.png");
        if(ticks==200)click(mc,"Kaydet");
        if(ticks==210)click(mc,"Klipler");
        if(ticks==240)snap(mc,"studio-library.png");
        if(ticks==250)click(mc,"Dünya / sunucu");
        if(ticks==280)snap(mc,"studio-filter.png");
        if(ticks==290)mc.screen.keyPressed(new KeyEvent(256,0,0));
        if(ticks==300)click(mc,"Oyun bekleniyor");
        if(ticks==330)snap(mc,"studio-capture.png");
        if(ticks==340)mc.screen.keyPressed(new KeyEvent(256,0,0));
        if(ticks==350)click(mc,"Aç");
        if(ticks==360)click(mc,"Videoyu oynat");
        if(ticks==420){
            try{
                var member=VoltScreen.class.getDeclaredField("preview");member.setAccessible(true);
                PreviewTexture player=(PreviewTexture)member.get(mc.screen);
                if(player==null||!player.decoder.error.isEmpty()||player.decoder.position<.1||player.frame()==null)throw new IllegalStateException("Preview failed: "+(player==null?"null":player.decoder.error));
                System.out.println("VOLT_STUDIO real silent preview playing at "+player.decoder.position);
            }catch(Exception e){throw new IllegalStateException(e);}
            snap(mc,"studio-player.png");
        }
        if(ticks==424)click(mc,"Bu ana işaret ekle");
        if(ticks==428){
            mc.screen.setFocused(null);
            mc.screen.keyPressed(new KeyEvent(263,0,0));
            mc.screen.keyPressed(new KeyEvent(262,0,2));
            System.out.println("VOLT_STUDIO bookmark added and keyboard navigation invoked");
        }
        if(ticks==430)for(var child:mc.screen.children())if(child instanceof SeekBar seek){double x=seek.getX()+seek.getWidth()*.5,y=seek.getY()+9;var event=new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));seek.onClick(event,false);seek.mouseReleased(event);}
        if(ticks==460)snap(mc,"studio-seek.png");
        if(ticks==466)click(mc,"Dışa aktar");
        if(ticks==475)snap(mc,"studio-export.png");
        if(ticks==479)mc.screen.keyPressed(new KeyEvent(256,0,0));
        if(ticks==480)click(mc,"Geri");
        if(ticks==510)click(mc,"Profil");
        if(ticks==520)click(mc,"İstatistikler");
        if(ticks==550)snap(mc,"studio-stats.png");
        if(ticks==560)click(mc,"Ayarlar");
        if(ticks==570)click(mc,"Kısayollar");
        if(ticks==600)snap(mc,"studio-hotkeys.png");
        if(ticks==603)click(mc,"Performans");
        if(ticks==608)snap(mc,"studio-performance.png");
        if(ticks==610)click(mc,"Klipler");
        if(ticks==620)click(mc,"Izgara görünümü");
        if(ticks==650)snap(mc,"studio-list.png");
        if(ticks==670)click(mc,"Ayarlar");
        if(ticks==680)click(mc,"Bildirimler ve ses");
        if(ticks==700)click(mc,"Onay sesini dene");
        if(ticks==720)snap(mc,"studio-feedback.png");
        if(ticks==740){
            for(var key:mc.options.keyMappings)if(key.getName().equals("key.volt.clip")||key.getName().equals("key.aac_observer.hud")){
                String expected=key.getName().equals("key.volt.clip")?"key.keyboard.f8":"key.keyboard.f6";
                if(!key.saveString().equals(expected))throw new IllegalStateException("Wrong binding "+key.getName()+"="+key.saveString());
                System.out.println("VOLT_STUDIO verified "+key.getName()+"="+key.saveString());
            }
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Volt feedback renderer test")){
                @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float delta){
                    g.fill(0,0,width,height,0xFF14171B);
                    g.pose().pushMatrix();g.pose().scale(1f/mc.getWindow().getGuiScale(),1f/mc.getWindow().getGuiScale());
                    new ClipFeedback().render(g,VoltClient.INSTANCE,mc.getWindow().getWidth(),mc.getWindow().getHeight(),mc.getWindow().getHeight()-76);g.pose().popMatrix();
                }
            });
            VoltClient.INSTANCE.notify("volt.notice.saved");
        }
        if(ticks==756)snap(mc,"studio-saved-feedback.png");
        if(ticks==790){System.out.println("VOLT_STUDIO UI smoke completed");mc.stop();stage=9;}
    }
    private String captureDiagnostics(){
        StringBuilder out=new StringBuilder();
        for(String name:new String[]{"persistentReadback","averageCaptureMillis","captured"})try{out.append(" ").append(name).append("=").append(VoltClient.INSTANCE.capture.getClass().getField(name).get(VoltClient.INSTANCE.capture));}catch(Exception ignored){}
        return out.toString();
    }
    private void snap(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, c -> System.out.println("VOLT_SMOKE " + c.getString())); }
    private void click(Minecraft mc,String label){
        if(mc.screen==null)return;var children=mc.screen.children();Button target=null;
        if(java.util.Set.of("Klipler","Profil","Ayarlar").contains(label))for(var child:children)if(child instanceof Button b&&b.getX()<64&&b.getY()>56&&b.getMessage().getString().equals(label)){target=b;break;}
        if(target==null&&label.equals("Aç"))for(var child:children)if(child instanceof Button b&&b.getMessage().getString().equals(label)){target=b;break;}
        if(target==null)for(int i=children.size()-1;i>=0;i--)if(children.get(i) instanceof Button b&&b.getMessage().getString().equals(label)){target=b;break;}
        if(target!=null){
            double scale=1;
            if(mc.screen instanceof VoltScreen)try{var member=VoltScreen.class.getDeclaredField("uiScale");member.setAccessible(true);scale=member.getFloat(mc.screen);}catch(Exception e){throw new IllegalStateException(e);}
            var event=new MouseButtonEvent((target.getX()+target.getWidth()/2.0)*scale,(target.getY()+target.getHeight()/2.0)*scale,new MouseButtonInfo(0,0));
            mc.screen.mouseClicked(event,false);if(mc.screen!=null)mc.screen.mouseReleased(event);
            System.out.println("VOLT_SMOKE clicked "+label);return;
        }
        System.out.println("VOLT_SMOKE missing button "+label+" screen="+mc.screen.getClass().getSimpleName());
        if(Boolean.getBoolean("volt.strictUiTest"))throw new IllegalStateException("Missing required UI action: "+label);
    }

    private void write(Minecraft mc, String value) { try { Files.writeString(mc.gameDirectory.toPath().resolve("smoke-result.txt"), value); } catch (Exception e) { throw new RuntimeException(e); } }
}
