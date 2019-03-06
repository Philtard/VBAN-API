package de.kaleidox.vban.audio;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

import de.kaleidox.vban.VBAN;
import de.kaleidox.vban.packet.VBANPacket;

import org.jetbrains.annotations.NotNull;

public final class VBANAudio {
    public static class RecieveStream extends VBAN.ReadStream {
        private PlaybackThread playbackThread = null;

        public RecieveStream(InetAddress address, int port) throws SocketException {
            super(address, port);
        }

        public Future<Void> playback(ExecutorService exc) {
            PlaybackThread playbackThread = (this.playbackThread != null
                    ? this.playbackThread
                    : (this.playbackThread = new PlaybackThread()));
            exc.submit(playbackThread);
            return playbackThread.cancellationFuture();
        }

        private class PlaybackThread implements Runnable {
            private boolean cancelled = false;
            private Future<Void> cancellationFuture;

            @Override
            public void run() {
                SourceDataLine line;
                try {
                    AudioFormat af = new AudioFormat(24000, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, af);
                    line = (SourceDataLine) AudioSystem.getLine(info);
                    line.open(af, 4096);
                    line.start();
                } catch (Throwable e) {
                    throw new RuntimeException("Internal Error", e);
                }

                while (!cancelled) {
                    try {
                        byte[] bytes = new byte[VBANPacket.MAX_SIZE];
                        int read = read(bytes);

                        line.write(bytes, 0, read);
                    } catch (Throwable e) {
                        throw new RuntimeException("Internal Error", e);
                    }
                }
            }

            public Future<Void> cancellationFuture() {
                return cancellationFuture != null
                        ? cancellationFuture
                        : (cancellationFuture = new Future<Void>() {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning) {
                        return cancelled = true;
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }

                    @Override
                    public boolean isDone() {
                        return false;
                    }

                    @Override
                    public Void get() {
                        throw new UnsupportedOperationException("Unobtainable Future element");
                    }

                    @Override
                    public Void get(long timeout, @NotNull TimeUnit unit) {
                        throw new UnsupportedOperationException("Unobtainable Future element");
                    }
                });
            }
        }
    }
}
