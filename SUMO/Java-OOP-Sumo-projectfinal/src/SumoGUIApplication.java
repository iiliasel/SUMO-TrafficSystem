import javax.swing.*;
import org.apache.logging.log4j.LogManager;
import com.sumo.log.TextAreaAppender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Entry point of SUMO Simulation Control System
 * Initializes GUI and configures log4j2 text area appender for real-time log display
 */
public class SumoGUIApplication {
    // Global logger instance for application-level logging
    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(SumoGUIApplication.class);

    public static void main(String[] args) {
        // Configure log4j2 with external XML configuration file
        System.setProperty("log4j.configurationFile", "SUMO-TrafficSystem-main/SUMO/resources/log4j2.xml");
        // Log application startup event
        logger.info("SumoGUIApplication started");

        // Register custom TextArea appender for real-time log display in GUI
        registerTextAreaAppender();

        // Initialize and display main GUI window in Swing's event dispatch thread (thread-safe)
        SwingUtilities.invokeLater(() -> {
            // Create main application window instance
            SumoMainFrame mainFrame = new SumoMainFrame();
            // Bind log text area to custom appender (for real-time log output)
            TextAreaAppender.setLogTextArea(mainFrame.getLogArea());

            // Configure main window properties
            mainFrame.setTitle("SUMO Simulation Control System");
            mainFrame.setSize(1200, 800); // Optimized size for 3-section 2-column layout
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Exit app when window closed
            mainFrame.setLocationRelativeTo(null); // Center window on screen
            mainFrame.setVisible(true); // Make window visible
        });
    }

    /**
     * Register custom TextAreaAppender to redirect log4j2 output to GUI text area
     * Enables real-time log display in the application window
     */
    private static void registerTextAreaAppender() {
        // Get log4j2 context (non-web app, false = do not use web context)
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

        // Create log pattern layout (format: time [level] message + newline)
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%d{HH:mm:ss} [%level] %msg%n") // Define log output format
                .withConfiguration(config) // Associate with log4j2 config
                .build();

        // Create custom appender for logging to Swing TextArea
        TextAreaAppender textAreaAppender = new TextAreaAppender("TextAreaAppender", null, layout);
        textAreaAppender.start(); // Activate the appender
        config.addAppender(textAreaAppender); // Add appender to log4j2 config

        // Attach appender to root logger (all log levels will be captured)
        org.apache.logging.log4j.core.Logger rootLogger = context.getRootLogger();
        rootLogger.addAppender(textAreaAppender);

        // Update log4j2 configuration to apply changes
        context.updateLoggers(config);
    }
}