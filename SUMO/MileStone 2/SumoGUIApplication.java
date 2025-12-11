import javax.swing.*;

/**
 * SUMO simulation control startup class
 * only responsible for GUI startup and main window initialization
 */
public class SumoGUIApplication {
    public static void main(String[] args) {
        // start the GUI using Swing thread-safe method
        SwingUtilities.invokeLater(() -> {
            // main window initialization
            SumoMainFrame mainFrame = new SumoMainFrame();
            mainFrame.setTitle("SUMO Simulation Control System");
            mainFrame.setSize(1200, 800); // window size adapted to the "three-section,two column" layout
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setLocationRelativeTo(null); // center the window
            mainFrame.setVisible(true);
        });
    }
}
