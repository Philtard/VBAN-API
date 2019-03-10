package de.kaleidox.vban;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import de.kaleidox.util.model.Bindable;
import de.kaleidox.util.model.IntEnum;
import de.kaleidox.vban.model.FormatValue;
import de.kaleidox.vban.model.SRValue;
import de.kaleidox.vban.packet.VBANPacket;
import de.kaleidox.vban.packet.VBANPacketHead;

import org.jetbrains.annotations.NotNull;

import static java.util.Arrays.copyOfRange;
import static de.kaleidox.vban.Util.createByteArray;

/**
 * Facade class for interacting with the API.
 */
public final class VBAN {
    public static final int DEFAULT_PORT = 6980;

    public static WriteStream<String> openTextStream(InetAddress address, int port) throws SocketException {
        return new WriteStream<>(VBANPacket.Factory.builder(Protocol.TEXT).build(), address, port);
    }

    public static class ReadStream extends InputStream {
        public final boolean async;

        private final InetAddress address;
        private final int port;
        private final DatagramSocket socket;
        private final Queue<Byte> rcv;

        protected boolean closed = false;

        /**
         * Creates a ReadStream.
         * This ReadStream only reads when {@link #read()} was invoked.
         *
         * @param address The address to listen to.
         * @param port    The port to listen on.
         *
         * @throws SocketException See {@link DatagramSocket} constructor.
         */
        public ReadStream(InetAddress address, int port) throws SocketException {
            this.address = address;
            this.port = port;

            socket = new DatagramSocket(new InetSocketAddress(address, port));
            rcv = new PriorityQueue<>();

            async = false;
        }

        /**
         * Creates an async reading and buffering ReadStream.
         * <p>
         * An async ReadStream permanently reads packets and buffers the results.
         *
         * @param exc     The ExecutorService to run the async reading on.
         * @param address The address to listen to.
         * @param port    The port to listen on.
         *
         * @throws SocketException See {@link DatagramSocket} constructor.
         */
        public ReadStream(ExecutorService exc, InetAddress address, int port) throws SocketException {
            this.address = address;
            this.port = port;

            socket = new DatagramSocket(new InetSocketAddress(address, port));
            rcv = new LinkedBlockingQueue<>();
            exc.submit(new ReadThread());

            async = true;
        }

        @Override
        public int read() throws IOException {
            if (closed) throw new IOException("Stream is closed");
            if (async) {
                synchronized (rcv) {
                    try {
                        while (rcv.isEmpty()) rcv.wait();
                        return rcv.poll();
                    } catch (InterruptedException e) {
                        throw new IOException("Read wait interrupted; check underlying InterruptedException", e);
                    }
                }
            } else {
                if (rcv.isEmpty()) {
                    byte[] bytes = new byte[VBANPacket.MAX_SIZE];
                    DatagramPacket packet = new DatagramPacket(bytes, VBANPacket.MAX_SIZE);
                    socket.receive(packet);
                    for (byte b : copyOfRange(bytes, VBANPacketHead.SIZE, bytes.length)) rcv.add(b);
                }
                //noinspection ConstantConditions
                return rcv.poll();
            }
        }

        @Override
        public int read(@NotNull byte[] buffer) throws IOException {
            if (closed) throw new IOException("Stream is closed");
            if (buffer.length > VBANPacketHead.SIZE)
                throw new IllegalArgumentException("Buffer is too large, must be smaller than 1436 bytes");
            if (async) return super.read(buffer);
            else {
                byte[] bytes = new byte[VBANPacket.MAX_SIZE];
                DatagramPacket packet = new DatagramPacket(bytes, VBANPacket.MAX_SIZE);
                socket.receive(packet);

                buffer = copyOfRange(bytes, VBANPacketHead.SIZE, bytes.length);
                return buffer.length;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) throw new IOException("Stream already is closed");

            socket.close();

            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }

        private class ReadThread implements Runnable {
            @SuppressWarnings("InfiniteLoopStatement")
            @Override
            public void run() {
                try {
                    while (true) {
                        try {
                            synchronized (rcv) {
                                byte[] buffer = new byte[VBANPacket.MAX_SIZE];
                                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                                socket.receive(packet);

                                for (byte b : buffer) rcv.add(b);
                                rcv.notify();
                            }
                        } catch (IOException e) {
                            if (e instanceof SocketException) throw e;
                            e.printStackTrace();
                        }
                    }
                } catch (Throwable e) {
                    RuntimeException runtimeException = new RuntimeException("Fatal exception in ReadThread", e);
                    runtimeException.printStackTrace();
                    throw runtimeException;
                }
            }
        }
    }

    public static class WriteStream<T> extends OutputStream {
        private final VBANPacket.Factory packetFactory;
        private final InetAddress address;
        private final int port;
        private final DatagramSocket socket;

        public boolean autoflush = false;

        protected byte[] buffer;
        protected int index;
        protected boolean closed = false;

        /**
         * @throws SocketException See {@link DatagramSocket} constructor.
         */
        protected WriteStream(
                VBANPacket.Factory packetFactory,
                InetAddress address, int port
        ) throws SocketException {
            super();

            this.packetFactory = packetFactory;
            this.address = address;
            this.port = port;

            socket = new DatagramSocket();

            clearBuffer();
        }

        /**
         * Tries to send the given data to the specified {@linkplain InetAddress address} on the specified {@code port}.
         *
         * @param data The data to send. Is converted to a bytearray using
         *             {@link Util#createByteArray(Object)}.
         *
         * @return The instance of the stream.
         * @throws IOException              If the stream has been {@linkplain #close() closed} before.
         * @throws IOException              See {@link DatagramSocket#send(DatagramPacket)} for details.
         * @throws IllegalArgumentException If the converted byte-array from the given data is too large.
         */
        public WriteStream<T> sendData(T data) throws IOException, IllegalArgumentException {
            write(createByteArray(data));
            flush();
            return this;
        }

        /**
         * Writes one byte to this stream's byte buffer, but does not send anything.
         * The byte buffer is being sent and cleared by invoking {@link #flush()}.
         *
         * @param b The byte as an int to append.
         *
         * @throws IOException If the stream has been {@linkplain #close() closed} before.
         * @throws IOException See {@link DatagramSocket#send(DatagramPacket)} for details.
         */
        @Override
        public void write(int b) throws IOException {
            if (index + 1 > VBANPacket.MAX_SIZE)
                throw new IOException("Byte array is too large, must be smaller than " + VBANPacket.MAX_SIZE);
            buffer[index++] = (byte) b;
        }

        @Override
        public void write(@NotNull byte[] b, int off, int len) throws IOException {
            super.write(b, off, len);
            if (autoflush) flush();
        }

        /**
         * Sends this stream's byte buffer to the specified {@linkplain InetAddress address} on the specified {@code
         * port},
         * then clears the byte buffer.
         *
         * @throws IOException If the stream has been {@linkplain #close() closed} before.
         * @throws IOException See {@link DatagramSocket#send(DatagramPacket)} for details.
         */
        @Override
        public synchronized void flush() throws IOException {
            if (closed) throw new IOException("Stream is closed");
            if (buffer.length > VBANPacket.MAX_SIZE)
                throw new IOException("Buffer is too large, must be smaller than " + VBANPacket.MAX_SIZE);
            byte[] bytes = packetFactory.create(buffer).getBytes();
            socket.send(new DatagramPacket(bytes, bytes.length, address, port));
            clearBuffer();
        }

        /**
         * Drops the Socket and PacketFactory object and marks this stream as {@code closed.}
         * Any attempt to send data after closing the stream will result in an {@link IOException} being thrown.
         */
        @Override
        public void close() throws IOException {
            if (closed) throw new IOException("Stream already is closed");
            flush();

            socket.close();

            closed = true;
        }

        protected void clearBuffer() {
            buffer = new byte[1436];
            Arrays.fill(buffer, (byte) 0);
            index = 0;
        }
    }

    /**
     * Collection of codec values, required for creating a {@link VBANPacketHead.Factory}.
     */
    public static final class Codec {
        public final static int PCM = 0x00;
        public final static int VBCA = 0x10; // VB-Audio AOIP Codec
        public final static int VBCV = 0x20; // VB-Audio VOIP Codec
        public final static int USER = 0xF0;
    }

    /**
     * Collection of protocol values, required for creating a {@link VBANPacketHead.Factory}.
     */
    public static final class Protocol<T> implements Bindable<T>, IntEnum {
        public final static Protocol<byte[]> AUDIO = new Protocol<>(0x00);
        public final static Protocol SERIAL = new Protocol(0x20);
        public final static Protocol<String> TEXT = new Protocol<>(0x40);
        public final static Protocol SERVICE = new Protocol(0x60);

        private final static Protocol[] values = new Protocol[]{AUDIO, SERIAL, TEXT, SERVICE};

        private final int value;

        private Protocol(int value) {
            this.value = value;
        }

        public String name() {
            switch (value) {
                case 0x00:
                    return "AUDIO";
                case 0x20:
                    return "SERIAL";
                case 0x40:
                    return "TEXT";
                case 0x60:
                    return "SERVICE";
            }
            throw new AssertionError("Unknown protocol: " + this.toString());
        }

        @Override
        public String toString() {
            return String.format("%s-Protocol(%s)", name(), Integer.toHexString(value));
        }

        @Override
        public int getValue() {
            return value;
        }

        public static Protocol[] values() {
            return values;
        }

        public static Protocol fromInt(int protocol) {
            for (Protocol p : values()) if (p.value == protocol) return p;
            throw new NoSuchElementException("Protocol with bitmask " + Integer.toHexString(protocol));
        }
    }

    /**
     * Collection of sample rate indices, required for creating a {@link VBANPacketHead.Factory}.
     */
    public enum SampleRate implements SRValue<byte[]> {
        Hz6000,
        Hz12000,
        Hz24000,
        Hz48000,
        Hz96000,
        Hz192000,
        Hz384000,

        Hz8000,
        Hz16000,
        Hz32000,
        Hz64000,
        Hz128000,
        Hz256000,
        Hz512000,

        Hz11025,
        Hz22050,
        Hz44100,
        Hz88200,
        Hz176400,
        Hz352800,
        Hz705600;

        private final int rate;

        SampleRate() {
            rate = Integer.parseInt(name().substring(2));
        }

        SampleRate(int rate) {
            this.rate = rate;
        }

        @Override
        public int getValue() {
            return ordinal();
        }

        @Override
        public int getRate() {
            return rate;
        }
    }

    public enum BitsPerSecond implements SRValue {
        Bps0,
        Bps110,
        Bps150,
        Bps300,
        Bps600,
        Bps1200,
        Bps2400,

        Bps4800,
        Bps9600,
        Bps14400,
        Bps19200,
        Bps31250,
        Bps38400,
        Bps57600,

        Bps115200,
        Bps128000,
        Bps230400,
        Bps250000,
        Bps256000,
        Bps460800,
        Bps921600,

        Bps1000000,
        Bps1500000,
        Bps2000000,
        Bps3000000;

        private final int rate;

        BitsPerSecond() {
            rate = Integer.parseInt(name().substring(3));
        }

        BitsPerSecond(int rate) {
            this.rate = rate;
        }

        @Override
        public int getValue() {
            return ordinal();
        }

        @Override
        public int getRate() {
            return rate;
        }
    }

    /**
     * Collection of format values, required for creating a {@link VBANPacketHead.Factory}.
     */
    public enum AudioFormat implements FormatValue<byte[]> {
        BYTE8(0x00),
        INT16(0x01),
        INT24(0x02),
        INT32(0x03),
        FLOAT32(0x04),
        FLOAT64(0x05),
        BITS12(0x06),
        BITS10(0x07);

        private final int value;

        AudioFormat(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }

        public static AudioFormat fromInt(int format) {
            for (AudioFormat audioFormat : values()) if (audioFormat.value == format) return audioFormat;
            throw new NoSuchElementException("AudioFormat with bitmask " + Integer.toHexString(format));
        }
    }

    public enum Format implements FormatValue {
        BYTE8(0x00);

        private final int value;

        Format(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }
    }
}
