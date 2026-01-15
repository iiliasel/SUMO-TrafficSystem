import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.sumo.libtraci.Route;
import org.eclipse.sumo.libtraci.TrafficLight;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Main window of SUMO Simulation Control System
 * Implements UI layout, component initialization, event binding, and invokes business logic
 */
public class SumoMainFrame extends JFrame {
    // Business logic handler to decouple UI from core logic
    private final SumoBusinessService businessService;
    private static final Logger logger = LogManager.getLogger(SumoMainFrame.class);

    // Top menu bar components
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenuItem openConfigItem, saveDataItem, exitItem;

    // Left function panel components (config + control)
    private JPanel leftPanel, configSubPanel, controlSubPanel;
    private JLabel configPathLabel, scenarioNameLabel, sumoStatusLabel;
    private JTextField configPathField, scenarioNameField;
    private JButton selectFileBtn;
    private JLabel statusIndicator; // SUMO connection status indicator (red/green)

    // Control area components
    private JRadioButton stepModeBtn, continuousModeBtn;
    private ButtonGroup modeGroup;
    private JButton connectBtn, stepForwardBtn, startPauseBtn, resetBtn, disconnectBtn;
    private JButton addVehicleBtn;
    private JButton tlControlBtn;
    private JSlider speedSlider;
    private JLabel speedLabel;
    private JTextArea logArea;
    private JScrollPane logScroll;

    // Right main display area components
    private JPanel rightPanel, mapSubPanel, dashboardSubPanel;
    private JPanel mapCanvas; // Panel for rendering simulation map
    private JToolBar mapToolBar;
    private JButton zoomInBtn, zoomOutBtn, transBtn, resetViewBtn, showVehicleLabelBtn, showTLLabelBtn;
    private JLabel simulationTimeLabel;

    // Data dashboard components
    private JPanel vehicleCard, tlCard, statCard;
    private JLabel vehicleTotalLabel, vehicleRunningLabel, vehicleCongestedLabel;
    private JLabel tlTotalLabel, tlRedLabel, tlGreenLabel, tlYellowLabel;
    private JLabel statStepLabel, statAvgSpeedLabel, statEfficiencyLabel;

    // System configuration parameters
    private String sumoGuiPath = "C:\\Sumo\\bin\\sumo-gui.exe"; // SUMO GUI executable path
    private String configPath;
    private int traciPort = 8813;
    private boolean isConnected = false;
    private boolean isContinuousRunning = false;
    private Timer continuousTimer; // Timer for continuous simulation mode

    /**
     * Initialize main frame and all core components
     */
    public SumoMainFrame() {
        initAllComponents(); // Initialize UI components
        businessService = new SumoBusinessService(this); // Initialize business logic handler
        layoutComponents(); // Layout all UI components
        bindEvents(); // Bind event listeners to components
        initComponentStatus(); // Set initial disabled/enabled status for components
    }

    /**
     * Initialize all UI components (menu, panels, buttons)
     */
    private void initAllComponents() {
        initMenuBar(); // Initialize top menu bar
        initLeftPanel(); // Initialize left function panel
        initRightPanel(); // Initialize right display panel
        continuousTimer = new Timer(1000, this::handleContinuousStep); // 1s default interval for continuous mode
    }

    /**
     * Initialize top menu bar (File menu)
     */
    private void initMenuBar() {
        menuBar = new JMenuBar();

        // File menu with config file and exit options
        fileMenu = new JMenu("File");
        openConfigItem = new JMenuItem("Open Configuration File");
        exitItem = new JMenuItem("Exit");
        fileMenu.add(openConfigItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
    }

    /**
     * Initialize left panel (configuration + control subpanels)
     */
    private void initLeftPanel() {
        leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initConfigSubPanel(); // Initialize config subpanel (file path, scenario)
        initControlSubPanel(); // Initialize control subpanel (buttons, mode selection)

        leftPanel.add(configSubPanel, BorderLayout.NORTH);
        leftPanel.add(controlSubPanel, BorderLayout.CENTER);
    }

    /**
     * Initialize configuration subpanel (file path, scenario, status indicator)
     */
    private void initConfigSubPanel() {
        configSubPanel = new JPanel(new GridBagLayout()); // Flexible layout for precise positioning
        configSubPanel.setBorder(new TitledBorder("Configuration Area"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 5px padding for all components
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Configuration file path section
        configPathLabel = new JLabel("Config File Path：");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(configPathLabel, gbc);

        configPathField = new JTextField();
        configPathField.setEditable(false); // Read-only display of selected file path
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0; // Take remaining horizontal space
        configSubPanel.add(configPathField, gbc);

        selectFileBtn = new JButton("Select File");
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        configSubPanel.add(selectFileBtn, gbc);

        // Scenario name section
        scenarioNameLabel = new JLabel("Current Scenario：");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(scenarioNameLabel, gbc);

        scenarioNameField = new JTextField("No Scenario Selected");
        scenarioNameField.setEditable(false); // Read-only scenario name display
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        configSubPanel.add(scenarioNameField, gbc);

        initStatusIndicator(gbc); // Initialize SUMO connection status indicator
    }

    /**
     * Initialize SUMO connection status indicator (red/green circle)
     */
    private void initStatusIndicator(GridBagConstraints gbc) {
        sumoStatusLabel = new JLabel("SUMO Connection Status：");
        statusIndicator = new JLabel();
        statusIndicator.setPreferredSize(new Dimension(15, 15)); // Fixed size for circle icon
        statusIndicator.setIcon(new ImageIcon(createIndicatorIcon(Color.RED))); // Red = disconnected by default

        // Position status label and indicator
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(sumoStatusLabel, gbc);

        gbc.gridx = 1;
        configSubPanel.add(statusIndicator, gbc);
    }

    /**
     * Initialize control subpanel (connection, mode selection, buttons, log area)
     */
    private void initControlSubPanel() {
        controlSubPanel = new JPanel(new GridBagLayout());
        controlSubPanel.setBorder(new TitledBorder("Control Area"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 3;

        // Connect SUMO button (disabled until config file selected)
        connectBtn = new JButton("Connect SUMO");
        connectBtn.setEnabled(false);
        gbc.gridy = 0;
        controlSubPanel.add(connectBtn, gbc);

        // Simulation mode selection (step/continuous)
        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.WEST;
        controlSubPanel.add(new JLabel("Simulation Mode："), gbc);

        modeGroup = new ButtonGroup();
        stepModeBtn = new JRadioButton("Single Step");
        continuousModeBtn = new JRadioButton("Continuous");
        modeGroup.add(stepModeBtn);
        modeGroup.add(continuousModeBtn);
        stepModeBtn.setSelected(true); // Step mode default

        gbc.gridx = 1;
        controlSubPanel.add(stepModeBtn, gbc);
        gbc.gridx = 2;
        controlSubPanel.add(continuousModeBtn, gbc);

        // Control buttons panel (step, start/pause, reset, disconnect)
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        JPanel controlBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        stepForwardBtn = new JButton("Step Forward");
        stepForwardBtn.setEnabled(false);
        startPauseBtn = new JButton("Start");
        startPauseBtn.setEnabled(false);
        resetBtn = new JButton("Reset");
        resetBtn.setEnabled(false);
        disconnectBtn = new JButton("Close");
        disconnectBtn.setEnabled(false);

        addVehicleBtn = new JButton("Add Vehicle");
        addVehicleBtn.setEnabled(false);

        tlControlBtn = new JButton("TL Controls");
        tlControlBtn.setEnabled(false);

        controlBtnPanel.add(stepForwardBtn);
        controlBtnPanel.add(startPauseBtn);
        controlBtnPanel.add(resetBtn);
        controlBtnPanel.add(disconnectBtn);
        controlBtnPanel.add(addVehicleBtn);
        controlBtnPanel.add(tlControlBtn);

        controlSubPanel.add(controlBtnPanel, gbc);

        // Simulation speed slider (1-10 levels)
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        speedLabel = new JLabel("Simulation Speed：Level 1");
        controlSubPanel.add(speedLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        speedSlider = new JSlider(1, 10, 1);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setEnabled(false); // Disabled until connected
        controlSubPanel.add(speedSlider, gbc);

        // Operation log area (read-only text area with scroll)
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0; // Take remaining vertical space
        logArea = new JTextArea(8, 20);
        logArea.setEditable(false);
        logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Operation Log"));
        controlSubPanel.add(logScroll, gbc);
    }

    /**
     * Create circular status indicator icon (red/green for disconnected/connected)
     */
    private BufferedImage createIndicatorIcon(Color color) {
        BufferedImage icon = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();

        // Draw filled circle with black border
        g2d.setColor(color);
        g2d.fillOval(0, 0, 15, 15);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(0, 0, 14, 14); // 14px to fit within 15px bounds
        g2d.dispose();

        return icon;
    }

    /**
     * Initialize right panel (map display + data dashboard)
     */
    private void initRightPanel() {
        rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Map subpanel (toolbar + canvas)
        mapSubPanel = new JPanel(new BorderLayout());
        mapSubPanel.setBorder(new TitledBorder("Simulation Map"));

        // Map toolbar (zoom, translate, labels)
        mapToolBar = new JToolBar();
        mapToolBar.setFloatable(false); // Disable toolbar dragging

        zoomInBtn = new JButton("Zoom In");
        zoomOutBtn = new JButton("Zoom Out");
        transBtn = new JButton("Translate");
        resetViewBtn = new JButton("Reset View");
        showVehicleLabelBtn = new JButton("Show Vehicle Label");
        showTLLabelBtn = new JButton("Show Traffic Light Status");

        setMapToolsEnabled(false); // Disabled until connected

        mapToolBar.add(zoomInBtn);
        mapToolBar.add(zoomOutBtn);
        mapToolBar.add(transBtn);
        mapToolBar.add(resetViewBtn);
        mapToolBar.addSeparator();
        mapToolBar.add(showVehicleLabelBtn);
        mapToolBar.add(showTLLabelBtn);

        // Simulation time display (right-aligned in toolbar)
        simulationTimeLabel = new JLabel("00:00:00");
        mapToolBar.add(Box.createHorizontalGlue()); // Push time label to right
        mapToolBar.add(simulationTimeLabel);

        mapSubPanel.add(mapToolBar, BorderLayout.NORTH);

        // Map canvas (custom panel for rendering SUMO map)
        mapCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                businessService.drawMap(g, this.getSize()); // Delegate map drawing to business service
            }
        };
        mapCanvas.setName("mapCanvas"); // For component lookup in business service
        mapCanvas.setBackground(Color.WHITE);
        mapSubPanel.add(mapCanvas, BorderLayout.CENTER);

        // Data dashboard (vehicle, TL, simulation stats)
        initDashboardSubPanel();

        rightPanel.add(mapSubPanel, BorderLayout.CENTER);
        rightPanel.add(dashboardSubPanel, BorderLayout.SOUTH);
    }

    /**
     * Initialize data dashboard subpanel (vehicle, traffic light, simulation statistics)
     */
    private void initDashboardSubPanel() {
        dashboardSubPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        dashboardSubPanel.setBorder(new TitledBorder("Data Dashboard"));

        // Vehicle data card
        vehicleCard = new JPanel(new GridBagLayout());
        vehicleCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints vehicleGbc = new GridBagConstraints();
        vehicleGbc.insets = new Insets(5, 10, 5, 10);
        vehicleGbc.anchor = GridBagConstraints.CENTER;

        JLabel vehicleTitle = new JLabel("Vehicle Data", SwingConstants.CENTER);
        vehicleTitle.setFont(new Font("Arial", Font.BOLD, 16));
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 0;
        vehicleCard.add(vehicleTitle, vehicleGbc);

        // Vehicle stats labels (default to "--" for no data)
        vehicleGbc.gridy = 1;
        vehicleTotalLabel = new JLabel("Total：-- vehicles", SwingConstants.CENTER);
        vehicleTotalLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        vehicleCard.add(vehicleTotalLabel, vehicleGbc);

        vehicleGbc.gridy = 2;
        vehicleRunningLabel = new JLabel("Running：-- vehicles", SwingConstants.CENTER);
        vehicleCard.add(vehicleRunningLabel, vehicleGbc);

        vehicleGbc.gridy = 3;
        vehicleCongestedLabel = new JLabel("Congestion：-- vehicles", SwingConstants.CENTER);
        vehicleCard.add(vehicleCongestedLabel, vehicleGbc);

        // Traffic light data card
        tlCard = new JPanel(new GridBagLayout());
        tlCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints tlGbc = new GridBagConstraints();
        tlGbc.insets = new Insets(10, 10, 10, 10);
        tlGbc.anchor = GridBagConstraints.CENTER;

        JLabel tlTitle = new JLabel("Traffic Light Data", SwingConstants.CENTER);
        tlTitle.setFont(new Font("Arial", Font.BOLD, 16));
        tlGbc.gridx = 0;
        tlGbc.gridy = 0;
        tlCard.add(tlTitle, tlGbc);

        // TL stats labels (default to "--" for no data)
        tlTotalLabel = new JLabel("Total：-- ", SwingConstants.CENTER);
        tlTotalLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        tlGbc.gridx = 0;
        tlGbc.gridy = 1;
        tlCard.add(tlTotalLabel, tlGbc);

        tlRedLabel = new JLabel("Red Light：-- ", SwingConstants.CENTER);
        tlGbc.gridx = 0;
        tlGbc.gridy = 2;
        tlCard.add(tlRedLabel, tlGbc);

        tlGreenLabel = new JLabel("Green Light：-- ", SwingConstants.CENTER);
        tlGbc.gridx = 0;
        tlGbc.gridy = 3;
        tlCard.add(tlGreenLabel, tlGbc);

        tlYellowLabel = new JLabel("Yellow Light：-- ", SwingConstants.CENTER);
        tlGbc.gridx = 0;
        tlGbc.gridy = 4;
        tlCard.add(tlYellowLabel, tlGbc);

        // Simulation statistics card
        statCard = new JPanel(new GridBagLayout());
        statCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints statGbc = new GridBagConstraints();
        statGbc.insets = new Insets(10, 10, 10, 10);
        statGbc.anchor = GridBagConstraints.CENTER;

        JLabel statTitle = new JLabel("Simulation Statistics", SwingConstants.CENTER);
        statTitle.setFont(new Font("Arial", Font.BOLD, 16));
        statGbc.gridx = 0;
        statGbc.gridy = 0;
        statCard.add(statTitle, statGbc);

        // Simulation stats labels (default to "--" for no data)
        statStepLabel = new JLabel("Total Steps：--", SwingConstants.CENTER);
        statStepLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statGbc.gridx = 0;
        statGbc.gridy = 1;
        statCard.add(statStepLabel, statGbc);

        statAvgSpeedLabel = new JLabel("Average Speed：-- km/h", SwingConstants.CENTER);
        statGbc.gridx = 0;
        statGbc.gridy = 2;
        statCard.add(statAvgSpeedLabel, statGbc);

        statEfficiencyLabel = new JLabel("Traffic Efficiency：-- %", SwingConstants.CENTER);
        statGbc.gridx = 0;
        statGbc.gridy = 3;
        statCard.add(statEfficiencyLabel, statGbc);

        // Assemble dashboard cards
        dashboardSubPanel.add(vehicleCard);
        dashboardSubPanel.add(tlCard);
        dashboardSubPanel.add(statCard);
    }

    /**
     * Layout all UI components in main frame (BorderLayout)
     */
    private void layoutComponents() {
        setJMenuBar(menuBar); // Add menu bar to top
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        contentPanel.add(leftPanel, BorderLayout.WEST); // Left function panel
        contentPanel.add(rightPanel, BorderLayout.CENTER); // Right display panel
        setContentPane(contentPanel); // Set main content pane
    }

    /**
     * Bind event listeners to all UI components (buttons, sliders, menu items)
     */
    private void bindEvents() {
        // File menu events
        openConfigItem.addActionListener(e -> selectConfigFile());
        exitItem.addActionListener(e -> handleExit());

        // Left panel events
        selectFileBtn.addActionListener(e -> selectConfigFile()); // Select config file button
        connectBtn.addActionListener(e -> businessService.connectSumo()); // Connect to SUMO
        stepForwardBtn.addActionListener(e -> businessService.stepSimulation()); // Step simulation forward
        startPauseBtn.addActionListener(e -> handleStartPause()); // Start/pause continuous mode
        resetBtn.addActionListener(e -> businessService.resetSimulation()); // Reset simulation
        disconnectBtn.addActionListener(e -> businessService.disconnectSumo()); // Disconnect from SUMO
        stepModeBtn.addActionListener(e -> updateControlStatus()); // Update controls when mode changes
        continuousModeBtn.addActionListener(e -> updateControlStatus());

        // Add vehicle button event (inject new vehicle into simulation)
        addVehicleBtn.addActionListener(e -> {
            try {
                String vehId = "veh" + System.currentTimeMillis(); // Unique vehicle ID
                String rouId = Route.getIDList().get(0); // Use first available route

                businessService.injectVehicle(vehId, rouId); // Delegate to business service
                logger.info("Vehicle injected: " + vehId);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "Vehicle injection failed:\n" + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // Traffic light control button (popup menu for TL actions)
        tlControlBtn.addActionListener(e -> {
            JPopupMenu tlPopup = new JPopupMenu();

            try{
                List<String> tlIds = TrafficLight.getIDList(); // Get all traffic light IDs from SUMO

                // Show error if no traffic lights exist
                if(tlIds.isEmpty()){
                    JOptionPane.showMessageDialog(this, "No Traffic Lights found", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Create menu items for each traffic light (auto/red/green modes)
                for(String tlId : tlIds){
                    JMenu tlOptions = new JMenu(tlId);

                    JMenuItem autoModeBtn = new JMenuItem("Auto");
                    autoModeBtn.addActionListener(e1 -> businessService.trafficLightControls(tlId, "auto"));
                    tlOptions.add(autoModeBtn);

                    JMenuItem redModeBtn = new JMenuItem("All Red");
                    redModeBtn.addActionListener(e1 -> businessService.trafficLightControls(tlId, "red"));
                    tlOptions.add(redModeBtn);

                    JMenuItem greenModeBtn = new JMenuItem("All Green");
                    greenModeBtn.addActionListener(e1 -> businessService.trafficLightControls(tlId, "green"));
                    tlOptions.add(greenModeBtn);

                    tlPopup.add(tlOptions);
                }
            }catch (Exception tle){
                tle.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading Traffic Lights: " + tle.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }

            tlPopup.show(tlControlBtn, 0, tlControlBtn.getHeight()); // Show popup below button
        });

        // Speed slider event (adjust continuous mode interval)
        speedSlider.addChangeListener(e -> {
            int speed = speedSlider.getValue();
            speedLabel.setText("Simulation Speed：Level " + speed);
            continuousTimer.setDelay(1100 - speed * 100); // 1000ms (Level1) to 100ms (Level10)
        });

        // Right panel map tool events
        zoomInBtn.addActionListener(e -> businessService.zoomMap(1.1f)); // Zoom in (10%)
        zoomOutBtn.addActionListener(e -> businessService.zoomMap(0.9f)); // Zoom out (10%)
        transBtn.addActionListener(e -> businessService.togglePanMode()); // Toggle map pan mode
        resetViewBtn.addActionListener(e -> businessService.resetMapView()); // Reset map zoom/position
        showVehicleLabelBtn.addActionListener(e -> businessService.toggleVehicleLabel()); // Show/hide vehicle labels
        showTLLabelBtn.addActionListener(e -> businessService.toggleTLStatusLabel()); // Show/hide TL status labels

        // Vehicle card click event (show detailed vehicle stats)
        vehicleCard.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                businessService.showVehicleDetail();
            }
        });
    }

    /**
     * Update translate button text to show pan mode status (Enabled/Disabled)
     */
    public void updatePanButtonText(String modeStatus) {
        if (transBtn != null) {
            transBtn.setText("Translate (" + modeStatus + ")");
        }
    }

    /**
     * Set enabled status for all map toolbar buttons
     */
    public void setMapToolsEnabled(boolean enabled) {
        zoomInBtn.setEnabled(enabled);
        zoomOutBtn.setEnabled(enabled);
        transBtn.setEnabled(enabled);
        resetViewBtn.setEnabled(enabled);
        showVehicleLabelBtn.setEnabled(enabled);
        showTLLabelBtn.setEnabled(enabled);
    }

    /**
     * Initialize component status (disabled by default until SUMO connected)
     */
    private void initComponentStatus() {
        stepForwardBtn.setEnabled(false);
        startPauseBtn.setEnabled(false);
        resetBtn.setEnabled(false);
        disconnectBtn.setEnabled(false);
        speedSlider.setEnabled(false);
        setMapToolsEnabled(false); // Disable map tools
        addVehicleBtn.setEnabled(false);
        tlControlBtn.setEnabled(false);
    }

    /**
     * Update control component status based on connection and simulation mode
     */
    public void updateControlStatus() {
        boolean isConnected = this.isConnected();
        boolean isStepMode = stepModeBtn.isSelected();

        // Enable/disable controls based on mode and connection status
        stepForwardBtn.setEnabled(isConnected && isStepMode);
        startPauseBtn.setEnabled(isConnected && !isStepMode);
        speedSlider.setEnabled(isConnected && !isStepMode);
        resetBtn.setEnabled(isConnected);
        disconnectBtn.setEnabled(isConnected);
        setMapToolsEnabled(isConnected);
        addVehicleBtn.setEnabled(isConnected); // Enable add vehicle when connected
        tlControlBtn.setEnabled(isConnected); // Enable TL control when connected

        // Stop continuous simulation if switching to step mode
        if (isStepMode && isContinuousRunning) {
            stopContinuousSimulation();
        }
    }

    /**
     * Open file chooser to select SUMO configuration file (.sumocfg)
     */
    private void selectConfigFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("SUMO Configuration File", "sumocfg")); // Filter for .sumocfg files
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File configFile = fileChooser.getSelectedFile();
            configPath = configFile.getAbsolutePath();
            configPathField.setText(configPath); // Display selected file path
            String scenarioName = configFile.getName().replace(".sumocfg", ""); // Extract scenario name (remove extension)
            scenarioNameField.setText(scenarioName);
            logger.info("Selected File：" + configFile.getName());
            connectBtn.setEnabled(true); // Enable connect button after file selection
        }
    }

    /**
     * Handle start/pause toggle for continuous simulation mode
     */
    private void handleStartPause() {
        if (continuousTimer.isRunning()) {
            stopContinuousSimulation(); // Pause if running
        } else {
            startContinuousSimulation(); // Start if paused
        }
    }

    /**
     * Start continuous simulation (enable timer, update button text)
     */
    public void startContinuousSimulation() {
        isContinuousRunning = true;
        startPauseBtn.setText("Pause"); // Update button text to "Pause"
        continuousTimer.start(); // Start timer (triggers stepSimulation() at set interval)
        logger.info("Continuous simulation has started，speed：Level " + speedSlider.getValue());
    }

    /**
     * Stop continuous simulation (disable timer, update button text)
     */
    public void stopContinuousSimulation() {
        isContinuousRunning = false;
        startPauseBtn.setText("Start"); // Update button text to "Start"
        continuousTimer.stop(); // Stop timer
        logger.info("Continuous simulation has been paused");
    }

    /**
     * Handle single simulation step for continuous mode (timer callback)
     */
    private void handleContinuousStep(ActionEvent e) {
        businessService.stepSimulation(); // Delegate step logic to business service
    }

    /**
     * Handle application exit (confirm with user, disconnect SUMO if connected)
     */
    private void handleExit() {
        int option = JOptionPane.showConfirmDialog(this, "Terminate the simulation and exit？", "Exit Confirmation", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            if (isConnected) {
                businessService.disconnectSumo(); // Clean up SUMO connection
            }
            System.exit(0); // Exit application
        }
    }

    /**
     * Update SUMO connection status (UI indicator + component status)
     */
    public void updateSumoConnectionStatus(boolean connected) {
        isConnected = connected;
        // Update status indicator (green = connected, red = disconnected)
        statusIndicator.setIcon(new ImageIcon(createIndicatorIcon(connected ? Color.GREEN : Color.RED)));
        connectBtn.setEnabled(!connected); // Disable connect button when connected
        updateControlStatus(); // Update control component status
        mapCanvas.repaint(); // Redraw map to reflect connection status
    }

    /**
     * Update data dashboard with latest simulation statistics
     */
    public void updateDashboard(SimulationData data) {
        // Vehicle stats (ensure non-negative values)
        int totalVehicles = Math.max(data.getVehicleTotal(), 0);
        int runningVehicles = Math.max(data.getVehicleRunning(), 0);
        int congestedVehicles = Math.max(data.getVehicleCongested(), 0);

        vehicleTotalLabel.setText("Total：" + totalVehicles + " vehicles");
        vehicleRunningLabel.setText("Running：" + runningVehicles + " vehicles");
        vehicleCongestedLabel.setText("Congestion：" + congestedVehicles + " vehicles");

        // Traffic light stats (ensure non-negative values)
        tlTotalLabel.setText("Total：" + Math.max(data.getTlTotal(), 0));
        tlRedLabel.setText("Red：" + Math.max(data.getTlRed(), 0));
        tlGreenLabel.setText("Green：" + Math.max(data.getTlGreen(), 0));
        tlYellowLabel.setText("Yellow：" + Math.max(data.getTlYellow(), 0));

        // Simulation stats (format numbers to 1 decimal place)
        statStepLabel.setText("Total Steps：" + data.getTotalSteps());
        statAvgSpeedLabel.setText(String.format("Average Speed：%.1f km/h", data.getAvgSpeed()));
        statEfficiencyLabel.setText(String.format("Traffic Efficiency：%.1f %%", data.getTrafficEfficiency()));

        // Update simulation time display
        simulationTimeLabel.setText(data.getSimulationTime());

        mapCanvas.repaint(); // Redraw map to reflect latest data
    }

    /**
     * Get log text area (for log4j2 TextAreaAppender binding)
     */
    public JTextArea getLogArea() {
        return this.logArea;
    }

    // ------------------- Getter Methods (for business service access) -------------------
    /**
     * Get continuous simulation running status
     */
    public boolean getisContinuousRunning(){
        return this.isContinuousRunning;
    }

    /**
     * Get SUMO GUI executable path
     */
    public String getSumoGuiPath() {
        return sumoGuiPath;
    }

    /**
     * Get selected SUMO configuration file path
     */
    public String getConfigPath() {
        return configPath;
    }

    /**
     * Get TRACI port number for SUMO connection
     */
    public int getTraciPort() {
        return traciPort;
    }

    /**
     * Get SUMO connection status
     */
    public boolean isConnected() {
        return isConnected;
    }
}