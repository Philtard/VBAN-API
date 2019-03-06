package de.kaleidox.vban.audio;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;

import de.kaleidox.vban.packet.VBANPacket;

public final class VBANAudio {
    public static class RecieveStream extends InputStream {
        public RecieveStream(
                VBANPacket.Factory<byte[]> packetFactory,
                InetAddress address,
                int port
        ) {
            super();
        }

        @Override
        public int read() throws IOException {
            return 0;
        }
    }
}
