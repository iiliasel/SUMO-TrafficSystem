/**
 * Simulation data encapsulation class:
 *  to pass all statistical data required by the dashboard
 */
public class SimulationData {
    // Vehicle-related Data
    private int vehicleTotal;       // total vehicles
    private int vehicleRunning;     // number of running vehicles
    private int vehicleCongested;   // number of congested vehicles

    // Traffic-light related data
    private int tlTotal;            // total lights
    private int tlRed;
    private int tlGreen;
    private int tlYellow;

    // Simulation statistical data
    private int totalSteps;         // total steps
    private double avgSpeed;        // average speed（km/h）
    private double trafficEfficiency;
    private String simulationTime;  // simulation time（HH:MM:SS）

    // constructor:receive all data and initializes
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

    // getter methods:for updateDashboard to call
    public int getVehicleTotal() {
        return vehicleTotal;
    }

    public int getVehicleRunning() {
        return vehicleRunning;
    }

    public int getVehicleCongested() {
        return vehicleCongested;
    }

    public int getTlTotal() {
        return tlTotal;
    }

    public int getTlRed() {
        return tlRed;
    }

    public int getTlGreen() {
        return tlGreen;
    }

    public int getTlYellow() {
        return tlYellow;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public double getTrafficEfficiency() {
        return trafficEfficiency;
    }

    public String getSimulationTime() {
        return simulationTime;
    }
}