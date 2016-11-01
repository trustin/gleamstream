package com.limelight.nvstream;

public interface NvConnectionListener {

    enum Stage {
        LAUNCH_APP,
        RTSP_HANDSHAKE,
        CONTROL_START,
        VIDEO_START,
        AUDIO_START,
        INPUT_START
    }

    void stageStarting(Stage stage);

    void stageComplete(Stage stage);

    void stageFailed(Stage stage);
}
