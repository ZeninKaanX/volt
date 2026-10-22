package dev.volt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Clip {
    public String id;
    public String title;
    public String file;
    public String player;
    public String world;
    public String dimension;
    public long created;
    public double seconds;
    public boolean favorite;
    public String shareUrl = "";
    public String captureReason = "";
    public int comboHits;
    public List<Marker> markers = new ArrayList<>();
    public record Marker(double second, String kind) { }
    public Path path(Path directory) { return directory.resolve(file); }
}
