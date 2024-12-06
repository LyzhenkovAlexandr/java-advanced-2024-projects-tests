package info.kgeorgiy.ja.lyzhenkov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;
import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Utility class for working with UDP protocol.
 */
public final class UtilsUDP {
    /**
     * Time constant to wait.
     */
    public static final int TIMEOUT_CLIENT = 200;
    /**
     * Encoding used.
     */
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    /**
     * Takes a message from a buffer.
     *
     * @param buffer buffer with message.
     * @return message.
     */
    public static String getData(ByteBuffer buffer) {
        var message = CHARSET.decode(buffer).toString();
        buffer.clear();
        return message;
    }

    /**
     * Takes a message from a packet.
     *
     * @param packet packet with message.
     * @return message.
     */
    public static String getData(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), CHARSET);
    }

    /**
     * Places a message in a buffer with encoding UTF-8.
     *
     * @param message message for buffer.
     * @return buffer with message.
     */
    public static ByteBuffer getBuffer(String message) {
        return ByteBuffer.wrap(message.getBytes(CHARSET));
    }

    /**
     * Places a message in a buffer with encoding UTF-8.
     *
     * @param buffer  where to put the buffer.
     * @param message message for buffer.
     */
    public static void putInBuffer(ByteBuffer buffer, String message) {
        buffer.put(message.getBytes(CHARSET)).flip();
    }

    /**
     * Send Message.
     *
     * @param channel channel for sending a message.
     * @param buffer  message for sending.
     * @param address address of the recipient.
     * @return will return {@code true} if successful and {@code false} otherwise.
     */
    public static boolean sendMessage(DatagramChannel channel, ByteBuffer buffer, SocketAddress address) {
        if (Objects.isNull(channel) || Objects.isNull(buffer) || Objects.isNull(address)) return false;
        try {
            channel.send(buffer, address);
        } catch (final IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Close selector. In doing so, it will first close all linked channels.
     *
     * @param selector set of channels that needs to be closed.
     * @return will return {@code true} if successful and {@code false} otherwise.
     */
    public static boolean closeSelector(Selector selector) {
        if (Objects.isNull(selector)) return true;
        var correctlyClose = closeChannels(selector.keys());
        try {
            selector.close();
        } catch (final IOException e) {
            correctlyClose = false;
        }
        return correctlyClose;
    }

    /**
     * Close channels.
     *
     * @param keySet set of channels that needs to be closed.
     * @return will return {@code true} if successful and {@code false} otherwise.
     */
    public static boolean closeChannels(final Set<SelectionKey> keySet) {
        if (Objects.isNull(keySet) || keySet.isEmpty()) return true;
        var correctlyClose = true;
        for (var key : keySet) {
            try {
                key.channel().close();
            } catch (final IOException exc) {
                correctlyClose = false;
            }
        }
        return correctlyClose;
    }

    /**
     * Close service.
     *
     * @param service service that needs to be closed.
     * @return will return {@code true} if successful and {@code false} otherwise.
     */
    public static boolean closeService(final ExecutorService service) {
        if (Objects.isNull(service)) return true;
        service.shutdownNow();
        try {
            if (!service.awaitTermination(1L, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }

    /**
     * Start client.
     *
     * @param clientSupplier launches the produced client.
     * @param args           ip, port, prefix, threads, requests.
     */
    public static void runClient(final Supplier<HelloClient> clientSupplier, final String[] args) {
        var invalidInput = "Format input must be: <ip> <port> <prefix> <threads> <requests>";
        if (args.length != 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println(invalidInput);
            return;
        }
        final int port;
        final int threads;
        final int requests;
        try {
            port = Integer.parseInt(args[1]);
            threads = Integer.parseInt(args[3]);
            requests = Integer.parseInt(args[4]);
        } catch (final NumberFormatException e) {
            System.err.println(invalidInput);
            return;
        }
        clientSupplier.get().run(args[0], port, args[2], threads, requests);
        System.out.println("Client running");
    }

    /**
     * Start server.
     *
     * @param serverSupplier launches the produced server.
     * @param args           number of ports and threads.
     */
    public static void runServer(final Supplier<NewHelloServer> serverSupplier, final String[] args) {
        var invalidInput = "Format input must be: <port> <threads>";
        if (args.length != 2) {
            System.err.println(invalidInput);
            return;
        }
        final int port;
        final int threads;
        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (final NumberFormatException e) {
            System.err.println(invalidInput);
            return;
        }
        try (final HelloServer server = serverSupplier.get()) {
            server.start(port, threads);
        }
        System.out.println("Server running");
    }


    private UtilsUDP() {
    }
}
