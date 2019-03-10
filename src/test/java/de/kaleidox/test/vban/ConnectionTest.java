package de.kaleidox.test.vban;

import java.io.IOException;
import java.net.InetAddress;

import de.kaleidox.vban.VBAN;

import static java.nio.charset.StandardCharsets.UTF_8;
import static de.kaleidox.vban.VBAN.DEFAULT_PORT;

public class ConnectionTest {
    public static void main(String[] args) {
        try (VBAN.WriteStream<String> vban = VBAN.openTextStream(InetAddress.getLocalHost(), DEFAULT_PORT)) {
            vban.autoflush = true;
            vban.write("bus(1).mute=1".getBytes(UTF_8));
            Thread.sleep(500);
            vban.sendData("bus(1).mute=0");
            Thread.sleep(500);
            vban.write("bus(1).mute=1".getBytes(UTF_8));
            vban.flush();
            Thread.sleep(500);
            vban.sendData("bus(1).mute=0");
            Thread.sleep(500);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }
}
