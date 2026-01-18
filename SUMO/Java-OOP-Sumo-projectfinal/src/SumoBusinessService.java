import org.eclipse.sumo.libtraci.*;
import org.eclipse.sumo.libtraci.Edge;

import java.awt.BasicStroke;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    // Simulation Data
    private SimulationData lastData;
    private boolean isTranslateMode = false;
    private int translateX = 0;
    private int translateY = 0;
    private Point dragStartPos = null;

    private TraCIPositionVector sumoMapBoundary; // Map boundary stored in TraCIPositionVector
    private Map<String, List<List<TraCIPosition>>> edgeLaneShapesCache = new HashMap<>(); // Edge -> all lane shapes cache


    // Filter
    private String filterMode = "ALL";


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
                if (isTranslateMode && mainFrame.isConnected()) {
                    dragStartPos = e.getPoint();
                    logger.debug("Translate mode: mouse pressed at ({}, {})", e.getX(), e.getY());
                    return;
                }
                if (isPanMode && mainFrame.isConnected()) {
                    lastMousePos = e.getPoint();
                    logger.debug("Pan mode: mouse pressed at ({}, {})", e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStartPos = null;
                lastMousePos = null;
                logger.debug("Pan mode: mouse released");
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (isTranslateMode) {
                    logger.debug("Entered map canvas (translate mode enabled)");
                } else if (isPanMode) {
                    logger.debug("Entered map canvas (pan mode enabled)");
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (isTranslateMode) {
                    logger.debug("Exited map canvas (translate mode enabled)");
                } else if (isPanMode) {
                    logger.debug("Exited map canvas (pan mode enabled)");
                }
            }
        });

        // Mouse drag listener: Calculate pan offset and refresh map
        mapCanvas.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartPos != null && mainFrame.isConnected()) {
                    int dx = e.getX() - dragStartPos.x;
                    int dy = e.getY() - dragStartPos.y;
                    translateX += dx;
                    translateY += dy;
                    dragStartPos = e.getPoint();
                    logger.debug("Translate mode: dragged offset (dx={}, dy={}), total translate ({}, {})",
                            dx, dy, translateX, translateY);
                    mapCanvas.repaint();
                    return;
                }
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
     * Retrieves the map canvas panel from the frame's content hierarchy.
     * Optimized with flattened logic and specific type validation.
     */
    private JPanel getMapCanvas() {
        // 1. Traverse the UI tree to find the component by its assigned name
        Component comp = findComponentByName(mainFrame.getContentPane(), "mapCanvas");

        // 2. Early exit if the component does not exist to avoid further processing
        if (comp == null) {
            logger.warn("Map canvas lookup failed: No component with name 'mapCanvas' found");
            return null;
        }

        // 3. Ensure the found component is actually a JPanel before casting
        // This prevents ClassCastException at runtime
        if (!(comp instanceof JPanel)) {
            logger.error("Type mismatch: Expected JPanel for 'mapCanvas', but found {}", comp.getClass().getName());
            return null;
        }

        // 4. Return the successfully validated JPanel
        return (JPanel) comp;
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

        // 1. Basic Parameter Validation (Check inputs BEFORE heavy parsing)
        String configPath = mainFrame.getConfigPath();
        String sumoPath = mainFrame.getSumoGuiPath();

        if (configPath == null || configPath.isEmpty() || sumoPath == null || sumoPath.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "Config file or SUMO path is missing!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. Validate Executable (Check if it's actually sumo-gui.exe)
        File sumoFile = new File(sumoPath);
        if (!sumoFile.exists() || !sumoFile.getName().endsWith("sumo-gui.exe")) {
            JOptionPane.showMessageDialog(mainFrame, "Invalid SUMO executable path!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 3. Heavy IO & Startup in background thread to keep UI responsive
        new Thread(() -> {
            try {
                // Parse network file path from SUMO config
                // We do this inside the thread as it involves XML parsing (IO bound)
                String netFilePath = SumoCfgParser.parseNetFilePath(configPath);
                if (!new File(netFilePath).exists()) {
                    throw new IOException("Network file defined in config does not exist: " + netFilePath);
                }

                // Prepare command line arguments for TraCI
                // "-c" is sufficient as the config file points to all other required files
                String[] args = {
                        sumoPath,
                        "-c", configPath,
                        "--start"
                };

                logger.debug("SUMO command: {}", String.join(" ", args));

                // Native Library Loading & Process Start
                Simulation.preloadLibraries();
                Simulation.start(new StringVector(args));

                // Retrieve map dimensions for coordinate mapping and centering
                sumoMapBoundary = Simulation.getNetBoundary();

                // Update UI components on the Event Dispatch Thread (EDT)
                SwingUtilities.invokeLater(() -> {
                    mainFrame.updateSumoConnectionStatus(true);
                    preloadRoadNetworkData(); // Cache static road geometry
                    initMapPanListener();      // Enable interactive map controls
                    logger.info("SUMO TraCI connection established successfully!");
                });

            } catch (Exception e) {
                logger.error("SUMO startup failed: {}", e.getMessage());
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(mainFrame, "Connection Error: " + e.getMessage())
                );
            }
        }).start();
    }

    /**
     * Disconnect from SUMO: Stop simulation, close connection, and reset state
     */
    public void disconnectSumo() {
        // 1. Skip if there is no active connection
        if (!mainFrame.isConnected()) {
            logger.info("No active SUMO connection to disconnect.");
            return;
        }
        try {
            // 2. Stop continuous simulation loops first to prevent TraCI calls after closure
            if (mainFrame.getisContinuousRunning()) {
                mainFrame.stopContinuousSimulation();
            }

            // 3. Terminate the TraCI session and close the SUMO-GUI process
            Simulation.close();

            // 4. Reset internal map state (zoom, offsets) to initial values
            resetMapView();

            // 5. Update UI components to reflect the disconnected state
            mainFrame.updateSumoConnectionStatus(false);

            logger.info("SUMO TraCI connection closed successfully.");
        } catch (Exception e) {
            // Use e.toString() or logger's built-in handling to avoid manual null checks on getMessage()
            String errorMsg = "Failed to close SUMO connection: " + e.toString();
            JOptionPane.showMessageDialog(mainFrame, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
            logger.error(errorMsg);
        }
    }

    /**
     * Execute single simulation step and update statistical data
     */
    /**
     * Advances the simulation by a single time step and updates statistics
     */
    public void stepSimulation() {
        // 1.Ensure TraCI connection is active before proceeding
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // 2. Execute one simulation step (typically 1 second in SUMO)
            Simulation.step();
            totalSteps++;
            logger.info("Simulation progressed to step: {}", totalSteps);
            // 3. Trigger UI update with newly calculated traffic data
            updateSimulationData();
        } catch (Exception e) {
            // Simplified error message retrieval using e.toString() to avoid manual null checks
            String errorMsg = "Single-step failed: " + e.toString();
            JOptionPane.showMessageDialog(mainFrame, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
            logger.error(errorMsg);
            // 4. Critical: Handle session interruption (e.g., SUMO-GUI closed by user)
            // Check for "Connection reset" or "Broken pipe" which indicate socket closure
            if (e.getMessage() != null && (e.getMessage().contains("Connection reset") || e.getMessage().contains("pipe"))) {
                mainFrame.updateSumoConnectionStatus(false);
                logger.warn("SUMO TraCI connection has been interrupted and marked as disconnected.");
            }
        }
    }

    /**
     * Reset simulation to initial state (restart SUMO and clear statistics)
     */
    public void resetSimulation() {
        // 1.  Ensure connection exists before attempting reset
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "Please connect to SUMO first!", "Prompt", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int option = JOptionPane.showConfirmDialog(mainFrame, "Reset simulation to its initial state?",
                "Reset Confirmation", JOptionPane.YES_NO_OPTION);

        if (option != JOptionPane.YES_OPTION) return;

        // 2. Pre-reset Cleanup on UI Thread: Stop ongoing simulation loops
        if (mainFrame.getisContinuousRunning()) {
            mainFrame.stopContinuousSimulation();
        }

        // 3. Execute restart in background thread to keep UI responsive
        new Thread(() -> {
            try {
                // Close existing TraCI session and kill the SUMO process
                Simulation.close();

                // Prepare arguments: Use config path as the primary source of truth
                String[] args = {
                        mainFrame.getSumoGuiPath(),
                        "-c", mainFrame.getConfigPath(),
                        "--start" // Ensures the new simulation window starts automatically
                };

                // Restart the simulation engine
                Simulation.start(new StringVector(args));

                // 4. State Reset: Zero out internal counters and statistics
                totalSteps = 0;
                totalVehicleDistance = 0;
                totalVehicleTime = 0;

                // 5. Post-reset UI Sync: Refresh dashboard with initial data
                SwingUtilities.invokeLater(() -> {
                    logger.info("The simulation has been reset to its initial state successfully.");
                    updateSimulationData(); // Refresh labels with zeroed stats
                });

            } catch (Exception e) {
                String errorMsg = "Reset failed: " + e.toString();
                logger.error(errorMsg);
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(mainFrame, errorMsg, "Error", JOptionPane.ERROR_MESSAGE)
                );
            }
        }).start();
    }
    /**
     * Core data processing method: Fetch simulation data, calculate statistics, and update UI
     */
    private void updateSimulationData() {
        try {
            // 1. Fetch current basic simulation environment parameters
            List<String> vehicleIds = Vehicle.getIDList();
            int vehicleTotal = vehicleIds.size();
            int vehicleRunning = 0;
            int vehicleCongested = 0;
            double currentSimTime = Simulation.getTime();

            // Process vehicle statistics
            for (String vehicleId : vehicleIds) {
                try {
                    double speedMs = Vehicle.getSpeed(vehicleId);

                    // Redundancy Clean: Moved calculations outside the loop if they aren't used for filtering
                    // Accumulate distance and time for average speed (Global stats)
                    double vehicleDistance = Vehicle.getDistance(vehicleId);
                    double departTime = Vehicle.getDeparture(vehicleId);

                    totalVehicleDistance += Math.max(0, vehicleDistance);
                    totalVehicleTime += Math.max(0, currentSimTime - departTime);

                    // Vehicle state classification using raw m/s to avoid repeated float multiplication
                    if (speedMs > 0) {
                        vehicleRunning++;
                        // Convert threshold to m/s once or compare in km/h only when needed
                        if (speedMs * 3.6 < CONGESTION_THRESHOLD_KMH) {
                            vehicleCongested++;
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Skip vehicle {}: {}", vehicleId, e.getMessage());
                }
            }

            // 2. Fetch and deduplicate traffic light data
            List<String> tlIds = TrafficLight.getIDList();
            int tlTotal = 0, tlRed = 0, tlGreen = 0, tlYellow = 0;

            for (String tlId : tlIds) {
                try {
                    StringVector lanes = TrafficLight.getControlledLanes(tlId);
                    String state = TrafficLight.getRedYellowGreenState(tlId).toLowerCase();

                    // Use a local Set for Edge-based deduplication per traffic light controller
                    Set<String> countedEdges = new HashSet<>();

                    for (int i = 0; i < lanes.size() && i < state.length(); i++) {
                        String edgeId = lanes.get(i).split("_")[0];

                        if (countedEdges.add(edgeId)) { // HashSet.add returns false if item exists (More efficient)
                            tlTotal++;
                            switch (state.charAt(i)) {
                                case 'g' -> tlGreen++;
                                case 'y' -> tlYellow++;
                                case 'r', 'u' -> tlRed++;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("TL error {}: {}", tlId, e.getMessage());
                }
            }

            // 3. Final metrics calculation (Normalization and Formatting)
            double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;
            double avgSpeed = (totalVehicleTime > 0) ?
                    (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0) : 0.0;

            // Optimized time formatting: Using integer division and modulo
            int totalSec = (int) currentSimTime;
            String simulationTime = String.format("%02d:%02d:%02d",
                    totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60);

            // 4. Encapsulate and synchronize with UI
            lastData = new SimulationData(
                    vehicleTotal, vehicleRunning, vehicleCongested,
                    tlTotal, tlRed, tlGreen, tlYellow,
                    totalSteps, avgSpeed, trafficEfficiency,
                    simulationTime
            );

            mainFrame.updateDashboard(lastData);

        } catch (Exception e) {
            logger.error("Global data update failure: {}", e.toString());
        }
    }

    /**
     * Draw simulation map with zoom, pan, and core elements (roads, vehicles, traffic lights)
     */
    public void drawMap(Graphics g, Dimension canvasSize) {
        // 1. Skip drawing if no active connection or valid geometry data
        if (!mainFrame.isConnected() || sumoMapBoundary.getValue().size() < 2) {
            return;
        }

        Graphics2D g2d = (Graphics2D) g;
        // Save original state to ensure UI elements drawn after the map are not affected
        AffineTransform originalTransform = g2d.getTransform();

        try {
            // Extract boundary points for the simulation world
            TraCIPosition minPos = sumoMapBoundary.getValue().get(0);
            TraCIPosition maxPos = sumoMapBoundary.getValue().get(1);

            // 2. Simplified Centering Logic:
            // Calculate the center point of the SUMO map in world coordinates
            double centerX = (minPos.getX() + maxPos.getX()) / 2.0;
            double centerY = (minPos.getY() + maxPos.getY()) / 2.0;

            // Transform world coordinates to screen center with pan (translateX/Y) and zoom (mapScale)
            // Note: SUMO's Y-axis is inverted relative to Java's Graphics2D coordinate system
            double offsetX = canvasSize.width / 2.0 - centerX * mapScale + translateX;
            double offsetY = canvasSize.height / 2.0 + centerY * mapScale + translateY;

            // 3. Transformation Sequence
            g2d.translate(offsetX, offsetY);
            g2d.scale(mapScale, mapScale);

            // Execute layered rendering (Bottom to Top)
            drawRoadNetwork(g2d);   // Static geometry
            drawTrafficLights(g2d); // Signal overlays
            drawVehicles(g2d);      // Dynamic entities

        } catch (Exception e) {
            logger.warn("Map rendering interrupted: {}", e.getMessage());
        } finally {
            // Ensure the graphics context is restored even if an error occurs during drawing
            g2d.setTransform(originalTransform);
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
            // Set default road color (matching SUMO's light gray style)
            g2d.setColor(new Color(180, 180, 180));

            for (String edgeId : edgeIds) {
                // Retrieve geometry for all lanes belonging to this edge
                List<List<TraCIPosition>> allLaneShapes = getAllLaneShapes(edgeId);

                for (int laneIdx = 0; laneIdx < allLaneShapes.size(); laneIdx++) {
                    List<TraCIPosition> laneShape = allLaneShapes.get(laneIdx);
                    if (laneShape.size() < 2) continue;

                    // 1. Coordinate Projection: Pre-allocate arrays for Polyline rendering
                    int nPoints = laneShape.size();
                    int[] xPoints = new int[nPoints];
                    int[] yPoints = new int[nPoints];

                    for (int j = 0; j < nPoints; j++) {
                        TraCIPosition pos = laneShape.get(j);
                        // Map simulation world coordinates to screen space
                        xPoints[j] = (int) (pos.getX() * mapScale);
                        // Critical: Invert Y-axis because SUMO Y grows upward, while UI Y grows downward
                        yPoints[j] = (int) (-pos.getY() * mapScale);
                    }

                    // 2. Stroke Optimization:
                    // We use the edge index to fetch lane width only once if possible (Logic assumed)
                    String laneId = edgeId + "_" + laneIdx;
                    double laneWidth = Lane.getWidth(laneId);

                    // Scale physical lane width to pixel width, ensuring at least 1 pixel is visible
                    float strokeWidth = Math.max((float) (laneWidth * mapScale), 1.0f);

                    // 3. Dynamic Stroke Styling: Apply width to the Graphics context
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2d.drawPolyline(xPoints, yPoints, nPoints);
                }
            }
        } catch (Exception e) {
            logger.error("Road network rendering failed: {}", e.toString());
        }
    }

    /**
     * Draw traffic lights with color-coded status (red/green/yellow) and optional ID labels
     */
    private void drawTrafficLights(Graphics2D g2d) {
        List<String> tlIds = TrafficLight.getIDList();

        for (String tlId : tlIds) {
            try {
                StringVector lanes = TrafficLight.getControlledLanes(tlId);
                String state = TrafficLight.getRedYellowGreenState(tlId).toLowerCase();

                // Iterate through controlled lanes, synchronized with the state string
                for (int i = 0; i < lanes.size() && i < state.length(); i++) {
                    String laneId = lanes.get(i);
                    char s = state.charAt(i);

                    // 1. Get physical position (Prioritize using lane geometry)
                    Point2D.Double pos = getSignalPosition(laneId);

                    if (pos == null) {
                        // Fallback: If lane shape is unavailable, use junction position with manual offset
                        String node = Edge.getToJunction(laneId.split("_")[0]);
                        TraCIPosition junc = Junction.getPosition(node);
                        pos = new Point2D.Double(junc.getX() + (i-2)*2, junc.getY() + (i-2)*2);
                    }

                    // 2. Coordinate Transformation (Apply scale and invert Y-axis for screen rendering)
                    int sx = (int) (pos.x * mapScale);
                    int sy = (int) (-pos.y * mapScale);

                    // Map SUMO state characters to UI colors using modern Switch expression
                    g2d.setColor(switch (s) {
                        case 'g' -> Color.GREEN;
                        case 'y' -> Color.YELLOW;
                        case 'r' -> Color.RED;
                        default  -> Color.GRAY;
                    });

                    // Render the circular signal icon with a dynamic size clamped between 3 and 10px
                    int size = Math.max(3, Math.min(10, (int)(6 * mapScale)));
                    g2d.fillOval(sx - size/2, sy - size/2, size, size);

                    // Draw a black outline for better contrast on the map
                    g2d.setColor(Color.BLACK);
                    g2d.drawOval(sx - size/2, sy - size/2, size - 1, size - 1);

                    if (showTLStatus) {
                        g2d.setFont(new Font("Arial", Font.PLAIN, Math.max(5, (int)(6 * mapScale))));
                        g2d.drawString(laneId.split("_")[0], sx - 10, sy - 12);
                    }
                }
            } catch (Exception e) {
                logger.warn("Draw error {}: {}", tlId, e.getMessage());
            }
        }
    }

    /**
     * Helper method for geometric calculations: calculates the signal position with lateral offset
     */
    private Point2D.Double getSignalPosition(String laneId) {
        try {
            List<TraCIPosition> points = Lane.getShape(laneId).getValue();
            if (points.size() < 2) return null;

            // Use the last segment of the lane to determine direction and placement
            TraCIPosition pLast = points.get(points.size() - 1);
            TraCIPosition pPrev = points.get(points.size() - 2);

            double dx = pLast.getX() - pPrev.getX();
            double dy = pLast.getY() - pPrev.getY();
            double len = Math.hypot(dx, dy);

            // Calculate back-off position (place the light ~2 meters before the lane end)
            double ratio = (len > 2.0) ? (2.0 / len) : 0;
            double bx = pLast.getX() - ratio * dx;
            double by = pLast.getY() - ratio * dy;

            // Apply lateral offset (using normal vector) to prevent icons from overlapping at junctions
            if (len > 0) {
                bx += (-dy / len) * 1.5;
                by += (dx / len) * 1.5;
            }
            return new Point2D.Double(bx, by);
        } catch (Exception e) {
            return null; // Return null if geometry data cannot be fetched
        }
    }


    /**
     * Setter for the  filter mode
     */
    public void setFilterMode(String mode) {
        this.filterMode = mode;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) mapCanvas.repaint();
    }

    /**
     * Checks whether a vehicle matches the currently selected filter mode
     */
    private boolean vehiclePassesFilter(String id) {
        try {
            // Retrieve the current speed of the vehicle
            double speed = Vehicle.getSpeed(id);

            // Apply filtering logic based on the active filter mode
            switch (filterMode) {
                case "Running":
                    // Vehicle is considered running if its speed is greater than zero
                    return speed > 0;

                case "Congested":
                    // Vehicle is congested if it is moving slowly
                    return speed > 0 && speed < 5;

                default:
                    // No filter applied: always include the vehicle
                    return true;
            }
        } catch (Exception e) {
            // Fail-safe behavior: include the vehicle if an error occurs
            return true;
        }
    }



    /**
     * Draw vehicles with color-coded status (running/congested/static) and optional ID labels
     */
    private void drawVehicles(Graphics2D g2d) {
        List<String> vehicleIds = Vehicle.getIDList();

        // Save the global transform to restore it after drawing all vehicles
        AffineTransform globalTransform = g2d.getTransform();

        for (String vehicleId : vehicleIds) {
            // 1. Filtering Logic
            if (!vehiclePassesFilter(vehicleId)) continue;

            try {
                TraCIPosition pos = Vehicle.getPosition(vehicleId);
                double angle = Vehicle.getAngle(vehicleId); // Heading angle in degrees from SUMO
                double speed = Vehicle.getSpeed(vehicleId);

                // Map world coordinates to screen space
                int x = (int) (pos.getX() * mapScale);
                int y = (int) (-pos.getY() * mapScale);

                // 2. Set vehicle color based on movement state
                if (speed <= 0) {
                    g2d.setColor(Color.GRAY);
                } else {
                    g2d.setColor(speed < 5 ? Color.ORANGE : Color.BLUE);
                }

                // 3. Apply Local Transformation for Rotation
                // Save the current state before rotating this specific vehicle
                AffineTransform localVehicleTransform = g2d.getTransform();

                g2d.translate(x, y); // Move origin to the vehicle's position
                // SUMO angle is clockwise from North (0°).
                // Java's rotate() is clockwise from positive X-axis.
                // Usually, we subtract 90 degrees or adjust based on your map orientation.
                g2d.rotate(Math.toRadians(angle));

                // 4. Draw the Rotated Vehicle Shape (Triangle)
                // Now we draw relative to (0,0) because the canvas is translated and rotated
                int size = Math.max(2, (int) (8 * mapScale));

                // Triangle pointing "Forward" (upward in local coordinate system)
                int[] xs = {0, -size / 2, size / 2};
                int[] ys = {-size, size / 2, size / 2};

                g2d.fillPolygon(xs, ys, 3);
                g2d.setColor(Color.BLACK);
                g2d.drawPolygon(xs, ys, 3);

                // 5. Restore Local Transformation for the next vehicle
                g2d.setTransform(localVehicleTransform);

                // 6. Draw Label (Optional: usually not rotated for readability)
                if (showVehicleLabel) {
                    g2d.setColor(Color.BLACK);
                    int fontSize = Math.max(5, (int) (6 * mapScale));
                    g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
                    g2d.drawString(vehicleId, x + size, y - size);
                }

            } catch (Exception e) {
                // Silently skip to maintain smooth simulation rendering
                continue;
            }
        }
        // Final restoration of the original coordinate system
        g2d.setTransform(globalTransform);
    }




    /**
     * Inject new vehicle into running simulation with specified route
     */
    public void injectVehiclesAdvanced(String edgeId, String routeId,
                                       double speedKmh,
                                       int batchCount) {
        try {
            // Create the specified number of vehicles
            for (int i = 0; i < batchCount; i++) {
                // Generate a unique vehicle ID using timestamp and index
                String vehId = "veh" + System.currentTimeMillis() + "_" + i;
                String typeId = "DEFAULT_VEHTYPE";
                String departLane = "0";

                // Build lane ID for the target edge (default lane 0)
                String laneId = edgeId + "_0";

                // Add the vehicle to the simulation with immediate departure
                Vehicle.add(vehId,
                        routeId,
                        typeId,
                        "now",
                        departLane,
                        "free",
                        "0.0",
                        "current",
                        "0",
                        "0");

                // Try to move the vehicle to the specified edge and lane
                try {
                    Vehicle.moveTo(vehId, laneId, 0.0);
                } catch (Exception e1) {
                    // Fallback: try an alternative lane if lane 0 does not exist
                    logger.info("Lane 0 not found -> try Edge _1");
                    try {
                        Vehicle.moveTo(vehId, edgeId + "_1", 0.0);
                    } catch (Exception e2) {
                        // Final fallback: vehicle remains at default position
                        logger.info("Edge have no valid Lanes → Vehicle set on Default-Position");
                    }
                }
                // Log successful vehicle creation
                logger.info("Created vehicle: " + vehId);
            }

        } catch (Exception e) {
            // Log and print any unexpected errors during vehicle injection
            e.printStackTrace();
            logger.error("Error injecting vehicles: " + e.getMessage());
        }
    }

    /**
     * Get List with Taffic Lights that shows which lanes are controlles
     */
    public List<SumoTrafficLights> getAllTrafficLightObjects() {
        List<SumoTrafficLights> tlList = new ArrayList<>();

        try {
            List<String> tlIDs = TrafficLight.getIDList();
            for (String id : tlIDs) {

                List<String> lanes = TrafficLight.getControlledLanes(id);
                if (lanes == null || lanes.isEmpty()){
                    continue;
                }

                SumoTrafficLights tlObj = new SumoTrafficLights(id, lanes);

                tlList.add(tlObj);
            }
        } catch (Exception e) {
            logger.error("Error loading Traffic Lights");
        }
        return tlList;
    }

    /**
     * Toggles the map translation (panning) mode.
     * Switches between the default pointer and a move cursor,
     * and updates the cursor visual on the map canvas.
     */
    public void toggleTranslateMode() {
        // Flip the boolean state
        isTranslateMode = !isTranslateMode;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            // Set cursor to 'MOVE' (hand icon) if enabled, otherwise return to default
            Cursor cursor = isTranslateMode ?
                    Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) :
                    Cursor.getDefaultCursor();
            mapCanvas.setCursor(cursor);
        }
        // Log the current state for debugging/tracking
        logger.info("Translate mode: {}", isTranslateMode ? "Enabled" : "Disabled");
    }

    /**
     * Resets the translation coordinates to their origin.
     * This effectively centers the view or clears any panning offsets.
     */
    public void resetTranslate() {
        translateX = 0;
        translateY = 0;
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
     * Reset map view to default zoom (1.0x) and position, disable pan mode
     */
    public void resetMapView() {
        mapScale = 1.0f; // Reset zoom to 100%
        isPanMode = false; // Disable pan mode
        resetTranslate();
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
        // 1. Guard Clause: Fast exit if not connected
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

            // 2. Pre-fetch Simulation Time: Call TraCI once instead of inside the loop
            double currentTime = Simulation.getTime();

            // Optimize StringBuilder with an estimated capacity to prevent multiple re-allocations
            StringBuilder detail = new StringBuilder(vehicleIds.size() * 80);

            detail.append("==================== Vehicle Detailed Information ====================\n");
            detail.append(String.format("Total Active Vehicles: %d | Time: %.1fs\n", vehicleIds.size(), currentTime));
            detail.append("----------------------------------------------------------------------\n");
            detail.append(String.format("%-12s | %-10s | %-12s | %-12s | %-12s\n",
                    "Vehicle ID", "Status", "Speed(km/h)", "Distance(m)", "TravelTime(s)"));
            detail.append("----------------------------------------------------------------------\n");

            for (String vehicleId : vehicleIds) {
                try {
                    // Fetch basic state in one go
                    double speedMs = Vehicle.getSpeed(vehicleId);
                    double distance = Vehicle.getDistance(vehicleId);
                    double departTime = Vehicle.getDeparture(vehicleId);

                    // 3. Logic Optimization: Simplified status check and travel time calculation
                    double travelTime = Math.max(0, currentTime - departTime);
                    String status = (speedMs <= 0) ? "Static" : (speedMs < 1.38 ? "Congested" : "Running"); // 1.38m/s ≈ 5km/h

                    detail.append(String.format("%-12s | %-10s | %-12.1f | %-12.1f | %-12.1f\n",
                            vehicleId, status, speedMs * 3.6, distance, travelTime));
                } catch (Exception inner) {
                    // If a specific vehicle leaves during processing, skip it
                    continue;
                }
            }
            detail.append("======================================================================\n");

            // 4. UI Components: Configured for better data visualization
            JTextArea textArea = new JTextArea(detail.toString());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", Font.PLAIN, 12)); // Essential for column alignment
            textArea.setMargin(new Insets(10, 10, 10, 10));

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(800, 500));

            JOptionPane.showMessageDialog(mainFrame, scrollPane, "Vehicle Detailed Data", JOptionPane.PLAIN_MESSAGE);

        } catch (Exception e) {
            String errorMsg = "Detail retrieval failed: " + e.toString();
            logger.error(errorMsg);
            JOptionPane.showMessageDialog(mainFrame, errorMsg, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Export Simulation data
     */
    public void exportSimulationStats() {
        try {
            // Ensure that simulation data is available before exporting
            if (lastData == null) {
                System.out.println("No simulation data collected yet — nothing to export.");
                return;
            }

            // Generate timestamp for the export file name
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            // Open file chooser to let the user select the export location
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Export Simulation Statistics");

            chooser.setSelectedFile(new File("simulation_stats" + timestamp + ".csv"));

            int result = chooser.showSaveDialog(null);
            if (result != JFileChooser.APPROVE_OPTION) {
                // Abort export if the user cancels the dialog
                return;
            }

            File file = chooser.getSelectedFile();

            // Write simulation statistics to CSV file
            try (FileWriter fw = new FileWriter(file)) {

                // Write CSV header
                fw.write("Metric,Value\n");

                // Write vehicle-related statistics
                fw.write("Total Vehicles," + lastData.getVehicleTotal() + "\n");
                fw.write("Running Vehicles," + lastData.getVehicleRunning() + "\n");
                fw.write("Congested Vehicles," + lastData.getVehicleCongested() + "\n");

                // Write traffic light statistics
                fw.write("Traffic Lights Total," + lastData.getTlTotal() + "\n");
                fw.write("Red Lights," + lastData.getTlRed() + "\n");
                fw.write("Green Lights," + lastData.getTlGreen() + "\n");
                fw.write("Yellow Lights," + lastData.getTlYellow() + "\n");

                // Write performance metrics
                fw.write("Average Speed (km/h)," + lastData.getAvgSpeed() + "\n");
                fw.write("Traffic Efficiency (%)," + lastData.getTrafficEfficiency() + "\n");
                fw.write("Simulation Time," + lastData.getSimulationTime() + "\n");

            }
            // Confirm successful export
            logger.info("CSV export completed: " + file.getAbsolutePath());

        } catch (IOException io) {
            // Handle file I/O errors
            io.printStackTrace();
            logger.info("Failed to export CSV: " + io.getMessage());

        } catch (Exception e) {
            // Handle any unexpected runtime errors
            e.printStackTrace();
            logger.info("Unexpected error during export: " + e.getMessage());
        }
    }

    /**
     * Get current simulation data and package into SimulationData object (for UI updates)
     */
    public SimulationData getCurrentSimulationData() {
        // 1. Efficiency Optimization:
        // If updateSimulationData() was just called, return the cached lastData
        // directly to avoid thousands of redundant TraCI socket calls.
        if (lastData != null) {
            return lastData;
        }

        // 2. Fallback Logic: (Only if lastData is null, use consistent logic)
        try {
            List<String> vehicleIds = Vehicle.getIDList();
            int vehicleTotal = vehicleIds.size();
            int vehicleRunning = 0;
            int vehicleCongested = 0;

            for (String vehicleId : vehicleIds) {
                try {
                    double speedMs = Vehicle.getSpeed(vehicleId);
                    if (speedMs > 0) {
                        vehicleRunning++;
                        if (speedMs * 3.6 < CONGESTION_THRESHOLD_KMH) vehicleCongested++;
                    }
                } catch (Exception e) { continue; }
            }

            // 3. Traffic Light Logic (Must match the Edge-based deduplication)
            List<String> tlIds = TrafficLight.getIDList();
            int tlTotal = 0, tlRed = 0, tlGreen = 0, tlYellow = 0;

            for (String tlId : tlIds) {
                StringVector lanes = TrafficLight.getControlledLanes(tlId);
                String state = TrafficLight.getRedYellowGreenState(tlId).toLowerCase();
                Set<String> countedEdges = new HashSet<>();

                for (int i = 0; i < lanes.size() && i < state.length(); i++) {
                    String edgeId = lanes.get(i).split("_")[0];
                    if (countedEdges.add(edgeId)) { // Deduplicate by edge
                        tlTotal++;
                        switch (state.charAt(i)) {
                            case 'g' -> tlGreen++;
                            case 'y' -> tlYellow++;
                            case 'r', 'u' -> tlRed++;
                        }
                    }
                }
            }

            // 4. Metrics Calculation (Consistent with global variables)
            double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;
            double avgSpeed = totalVehicleTime > 0 ? (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0) : 0.0;

            double simTimeSec = Simulation.getTime();
            String simulationTime = String.format("%02d:%02d:%02d",
                    (int)simTimeSec/3600, (int)(simTimeSec%3600)/60, (int)(simTimeSec%60));

            return new SimulationData(
                    vehicleTotal, vehicleRunning, vehicleCongested,
                    tlTotal, tlRed, tlGreen, tlYellow,
                    totalSteps, avgSpeed, trafficEfficiency,
                    simulationTime
            );
        } catch (Exception e) {
            logger.error("Failed to fetch current simulation data: {}", e.getMessage());
            return null;
        }
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
