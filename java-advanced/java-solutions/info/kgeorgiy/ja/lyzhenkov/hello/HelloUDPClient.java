package info.kgeorgiy.ja.lyzhenkov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * This class represents the client, implements the interface {@link HelloClient} and operates in blocking mode.
 */
public class HelloUDPClient implements HelloClient {

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
        try (var workers = Executors.newFixedThreadPool(threads)) {
            var waiter = new Phaser(threads + 1);
            IntStream.rangeClosed(1, threads).forEach(numberThread ->
                    workers.submit(sendRequest(socketAddress, prefix, requests, numberThread, waiter)));
            workers.shutdown();
            waiter.arriveAndAwaitAdvance();
        }
    }

    private static Runnable sendRequest(
            final SocketAddress address,
            final String prefix,
            final int requests,
            final int numberThread,
            final Phaser waiter
    ) {
        return () -> {
            try (var socket = new DatagramSocket()) {
                socket.setSoTimeout(UtilsUDP.TIMEOUT_CLIENT);
                var bufferSize = socket.getReceiveBufferSize();
                var packet = new DatagramPacket(new byte[bufferSize], bufferSize, address);
                for (int numberTask = 1; numberTask <= requests; numberTask++) {

                    var initMessage = prefix + numberThread + "_" + numberTask;
                    while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                        packet.setData(initMessage.getBytes());
                        try {
                            socket.send(packet);
                            packet.setData(new byte[bufferSize]);
                            socket.receive(packet);
                        } catch (final SocketTimeoutException e) {
                            System.err.println("Timeout error: " + e.getMessage());
                            continue;
                        } catch (final IOException e) {
                            System.err.println("I/O error client: " + e.getMessage());
                            continue;
                        }
                        var inMessage = UtilsUDP.getData(packet);
                        if (inMessage.contains(initMessage)) {
                            System.out.println("Response: " + inMessage);
                            break;
                        }
                    }
                }
            } catch (final SocketException e) {
                System.err.println("Client couldn't start: " + e.getMessage());
            } finally {
                waiter.arriveAndDeregister();
            }
        };
    }

    /**
     * The entry point to the program that starts the client with the passed parameters.
     *
     * @param args ip, port, prefix, threads, requests.
     * @see UtilsUDP#runClient(Supplier, String[])
     */
    public static void main(String[] args) {
        UtilsUDP.runClient(HelloUDPClient::new, args);
    }
}
