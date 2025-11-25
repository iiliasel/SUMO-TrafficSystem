/**
 * 仿真数据封装类，用于传递仪表盘所需的所有统计数据
 */
public class SimulationData {
    // 车辆相关数据
    private int vehicleTotal;       // 总车辆数
    private int vehicleRunning;     // 运行中车辆数
    private int vehicleCongested;   // 拥堵车辆数

    // 交通灯相关数据
    private int tlTotal;            // 总交通灯数
    private int tlRed;              // 红灯交通灯数
    private int tlGreen;            // 绿灯交通灯数
    private int tlYellow;           // 黄灯交通灯数

    // 仿真统计数据
    private int totalSteps;         // 总步数
    private double avgSpeed;        // 平均车速（km/h）
    private double trafficEfficiency; // 通行效率（%）
    private String simulationTime;  // 仿真时间（HH:MM:SS）

    // 构造方法：接收所有数据并初始化
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

    // 所有属性的 getter 方法（供 updateDashboard 调用）
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