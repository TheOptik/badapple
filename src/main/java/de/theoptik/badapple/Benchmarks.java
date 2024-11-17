package de.theoptik.badapple;


import org.openjdk.jmh.annotations.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class Benchmarks {

    public static void main(String[] args) throws IOException {
        org.openjdk.jmh.Main.main(args);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void init() throws Exception {
        try (var socket = SocketChannel.open(); var selector = Selector.open()) {
            socket.configureBlocking(false);

            socket.register(selector, SelectionKey.OP_CONNECT);
            socket.connect(new InetSocketAddress("localhost", 1337));
            while (socket.isOpen()) {
                selector.select();
                for (var key : selector.keys()) {
                    if (key.isConnectable()) {
                        socket.finishConnect();
                        socket.register(socket.keyFor(selector).selector(), SelectionKey.OP_WRITE);
                        selector.selectedKeys().clear();

                    } else if (selector.keys().stream().anyMatch(SelectionKey::isWritable)) {
                        Launcher.streamMp4(socket, "Touhou_Bad_Apple.mp4");
                        socket.close();
                    }
                }
            }
        }
    }

}
