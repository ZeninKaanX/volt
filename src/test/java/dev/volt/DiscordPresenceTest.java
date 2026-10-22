package dev.volt;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

class DiscordPresenceTest {
    @TempDir Path root;
    record Packet(int op, String body) { }
    static Packet read(SocketChannel channel) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN); fill(channel, header); header.flip();
        int opcode = header.getInt(); ByteBuffer payload = ByteBuffer.allocate(header.getInt()); fill(channel, payload);
        return new Packet(opcode, new String(payload.array(), StandardCharsets.UTF_8));
    }
    static void fill(SocketChannel channel, ByteBuffer buffer) throws Exception { while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new java.io.EOFException(); }
    static void send(SocketChannel channel, int opcode, String body) throws Exception {
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + text.length).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(text.length).put(text); buffer.flip();
        while (buffer.hasRemaining()) channel.write(buffer);
    }
    private static VoltConfig newConfig(){VoltConfig c=new VoltConfig();c.discordApplicationId="123456789012345678";return c;}
    @Test void handshakesPublishesAndRespondsToDiscordPingWithoutAccountTokens() throws Exception {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        Path socket = root.resolve("ipc");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            CompletableFuture<Void> checked = new CompletableFuture<>();
            Thread.ofVirtual().start(() -> {
                try (SocketChannel channel = server.accept()) {
                    Packet hello = read(channel); assertEquals(0, hello.op());
                    assertEquals(DiscordApplication.id(newConfig()), JsonParser.parseString(hello.body()).getAsJsonObject().get("client_id").getAsString());
                    send(channel, 1, "{\"evt\":\"READY\",\"cmd\":\"DISPATCH\"}");
                    Packet activity = read(channel); assertEquals(1, activity.op());
                    JsonObject json = JsonParser.parseString(activity.body()).getAsJsonObject();
                    assertEquals("SET_ACTIVITY", json.get("cmd").getAsString());
                    assertEquals("Clipped with Volt", json.getAsJsonObject("args").getAsJsonObject("activity").get("details").getAsString());
                    JsonObject ack=new JsonObject();ack.add("nonce",json.get("nonce"));ack.addProperty("cmd","SET_ACTIVITY");ack.add("evt",JsonNull.INSTANCE);ack.add("data",new JsonObject());send(channel,1,ack.toString());
                    assertEquals(2,json.getAsJsonObject("args").getAsJsonObject("activity").get("status_display_type").getAsInt());
                    send(channel, 3, "ping-test"); Packet pong = read(channel); assertEquals(4, pong.op()); assertEquals("ping-test", pong.body());
                    checked.complete(null);
                } catch (Throwable e) { checked.completeExceptionally(e); }
            });
            VoltConfig config = new VoltConfig(); config.discordApplicationId = "123456789012345678";
            try (DiscordPresence presence = new DiscordPresence(config, () -> "Clipped with Volt", socket)) { checked.get(8, TimeUnit.SECONDS); }
        }
    }
}
