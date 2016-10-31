package com.limelight.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.HashMap;
import java.util.Map.Entry;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.limelight.input.Device;
import com.limelight.input.DeviceListener;
import com.limelight.input.gamepad.GamepadComponent;
import com.limelight.input.gamepad.GamepadListener;
import com.limelight.input.gamepad.GamepadMapping;
import com.limelight.input.gamepad.GamepadMapping.Mapping;
import com.limelight.input.gamepad.SourceComponent;
import com.limelight.input.gamepad.SourceComponent.Direction;
import com.limelight.settings.GamepadSettingsManager;

/**
 * A frame used to configure the gamepad mappings.
 * @author Diego Waxemberg
 */
public class GamepadConfigFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(GamepadConfigFrame.class);

    private boolean configChanged;

    private MappingThread mappingThread;
    private final GamepadMapping config;
    private HashMap<Box, Mapping> componentMap;

    /**
     * Constructs a new config frame. The frame is initially invisible and will <br>
     * be made visible after all components are built by calling {@code build()}
     */
    public GamepadConfigFrame() {
        super("Gamepad Settings");
        logger.info("Creating Settings Frame");
        setSize(850, 550);
        setResizable(false);
        setAlwaysOnTop(true);
        config = GamepadSettingsManager.getSettings();
    }

    /**
     * Builds all components of the config frame and sets the frame visible.
     */
    public void build() {
        componentMap = new HashMap<>();

        GridLayout layout = new GridLayout(GamepadComponent.values().length / 2 + 1, 2);
        layout.setHgap(60);
        layout.setVgap(3);
        JPanel mainPanel = new JPanel(layout);

        GamepadComponent[] components = GamepadComponent.values();

        for (GamepadComponent c : components) {
            Mapping mapping = config.get(c);
            if (mapping == null) {
                mapping = config.new Mapping(c, false, false);
            }
            Box componentBox = createComponentBox(mapping);

            mainPanel.add(componentBox);
        }

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        setLocation(dim.width / 2 - getSize().width / 2, dim.height / 2 - getSize().height / 2);
        setLayout(new BorderLayout());

        getContentPane().add(mainPanel, "Center");
        getContentPane().add(Box.createVerticalStrut(20), "North");
        getContentPane().add(Box.createVerticalStrut(20), "South");
        getContentPane().add(Box.createHorizontalStrut(20), "East");
        getContentPane().add(Box.createHorizontalStrut(20), "West");

        addWindowListener(createWindowListener());
        setVisible(true);
    }

    /*
     * Creates the box that holds the button and checkboxes
     */
    private Box createComponentBox(Mapping mapping) {
        Box componentBox = Box.createHorizontalBox();

        JButton mapButton = new JButton();
        JCheckBox invertBox = new GamepadCheckBox("Invert", GamepadCheckBox.Type.INVERT);
        JCheckBox triggerBox = new GamepadCheckBox("Trigger", GamepadCheckBox.Type.TRIGGER);

        Dimension buttonSize = new Dimension(110, 24);
        mapButton.setMaximumSize(buttonSize);
        mapButton.setMinimumSize(buttonSize);
        mapButton.setPreferredSize(buttonSize);
        mapButton.addActionListener(createMapListener());

        setButtonText(mapButton, config.getMapping(mapping.padComp));

        invertBox.setSelected(mapping.invert);
        invertBox.addActionListener(createCheckboxListener());
        invertBox.setName(mapping.padComp.name());

        triggerBox.setSelected(mapping.trigger);
        triggerBox.addActionListener(createCheckboxListener());
        triggerBox.setName(mapping.padComp.name());
        triggerBox.setToolTipText("If this component should act as a trigger. (one-way axis)");

        componentBox.add(Box.createHorizontalStrut(5));
        componentBox.add(mapping.padComp.getLabel());
        componentBox.add(Box.createHorizontalGlue());
        componentBox.add(mapButton);
        componentBox.add(invertBox);
        componentBox.add(triggerBox);
        componentBox.add(Box.createHorizontalStrut(5));

        componentBox.setBorder(new LineBorder(Color.GRAY, 1, true));

        componentMap.put(componentBox, mapping);

        return componentBox;
    }

    /*
     * Creates the listener for the checkbox
     */
    private ActionListener createCheckboxListener() {
        return e -> {
            JCheckBox clicked = (JCheckBox) e.getSource();
            GamepadComponent padComp = GamepadComponent.valueOf(clicked.getName());
            Mapping currentMapping = config.get(padComp);
            if (currentMapping == null) {
                //this makes more semantic sense to me than using !=
                clicked.setSelected(!clicked.isSelected());
            } else {
                ((GamepadCheckBox) clicked).setValue(currentMapping, clicked.isSelected());
                configChanged = true;
            }
        };
    }

    /*
     * Creates the listener for the window.
     * It will save configs on exit and restart controller threads
     */
    private WindowListener createWindowListener() {
        return new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (mappingThread != null && mappingThread.isAlive()) {
                    mappingThread.interrupt();
                }
                if (configChanged) {
                    updateConfigs();
                }
                dispose();
            }
        };
    }

    /*
     * Creates the listener for the map button
     */
    private ActionListener createMapListener() {
        return e -> {
            Box toMap = (Box) ((Component) e.getSource()).getParent();

            if (GamepadListener.deviceCount() == 0) {
                JOptionPane.showMessageDialog(this, "No Gamepad Detected");
                return;
            }

            map(toMap);
        };
    }

    /*
     * Maps a gamepad component to the clicked component
     */
    private void map(final Box toMap) {
        if (mappingThread == null || !mappingThread.isAlive()) {

            //a little janky, could probably be fixed up a bit
            final JButton buttonPressed = getButton(toMap);
            final Mapping mappingToMap = componentMap.get(toMap);

            buttonPressed.setSelected(true);

            buttonPressed.setText("Select Input");

            mappingThread = new MappingThread(buttonPressed, mappingToMap);
            mappingThread.start();

            GamepadListener.addDeviceListener(mappingThread);
        }

    }

    /*
     * Helper method to get the box component that contains the given a mapping
     */
    private Box getBox(Mapping mapping) {
        for (Entry<Box, Mapping> entry : componentMap.entrySet()) {
            if (entry.getValue() == mapping) {
                return entry.getKey();
            }
        }
        return null;
    }

    /*
     * Helper method to get the button out of the box component
     */
    private static JButton getButton(Box componentBox) {
        for (Component comp : componentBox.getComponents()) {
            if (comp instanceof JButton) {
                return (JButton) comp;
            }
        }
        return null;
    }

    /*
     * Writes the current cofig to the configs on disk.
     */
    private void updateConfigs() {
        GamepadSettingsManager.writeSettings(config);
    }

    private static void setButtonText(JButton button, SourceComponent comp) {
        if (comp == null) {
            button.setText("");
        } else {
            String dir = "";
            if (comp.getDirection() == Direction.POSITIVE) {
                dir = "+";
            } else if (comp.getDirection() == Direction.NEGATIVE) {
                dir = "-";
            }
            button.setText(comp.getType().name() + ' ' + comp.getId() + ' ' + dir);
        }
    }

    private class MappingThread extends Thread implements DeviceListener {
        private SourceComponent newMapping;
        private final JButton buttonPressed;
        private final Mapping mappingToMap;

        MappingThread(JButton buttonPressed, Mapping mappingToMap) {
            super("Gamepad Mapping Thread");
            this.buttonPressed = buttonPressed;
            this.mappingToMap = mappingToMap;
        }

        @Override
        public void run() {

            while (newMapping == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    setButtonText(buttonPressed, config.getMapping(mappingToMap.padComp));
                    GamepadListener.removeListener(this);
                    buttonPressed.setSelected(false);
                    return;
                }
            }

            Mapping oldConfig = config.get(newMapping);
            if (oldConfig != null) {
                getButton(getBox(oldConfig)).setText("");
            }

            SourceComponent oldSource = config.getMapping(mappingToMap.padComp);
            if (oldSource != null) {
                config.remove(oldSource);
            }

            config.insertMapping(mappingToMap, newMapping);

            setButtonText(buttonPressed, newMapping);
            buttonPressed.setSelected(false);
            configChanged = true;

            GamepadListener.removeListener(this);

        }

        @Override
        public void handleButton(Device device, int buttonId, boolean pressed) {
            if (pressed) {
                newMapping = new SourceComponent(SourceComponent.Type.BUTTON, buttonId, null);
            }
        }

        @Override
        public void handleAxis(Device device, int axisId, float newValue,
                               float lastValue) {
            if (newValue > 0.75) {
                newMapping = new SourceComponent(SourceComponent.Type.AXIS, axisId, Direction.POSITIVE);
            } else if (newValue < -0.75) {
                newMapping = new SourceComponent(SourceComponent.Type.AXIS, axisId, Direction.NEGATIVE);
            }
        }

        @Override
        public void handleDeviceAdded(Device device) {
        }

        @Override
        public void handleDeviceRemoved(Device device) {
        }

    }

    private static class GamepadCheckBox extends JCheckBox {
        private static final long serialVersionUID = 1L;

        private enum Type {TRIGGER, INVERT}

        private final Type type;

        GamepadCheckBox(String text, Type type) {
            super(text);
            this.type = type;
        }

        public void setValue(Mapping mapping, boolean value) {
            switch (type) {
                case TRIGGER:
                    mapping.trigger = value;
                    break;
                case INVERT:
                    mapping.invert = value;
                    break;
                default:
                    logger.error("You did something terrible and should feel terrible.");
                    logger.error("Fix it or the checkbox gods will smite you!");
                    break;
            }
        }
    }
}
