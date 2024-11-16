package de.theoptik.badapple;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

public class Launcher {

    private static final String[] hexTable = IntStream.iterate(0, i -> i+1).mapToObj(i -> String.format("%02X",i)).limit(256).toArray(String[]::new);

    public static void main(String[] args) {
        try (var socket = new Socket("localhost", 1337); var writer = new PrintWriter(socket.getOutputStream(), false); var frameGrabber = new FFmpegFrameGrabber("Touhou_Bad_Apple.mp4")) {
            frameGrabber.start();
            var image = frameGrabber.grabImage();
            var timesPerFrame = new long[8];
            var frameCounter = 0;
            var width = image.imageWidth;
            var height = image.imageHeight;
            var offsetPerLine = (image.imageStride/3) - width;
            while (image != null) {
                var startTime = System.currentTimeMillis();
                var buffer = (ByteBuffer) image.image[0];
                for (int y = 0; y < height ; y++) {
                    var yOffset = y*width + y*offsetPerLine;
                    for (int x = 0; x < width; x++) {
                        var index = (x + yOffset) * 3;
                        writer.println("PX " + x  + " " + y + " " + formatPixelValue(buffer.get(index),  buffer.get(index + 1),  buffer.get(index + 2)));
                    }
                }
                writer.flush();
                //the assignment is technically not necessary, because the FrameGrabber will overwrite the same memory address, but this way we get null as a result when the stream is over
                image = frameGrabber.grabImage();
                timesPerFrame[frameCounter % timesPerFrame.length] = System.currentTimeMillis() - startTime;
                printFps(timesPerFrame);
                frameCounter++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String formatPixelValue(byte r, byte g, byte b) {

        return hexTable[r& 0xFF] + hexTable[g& 0xFF] + hexTable[b& 0xFF];

    }

    private static void printFps(long[] timesPerFrame) {
        System.out.println(1000/Arrays.stream(timesPerFrame).filter(t -> t > 0).average().orElse(0.0) + " fps");
    }
}
