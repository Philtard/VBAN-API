package de.kaleidox;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import de.kaleidox.vban.VBAN;
import de.kaleidox.vban.audio.VBANAudio;

public class RawAudioPlay {
    public static void main(String[] args) {
        try (VBANAudio.RecieveStream vban = VBANAudio.openRecieveStream(Executors.newSingleThreadExecutor(), InetAddress.getLocalHost(), 6981)) {
            vban.playback(Executors.newSingleThreadExecutor());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
