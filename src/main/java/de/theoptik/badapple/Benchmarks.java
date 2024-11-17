package de.theoptik.badapple;


import org.openjdk.jmh.annotations.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

public class Benchmarks {

    public static void main(String[] args) throws IOException {
        org.openjdk.jmh.Main.main(args);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void init() throws Exception{
        try (var socket = SocketChannel.open()) {
            socket.configureBlocking(true);
            socket.connect(new InetSocketAddress("localhost", 1337));
            Launcher.streamMp4(socket, "Touhou_Bad_Apple.mp4");
        }
    }

}
