package dev.volt.recording;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.ArrayList;
import java.util.Optional;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** RenderPearl capture shared by Vulkan and OpenGL, with bounded asynchronous readback. */
public final class GpuCapture implements AutoCloseable {
    private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0, 0, 0, 1));
    private static final class Slot {
        final GpuBuffer buffer;
        final Runnable completed = () -> ready = true;
        volatile boolean ready;
        boolean busy;
        long serial;
        Slot(int bytes) {
            buffer = RenderSystem.getDevice().createBuffer(() -> "Volt asynchronous readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bytes);
        }
    }
    private static final class Batch {
        final int width, height;
        final TextureTarget target;
        final Slot[] slots = new Slot[3];
        Batch(int width, int height) {
            this.width = width; this.height = height;
            target = new TextureTarget("Volt GPU downscale", width, height, GpuFormat.RGBA8_UNORM, null);
            try { for (int i = 0; i < slots.length; i++) slots[i] = new Slot(Math.multiplyExact(Math.multiplyExact(width, height), 4)); }
            catch (RuntimeException failure) { release(); throw failure; }
        }
        boolean complete() {
            for (Slot slot : slots) if (slot != null && slot.busy && !slot.ready) return false;
            return true;
        }
        void release() {
            for (Slot slot : slots) if (slot != null) slot.buffer.close();
            target.destroyBuffers();
        }
    }
    private final ArrayList<Batch> retired = new ArrayList<>(2);
    private Batch current;
    private long nextFrame, sequence;
    public long missed, captured, discarded;
    public double lastCaptureMillis, averageCaptureMillis;
    public boolean persistentReadback;

    public void capture(ReplayRecorder recorder, int sourceWidth, int sourceHeight) {
        cleanupRetired();
        if (recorder.state != ReplayRecorder.State.BUFFERING || recorder.frames == null) { close(); return; }
        long now = System.nanoTime();
        if (now < nextFrame || sourceWidth <= 0 || sourceHeight <= 0) return;
        nextFrame = now + 1_000_000_000L / recorder.fps();
        try {
            if (current == null || current.width != recorder.width || current.height != recorder.height) {
                retireCurrent();
                cleanupRetired();
                // Resizing never grows an unbounded queue of GPU-owned buffers.
                if (retired.size() >= 2) { missed++; return; }
                current = new Batch(recorder.width, recorder.height);
            }
            Slot newest = null;
            for (Slot slot : current.slots)
                if (slot.busy && slot.ready && (newest == null || slot.serial > newest.serial)) newest = slot;
            for (Slot slot : current.slots) if (slot != newest && slot.busy && slot.ready) {
                slot.busy = false; slot.ready = false; discarded++;
            }
            if (newest != null) {
                byte[] bytes = recorder.frames.acquire();
                if (bytes == null) missed++;
                else {
                    boolean offered = false;
                    try (var mapped = newest.buffer.map(true, false)) {
                        mapped.data().get(0, bytes, 0, bytes.length);
                        recorder.frames.offer(bytes); offered = true; captured++;
                    } finally { if (!offered) recorder.frames.release(bytes); }
                }
                newest.busy = false; newest.ready = false;
            }
            Slot free = null;
            for (Slot slot : current.slots) if (!slot.busy) { free = slot; break; }
            if (free == null) { missed++; return; }
            var source = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
            if (source == null) { missed++; return; }
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (var pass = encoder.createRenderPass(() -> "Volt GPU downscale", current.target.getColorTextureView(), CLEAR)) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.ENTITY_OUTLINE_BLIT));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(3, 1, 0, 0);
            }
            free.ready = false; free.busy = true; free.serial = ++sequence;
            encoder.copyTextureToBuffer(current.target.getColorTexture(), free.buffer, 0L, free.completed, 0);
        } finally {
            lastCaptureMillis = (System.nanoTime() - now) / 1e6;
            averageCaptureMillis = averageCaptureMillis == 0 ? lastCaptureMillis : averageCaptureMillis * .95 + lastCaptureMillis * .05;
        }
    }
    private void retireCurrent() {
        if (current != null) { retired.add(current); current = null; }
    }
    private void cleanupRetired() {
        for (int i = retired.size() - 1; i >= 0; i--) if (retired.get(i).complete()) {
            retired.remove(i).release();
        }
    }
    @Override public void close() {
        retireCurrent();
        cleanupRetired();
    }
}
