package info.kgeorgiy.ja.lyzhenkov.hello;

import info.kgeorgiy.java.advanced.hello.NewHelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * This class represents the server, implements {@link NewHelloServer} the interface and operates in blocking mode.
 */
public class HelloUDPServer implements NewHelloServer {

    private int BUFFER_SIZE = -1;
    private List<DatagramSocket> sockets;
    private ExecutorService listeners;
    private ExecutorService workers;

    @Override
    public void start(int threads, Map<Integer, String> ports) {
        workers = Executors.newFixedThreadPool(threads);
        listeners = Executors.newFixedThreadPool(Math.max(1, ports.size()));
        sockets = new ArrayList<>(ports.size());
        for (var pair : ports.entrySet()) {
            try {
                var socket1 = new DatagramSocket(pair.getKey());
                sockets.add(socket1);
                if (BUFFER_SIZE == -1) {
                    BUFFER_SIZE = socket1.getReceiveBufferSize();
                }
                listeners.submit(handleRequest(socket1, pair.getValue()));
            } catch (final SocketException e) {
                System.err.println("Server couldn't start: " + e.getMessage());
                return;
            }
        }
    }

    private Runnable handleRequest(final DatagramSocket socket, final String format) {
        return () -> {
            try {
                while (!socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                    var inPacket = new DatagramPacket(new byte[BUFFER_SIZE], BUFFER_SIZE);
                    socket.receive(inPacket);
                    workers.submit(sendResponse(socket, inPacket, format));
                }
            } catch (final SocketTimeoutException e) {
                System.err.println("Timeout error: " + e.getMessage());
            } catch (final IOException e) {
                System.err.println("I/O error while receiving a message from the server: " + e.getMessage());
            }
        };
    }

    private Runnable sendResponse(final DatagramSocket socket, final DatagramPacket inPacket, final String format) {
        return () -> {
            try {
                var inMessage = UtilsUDP.getData(inPacket);
                var outMessage = format.replaceAll("\\$", inMessage);
                inPacket.setData(outMessage.getBytes());
                socket.send(inPacket);
            } catch (final IOException e) {
                System.err.println("I/O error while sending a message from the server: " + e.getMessage());
            }
        };
    }

    @Override
    public void close() {
        var errorMessage = "HelloUDPServer couldn't close correctly";
        sockets.forEach(DatagramSocket::close);
        var correctlyClose = UtilsUDP.closeService(listeners);
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
        UtilsUDP.runServer(HelloUDPServer::new, args);
    }
}
