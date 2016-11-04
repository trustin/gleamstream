package com.limelight.nvstream;

import com.limelight.nvstream.http.NvApp;

public class StreamConfiguration {
    public static final int INVALID_APP_ID = 0;

    public static final int AUDIO_CONFIGURATION_STEREO = 1;
    public static final int AUDIO_CONFIGURATION_5_1 = 2;

    private static final int CHANNEL_COUNT_STEREO = 2;
    private static final int CHANNEL_COUNT_5_1 = 6;

    private static final int CHANNEL_MASK_STEREO = 0x3;
    private static final int CHANNEL_MASK_5_1 = 0xFC;

    private NvApp app;
    private int width, height;
    private int refreshRate;
    private int bitrate;
    private boolean sops;
    private boolean enableAdaptiveResolution;
    private boolean playLocalAudio;
    private int maxPacketSize;
    private boolean remote;
    private int audioChannelMask;
    private int audioChannelCount;
    private boolean supportsHevc;

    public static class Builder {
        private final StreamConfiguration config = new StreamConfiguration();

        public Builder setApp(NvApp app) {
            config.app = app;
            return this;
        }

        public Builder setRemote(boolean remote) {
            config.remote = remote;
            return this;
        }

        public Builder setResolution(int width, int height) {
            config.width = width;
            config.height = height;
            return this;
        }

        public Builder setRefreshRate(int refreshRate) {
            config.refreshRate = refreshRate;
            return this;
        }

        public Builder setBitrate(int bitrate) {
            config.bitrate = bitrate;
            return this;
        }

        public Builder setEnableSops(boolean enable) {
            config.sops = enable;
            return this;
        }

        public Builder enableAdaptiveResolution(boolean enable) {
            config.enableAdaptiveResolution = enable;
            return this;
        }

        public Builder enableLocalAudioPlayback(boolean enable) {
            config.playLocalAudio = enable;
            return this;
        }

        public Builder setMaxPacketSize(int maxPacketSize) {
            config.maxPacketSize = maxPacketSize;
            return this;
        }

        public Builder setAudioConfiguration(int audioConfig) {
            if (audioConfig == AUDIO_CONFIGURATION_STEREO) {
                config.audioChannelCount = CHANNEL_COUNT_STEREO;
                config.audioChannelMask = CHANNEL_MASK_STEREO;
            } else if (audioConfig == AUDIO_CONFIGURATION_5_1) {
                config.audioChannelCount = CHANNEL_COUNT_5_1;
                config.audioChannelMask = CHANNEL_MASK_5_1;
            } else {
                throw new IllegalArgumentException("Invalid audio configuration");
            }

            return this;
        }

        public Builder setHevcSupported(boolean supportsHevc) {
            config.supportsHevc = supportsHevc;
            return this;
        }

        public StreamConfiguration build() {
            return config;
        }
    }

    private StreamConfiguration() {
        // Set default attributes
        app = new NvApp("Steam");
        width = 1280;
        height = 720;
        refreshRate = 60;
        bitrate = 10000;
        maxPacketSize = 1024;
        sops = true;
        enableAdaptiveResolution = false;
        audioChannelCount = CHANNEL_COUNT_STEREO;
        audioChannelMask = CHANNEL_MASK_STEREO;
        supportsHevc = false;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRefreshRate() {
        return refreshRate;
    }

    public int getBitrate() {
        return bitrate;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public NvApp getApp() {
        return app;
    }

    public boolean getSops() {
        return sops;
    }

    public boolean getAdaptiveResolutionEnabled() {
        return enableAdaptiveResolution;
    }

    public boolean getPlayLocalAudio() {
        return playLocalAudio;
    }

    public boolean getRemote() {
        return remote;
    }

    public int getAudioChannelCount() {
        return audioChannelCount;
    }

    public int getAudioChannelMask() {
        return audioChannelMask;
    }

    public boolean getHevcSupported() {
        return supportsHevc;
    }
}
