package kr.motd.gleamstream;

import static org.lwjgl.nuklear.Nuklear.NK_TEXT_ALIGN_LEFT;
import static org.lwjgl.nuklear.Nuklear.NK_TEXT_ALIGN_MIDDLE;
import static org.lwjgl.nuklear.Nuklear.NK_TEXT_CENTERED;
import static org.lwjgl.nuklear.Nuklear.NK_WINDOW_NO_SCROLLBAR;
import static org.lwjgl.nuklear.Nuklear.NK_WINDOW_TITLE;
import static org.lwjgl.nuklear.Nuklear.nk_begin;
import static org.lwjgl.nuklear.Nuklear.nk_end;
import static org.lwjgl.nuklear.Nuklear.nk_input_scroll;
import static org.lwjgl.nuklear.Nuklear.nk_label;
import static org.lwjgl.nuklear.Nuklear.nk_layout_row_dynamic;
import static org.lwjgl.nuklear.Nuklear.nk_rect;
import static org.lwjgl.nuklear.Nuklear.nk_text;
import static org.lwjgl.nuklear.Nuklear.nk_window_get_content_region;
import static org.lwjgl.nuklear.Nuklear.nk_window_get_panel;
import static org.lwjgl.nuklear.Nuklear.nk_window_set_position;
import static org.lwjgl.nuklear.Nuklear.nk_window_set_size;
import static org.lwjgl.system.MemoryStack.stackPush;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

import org.lwjgl.nuklear.NkContext;
import org.lwjgl.nuklear.NkPanel;
import org.lwjgl.nuklear.NkRect;
import org.lwjgl.nuklear.NkVec2;
import org.lwjgl.system.MemoryStack;

import com.google.common.math.DoubleMath;

public final class Osd {

    public static final Osd INSTANCE = new Osd();

    private static final int MAX_LOG_LINES = 128;
    private static final String[] PROGRESS_DOTS = {
            ".   ",
            " .  ",
            "  . ",
            "   ."
    };

    private final Deque<String> logLines = new ArrayDeque<>(MAX_LOG_LINES);
    private final String[] logLineArray = new String[MAX_LOG_LINES];
    private final OutputStream outputStream = new LogLineOutputStream();
    private long lastProgressUpdateTime;
    private String progressText;
    private int progressDotIdx;
    private boolean wasFollowing = true;

    private Osd() {}

    public OutputStream outputStream() {
        return outputStream;
    }

    public synchronized void setProgress(String progressText) {
        assert progressText != null;
        lastProgressUpdateTime = 0;
        progressDotIdx = 0;
        this.progressText = progressText;
    }

    public synchronized void setStatus(String statusText) {
        assert statusText != null;
        progressText = statusText;
        progressDotIdx = -1;
    }

    public synchronized void clear() {
        progressText = null;
    }

    public void follow() {
        wasFollowing = true;
    }

    public void layout(NuklearHelper nk, int width, int height) {
        final int lineHeight = nk.lineHeight();
        final String progressText;
        final int progressDotIdx;

        synchronized (this) {
            progressText = this.progressText;
            if (progressText != null && this.progressDotIdx >= 0) {
                final long currentTime = System.nanoTime();
                if (lastProgressUpdateTime == 0) {
                    lastProgressUpdateTime = currentTime;
                } else if (currentTime - lastProgressUpdateTime > 1000000000) {
                    lastProgressUpdateTime = currentTime;
                    this.progressDotIdx = (this.progressDotIdx + 1) % PROGRESS_DOTS.length;
                }
            }
            progressDotIdx = this.progressDotIdx;
        }

        final NkContext ctx = nk.ctx();
        try (MemoryStack stack = stackPush()) {
            final int y;
            if (progressText != null) {
                drawProgress(ctx, stack, width, lineHeight * 3, progressText, progressDotIdx);
                y = lineHeight * 3;
            } else {
                y = 0;
            }
            drawLogLines(ctx, stack, y, width, height, lineHeight);
        }
    }

    private static void drawProgress(NkContext ctx, MemoryStack stack, int width, int height,
                                     String progressText, int progressDotIdx) {
        NkRect rect = NkRect.mallocStack(stack);
        if (nk_begin(ctx, "Progress", nk_rect(0, 0, width, height, rect), NK_WINDOW_NO_SCROLLBAR)) {
            NkRect contentRect = NkRect.mallocStack(stack);
            nk_window_get_content_region(ctx, contentRect);
            nk_layout_row_dynamic(ctx, height, 1);
            final String text;
            if (progressDotIdx >= 0) {
                text = progressText + ' ' + PROGRESS_DOTS[progressDotIdx];
            } else {
                text = progressText;
            }
            nk_label(ctx, text, NK_TEXT_CENTERED);
        }
        nk_end(ctx);
    }

    private void drawLogLines(NkContext ctx, MemoryStack stack, int y, int width, int height, int lineHeight) {
        NkRect rect = NkRect.mallocStack(stack);
        if (nk_begin(ctx, "Log messages", nk_rect(0, y, width, height - y, rect), NK_WINDOW_TITLE)) {
            if (!DoubleMath.fuzzyEquals(rect.y(), y, 0.5)) {
                NkVec2 position = NkVec2.mallocStack(stack).set(0, y);
                NkVec2 size = NkVec2.mallocStack(stack).set(width, height - y);
                nk_window_set_position(ctx, position);
                nk_window_set_size(ctx, size);
            }

            final int numLogLines;
            synchronized (logLines) {
                numLogLines = logLines.size();
                logLines.toArray(logLineArray);
            }

            final float spacing = ctx.style().window().spacing().y();
            final NkPanel panel = nk_window_get_panel(ctx);
            nk_layout_row_dynamic(ctx, lineHeight, 1);
            for (String l : logLineArray) {
                if (l == null) {
                    break;
                }

                nk_text(ctx, l, NK_TEXT_ALIGN_LEFT | NK_TEXT_ALIGN_MIDDLE);
            }

            final int maxOffsetY = (int) ((lineHeight + spacing) * numLogLines - panel.bounds().h());

            if (ctx.input().mouse().scroll_delta() > 0) {
                wasFollowing = false;
            } else if (!wasFollowing) {
                wasFollowing = panel.offset().y() >= maxOffsetY;
            } else {
                nk_input_scroll(ctx, -maxOffsetY);
            }
        }
        nk_end(ctx);
    }

    private final class LogLineOutputStream extends OutputStream {

        private final byte[] buf = new byte[1024];
        private int cnt;

        @Override
        public synchronized void write(byte[] buf, int off, int len) {
            final int endOffset = off + len;
            for (int i = off; i < endOffset; i++) {
                write0(buf[i]);
            }
        }

        @Override
        public synchronized void write(int b) {
            write0(b);
        }

        private void write0(int b) {
            if (cnt == buf.length) {
                return;
            }

            if (b == '\n') {
                final String line = cnt != 0 ? new String(buf, 0, cnt, StandardCharsets.US_ASCII) : "";
                synchronized (logLines) {
                    if (logLines.size() == MAX_LOG_LINES) {
                        logLines.removeFirst();
                    }
                    logLines.addLast(line);
                }
                cnt = 0;
            } else if (b != '\r') {
                buf[cnt++] = (byte) b;
            }
        }
    }
}
