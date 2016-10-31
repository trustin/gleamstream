package com.limelight.input;

import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.gui.StreamFrame;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * Class that handles keyboard input
 * @author Diego Waxemberg
 */
public class KeyboardHandler extends KeyAdapter {

    private static final Logger logger = LoggerFactory.getLogger(KeyboardHandler.class);

    private final KeyboardTranslator translator;
    private final StreamFrame parent;
    private boolean mouseCaptured = true;

    /**
     * Constructs a new keyboard listener that will send key events to the specified connection
     * and belongs to the specified frame
     * @param conn the connection to send key events to
     * @param parent the frame that owns this handler
     */
    public KeyboardHandler(NvConnection conn, StreamFrame parent) {
        translator = new KeyboardTranslator(conn);
        this.parent = parent;
    }

    /**
     * Invoked when a key is pressed and will send that key-down event to the host
     * @param event the key-down event
     */
    @Override
    public void keyPressed(KeyEvent event) {
        if (event.isConsumed()) { return; }
        event.consume();

        short keyMap = translator.translate(event.getKeyCode());

        byte modifier = 0x0;

        int modifiers = event.getModifiersEx();
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            modifier |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            modifier |= KeyboardPacket.MODIFIER_CTRL;
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            modifier |= KeyboardPacket.MODIFIER_ALT;
        }

        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0 &&
            (modifiers & InputEvent.ALT_DOWN_MASK) != 0 &&
            (modifiers & InputEvent.CTRL_DOWN_MASK) != 0 &&
            event.getKeyCode() == KeyEvent.VK_Q) {
            logger.info("quitting");

            // Free mouse before closing to avoid the mouse code
            // trying to interact with the now closed streaming window.
            parent.freeMouse();

            parent.close();
            return;
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0 &&
            (modifiers & InputEvent.ALT_DOWN_MASK) != 0 &&
            (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            if (mouseCaptured) {
                parent.freeMouse();
            } else {
                parent.captureMouse();
            }
            mouseCaptured = !mouseCaptured;
            return;
        }

        translator.sendKeyDown(keyMap, modifier);
    }

    /**
     * Invoked when a key is released and will send that key-up event to the host
     * @param event the key-up event
     */
    @Override
    public void keyReleased(KeyEvent event) {
        if (event.isConsumed()) { return; }
        event.consume();

        short keyMap = translator.translate(event.getKeyCode());

        byte modifier = 0x0;

        int modifiers = event.getModifiersEx();
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            modifier |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            modifier |= KeyboardPacket.MODIFIER_CTRL;
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            modifier |= KeyboardPacket.MODIFIER_ALT;
        }

        translator.sendKeyUp(keyMap, modifier);
    }

}
