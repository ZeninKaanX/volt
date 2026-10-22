package dev.volt.smoke;
import java.util.*;
public final class SmokeMetrics {
    public static int phase;
    private static long previous;
    private static final Map<Integer,List<Double>> samples = new HashMap<>();
    public static void frame() {
        long now = System.nanoTime(); double ms = (now - previous) / 1e6; previous = now;
        if (phase > 0 && ms < 1000) samples.computeIfAbsent(phase, p -> new ArrayList<>()).add(ms);
    }
    public static String report() {
        StringBuilder s = new StringBuilder();
        for (int phase : List.of(1,2,3)) {
            List<Double> values = samples.getOrDefault(phase, List.of()).stream().sorted().toList();
            if (!values.isEmpty()) s.append("Phase ").append(phase).append(" frames=").append(values.size()).append(" meanMs=").append(values.stream().mapToDouble(x->x).average().orElse(0))
                .append(" p95Ms=").append(values.get((int)(values.size()*.95))).append('\n');
        }
        return s.toString();
    }
}
