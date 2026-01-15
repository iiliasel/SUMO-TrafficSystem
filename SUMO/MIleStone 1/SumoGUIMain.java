/*
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.Vehicle;
 */
import org.eclipse.sumo.libtraci.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;

public class SumoGUIMain extends JFrame {
    // UI controls
    private JTextField configPathField; //configuration file path
    private JButton selectBtn, connectBtn, stepBtn, closeBtn;//various buttons
    private JLabel tlCountLabel, vehicleCountLabel; // data presentation

    // some SUMO paraments
    private String sumoGuiPath = "D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe";
    private String configPath;
    private boolean isConnected = false;

    //constructor
    public SumoGUIMain() {
        // initialize window properties and interface components
        setTitle("SUMO Dashboard");                 // title
        setSize(600, 400);            // size
        setDefaultCloseOperation(EXIT_ON_CLOSE);    // exit
        setLocationRelativeTo(null);                // center the window
        initComponents();                            //calling functions to  construct and initialize interface components
    }

    // construct and initialize interface components
    private void initComponents() {
        // main panel(grid layout, 5 rows and 1 column)
        JPanel mainPanel = new JPanel(new GridLayout(5, 1, 10, 10)); // grid layout, 5 rows and 1 column，
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // set an outer border for the main panel,10 px from the edge of the container

        // 1. configuration file selection area
        JPanel configPanel = new JPanel(new BorderLayout(5, 5)); // use BorderLayout，with a 5px gap between child components
        configPathField = new JTextField(); // a text field to display the path to the selected configuration file
        configPathField.setEditable(false); // only read permit
        selectBtn = new JButton("select file(.sumocfg)"); // prompting user to select "sumocfg" file
        configPanel.add(selectBtn, BorderLayout.WEST); // put the button on the left area
        configPanel.add(configPathField, BorderLayout.CENTER); // put text ont the center
        mainPanel.add(configPanel); // add the configuration area to the first row of the main panel

        // 2. data display area(number of traffic lights and vehicles)
        JPanel dataPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5)); // using left-aligned flow layout
        tlCountLabel = new JLabel("The number of trafficlight：--");
        vehicleCountLabel = new JLabel("The number of vehicle：--");
        dataPanel.add(tlCountLabel);
        dataPanel.add(vehicleCountLabel);
        mainPanel.add(dataPanel);

        // 3. control buttons area
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        connectBtn = new JButton("connect SUMO");//connect button
        stepBtn = new JButton("single step"); //single step button
        closeBtn = new JButton("close"); //close button
        // initially disable some buttons
        stepBtn.setEnabled(false); // enables it after connection is established
        closeBtn.setEnabled(false);
        //add these buttons to btnPanel
        btnPanel.add(connectBtn);
        btnPanel.add(stepBtn);
        btnPanel.add(closeBtn);
        mainPanel.add(btnPanel);

        this.getContentPane().add(mainPanel); // calling getContentPane().add(...),add the main panel to current container
        bindEvents(); // calling functions to bin button events


    }

    // bind button events
    private void bindEvents() {
        selectBtn.addActionListener((ActionEvent e) -> {
            // when click on the select button，a file selection dialog box will pop uo
            JFileChooser chooser = new JFileChooser();
            // display the open dialog box
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                // after selecting a file,the absolute path will be assigned to configPath
                configPath = chooser.getSelectedFile().getAbsolutePath();
                // display the path in text box
                configPathField.setText(configPath);
            }
        });

        // event binding part about connecting SUMO button
        connectBtn.addActionListener((ActionEvent e) -> {
            // construct the command parameters to start SUMO
            String[] args = {sumoGuiPath, "-c", configPath};
            // preload necessary libraries
            Simulation.preloadLibraries();
            // start SUMO GUI simulation, pass in parameters
            Simulation.start(new StringVector(args));
            isConnected = true; // marks as connection
            connectBtn.setEnabled(false); // disable the connect button after connection to prevent duplicate connections
            stepBtn.setEnabled(true);       // enable step-by-step button
            closeBtn.setEnabled(true);      // enable close button
            updateData(); // update the data displayed on interface
        });

        // step-by-step simulation
        stepBtn.addActionListener((ActionEvent e) -> {
            if (!isConnected) return; // if not connected return directly
            try {
                Simulation.step(); // call the step() function to advance by 1 step
                updateData(); // update data display
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // close connection
        closeBtn.addActionListener((ActionEvent e) -> {
            if (!isConnected) return; // if not connected return directly
            Simulation.close(); // close simulation connection
            isConnected = false;
            // reset button state to restore initial interface availability
            connectBtn.setEnabled(true);
            stepBtn.setEnabled(false);
            closeBtn.setEnabled(false);
            // clear data display
            tlCountLabel.setText("The number of trafficlight：--");
            vehicleCountLabel.setText("The number of vehicle：--");

        });
    }

    // update the number of traffic light and vehicle
    private void updateData() {
        // get the number of traffic lights
        List<String> tlList = TrafficLight.getIDList();
        tlCountLabel.setText("The number of trafficlight：" + tlList.size());
        // get the number of vehicles
        List<String> vehicleList = Vehicle.getIDList();
        vehicleCountLabel.setText("The number of vehicle：" + vehicleList.size());
    }


    public static void main(String[] args) {
        // start GUI
        SumoGUIMain frame = new SumoGUIMain();
        frame.setVisible(true);
    }
}