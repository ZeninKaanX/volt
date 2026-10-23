package dev.volt.recording;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Ffmpeg {
    private Ffmpeg() { }
    /** Lower Unix media-worker CPU priority without touching the game's scheduler or settings. */
    public static List<String> backgroundCommand(List<String> command, boolean prioritizeGame) {
        if (!prioritizeGame || System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ||
            !java.nio.file.Files.isExecutable(Path.of("/usr/bin/nice"))) return command;
        List<String> result = new ArrayList<>(List.of("/usr/bin/nice", "-n", "5"));
        result.addAll(command); return result;
    }
    public static boolean probe(List<String> args, Path log, int timeout) {
        Process p = null;
        try {
            p = new ProcessBuilder(backgroundCommand(args, true)).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            return p.waitFor(timeout, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally { if (p != null && p.isAlive()) p.destroyForcibly(); }
    }
    public static String selectEncoder(String executable, String preferred, String vaapiDevice, Path log) throws IOException {
        if (!probe(List.of(executable, "-version"), log, 5)) throw new IOException("FFmpeg bulunamadı / FFmpeg not found");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        List<String> automatic = os.contains("mac") || os.contains("darwin") ? List.of("h264_videotoolbox", "libx264") :
            List.of("h264_nvenc", "h264_vaapi", "h264_qsv", "h264_amf", "libx264");
        List<String> options = preferred.equals("auto") ? automatic : List.of(preferred);
        for (String candidate : options) {
            if (!List.of("h264_nvenc", "h264_vaapi", "h264_qsv", "h264_amf", "h264_videotoolbox", "libx264").contains(candidate)) continue;
            List<String> command = new ArrayList<>(List.of(executable, "-hide_banner", "-loglevel", "error"));
            if (candidate.equals("h264_vaapi")) {
                if (!java.nio.file.Files.isReadable(Path.of(vaapiDevice))) continue;
                command.addAll(List.of("-vaapi_device", vaapiDevice));
            }
            command.addAll(List.of("-f", "lavfi", "-i", "color=size=128x128:rate=30"));
            if (candidate.equals("h264_vaapi")) command.addAll(List.of("-vf", "format=nv12,hwupload"));
            command.addAll(List.of("-frames:v", "1", "-c:v", candidate, "-f", "null", "-"));
            if (probe(command, log, 5)) return candidate;
        }
        throw new IOException("Kullanılabilir H.264 kodlayıcı yok / No H.264 encoder available");
    }
    public static void run(List<String> command, Path log) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(backgroundCommand(command, true)).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if (!p.waitFor(180, TimeUnit.SECONDS)) throw new IOException("FFmpeg timeout");
            if (p.exitValue() != 0) throw new IOException("FFmpeg failed; see " + log.getFileName());
        } finally { if (p.isAlive()) p.destroyForcibly(); }
    }
}
