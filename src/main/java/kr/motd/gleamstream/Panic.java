package kr.motd.gleamstream;

import java.util.regex.Pattern;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.google.common.base.Throwables;

public final class Panic {

    private static final Pattern TAB_PATTERN = Pattern.compile("\t", Pattern.LITERAL);
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("\r?\n");

    public static RuntimeException panic(String message, Throwable cause) {
        MainWindow.INSTANCE.destroy();

        TinyFileDialogs.tinyfd_messageBox("Error", toString(message, cause),
                                          "ok", "error", true);
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

    private static String toString(String message, Throwable cause) {
        final String messageWithTrace =
                TAB_PATTERN.matcher(message + '\n' + Throwables.getStackTraceAsString(cause))
                           .replaceAll("    ");

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
