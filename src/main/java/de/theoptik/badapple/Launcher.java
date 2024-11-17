package de.theoptik.badapple;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.IntStream;

public class Launcher {

    private static final String[] hexTable = IntStream.iterate(0, i -> i + 1).mapToObj(i -> String.format("%02X", i)).limit(256).toArray(String[]::new);

    public static void main(String[] args) {

        try (var socket = new Socket("localhost", 1337); var writer = new PrintWriter(socket.getOutputStream(), false)) {
            while (true) {
                try (var frameGrabber = new FFmpegFrameGrabber("Touhou_Bad_Apple.mp4")) {
                    frameGrabber.start();
                    var image = frameGrabber.grabImage();
                    var timesPerFrame = new long[100];
                    var frameCounter = 0;
                    var width = image.imageWidth;
                    var height = image.imageHeight;
                    var offsetPerLine = (image.imageStride / 3) - width;
                    var previousFrame = new byte[width * height * 3];
                    while (image != null) {
                        var startTime = System.currentTimeMillis();
                        var buffer = (ByteBuffer) image.image[0];
                        if (frameCounter == 0) {
                            sendChangedPixels(height, width, offsetPerLine, writer, buffer, (x, y, r, g, b) -> true);
                        } else {
                            sendChangedPixels(height, width, offsetPerLine, writer, buffer, (x, y, r, g, b) -> {
                                var index = (x + y * width) * 3;
                                var result = previousFrame[index] != r || previousFrame[index + 1] != g || previousFrame[index + 2] != b;

                                previousFrame[index] = r;
                                previousFrame[index + 1] = g;
                                previousFrame[index + 2] = b;

                                return result;
                            });
                        }
                        writer.flush();
                        //the assignment is technically not necessary, because the FrameGrabber will overwrite the same memory address, but this way we get null as a result when the stream is over
                        image = frameGrabber.grabImage();
                        timesPerFrame[frameCounter % timesPerFrame.length] = System.currentTimeMillis() - startTime;
                        printFps(timesPerFrame);
                        frameCounter++;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendChangedPixels(int height, int width, int offsetPerLine, PrintWriter writer, ByteBuffer buffer, HasValueChanged changeDetector) {
        for (int y = 0; y < height; y++) {
            var yOffset = y * width + y * offsetPerLine;
            for (int x = 0; x < width; x++) {
                var index = (x + yOffset) * 3;
                byte r = buffer.get(index);
                byte g = buffer.get(index + 1);
                byte b = buffer.get(index + 2);
                if (changeDetector.hasChanged(x, y, r, g, b)) {
                    writer.println("PX " + x + " " + y + " " + formatPixelValue(r, g, b));
                }
            }
        }
    }

    private static String formatPixelValue(byte r, byte g, byte b) {
        return hexTable[r & 0xFF] + hexTable[g & 0xFF] + hexTable[b & 0xFF];
    }

    private static void printFps(long[] timesPerFrame) {
        System.out.println(1000 / Arrays.stream(timesPerFrame).filter(t -> t > 0).average().orElse(0.0) + " fps");
    }

    @FunctionalInterface
    private interface HasValueChanged {
        boolean hasChanged(int x, int y, byte r, byte g, byte b);
    }

}
