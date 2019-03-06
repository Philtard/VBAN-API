package de.kaleidox.vban.packet;

import de.kaleidox.util.model.ByteArray;
import de.kaleidox.util.model.IBuilder;
import de.kaleidox.util.model.IFactory;
import de.kaleidox.vban.VBAN.Protocol;

import static de.kaleidox.vban.Util.appendByteArray;

/**
 * Structural object representation of a VBAN UDP Packet.
 */
public class VBANPacket implements ByteArray {
    public static final int MAX_SIZE = 1436;
    private VBANPacketHead head;
    private byte[] bytes;

    /**
     * Private constructor.
     *
     * @param head The PacketHead to attach to this packet.
     */
    private VBANPacket(VBANPacketHead head) {
        this.head = head;
    }

    /**
     * Sets the data of this packet to the given byte-array.
     *
     * @param data The data to set.
     *
     * @return This instance.
     * @throws IllegalArgumentException If the given byte-array is too large.
     */
    public VBANPacket setData(byte[] data) throws IllegalArgumentException {
        if (data.length > MAX_SIZE)
            throw new IllegalArgumentException("Data is too large to be sent! Must be smaller than " + MAX_SIZE);
        bytes = data;
        return this;
    }

    @Override
    public byte[] getBytes() {
        return appendByteArray(head.getBytes(), bytes);
    }

    public static class Factory implements IFactory.Advanced<VBANPacket, byte[]> {

        private final IFactory<VBANPacketHead> headFactory;

        public Factory(IFactory<VBANPacketHead> headFactory) {
            this.headFactory = headFactory;
        }

        @Override
        public VBANPacket create(byte... data) {
            VBANPacket packet = new VBANPacket(headFactory.create());
            packet.bytes = data;
            return packet;
        }

        @Override
        public int counter() {
            return headFactory.counter();
        }

        public static <T> Builder<T> builder(Protocol<T> protocol) {
            return new Builder<>(protocol);
        }

        public static class Builder<T> implements IBuilder<Factory> {
            private final Protocol<T> protocol;
            private IFactory<VBANPacketHead> headFactory;

            private Builder(Protocol<T> protocol) {
                this.protocol = protocol;

                setHeadFactory(VBANPacketHead.defaultFactory(protocol));
            }

            public IFactory<VBANPacketHead> getHeadFactory() {
                return headFactory;
            }

            public Builder<T> setHeadFactory(VBANPacketHead.Factory<T> headFactory) {
                this.headFactory = headFactory;
                return this;
            }

            @Override
            public Factory build() {
                return new Factory(headFactory);
            }
        }
    }
}
