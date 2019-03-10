package de.kaleidox.test.vban;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

import de.kaleidox.vban.VBAN;
import de.kaleidox.vban.packet.VBANPacket;

import org.junit.Test;

public class VBANExceptionsTest {
    @Test
    public void testStringWithinBounds() throws IOException {
        byte[] bytes = new byte[VBANPacket.MAX_SIZE];
        Arrays.fill(bytes, (byte) 'x');
        VBAN.WriteStream<String> vban = createTextStream();
        vban.write(bytes);
        vban.flush();
        vban.close();
    }

    @Test(expected = IOException.class)
    public void testStringTooLong() throws IOException {
        byte[] bytes = new byte[VBANPacket.MAX_SIZE + 1];
        Arrays.fill(bytes, (byte) 'x');
        VBAN.WriteStream<String> vban = createTextStream();
        vban.write(bytes);
        vban.flush();
        vban.close();
    }

    @Test(expected = IOException.class)
    public void testStreamClosed() throws IOException {
        VBAN.WriteStream<String> vban = createTextStream();
        vban.close();
        vban.sendData("x");
    }

    private static VBAN.WriteStream<String> createTextStream() throws UnknownHostException, SocketException {
        return VBAN.openTextStream(InetAddress.getLocalHost(), VBAN.DEFAULT_PORT);
    }
}
