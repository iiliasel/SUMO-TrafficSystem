import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;

import com.sumo.log.TextAreaAppender;
/**
 * main dashboard:
 *  implement UI layout
 *  component initialization
 *  event binding
 *  and call functions of business logic processing classes
 */
public class SumoMainFrame extends JFrame {
    // creat a business logic processing object to decoupling user interface from business logic
    private final SumoBusinessService businessService;
    private static final Logger logger = LogManager.getLogger(SumoMainFrame.class);
    // top navigation bar component
    private JMenuBar menuBar;
    private JMenu fileMenu, configMenu, helpMenu;
    private JMenuItem openConfigItem, saveDataItem, loadScenarioItem, exitItem;
    private JMenuItem sumoPathItem, portItem, restoreDefaultItem;
    private JMenuItem userGuideItem, aboutItem;

    // left function panel component
    private JPanel leftPanel, configSubPanel, controlSubPanel;
    private JLabel configPathLabel, scenarioNameLabel, sumoStatusLabel;
    private JTextField configPathField, scenarioNameField;
    private JButton selectFileBtn;
    private JLabel statusIndicator; // SUMO connection status indicator

    // control area component
    private JRadioButton stepModeBtn, continuousModeBtn;
    private ButtonGroup modeGroup;
    private JButton connectBtn, stepForwardBtn, startPauseBtn, resetBtn, disconnectBtn;
    // Initialize VehicleButton  
    private JButton addVehicleBtn;
    private JSlider speedSlider;
    private JLabel speedLabel;
    private JTextArea logArea;
    private JScrollPane logScroll;

    // right-side main display area conponent
    private JPanel rightPanel, mapSubPanel, dashboardSubPanel;
    private JPanel mapCanvas; // display the map
    private JToolBar mapToolBar;
    private JButton zoomInBtn, zoomOutBtn, panBtn, resetViewBtn, showVehicleLabelBtn, showTLLabelBtn;
    private JLabel simulationTimeLabel;

    // data dashboard component
    private JPanel vehicleCard, tlCard, statCard;
    private JLabel vehicleTotalLabel, vehicleRunningLabel, vehicleCongestedLabel;
    private JLabel tlTotalLabel, tlRedLabel, tlGreenLabel, tlYellowLabel;
    private JLabel statStepLabel, statAvgSpeedLabel, statEfficiencyLabel;

    // system configuration parameters
    private String sumoGuiPath = "D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe";
    private String configPath;
    private int traciPort = 8813;
    private boolean isConnected = false;
    private boolean isContinuousRunning = false;
    private Timer continuousTimer; // continuous mode timer



    public SumoMainFrame() {
        // call function to initialize all the components
        initAllComponents();
        // initialize backend service
        businessService = new SumoBusinessService(this);
        // layout component
        layoutComponents();
        // bind event listener
        bindEvents();
        // initialize tate
        initComponentStatus();
        bindLogAreaToAppender(); // 绑定logArea到Appender
    }

    // 在SumoMainFrame的构造方法或初始化方法中
    private void bindLogAreaToAppender() {
        TextAreaAppender.setLogTextArea(logArea); // 绑定GUI的日志区域
        System.out.println("logArea绑定状态：" + (logArea != null)); // 确认绑定成功
    }

    /**
     * initialize all the components
     */
    private void initAllComponents() {
        // 1. initialize the top navigation bar
        initMenuBar();
        // 2. initialize the left-side function panel
        initLeftPanel();
        // 3. initialize the right-side function panel
        initRightPanel();
        // 4. initialize the continuous mode timer
        continuousTimer = new Timer(1000, this::handleContinuousStep);
    }

    /**
     * initialize the top navigation bar
     */
    private void initMenuBar() {
        menuBar = new JMenuBar();

        // file menu
        fileMenu = new JMenu("File");
        openConfigItem = new JMenuItem("Open Configuration File");
        saveDataItem = new JMenuItem("");
        loadScenarioItem = new JMenuItem("Enhanced Simulation Scrnr");
        exitItem = new JMenuItem("Exit");
        fileMenu.add(openConfigItem);
        fileMenu.add(saveDataItem);
        fileMenu.add(loadScenarioItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // Configuration menu
        configMenu = new JMenu("Configuration");
        sumoPathItem = new JMenuItem("SUMO Path Settings");
        portItem = new JMenuItem("Port Settings");
        restoreDefaultItem = new JMenuItem("Restore Configuration Settings");
        configMenu.add(sumoPathItem);
        configMenu.add(portItem);
        configMenu.add(restoreDefaultItem);

        // help menu
        helpMenu = new JMenu("Help");
        userGuideItem = new JMenuItem("User Guide");
        aboutItem = new JMenuItem("About");
        helpMenu.add(userGuideItem);
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(configMenu);
        menuBar.add(Box.createHorizontalGlue()); // right-align help menu
        menuBar.add(helpMenu);
    }

    /**
     * initialize the left-side function panel（configuration area + control area）
     */
    private void initLeftPanel() {
        leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // call function to initialize configuration area
        initConfigSubPanel();

        // call function to initialize control area
        initControlSubPanel();

        leftPanel.add(configSubPanel, BorderLayout.NORTH);
        leftPanel.add(controlSubPanel, BorderLayout.CENTER);
    }

    /**
     * initialize configuration subpanel
     */
    private void initConfigSubPanel() {
        configSubPanel = new JPanel(new GridBagLayout());//flexible layout for precise component positioning
        configSubPanel.setBorder(new TitledBorder("Configuration Area"));
        //to control component positioning/sizing in GridBagLayout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);// Set 5px padding around all components
        gbc.fill = GridBagConstraints.HORIZONTAL;// Make components fill available horizontal space

        // Configuration File Path Section
        configPathLabel = new JLabel("Configure File Path：");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;// Align component to the west (left) of its cell
        configSubPanel.add(configPathLabel, gbc);

        configPathField = new JTextField();
        configPathField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        configSubPanel.add(configPathField, gbc);

        selectFileBtn = new JButton("Select File");
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        configSubPanel.add(selectFileBtn, gbc);

        scenarioNameLabel = new JLabel("Current Scenario：");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(scenarioNameLabel, gbc);

        // Read-only text field to show current scenario name (default: "未选择场景")
        scenarioNameField = new JTextField("No Scenario Selected");
        scenarioNameField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        configSubPanel.add(scenarioNameField, gbc);

        // initialize status indicator light
        initStatusIndicator(gbc);
    }

    /**
     * initialize control subpanel
     */
    private void initControlSubPanel() {
        controlSubPanel = new JPanel(new GridBagLayout());
        controlSubPanel.setBorder(new TitledBorder("Control Area"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 3;

        // connection button
        connectBtn = new JButton("Connect SUMO");
        connectBtn.setEnabled(false); // disable at first,after selecting the file
        gbc.gridy = 0;
        controlSubPanel.add(connectBtn, gbc);

        // select simulation mode
        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.WEST;
        controlSubPanel.add(new JLabel("Simulation Mode："), gbc);

        modeGroup = new ButtonGroup();
        stepModeBtn = new JRadioButton("Single Step");
        continuousModeBtn = new JRadioButton("Continuous");
        modeGroup.add(stepModeBtn);
        modeGroup.add(continuousModeBtn);
        stepModeBtn.setSelected(true);

        gbc.gridx = 1;
        controlSubPanel.add(stepModeBtn, gbc);
        gbc.gridx = 2;
        controlSubPanel.add(continuousModeBtn, gbc);

        // control button
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


        controlBtnPanel.add(stepForwardBtn);
        controlBtnPanel.add(startPauseBtn);
        controlBtnPanel.add(resetBtn);
        controlBtnPanel.add(disconnectBtn);
        controlSubPanel.add(controlBtnPanel, gbc);

        // speed adjust
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        speedLabel = new JLabel("Simulation Speed：Lever 1");
        controlSubPanel.add(speedLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        speedSlider = new JSlider(1, 10, 1);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setEnabled(false);
        controlSubPanel.add(speedSlider, gbc);

        // Log output area
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        logArea = new JTextArea(8, 20);
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("Operation Log"));
        controlSubPanel.add(logScroll, gbc);
    }

    /**
     * initialize SUMO connection button
     */
    private void initStatusIndicator(GridBagConstraints gbc) {
        sumoStatusLabel = new JLabel("SUMO Connection Status：");
        statusIndicator = new JLabel();
        //Set fixed preferred size (15x15px) to ensure the indicator is a consistent circle
        statusIndicator.setPreferredSize(new Dimension(15, 15));
        // initially red(not connected)
        statusIndicator.setIcon(new ImageIcon(createIndicatorIcon(Color.RED)));
        // Position status label at column 0, row 2 (below scenario name section)
        gbc.gridx = 0;
        gbc.gridy = 2;
        // Align label to the left (west) of its grid cell
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(sumoStatusLabel, gbc);

        gbc.gridx = 1;
        configSubPanel.add(statusIndicator, gbc);
    }

    /**
     * create a status light icon
     */
    private BufferedImage createIndicatorIcon(Color color) {
        // Create 15x15px ARGB image
        BufferedImage icon = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();// Get 2D graphics context for drawing shapes on the image
        // Fill the circle
        g2d.setColor(color);
        g2d.fillOval(0, 0, 15, 15);
        // Draw thin black border
        g2d.setColor(Color.BLACK);
        g2d.drawOval(0, 0, 14, 14);
        g2d.dispose();
        return icon;
    }

    /**
     * initialize the main display area on the right(map + dashboard)
     */
    private void initRightPanel() {
        rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // upper half:simulation map area
        mapSubPanel = new JPanel(new BorderLayout());
        mapSubPanel.setBorder(new TitledBorder("Simulation Map"));

        // Map operation
        mapToolBar = new JToolBar();
        mapToolBar.setFloatable(false);

        zoomInBtn = new JButton("Zoom In");
        zoomOutBtn = new JButton("Zoom Out");
        panBtn = new JButton("Translate");
        resetViewBtn = new JButton("Reset View");
        showVehicleLabelBtn = new JButton("Show Vehicle Label");
        showTLLabelBtn = new JButton("Show Traffic Light Status");

        // initially disable mao tools
        setMapToolsEnabled(false);

        mapToolBar.add(zoomInBtn);
        mapToolBar.add(zoomOutBtn);
        mapToolBar.add(panBtn);
        mapToolBar.add(resetViewBtn);
        mapToolBar.addSeparator();
        mapToolBar.add(showVehicleLabelBtn);
        mapToolBar.add(showTLLabelBtn);

        // Simulation time display
        simulationTimeLabel = new JLabel("00:00:00");
        mapToolBar.add(Box.createHorizontalGlue());
        mapToolBar.add(simulationTimeLabel);

        mapSubPanel.add(mapToolBar, BorderLayout.NORTH);

        // map canvas
        mapCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // call businessService.drawMap(g) to draw map
                businessService.drawMap(g, this.getSize());
            }
        };
        mapCanvas.setName("mapCanvas");
        mapCanvas.setBackground(Color.WHITE);
        mapSubPanel.add(mapCanvas, BorderLayout.CENTER);

        // data dashboard (lower half)
        initDashboardSubPanel();

        rightPanel.add(mapSubPanel, BorderLayout.CENTER);
        rightPanel.add(dashboardSubPanel, BorderLayout.SOUTH);
    }

    /**
     * initialize data subpanel
     */
    private void initDashboardSubPanel() {
        dashboardSubPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        dashboardSubPanel.setBorder(new TitledBorder("Data Dashboard"));

        // vehicle data
        vehicleCard = new JPanel(new GridBagLayout());
        vehicleCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints vehicleGbc = new GridBagConstraints();
        vehicleGbc.insets = new Insets(10, 10, 10, 10);
        vehicleGbc.anchor = GridBagConstraints.CENTER;

        JLabel vehicleTitle = new JLabel("Vehicle Data", SwingConstants.CENTER);
        vehicleTitle.setFont(new Font("Arial",Font.BOLD, 16));
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 0;
        vehicleCard.add(vehicleTitle, vehicleGbc);

        vehicleTotalLabel = new JLabel("Total：--vehicles", SwingConstants.CENTER);
        vehicleTotalLabel.setFont(new Font("宋体", Font.PLAIN, 14));
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 1;
        vehicleCard.add(vehicleTotalLabel, vehicleGbc);

        vehicleRunningLabel = new JLabel("Running：-- vehicles", SwingConstants.CENTER);
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 2;
        vehicleCard.add(vehicleRunningLabel, vehicleGbc);

        vehicleCongestedLabel = new JLabel("Congestion：-- vehicles", SwingConstants.CENTER);
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 3;
        vehicleCard.add(vehicleCongestedLabel, vehicleGbc);

        // traffic light data
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

        tlTotalLabel = new JLabel("Total：-- ", SwingConstants.CENTER);
        tlTotalLabel.setFont(new Font("宋体", Font.PLAIN, 14));
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

        // Simulation data
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

        statStepLabel = new JLabel("Total Steps：--", SwingConstants.CENTER);
        statStepLabel.setFont(new Font("宋体", Font.PLAIN, 14));
        statGbc.gridx = 0;
        statGbc.gridy = 1;
        statCard.add(statStepLabel, statGbc);

        statAvgSpeedLabel = new JLabel("Average Speed：-- km/h", SwingConstants.CENTER);
        statGbc.gridx = 0;
        statGbc.gridy = 2;
        statCard.add(statAvgSpeedLabel, statGbc);

        statEfficiencyLabel = new JLabel("Passage Efficiency：-- %", SwingConstants.CENTER);
        statGbc.gridx = 0;
        statGbc.gridy = 3;
        statCard.add(statEfficiencyLabel, statGbc);

        // assemble dashboard
        dashboardSubPanel.add(vehicleCard);
        dashboardSubPanel.add(tlCard);
        dashboardSubPanel.add(statCard);
    }

    /**
     * layout all components
     */
    private void layoutComponents() {
        // set main window layout BorderLayout,the navigation bar at the top and left and right panels in the middle
        setJMenuBar(menuBar);
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        contentPanel.add(leftPanel, BorderLayout.WEST);
        contentPanel.add(rightPanel, BorderLayout.CENTER);
        setContentPane(contentPanel);
    }

    /**
     * bind event listeners to all components
     */
    private void bindEvents() {
        /*
         1. navigation bar enevts
         */
        // file menu event
        openConfigItem.addActionListener(e -> selectConfigFile());
        saveDataItem.addActionListener(e -> businessService.saveSimulationData());
        loadScenarioItem.addActionListener(e -> businessService.loadSimulationScenario());
        exitItem.addActionListener(e -> handleExit());

        // configuration menu events
        sumoPathItem.addActionListener(e -> businessService.setSumoPath());
        portItem.addActionListener(e -> businessService.setTraciPort());
        restoreDefaultItem.addActionListener(e -> businessService.restoreDefaultConfig());

        // help menu events
        userGuideItem.addActionListener(e -> businessService.showUserGuide());
        aboutItem.addActionListener(e -> businessService.showAbout());

        /*
         2. left panel events
         */
        // Configuration area
        selectFileBtn.addActionListener(e -> selectConfigFile());

        // Control area
        connectBtn.addActionListener(e -> businessService.connectSumo());
        stepForwardBtn.addActionListener(e -> businessService.stepSimulation());
        startPauseBtn.addActionListener(e -> handleStartPause());
        resetBtn.addActionListener(e -> businessService.resetSimulation());
        disconnectBtn.addActionListener(e -> businessService.disconnectSumo());
        stepModeBtn.addActionListener(e -> updateControlStatus());
        continuousModeBtn.addActionListener(e -> updateControlStatus());

        // speed slider
        speedSlider.addChangeListener(e -> {
            int speed = speedSlider.getValue();
            speedLabel.setText("Simulation Speed：Level" + speed );
            // adjust the time interval for continuous mode（1:1000ms，10:100ms）
            continuousTimer.setDelay(1100 - speed * 100);
        });



        /*
        3. right panel events
         */
        // map tools
        zoomInBtn.addActionListener(e -> businessService.zoomMap(1.1f));
        zoomOutBtn.addActionListener(e -> businessService.zoomMap(0.9f));
        panBtn.addActionListener(e -> businessService.togglePanMode());
        resetViewBtn.addActionListener(e -> businessService.resetMapView());
        showVehicleLabelBtn.addActionListener(e -> businessService.toggleVehicleLabel());
        showTLLabelBtn.addActionListener(e -> businessService.toggleTLStatusLabel());

        // data card click:view detailed data
        vehicleCard.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                businessService.showVehicleDetail();
            }
        });

        tlCard.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                businessService.showTLDetail();
            }
        });

        statCard.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                businessService.showStatDetail();
            }
        });
    }

    /**
     * initialize the status of components
     */
    private void initComponentStatus() {
        // components disabled when not connected
        stepForwardBtn.setEnabled(false);
        startPauseBtn.setEnabled(false);
        resetBtn.setEnabled(false);
        disconnectBtn.setEnabled(false);
        speedSlider.setEnabled(false);
        // map toolbar disabled
        setMapToolsEnabled(false);
        // save data disabled
        saveDataItem.setEnabled(false);
    }

    /**
     * update control area status
     */
    public void updateControlStatus() {
        boolean isConnected = businessService.isConnected();
        boolean isStepMode = stepModeBtn.isSelected();

        stepForwardBtn.setEnabled(isConnected && isStepMode);
        startPauseBtn.setEnabled(isConnected && !isStepMode);
        speedSlider.setEnabled(isConnected && !isStepMode);
        resetBtn.setEnabled(isConnected);
        disconnectBtn.setEnabled(isConnected);
        setMapToolsEnabled(isConnected);
        saveDataItem.setEnabled(isConnected);

        // reset button text when continuous mode stops
        if (isStepMode && isContinuousRunning) {
            stopContinuousSimulation();
        }
    }

    /**
     * select configuration file
     */
    private void selectConfigFile() {
        JFileChooser fileChooser = new JFileChooser();
        //set filter,only shows files with sumocfg
        fileChooser.setFileFilter(new FileNameExtensionFilter("SUMO Configuration File", "sumocfg"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            //get the selected file
            File configFile = fileChooser.getSelectedFile();
            configPath = configFile.getAbsolutePath();
            //put the path into textbox
            configPathField.setText(configPath);
            String scenarioName = configFile.getName().replace(".sumocfg", "");
            scenarioNameField.setText(scenarioName);
            logger.info("Selected configuration file: {}", configFile.getName());
            connectBtn.setEnabled(true);
        }
    }

    /**
     * handle start/pause in continuous mode
     */
    private void handleStartPause() {
        if (continuousTimer.isRunning()) {
            stopContinuousSimulation();
        } else {
            startContinuousSimulation();
        }
    }

    /**
     * start continuous simulation
     */
    public void startContinuousSimulation() {
        isContinuousRunning = true;
        startPauseBtn.setText("Pause");
        continuousTimer.start();
        logger.info("Continuous simulation started. Speed level: {}", speedSlider.getValue());
    }

    /**
     * stop Continuous simulation
     */
    public void stopContinuousSimulation() {
        isContinuousRunning = false;
        startPauseBtn.setText("Start");
        continuousTimer.stop();
        logger.info("Continuous simulation has been paused");
    }

    /**
     * single-step processing in wired mode
     */
    private void handleContinuousStep(ActionEvent e) {
        businessService.stepSimulation();
    }

    /**
     * handle exit operation
     */
    private void handleExit() {
        int option = JOptionPane.showConfirmDialog(this, "Terminate the simulation and exit？", "Exit Confirmation", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            logger.info("User confirmed exit. Terminating simulation...");
            // close connection before exit
            if (isConnected) {
                businessService.disconnectSumo();
            }
            logger.info("Simulation exited successfully. System exiting with code 0");
            System.exit(0);
        }
    }

    /**
     * set map toolbar enabled status
     */
    public void setMapToolsEnabled(boolean enabled) {
        zoomInBtn.setEnabled(enabled);
        zoomOutBtn.setEnabled(enabled);
        panBtn.setEnabled(enabled);
        resetViewBtn.setEnabled(enabled);
        showVehicleLabelBtn.setEnabled(enabled);
        showTLLabelBtn.setEnabled(enabled);
    }

    /**
     * update sumo connection status
     */
    public void updateSumoConnectionStatus(boolean connected) {
        isConnected = connected;
        statusIndicator.setIcon(new ImageIcon(createIndicatorIcon(connected ? Color.GREEN : Color.RED)));
        connectBtn.setEnabled(!connected);
        // update other component status
        updateControlStatus();
        // refresh map when connection status changes
        mapCanvas.repaint();
    }

    /**update data dashboard display
     */
    public void updateDashboard(SimulationData data) {
        // update vehicles data
        vehicleTotalLabel.setText("Total：" + data.getVehicleTotal());
        vehicleRunningLabel.setText("Running：" + data.getVehicleRunning());
        vehicleCongestedLabel.setText("Congestion：" + data.getVehicleCongested());

        // update traffic lights data
        tlTotalLabel.setText("Total：" + data.getTlTotal());
        tlRedLabel.setText("Red：" + data.getTlRed());
        tlGreenLabel.setText("Green：" + data.getTlGreen());
        tlYellowLabel.setText("Yellow：" + data.getTlYellow());

        // update simulation statistics
        statStepLabel.setText("Total Steps：" + data.getTotalSteps());
        statAvgSpeedLabel.setText(String.format("Average Speed：%.1f km/h", data.getAvgSpeed()));
        statEfficiencyLabel.setText(String.format("Traffic Efficiency：%.1f %%", data.getTrafficEfficiency()));

        // update simulation time
        simulationTimeLabel.setText(data.getSimulationTime());

        // refresh map
        mapCanvas.repaint();
    }

    /**
     *set configuration file path to synchronize the update of the interface text boxes
     **/
    public void setConfigPath(String configPath){
        this.configPath = configPath;
        // synchronize the configuration file path text box on the interface
        this.configPathField.setText(configPath);
        // after updating configuration file path,enable Connect SUMO button
        this.connectBtn.setEnabled(true);
    }






    // ------------------- getter methods:provided for access -------------------
    public boolean getisContinuousRunning(){
        return this.isContinuousRunning;
    }

    public String getSumoGuiPath() {
        return sumoGuiPath;
    }

    public void setSumoGuiPath(String sumoGuiPath) {
        this.sumoGuiPath = sumoGuiPath;
    }

    public String getConfigPath() {
        return configPath;
    }

    public int getTraciPort() {
        return traciPort;
    }

    public void setTraciPort(int traciPort) {
        this.traciPort = traciPort;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public JFrame getFrame() {
        return this;
    }

    public String getScenarioName() {
        return scenarioNameField.getText();
    }

    public void setScenarioName(String scenarioName) {
        scenarioNameField.setText(scenarioName);
    }
}