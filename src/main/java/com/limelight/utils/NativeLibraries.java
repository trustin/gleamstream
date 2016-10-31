package com.limelight.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.motd.maven.os.Detector;

public final class NativeLibraries {

    private static final Logger logger = LoggerFactory.getLogger(NativeLibraries.class);

    private static final DefaultDetector detector = new DefaultDetector();

    public static void load(String name) {
        final String resourcePath = '/' + detector.detect() + '/' + System.mapLibraryName(name);
        final InputStream in = NativeLibraries.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("cannot find a native library: " + resourcePath);
        }

        try {
            final Path path;
            try {
                path = Files.createTempFile("gleamstream-", '-' + System.mapLibraryName(name));
                path.toFile().deleteOnExit();
            } catch (IOException e) {
                throw new IllegalStateException(
                        "cannot create a temporary file for a native library: " + resourcePath, e);
            }

            try (OutputStream out = new FileOutputStream(path.toFile())) {
                final byte[] buf = new byte[8192];
                for (;;) {
                    final int numBytes = in.read(buf);
                    if (numBytes < 0) {
                        break;
                    }
                    if (numBytes > 0) {
                        out.write(buf, 0, numBytes);
                    }
                }

            } catch (IOException e) {
                throw new IllegalStateException(
                        "cannot write a temporary file for a native library: " + resourcePath, e);
            }

            System.load(path.toString());
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                logger.warn("Failed to close a stream: {}", resourcePath, e);
            }
        }
    }

    private NativeLibraries() {}

    private static class DefaultDetector extends Detector {

        String detect() {
            Properties props = new Properties();
            detect(props, Collections.emptyList());
            return props.getProperty("os.detected.classifier");
        }

        @Override
        protected void log(String msg) {
            logger.debug(msg);
        }

        @Override
        protected void logProperty(String name, String value) {
            logger.debug("{}: {}", name, value);
        }
    }
}
