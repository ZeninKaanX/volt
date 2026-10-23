package dev.volt.ui;

import dev.volt.*;
import dev.volt.recording.ReplayRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import java.nio.file.Files;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Native, smooth desktop workspace inspired by the Medal library/quick-trimmer layout. */
public final class VoltScreen extends Screen {
    private static final int RAIL=64, HEADER=56;
    private final Screen parent;
    private final VoltClient v=VoltClient.INSTANCE;
    private int tab,settingsTab,page,sortMode,libraryTab,typeFilter,dateFilter,profileTab;
    private String query="",worldFilter="",popup="",lastStatus="";
    private boolean grid=true,loading,loaded,filterDirty,advanced;
    private Clip selected,contextClip;
    private List<Clip> clips=List.of();
    private List<Clip> deleted=List.of(),undoDeleted=List.of();
    private Map<String,Long> sizes=Map.of();
    private final Set<String> selection=new HashSet<>();
    private final List<Card> cards=new ArrayList<>();
    private final List<AbstractWidget> popupWidgets=new ArrayList<>();
    private final List<AbstractWidget> floatingWidgets=new ArrayList<>();
    private int left,contentWidth,galleryTop,columns,cardWidth,thumbHeight,cardHeight,rows;
    private int profileX,profileW,settingsX,settingsW,popupX,popupY,popupW,popupH;
    private float uiScale=1;
    private int physicalWidth,physicalHeight;
    private EditBox search,bio,endpoint,token,executable,directory,audioDevice,titleBox,trimStart,trimEnd;
    private PreviewTexture preview;
    private StudioButton playButton,captureStatus,recordControl;
    private String draftId="",trimId="";
    private long lastClipSeen;
    private int exportHeight=1080,exportFps=30;
    private double exportSpeed=1,exportVolume=1;
    private boolean exportPortrait;
    private String diagnostic="";
    private long diagnosticAt;
    private record Card(Clip clip,int x,int y,int w,int h,int imageHeight,boolean list,Button hit) { }
    public VoltScreen(Screen parent){super(VoltTheme.literal("Volt"));this.parent=parent;}

    @Override protected void init(){
        if(physicalWidth==0){physicalWidth=width;physicalHeight=height;}
        uiScale=Math.min(physicalWidth/1280f,physicalHeight/820f);
        width=Math.round(physicalWidth/uiScale);height=Math.round(physicalHeight/uiScale);
        left=RAIL+24;contentWidth=width-left-24;galleryTop=184;
        cards.clear();popupWidgets.clear();floatingWidgets.clear();v.profileImages.load();chrome();
        if(selected!=null)editor();else if(tab==0)library();else if(tab==1)profile();else settings();
        buildPopup();if(!loaded&&!loading)refresh();
    }
    private StudioButton control(int x,int y,int w,int h,String label,String icon,int style,Runnable action){return addRenderableWidget(new StudioButton(x,y,w,h,VoltTheme.literal(label),icon,style,false,action));}
    private StudioButton control(int x,int y,int w,String key,String icon,int style,Runnable action){return control(x,y,w,32,t(key),icon,style,action);}
    private StudioButton floating(StudioButton button){floatingWidgets.add(button);return button;}
    private StudioButton icon(int x,int y,int size,String label,String glyph,int style,Runnable action){return addRenderableWidget(new StudioButton(x,y,size,size,VoltTheme.literal(label),glyph,style,true,action));}
    private EditBox field(int x,int y,int w,String value,String hint){EditBox f=new SmoothEditBox(font,x,y,w,36,VoltTheme.text(hint),hint.equals("volt.token"));f.setMaxLength(512);f.setValue(value);f.setTextShadow(false);addRenderableWidget(f);return f;}
    private void toggle(int x,int y,String label,BooleanSupplier state,Runnable action){addRenderableWidget(new StudioToggle(x,y,VoltTheme.text(label),state,()->{action.run();v.saveConfig();rebuildWidgets();}));}
    private void navigate(int target){if(preview!=null)preview.decoder.pause();tab=target;selected=null;popup="";page=0;rebuildWidgets();}
    private void menu(String value){popup=popup.equals(value)?"":value;rebuildWidgets();}
    private void setting(int index){tab=2;settingsTab=index;selected=null;popup="";if(preview!=null)preview.decoder.pause();rebuildWidgets();}
    private void open(Clip clip){selected=clip;popup="";if(preview==null)preview=new PreviewTexture(v.config.ffmpeg);preview.select(clip);rebuildWidgets();}
    private void back(){if(selected!=null){if(preview!=null)preview.decoder.pause();selected=null;popup="";rebuildWidgets();}else onClose();}
    private void refresh(){
        if(loading)return;loading=true;
        v.io.submit(()->{
            List<Clip> data=v.library.list(),trash=v.library.trash();Map<String,Long> disk=new HashMap<>();
            for(Clip c:java.util.stream.Stream.concat(data.stream(),trash.stream()).toList())try{disk.put(c.id,Files.size(v.library.path(c)));}catch(Exception ignored){}
            Minecraft.getInstance().execute(()->{clips=data;deleted=trash;sizes=Map.copyOf(disk);selection.retainAll(java.util.stream.Stream.concat(data.stream(),trash.stream()).map(c->c.id).toList());loaded=true;loading=false;if(minecraft.screen==this)rebuildWidgets();});
        });
    }
    private void moveClips(List<Clip> targets,boolean restore){
        if(loading||targets.isEmpty())return;
        loading=true;v.thumbnails.suspend();popup="";
        if(selected!=null){if(preview!=null){preview.close();preview=null;}selected=null;tab=0;}
        v.io.submit(()->{
            List<Clip> moved=new ArrayList<>();
            for(Clip c:targets)try{if(restore)v.library.restore(c);else v.library.trash(c);moved.add(c);}catch(Exception ignored){}
            Minecraft.getInstance().execute(()->{
                loading=false;selection.clear();undoDeleted=restore?List.of():List.copyOf(moved);
                v.notify(moved.size()!=targets.size()?"volt.notice.clip_move_failed":restore?"volt.notice.restored":"volt.notice.trashed");
                refresh();
            });
        });
    }
    private void save(Clip c){v.io.submit(()->{try{v.library.save(c);}catch(Exception e){v.notify("volt.notice.config_error");}});}
    private List<Clip> filtered(){
        var stream=(tab==0&&libraryTab==4?deleted:clips).stream();
        if(tab!=1)stream=stream.filter(c->(libraryTab!=1||c.favorite)&&(libraryTab!=2||!c.captureReason.isEmpty())&&(libraryTab!=3||!c.shareUrl.isEmpty())&&
            (typeFilter==0||typeFilter==1&&c.captureReason.isEmpty()||typeFilter==2&&!c.captureReason.isEmpty())&&
            (worldFilter.isEmpty()||worldFilter.equals(c.world))&&(query.isEmpty()||(c.title+" "+c.world+" "+c.player).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))&&
            (dateFilter==0||System.currentTimeMillis()-c.created<(dateFilter==1?86400000L:604800000L)));
        Comparator<Clip> order=switch(sortMode){case 1->Comparator.comparingLong(c->c.created);case 2->Comparator.comparingDouble((Clip c)->c.seconds).reversed();default->Comparator.comparingLong((Clip c)->c.created).reversed();};
        return stream.sorted(order).toList();
    }
    private void chrome(){
        icon(12,76,40,t("volt.studio.home"),"home",tab==1?3:0,()->navigate(1));
        icon(12,128,40,t("volt.nav.0"),"clips",tab==0?3:0,()->navigate(0));
        icon(12,180,40,t("volt.folder"),"folder",0,()->Util.getPlatform().openFile(v.library.directory().toFile()));
        icon(12,height-144,40,t("volt.nav.1"),"user",0,()->navigate(1));
        icon(12,height-92,40,t("volt.nav.2"),"settings",tab==2?3:0,()->navigate(2));
        icon(12,height-46,40,t("volt.close"),"back",0,this::onClose);
        if(selected!=null){
            control(78,12,216,32,t("volt.studio.back_library"),"back",0,this::back);
            control(width-212,10,166,36,t("volt.export.title"),"upload",2,()->menu("export"));
            icon(width-38,12,30,t("volt.close"),"close",0,this::onClose);return;
        }
        icon(74,11,32,t("volt.back"),"back",0,this::back);
        icon(110,11,32,t("volt.next"),"next",0,()->{if(selected!=null){int at=clips.indexOf(selected);if(at>=0&&at+1<clips.size())open(clips.get(at+1));}else if((page+1)*pageSize()<filtered().size()){page++;rebuildWidgets();}});

        captureStatus=control(151,8,200,40,recordingTitle(),"game",1,()->menu("capture"));
        control(362,12,126,32,v.keyName("clip")+" · "+t("volt.studio.clip_control",v.config.clipSeconds),"cut",0,()->v.recorder.saveReplay(v.context()));
        recordControl=control(495,12,174,32,t(v.recorder.longRecording?"volt.studio.stop_record":"volt.studio.long_record",v.keyName("record")),"record",0,()->{if(v.recorder.state==ReplayRecorder.State.BUFFERING)v.recorder.toggleLong(v.context());else v.notify("volt.notice.wait_buffer");});
        control(676,12,124,32,(v.recorder.state==ReplayRecorder.State.BUFFERING?v.recorder.height:v.config.height)+"p "+(v.recorder.state==ReplayRecorder.State.BUFFERING?v.recorder.fps():v.config.fps)+" FPS","settings",0,()->menu("quality"));
        if(width>1370)control(808,12,126,32,t("volt.studio.auto_control",t(v.config.autoHighlights?"volt.studio.on":"volt.studio.off")),"auto",0,()->setting(3));
        icon(width-164,12,30,t("volt.search"),"search",0,()->{navigate(0);setFocused(search);search.setFocused(true);});
        icon(width-122,12,30,t("volt.nav.2"),"settings",0,()->navigate(2));
        icon(width-80,12,30,t("volt.nav.1"),"user",0,()->navigate(1));
        icon(width-38,12,30,t("volt.close"),"close",0,this::onClose);
    }
    private String recordingTitle(){return v.recorder.longRecording?t("volt.studio.recording_title",VoltTheme.duration(v.recorder.longSeconds())):t(v.recorder.state==ReplayRecorder.State.BUFFERING?"volt.studio.now_clipping":v.recorder.state==ReplayRecorder.State.STARTING?"volt.state.starting":"volt.studio.waiting");}
    private int pageSize(){return Math.max(1,rows)*Math.max(1,grid?columns:1);}
    private void library(){
        search=field(left,72,Math.min(270,contentWidth/4),query,"volt.search");search.setResponder(value->{query=value;page=0;filterDirty=true;});
        int x=left+Math.min(270,contentWidth/4)+14;
        control(x,74,170,32,worldFilter.isEmpty()?t("volt.studio.world_filter"):worldFilter,"game",0,()->menu("world"));
        control(x+176,74,128,32,t("volt.studio.type"+typeFilter),"clips",0,()->menu("type"));
        control(x+310,74,124,32,t("volt.studio.date"+dateFilter),"clock",0,()->menu("date"));
        icon(left+contentWidth-78,74,32,t("volt.refresh"),"refresh",0,this::refresh);
        icon(left+contentWidth-38,74,32,t(grid?"volt.view.grid":"volt.view.list"),grid?"grid":"list",0,()->{grid=!grid;page=0;rebuildWidgets();});
        int tx=left;
        for(int i=0;i<5;i++){int index=i;int w=i==0?106:138;control(tx,119,w,34,t("volt.studio.library_tab"+i),"",0,()->{libraryTab=index;page=0;selection.clear();rebuildWidgets();});tx+=w+6;}
        control(left+contentWidth-166,119,166,32,t("volt.sort."+sortMode),"down",0,()->menu("sort"));
        layoutCards(left,galleryTop,contentWidth);buildCards(filtered());
        icon(left,height-42,30,t("volt.previous"),"back",1,()->{if(page>0){page--;rebuildWidgets();}});
        icon(left+38,height-42,30,t("volt.next"),"next",1,()->{if((page+1)*pageSize()<filtered().size()){page++;rebuildWidgets();}});
        control(width-174,height-42,150,"volt.folder","folder",0,()->Util.getPlatform().openFile(v.library.directory().toFile()));
        if(!undoDeleted.isEmpty())control(left+132,height-42,140,"volt.trash.undo","back",2,()->moveClips(undoDeleted,true));
        if(!selection.isEmpty()){
            int bx=left+contentWidth/2-290;
            if(libraryTab==4)floating(control(bx,height-98,190,"volt.trash.restore_selected","restore",2,()->moveClips(deleted.stream().filter(c->selection.contains(c.id)).toList(),true)));
            else{
                floating(control(bx,height-98,190,38,t("volt.studio.favorite_selected",selection.size()),"star",2,()->{for(Clip c:clips)if(selection.contains(c.id)){c.favorite=true;save(c);}selection.clear();rebuildWidgets();}));
                floating(control(bx+200,height-98,180,"volt.trash.move","trash",4,()->moveClips(clips.stream().filter(c->selection.contains(c.id)).toList(),false)));
            }
            floating(control(bx+(libraryTab==4?200:390),height-98,176,38,t("volt.studio.clear_selection"),"close",1,()->{selection.clear();rebuildWidgets();}));
        }
    }
    private void layoutCards(int x,int y,int w){left=x;galleryTop=y;contentWidth=w;columns=Math.clamp(w/300,2,4);cardWidth=(w-(columns-1)*16)/columns;thumbHeight=cardWidth*9/16;cardHeight=thumbHeight+87;rows=Math.max(1,(height-y-58)/(grid?cardHeight+18:86));}
    private void buildCards(List<Clip> data){
        page=Math.min(page,Math.max(0,(data.size()-1)/pageSize()));
        for(int i=0;i<pageSize();i++){
            int at=page*pageSize()+i;if(at>=data.size())break;Clip c=data.get(at);
            int x=grid?left+(i%columns)*(cardWidth+16):left,y=grid?galleryTop+(i/columns)*(cardHeight+18):galleryTop+i*86;
            int w=grid?cardWidth:contentWidth,h=grid?cardHeight:74;
            icon(x+8,y+8,26,t("volt.studio.select_clip"),selection.contains(c.id)?"check":"select",selection.contains(c.id)?2:1,()->{if(!selection.add(c.id))selection.remove(c.id);rebuildWidgets();});
            if(libraryTab!=4||tab!=0)icon(x+w-36,y+8,26,t(c.favorite?"volt.unfavorite":"volt.favorite"),"star",c.favorite?2:1,()->{c.favorite=!c.favorite;save(c);rebuildWidgets();});
            if(grid){
                if(tab==0&&libraryTab==4)control(x+5,y+h-32,w-48,28,t("volt.trash.restore"),"restore",0,()->moveClips(List.of(c),true));
                else control(x+5,y+h-32,w-48,28,t(c.shareUrl.isBlank()?"volt.share.create":"volt.share.copy"),"link",0,()->v.actions.share(c));
                icon(x+w-35,y+h-32,28,t("volt.studio.more"),"more",0,()->{contextClip=c;popupX=x+w-220;popupY=y+h-28;menu("context");});
            }
            Button hit=addRenderableWidget(new HitButton(x,y,w,h,VoltTheme.text(tab==0&&libraryTab==4?"volt.trash.restore":"volt.open"),()->{if(tab==0&&libraryTab==4)moveClips(List.of(c),true);else open(c);}));
            cards.add(new Card(c,x,y,w,h,grid?thumbHeight:58,!grid,hit));
        }
    }
    private void profile(){
        profileW=Math.min(1040,width-RAIL-64);profileX=RAIL+(width-RAIL-profileW)/2;
        control(profileX+profileW-144,286,144,"volt.studio.edit_profile","",5,()->menu("editProfile"));
        int x=profileX;
        for(int i=0;i<3;i++){int index=i;control(x,366,112,34,t("volt.studio.profile_tab"+i),"",0,()->{profileTab=index;page=0;rebuildWidgets();});x+=118;}
        if(profileTab==1){layoutCards(profileX,442,profileW);buildCards(filtered());}
        else if(profileTab==0){
            List<Clip> featured=clips.stream().filter(c->c.favorite).limit(3).toList();if(featured.isEmpty())featured=clips.stream().limit(3).toList();
            int cardW=(profileW-32)/3;
            for(int i=0;i<featured.size();i++){Clip c=featured.get(i);int cx=profileX+i*(cardW+16);Button hit=addRenderableWidget(new HitButton(cx,590,cardW,Math.min(140,cardW*9/16)+60,VoltTheme.text("volt.open"),()->open(c)));cards.add(new Card(c,cx,590,cardW,Math.min(140,cardW*9/16)+60,Math.min(140,cardW*9/16),false,hit));}
        }
    }
    private void settings(){
        settingsX=left+240;settingsW=width-settingsX-34;
        String[] names={"volt.settings.tab0","volt.settings.tab3","volt.settings.tab1","volt.settings.tab2","volt.studio.shortcuts_title","volt.feedback.title","volt.performance.title"};
        String[] glyphs={"record","auto","link","user","grid","volume","auto"};int[] indices={0,3,1,2,4,5,6};
        for(int i=0;i<indices.length;i++){int index=indices[i];control(left,168+i*44,202,36,t(names[i]),glyphs[i],settingsTab==index?3:0,()->setting(index));}
        int x=settingsX,w=settingsW;
        if(settingsTab==0){
            toggle(x+w-40,190,"volt.recording.on",()->v.config.enabled,()->{v.config.enabled=!v.config.enabled;v.recorder.resetError();});
            control(x+w-166,253,166,32,v.config.clipSeconds+" "+t("volt.seconds"),"down",1,()->menu("length"));
            control(x+w-166,315,166,32,v.config.height+"p · "+v.config.fps+" FPS","down",1,()->menu("quality"));
            control(x+w-230,377,230,32,encoderName(v.config.encoder),"down",1,()->{List<String> options=List.of("auto","libx264","h264_nvenc","h264_vaapi","h264_qsv","h264_amf","h264_videotoolbox");v.config.encoder=options.get((options.indexOf(v.config.encoder)+1)%options.size());v.recorder.stop();v.saveConfig();rebuildWidgets();});
            control(x+w-166,439,166,32,t(v.config.hud?"volt.hud.anchor"+v.config.hudAnchor:"volt.hud.off"),"down",1,()->{if(!v.config.hud){v.config.hud=true;v.config.hudAnchor=0;}else if(v.config.hudAnchor<3)v.config.hudAnchor++;else v.config.hud=false;v.saveConfig();rebuildWidgets();});
            toggle(x+w-40,501,"volt.audio.on",()->!v.config.audioFormat.equals("off"),()->{v.config.audioFormat=v.config.audioFormat.equals("off")?(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")?"pulse":System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"dshow":"avfoundation"):"off";v.recorder.stop();});
            control(x,567,210,32,t("volt.studio.advanced"),advanced?"down":"next",0,()->{advanced=!advanced;rebuildWidgets();});
            if(advanced){
                executable=field(x,628,(w-14)/2,v.config.ffmpeg,"volt.ffmpeg");directory=field(x+(w+14)/2,628,(w-14)/2,v.config.clipDirectory,"volt.directory");
                audioDevice=field(x,703,w,v.config.audioDevice,"volt.audio_device");
                control(x+w-150,height-46,150,"volt.apply","check",2,()->{v.config.ffmpeg=executable.getValue().strip();v.config.clipDirectory=directory.getValue().strip();v.config.audioDevice=audioDevice.getValue().strip();v.recorder.stop();v.saveConfig();v.notify("volt.notice.restart_directory");});
            }else control(x,682,186,"volt.folder","folder",4,()->Util.getPlatform().openFile(v.library.directory().toFile()));
        }else if(settingsTab==3){
            toggle(x+w-40,190,"volt.auto.on",()->v.config.autoHighlights,()->v.config.autoHighlights=!v.config.autoHighlights);
            toggle(x+w-40,265,"volt.auto.combo_on",()->v.config.autoComboClip,()->v.config.autoComboClip=!v.config.autoComboClip);
            control(x+w-166,333,166,32,t("volt.auto.threshold",v.config.comboHits),"down",1,()->{v.config.comboHits=v.config.comboHits>=8?3:v.config.comboHits+1;v.saveConfig();rebuildWidgets();});
            toggle(x+w-40,403,"volt.auto.combat_on",()->v.config.autoCombatClips,()->v.config.autoCombatClips=!v.config.autoCombatClips);
            toggle(x+w-40,473,"volt.auto.adventure_on",()->v.config.autoAdventureClips,()->v.config.autoAdventureClips=!v.config.autoAdventureClips);
            control(x+w-214,541,214,32,t("volt.auto.limit",v.config.autoClipsPerMinute),"down",1,()->{v.config.autoClipsPerMinute=v.config.autoClipsPerMinute>=6?1:v.config.autoClipsPerMinute+1;v.saveConfig();rebuildWidgets();});
            toggle(x+w-40,611,"volt.death.on",()->v.config.autoDeathClip,()->v.config.autoDeathClip=!v.config.autoDeathClip);
        }else if(settingsTab==1){
            endpoint=field(x,250,w,v.config.uploadEndpoint,"volt.endpoint");token=field(x,338,w,v.config.uploadToken,"volt.token");
            control(x,397,154,"volt.save","check",2,()->{v.config.uploadEndpoint=endpoint.getValue().strip();v.config.uploadToken=token.getValue().strip();v.saveConfig();v.notify("volt.notice.config_saved");});
        }else if(settingsTab==2){
            toggle(x+w-40,193,"volt.discord.on",()->v.config.discordPresence,()->{v.config.discordPresence=!v.config.discordPresence;v.discord.refresh();});
            control(x,339,230,"volt.discord.reconnect","refresh",4,()->v.discord.refresh());
        }else if(settingsTab==6){
            toggle(x+w-40,205,"volt.performance.on",()->v.config.performanceMode,()->{v.config.performanceMode=!v.config.performanceMode;v.recorder.stop();});
            toggle(x+w-40,275,"volt.performance.gpu_yuv",()->v.config.gpuColorConversion,()->{v.config.gpuColorConversion=!v.config.gpuColorConversion;v.recorder.stop();});
        }else if(settingsTab==5){
            toggle(x+w-40,205,"volt.feedback.animation",()->v.config.notificationAnimation,()->v.config.notificationAnimation=!v.config.notificationAnimation);
            control(x+w-150,275,150,32,v.config.notificationVolume+" %","volume",1,()->{v.config.notificationVolume=(v.config.notificationVolume+25)%125;v.saveConfig();rebuildWidgets();});
            control(x+w-150,345,150,32,v.config.buttonVolume+" %","volume",1,()->{v.config.buttonVolume=v.config.buttonVolume>=100?0:Math.min(100,(v.config.buttonVolume/25+1)*25);v.saveConfig();rebuildWidgets();});
            control(x+w-214,415,214,32,t("volt.feedback.style_value",t("volt.feedback.style"+v.config.soundStyle)),"down",1,()->{v.config.soundStyle=(v.config.soundStyle+1)%3;v.saveConfig();rebuildWidgets();});
            control(x,505,230,"volt.feedback.preview","play",2,()->v.notify("volt.notice.feedback_preview"));
        }else control(x,495,230,"volt.studio.open_bindings","grid",2,()->minecraft.setScreen(new KeyBindsScreen(this,minecraft.options)));
    }
    private void editor(){
        if(preview==null)preview=new PreviewTexture(v.config.ffmpeg);preview.select(selected);
        int panel=320,sx=width-panel,px=RAIL+26,pw=sx-px-28;
        icon(sx-42,71,30,t("volt.back"),"close",1,this::back);
        String titleValue=titleBox!=null&&draftId.equals(selected.id)?titleBox.getValue():selected.title;draftId=selected.id;
        titleBox=field(sx+20,107,panel-82,titleValue,"volt.clip_title");titleBox.setCursorPosition(0);titleBox.setHighlightPos(0);
        icon(width-50,109,30,t("volt.save"),"check",2,()->{selected.title=titleBox.getValue();save(selected);v.notify("volt.notice.config_saved");});
        control(sx+20,217,panel-40,32,t(selected.favorite?"volt.unfavorite":"volt.favorite"),"star",selected.favorite?3:4,()->{selected.favorite=!selected.favorite;save(selected);rebuildWidgets();});
        control(sx+20,472,panel-40,"volt.folder","folder",4,()->Util.getPlatform().openFile(selected.path(v.library.directory()).getParent().toFile()));
        control(sx+20,height-102,panel-40,"volt.studio.external_player","play",4,()->Util.getPlatform().openFile(selected.path(v.library.directory()).toFile()));
        control(sx+20,height-58,panel-40,38,t(selected.shareUrl.isBlank()?"volt.share.create":"volt.share.copy"),"link",2,()->v.actions.share(selected));
        control(sx+20,522,panel-40,"volt.editor.add_marker","auto",4,()->{selected.markers.add(new Clip.Marker(preview.decoder.position,"manual"));save(selected);v.notify("volt.notice.clip_marked");rebuildWidgets();});
        control(sx+20,566,panel-40,"volt.trash.move","trash",4,()->moveClips(List.of(selected),false));
        int controlsY=height-237;
        playButton=icon(px,controlsY,34,t("volt.play"),preview.decoder.playing?"pause":"play",0,()->{preview.decoder.toggle();});
        addRenderableWidget(new SeekBar(px+144,controlsY+7,pw-272,selected.seconds,()->preview.decoder.position,preview.decoder::seek));
        icon(px+pw-111,controlsY,34,t("volt.studio.muted"),"mute",0,()->v.notify("volt.notice.preview_silent"));
        icon(px+pw-68,controlsY,34,t("volt.previous"),"back",0,()->preview.decoder.seek(preview.decoder.position-5));
        icon(px+pw-31,controlsY,34,t("volt.next"),"next",0,()->preview.decoder.seek(preview.decoder.position+5));
        for(Clip.Marker mark:selected.markers.stream().limit(64).toList()){
            int mx=px+10+(int)((pw-20)*Math.clamp(mark.second()/Math.max(.1,selected.seconds),0,1));
            var hit=addRenderableWidget(new HitButton(mx-6,height-181,12,28,VoltTheme.literal(precise(mark.second())+" · "+mark.kind()),()->preview.decoder.seek(mark.second())));
            hit.setTooltip(net.minecraft.client.gui.components.Tooltip.create(hit.getMessage()));
        }
        String from=trimStart!=null&&trimId.equals(selected.id)?trimStart.getValue():"0",to=trimEnd!=null&&trimId.equals(selected.id)?trimEnd.getValue():String.format(Locale.ROOT,"%.2f",selected.seconds);trimId=selected.id;
        trimStart=field(px+14,height-69,84,from,"volt.trim.start");trimEnd=field(px+110,height-69,84,to,"volt.trim.end");
        addRenderableWidget(new TrimRange(px+10,height-155,pw-20,54,selected.seconds,trimStart,trimEnd));
        control(px+pw-224,height-69,224,36,t("volt.export.title"),"upload",2,()->menu("export"));
    }
    private void popupPanel(int x,int y,int w,int h){
        popupX=Math.clamp(x,RAIL+8,Math.max(RAIL+8,width-w-12));popupY=Math.clamp(y,HEADER+2,Math.max(HEADER+2,height-h-10));popupW=w;popupH=h;
        Button panel=addRenderableWidget(new HitButton(popupX,popupY,w,h,VoltTheme.literal("Volt"),()->{}){
            @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
                if(popup.equals("editProfile"))g.fill(RAIL,HEADER,VoltScreen.this.width,VoltScreen.this.height,0xAA000000);
                VoltTheme.box(g,popupX-1,popupY-1,popupW+2,popupH+2,0xFF454545);VoltTheme.box(g,popupX,popupY,popupW,popupH,0xFF1C1C1C);renderPopupLabels(g);
            }
        });popupWidgets.add(panel);
    }
    private void popButton(int x,int y,int w,String label,String glyph,int style,Runnable action){
        var b=addRenderableWidget(new StudioButton(x,y,w,32,VoltTheme.literal(label),glyph,style,false,action));popupWidgets.add(b);
    }
    private void item(int index,String label,String glyph,Runnable action){popButton(popupX+8,popupY+44+index*35,popupW-16,label,glyph,0,()->{popup="";action.run();rebuildWidgets();});}
    private void buildPopup(){
        if(popup.isEmpty())return;
        switch(popup){
            case "export"->{
                popupPanel(width/2-225,height/2-230,450,460);
                popButton(popupX+215,popupY+64,215,exportHeight+"p","down",1,()->{exportHeight=exportHeight==1080?720:1080;rebuildWidgets();});
                popButton(popupX+215,popupY+113,215,exportFps+" FPS","down",1,()->{exportFps=exportFps==30?60:30;rebuildWidgets();});
                popButton(popupX+215,popupY+162,215,t(exportPortrait?"volt.export.portrait":"volt.export.original"),"down",1,()->{exportPortrait=!exportPortrait;rebuildWidgets();});
                popButton(popupX+215,popupY+211,215,exportSpeed+"×","down",1,()->{exportSpeed=exportSpeed==.5?1:exportSpeed==1?1.5:exportSpeed==1.5?2:.5;rebuildWidgets();});
                popButton(popupX+215,popupY+260,215,(int)(exportVolume*100)+" %","mic",1,()->{exportVolume=exportVolume>=1?0:exportVolume+.25;rebuildWidgets();});
                popButton(popupX+20,popupY+400,150,t("volt.back"),"close",1,()->{popup="";rebuildWidgets();});
                popButton(popupX+190,popupY+400,240,t("volt.trim.save"),"check",2,()->{
                    try{v.actions.trim(selected,Double.parseDouble(trimStart.getValue().replace(',','.')),Double.parseDouble(trimEnd.getValue().replace(',','.')),new dev.volt.recording.VideoExport.Settings(exportHeight,exportFps,exportSpeed,exportVolume,exportPortrait));popup="";rebuildWidgets();}
                    catch(NumberFormatException e){v.notify("volt.notice.trim_invalid");}
                });
            }
            case "capture"->{
                popupPanel(151,58,344,416);
                var sw=addRenderableWidget(new StudioToggle(popupX+288,popupY+12,VoltTheme.text("volt.recording.on"),()->v.config.enabled,()->{v.config.enabled=!v.config.enabled;v.recorder.resetError();v.saveConfig();rebuildWidgets();}));popupWidgets.add(sw);
                popButton(popupX+16,popupY+180,312,t("volt.studio.capture_source"),"game",0,()->setting(0));
                popButton(popupX+16,popupY+224,312,v.config.height+"p · "+v.config.fps+" FPS","settings",0,()->menu("quality"));
                popButton(popupX+16,popupY+268,312,t("volt.studio.audio_settings"),"mic",0,()->setting(0));
                popButton(popupX+16,popupY+312,312,t("volt.studio.shortcuts_title"),"grid",0,()->setting(4));
                popButton(popupX+16,popupY+368,312,t("volt.studio.recorder_settings"),"next",0,()->setting(0));
            }
            case "quality"->{
                popupPanel(tab==2?settingsX+settingsW-240:676,tab==2?352:58,240,198);
                for(int i=0;i<4;i++){int quality=i;item(i,(i<2?"720p":"1080p")+" · "+(i%2==0?30:60)+" FPS","settings",()->{v.config.height=quality<2?720:1080;v.config.fps=quality%2==0?30:60;v.recorder.stop();v.saveConfig();});}
            }
            case "length"->{popupPanel(settingsX+settingsW-180,291,180,198);int[] lengths={15,30,60,120};for(int i=0;i<4;i++){int seconds=lengths[i];item(i,seconds+" "+t("volt.seconds"),"clock",()->{v.config.clipSeconds=seconds;v.saveConfig();});}}
            case "world"->{
                List<String> worlds=clips.stream().map(c->c.world).distinct().sorted().limit(12).toList();popupPanel(left+240,110,288,83+35*worlds.size());
                item(0,t("volt.all_worlds"),"game",()->{worldFilter="";page=0;});for(int i=0;i<worlds.size();i++){String value=worlds.get(i);item(i+1,value,"game",()->{worldFilter=value;page=0;});}
            }
            case "type"->{popupPanel(left+440,110,210,156);for(int i=0;i<3;i++){int n=i;item(i,t("volt.studio.type"+i),"clips",()->{typeFilter=n;page=0;});}}
            case "date"->{popupPanel(left+570,110,210,156);for(int i=0;i<3;i++){int n=i;item(i,t("volt.studio.date"+i),"clock",()->{dateFilter=n;page=0;});}}
            case "sort"->{popupPanel(width-224,156,200,156);for(int i=0;i<3;i++){int n=i;item(i,t("volt.sort."+i),"list",()->sortMode=n);}}
            case "context"->{
                popupPanel(popupX,popupY,220,tab==0&&libraryTab==4?121:226);Clip c=contextClip;if(c==null){popup="";return;}
                if(tab==0&&libraryTab==4){
                    item(0,t("volt.trash.restore"),"restore",()->moveClips(List.of(c),true));
                    item(1,t("volt.folder"),"folder",()->Util.getPlatform().openFile(v.library.path(c).getParent().toFile()));
                    break;
                }
                item(0,t("volt.open"),"play",()->open(c));
                item(1,t(c.shareUrl.isBlank()?"volt.share.create":"volt.share.copy"),"link",()->v.actions.share(c));
                item(2,t(c.favorite?"volt.unfavorite":"volt.favorite"),"star",()->{c.favorite=!c.favorite;save(c);});
                item(3,t("volt.folder"),"folder",()->Util.getPlatform().openFile(c.path(v.library.directory()).getParent().toFile()));
                item(4,t("volt.trash.move"),"trash",()->moveClips(List.of(c),false));
            }
            case "editProfile"->{
                popupPanel((width-720)/2,(height-566)/2,720,566);
                bio=field(popupX+24,popupY+90,672,v.config.bio,"volt.bio");bio.setMaxLength(160);popupWidgets.add(bio);
                popButton(popupX+24,popupY+153,204,t("volt.profile.import_avatar"),"user",4,()->v.profileImages.choose(false));
                popButton(popupX+242,popupY+153,204,t("volt.profile.import_banner"),"upload",4,()->v.profileImages.choose(true));
                popButton(popupX+460,popupY+153,234,t("volt.profile.reset_banner"),"refresh",4,()->{v.config.customBanner=false;v.saveConfig();});
                for(int i=0;i<11;i++){int n=i;Button b=addRenderableWidget(new AvatarButton(popupX+26+(i%8)*83,popupY+290+(i/8)*82,62,i,()->{v.config.avatar=n;v.config.customAvatar=false;v.saveConfig();}));popupWidgets.add(b);}
                popButton(popupX+518,popupY+510,178,t("volt.save"),"check",2,()->{v.config.bio=bio.getValue();v.saveConfig();popup="";v.notify("volt.notice.profile_saved");rebuildWidgets();});
                popButton(popupX+24,popupY+510,150,t("volt.back"),"close",1,()->{popup="";rebuildWidgets();});
            }
            default->popup="";
        }
    }
    private void renderPopupLabels(GuiGraphics g){
        int x=popupX+18,y=popupY+16;
        if(popup.equals("export")){
            text(g,t("volt.export.title"),x,y,21,VoltTheme.WHITE,true);
            String[] labels={"volt.studio.quality","volt.export.fps","volt.export.aspect","volt.export.speed","volt.export.volume"};
            for(int i=0;i<labels.length;i++)text(g,t(labels[i]),x,popupY+72+i*49,13,VoltTheme.WHITE,false);
            limited(g,t("volt.export.note"),x,popupY+335,popupW-36,11,VoltTheme.MUTED,false);
        }else if(popup.equals("capture")){
            text(g,t("volt.studio.record_with"),x,y,11,VoltTheme.MUTED,true);
            VoltTheme.box(g,x,popupY+56,popupW-36,108,0xFF303030);SmoothRaster.icon(g,"game",x+130,popupY+73,24,VoltTheme.MUTED);
            text(g,"Minecraft",x+112,popupY+106,15,VoltTheme.WHITE,true);
            text(g,t(v.recorder.state==ReplayRecorder.State.BUFFERING?"volt.state.buffering":"volt.studio.waiting"),x+24,popupY+136,11,VoltTheme.MUTED,false);
        }else if(popup.equals("editProfile")){
            text(g,t("volt.studio.edit_profile"),x+6,y+2,22,VoltTheme.WHITE,true);text(g,t("volt.bio"),x+6,popupY+64,11,VoltTheme.MUTED,false);
            text(g,t("volt.choose_avatar"),x+6,popupY+230,15,VoltTheme.WHITE,true);text(g,t("volt.profile.avatar_hint"),x+6,popupY+257,11,VoltTheme.MUTED,false);
            text(g,t("volt.minecraft_profile")+" · "+minecraft.getUser().getName(),x+6,popupY+464,12,VoltTheme.MUTED,false);
        }else text(g,t(switch(popup){case "quality"->"volt.studio.quality";case "length"->"volt.studio.history";case "world"->"volt.studio.world_filter";case "type"->"volt.studio.type0";case "date"->"volt.studio.date0";case "sort"->"volt.studio.sort_title";default->"volt.studio.clip_actions";}),x,y,11,VoltTheme.MUTED,true);
    }
    // Volt paints an opaque background itself. Older Screen.render implementations
    // call this after our content and would blur the already-drawn library.
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.pose().pushMatrix();g.pose().scale(uiScale,uiScale);mx=Math.round(mx/uiScale);my=Math.round(my/uiScale);
        g.fill(0,0,width,height,VoltTheme.BG);g.fill(0,0,RAIL,height,VoltTheme.PANEL);g.fill(RAIL,0,width,HEADER,VoltTheme.PANEL);
        g.fill(RAIL,HEADER,width,HEADER+1,0xFF242424);g.fill(RAIL-1,HEADER,RAIL,height,0xFF252525);
        VoltTheme.image(g,Identifier.fromNamespaceAndPath("volt","icon.png"),8,3,48,48);
        long disk=sizes.values().stream().mapToLong(Long::longValue).sum();
        text(g,disk<1L<<30?String.format(Locale.ROOT,"%.0f MB",disk/(1024.0*1024)):String.format(Locale.ROOT,"%.1f GB",disk/(1024.0*1024*1024)),8,height-205,9,VoltTheme.MUTED,false);
        text(g,"VOLT",15,height-188,8,VoltTheme.MUTED,true);
        if(selected!=null){limited(g,selected.title,365,22,width-630,13,VoltTheme.WHITE,true);renderEditor(g);}else if(tab==0)renderLibrary(g);else if(tab==1)renderProfile(g);else renderSettings(g);
        super.render(g,mx,my,delta);
        if(System.currentTimeMillis()<v.noticeUntil){int w=Math.min(640,width-RAIL-80),y=height-(selection.isEmpty()?99:150);VoltTheme.box(g,RAIL+30,y,w,38,0xF0323825);limited(g,t(v.notice),RAIL+44,y+10,w-26,12,VoltTheme.WHITE,false);}
        g.pose().popMatrix();
    }
    private void renderLibrary(GuiGraphics g){
        g.fill(RAIL,112,width,113,0xFF232323);g.fill(RAIL,158,width,159,0xFF292929);
        int tx=left;for(int i=0;i<libraryTab;i++)tx+=(i==0?106:138)+6;g.fill(tx+12,154,tx+(libraryTab==0?106:138)-12,157,VoltTheme.WHITE);
        List<Clip> data=filtered();text(g,data.isEmpty()?t("volt.nav.0"):dateGroup(data.get(Math.min(page*pageSize(),data.size()-1)).created),left,164,12,VoltTheme.WHITE,true);
        text(g,t("volt.result_count",data.size()),left+136,165,10,VoltTheme.MUTED,false);
        renderCards(g);
        if(data.isEmpty()){
            int cx=left+contentWidth/2,cy=height/2-12;SmoothRaster.icon(g,"clips",cx-26,cy-62,52,VoltTheme.MUTED);
            text(g,t(loading?"volt.loading":"volt.empty"),cx-170,cy+5,23,VoltTheme.WHITE,true);text(g,t("volt.empty_hint"),cx-190,cy+49,12,VoltTheme.MUTED,false);
        }
        text(g,(page+1)+" / "+Math.max(1,(data.size()+pageSize()-1)/pageSize()),left+86,height-34,11,VoltTheme.MUTED,false);
        text(g,t(libraryTab==4?"volt.trash.hint":"volt.studio.local_library"),left+(undoDeleted.isEmpty()?180:286),height-34,11,VoltTheme.MUTED,false);
    }
    private void renderCards(GuiGraphics g){
        for(Card card:cards){
            Clip c=card.clip;int x=card.x,y=card.y,w=card.w,h=card.h;
            VoltTheme.box(g,x,y,w,h,selection.contains(c.id)?VoltTheme.ACCENT:card.hit.isHoveredOrFocused()?0xFF444444:0xFF292929);
            VoltTheme.box(g,x+1,y+1,w-2,h-2,VoltTheme.CARD);
            Identifier thumb=v.thumbnails.get(c);
            if(!card.list){
                if(thumb!=null)cover(g,thumb,x+1,y+1,w-2,card.imageHeight,v.thumbnails.aspect(c));else VoltTheme.box(g,x+1,y+1,w-2,card.imageHeight,0xFF252525);
                if(card.hit.isHoveredOrFocused()){VoltTheme.box(g,x+w/2-23,y+card.imageHeight/2-23,46,46,0xD5111111);SmoothRaster.icon(g,tab==0&&libraryTab==4?"restore":"play",x+w/2-12,y+card.imageHeight/2-12,24,VoltTheme.WHITE);}
                int durationW=SmoothRaster.width(VoltTheme.duration(c.seconds),11,true)+16;
                VoltTheme.box(g,x+w-durationW-9,y+card.imageHeight-28,durationW,22,0xE6000000);text(g,VoltTheme.duration(c.seconds),x+w-durationW-1,y+card.imageHeight-25,11,VoltTheme.WHITE,true);
                limited(g,c.title,x+12,y+card.imageHeight+10,w-24,13,VoltTheme.WHITE,true);
                String metadata=t("volt.studio.on_device")+" · "+megabytes(sizes.getOrDefault(c.id,0L))+" · "+date(c.created);
                limited(g,metadata,x+12,y+card.imageHeight+33,w-24,10,VoltTheme.MUTED,false);
                if(h>=card.imageHeight+80)g.fill(x+1,y+h-36,x+w-1,y+h-35,0xFF303030);
                if(c.recovered||!c.captureReason.isEmpty()){int bx=x+8,by=y+card.imageHeight-29;VoltTheme.box(g,bx,by,91,21,0xE6000000);SmoothRaster.icon(g,c.recovered?"check":"auto",bx+5,by+4,13,VoltTheme.ACCENT);text(g,t(c.recovered?"volt.studio.recovered_badge":"volt.studio.auto_badge"),bx+22,by+4,9,VoltTheme.ACCENT,true);}
            }else{
                if(thumb!=null)cover(g,thumb,x+8,y+7,104,58,v.thumbnails.aspect(c));
                limited(g,c.title,x+129,y+14,w-230,14,VoltTheme.WHITE,true);limited(g,c.world+" · "+VoltTheme.duration(c.seconds)+" · "+date(c.created),x+129,y+43,w-230,11,VoltTheme.MUTED,false);
            }
        }
    }
    private Identifier avatar(){return v.config.customAvatar&&v.profileImages.avatarReady?ProfileImages.AVATAR:v.profileImages.defaultAvatar(v.config.avatar);}
    private void renderProfile(GuiGraphics g){
        int x=profileX,w=profileW;
        g.fill(x,80,x+w,360,0xFF151515);
        Identifier cover=v.config.customBanner&&v.profileImages.bannerReady?ProfileImages.BANNER:clips.isEmpty()?null:v.thumbnails.get(clips.getFirst());
        if(cover!=null)cover(g,cover,x,80,w,175,v.config.customBanner?4f:16f/9);else{
            g.fillGradient(x,80,x+w,255,0xFF303632,0xFF171917);text(g,"VOLT",x+w-220,111,42,0xFF697166,true);text(g,t("volt.profile.cover_hint"),x+24,116,21,VoltTheme.WHITE,true);
        }
        Identifier avatar=avatar();if(avatar!=null)VoltTheme.image(g,avatar,x+22,211,100,100);
        text(g,minecraft.getUser().getName(),x+142,271,25,VoltTheme.WHITE,true);
        limited(g,v.config.bio.isBlank()?t("volt.minecraft_profile"):v.config.bio,x+142,310,w-316,12,VoltTheme.MUTED,false);
        text(g,t("volt.clip_count",clips.size()),x+142,339,11,VoltTheme.WHITE,true);
        text(g,t("volt.studio.profile_favorites",clips.stream().filter(c->c.favorite).count()),x+270,339,11,VoltTheme.MUTED,false);
        g.fill(x,404,x+w,405,VoltTheme.LINE);g.fill(x+profileTab*118+10,400,x+profileTab*118+102,403,VoltTheme.WHITE);
        if(profileTab==1){text(g,t("volt.studio.all_clips"),x,420,13,VoltTheme.WHITE,true);renderCards(g);if(cards.isEmpty())text(g,t("volt.empty"),x+24,500,22,VoltTheme.MUTED,false);return;}
        int sw=(w-28)/3;
        stat(g,x,430,sw,t("volt.stats.clips"),""+clips.size(),t("volt.stats.local"));
        stat(g,x+sw+14,430,sw,t("volt.stats.duration"),VoltTheme.duration(clips.stream().mapToDouble(c->c.seconds).sum()),t("volt.stats.recorded"));
        stat(g,x+2*(sw+14),430,sw,t("volt.favorites"),""+clips.stream().filter(c->c.favorite).count(),t("volt.stats.saved"));
        if(profileTab==0){text(g,t("volt.studio.featured"),x,567,15,VoltTheme.WHITE,true);renderCards(g);if(cards.isEmpty())text(g,t("volt.empty_hint"),x+20,620,13,VoltTheme.MUTED,false);}
        else{
            VoltTheme.box(g,x,554,w,218,VoltTheme.CARD);text(g,t("volt.studio.activity"),x+22,573,15,VoltTheme.WHITE,true);
            long[] days=new long[7];LocalDate now=LocalDate.now();for(Clip c:clips){long ago=java.time.temporal.ChronoUnit.DAYS.between(Instant.ofEpochMilli(c.created).atZone(ZoneId.systemDefault()).toLocalDate(),now);if(ago>=0&&ago<7)days[6-(int)ago]++;}
            long max=Math.max(1,Arrays.stream(days).max().orElse(1));int gap=(w-52)/7;
            for(int i=0;i<7;i++){int bx=x+30+i*gap,bh=(int)(105*days[i]/max);g.fill(bx,730-bh,bx+gap-18,730,VoltTheme.ACCENT);text(g,now.minusDays(6-i).format(DateTimeFormatter.ofPattern("dd.MM")),bx,744,10,VoltTheme.MUTED,false);if(days[i]>0)text(g,""+days[i],bx,708-bh,11,VoltTheme.WHITE,true);}
        }
    }
    private void stat(GuiGraphics g,int x,int y,int w,String label,String value,String hint){VoltTheme.box(g,x,y,w,110,0xFF242424);text(g,label,x+18,y+16,11,VoltTheme.MUTED,false);text(g,value,x+18,y+38,29,VoltTheme.WHITE,true);text(g,hint,x+18,y+82,10,VoltTheme.MUTED,false);}
    private void row(GuiGraphics g,int y,String label,String description){int max=settingsW-265;text(g,label,settingsX,y,14,VoltTheme.WHITE,true);limited(g,description,settingsX,y+25,Math.max(100,max),11,VoltTheme.MUTED,false);g.fill(settingsX,y+57,settingsX+settingsW,y+58,0xFF252525);}
    private void renderSettings(GuiGraphics g){
        text(g,t("volt.nav.2"),left,91,27,VoltTheme.WHITE,true);text(g,t("volt.studio.settings_subtitle"),left,132,12,VoltTheme.MUTED,false);
        g.fill(settingsX-24,168,settingsX-23,height-35,0xFF292929);
        String key=switch(settingsTab){case 0->"volt.settings.tab0";case 1->"volt.settings.tab1";case 2->"volt.settings.tab2";case 3->"volt.settings.tab3";case 5->"volt.feedback.title";case 6->"volt.performance.title";default->"volt.studio.shortcuts_title";};
        text(g,t(key),settingsX,169,11,VoltTheme.MUTED,true);
        int x=settingsX,w=settingsW;
        if(settingsTab==0){
            row(g,194,t("volt.studio.record_with"),t("volt.studio.buffer_description"));
            row(g,256,t("volt.studio.history"),t("volt.studio.history_description"));
            row(g,318,t("volt.studio.quality"),t("volt.studio.quality_description"));
            row(g,380,t("volt.studio.encoder"),t("volt.studio.encoder_description"));
            row(g,442,t("volt.studio.overlay"),t("volt.studio.overlay_description"));
            row(g,504,t("volt.studio.audio_settings"),t("volt.studio.audio_description"));
            if(advanced){text(g,t("volt.ffmpeg"),x,608,11,VoltTheme.MUTED,false);text(g,t("volt.directory"),x+(w+14)/2,608,11,VoltTheme.MUTED,false);text(g,t("volt.audio_device"),x,681,11,VoltTheme.MUTED,false);}
            else{
                text(g,t("volt.studio.file_location"),x,623,12,VoltTheme.WHITE,true);limited(g,v.library.directory().toString(),x,646,w-12,11,VoltTheme.MUTED,false);
                if(System.currentTimeMillis()-diagnosticAt>1000){diagnostic=encoderName(v.recorder.encoder)+" · "+String.format(Locale.ROOT,"%.2f ms",v.capture.lastCaptureMillis);diagnosticAt=System.currentTimeMillis();}
                limited(g,diagnostic,x,742,w,11,VoltTheme.MUTED,false);
            }
            if(!v.recorder.error.isBlank())limited(g,v.recorder.error,x,height-70,w-170,11,0xFFFF9797,false);
        }else if(settingsTab==3){
            row(g,205,t("volt.auto.title"),t("volt.auto.timing"));row(g,275,t("volt.auto.combo_on"),t("volt.auto.combo_hint"));
            row(g,345,t("volt.studio.combo_threshold"),t("volt.studio.confirmed_hits"));row(g,415,t("volt.auto.combat_on"),t("volt.studio.combat_description"));
            row(g,485,t("volt.auto.adventure_on"),t("volt.auto.adventure_hint"));row(g,555,t("volt.studio.rate_limit"),t("volt.auto.merge_hint"));row(g,625,t("volt.death.on"),t("volt.studio.death_description"));
            text(g,t("volt.auto.local_hint"),x,729,11,VoltTheme.MUTED,false);
        }else if(settingsTab==1){
            text(g,t("volt.share.explain"),x,210,13,VoltTheme.WHITE,false);text(g,t("volt.endpoint"),x,229,11,VoltTheme.MUTED,false);text(g,t("volt.token"),x,317,11,VoltTheme.MUTED,false);
            VoltTheme.box(g,x,477,w,146,0xFF202020);SmoothRaster.icon(g,"link",x+22,501,30,VoltTheme.ACCENT);text(g,t("volt.studio.link_sharing"),x+68,502,16,VoltTheme.WHITE,true);
            limited(g,t("volt.share.local_default"),x+22,550,w-44,12,VoltTheme.MUTED,false);limited(g,t("volt.share.privacy"),x+22,581,w-44,11,VoltTheme.MUTED,false);
        }else if(settingsTab==2){
            text(g,t("volt.studio.discord_activity"),x,215,14,VoltTheme.WHITE,true);limited(g,t("volt.discord.automatic"),x,269,w,12,VoltTheme.MUTED,false);
            limited(g,t(v.discord.status),x,407,w,12,VoltTheme.MUTED,false);VoltTheme.box(g,x,463,w,168,0xFF202020);
            VoltTheme.image(g,Identifier.fromNamespaceAndPath("volt","icon.png"),x+18,486,100,100);text(g,"VOLT",x+142,486,12,VoltTheme.WHITE,true);text(g,"Clipped with Volt",x+142,516,20,VoltTheme.WHITE,true);text(g,t("volt.studio.presence_preview"),x+142,557,11,VoltTheme.MUTED,false);
            limited(g,t("volt.discord.privacy"),x,665,w,12,VoltTheme.MUTED,false);
        }else if(settingsTab==6){
            row(g,217,t("volt.performance.on"),t("volt.performance.description"));
            row(g,287,t("volt.performance.gpu_yuv"),t("volt.performance.gpu_yuv_hint"));
            text(g,t("volt.performance.hardware"),x,370,14,VoltTheme.WHITE,true);
            limited(g,t("volt.performance.explain"),x,407,w,12,VoltTheme.MUTED,false);
            if(v.recorder.state==ReplayRecorder.State.BUFFERING){
                text(g,encoderName(v.recorder.encoder)+" · "+v.recorder.width+"×"+v.recorder.height+" · "+v.recorder.fps()+" FPS",x,464,15,VoltTheme.ACCENT,true);
                text(g,t("volt.performance.capture_time")+" · "+String.format(Locale.ROOT,"%.2f ms",v.capture.averageCaptureMillis),x,505,12,VoltTheme.MUTED,false);
            }else text(g,t("volt.performance.waiting"),x,464,13,VoltTheme.MUTED,false);
        }else if(settingsTab==5){
            row(g,217,t("volt.feedback.animation"),t("volt.feedback.animation_hint"));
            row(g,287,t("volt.feedback.volume"),t("volt.feedback.volume_hint"));
            row(g,357,t("volt.feedback.button_volume"),t("volt.feedback.button_hint"));
            row(g,427,t("volt.feedback.style"),t("volt.feedback.style_hint"));
            limited(g,t("volt.feedback.recording_hint"),x,585,w,12,VoltTheme.MUTED,false);
        }else{
            String[] actions={"menu","clip","record","marker"};String[] labels={"key.volt.menu","key.volt.clip","key.volt.record","key.volt.marker"};
            for(int i=0;i<4;i++){int y=227+i*62;text(g,t(labels[i]),x,y,14,VoltTheme.WHITE,false);VoltTheme.box(g,x+w-164,y-4,164,31,0xFF292929);text(g,v.keyName(actions[i]),x+w-148,y+1,12,VoltTheme.WHITE,true);g.fill(x,y+43,x+w,y+44,VoltTheme.LINE);}
            limited(g,t("volt.studio.conflict_help"),x,562,w,12,VoltTheme.MUTED,false);
        }
    }
    private void renderEditor(GuiGraphics g){
        int panel=320,sx=width-panel,px=RAIL+26,pw=sx-px-28;
        g.fill(sx,HEADER,width,height,0xFF121212);g.fill(sx-1,HEADER,sx,height,VoltTheme.LINE);
        text(g,t("volt.studio.details"),sx+20,76,14,VoltTheme.WHITE,true);
        text(g,date(selected.created)+" · "+megabytes(sizes.getOrDefault(selected.id,0L))+" · "+VoltTheme.duration(selected.seconds),sx+20,163,10,VoltTheme.MUTED,false);
        text(g,t("volt.studio.saved_by"),sx+20,184,10,VoltTheme.MUTED,false);text(g,selected.player,sx+20,201,11,VoltTheme.WHITE,true);
        text(g,t("volt.studio.highlight_type"),sx+20,280,11,VoltTheme.MUTED,true);
        limited(g,selected.captureReason.isEmpty()?t("volt.studio.type1"):t("volt.highlight."+selected.captureReason,selected.comboHits),sx+20,303,panel-40,13,VoltTheme.ACCENT,true);
        text(g,t("volt.studio.game"),sx+20,352,11,VoltTheme.MUTED,true);SmoothRaster.icon(g,"game",sx+20,376,18,VoltTheme.WHITE);text(g,"Minecraft",sx+48,376,13,VoltTheme.WHITE,true);
        text(g,t("volt.studio.world"),sx+20,416,11,VoltTheme.MUTED,true);limited(g,selected.world,sx+20,438,panel-40,12,VoltTheme.WHITE,false);
        text(g,t("volt.studio.file_location"),sx+20,531,11,VoltTheme.MUTED,true);text(g,t("volt.studio.on_device"),sx+20,555,13,VoltTheme.WHITE,false);
        if(!v.actions.status.isBlank())limited(g,t(v.actions.status),sx+20,596,panel-40,11,VoltTheme.ACCENT,false);
        int videoH=Math.min(height-340,pw*9/16),videoW=videoH*16/9,videoX=px+(pw-videoW)/2,videoY=84+Math.max(0,(height-340-videoH)/2);
        g.fill(px,82,px+pw,height-255,0xFF050505);
        Identifier frame=preview.frame();boolean decoded=frame!=null;if(frame==null)frame=v.thumbnails.get(selected);
        if(frame!=null)cover(g,frame,videoX,videoY,videoW,videoH,decoded?16f/9:v.thumbnails.aspect(selected));
        if(preview.decoder.loading)text(g,t("volt.loading"),px+18,height-283,12,VoltTheme.WHITE,false);
        if(!preview.decoder.error.isBlank())text(g,t("volt.studio.preview_failed"),px+18,height-283,12,0xFFFFA0A0,false);
        text(g,precise(preview.decoder.position)+" / "+precise(selected.seconds),px+44,height-227,11,VoltTheme.WHITE,false);
        text(g,t("volt.studio.timeline"),px+12,height-195,11,VoltTheme.MUTED,false);
        for(int i=0;i<6;i++){
            int x=px+10+(pw-20)*i/6;int end=px+10+(pw-20)*(i+1)/6;
            text(g,precise(selected.seconds*i/5),x,height-179,9,VoltTheme.MUTED,false);
            Identifier tile=v.thumbnails.getFrame(selected,i);if(tile!=null)cover(g,tile,x,height-155,end-x,54,v.thumbnails.aspect(selected));else g.fill(x,height-155,end,height-101,0xFF262626);
        }
        for(Clip.Marker marker:selected.markers){int mx=px+10+(int)((pw-20)*marker.second()/Math.max(.1,selected.seconds));g.fill(mx,height-163,mx+2,height-156,VoltTheme.ACCENT);}
        text(g,t("volt.trim.start"),px+15,height-88,10,VoltTheme.MUTED,false);text(g,t("volt.trim.end"),px+111,height-88,10,VoltTheme.MUTED,false);
        limited(g,t("volt.editor.keys"),px+223,height-60,Math.max(80,pw-470),10,VoltTheme.MUTED,false);
    }
    private String encoderName(String value){return switch(value){case "auto"->t("volt.encoder.auto");case "libx264"->"CPU · H.264";case "h264_vaapi"->"GPU · VAAPI";case "h264_nvenc"->"GPU · NVIDIA";case "h264_qsv"->"GPU · Intel QSV";case "h264_amf"->"GPU · AMD";case "h264_videotoolbox"->"GPU · Apple VideoToolbox";default->t("volt.encoder.pending");};}
    private static void cover(GuiGraphics g,Identifier image,int x,int y,int w,int h,float aspect){
        float target=(float)w/h,u0=0,u1=1,v0=0,v1=1;if(target>aspect){float portion=aspect/target;v0=(1-portion)/2;v1=1-v0;}else{float portion=target/aspect;u0=(1-portion)/2;u1=1-u0;}
        g.blit(image,x,y,x+w,y+h,u0,u1,v0,v1);
    }
    private static String precise(double seconds){int total=(int)Math.max(0,seconds);return String.format(Locale.ROOT,"%d:%02d",total/60,total%60);}
    private static String megabytes(long bytes){return String.format(Locale.ROOT,"%.1f MB",bytes/(1024.0*1024));}
    private static String date(long millis){return DateTimeFormatter.ofPattern("dd MMM · HH:mm",Locale.getDefault()).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));}
    private static String dateGroup(long millis){LocalDate day=Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();return day.equals(LocalDate.now())?t("volt.studio.today"):day.equals(LocalDate.now().minusDays(1))?t("volt.studio.yesterday"):day.format(DateTimeFormatter.ofPattern("dd MMMM yyyy",Locale.getDefault()));}
    private static String t(String key,Object...args){return Component.translatable(key,args).getString();}
    private static void text(GuiGraphics g,String value,int x,int y,int size,int color,boolean bold){SmoothRaster.text(g,value,x,y,size,color,bold);}
    private static void limited(GuiGraphics g,String value,int x,int y,int max,int size,int color,boolean bold){if(max<1)return;String out=value;if(SmoothRaster.width(out,size,bold)>max){while(!out.isEmpty()&&SmoothRaster.width(out+"…",size,bold)>max)out=out.substring(0,out.length()-1);out+="…";}text(g,out,x,y,size,color,bold);}
    @Override public void tick(){
        if(!lastStatus.equals(v.actions.status)&&!v.actions.busy.get()){lastStatus=v.actions.status;if(tab==0&&selected==null)refresh();}
        if(lastClipSeen!=v.recorder.lastClipMillis){lastClipSeen=v.recorder.lastClipMillis;refresh();}
        if(filterDirty&&selected==null){filterDirty=false;boolean focus=search!=null&&search.isFocused();int cursor=search==null?0:search.getCursorPosition();rebuildWidgets();if(focus&&search!=null){setFocused(search);search.setFocused(true);search.setCursorPosition(cursor);}}
        if(captureStatus!=null)captureStatus.setMessage(VoltTheme.literal(recordingTitle()));
        if(recordControl!=null)recordControl.setMessage(VoltTheme.literal(t(v.recorder.longRecording?"volt.studio.stop_record":"volt.studio.long_record",v.keyName("record"))));
        if(playButton!=null&&preview!=null)playButton.setIcon(preview.decoder.playing?"pause":"play");
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void resize(int w,int h){physicalWidth=w;physicalHeight=h;super.resize(w,h);}
    private MouseButtonEvent scaled(MouseButtonEvent event){return new MouseButtonEvent(event.x()/uiScale,event.y()/uiScale,event.buttonInfo());}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean dbl){
        MouseButtonEvent e=scaled(event);
        if(!popup.isEmpty()){
            if(e.x()>=popupX&&e.x()<popupX+popupW&&e.y()>=popupY&&e.y()<popupY+popupH){
                for(int i=popupWidgets.size()-1;i>=0;i--){var widget=popupWidgets.get(i);if(widget.mouseClicked(e,dbl)){setFocused(widget);return true;}}
                return true;
            }
            popup="";rebuildWidgets();return true;
        }
        for(int i=floatingWidgets.size()-1;i>=0;i--){var widget=floatingWidgets.get(i);if(widget.mouseClicked(e,dbl)){setFocused(widget);if(e.button()==0)setDragging(true);return true;}}
        if(e.button()==1&&selected==null){for(Card c:cards)if(e.x()>=c.x&&e.x()<c.x+c.w&&e.y()>=c.y&&e.y()<c.y+c.h){contextClip=c.clip;popupX=(int)e.x();popupY=(int)e.y();popup="context";rebuildWidgets();return true;}}
        return super.mouseClicked(e,dbl);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(scaled(e));}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(scaled(e),dx/uiScale,dy/uiScale);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(popup.isEmpty()&&selected==null&&(tab==0||tab==1&&profileTab==1)){if(dy<0&&(page+1)*pageSize()<filtered().size())page++;else if(dy>0&&page>0)page--;rebuildWidgets();return true;}return super.mouseScrolled(x/uiScale,y/uiScale,dx,dy);}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent key){
        if(key.key()==256){if(!popup.isEmpty()){popup="";rebuildWidgets();return true;}if(selected!=null){back();return true;}}
        if(popup.isEmpty()&&!(getFocused() instanceof EditBox)){
            boolean ctrl=(key.modifiers()&2)!=0;
            if(ctrl&&key.key()==90&&!undoDeleted.isEmpty()){moveClips(undoDeleted,true);return true;}
            if(ctrl&&key.key()==70&&selected==null&&tab==0&&search!=null){setFocused(search);search.setFocused(true);return true;}
            if(ctrl&&key.key()==69&&selected!=null){menu("export");return true;}
            if(key.key()==261){
                if(selected!=null){moveClips(List.of(selected),false);return true;}
                if(tab==0&&libraryTab!=4&&!selection.isEmpty()){moveClips(clips.stream().filter(c->selection.contains(c.id)).toList(),false);return true;}
            }
        }
        if(selected==null&&popup.isEmpty()&&tab==0&&(key.modifiers()&2)!=0&&key.key()==65&&!(getFocused() instanceof EditBox)){filtered().stream().limit(50).forEach(c->selection.add(c.id));rebuildWidgets();return true;}
        if(selected!=null&&popup.isEmpty()&&!(getFocused() instanceof EditBox)){
            boolean ctrl=(key.modifiers()&2)!=0;
            if(key.key()==263||key.key()==262){
                if(ctrl){double next=dev.volt.ui.MarkerNavigation.adjacent(selected.markers,preview.decoder.position,key.key()==262,selected.seconds);preview.decoder.seek(next);}
                else preview.decoder.seek(preview.decoder.position+(key.key()==262?5:-5));return true;
            }
            if(ctrl&&key.key()==70){selected.favorite=!selected.favorite;save(selected);rebuildWidgets();return true;}
            if(key.key()==73){trimStart.setValue(String.format(Locale.ROOT,"%.2f",preview.decoder.position));return true;}
            if(key.key()==79){trimEnd.setValue(String.format(Locale.ROOT,"%.2f",preview.decoder.position));return true;}
        }
        if(selected!=null&&key.key()==32&&!(getFocused() instanceof EditBox)){preview.decoder.toggle();return true;}
        return super.keyPressed(key);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void removed(){v.thumbnails.suspend();if(preview!=null){preview.close();preview=null;}SmoothRaster.clear();}
    private static class HitButton extends Button {
        HitButton(int x,int y,int w,int h,Component label,Runnable action){super(x,y,w,h,label,b->action.run(),DEFAULT_NARRATION);}
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){ClipFeedback.click(manager);}
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){}
    }
    private static final class AvatarButton extends Button {
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager){ClipFeedback.click(manager);}
        private final int index;
        AvatarButton(int x,int y,int size,int index,Runnable action){super(x,y,size,size,VoltTheme.literal("Avatar "+(index+1)),b->action.run(),DEFAULT_NARRATION);this.index=index;}
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){var v=VoltClient.INSTANCE;boolean selected=!v.config.customAvatar&&v.config.avatar==index;VoltTheme.box(g,getX()-3,getY()-3,width+6,height+6,selected?VoltTheme.ACCENT:isHoveredOrFocused()?0xFF777777:0xFF333333);Identifier id=v.profileImages.defaultAvatar(index);if(id!=null)VoltTheme.image(g,id,getX(),getY(),width,height);}
    }
}
