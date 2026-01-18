package com.sumo.log; // Ensure the class is in this package

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import javax.swing.*;
import java.io.Serializable;

// Custom appender for redirecting log messages to Swing JTextArea in GUI
public class TextAreaAppender extends AbstractAppender {
    // Static reference to the JTextArea component in the GUI where logs will be displayed
    private static JTextArea logTextArea;

    /**
     * Constructor for TextAreaAppender
     * @param name Name of the appender
     * @param filter Log filter (can be null)
     * @param layout Log message layout (defines log format)
     */
    public TextAreaAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        // Call parent constructor: name, filter, layout, ignoreExceptions (set to false)
        super(name, filter, layout, false);
    }

    /**
     * Sets the target JTextArea component from the GUI
     * @param textArea The JTextArea where logs should be displayed
     */
    public static void setLogTextArea(JTextArea textArea) {
        logTextArea = textArea;
    }

    /**
     * Overrides the append method to process log events
     * @param event The log event containing the message to be displayed
     */
    @Override
    public void append(LogEvent event) {
        // Return if the target text area is not initialized
        if (logTextArea == null) return;

        // Get formatted log message using the specified layout
        String logMessage = getLayout().toSerializable(event).toString();

        // Update Swing component on Event Dispatch Thread to ensure thread safety
        SwingUtilities.invokeLater(() -> {
            // Append the log message to the text area
            logTextArea.append(logMessage + "\n");
            // Scroll to the bottom to show the latest log
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }
}