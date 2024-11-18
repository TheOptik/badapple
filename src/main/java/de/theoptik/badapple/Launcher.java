package de.theoptik.badapple;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.stream.IntStream;

public class Launcher {

    private static final byte[][] hexTable = IntStream.iterate(0, i -> i + 1).mapToObj(i -> String.format("%02X", i).getBytes()).limit(256).toArray(byte[][]::new);
    private static final byte[][] coordinateTable = IntStream.iterate(0, i -> i + 1).mapToObj(i -> String.format("%03d", i).getBytes()).limit(1000).toArray(byte[][]::new);

    //template for messages, assuming coordinates are within x<=999 and y<=999
    private static final byte[] message = "PX 000 000 000000\n".getBytes();
    private static final int xCoordinateOffset = 3;
    private static final int yCoordinateOffset = 7;
    private static final int colorOffset = 11;

    public static void main(String[] args) throws Exception {

        try (var socket = SocketChannel.open(); var selector = Selector.open()) {
            socket.configureBlocking(false);

            socket.register(selector, SelectionKey.OP_CONNECT);
            socket.connect(new InetSocketAddress("localhost", 1337));
            while (true) {
                selector.select();
                for (var key : selector.keys()) {
                    if (key.isConnectable()) {
                        socket.finishConnect();
                        socket.register(socket.keyFor(selector).selector(), SelectionKey.OP_WRITE);
                        selector.selectedKeys().clear();

                    } else if (selector.keys().stream().anyMatch(SelectionKey::isWritable)) {
                        streamMp4(socket, "Touhou_Bad_Apple.mp4");
                    }
                }
            }
        }
    }

    public static void streamMp4(SocketChannel socket, String filePath) throws Exception {
        try (var frameGrabber = new FFmpegFrameGrabber(filePath)) {
            frameGrabber.start();
            var width = frameGrabber.getImageWidth();
            var height = frameGrabber.getImageHeight();
            var previousFrame = new byte[width * height * 3];
            var writeBuffers = new ByteBuffer[]{ByteBuffer.allocate(width * height * message.length), ByteBuffer.allocate(width * height * message.length)};
            var bufferLocks = new Semaphore[]{new Semaphore(1), new Semaphore(1)};
            var bufferFiller = new Thread(() -> {
                try {
                    var frameCounter = 0;
                    var image = frameGrabber.grabImage();
                    var offsetPerLine = (image.imageStride / 3) - width;

                    while (image != null) {
                        var frameBufferIndex = frameCounter % writeBuffers.length;
                        bufferLocks[frameBufferIndex].acquire();
                        var buffer = (ByteBuffer) image.image[0];
                        if (frameCounter == 0) {
                            sendChangedPixels(height, width, offsetPerLine, writeBuffers[frameBufferIndex], buffer, (x, y, r, g, b) -> true);
                        } else {
                            sendChangedPixels(height, width, offsetPerLine, writeBuffers[frameBufferIndex], buffer, (x, y, r, g, b) -> {
                                var index = (x + y * width) * 3;
                                var result = previousFrame[index] != r || previousFrame[index + 1] != g || previousFrame[index + 2] != b;

                                previousFrame[index] = r;
                                previousFrame[index + 1] = g;
                                previousFrame[index + 2] = b;

                                return result;
                            });
                        }
                        bufferLocks[frameBufferIndex].release();
                        //the assignment is technically not necessary, because the FrameGrabber will overwrite the same memory address, but this way we get null as a result when the stream is over
                        image = frameGrabber.grabImage();
                        frameCounter++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            bufferFiller.start();
            var writerThread = new Thread(() -> {
                try {
                    var frameCounter = 0;
                    while (bufferFiller.isAlive()) {
                        var frameBufferIndex = frameCounter % writeBuffers.length;
                        bufferLocks[frameBufferIndex].acquire();
                        writeBuffers[frameBufferIndex].flip();
                        while (writeBuffers[frameBufferIndex].remaining() > 0) {
                            socket.write(writeBuffers[frameBufferIndex]);
                        }
                        writeBuffers[frameBufferIndex].clear();
                        bufferLocks[frameBufferIndex].release();
                        socket.socket().getOutputStream().flush();
                        frameCounter++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            writerThread.start();
            bufferFiller.join();
            writerThread.join();
        }
    }

    private static void sendChangedPixels(int height, int width, int offsetPerLine, ByteBuffer writeBuffer, ByteBuffer buffer, HasValueChanged changeDetector) throws IOException {
        for (int y = 0; y < height; y++) {
            var yOffset = y * width + y * offsetPerLine;

            var yBytes = formatCoordinateValue(y);
            message[yCoordinateOffset] = yBytes[0];
            message[yCoordinateOffset + 1] = yBytes[1];
            message[yCoordinateOffset + 2] = yBytes[2];

            for (int x = 0; x < width; x++) {

                var xBytes = formatCoordinateValue(x);
                message[xCoordinateOffset] = xBytes[0];
                message[xCoordinateOffset + 1] = xBytes[1];
                message[xCoordinateOffset + 2] = xBytes[2];

                var index = (x + yOffset) * 3;
                byte r = buffer.get(index);
                byte g = buffer.get(index + 1);
                byte b = buffer.get(index + 2);
                byte[] rBytes = formatColorValue(r);
                byte[] gBytes = formatColorValue(g);
                byte[] bBytes = formatColorValue(b);

                message[colorOffset] = rBytes[0];
                message[colorOffset + 1] = rBytes[1];
                message[colorOffset + 2] = gBytes[0];
                message[colorOffset + 3] = gBytes[1];
                message[colorOffset + 4] = bBytes[0];
                message[colorOffset + 5] = bBytes[1];

                if (changeDetector.hasChanged(x, y, r, g, b)) {
                    writeBuffer.put(message);
                }
            }
        }
    }

    private static byte[] formatColorValue(byte value) {
        return hexTable[value & 0xFF];
    }

    private static byte[] formatCoordinateValue(int position) {
        return coordinateTable[position];
    }

    private static void printFps(long[] timesPerFrame) {
        System.out.println(1000 / Arrays.stream(timesPerFrame).filter(t -> t > 0).average().orElse(0.0) + " fps");
    }

    @FunctionalInterface
    private interface HasValueChanged {
        boolean hasChanged(int x, int y, byte r, byte g, byte b);
    }

}
