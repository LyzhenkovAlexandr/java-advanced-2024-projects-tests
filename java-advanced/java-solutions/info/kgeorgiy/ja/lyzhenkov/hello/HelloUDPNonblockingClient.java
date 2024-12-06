package info.kgeorgiy.ja.lyzhenkov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.function.Supplier;

/**
 * This class represents the client, implements the interface {@link HelloClient} and operates in Nonblocking mode.
 */
public class HelloUDPNonblockingClient implements HelloClient {
    private int BUFFER_SIZE;
    private ByteBuffer buffer;

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (final UnknownHostException e) {
            System.err.println("Host error: " + e.getMessage());
            return;
        }

        var socketAddress = new InetSocketAddress(address, port);
        try (var selector = Selector.open()) {
            try {
                for (int numberThread = 1; numberThread <= threads; numberThread++) {

                    var channel = DatagramChannel.open();
                    channel.configureBlocking(false);
                    channel.connect(socketAddress);
                    channel.register(selector, SelectionKey.OP_WRITE)
                            .attach(new Context(prefix, numberThread, requests));
                    if (BUFFER_SIZE == 0) {
                        BUFFER_SIZE = channel.getOption(StandardSocketOptions.SO_RCVBUF);
                    }
                }
            } catch (final IOException e) {
                System.err.println("Failed to create DatagramChannel: " + e.getMessage());
                if (UtilsUDP.closeSelector(selector)) {
                    System.err.println("Failed to close correctly Selector");
                }
                return;
            }
            buffer = ByteBuffer.allocate(BUFFER_SIZE);
            controller(selector);
            UtilsUDP.closeChannels(selector.keys());
        } catch (final IOException e) {
            System.err.println("Failed to create Selector: " + e.getMessage());
        } finally {
            buffer = null;
        }
    }

    private void controller(final Selector selector) {
        while (!Thread.currentThread().isInterrupted() && !selector.keys().isEmpty()) {
            try {
                selector.select(UtilsUDP.TIMEOUT_CLIENT);
            } catch (final IOException e) {
                System.err.println("Selector I/O error: " + e.getMessage());
            }
            if (selector.selectedKeys().isEmpty()) {
                selector.keys().forEach(key -> key.interestOps(SelectionKey.OP_WRITE));
            }
            for (var keyIterator = selector.selectedKeys().iterator(); keyIterator.hasNext(); ) {
                var key = keyIterator.next();
                try {
                    if (key.isWritable()) {
                        send(key);
                    }
                    if (key.isReadable()) {
                        receive(key);
                    }
                } finally {
                    keyIterator.remove();
                }
            }
        }
    }

    private void receive(final SelectionKey key) {
        var channel = (DatagramChannel) key.channel();
        var attachment = (Context) key.attachment();
        buffer.clear();
        key.interestOps(SelectionKey.OP_WRITE);
        try {
            channel.receive(buffer);
        } catch (final IOException e) {
            System.err.println("Failed receive message from server: " + e.getMessage());
            return;
        }
        buffer.flip();
        var response = UtilsUDP.getData(buffer);
        System.out.println("Response: " + response);
        if (response.contains(attachment.getRequest())) {
            attachment.inc();
            if (attachment.isEnd()) {
                try {
                    channel.close();
                } catch (final IOException e) {
                    System.err.println("Failed to close correctly DatagramChannel: " + e.getMessage());
                }
            }
        }
    }

    private void send(final SelectionKey key) {
        var channel = (DatagramChannel) key.channel();
        var attachment = (Context) key.attachment();
        buffer.clear();
        UtilsUDP.putInBuffer(buffer, attachment.getRequest());
        try {
            channel.send(buffer, channel.getRemoteAddress());
        } catch (final IOException e) {
            System.err.println("Failed send message to server: " + e.getMessage());
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    private static final class Context {
        private final String prefix;
        private final int numberThread;
        private final int maxNumberTask;
        private int numberTask = 1;

        private Context(final String prefix, final int numberThread, final int maxNumberTask) {
            this.prefix = prefix;
            this.numberThread = numberThread;
            this.maxNumberTask = maxNumberTask;
        }

        private void inc() {
            numberTask++;
        }

        private String getRequest() {
            return prefix + numberThread + "_" + numberTask;
        }

        private boolean isEnd() {
            return numberTask > maxNumberTask;
        }
    }

    /**
     * The entry point to the program that starts the client with the passed parameters.
     *
     * @param args ip, port, prefix, threads, requests.
     * @see UtilsUDP#runClient(Supplier, String[])
     */
    public static void main(String[] args) {
        UtilsUDP.runClient(HelloUDPNonblockingClient::new, args);
    }
}
