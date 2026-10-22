package dev.volt;

import dev.volt.recording.*;
import dev.volt.ui.*;
import dev.volt.highlights.AutoHighlights;
import dev.volt.compat.DefaultKeyPolicy;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.concurrent.*;

public final class VoltClient implements ClientModInitializer {
    public static VoltClient INSTANCE;
    public VoltConfig config;
    public ClipLibrary library;
    public ReplayRecorder recorder;
    public final GpuCapture capture = new GpuCapture();
    public DiscordPresence discord;
    public ClipActions actions;
    public ProfileImages profileImages;
    public ClipThumbnails thumbnails;
    public AutoHighlights highlights;
    public Path gameDir, configPath;
    public volatile String notice = "";
    public volatile long noticeUntil;
    public volatile long noticeStarted;
    private final ClipFeedback feedback = new ClipFeedback();
    public final ExecutorService io = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Volt-library").factory());
    private boolean wasDead, keyDefaultsChecked;
    private String previousDimension = "";
    private KeyMapping menu, clip, recording, marker;
    @Override public void onInitializeClient() {
        INSTANCE = this;
        gameDir = FabricLoader.getInstance().getGameDir();
        configPath = FabricLoader.getInstance().getConfigDir().resolve("volt.json");
        config = VoltConfig.load(configPath);
        try { library = new ClipLibrary(gameDir.resolve(config.clipDirectory).normalize()); }
        catch (Exception e) { throw new IllegalStateException("Cannot create Volt clip directory", e); }
        recorder = new ReplayRecorder(config, library, gameDir.resolve("volt/cache"), this::notify);
        highlights = new AutoHighlights(this);
        actions = new ClipActions(this);
        profileImages = new ProfileImages(this);
        thumbnails = new ClipThumbnails(this);
        discord = new DiscordPresence(config, () -> "Clipped with Volt");
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("volt", "controls"));
        menu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.volt.menu", GLFW.GLFW_KEY_V, category));
        clip = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.volt.clip", GLFW.GLFW_KEY_F8, category));
        recording = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.volt.record", GLFW.GLFW_KEY_F9, category));
        marker = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.volt.marker", GLFW.GLFW_KEY_F7, category));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { discord.close(); actions.close(); recorder.close(); capture.close(); io.shutdownNow(); });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            boolean atlasPause=screen.getClass().getName().equals("me.koraizen.atlasclient.menu.AtlasPauseScreen");
            if(screen instanceof TitleScreen||screen instanceof PauseScreen||atlasPause){
                var launcher=new VoltMenuButton(w,()->client.setScreen(new VoltScreen(screen)));
                if(FabricLoader.getInstance().isModLoaded("atlas-client")){
                    // Atlas renders its own menu instead of the vanilla child widget list.
                    ScreenEvents.afterRender(screen).register((s,g,mx,my,delta)->launcher.render(g,mx,my,delta));
                    ScreenMouseEvents.allowMouseClick(screen).register((s,event)->!launcher.mouseClicked(event,false));
                }else Screens.getButtons(screen).add(launcher);
            }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("volt", "status"), (g, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.screen instanceof VoltScreen) return;
            // Small overlays use framebuffer pixels, independent of Minecraft GUI scale.
            int screenWidth=mc.getWindow().getWidth(),screenHeight=mc.getWindow().getHeight();
            int hudX=config.hudAnchor%2==1?Math.max(8,screenWidth-116):8;
            int hudY=config.hudAnchor>=2?Math.max(8,screenHeight-76):8;
            boolean message=System.currentTimeMillis()<noticeUntil;
            if(!config.hud&&!message)return;
            g.pose().pushMatrix();
            float scale=1f/mc.getWindow().getGuiScale();g.pose().scale(scale,scale);
            try {
                if(config.hud&&recorder.state!=ReplayRecorder.State.OFF){
                    VoltTheme.box(g,hudX,hudY,108,20,0xD014161B);
                    g.fill(hudX+7,hudY+8,hudX+11,hudY+12,recorder.state==ReplayRecorder.State.BUFFERING?VoltTheme.ACCENT:0xFF9399A7);
                    SmoothRaster.text(g,"VOLT · "+(recorder.longRecording?"REC ":"")+VoltTheme.duration(recorder.availableSeconds()),hudX+16,hudY+4,10,VoltTheme.WHITE,false);
                }
                if(message) feedback.render(g,this,screenWidth,screenHeight,hudY);

            } finally {g.pose().popMatrix();}
        });
        saveConfig();
    }
    private void checkDefaultKeys(Minecraft mc){
        if(keyDefaultsChecked||mc.options==null)return;
        keyDefaultsChecked=true;
        if(config.resetClipKeyToF8){
            clip.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
            KeyMapping.resetMapping();mc.options.save();config.resetClipKeyToF8=false;saveConfig();
        }
        var bindings=java.util.Arrays.stream(mc.options.keyMappings)
            .map(k->new DefaultKeyPolicy.Binding(k.getName(),k.saveString(),k.isDefault(),k.getCategory().equals(KeyMapping.Category.DEBUG))).toList();
        var conflicts=DefaultKeyPolicy.conflicts(bindings);
        if(conflicts.isEmpty())return;
        for(KeyMapping k:new KeyMapping[]{menu,clip,recording,marker})if(conflicts.contains(k.getName())){
            k.setKey(InputConstants.UNKNOWN);
            org.slf4j.LoggerFactory.getLogger("Volt").info("Released conflicting Volt default binding: {}. Choose a free key in Controls.",k.getName());
        }
        KeyMapping.resetMapping();mc.options.save();notify("volt.notice.key_conflict");
    }
    public String keyName(String action){
        KeyMapping k=switch(action){case "menu"->menu;case "record"->recording;case "marker"->marker;default->clip;};
        return k==null||k.isUnbound()?Component.translatable("volt.key.unbound").getString():k.getTranslatedKeyMessage().getString();
    }
    private void tick(Minecraft mc) {
        checkDefaultKeys(mc);
        while (menu.consumeClick()) mc.setScreen(new VoltScreen(mc.screen));
        while (clip.consumeClick()) recorder.saveReplay(context());
        while (recording.consumeClick()) recorder.toggleLong(context());
        while (marker.consumeClick()) { recorder.mark("manual"); notify("volt.notice.marked"); }
        highlights.tick(mc);
        if (mc.level != null && config.enabled && !recorder.isExporting() && recorder.state == ReplayRecorder.State.OFF)
            recorder.start(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if ((mc.level == null || !config.enabled) && (recorder.state == ReplayRecorder.State.BUFFERING || recorder.state == ReplayRecorder.State.STARTING)) recorder.stop();
        if (mc.player != null) {
            boolean dead = mc.player.isDeadOrDying();
            if (dead && !wasDead) {
                recorder.mark("death");
                highlights.death();
            }
            wasDead = dead;
            String dimension = mc.level.dimension().identifier().toString();
            if (!previousDimension.isEmpty() && !previousDimension.equals(dimension)) recorder.mark("dimension");
            previousDimension = dimension;
        } else { wasDead = false; previousDimension = ""; }
    }
    public ReplayRecorder.Context context() {
        Minecraft mc = Minecraft.getInstance();
        String world = mc.getCurrentServer() != null ? mc.getCurrentServer().name : mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldData().getLevelName() : "Minecraft";
        return new ReplayRecorder.Context(mc.getUser().getName(), world, mc.level != null ? mc.level.dimension().identifier().toString() : "");
    }
    public void notify(String translationKey) {
        Minecraft.getInstance().execute(() -> {
            notice = translationKey; noticeStarted = System.currentTimeMillis(); noticeUntil = noticeStarted + 3200;
            feedback.play(translationKey,config.notificationVolume);
            if(discord!=null)discord.refresh();
        });
    }
    public void saveConfig() { io.submit(() -> { try { config.save(configPath); } catch (Exception e) { notify("volt.notice.config_error"); } }); }
    public static void captureFrame() {
        VoltClient v = INSTANCE;
        if (v == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (mc.level == null) { v.capture.close(); return; }
        if (mc.getWindow().isMinimized()) return;
        try { v.capture.capture(v.recorder, mc.getWindow().getWidth(), mc.getWindow().getHeight()); }
        catch (Exception e) {
            v.recorder.stop(); v.config.enabled = false; v.recorder.error = "GPU capture: " + e.getMessage(); v.notify("volt.notice.error");
        }
    }
}
