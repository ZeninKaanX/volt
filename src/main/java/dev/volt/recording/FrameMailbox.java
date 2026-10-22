package dev.volt.recording;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/** Three reusable buffers: render thread never waits for the encoder. */
public final class FrameMailbox {
    private final ArrayBlockingQueue<byte[]> pool = new ArrayBlockingQueue<>(3);
    private final AtomicReference<byte[]> latest = new AtomicReference<>();
    public FrameMailbox(int bytes) { for (int i = 0; i < 3; i++) pool.add(new byte[bytes]); }
    public byte[] acquire() { return pool.poll(); }
    public void offer(byte[] frame) { release(latest.getAndSet(frame)); }
    public byte[] take() { return latest.getAndSet(null); }
    public void release(byte[] frame) { if (frame != null) pool.offer(frame); }
}
