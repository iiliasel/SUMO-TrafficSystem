// VehicleInjection() in line 567


import org.eclipse.sumo.libtraci.*;


import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * service class:
 *  encapsulate all business logic
 *  decouple it from the UI class
 *  provide service interface for UI calls
 */
public class SumoBusinessService {
    // UI reference:only used for updating UI state and receiving configuration parameters,does not intrude on UI logic
    private final SumoMainFrame mainFrame;

    // map related core parameters
    private float mapScale = 1.0f; // map scaling ratio（range0.1-5.0）
    private Point mapOffset = new Point(0, 0); // map translation offset
    private boolean isPanMode = false; // map panning mode switch
    private Point lastMousePos = null; // last mouse position:to calculate translation distance
    private boolean showVehicleLabel = true; // vehicle label display switch
    private boolean showTLStatus = true; // traffic light status display switch

    // core variables in simulation data statistics
    private int totalSteps = 0; // total simulation step
    private long totalVehicleDistance = 0; // total distance traveled by vehicles（m）
    private long totalVehicleTime = 0; // total travel time for all vehicles（s）



    /**
     * constructor method,inject UI instance,initialize dependencies
     *
     * @param mainFrame main window interface example
     */
    public SumoBusinessService(SumoMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        // initialize map pan listener(bind to the UI canvas)
        //initMapPanListener();
    }

    /**
     * initialize map pan listener(bind to the UI canvas)
     * implement drag and drop translation functionality in pan mode
     */
    private void initMapPanListener() {
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas == null) {
            mainFrame.log("Map initialization failed, unable to bind pan listener");
            return;
        }

        // listen for mouse press:record the starting position of the translation
        mapCanvas.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isPanMode && mainFrame.isConnected()) {
                    lastMousePos = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastMousePos = null; // reset position when the mouse is released
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });

        // mouse drag listener:calculate translation offset and refresh map
        mapCanvas.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos != null && mainFrame.isConnected()) {
                    int dx = e.getX() - lastMousePos.x;
                    int dy = e.getY() - lastMousePos.y;
                    mapOffset.translate(dx, dy);
                    lastMousePos = e.getPoint();
                    mapCanvas.repaint(); // refresh map
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        });
    }

    /* locating by name to avoid type conversion errors
    searching for the component named "mapCanvas" from the mian window's content panel
     */
    private JPanel getMapCanvas() {
        try {
            Component comp = findComponentByName(mainFrame.getContentPane(), "mapCanvas");
            if (comp == null) {
                mainFrame.log("Failed to retrieve map canvas:No component named mapCanvas found");
                return null;
            }
            // validate the component type to make sure it's a JPanel
            if (!(comp instanceof JPanel)) {
                mainFrame.log("Failed to retrieve map canvas:The component found isn't a JPanel");
                return null;
            }
            return (JPanel) comp; //conversion
        } catch (Exception e) {
            mainFrame.log("Failed to retrieve map canvas：" + e.getMessage());
            return null;
        }
    }

    public boolean isConnected() {
        return mainFrame.isConnected();
    }


    /**
     * SUMO connection core functions：
     *      loading library;starting SUMO;initialize connection
     * handling abnormal scenarios such as configuration verification and port occupancy
     */
    public void connectSumo() {
        mainFrame.log("Start to execute SUMO connection operation...");
        // get configuration parameters from the interface
        String configPath = mainFrame.getConfigPath();
        String sumoPath = mainFrame.getSumoGuiPath();
        int port = mainFrame.getTraciPort();

        // configuration parameter validation,executed on the UI thread,pop-up window
        if (configPath == null || configPath.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Please select SUMO configuration file first！", "Error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Connection failed:No configuration file");
            return;
        }

        File sumoFile = new File(sumoPath);
        if (!sumoFile.exists()) {
            JOptionPane.showMessageDialog(mainFrame, "SUMO path errre：" + sumoPath, "error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Connection failed:SUMO path doesn't exist");
            return;
        }
        if (!sumoFile.getName().endsWith("sumo-gui.exe")) {
            mainFrame.log("Connection failed:Path is not sumo-gui.exe -> " + sumoPath);
            JOptionPane.showMessageDialog(mainFrame, "Please select the sumo-gui.exe file！");
            return;
        }

        // 3. build start-up parameters
        String[] args = {
                sumoPath,
                "-c", configPath,
                //"--remote-port", String.valueOf(port),  // reserve the port parameter
                "--start"
        };

        // start a sub-thread to excute time-consuming operations
        new Thread(() -> {
            try {
                mainFrame.log("SUMO startup command：" + String.join(" ", args));
                // perform time-consuming operations in a sub-thread
                Simulation.preloadLibraries();
                mainFrame.log("Starting SUMO...");
                Simulation.start(new StringVector(args));

                // 5. update the UI after successful startup
                SwingUtilities.invokeLater(() -> {
                    mainFrame.updateSumoConnectionStatus(true);
                    mainFrame.log("SUMO connection successful！");
                    initMapPanListener();  //initialize the map listener
                });
            } catch (Exception e) {
                mainFrame.log("SUMO startup failed：" + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame, "Connection failed：" + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * SUMO disconnection methods:stop simulation,close connection,reset state
     */
    public void disconnectSumo() {
        if (!mainFrame.isConnected()) {
            mainFrame.log("Not connected,no need to disconnect");
            return;
        }

        try {
            // if it is running,stop continuous simulation
            if (mainFrame.getisContinuousRunning()) {
                mainFrame.stopContinuousSimulation();
            }

            // close SUMO simulation engine connection
            Simulation.close();
            // reset map view parameters
            resetMapView();
            // notification interface updates connection status
            mainFrame.updateSumoConnectionStatus(false);
            mainFrame.log("SUMO connection closed");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to close connection：" + (e.getMessage() != null ? e.getMessage() : "error"), "error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Failed to close connection：" + (e.getMessage() != null ? e.getMessage() : "error"));
        }
    }

    /**
     * Single step simulation and update statistical data
     */
    public void stepSimulation() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first！", "Promt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // single-step:1 step=1 second
            Simulation.step();
            totalSteps++;
            mainFrame.log("Simulation progressed to step" + totalSteps);
            // update simulation data and synchronize to interface
            updateSimulationData();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Single-step failed：" + (e.getMessage() != null ? e.getMessage() : "error"), "error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Single-step failed：" + (e.getMessage() != null ? e.getMessage() : "error"));
            // handle connection interruption
            if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
                mainFrame.updateSumoConnectionStatus(false);
                mainFrame.log("SUMO connection has been interruption");
            }
        }
    }

    /**
     * simulation setting method,purchase SUMO status and statistics
     */
    public void resetSimulation() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect SUMO first！", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int option = JOptionPane.showConfirmDialog(mainFrame, "Reset simulation to its initial state？", "Reset confirmed", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            // sub-thread performs reset operation
            new Thread(() -> {
                try {
                    // stop continuous simulation
                    SwingUtilities.invokeLater(() -> {
                        if (mainFrame.getisContinuousRunning()) {
                            mainFrame.stopContinuousSimulation();
                        }
                    });

                    // close and restart SUMO
                    Simulation.close();
                    String[] args = {
                            mainFrame.getSumoGuiPath(),
                            "-c", mainFrame.getConfigPath(),
                            "--remote-port", String.valueOf(mainFrame.getTraciPort())
                    };
                    Simulation.start(new StringVector(args));

                    // reset statistic data
                    totalSteps = 0;
                    totalVehicleDistance = 0;
                    totalVehicleTime = 0;

                    // switch UI thread to update state
                    SwingUtilities.invokeLater(() -> {
                        mainFrame.log("The simulation has been reset to its initial state");
                        updateSimulationData();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainFrame, "Reset failed：" + (e.getMessage() != null ? e.getMessage() : "error"), "error", JOptionPane.ERROR_MESSAGE);
                        mainFrame.log("Reset failed：" + (e.getMessage() != null ? e.getMessage() : "error"));
                    });
                }
            }).start();
        }
    }

    /**
     * core data processing methods:
     *      acquiring simulation data
     *      calculating statistical indicators
     *      synchronizing to the interface
     */
    private void updateSimulationData() {
        try {
            // 1. vehicle data acquistion and processing
            List<String> vehicleIds = Vehicle.getIDList();
            int vehicleTotal = vehicleIds.size();
            int vehicleRunning = 0; // number of running vehicles(speed>0)
            int vehicleCongested = 0; // number of congested vehicles(speed<5 km/h)
            double currentDistance = 0; // total distance
            double currentTime = 0; // total time

            // get current simulation time to calculate vehicle travel tome
            double currentSimTime = Simulation.getTime();

            for (String vehicleId : vehicleIds) {
                // get current speed,needs to  be converted to km/h
                double speedMs = Vehicle.getSpeed(vehicleId); // m/s
                double speedKmH = speedMs * 3.6; // km/h

                // determine vehicle status
                if (speedMs > 0) { //running
                    vehicleRunning++;
                    if (speedKmH < 5) { //congested
                        vehicleCongested++;
                    }
                }

                // accumulate driving distance
                currentDistance += Vehicle.getDistance(vehicleId);

                // calculate travel time
                double departTime = Vehicle.getDeparture(vehicleId); // departure time
                double vehicleTime = currentSimTime - departTime; // travel time
                currentTime += Math.max(0, vehicleTime); // ensure non-negative values
            }

            // update global statistics
            totalVehicleDistance += (long) currentDistance;
            totalVehicleTime += (long) currentTime;

            // 2. traffic light data acquistion and processing
            List<String> tlIds = TrafficLight.getIDList();
            int tlTotal = tlIds.size();
            int tlRed = 0;
            int tlGreen = 0;
            int tlYellow = 0;

            // 预设交通灯相位顺序与状态的映射（需根据你的路网配置修改！）
            // 例如：索引0=红灯，1=绿灯，2=黄灯（请根据实际相位定义调整）
            int redPhaseIndex = 0;
            int greenPhaseIndex = 1;
            int yellowPhaseIndex = 2;


            for (String tlId : tlIds) {
                // 获取当前相位索引（SUMO中常用方法，多数版本支持）
                int phaseIndex = TrafficLight.getPhase(tlId);

                // 根据相位索引判断状态
                if (phaseIndex == redPhaseIndex) {
                    tlRed++;
                } else if (phaseIndex == greenPhaseIndex) {
                    tlGreen++;
                } else if (phaseIndex == yellowPhaseIndex) {
                    tlYellow++;
                }
                // 其他相位（如有）可忽略或扩展
            }

            // 3. calculation
            // average speed（km/h）：total distance(km) / total time(h)
            double avgSpeed = 0.0;
            if (totalVehicleTime > 0) {
                avgSpeed = (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0);
            }

            // trafficEfficiency（%）：vehicleRunning / 总车辆数 * 100
            double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;

            // simulation time formatting（HH:MM:SS）
            int hours = (int) (currentSimTime / 3600);
            int minutes = (int) ((currentSimTime % 3600) / 60);
            int seconds = (int) (currentSimTime % 60);
            String simulationTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            // 4. encapsulate data and notify the interface to update
            SimulationData data = new SimulationData(
                    vehicleTotal, vehicleRunning, vehicleCongested,
                    tlTotal, tlRed, tlGreen, tlYellow,
                    totalSteps, avgSpeed, trafficEfficiency,
                    simulationTime
            );
            mainFrame.updateDashboard(data);

        } catch (Exception e) {
            mainFrame.log("Data updating failed：" + (e.getMessage() != null ? e.getMessage() : "error"));
        }
    }

    /**
     * map drawing：based on Graphics2D to realize zooming,panning,element drawing
     * draw core elements such as road networks,traffic lights,vehicles and support status visualization
     *
     * @param g          draw contexr
     * @param canvasSize canvas size
     */
    public void drawMap(Graphics g, Dimension canvasSize) {
        if (!mainFrame.isConnected()) {
            // display a prompt message when not connected
            g.setColor(Color.GRAY);
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            String tip = "Please connect to SUMO to load the map";
            int x = (canvasSize.width - g.getFontMetrics().stringWidth(tip)) / 2;
            int y = canvasSize.height / 2;
            g.drawString(tip, x, y);
            return;
        }

        try {
            Graphics2D g2d = (Graphics2D) g;
            // save original drawing state for later restoration
            AffineTransform originalTransform = g2d.getTransform();

            // set scaling and panning transformations to center the map
            g2d.translate(canvasSize.width / 2 + mapOffset.x, canvasSize.height / 2 + mapOffset.y);
            g2d.scale(mapScale, mapScale);

            // 1. draw road network
            drawRoadNetwork(g2d);

            // 2. draw traffic lights
            drawTrafficLights(g2d);

            // 3. draw vehicles

            // restore original drawing state
            g2d.setTransform(originalTransform);
        } catch (Exception e) {
            mainFrame.log("Map drawing failed：" + (e.getMessage() != null ? e.getMessage() : "error"));
        }
    }

    /**
     * Draw road network: Simplified implementation of intersection road network, can be extended to parse SUMO road network data
     *
     * @param g2d Graphics context
     */
    private void drawRoadNetwork(Graphics2D g2d) {
        g2d.setColor(new Color(200, 200, 200));
        int roadWidth = 30;
        // Horizontal road
        g2d.fillRect(-500, -15, 1000, roadWidth);
        // Vertical road
        g2d.fillRect(-15, -500, roadWidth, 1000);
        // Draw road boundaries
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(-500, -15, 1000, roadWidth);
        g2d.drawRect(-15, -500, roadWidth, 1000);
    }

    /**
     * Draw traffic lights: Color according to status (red/green/yellow), support displaying ID and status labels
     *
     * @param g2d Graphics context
     */
    private void drawTrafficLights(Graphics2D g2d) {
        List<String> tlIds = TrafficLight.getIDList();
        for (String tlId : tlIds) {
            // Get traffic light position (SUMO coordinate system)
            StringVector controlledJunctions = TrafficLight.getControlledJunctions(tlId);
            if (controlledJunctions.isEmpty()) {
                continue;
            }
            String junctionID = controlledJunctions.get(0);
            TraCIPosition pos = Junction.getPosition(junctionID);
            int x = (int) pos.getX();
            int y = (int) pos.getY();

            // Set color according to status
            String phase = TrafficLight.getPhaseName(tlId);
            if (phase.contains("r")) {
                g2d.setColor(Color.RED);
            } else if (phase.contains("g")) {
                g2d.setColor(Color.GREEN);
            } else if (phase.contains("y")) {
                g2d.setColor(Color.YELLOW);
            }

            // Draw traffic light (circular)
            g2d.fillOval(x - 12, y - 12, 24, 24);
            g2d.setColor(Color.BLACK);
            g2d.drawOval(x - 12, y - 12, 23, 23);

            // Display traffic light ID and status (controllable via switch)
            if (showTLStatus) {
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("SimSun", Font.PLAIN, 10));
                // Draw ID
                g2d.drawString(tlId, x - 15, y - 15);
                // Draw status text
                String status = phase.contains("r") ? "Red" : phase.contains("g") ? "Green" : "Yellow";
                g2d.drawString(status, x - 4, y + 30);
            }
        }
    }

    /**
     * Draw vehicles: Color according to running status (running/congested/static), support displaying ID
     *
     * @param g2d Graphics context
     */
    private void drawVehicles(Graphics2D g2d) {
        List<String> vehicleIds = Vehicle.getIDList();
        for (String vehicleId : vehicleIds) {
            // Get vehicle position and driving direction
            TraCIPosition pos = Vehicle.getPosition(vehicleId);
            double angle = Vehicle.getAngle(vehicleId);
            int x = (int) pos.getX();
            int y = (int) pos.getY();

            // Set vehicle color according to speed
            double speed = Vehicle.getSpeed(vehicleId);
            if (speed > 0) {
                g2d.setColor(speed < 5 ? Color.ORANGE : Color.BLUE); // Congested is orange, running is blue
            } else {
                g2d.setColor(Color.GRAY); // Static is gray
            }

            // Save current transformation state (for rotating vehicle direction)
            AffineTransform originalTransform = g2d.getTransform();
            // Rotate around vehicle center (matching driving direction)
            g2d.rotate(Math.toRadians(angle), x, y);
            // Draw vehicle (represented by triangle)
            int[] xs = {x, x - 15, x + 15};
            int[] ys = {y - 20, y + 10, y + 10};
            g2d.fillPolygon(xs, ys, 3);
            g2d.setColor(Color.BLACK);
            g2d.drawPolygon(xs, ys, 3);
            // Restore transformation state
            g2d.setTransform(originalTransform);

            // Display vehicle ID (controllable via switch)
            if (showVehicleLabel) {
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("SimSun", Font.PLAIN, 10));
                g2d.drawString(vehicleId, x - 15, y - 25);
            }
        }
    }
    // Ilias Vehicle spawn
    public void injectVehicle(String vehicleId) throws SumoConnectException {
        try {
            String typeId = "DEFAULT_VEHTYPE";
            StringVector allRoutes = Route.getIDList();
            double depart = 0.0;

            // Real Routes Filter
            List<String> validRoutes = new ArrayList<>();
            for (String r : allRoutes) {
                if (!r.startsWith("!")) {
                    validRoutes.add(r);
                }
            }

            String routeId = validRoutes.get(0);

            // minimaler gültiger libtraci Aufruf
            Vehicle.add(vehicleId, routeId, typeId, String.valueOf(depart), "0");

            System.out.println("Injected vehicle: " + vehicleId);

        } catch (Exception e) {
            throw new SumoConnectException("Vehicle injection failed: " + e.getMessage());
        }
    }




    /**
     * Map zoom method: Zoom proportionally, limit zoom range
     *
     * @param scaleFactor Zoom factor (>1 to zoom in, <1 to zoom out)
     */
    public void zoomMap(float scaleFactor) {
        mapScale *= scaleFactor;
        // Limit zoom range (0.1x to 5.0x)
        mapScale = Math.max(0.1f, Math.min(5.0f, mapScale));
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint();
        }
        mainFrame.log("Map zoomed to " + String.format("%.1f", mapScale) + "x");
    }

    /**
     * Toggle map pan mode: Enable/disable pan function
     */
    public void togglePanMode() {
        isPanMode = !isPanMode;
        mainFrame.log("Map pan mode: " + (isPanMode ? "Enabled" : "Disabled"));
        // Switch mouse cursor style
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.setCursor(isPanMode ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());
        }
    }

    /**
     * Reset map view: Restore default zoom and position
     */
    public void resetMapView() {
        mapScale = 1.0f;
        mapOffset = new Point(0, 0);
        isPanMode = false;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.setCursor(Cursor.getDefaultCursor());
            mapCanvas.repaint();
        }
        mainFrame.log("Map view has been reset");
    }

    /**
     * Toggle vehicle label display: Enable/disable vehicle ID display
     */
    public void toggleVehicleLabel() {
        showVehicleLabel = !showVehicleLabel;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint();
        }
        mainFrame.log("Vehicle label display: " + (showVehicleLabel ? "Enabled" : "Disabled"));
    }

    /**
     * Toggle traffic light status display: Enable/disable traffic light ID and status display
     */
    public void toggleTLStatusLabel() {
        showTLStatus = !showTLStatus;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint();
        }
        mainFrame.log("Traffic light status display: " + (showTLStatus ? "Enabled" : "Disabled"));
    }

    /**
     * Save simulation data: Export current statistical data as CSV file
     * Support custom save path, standardized data format
     */
    public void saveSimulationData() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO and run simulation first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        // Filter CSV files
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        // Automatically generate default file name (including timestamp)
        String defaultFileName = "SimulationData_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".csv";
        chooser.setSelectedFile(new File(defaultFileName));

        int result = chooser.showSaveDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            // Ensure file suffix is .csv
            String filePath = file.getAbsolutePath();
            if (!filePath.endsWith(".csv")) {
                filePath += ".csv";
                file = new File(filePath);
            }

            try (FileWriter writer = new FileWriter(file)) {
                // Write CSV header
                writer.write("SimulationTime,TotalSteps,TotalVehicles,RunningVehicles,CongestedVehicles,TotalTrafficLights,RedLights,GreenLights,YellowLights,AvgSpeed(km/h),TrafficEfficiency(%)\n");
                // Get current simulation data
                SimulationData data = getCurrentSimulationData();
                // Write data row (use Locale.US to ensure decimal point)
                writer.write(String.format(Locale.US, "%s,%d,%d,%d,%d,%d,%d,%d,%d,%.1f,%.1f\n",
                        data.getSimulationTime(),
                        data.getTotalSteps(),
                        data.getVehicleTotal(),
                        data.getVehicleRunning(),
                        data.getVehicleCongested(),
                        data.getTlTotal(),
                        data.getTlRed(),
                        data.getTlGreen(),
                        data.getTlYellow(),
                        data.getAvgSpeed(),
                        data.getTrafficEfficiency()));

                writer.flush();
                JOptionPane.showMessageDialog(mainFrame, "Data saved to: " + filePath, "Save Successful", JOptionPane.INFORMATION_MESSAGE);
                mainFrame.log("Simulation data saved successfully: " + filePath);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainFrame, "Failed to save data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("Failed to save data: " + e.getMessage());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainFrame, "Failed to get data: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("Failed to get data: " + e.getMessage());
            }
        }
    }

    /**
     * Load simulation scenario: Select .sumocfg configuration file from folder
     * Support batch scenario management, automatically extract scenario names
     */
    public void loadSimulationScenario() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select scenario folder (containing .sumocfg files)");

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File scenarioDir = chooser.getSelectedFile();
            // Filter .sumocfg files
            File[] configFiles = scenarioDir.listFiles((dir, name) -> name.endsWith(".sumocfg"));

            if (configFiles == null || configFiles.length == 0) {
                JOptionPane.showMessageDialog(mainFrame, "No .sumocfg configuration files found in this folder!", "Error", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("Scenario loading failed: No configuration files in folder");
                return;
            }

            // Build scenario file list prompt
            StringBuilder fileList = new StringBuilder("Scenario contains the following configuration files:\n");
            for (int i = 0; i < configFiles.length; i++) {
                fileList.append(i + 1).append(". ").append(configFiles[i].getName()).append("\n");
            }
            fileList.append("Please enter the number of the file to load:");

            // Input number to select file
            String input = JOptionPane.showInputDialog(mainFrame, fileList.toString(), "Select Configuration File", JOptionPane.PLAIN_MESSAGE);
            if (input == null || input.isEmpty()) {
                return;
            }

            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < configFiles.length) {
                    File selectedFile = configFiles[index];
                    String configPath = selectedFile.getAbsolutePath();
                    // Update interface configuration information
                    mainFrame.setConfigPath(configPath);
                    mainFrame.setScenarioName(selectedFile.getName().replace(".sumocfg", ""));
                    mainFrame.log("Scenario loaded successfully: " + selectedFile.getName());
                    JOptionPane.showMessageDialog(mainFrame, "Configuration file loaded: " + selectedFile.getName(), "Load Successful", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(mainFrame, "Invalid input number!", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(mainFrame, "Please enter a valid number!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Set SUMO path: Select sumo-gui.exe executable file
     */
    public void setSumoPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Executable files (*.exe)", "exe"));
        chooser.setDialogTitle("Select sumo-gui.exe file");

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File sumoFile = chooser.getSelectedFile();
            if (sumoFile.getName().equals("sumo-gui.exe")) {
                mainFrame.setSumoGuiPath(sumoFile.getAbsolutePath());
                JOptionPane.showMessageDialog(mainFrame, "SUMO path set to: " + sumoFile.getAbsolutePath(), "Setting Successful", JOptionPane.INFORMATION_MESSAGE);
                mainFrame.log("SUMO path updated: " + sumoFile.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Please select sumo-gui.exe file!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Set TRACI port: Configure communication port between SUMO and program
     * Verify port range (1024-65535)
     */
    public void setTraciPort() {
        String input = JOptionPane.showInputDialog(mainFrame, "Please enter TRACI port number (1024-65535):", mainFrame.getTraciPort());
        if (input == null || input.isEmpty()) {
            return;
        }

        try {
            int port = Integer.parseInt(input);
            if (port >= 1024 && port <= 65535) {
                mainFrame.setTraciPort(port);
                JOptionPane.showMessageDialog(mainFrame, "TRACI port set to: " + port, "Setting Successful", JOptionPane.INFORMATION_MESSAGE);
                mainFrame.log("TRACI port updated: " + port);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Port number must be between 1024-65535!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(mainFrame, "Please enter a valid numeric port number!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Restore default configuration: Reset SUMO path, port and map parameters
     */
    public void restoreDefaultConfig() {
        int option = JOptionPane.showConfirmDialog(mainFrame, "Restore default configuration? (SUMO path, port, etc. will be reset)", "Restore Default", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            // Reset SUMO path and port
            mainFrame.setSumoGuiPath("D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe");
            mainFrame.setTraciPort(8813);
            // Reset map parameters
            resetMapView();
            showVehicleLabel = true;
            showTLStatus = true;
            JOptionPane.showMessageDialog(mainFrame, "Default configuration restored!", "Restore Successful", JOptionPane.INFORMATION_MESSAGE);
            mainFrame.log("System restored to default configuration");
            // Refresh map
            JPanel mapCanvas = getMapCanvas();
            if (mapCanvas != null) {
                mapCanvas.repaint();
            }
        }
    }

    /**
     * Display user guide: Pop up help dialog explaining core operation流程
     */
    public void showUserGuide() {
        String guide = "Intelligent Transportation SUMO Simulation Control System User Guide:\n" +
                "1. Configuration Process:\n" +
                "   - Click \"Select File\" to load .sumocfg configuration file\n" +
                "   - To modify SUMO path or port, set via \"Configuration\" menu\n" +
                "2. Simulation Control:\n" +
                "   - Step Mode: Click \"Step Forward\" to run step by step after connection\n" +
                "   - Continuous Mode: Switch mode, set speed with slider, click \"Start/Pause\"\n" +
                "3. Map Operations:\n" +
                "   - Zoom In/Out: Adjust map display scale\n" +
                "   - Pan: Drag map after enabling pan mode\n" +
                "   - Can show/hide vehicle labels and traffic light status\n" +
                "4. Data Viewing:\n" +
                "   - Dashboard displays core data, click cards to view details\n" +
                "   - Save simulation data as CSV file via \"File\" menu\n" +
                "5. Scenario Management: Batch manage configuration files via \"File-Load Simulation Scenario\"";
        JOptionPane.showMessageDialog(mainFrame, guide, "User Guide", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Display about information: Pop up software version and copyright information
     */
    public void showAbout() {
        String about = "Intelligent Transportation SUMO Simulation Control System v1.0\n" +
                "Development Dependencies: SUMO 1.25.0, Java Swing, TraCI SDK\n" +
                "Core Functions: SUMO simulation control, map visualization, data statistics and export\n" +
                "Design Principles: Decoupling of interface and business logic, modular architecture\n" +
                "Copyright © 2025 Intelligent Transportation Simulation Team";
        JOptionPane.showMessageDialog(mainFrame, about, "About System", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Display vehicle detailed data: Pop up dialog showing status information of all vehicles
     */
    public void showVehicleDetail() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<String> vehicleIds = Vehicle.getIDList();
            if (vehicleIds.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No vehicle data available!", "Prompt", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Build vehicle detailed information
            StringBuilder detail = new StringBuilder("Vehicle Detailed Information (Total " + vehicleIds.size() + " vehicles):\n");
            for (String vehicleId : vehicleIds) {
                double speed = Vehicle.getSpeed(vehicleId);
                double distance = Vehicle.getDistance(vehicleId);
                double departTime = Vehicle.getDeparture(vehicleId);
                double travelTime = Simulation.getTime() - departTime;
                String status = speed > 0 ? (speed < 5 ? "Congested" : "Running") : "Static";
                // Format and output vehicle information
                detail.append(String.format("ID: %s | Status: %s | Speed: %.1f km/h | Distance: %.1f m | Travel Time: %.1f s\n",
                        vehicleId, status, speed, distance, travelTime));
            }

            // Display with scrollable text box
            JTextArea textArea = new JTextArea(detail.toString());
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(650, 400));
            JOptionPane.showMessageDialog(mainFrame, scrollPane, "Vehicle Detailed Data", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to get vehicle details: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Failed to get vehicle details: " + e.getMessage());
        }
    }

    /**
     * Display traffic light detailed data: Pop up dialog showing status information of all traffic lights
     */
    public void showTLDetail() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<String> tlIds = TrafficLight.getIDList();
            if (tlIds.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "No traffic light data available!", "Prompt", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // Build traffic light detailed information
            StringBuilder detail = new StringBuilder("Traffic Light Detailed Information (Total " + tlIds.size() + " lights):\n");
            for (String tlId : tlIds) {
                String phase = TrafficLight.getPhaseName(tlId);
                double phaseDuration = TrafficLight.getPhaseDuration(tlId);
                String status = phase.contains("r") ? "Red" : phase.contains("g") ? "Green" : "Yellow";
                // Format and output traffic light information
                detail.append(String.format("ID: %s | Status: %s | Current Phase: %s | Phase Duration: %d s\n",
                        tlId, status, phase, phaseDuration));
            }

            // Display with scrollable text box
            JTextArea textArea = new JTextArea(detail.toString());
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(mainFrame, scrollPane, "Traffic Light Detailed Data", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to get traffic light details: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Failed to get traffic light details: " + e.getMessage());
        }
    }

    /**
     * Display simulation statistics detailed data: Pop up dialog showing core statistical indicators
     */
    public void showStatDetail() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Get current simulation data
            SimulationData data = getCurrentSimulationData();
            int vehicleTotal = data.getVehicleTotal();
            int vehicleRunning = data.getVehicleRunning();
            int vehicleCongested = data.getVehicleCongested();
            int tlTotal = data.getTlTotal();

            // Calculate extended statistical indicators
            int vehicleStatic = vehicleTotal - vehicleRunning; // Number of static vehicles
            double staticRate = vehicleTotal > 0 ? (double) vehicleStatic / vehicleTotal * 100 : 0.0; // Static rate
            double congestedRate = vehicleTotal > 0 ? (double) vehicleCongested / vehicleTotal * 100 : 0.0; // Congestion rate
            double greenRate = tlTotal > 0 ? (double) data.getTlGreen() / tlTotal * 100 : 0.0; // Green light ratio

            // Build statistical details
            String detail = String.format("Simulation Statistical Detailed Data\n" +
                            "======================\n" +
                            "1. Basic Information\n" +
                            "   - Simulation Time: %s\n" +
                            "   - Total Simulation Steps: %d steps\n" +
                            "   - Total Vehicles: %d vehicles\n" +
                            "   - Total Traffic Lights: %d lights\n" +
                            "\n2. Vehicle Operation Status\n" +
                            "   - Running: %d vehicles (%.1f%%)\n" +
                            "   - Congested: %d vehicles (%.1f%%)\n" +
                            "   - Static: %d vehicles (%.1f%%)\n" +
                            "\n3. Traffic Light Status\n" +
                            "   - Green: %d lights (%.1f%%)\n" +
                            "   - Red: %d lights\n" +
                            "   - Yellow: %d lights\n" +
                            "\n4. Performance Indicators\n" +
                            "   - Average Speed: %.1f km/h\n" +
                            "   - Traffic Efficiency: %.1f%%\n" +
                            "   - Total Distance Traveled: %.1f km\n" +
                            "   - Total Travel Time: %.1f h",
                    data.getSimulationTime(),
                    data.getTotalSteps(),
                    vehicleTotal,
                    tlTotal,
                    vehicleRunning,
                    (double) vehicleRunning / vehicleTotal * 100,
                    vehicleCongested,
                    congestedRate,
                    vehicleStatic,
                    staticRate,
                    data.getTlGreen(),
                    greenRate,
                    data.getTlRed(),
                    data.getTlYellow(),
                    data.getAvgSpeed(),
                    data.getTrafficEfficiency(),
                    totalVehicleDistance / 1000.0,
                    totalVehicleTime / 3600.0);

            JOptionPane.showMessageDialog(mainFrame, detail, "Simulation Statistical Detailed Data", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to get statistical details: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("Failed to get statistical details: " + e.getMessage());
        }
    }

    /**
     * Get current simulation data: Encapsulated as SimulationData object
     *
     * @return Simulation data object
     */
    private SimulationData getCurrentSimulationData() {
        List<String> vehicleIds = Vehicle.getIDList();
        List<String> tlIds = TrafficLight.getIDList();
        int vehicleTotal = vehicleIds.size();
        int vehicleRunning = 0;
        int vehicleCongested = 0;
        int tlTotal = tlIds.size();
        int tlRed = 0;
        int tlGreen = 0;
        int tlYellow = 0;

        // Calculate vehicle status
        for (String vehicleId : vehicleIds) {
            double speed = Vehicle.getSpeed(vehicleId);
            if (speed > 0) {
                vehicleRunning++;
                if (speed < 5) {
                    vehicleCongested++;
                }
            }
        }

        // Calculate traffic light status
        for (String tlId : tlIds) {
            String phase = TrafficLight.getPhaseName(tlId);
            if (phase.contains("r")) tlRed++;
            if (phase.contains("g")) tlGreen++;
            if (phase.contains("y")) tlYellow++;
        }

        // Calculate core indicators
        double avgSpeed = totalVehicleTime > 0 ? (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0) : 0.0;
        double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;
        double simTimeSec = Simulation.getTime();
        int hours = (int) (simTimeSec / 3600);
        int minutes = (int) ((simTimeSec % 3600) / 60);
        int seconds = (int) (simTimeSec % 60);
        String simulationTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

        return new SimulationData(
                vehicleTotal, vehicleRunning, vehicleCongested,
                tlTotal, tlRed, tlGreen, tlYellow,
                totalSteps, avgSpeed, trafficEfficiency,
                simulationTime
        );
    }

    /**
     * Get map canvas component: Locate canvas from interface component tree
     *
     * @return Map canvas panel
     */

    private Component findComponentByName(Container container, String name) {
        // Traverse all components in the container
        for (Component comp : container.getComponents()) {
            // Find component with matching name and return directly
            if (name.equals(comp.getName())) {
                return comp;
            }
            // If current component is a container (e.g., JPanel), recursively search its subcomponents
            if (comp instanceof Container) {
                Component found = findComponentByName((Container) comp, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null; // Not found
    }
    /**
     * Recursively find component with specified name (replace index positioning, solve type conversion error)
     * @param container Parent container (e.g., main window's content panel)
     * @param name Component name (using "mapCanvas" here)
     * @return Found component, null if not found
     */
}
