package de.theoptik.badapple;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Launcher {
    public static void main(String[] args) {
        try (var socket = new Socket("localhost", 1337); var writer = new PrintWriter((socket.getOutputStream()), false); var reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); var frameGrabber = new FFmpegFrameGrabber("Touhou_Bad_Apple.mp4")) {
            frameGrabber.start();
            var image = frameGrabber.grabImage();
            while (image != null) {
                var buffer = (ByteBuffer) image.image[0];
                var offsetPerLine = (image.imageStride/3) - image.imageWidth;
                for (int y = 0; y < image.imageHeight ; y++) {
                    for (int x = 0; x < image.imageWidth; x++) {
                        var index = (x + y*image.imageWidth + y*offsetPerLine) * 3;
                        writer.println("PX " + x  + " " + y + " " + String.format("%06X", (buffer.get(index) << 16) + (buffer.get(index + 1) << 8) + buffer.get(index + 2)));
                        if (reader.ready()) {
                            System.out.println(reader.readLine());
                        }
                    }
                }
                writer.flush();
                //the assignment is technically not necessary, because the FrameGrabber will overwrite the same memory address, but this way we get null as a result when the stream is over
                image = frameGrabber.grabImage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
