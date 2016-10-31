package com.limelight.gui;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;

import com.limelight.Main;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.GfeHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.settings.PreferencesManager;

/**
 * Author: spartango
 * Date: 2/2/14
 * Time: 3:02 PM.
 */
public class AppsFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(AppsFrame.class);

    // Connection to the host
    private NvHTTP httpConnection;
    private final String host;

    private final Map<String, NvApp> apps;

    // UI Elements
    private JComboBox<String> appSelector;
    private JButton launchButton;
    private JButton quitButton;

    private void fetchAppList() {
        SwingWorker<LinkedList<NvApp>, Void> fetchBg = new SwingWorker<LinkedList<NvApp>, Void>() {
            @Override
            protected LinkedList<NvApp> doInBackground() throws Exception {
                return fetchApps();
            }

            @Override
            protected void done() {
                try {
                    LinkedList<NvApp> fetched = get();
                    if (fetched == null) {
                        return;
                    }

                    // Sort the fetched list alphabetically by app name
                    fetched.sort(Comparator.comparing(NvApp::getAppName));

                    appSelector.removeAllItems();

                    for (NvApp app : fetched) {
                        apps.put(app.getAppName(), app);
                        appSelector.addItem(app.getAppName());
                    }

                    quitButton.setEnabled(false);
                    for (NvApp app : fetched) {
//                    	if (app.getIsRunning()) {
//                            appSelector.setSelectedItem(app.getAppName());
//                            quitButton.setEnabled(true);
//                    	}
                    }
                } catch (InterruptedException e) {
                    logger.warn("Failed to get list of apps; interrupted by " + e);
                } catch (ExecutionException e) {
                    logger.warn("Failed to get list of apps; broken by " + e);
                }
            }
        };
        fetchBg.execute();
    }

    public AppsFrame(String host) {
        super("Apps on " + host);
        this.host = host;

        apps = new HashMap<>();

        setSize(400, 115);
        setResizable(false);
    }

    /**
     * Constructs all components of the frame and makes the frame visible to the user.
     */
    public void build() {
        try {
            httpConnection = new NvHTTP(InetAddress.getByName(host),
                                        PreferencesManager.getPreferences().getUniqueId(),
                                        PlatformBinding.getCryptoProvider());
        } catch (UnknownHostException e) {
            JOptionPane.showMessageDialog(null, "Unable to resolve machine address",
                                          "Moonlight", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        appSelector = new JComboBox<>();
        appSelector.addItem("Loading apps...");

        appSelector.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && appSelector.getSelectedIndex() != -1) {
                NvApp app = apps.get(appSelector.getSelectedItem());
//                    if (app.getIsRunning()) {
//                    	launchButton.setText("Resume");
//                    }
//                    else {
                launchButton.setText("Launch");
//                    }
            }
        });

        // Asynchronously fetch app list
        fetchAppList();

        launchButton = new JButton("Launch");
        launchButton.addActionListener(e -> {
            String appName = (String) appSelector.getSelectedItem();
            NvApp app = apps.get(appName);
            if (app != null) {
                launchApp(app.getAppName());
            } else {
                launchApp("Steam");
            }
        });

        quitButton = new JButton("Quit Running App");
        quitButton.addActionListener(arg0 -> quitApp());
        quitButton.setEnabled(false);

        getRootPane().setDefaultButton(launchButton);

        Box appSelectorBox = Box.createHorizontalBox();
        appSelectorBox.add(Box.createHorizontalGlue());
        appSelectorBox.add(appSelector);
        appSelectorBox.add(Box.createHorizontalGlue());

        Box launchBox = Box.createHorizontalBox();
        launchBox.add(Box.createHorizontalGlue());
        launchBox.add(launchButton);
        launchBox.add(Box.createHorizontalStrut(5));
        launchBox.add(quitButton);
        launchBox.add(Box.createHorizontalGlue());

        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(appSelectorBox);
        mainPanel.add(Box.createVerticalStrut(5));
        mainPanel.add(launchBox);
        mainPanel.add(Box.createVerticalGlue());

        getContentPane().add(mainPanel);

        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        //center on screen
        setLocation((int) dim.getWidth() / 2 - getWidth() / 2,
                         (int) dim.getHeight() / 2 - getHeight() / 2);

        setVisible(true);
    }

    private LinkedList<NvApp> fetchApps() {
        // List out the games that are installed
        try {
            return httpConnection.getAppList();
        } catch (GfeHttpResponseException e) {
            if (e.getErrorCode() == 401) {
                Main.displayUiMessage(null, "Not paired with computer",
                                      "Moonlight", JOptionPane.ERROR_MESSAGE);
            } else {
                Main.displayUiMessage(null, "GFE error: " + e.getErrorMessage() + " (Error Code: " + e
                                                   .getErrorCode() + ')',
                                      "Moonlight", JOptionPane.ERROR_MESSAGE);
            }
            setVisible(false);
        } catch (Exception e) {
            Main.displayUiMessage(null, "Unable to retrieve app list",
                                  "Moonlight", JOptionPane.ERROR_MESSAGE);
            setVisible(false);
        }

        return null;
    }

    private void launchApp(String appName) {
        setVisible(false);
        Main.createInstance(host, appName);
    }

    private void quitApp() {
        boolean quit = false;

        try {
            quit = httpConnection.quitApp();
        } catch (IOException | XmlPullParserException e) {
            e.printStackTrace();
        }

        if (quit) {
            // Update the app list again
            fetchAppList();

            Main.displayUiMessage(null, "Successfully quit app",
                                  "Moonlight", JOptionPane.INFORMATION_MESSAGE);
        } else {
            Main.displayUiMessage(null, "Failed to quit app",
                                  "Moonlight", JOptionPane.ERROR_MESSAGE);
        }
    }
}
