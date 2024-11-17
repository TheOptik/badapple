package de.theoptik.badapple;


import org.openjdk.jmh.annotations.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class Benchmarks {

    public static void main(String[] args) throws IOException {
        org.openjdk.jmh.Main.main(args);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public void init() throws Exception{
        try(var socket = new Socket("localhost", 1337); var outputStream = new BufferedOutputStream(socket.getOutputStream())) {
            Launcher.streamMp4(outputStream, "Touhou_Bad_Apple.mp4");
        }
    }

}
