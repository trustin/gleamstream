package kr.motd.gleamstream;

import static kr.motd.gleamstream.Panic.panic;
import static org.lwjgl.openal.AL.createCapabilities;
import static org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED;
import static org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.AL_NO_ERROR;
import static org.lwjgl.openal.AL10.AL_PLAYING;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDeleteBuffers;
import static org.lwjgl.openal.AL10.alDeleteSources;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetError;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alGetString;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourceQueueBuffers;
import static org.lwjgl.openal.AL10.alSourceUnqueueBuffers;
import static org.lwjgl.openal.AL10.alSourcef;
import static org.lwjgl.openal.ALC10.ALC_DEFAULT_DEVICE_SPECIFIER;
import static org.lwjgl.openal.ALC10.alcCloseDevice;
import static org.lwjgl.openal.ALC10.alcCreateContext;
import static org.lwjgl.openal.ALC10.alcDestroyContext;
import static org.lwjgl.openal.ALC10.alcGetString;
import static org.lwjgl.openal.ALC10.alcMakeContextCurrent;
import static org.lwjgl.openal.ALC10.alcOpenDevice;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.nvstream.av.audio.AudioRenderer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

public final class OpenAlAudioRenderer implements AudioRenderer {

    private static final Logger logger = LoggerFactory.getLogger(OpenAlAudioRenderer.class);

    private boolean initialized;
    private long device;
    private long ctx;
    private int format;
    private int frequency;
    private int source;
    private ByteBuffer directBuffer;

    private final IntList buffers = new IntArrayList();

    /**
     * The callback for the audio stream being initialized and starting to receive.
     * @param channelCount the number of channels in the audio
     * @param channelMask the enabled channels in the audio
     * @param samplesPerFrame the number of 16-bit samples per audio frame
     * @param sampleRate the sample rate for the audio.
     */
    @Override
    public boolean streamInitialized(int channelCount, int channelMask, int samplesPerFrame, int sampleRate) {
        switch (channelCount) {
            case 2:
                format = AL_FORMAT_STEREO16;
                break;
            default:
                logger.warn("Unsupported channel count: {}", channelCount);
                return false;
        }

        frequency = sampleRate;
        return true;
    }

    /**
     * Takes some audio data and writes it out to the renderer.
     * @param pcmData the array that contains the audio data
     * @param offset the offset at which the data starts in the array
     * @param length the length of data to be rendered
     */
    @Override
    public void playDecodedAudio(byte[] pcmData, int offset, int length) {
        if (!lazyInit()) {
            return;
        }

        final int buf;
        if (alGetSourcei(source, AL_BUFFERS_PROCESSED) != 0) {
            buf = alSourceUnqueueBuffers(source);
        } else {
            buf = alGenBuffers();
            buffers.add(buf);
        }

        if (directBuffer.capacity() < length) {
            directBuffer = MemoryUtil.memRealloc(directBuffer, length * 2);
        }
        directBuffer.clear();
        directBuffer.put(pcmData, offset, length);
        directBuffer.flip();

        alBufferData(buf, format, directBuffer, frequency);
        alSourceQueueBuffers(source, buf);
        final int alError = alGetError();
        if (alError != AL_NO_ERROR) {
            throw panic("Failed to enqueue a sound buffer: " + alGetString(alError));
        }

        if (alGetSourcei(source, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(source);
        }
    }

    private boolean lazyInit() {
        if (initialized) {
            return device != NULL;
        }

        initialized = true;
        device = alcOpenDevice((ByteBuffer) null);
        if (device == NULL) {
            logger.warn("Failed to open a sound device: {}", alGetString(alGetError()));
            return false;
        }

        logger.info("Using sound device: {}", alcGetString(device, ALC_DEFAULT_DEVICE_SPECIFIER));

        ctx = alcCreateContext(device, (IntBuffer) null);
        if (ctx == NULL) {
            logger.warn("Failed to create a sound context: {}", alGetString(alGetError()));
            destroy();
            return false;
        }

        int alError;

        final ALCCapabilities alcCaps = ALC.createCapabilities(device);
        alcMakeContextCurrent(ctx);
        createCapabilities(alcCaps);
        alError = alGetError();
        if (alError != AL_NO_ERROR) {
            logger.warn("Failed to set the current sound context: {}", alGetString(alError));
            destroy();
            return false;
        }

        source = alGenSources();
        alError = alGetError();
        if (alError != AL_NO_ERROR) {
            logger.warn("Failed to generate a sound source: {}", alGetString(alError));
            destroy();
            return false;
        }

        alSourcef(source, AL_GAIN, 1.0f);

        directBuffer = MemoryUtil.memAlloc(8192);
        return true;
    }

    /**
     * Callback for when the stream session is closing and the audio renderer should stop.
     */
    @Override
    public void streamClosing() {
        boolean interrupted = false;
        try {
            while (alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING) {
                Thread.sleep(10);
            }
        } catch (InterruptedException ignored) {
            interrupted = true;
        } finally {
            destroy();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void destroy() {
        if (directBuffer != null) {
            MemoryUtil.memFree(directBuffer);
            directBuffer = null;
        }

        for (int buf : buffers) {
            if (buf != 0) {
                alDeleteBuffers(buf);
            }
        }
        buffers.clear();

        if (source != 0) {
            alDeleteSources(source);
            source = 0;
        }

        if (ctx != NULL) {
            alcMakeContextCurrent(NULL);
            alcDestroyContext(ctx);
            ctx = NULL;
        }
        if (device != NULL) {
            alcCloseDevice(device);
            device = NULL;
        }
    }

    @Override
    public int getCapabilities() {
        return 0;
    }
}
