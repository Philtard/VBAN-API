package de.kaleidox.vban.packet;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import de.kaleidox.util.model.ByteArray;
import de.kaleidox.util.model.Construction;
import de.kaleidox.vban.VBAN;
import de.kaleidox.vban.model.FormatValue;
import de.kaleidox.vban.model.SRValue;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;

import static java.lang.System.arraycopy;
import static de.kaleidox.vban.Util.appendByteArray;
import static de.kaleidox.vban.Util.checkRange;
import static de.kaleidox.vban.Util.intToByteArray;
import static de.kaleidox.vban.Util.stringToBytesASCII;
import static de.kaleidox.vban.Util.trimArray;
import static de.kaleidox.vban.packet.VBANPacketHead.Factory.builder;

public class VBANPacketHead implements ByteArray {
    public final static int SIZE = 28;
    public final Factory factory;
    private final byte[] bytes;

    private VBANPacketHead(final Factory factory,
                           int protocol,
                           int sampleRateIndex,
                           int samples,
                           int channel,
                           int format,
                           int codec,
                           String streamName,
                           int frameCounter) {
        this.factory = factory;
        byte[] bytes = new byte[0];
        checkRange(samples, 0, 127);
        checkRange(channel, 0, 127);

        bytes = appendByteArray(bytes, "VBAN".getBytes());
        bytes = appendByteArray(bytes, (byte) (protocol | sampleRateIndex));
        bytes = appendByteArray(bytes, (byte) samples, (byte) channel);
        bytes = appendByteArray(bytes, (byte) (format | codec));
        bytes = appendByteArray(bytes, trimArray(stringToBytesASCII(streamName), 16));
        this.bytes = appendByteArray(bytes, intToByteArray(frameCounter, 4));
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Creates a Factory instance with the default properties for the specified Protocol.
     *
     * @param forProtocol The protocol to use standards for.
     * @param <T>         Type-variable for the VBAN-Stream.
     *
     * @return A new Factory instance.
     * @throws UnsupportedOperationException If the Protocol is one of {@code [AUDIO, SERIAL, SERVICE]}.
     */
    public static <T> Factory<T> defaultFactory(VBAN.Protocol<T> forProtocol) throws UnsupportedOperationException {
        return builder(forProtocol).build();
    }

    /**
     * Creates a Properties object based on a recieved VBAN Packet.
     *
     * @param data The bytes of the recieved packet.
     *
     * @return The composed packet information.
     */
    public static Properties getProperties(byte[] data) {
        if (data.length <= SIZE) throw new IllegalArgumentException("Wrong array size, must be larger than " + SIZE);

        int protocol, sampleRateIndex, samples, channel, format, codec, frameCounter;
        String streamName;

        assert (char) data[0] == 'V' : "Illegal Header";
        assert (char) data[1] == 'B' : "Illegal Header";
        assert (char) data[2] == 'A' : "Illegal Header";
        assert (char) data[3] == 'N' : "Illegal Header";

        sampleRateIndex = data[4] & 0x17;
        protocol = (data[4] >> 5) << 5;

        samples = (int) data[5];
        channel = (int) data[6];

        format = data[7] & 0x07;
        codec = (data[7] >> 4) << 4;

        byte[] chars = new byte[16];
        arraycopy(data, 8, chars, 0, 16);
        int c = 0;
        for (byte b : chars) if (b == 0) c++;
        byte[] printable = new byte[16 - c];
        arraycopy(chars, 0, printable, 0, printable.length);
        streamName = new String(printable, StandardCharsets.US_ASCII);

        frameCounter = ByteBuffer.wrap(new byte[]{data[24], data[25], data[26], data[27]}).getInt();

        byte[] packetData = new byte[data.length - SIZE];
        arraycopy(data, SIZE, packetData, 0, packetData.length);

        return new Properties(
                protocol,
                sampleRateIndex,
                samples,
                channel,
                format,
                codec,
                streamName,
                frameCounter,
                packetData
        );
    }

    public static class Properties {
        public final VBAN.Protocol protocol;
        public final byte[] data;
        public final SRValue sampleRate;
        @Range(from = 0, to = 128)
        public final int samples;
        @Range(from = 0, to = 128)
        public final int channel;
        public final FormatValue format;
        public final int codec;
        public final String streamName;
        public final int frameCounter;

        private Properties(
                int protocol,
                int sampleRateIndex,
                int samples,
                int channel,
                int format,
                int codec,
                String streamName,
                int frameCounter,
                byte[] data
        ) {
            this.protocol = VBAN.Protocol.fromInt(protocol);
            this.sampleRate = protocol == VBAN.Protocol.AUDIO.getValue()
                    ? VBAN.SampleRate.values()[sampleRateIndex]
                    : VBAN.BitsPerSecond.values()[sampleRateIndex];
            this.samples = samples < 0 ? 0 : samples;
            this.channel = channel < 0 ? 0 : channel;
            this.format = protocol == VBAN.Protocol.AUDIO.getValue()
                    ? VBAN.AudioFormat.fromInt(format)
                    : VBAN.Format.BYTE8;
            this.codec = codec;
            this.streamName = streamName;
            this.frameCounter = frameCounter;
            this.data = data;
        }
    }

    public static class Factory<T> implements Construction.Factory<VBANPacketHead> {
        private final VBAN.Protocol<T> protocol;
        private final SRValue<T> sampleRate;
        private final int samples;
        private final int channel;
        private final FormatValue<T> format;
        private final int codec;
        private final String streamName;
        private int counter;

        private Factory(VBAN.Protocol<T> protocol,
                        SRValue<T> sampleRateIndex,
                        @Range(from = 0, to = 128) int samples,
                        @Range(from = 0, to = 128) int channel,
                        FormatValue<T> format,
                        int codec,
                        String streamName) {
            this.protocol = protocol;
            this.sampleRate = sampleRateIndex;
            this.samples = samples;
            this.channel = channel;
            this.format = format;
            this.codec = codec;
            this.streamName = streamName;

            counter = 0;
        }

        public VBAN.Protocol<T> getProtocol() {
            return protocol;
        }

        @SuppressWarnings("unchecked")
        public <SR extends SRValue<T>> SR getSampleRate() {
            return (SR) sampleRate;
        }

        public int getSamples() {
            return samples;
        }

        public int getChannel() {
            return channel;
        }

        @SuppressWarnings("unchecked")
        public <FV extends FormatValue<T>> FV getFormat() {
            return (FV) format;
        }

        public int getCodec() {
            return codec;
        }

        public String getStreamName() {
            return streamName;
        }

        @Override
        public synchronized VBANPacketHead create() {
            return new VBANPacketHead(
                    this,
                    protocol.getValue(),
                    sampleRate.getValue(),
                    samples,
                    channel,
                    format.getValue(),
                    codec,
                    streamName,
                    counter++
            );
        }

        @Override
        public synchronized int counter() {
            return counter;
        }

        public javax.sound.sampled.AudioFormat createAudioFormat() {
            if (protocol == VBAN.Protocol.AUDIO)
                return new javax.sound.sampled.AudioFormat(
                        sampleRate.getRate(),
                        samples,
                        channel,
                        true,
                        false
                );
            return null;
        }

        /**
         * Creates a new Builder with the default properties pre-set for the specified protocol.
         *
         * @param protocol The protocol to create the Builder for.
         * @param <T>      Type-Variable for the stream type.
         *
         * @return A new builder for the given protocol.
         */
        public static <T> Builder<T> builder(VBAN.Protocol<T> protocol) throws UnsupportedOperationException {
            return new Builder<>(protocol);
        }

        public static class Builder<T> implements Construction.Builder<Factory<T>> {
            private final VBAN.Protocol<T> protocol;
            private SRValue<T> sampleRate;
            @Range(from = 0, to = 128)
            private int samples;
            @Range(from = 0, to = 128)
            private int channel;
            private FormatValue<T> format;
            @MagicConstant(valuesFromClass = VBAN.Codec.class)
            private int codec = VBAN.Codec.PCM;
            private String streamName = null;

            /*
            Suppress ConstantConditions because, while the MIDI branch breaks away due to Serial communication not
            being implemented yet, the IF in the Text communication branch will always be 'false'
             */
            @SuppressWarnings({"unchecked", "ConstantConditions"})
            private Builder(VBAN.Protocol<T> protocol) throws UnsupportedOperationException {
                this.protocol = protocol;

                switch (protocol.getValue()) {
                    case 0x00:
                        sampleRate = (SRValue<T>) VBAN.SampleRate.Hz48000;
                        samples = 127;
                        channel = 2;
                        format = (FormatValue<T>) VBAN.AudioFormat.INT16;
                        streamName = "Stream1";
                        return;
                    case 0x20:
                        streamName = "MIDI1";
                        break;
                    case 0x40:
                        sampleRate = (SRValue<T>) VBAN.BitsPerSecond.Bps256000;
                        samples = 0;
                        channel = 0;
                        format = (FormatValue<T>) VBAN.Format.BYTE8;
                        // if because we are in a shared branch
                        if (streamName == null) streamName = "Command1";
                        return;
                    case 0x60:
                        break;
                    default:
                        throw new AssertionError("Unknown Protocol: " + protocol);
                }

                throw new UnsupportedOperationException("Unsupported Protocol: " + protocol);
            }

            public VBAN.Protocol<T> getProtocol() {
                return protocol;
            }

            public SRValue<T> getSampleRate() {
                return sampleRate;
            }

            public Builder setSRValue(SRValue<T> sampleRate) {
                this.sampleRate = sampleRate;
                return this;
            }

            public int getSamples() {
                return samples;
            }

            public Builder setSamples(@Range(from = 0, to = 128) int samples) {
                this.samples = samples;
                return this;
            }

            public int getChannel() {
                return channel;
            }

            public Builder setChannel(@Range(from = 0, to = 128) int channel) {
                this.channel = channel;
                return this;
            }

            public FormatValue<T> getFormat() {
                return format;
            }

            public Builder setFormatValue(FormatValue<T> format) {
                this.format = format;
                return this;
            }

            public int getCodec() {
                return codec;
            }

            public Builder setCodec(int codec) {
                if (protocol == VBAN.Protocol.AUDIO && codec != VBAN.Codec.PCM)
                    throw new IllegalStateException("Only PCM codec is supported!");
                this.codec = codec;
                return this;
            }

            public String getStreamName() {
                return streamName;
            }

            public Builder setStreamName(String streamName) {
                this.streamName = streamName;
                return this;
            }

            @Override
            public Factory<T> build() {
                assert protocol != null : "No protocol defined!";

                return new Factory<>(protocol, sampleRate, samples, channel, format, codec, streamName);
            }
        }
    }
}
