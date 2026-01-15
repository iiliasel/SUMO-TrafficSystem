import org.eclipse.sumo.libtraci.*;
import org.eclipse.sumo.libtraci.Edge;

import java.awt.BasicStroke;
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
import java.util.*;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.Lane;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.TraCIPositionVector;

/**
 * Business logic service class for SUMO simulation
 * Encapsulates core business logic, decouples from UI layer, and provides service interfaces for UI calls
 */
public class SumoBusinessService {
    // Reference to main UI frame (only for state update and config retrieval, no UI logic intrusion)
    private final SumoMainFrame mainFrame;
    private static final Logger logger = LogManager.getLogger(SumoBusinessService.class);

    // Core map-related parameters
    private float mapScale = 1.0f; // Map scaling ratio (range: 0.1-5.0)
    private boolean isPanMode = false; // Map panning mode toggle
    private Point lastMousePos = null; // Last mouse position (for calculating pan distance)
    private boolean showVehicleLabel = true; // Vehicle label display toggle
    private boolean showTLStatus = true; // Traffic light status display toggle

    // Core simulation data statistics variables
    private int totalSteps = 0; // Total simulation steps
    private long totalVehicleDistance = 0; // Total distance traveled by all vehicles (meters)
    private long totalVehicleTime = 0; // Total travel time for all vehicles (seconds)
    private static final double CONGESTION_THRESHOLD_KMH = 5.0; // Congestion speed threshold (km/h)

    // Filter criteria (no filter by default, modifiable by GUI)
    private double filterMinSpeed = 0.0;    // Minimum speed filter (km/h)
    private TraCIPositionVector sumoMapBoundary; // Map boundary stored in TraCIPositionVector
    private Map<String, List<List<TraCIPosition>>> edgeLaneShapesCache = new HashMap<>(); // Edge -> all lane shapes cache

    /**
     * Initialize business service with reference to main UI frame
     */
    public SumoBusinessService(SumoMainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * Initialize map pan listener (bind to UI canvas) to implement drag-to-pan functionality
     */
    private void initMapPanListener() {
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas == null) {
            logger.warn("Map initialization failed, unable to bind pan listener");
            return;
        }

        // Critical optimization: Ensure canvas receives mouse events (prevent event blocking)
        mapCanvas.setEnabled(true);
        mapCanvas.setFocusable(true);
        mapCanvas.requestFocusInWindow();

        // Add mouse listener for pan mode click/release detection
        mapCanvas.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {
                if (isPanMode && mainFrame.isConnected()) {
                    lastMousePos = e.getPoint();
                    logger.debug("Pan mode: mouse pressed at ({}, {})", e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastMousePos = null;
                logger.debug("Pan mode: mouse released");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (isPanMode) {
                    logger.debug("Entered map canvas (pan mode enabled)");
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (isPanMode) {
                    logger.debug("Exited map canvas (pan mode enabled)");
                }
            }
        });

        // Mouse drag listener: Calculate pan offset and refresh map
        mapCanvas.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos != null && mainFrame.isConnected()) {
                    int dx = e.getX() - lastMousePos.x;
                    int dy = e.getY() - lastMousePos.y;
                    logger.debug("Pan mode: dragged offset (dx={}, dy={})", dx, dy);
                    lastMousePos = e.getPoint();
                    mapCanvas.repaint(); // Trigger map redraw with new pan offset
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {}
        });

        logger.info("Map pan listener bound successfully");
    }

    /**
     * Locate map canvas component by name to avoid type conversion errors
     */
    private JPanel getMapCanvas() {
        try {
            Component comp = findComponentByName(mainFrame.getContentPane(), "mapCanvas");
            if (comp == null) {
                logger.warn("Failed to retrieve map canvas: No component named mapCanvas found");
                return null;
            }
            // Validate component type to ensure it's a JPanel
            if (!(comp instanceof JPanel)) {
                logger.warn("Failed to retrieve map canvas: The component found isn't a JPanel");
                return null;
            }
            return (JPanel) comp;
        } catch (Exception e) {
            logger.error("Failed to retrieve map canvas: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Preload road network data (lane shapes) into cache for improved drawing performance
     */
    private void preloadRoadNetworkData() {
        try {
            StringVector edgeIds = Edge.getIDList();
            for (String edgeId : edgeIds) {
                List<List<TraCIPosition>> laneShapes = getAllLaneShapes(edgeId);
                edgeLaneShapesCache.put(edgeId, laneShapes);
            }
            logger.info("Road network data preloaded: {} edges", edgeIds.size());
        } catch (Exception e) {
            logger.error("Failed to preload road network data: {}", e.getMessage());
        }
    }

    /**
     * Core SUMO connection function: Load libraries, start SUMO, initialize connection with error handling
     */
    public void connectSumo() {
        logger.info("Starting SUMO connection operation...");
        String configPath = mainFrame.getConfigPath();
        String sumoPath = mainFrame.getSumoGuiPath();
        int port = mainFrame.getTraciPort();

        String netFilePath = "";
        try {
            netFilePath = SumoCfgParser.parseNetFilePath(configPath); // Parse network file path from SUMO config
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "SUMO config parsing failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            logger.error("SUMO config parsing failed: {}", e.getMessage());
            return;
        }

        // Validate network file existence
        File netFile = new File(netFilePath);
        if (!netFile.exists()) {
            JOptionPane.showMessageDialog(mainFrame, "Network file not found: " + netFilePath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Validate configuration parameters (UI thread execution for pop-up dialogs)
        if (configPath == null || configPath.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Please select SUMO configuration file first!", "Error", JOptionPane.ERROR_MESSAGE);
            logger.warn("Connection failed: No configuration file");
            return;
        }

        File sumoFile = new File(sumoPath);
        if (!sumoFile.exists()) {
            JOptionPane.showMessageDialog(mainFrame, "SUMO path error: " + sumoPath, "Error", JOptionPane.ERROR_MESSAGE);
            logger.warn("Connection failed: SUMO path doesn't exist");
            return;
        }
        if (!sumoFile.getName().endsWith("sumo-gui.exe")) {
            logger.warn("Connection failed: Path is not sumo-gui.exe -> {}", sumoPath);
            JOptionPane.showMessageDialog(mainFrame, "Please select the sumo-gui.exe file!");
            return;
        }

        // Build SUMO startup arguments
        String[] args = {
                sumoPath,
                "-n", netFilePath,
                "-c", configPath,
                "--start" // Auto-start simulation after launch
        };

        // Execute time-consuming operations in background thread
        new Thread(() -> {
            try {
                logger.debug("SUMO startup command: {}", String.join(" ", args));
                Simulation.preloadLibraries(); // Preload SUMO native libraries
                logger.info("Starting SUMO...");
                Simulation.start(new StringVector(args)); // Start SUMO process
                sumoMapBoundary = Simulation.getNetBoundary(); // Get map boundary for centering

                // Update UI on Swing thread after successful startup
                SwingUtilities.invokeLater(() -> {
                    mainFrame.updateSumoConnectionStatus(true);
                    preloadRoadNetworkData(); // Cache road network data for faster drawing
                    initMapPanListener(); // Bind pan functionality to map canvas
                    logger.info("SUMO connection successful!");
                });
            } catch (Exception e) {
                logger.error("SUMO startup failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(mainFrame, "Connection failed: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Disconnect from SUMO: Stop simulation, close connection, and reset state
     */
    public void disconnectSumo() {
        if (!mainFrame.isConnected()) {
            logger.info("Not connected, no need to disconnect");
            return;
        }

        try {
            // Stop continuous simulation if running
            if (mainFrame.getisContinuousRunning()) {
                mainFrame.stopContinuousSimulation();
            }

            // Close SUMO simulation engine connection
            Simulation.close();
            resetMapView(); // Reset map to default zoom/position
            mainFrame.updateSumoConnectionStatus(false); // Update UI connection status
            logger.info("SUMO connection closed");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to close connection: " + (e.getMessage() != null ? e.getMessage() : "unknown error"), "Error", JOptionPane.ERROR_MESSAGE);
            logger.error("Failed to close connection: {}", e.getMessage() != null ? e.getMessage() : "unknown error");
        }
    }

    /**
     * Execute single simulation step and update statistical data
     */
    public void stepSimulation() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Simulation.step(); // Execute 1 simulation step (1 second real-time)
            totalSteps++;
            logger.info("Simulation progressed to step " + totalSteps);
            updateSimulationData(); // Recalculate and update UI with latest stats

        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Single-step failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error"), "Error", JOptionPane.ERROR_MESSAGE);
            logger.error("Single-step failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error"));
            // Handle connection reset (common error when SUMO is closed manually)
            if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
                mainFrame.updateSumoConnectionStatus(false);
                logger.info("SUMO connection has been interrupted");
            }
        }
    }

    /**
     * Reset simulation to initial state (restart SUMO and clear statistics)
     */
    public void resetSimulation() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int option = JOptionPane.showConfirmDialog(mainFrame, "Reset simulation to its initial state?", "Reset Confirmation", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            // Execute reset in background thread to avoid UI freezing
            new Thread(() -> {
                try {
                    // Stop continuous simulation on Swing thread
                    SwingUtilities.invokeLater(() -> {
                        if (mainFrame.getisContinuousRunning()) {
                            mainFrame.stopContinuousSimulation();
                        }
                    });

                    // Restart SUMO simulation engine
                    Simulation.close();
                    String[] args = {
                            mainFrame.getSumoGuiPath(),
                            "-c", mainFrame.getConfigPath(),
                            "--remote-port", String.valueOf(mainFrame.getTraciPort())
                    };
                    Simulation.start(new StringVector(args));

                    // Reset statistical data to initial values
                    totalSteps = 0;
                    totalVehicleDistance = 0;
                    totalVehicleTime = 0;

                    // Update UI with reset data on Swing thread
                    SwingUtilities.invokeLater(() -> {
                        logger.info("The simulation has been reset to its initial state");
                        updateSimulationData();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(mainFrame, "Reset failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error"), "Error", JOptionPane.ERROR_MESSAGE);
                        logger.error("Reset failed: " + (e.getMessage() != null ? e.getMessage() : "unknown error"));
                    });
                }
            }).start();
        }
    }

    /**
     * Core data processing method: Fetch simulation data, calculate statistics, and update UI
     */
    private void updateSimulationData() {
        try {
            // 1. Fetch and process vehicle data
            List<String> vehicleIds = Vehicle.getIDList();
            int vehicleTotal = vehicleIds.size();
            int vehicleRunning = 0; // Vehicles with speed > 0 km/h
            int vehicleCongested = 0; // Vehicles with speed < 5 km/h (congestion threshold)
            double currentSimTime = Simulation.getTime(); // Current simulation time in seconds

            // Iterate through all vehicles to calculate statistics
            for (String vehicleId : vehicleIds) {
                try {
                    double speedMs = Vehicle.getSpeed(vehicleId); // Speed in m/s (SUMO native unit)
                    double speedKmH = speedMs * 3.6; // Convert to km/h for user-friendly display

                    // Filter out invalid vehicles (not departed or already arrived)
                    double departTime = Vehicle.getDeparture(vehicleId); // Vehicle departure time
                    int routeIndex = Vehicle.getRouteIndex(vehicleId); // Current route segment index
                    int routeLength = Vehicle.getRoute(vehicleId).size(); // Total route segments

                    // Skip vehicles that haven't departed or have reached destination
                    if (departTime > currentSimTime || routeIndex >= routeLength) {
                        continue;
                    }

                    // Accumulate total distance and travel time for average speed calculation
                    double vehicleDistance = Vehicle.getDistance(vehicleId); // Distance traveled in meters
                    double vehicleTime = currentSimTime - departTime; // Travel time in seconds
                    totalVehicleDistance += Math.max(0, vehicleDistance); // Ensure non-negative values
                    totalVehicleTime += Math.max(0, vehicleTime);

                    // Classify vehicle status (running/congested)
                    if (speedMs > 0) {
                        vehicleRunning++;
                        if (speedKmH < CONGESTION_THRESHOLD_KMH) {
                            vehicleCongested++;
                            logger.debug("Congested vehicle: {} | Speed: {:.1f} km/h", vehicleId, speedKmH);
                        }
                    }
                } catch (Exception e) {
                    // Skip single vehicle errors to avoid breaking overall statistics
                    logger.warn("Failed to read data for vehicle {}: {}", vehicleId, e.getMessage());
                    continue;
                }
            }

            // 2. Fetch and process traffic light data
            List<String> tlIds = TrafficLight.getIDList();
            int tlTotal = tlIds.size();
            int tlRed = 0;
            int tlGreen = 0;
            int tlYellow = 0;

            for (String tlId : tlIds) {
                String tlState = TrafficLight.getRedYellowGreenState(tlId).toLowerCase();
                if (tlState.contains("g")) tlGreen++;
                else if (tlState.contains("y")) tlYellow++;
                else if (tlState.contains("r")) tlRed++;
            }

            // 3. Calculate key statistics
            double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;
            double avgSpeed = 0.0;
            if (totalVehicleTime > 0) {
                // Calculate average speed (km/h): total distance (km) / total time (hours)
                avgSpeed = (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0);
                logger.debug("Average speed calculated: {:.1f} km/h (totalDistance: {}m, totalTime: {}s)",
                        avgSpeed, totalVehicleDistance, totalVehicleTime);
            } else {
                logger.debug("Average speed is 0: totalVehicleTime is 0");
            }

            // Format simulation time as HH:MM:SS for UI display
            int hours = (int) (currentSimTime / 3600);
            int minutes = (int) ((currentSimTime % 3600) / 60);
            int seconds = (int) (currentSimTime % 60);
            String simulationTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            // Package data and update UI dashboard
            SimulationData data = new SimulationData(
                    vehicleTotal, vehicleRunning, vehicleCongested,
                    tlTotal, tlRed, tlGreen, tlYellow,
                    totalSteps, avgSpeed, trafficEfficiency,
                    simulationTime
            );
            mainFrame.updateDashboard(data);
        } catch (Exception e) {
            logger.warn("Data updating failed: {}", e.getMessage() != null ? e.getMessage() : "unknown error");
        }
    }

    /**
     * Draw simulation map with zoom, pan, and core elements (roads, vehicles, traffic lights)
     */
    public void drawMap(Graphics g, Dimension canvasSize) {
        if (!mainFrame.isConnected() || sumoMapBoundary.getValue().size() != 2) {
            return; // Skip drawing if not connected or invalid map boundary
        }

        try {
            Graphics2D g2d = (Graphics2D) g;
            AffineTransform originalTransform = g2d.getTransform(); // Save original transform for restoration

            // Extract map boundary from TraCIPositionVector (SUMO 1.25.0 compatible)
            TraCIPosition minPos = sumoMapBoundary.getValue().get(0);
            TraCIPosition maxPos = sumoMapBoundary.getValue().get(1);
            double mapWidth = maxPos.getX() - minPos.getX();
            double mapHeight = maxPos.getY() - minPos.getY();

            // Calculate map offset for centering on canvas
            double offsetX = canvasSize.width / 2 - (minPos.getX() + mapWidth/2) * mapScale;
            double offsetY = canvasSize.height / 2 - (-minPos.getY() - mapHeight/2) * mapScale;
            g2d.translate(offsetX, offsetY); // Apply center offset
            g2d.scale(mapScale, mapScale); // Apply zoom scale

            // Draw core map elements (roads, traffic lights, vehicles)
            drawRoadNetwork(g2d);
            drawTrafficLights(g2d);
            drawVehicles(g2d);

            g2d.setTransform(originalTransform); // Restore original graphics transform
        } catch (Exception e) {
            logger.warn("Map drawing failed: " + e.getMessage());
        }
    }

    /**
     * Get all lane shapes for a given edge (supports multi-lane edges)
     */
    private List<List<TraCIPosition>> getAllLaneShapes(String edgeId) {
        List<List<TraCIPosition>> allLaneShapes = new ArrayList<>();
        try {
            int laneCount = Edge.getLaneNumber(edgeId); // Get number of lanes for the edge
            for (int i = 0; i < laneCount; i++) {
                String laneId = edgeId + "_" + i; // Lane ID format: edgeId_0, edgeId_1...
                List<TraCIPosition> laneShape = Lane.getShape(laneId).getValue(); // Get lane geometry
                allLaneShapes.add(laneShape);
            }
        } catch (Exception e) {
            logger.warn("Failed to get lane shapes: edge={}, msg={}", edgeId, e.getMessage());
        }
        return allLaneShapes;
    }

    /**
     * Draw road network with realistic lane widths and SUMO-like color scheme
     */
    private void drawRoadNetwork(Graphics2D g2d) {
        try {
            StringVector edgeIds = Edge.getIDList();
            g2d.setColor(new Color(180, 180, 180)); // Light gray (matching SUMO's default road color)

            for (String edgeId : edgeIds) {
                List<List<TraCIPosition>> allLaneShapes = getAllLaneShapes(edgeId);
                // Draw each lane individually for accurate width representation
                for (int laneIdx = 0; laneIdx < allLaneShapes.size(); laneIdx++) {
                    List<TraCIPosition> laneShape = allLaneShapes.get(laneIdx);
                    if (laneShape.size() < 2) continue; // Skip invalid lane shapes (need at least 2 points)

                    // Convert SUMO coordinates to screen coordinates (Y-axis inverted for UI)
                    int[] xPoints = new int[laneShape.size()];
                    int[] yPoints = new int[laneShape.size()];
                    for (int j = 0; j < laneShape.size(); j++) {
                        TraCIPosition pos = laneShape.get(j);
                        xPoints[j] = (int) (pos.getX() * mapScale);
                        yPoints[j] = (int) (-pos.getY() * mapScale); // Invert Y-axis (SUMO → screen)
                    }

                    // Get real lane width from SUMO and scale for display
                    String laneId = edgeId + "_" + laneIdx;
                    double laneWidth = Lane.getWidth(laneId);
                    float strokeWidth = (float) (laneWidth * mapScale);
                    strokeWidth = Math.max(strokeWidth, 1.0f); // Minimum 1px to ensure visibility

                    g2d.setStroke(new BasicStroke(strokeWidth));
                    g2d.drawPolyline(xPoints, yPoints, laneShape.size());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to draw road network: {}", e.getMessage());
        }
    }

    /**
     * Draw traffic lights with color-coded status (red/green/yellow) and optional ID labels
     */
    private void drawTrafficLights(Graphics2D g2d) {
        List<String> tlIds = TrafficLight.getIDList();
        for (String tlId : tlIds) {
            try {
                // Get controlled lanes for traffic light position calculation
                StringVector controlledLanes = TrafficLight.getControlledLanes(tlId);
                if (controlledLanes.isEmpty()) continue;
                String laneId = controlledLanes.getFirst();

                // Get traffic light position (fallback for different SUMO versions)
                TraCIPosition stopLinePos = null;
                try {
                    // Primary method: Get lane shape and use first point (near stop line)
                    TraCIPositionVector laneShape = Lane.getShape(laneId);
                    if (!laneShape.getValue().isEmpty()) {
                        stopLinePos = laneShape.getValue().get(0);
                    }
                } catch (NoSuchMethodError e) {
                    // Fallback: Use edge start junction if Lane.getShape() is unavailable
                    String edgeId = laneId.split("_")[0]; // Extract edge ID from lane ID
                    String fromNodeId = Edge.getFromJunction(edgeId);
                    stopLinePos = Junction.getPosition(fromNodeId);
                    logger.warn("Lane.getShape() not available, using edge start junction: laneId={}", laneId);
                }
                if (stopLinePos == null) {
                    logger.warn("Unable to get traffic light position, skipping: tlId={}", tlId);
                    continue;
                }

                // Convert SUMO coordinates to screen coordinates (Y-axis inverted)
                int x = (int) (stopLinePos.getX() * mapScale);
                int y = (int) (-stopLinePos.getY() * mapScale);

                // Determine traffic light color based on current state
                String tlState = TrafficLight.getRedYellowGreenState(tlId).toLowerCase();
                Color color = Color.GRAY; // Default to gray if state is unknown
                if (tlState.contains("g")) color = Color.GREEN;
                else if (tlState.contains("y")) color = Color.YELLOW;
                else if (tlState.contains("r")) color = Color.RED;

                // Draw traffic light circle (size scaled with map zoom)
                int size = (int) (8 * mapScale);
                size = Math.max(4, Math.min(12, size)); // Limit size to 4-12px for visibility
                g2d.setColor(color);
                g2d.fillOval(x - size/2, y - size/2, size, size);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(x - size/2, y - size/2, size-1, size-1); // Add black border

                // Draw traffic light ID label if enabled
                if (showTLStatus) {
                    g2d.setColor(Color.BLACK);
                    int fontSize = Math.max(6, (int) (8 * mapScale)); // Scale font with map zoom
                    g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
                    g2d.drawString(tlId, x - 15, y - 10);
                }
            } catch (Exception e) {
                logger.warn("Failed to draw traffic light: tlId={}, msg={}", tlId, e.getMessage());
            }
        }
    }

    /**
     * Draw vehicles with color-coded status (running/congested/static) and optional ID labels
     */
    private void drawVehicles(Graphics2D g2d) {
        List<String> vehicleIds = Vehicle.getIDList();
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas == null) {
            logger.warn("Failed to draw vehicles: Map canvas not found");
            return;
        }

        for (String vehicleId : vehicleIds) {
            try {
                // Get vehicle position and orientation from SUMO
                TraCIPosition pos = Vehicle.getPosition(vehicleId);
                double angle = Vehicle.getAngle(vehicleId); // Vehicle heading angle (degrees)

                // Convert SUMO coordinates to screen coordinates (Y-axis inverted)
                int x = (int) (pos.getX() * mapScale);
                int y = (int) (-pos.getY() * mapScale);

                // Set vehicle color based on speed (congestion detection)
                double speed = Vehicle.getSpeed(vehicleId); // Speed in m/s
                if (speed > 0) {
                    g2d.setColor(speed < 5 ? Color.ORANGE : Color.BLUE); // Orange = congested, Blue = running
                } else {
                    g2d.setColor(Color.GRAY); // Gray = stationary
                }

                // Draw vehicle as scaled triangle (size adapts to map zoom)
                int size = (int) (8 * mapScale);
                size = Math.max(1, size); // Minimum size to ensure visibility
                int[] xs = {x, x - size, x + size};
                int[] ys = {y - size, y + size/2, y + size/2};
                g2d.fillPolygon(xs, ys, 3); // Fill triangle
                g2d.setColor(Color.BLACK);
                g2d.drawPolygon(xs, ys, 3); // Add black border

                // Draw vehicle ID label if enabled (font scaled with map zoom)
                if (showVehicleLabel) {
                    g2d.setColor(Color.BLACK);
                    int fontSize = Math.max(1, (int) (8 * mapScale));
                    g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
                    g2d.drawString(vehicleId, x - 15, y - 25);
                }
            } catch (Exception e) {
                logger.warn("Failed to draw vehicle: vehicleId={}, msg={}", vehicleId, e.getMessage());
                continue;
            }
        }
    }

    /**
     * Inject new vehicle into running simulation with specified route
     */
    public void injectVehicle(String vehicleId, String routeId) throws SumoConnectException {
        try {
            String typeId = "DEFAULT_VEHTYPE"; // Default vehicle type
            String depart = "now"; // Depart immediately

            // Add vehicle to SUMO simulation
            Vehicle.add(vehicleId, routeId, typeId, depart, "free");
            System.out.println("Injected vehicle: " + vehicleId);

        } catch (Exception e) {
            throw new SumoConnectException("Vehicle injection failed: " + e.getMessage());
        }
    }

    /**
     * Control traffic light state (auto/red/green modes)
     */
    public void trafficLightControls(String tlId, String mode) {
        try {
            switch (mode) {
                case "auto":
                    TrafficLight.setProgram(tlId, "0"); // Restore default traffic light program
                    break;
                case "red": {
                    int count = TrafficLight.getControlledLanes(tlId).size();
                    TrafficLight.setRedYellowGreenState(tlId, "r".repeat(count)); // Set all lanes to red
                    break;
                }
                case "green": {
                    int count = TrafficLight.getControlledLanes(tlId).size();
                    TrafficLight.setRedYellowGreenState(tlId, "G".repeat(count)); // Set all lanes to green (SUMO supports uppercase G)
                    break;
                }
            }
            logger.info("Traffic Light " + tlId + " set to " + mode);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn("Error controlling traffic light " + tlId + ": " + e.getMessage());
        }
    }

    /**
     * Zoom map by specified factor (limit zoom range 0.1x to 5.0x)
     */
    public void zoomMap(float scaleFactor) {
        mapScale *= scaleFactor;
        // Clamp zoom scale to valid range (prevent extreme zoom)
        mapScale = Math.max(0.1f, Math.min(5.0f, mapScale));
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint(); // Redraw map with new scale
        }
        logger.info("Map zoomed to {}x", String.format("%.1f", mapScale));
    }

    /**
     * Toggle map pan mode (enable/disable drag-to-pan functionality)
     */
    public void togglePanMode() {
        isPanMode = !isPanMode;
        logger.info("Map pan mode: {}", isPanMode ? "Enabled" : "Disabled");

        // Update mouse cursor to indicate pan mode
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.setCursor(isPanMode ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());
        }
    }

    /**
     * Reset map view to default zoom (1.0x) and position, disable pan mode
     */
    public void resetMapView() {
        mapScale = 1.0f; // Reset zoom to 100%
        isPanMode = false; // Disable pan mode
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.setCursor(Cursor.getDefaultCursor()); // Restore default cursor
            mapCanvas.repaint(); // Redraw map with default settings
        }
        logger.info("Map view has been reset");
    }

    /**
     * Toggle vehicle label display (show/hide vehicle IDs on map)
     */
    public void toggleVehicleLabel() {
        showVehicleLabel = !showVehicleLabel;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint(); // Redraw map to show/hide labels
        }
        logger.info("Vehicle label display: {}", showVehicleLabel ? "Enabled" : "Disabled");
    }

    /**
     * Toggle traffic light status display (show/hide TL IDs on map)
     */
    public void toggleTLStatusLabel() {
        showTLStatus = !showTLStatus;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint(); // Redraw map to show/hide TL labels
        }
        logger.info("Traffic light status display: {}", showTLStatus ? "Enabled" : "Disabled");
    }

    /**
     * Show detailed vehicle data in a scrollable dialog (ID, speed, distance, travel time)
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

            // Build formatted vehicle detail string (monospaced font for alignment)
            StringBuilder detail = new StringBuilder();
            detail.append("==================== Vehicle Detailed Information ====================\n");
            detail.append(String.format("Total Vehicles: %d\n", vehicleIds.size()));
            detail.append("----------------------------------------------------------------------\n");
            // Table header with fixed-width columns for alignment
            detail.append(String.format("%-10s | %-10s | %-10s | %-12s | %-12s\n",
                    "Vehicle ID", "Status", "Speed (km/h)", "Distance (m)", "Travel Time (s)"));
            detail.append("----------------------------------------------------------------------\n");

            // Add each vehicle's data row (formatted for alignment)
            for (String vehicleId : vehicleIds) {
                double speed = Vehicle.getSpeed(vehicleId);
                double distance = Vehicle.getDistance(vehicleId);
                double departTime = Vehicle.getDeparture(vehicleId);
                double travelTime = Simulation.getTime() - departTime;
                String status = speed > 0 ? (speed < 5 ? "Congested" : "Running") : "Static";

                detail.append(String.format("%-10s | %-10s | %-10.1f | %-12.1f | %-12.1f\n",
                        vehicleId, status, speed * 3.6, distance, travelTime));
            }
            detail.append("======================================================================\n");

            // Create scrollable text area for large datasets
            JTextArea textArea = new JTextArea(detail.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Monospaced font for perfect alignment
            textArea.setLineWrap(false); // Disable line wrap to preserve table structure
            textArea.setWrapStyleWord(false);
            textArea.setMargin(new Insets(10, 10, 10, 10)); // Add padding for readability

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(750, 500)); // Optimize dialog size
            scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            JOptionPane.showMessageDialog(mainFrame, scrollPane, "Vehicle Detailed Data", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "Failed to get vehicle details: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            logger.warn("Failed to get vehicle details: {}", e.getMessage());
        }
    }

    /**
     * Get current simulation data and package into SimulationData object (for UI updates)
     */
    private SimulationData getCurrentSimulationData() {
        // Reuse global statistics to avoid redundant SUMO API calls
        List<String> vehicleIds = Vehicle.getIDList();
        List<String> tlIds = TrafficLight.getIDList();
        int vehicleTotal = vehicleIds.size();
        int vehicleRunning = 0;
        int vehicleCongested = 0;
        int tlRed = 0;
        int tlGreen = 0;
        int tlYellow = 0;

        // Calculate vehicle statistics (filter invalid vehicles)
        for (String vehicleId : vehicleIds) {
            try {
                double speedMs = Vehicle.getSpeed(vehicleId);
                double speedKmH = speedMs * 3.6;

                // Filter out vehicles that haven't departed or have arrived
                double departTime = Vehicle.getDeparture(vehicleId);
                int routeIndex = Vehicle.getRouteIndex(vehicleId);
                int routeLength = Vehicle.getRoute(vehicleId).size();
                if (departTime > Simulation.getTime() || routeIndex >= routeLength) {
                    continue;
                }

                // Classify vehicle status
                if (speedMs > 0) {
                    vehicleRunning++;
                    if (speedKmH < CONGESTION_THRESHOLD_KMH) {
                        vehicleCongested++;
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read data for vehicle {} in getCurrentSimulationData: {}", vehicleId, e.getMessage());
                continue;
            }
        }

        // Calculate traffic light statistics
        for (String tlId : tlIds) {
            String tlState = TrafficLight.getRedYellowGreenState(tlId).toLowerCase();
            if (tlState.contains("g")) tlGreen++;
            else if (tlState.contains("y")) tlYellow++;
            else if (tlState.contains("r")) tlRed++;
        }

        // Calculate core metrics (consistent with updateSimulationData logic)
        double avgSpeed = totalVehicleTime > 0 ? (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0) : 0.0;
        double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;
        double simTimeSec = Simulation.getTime();
        String simulationTime = String.format("%02d:%02d:%02d",
                (int) simTimeSec/3600, (int)(simTimeSec%3600)/60, (int)(simTimeSec%60));

        return new SimulationData(
                vehicleTotal, vehicleRunning, vehicleCongested,
                tlIds.size(), tlRed, tlGreen, tlYellow,
                totalSteps, avgSpeed, trafficEfficiency,
                simulationTime
        );
    }

    /**
     * Recursively find component by name in container hierarchy (replaces index-based lookup)
     */
    private Component findComponentByName(Container container, String name) {
        // Iterate through all child components
        for (Component comp : container.getComponents()) {
            if (name.equals(comp.getName())) {
                return comp; // Return immediately if name matches
            }
            // Recursively search child containers (e.g., nested JPanels)
            if (comp instanceof Container) {
                Component found = findComponentByName((Container) comp, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null; // Component not found
    }
}
