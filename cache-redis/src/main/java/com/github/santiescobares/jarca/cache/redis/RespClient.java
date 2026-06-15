package com.github.santiescobares.jarca.cache.redis;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal synchronised RESP (Redis Serialisation Protocol) client.
 * Implements only the commands needed by {@link RedisArcaCache}: GET, SET EX, DEL.
 * Zero external dependencies — pure JDK sockets.
 */
final class RespClient implements Closeable {

    private final String host;
    private final int port;
    private final Duration connectTimeout;

    private Socket socket;
    private BufferedReader reader;
    private OutputStream writer;

    RespClient(String host, int port, Duration connectTimeout) {
        this.host = host;
        this.port = port;
        this.connectTimeout = connectTimeout;
    }

    void connect() throws IOException {
        socket = new Socket();
        socket.connect(new java.net.InetSocketAddress(host, port),
                (int) connectTimeout.toMillis());
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = socket.getOutputStream();
    }

    /** GET key → value or null if absent. */
    String get(String key) throws IOException {
        send("GET", key);
        return readBulkString();
    }

    /** SET key value EX seconds */
    void setEx(String key, String value, long ttlSeconds) throws IOException {
        send("SET", key, value, "EX", String.valueOf(ttlSeconds));
        readSimpleString(); // "+OK"
    }

    /** DEL key */
    void del(String key) throws IOException {
        send("DEL", key);
        readInteger();
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) socket.close();
    }

    // ── RESP wire format ─────────────────────────────────────────────────────

    private void send(String... args) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append('*').append(args.length).append("\r\n");
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(bytes.length).append("\r\n").append(arg).append("\r\n");
        }
        writer.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        writer.flush();
    }

    private String readBulkString() throws IOException {
        String line = reader.readLine();
        if (line.startsWith("$-1")) return null;   // null bulk string
        if (!line.startsWith("$")) throw new IOException("Expected bulk string, got: " + line);
        int len = Integer.parseInt(line.substring(1));
        char[] buf = new char[len];
        int read = 0;
        while (read < len) {
            int n = reader.read(buf, read, len - read);
            if (n < 0) throw new IOException("Connection closed reading bulk string");
            read += n;
        }
        reader.readLine(); // consume \r\n
        return new String(buf);
    }

    private String readSimpleString() throws IOException {
        return reader.readLine();
    }

    private long readInteger() throws IOException {
        String line = reader.readLine();
        if (!line.startsWith(":")) throw new IOException("Expected integer, got: " + line);
        return Long.parseLong(line.substring(1));
    }
}
