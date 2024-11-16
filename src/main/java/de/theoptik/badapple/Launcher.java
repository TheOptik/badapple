package de.theoptik.badapple;

import org.bytedeco.javacv.FFmpegFrameGrabber;

public class Launcher {
    public static void main(String[] args) {
        try (var frameGrabber = new FFmpegFrameGrabber("Touhou_Bad_Apple.mp4")) {
            frameGrabber.start();
            var image = frameGrabber.grabImage();
            while (image != null) {
                //the assignment is technically not necessary, because the FrameGrabber will overwrite the same memory address, but this way we get null as a result when the stream is over
                image = frameGrabber.grabImage();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
