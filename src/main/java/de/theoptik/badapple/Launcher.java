package de.theoptik.badapple;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        var processors = Runtime.getRuntime().availableProcessors();

        try (var frameGrabber = new FFmpegFrameGrabber(filePath); var executor = Executors.newFixedThreadPool(processors);) {
            frameGrabber.start();
            var width = frameGrabber.getImageWidth();
            var height = frameGrabber.getImageHeight();
            var batchSize = height / processors;
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
                        var buffer = (ByteBuffer) image.image[0];
                        bufferLocks[frameBufferIndex].acquire();
                        List<Callable<Void>> tasks;
                        if (frameCounter == 0) {
                            tasks = writeChangedPixelsToBuffer(height, width, offsetPerLine, writeBuffers[frameBufferIndex], buffer, (x, y, r, g, b) -> true, batchSize);
                        } else {
                            tasks = writeChangedPixelsToBuffer(height, width, offsetPerLine, writeBuffers[frameBufferIndex], buffer, (x, y, r, g, b) -> {
                                var index = (x + y * width) * 3;
                                var result = previousFrame[index] != r || previousFrame[index + 1] != g || previousFrame[index + 2] != b;

                                previousFrame[index] = r;
                                previousFrame[index + 1] = g;
                                previousFrame[index + 2] = b;

                                return result;
                            }, batchSize);
                        }
                        for(var future: executor.invokeAll(tasks)){
                            future.get();
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

    private static List<Callable<Void>> writeChangedPixelsToBuffer(
            int height,
            int width,
            int offsetPerLine,
            ByteBuffer writeBuffer,
            ByteBuffer image,
            HasValueChanged changeDetector,
            int batchSize
    ) {
        return IntStream.range(0,height/batchSize).<Callable<Void>>mapToObj(yChunk ->{
                    var startY = yChunk * batchSize;
            var endY = Math.min(startY + batchSize, height);

        return () -> {
                byte[] messageCopy = new byte[message.length];

                for (int y = startY; y < endY; y++) {
                    System.arraycopy(message, 0, messageCopy, 0, message.length);

                    var yOffset = y * width + y * offsetPerLine;
                    var yBytes = formatCoordinateValue(y);
                    messageCopy[yCoordinateOffset] = yBytes[0];
                    messageCopy[yCoordinateOffset + 1] = yBytes[1];
                    messageCopy[yCoordinateOffset + 2] = yBytes[2];

                    for (int x = 0; x < width; x++) {
                        var xBytes = formatCoordinateValue(x);
                        messageCopy[xCoordinateOffset] = xBytes[0];
                        messageCopy[xCoordinateOffset + 1] = xBytes[1];
                        messageCopy[xCoordinateOffset + 2] = xBytes[2];

                        var index = (x + yOffset) * 3;
                        byte r = image.get(index);
                        byte g = image.get(index + 1);
                        byte b = image.get(index + 2);
                        byte[] rBytes = formatColorValue(r);
                        byte[] gBytes = formatColorValue(g);
                        byte[] bBytes = formatColorValue(b);

                        messageCopy[colorOffset] = rBytes[0];
                        messageCopy[colorOffset + 1] = rBytes[1];
                        messageCopy[colorOffset + 2] = gBytes[0];
                        messageCopy[colorOffset + 3] = gBytes[1];
                        messageCopy[colorOffset + 4] = bBytes[0];
                        messageCopy[colorOffset + 5] = bBytes[1];

                        if (changeDetector.hasChanged(x, y, r, g, b)) {
                            synchronized (writeBuffer) {
                                writeBuffer.put(messageCopy);
                            }
                        }
                    }
                }
                return null;
            };
        }).toList();
    }

    private static byte[] formatColorValue(byte value) {
        return hexTable[value & 0xFF];
    }

    private static byte[] formatCoordinateValue(int position) {
        return coordinateTable[position];
    }

    @FunctionalInterface
    private interface HasValueChanged {
        boolean hasChanged(int x, int y, byte r, byte g, byte b);
    }
}
