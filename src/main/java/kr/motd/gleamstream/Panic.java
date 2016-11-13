package kr.motd.gleamstream;

import java.util.regex.Pattern;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;

public final class Panic {

    private static final Logger logger = LoggerFactory.getLogger(Panic.class);
    private static final Pattern TAB_PATTERN = Pattern.compile("\t", Pattern.LITERAL);
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\r?\n");

    private static volatile boolean guiEnabled;

    public static void enableGui() {
        guiEnabled = true;
    }

    public static RuntimeException panic(String message, Throwable cause) {
        final MainWindow window = MainWindow.current();
        if (window != null) {
            window.destroy();
        }

        if (guiEnabled) {
            TinyFileDialogs.tinyfd_messageBox("Error", toString(message, cause),
                                              "ok", "error", true);
        } else if (cause != null) {
            logger.error(message, cause);
        } else {
            logger.error(message);
        }

        System.exit(1);

        return new RuntimeException("panic!") {
            private static final long serialVersionUID = -8741249797974207645L;

            @Override
            public Throwable fillInStackTrace() {
                return this;
            }
        };
    }

    public static RuntimeException panic(Throwable cause) {
        return panic("Unexpected exception from " + Thread.currentThread().getName() + ':', cause);
    }

    public static RuntimeException panic(String message) {
        return panic(message, new IllegalStateException("panic!"));
    }

    public static RuntimeException panicWithoutTrace(String message) {
        return panic(message, null);
    }

    private static String toString(String message, Throwable cause) {
        final String messageWithTrace;
        if (cause != null) {
            messageWithTrace = TAB_PATTERN.matcher(message + '\n' + Throwables.getStackTraceAsString(cause))
                                          .replaceAll("    ");
        } else {
            messageWithTrace = message;
        }

        final String[] lines = NEWLINE_PATTERN.split(messageWithTrace);
        final StringBuilder buf = new StringBuilder();
        final int end = Math.min(lines.length, 10);
        for (int i = 0;;) {
            buf.append(lines[i]);
            if (++i == end) {
                break;
            }
            buf.append(System.lineSeparator());
        }

        return buf.toString();
    }

    private Panic() {}
}
