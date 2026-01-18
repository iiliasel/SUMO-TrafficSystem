/**
 * Simulation data encapsulation class:
 *  to pass all statistical data required by the dashboard (including grouping/filtering results)
 */
public class SimulationData {
    private final int vehicleTotal;       // Total number of vehicles
    private final int vehicleRunning;     // Number of running vehicles
    private final int vehicleCongested;   // Number of congested vehicles
    private final int tlTotal;            // Total number of traffic lights
    private final int tlRed;              // Number of red traffic lights
    private final int tlGreen;            // Number of green traffic lights
    private final int tlYellow;           // Number of yellow traffic lights
    private final int totalSteps;         // Total simulation steps
    private final double avgSpeed;        // Average speed (km/h)
    private final double trafficEfficiency; // Traffic efficiency
    private final String simulationTime;  // Simulation time (HH:MM:SS)


    public SimulationData(
            int vehicleTotal, int vehicleRunning, int vehicleCongested,
            int tlTotal, int tlRed, int tlGreen, int tlYellow,
            int totalSteps, double avgSpeed, double trafficEfficiency,
            String simulationTime
    ) {
        this.vehicleTotal = vehicleTotal;
        this.vehicleRunning = vehicleRunning;
        this.vehicleCongested = vehicleCongested;
        this.tlTotal = tlTotal;
        this.tlRed = tlRed;
        this.tlGreen = tlGreen;
        this.tlYellow = tlYellow;
        this.totalSteps = totalSteps;
        this.avgSpeed = avgSpeed;
        this.trafficEfficiency = trafficEfficiency;
        this.simulationTime = simulationTime;
    }

    public int getVehicleTotal() { return vehicleTotal; }
    public int getVehicleRunning() { return vehicleRunning; }
    public int getVehicleCongested() { return vehicleCongested; }
    public int getTlTotal() { return tlTotal; }
    public int getTlRed() { return tlRed; }
    public int getTlGreen() { return tlGreen; }
    public int getTlYellow() { return tlYellow; }
    public int getTotalSteps() { return totalSteps; }
    public double getAvgSpeed() { return avgSpeed; }
    public double getTrafficEfficiency() { return trafficEfficiency; }
    public String getSimulationTime() { return simulationTime; }
}

