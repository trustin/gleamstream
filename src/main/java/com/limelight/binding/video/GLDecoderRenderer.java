package com.limelight.binding.video;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_STENCIL_BUFFER_BIT;

import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.util.Map.Entry;
import java.util.Queue;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.avutil.AVFrame;
import org.jctools.queues.SpscArrayQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.limelight.gui.RenderPanel;
import com.limelight.gui.StreamFrame;
import com.limelight.nvstream.av.video.VideoDepacketizer;

/**
 * Author: spartango
 * Date: 2/1/14
 * Time: 11:42 PM.
 */
public class GLDecoderRenderer extends AbstractCpuDecoder implements GLEventListener {

    private static final Logger logger = LoggerFactory.getLogger(GLDecoderRenderer.class);

    private final GLCanvas glcanvas;
    private FPSAnimator animator;
    private final TextRenderer textRenderer;
    private String statusText = "";
    private boolean keepAspectRatio;
    private final Queue<Entry<AVFrame, BytePointer>> pendingFrames = new SpscArrayQueue<>(64);

    private long lastFpsCheckTime;
    private int frameCounter;
    private int droppedFrameCounter;
    private long renderTimeCounter;

    public GLDecoderRenderer() {
        GLProfile.initSingleton();
        GLProfile glprofile = GLProfile.getDefault();
        GLCapabilities glcapabilities = new GLCapabilities(glprofile);
        glcanvas = new GLCanvas(glcapabilities);
        textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 24),
                                        true, true);
    }

    @Override
    public boolean setupInternal(Object renderTarget, int drFlags) {
        final StreamFrame frame = (StreamFrame) renderTarget;
        final RenderPanel renderingSurface = frame.getRenderingSurface();

        keepAspectRatio = frame.getUserPreferences().isKeepAspectRatio();

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                glcanvas.setSize(renderingSurface.getSize());
            }
        });

        glcanvas.setSize(renderingSurface.getSize());
        glcanvas.addGLEventListener(this);

        for (MouseListener m : renderingSurface.getMouseListeners()) {
            glcanvas.addMouseListener(m);
        }

        for (KeyListener k : renderingSurface.getKeyListeners()) {
            glcanvas.addKeyListener(k);
        }

        for (MouseWheelListener w : renderingSurface.getMouseWheelListeners()) {
            glcanvas.addMouseWheelListener(w);
        }

        for (MouseMotionListener m : renderingSurface.getMouseMotionListeners()) {
            glcanvas.addMouseMotionListener(m);
        }

        frame.setLayout(null);
        frame.add(glcanvas, 0, 0);
        glcanvas.setCursor(frame.getCursor());

        animator = new FPSAnimator(glcanvas, targetFps * 2);

        logger.info("Using OpenGL rendering");

        return true;
    }

    @Override
    public boolean start(VideoDepacketizer depacketizer) {
        if (!super.start(depacketizer)) {
            return false;
        }

        animator.start();
        //animator.setUpdateFPSFrames(targetFps, System.out);
        return true;
    }

    /**
     * Stops the decoding and rendering of the video stream.
     */
    @Override
    public void stop() {
        super.stop();
        animator.stop();
    }

    @Override
    public void reshape(GLAutoDrawable glautodrawable, int x, int y, int viewportWidth, int viewportHeight) {
        GL2 gl = glautodrawable.getGL().getGL2();

        float zoomX = (float) viewportWidth / width;
        float zoomY = (float) viewportHeight / height;

        if (keepAspectRatio) {
            zoomX = zoomY = Math.min(zoomX, zoomY);
        }

        gl.glViewport(x, y, viewportWidth, viewportHeight);
        gl.glRasterPos2f(-zoomX * width / viewportWidth, zoomY * height / viewportHeight);
        gl.glPixelZoom(zoomX, -zoomY);
        gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    }

    @Override
    public void init(GLAutoDrawable glautodrawable) {
        lastFpsCheckTime = System.nanoTime();
        GL2 gl = glautodrawable.getGL().getGL2();

        gl.glDisable(GL.GL_DITHER);
        gl.glDisable(GL.GL_MULTISAMPLE);
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.setSwapInterval(1);
    }

    @Override
    public void dispose(GLAutoDrawable glautodrawable) {
    }

    @Override
    public void display(GLAutoDrawable glautodrawable) {
        final int numFrames = pendingFrames.size();
        if (numFrames == 0) {
            return;
        }

        if (numFrames > 2) {
            for (int i = 1; i < numFrames; i++) {
                framePool.release(pendingFrames.poll());
                droppedFrameCounter++;
            }
        }

        final Entry<AVFrame, BytePointer> e = pendingFrames.poll();
        final long renderStartTime = System.nanoTime();

        GL2 gl = glautodrawable.getGL().getGL2();
        gl.glDrawPixels(width, height, GL.GL_BGRA, GL.GL_UNSIGNED_BYTE, e.getValue().asByteBuffer());
        framePool.release(e);
        drawStatusText();

        final long currentTime = System.nanoTime();
        final long elapsedTime = currentTime - lastFpsCheckTime;

        frameCounter++;
        renderTimeCounter += currentTime - renderStartTime;
        final long interval = 1000000000L; // 1 second
        if (elapsedTime > interval) {
            statusText = String.format("fps: %2.2f, drops/s: %2.2f, ms/f: %2.2f",
                                       frameCounter * 1000000000.0 / elapsedTime,
                                       droppedFrameCounter * 1000000000.0 / elapsedTime,
                                       renderTimeCounter / 1000000.0 / frameCounter);
            frameCounter = 0;
            droppedFrameCounter = 0;
            renderTimeCounter = 0;
            lastFpsCheckTime = currentTime;
        }
    }

    private void drawStatusText() {
        textRenderer.beginRendering(width, height);
        int x = 7;
        int y = height - 26;
        textRenderer.setColor(0f, 0f, 0f, 0.6f);
        textRenderer.draw(statusText, x, y);
        x -= 2;
        y += 2;
        textRenderer.setColor(1.0f, 1.0f, 1.0f, 0.6f);
        textRenderer.draw(statusText, x, y);
        textRenderer.endRendering();
    }

    @Override
    protected void drawFrame(Entry<AVFrame, BytePointer> e) {
        if (!pendingFrames.add(e)) {
            framePool.release(e);
        }
    }
}

