package com.limelight.binding.audio;

import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.audio.AudioRenderer;

/**
 * Audio renderer implementation
 * @author Cameron Gutman
 */
public class JavaxAudioRenderer implements AudioRenderer {

    private static final Logger logger = LoggerFactory.getLogger(JavaxAudioRenderer.class);

    private SourceDataLine soundLine;
    private SoundBuffer soundBuffer;
    private byte[] lineBuffer;
    private int channelCount;
    private int sampleRate;
    private boolean reallocateLines;

    public static final int STARING_BUFFER_SIZE = 8192;
    public static final int STAGING_BUFFERS = 3; // 3 complete frames of audio

    /**
     * Takes some audio data and writes it out to the renderer.
     * @param pcmData the array that contains the audio data
     * @param offset the offset at which the data starts in the array
     * @param length the length of data to be rendered
     */
    @Override
    public void playDecodedAudio(byte[] pcmData, int offset, int length) {
        if (soundLine != null) {
            // Queue the decoded samples into the staging sound buffer
            soundBuffer.queue(new ByteBufferDescriptor(pcmData, offset, length));

            int available = soundLine.available();
            if (reallocateLines) {
                // Kinda jank. If the queued is larger than available, we are going to have a delay
                // so we increase the buffer size
                if (available < soundBuffer.size()) {
                    logger.warn("buffer too full, buffer size: " + soundLine.getBufferSize());
                    int currentBuffer = soundLine.getBufferSize();
                    soundLine.close();
                    createSoundLine(currentBuffer * 2);
                    if (soundLine != null) {
                        available = soundLine.available();
                        logger.warn("creating new line with buffer size: " + soundLine.getBufferSize());
                    } else {
                        available = 0;
                        logger.warn("failed to create sound line");
                    }
                }
            }

            // If there's space available in the sound line, pull some data out
            // of the staging buffer and write it to the sound line
            if (available > 0) {
                int written = soundBuffer.fill(lineBuffer, 0, available);
                if (written > 0) {
                    soundLine.write(lineBuffer, 0, written);
                }
            }
        }
    }

    /**
     * Callback for when the stream session is closing and the audio renderer should stop.
     */
    @Override
    public void streamClosing() {
        if (soundLine != null) {
            soundLine.close();
        }
    }

    private boolean createSoundLine(int bufferSize) {
        AudioFormat audioFormat = new AudioFormat(sampleRate, 16, channelCount, true,
                                                  ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN);

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat, bufferSize);

        try {
            soundLine = (SourceDataLine) AudioSystem.getLine(info);
            soundLine.open(audioFormat, bufferSize);

            soundLine.start();
            lineBuffer = new byte[soundLine.getBufferSize()];
            soundBuffer = new SoundBuffer(STAGING_BUFFERS);
        } catch (LineUnavailableException e) {
            return false;
        }

        return true;
    }

    /**
     * The callback for the audio stream being initialized and starting to receive.
     * @param channelCount the number of channels in the audio
     * @param channelMask the enabled channels in the audio
     * @param samplesPerFrame the number of 16-bit samples per audio frame
     * @param sampleRate the sample rate for the audio.
     */
    @Override
    public boolean streamInitialized(int channelCount, int channelMask, int samplesPerFrame, int sampleRate) {
        this.channelCount = channelCount;
        this.sampleRate = sampleRate;

        if (!createSoundLine(STARING_BUFFER_SIZE)) {
            return false;
        }
        reallocateLines = true;

        return true;
    }

    @Override
    public int getCapabilities() {
        return 0;
    }

}