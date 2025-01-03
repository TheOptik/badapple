package de.theoptik.badapple;


import org.openjdk.jmh.annotations.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.stream.Stream;

import static de.theoptik.badapple.Launcher.createSocket;

public class Benchmarks {

    public static void main(String[] args) throws IOException {
        org.openjdk.jmh.Main.main(args);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void init() throws Exception {
        var sockets = Stream.generate(() -> {
            try {
                return createSocket();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).limit(4).toArray(SocketChannel[]::new);
        Launcher.streamMp4(sockets, "Touhou_Bad_Apple.mp4");
        for (var socket:sockets){
            try{
                socket.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

}
