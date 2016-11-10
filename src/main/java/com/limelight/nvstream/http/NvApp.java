package com.limelight.nvstream.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NvApp {

    private static final Logger logger = LoggerFactory.getLogger(NvApp.class);

    private String appName = "";
    private int appId;
    private boolean initialized;

    public NvApp() {}

    public NvApp(String appName) {
        this.appName = appName;
    }

    public NvApp(String appName, int appId) {
        this.appName = appName;
        this.appId = appId;
        initialized = true;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public void setAppId(String appId) {
        try {
            setAppId(Integer.parseInt(appId));
        } catch (NumberFormatException e) {
            logger.warn("Malformed app ID: " + appId, e);
        }
    }

    public void setAppId(int appId) {
        this.appId = appId;
        initialized = true;
    }

    public String getAppName() {
        return appName;
    }

    public int getAppId() {
        return appId;
    }

    public boolean isInitialized() {
        return initialized;
    }
}
