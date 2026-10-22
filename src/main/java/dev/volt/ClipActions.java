package dev.volt;

import dev.volt.recording.Ffmpeg;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import com.google.gson.JsonParser;

public final class ClipActions implements AutoCloseable {
    private final VoltClient volt;
    public final AtomicBoolean busy = new AtomicBoolean();
    public volatile String status = "";
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("Volt-sharing").factory());
    public ClipActions(VoltClient volt) { this.volt = volt; }
    public void share(Clip clip) {
        if (!clip.shareUrl.isBlank()) { copyLink(clip.shareUrl); return; }
        if (volt.config.uploadEndpoint.isBlank() || volt.config.uploadToken.isBlank()) { volt.notify("volt.notice.sharing_setup"); return; }
        if (!busy.compareAndSet(false, true)) { volt.notify("volt.notice.busy"); return; }
        status = "volt.share.uploading";
        worker.submit(() -> {
            try {
                URI endpoint = URI.create(volt.config.uploadEndpoint);
                if (!allowedUrl(endpoint)) throw new IllegalArgumentException("HTTPS upload endpoint required");
                Path file = clip.path(volt.library.directory());
                if (Files.size(file) > 250L * 1024 * 1024) throw new IllegalArgumentException("Clip exceeds 250 MB");
                HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofMinutes(10))
                    .header("Authorization", "Bearer " + volt.config.uploadToken).header("Content-Type", "video/mp4")
                    .PUT(HttpRequest.BodyPublishers.ofFile(file)).build();
                try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build()) {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 201) throw new IllegalStateException("Upload HTTP " + response.statusCode());
                    String url = JsonParser.parseString(response.body()).getAsJsonObject().get("url").getAsString();
                    if (!allowedUrl(URI.create(url))) throw new IllegalArgumentException("Invalid share URL");
                    clip.shareUrl = url; volt.library.save(clip); copyLink(url); status = "volt.share.ready";
                }
            } catch (Exception e) { status = "volt.share.failed"; volt.notify("volt.notice.upload_failed"); }
            finally { busy.set(false); }
        });
    }
    public static boolean allowedUrl(URI uri) {
        return uri.getUserInfo() == null && uri.getHost() != null && (uri.getScheme().equals("https") ||
            (uri.getScheme().equals("http") && Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(uri.getHost())));
    }
    private void copyLink(String url) {
        Minecraft.getInstance().execute(() -> { Minecraft.getInstance().keyboardHandler.setClipboard(url); volt.notify("volt.notice.link_copied"); });
    }
    public void trim(Clip source,double start,double end){trim(source,start,end,dev.volt.recording.VideoExport.Settings.original());}
    public void trim(Clip source, double start, double end, dev.volt.recording.VideoExport.Settings settings) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start || end > source.seconds + .1) { volt.notify("volt.notice.trim_invalid"); return; }
        if (!busy.compareAndSet(false, true)) { volt.notify("volt.notice.busy"); return; }
        status = "volt.trim.working";
        worker.submit(() -> {
            Path output = null;
            try {
                Clip c = new Clip(); c.id = UUID.randomUUID().toString(); c.file = c.id + ".mp4"; c.title = source.title + " · Trim"; c.captureReason = source.captureReason; c.comboHits = source.comboHits;
                c.player = source.player; c.world = source.world; c.dimension = source.dimension; c.created = System.currentTimeMillis(); c.seconds = (end - start)/settings.speed();
                for (Clip.Marker m : source.markers) if (m.second() >= start && m.second() <= end) c.markers.add(new Clip.Marker((m.second() - start)/settings.speed(), m.kind()));
                output = volt.library.directory().resolve(c.id + ".partial.mp4");
                Ffmpeg.run(dev.volt.recording.VideoExport.command(volt.config.ffmpeg,source.path(volt.library.directory()),output,start,end,settings),volt.gameDir.resolve("volt/trim.log"));
                Files.move(output, c.path(volt.library.directory())); volt.library.save(c); volt.notify("volt.notice.saved"); status = "";
            } catch (Exception e) { volt.notify("volt.notice.export_failed"); status = "volt.share.failed"; }
            finally { try { if (output != null) Files.deleteIfExists(output); } catch (Exception ignored) { } busy.set(false); }
        });
    }
    @Override public void close() { worker.shutdownNow(); }
}
