package info.kgeorgiy.ja.lyzhenkov.hello;

import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * This class represents the server, implements {@link NewHelloServer} the interface and operates in Nonblocking mode.
 */
public class HelloUDPNonblockingServer implements NewHelloServer {
    private int BUFFER_SIZE = 0;
    private Selector selector;
    private ExecutorService listener;
    private ExecutorService workers;
    private Queue<Packet> requests;
    private Queue<Packet> responses;

    private record Context(String format) {
    }

    private static final class Packet {
        private final ByteBuffer buffer;
        private SocketAddress address;

        private Packet(final ByteBuffer buffer, final SocketAddress address) {
            this.buffer = buffer;
            this.address = address;
        }
    }

    @Override
    public void start(int threads, Map<Integer, String> ports) {
        try {
            selector = Selector.open();
            for (var pair : ports.entrySet()) {
                try {
                    var server = DatagramChannel.open();
                    server.configureBlocking(false);
                    server.socket().bind(new InetSocketAddress("localhost", pair.getKey()));
                    server.register(selector, SelectionKey.OP_READ).attach(new Context(pair.getValue()));
                    if (BUFFER_SIZE == 0) {
                        BUFFER_SIZE = server.getOption(StandardSocketOptions.SO_RCVBUF);
                    }
                } catch (final SocketException e) {
                    System.err.println("Server couldn't start: " + e.getMessage());
                    return;
                }
            }
        } catch (final IOException e) {
            System.err.println("Server couldn't start: " + e.getMessage());
            return;
        }
        this.requests = new ConcurrentLinkedQueue<>();
        this.responses = new ConcurrentLinkedQueue<>();
        IntStream.range(0, threads).forEach(i -> requests.add(new Packet(ByteBuffer.allocate(BUFFER_SIZE), null)));
        this.listener = Executors.newSingleThreadExecutor();
        this.workers = Executors.newFixedThreadPool(threads);
        listener.submit(controller());
    }

    private Runnable controller() {
        return () -> {
            while (!Thread.currentThread().isInterrupted() && !selector.keys().isEmpty()) {
                try {
                    if (selector.select() == 0) continue;
                    for (var i = selector.selectedKeys().iterator(); i.hasNext(); ) {
                        var key = i.next();
                        try {
                            if (key.isWritable()) {
                                send(key);
                            }
                            if (key.isReadable()) {
                                receive(key);
                            }
                        } finally {
                            i.remove();
                        }
                    }
                } catch (final IOException e) {
                    System.err.println("Selector I/O error: " + e.getMessage());
                }
            }
        };
    }

    private void receive(final SelectionKey key) {
        var attachment = (Context) key.attachment();
        var serverChannel = (DatagramChannel) key.channel();
        if (requests.isEmpty()) {
            key.interestOpsAnd(~SelectionKey.OP_READ);
            return;
        }
        var packet = requests.poll();
        try {
            packet.address = serverChannel.receive(packet.buffer);
        } catch (final IOException e) {
            packet.buffer.clear();
            requests.add(packet);
            System.err.println("Failed receive message from client: " + e.getMessage());
            return;
        }
        packet.buffer.flip();
        workers.submit(() -> {
            var inMessage = UtilsUDP.getData(packet.buffer);
            var outMessage = attachment.format().replaceAll("\\$", inMessage);
            responses.add(
                    new Packet(UtilsUDP.getBuffer(outMessage), packet.address)
            );
            requests.add(packet);
            key.interestOpsOr(SelectionKey.OP_WRITE);
            selector.wakeup();
        });
    }

    private void send(final SelectionKey key) {
        var serverChannel = (DatagramChannel) key.channel();
        if (responses.isEmpty()) {
            key.interestOpsAnd(~SelectionKey.OP_WRITE);
            return;
        }
        var packet = responses.poll();
        if (!UtilsUDP.sendMessage(serverChannel, packet.buffer, packet.address)) {
            System.err.println("Failed send message to client");
        }
        key.interestOpsOr(SelectionKey.OP_READ);
    }

    @Override
    public void close() {
        var errorMessage = "HelloUDPNonblockingServer couldn't close correctly";
        requests = null;
        responses = null;
        var correctlyClose = UtilsUDP.closeSelector(selector);
        correctlyClose = correctlyClose && UtilsUDP.closeService(listener);
        correctlyClose = correctlyClose && UtilsUDP.closeService(workers);
        if (!correctlyClose) {
            System.err.println(errorMessage);
        }
    }

    /**
     * Entry point to the program that starts the server with the passed parameters.
     *
     * @param args number of ports and threads.
     * @see UtilsUDP#runServer(Supplier, String[])
     */
    public static void main(String[] args) {
        UtilsUDP.runServer(HelloUDPNonblockingServer::new, args);
    }
}
