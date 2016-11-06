package kr.motd.gleamstream;

import static kr.motd.gleamstream.Panic.panic;

import java.io.IOException;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

public class OsdAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private final PatternLayoutEncoder encoder = new PatternLayoutEncoder();

    @Override
    public void start() {
        super.start();
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %msg%n");
        encoder.setContext(getContext());
        try {
            encoder.init(Osd.outputStream());
        } catch (IOException e) {
            throw panic("Failed to initialize the log encoder:", e);
        }
        encoder.start();
    }

    @Override
    protected void append(ILoggingEvent evt) {
        try {
            encoder.doEncode(evt);
        } catch (IOException e) {
            throw panic("Failed to write a log message:", e);
        }
    }
}
