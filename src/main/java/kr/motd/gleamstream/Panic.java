package kr.motd.gleamstream;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.google.common.base.Throwables;

public final class Panic {

    public static RuntimeException panic(String message, Throwable cause) {
        MainWindow.INSTANCE.destroy();

        message += '\n' + Throwables.getStackTraceAsString(cause);
        final String[] lines = message.replace(System.lineSeparator(), "\n")
                                      .replace("\t", "    ").split("\n");

        final StringBuilder buf = new StringBuilder();
        final int end = Math.min(lines.length, 10);
        for (int i = 0;;) {
            buf.append(lines[i]);
            if (++i == end) {
                break;
            }
            buf.append(System.lineSeparator());
        }

        TinyFileDialogs.tinyfd_messageBox("Error", message, "ok", "error", true);
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

    private Panic() {}
}
