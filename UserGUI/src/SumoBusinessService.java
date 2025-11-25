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
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * SUMO业务逻辑服务类
 * 职责：封装所有业务逻辑，与界面类解耦，提供界面调用的服务接口
 */
public class SumoBusinessService {
    // 界面引用（仅用于更新界面状态和获取配置参数，不侵入界面逻辑）
    private final SumoMainFrame mainFrame;

    // 地图相关核心参数
    private float mapScale = 1.0f; // 地图缩放比例（范围0.1-5.0）
    private Point mapOffset = new Point(0, 0); // 地图平移偏移量
    private boolean isPanMode = false; // 地图平移模式开关
    private Point lastMousePos = null; // 上次鼠标位置（用于计算平移距离）
    private boolean showVehicleLabel = true; // 车辆标签显示开关
    private boolean showTLStatus = true; // 交通灯状态显示开关

    // 仿真数据统计核心变量
    private int totalSteps = 0; // 总仿真步数
    private long totalVehicleDistance = 0; // 所有车辆总行驶距离（单位：m）
    private long totalVehicleTime = 0; // 所有车辆总行驶时间（单位：s）

    // 自定义异常：SUMO连接相关异常
    public static class SumoConnectException extends Exception {
        public SumoConnectException(String message) {
            super(message);
        }
    }

    /**
     * 构造方法：注入界面实例，初始化依赖
     * @param mainFrame 主窗口界面实例
     */
    public SumoBusinessService(SumoMainFrame mainFrame) {
        this.mainFrame = mainFrame;
        // 初始化地图平移监听（绑定到界面画布）
        initMapPanListener();
    }

    /**
     * 初始化地图平移监听：绑定鼠标事件到地图画布
     * 实现平移模式下的拖拽平移功能
     */
    private void initMapPanListener() {
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas == null) {
            mainFrame.log("地图画布初始化失败，无法绑定平移监听");
            return;
        }

        // 鼠标按下监听：记录平移起始位置
        mapCanvas.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {
                if (isPanMode && mainFrame.isConnected()) {
                    lastMousePos = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastMousePos = null; // 释放鼠标时重置位置
            }

            @Override
            public void mouseEntered(MouseEvent e) {}

            @Override
            public void mouseExited(MouseEvent e) {}
        });

        // 鼠标拖拽监听：计算平移偏移并刷新地图
        mapCanvas.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos != null && mainFrame.isConnected()) {
                    int dx = e.getX() - lastMousePos.x;
                    int dy = e.getY() - lastMousePos.y;
                    mapOffset.translate(dx, dy);
                    lastMousePos = e.getPoint();
                    mapCanvas.repaint(); // 刷新地图显示
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {}
        });
    }

    /**
     * SUMO连接核心方法：加载库、启动SUMO、初始化连接
     * 处理配置校验、端口占用等异常场景
     */
    public void connectSumo() {
        // 从界面获取配置参数
        String configPath = mainFrame.getConfigPath();
        String sumoPath = mainFrame.getSumoGuiPath();
        int port = mainFrame.getTraciPort();

        // 配置参数校验
        if (configPath == null || configPath.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "请先选择SUMO配置文件！", "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("连接失败：未选择配置文件");
            return;
        }

        File sumoFile = new File(sumoPath);
        if (!sumoFile.exists()) {
            JOptionPane.showMessageDialog(mainFrame, "SUMO路径错误：" + sumoPath, "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("连接失败：SUMO路径不存在");
            return;
        }

        try {
            // 构建SUMO启动参数（包含TRACI端口配置）
            String[] args = {sumoPath, "-c", configPath, "--remote-port", String.valueOf(port)};
            mainFrame.log("正在连接SUMO，启动参数：" + String.join(" ", args));

            // 预加载SUMO库并启动仿真引擎
            Simulation.preloadLibraries();
            Simulation.start(new StringVector(args));

            // 连接成功后初始化统计数据
            totalSteps = 0;
            totalVehicleDistance = 0;
            totalVehicleTime = 0;

            // 通知界面更新连接状态
            mainFrame.updateSumoConnectionStatus(true);
            mainFrame.log("SUMO连接成功！");
            // 首次加载数据并刷新界面
            updateSimulationData();
        } catch (Exception e) {
            // 特殊异常处理：端口占用
            if (e.getMessage() != null && e.getMessage().contains("Address already in use")) {
                JOptionPane.showMessageDialog(mainFrame, "端口" + port + "被占用，请重新配置端口！", "错误", JOptionPane.ERROR_MESSAGE);
                setTraciPort(); // 引导用户重新设置端口
            } else {
                JOptionPane.showMessageDialog(mainFrame, "连接失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"), "错误", JOptionPane.ERROR_MESSAGE);
            }
            mainFrame.log("连接失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * SUMO断开连接方法：停止仿真、关闭连接、重置状态
     */
    public void disconnectSumo() {
        if (!mainFrame.isConnected()) {
            mainFrame.log("未处于连接状态，无需断开");
            return;
        }

        try {
            // 先停止连续仿真（如果正在运行）
            if (mainFrame.getisContinuousRunning()) {
                mainFrame.stopContinuousSimulation();
            }

            // 关闭SUMO仿真引擎连接
            Simulation.close();
            // 重置地图视图参数
            resetMapView();
            // 通知界面更新连接状态
            mainFrame.updateSumoConnectionStatus(false);
            mainFrame.log("SUMO连接已关闭");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "关闭连接失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"), "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("关闭连接失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 单步仿真推进方法：执行单步仿真、更新统计数据
     */
    public void stepSimulation() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "请先连接SUMO！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // 执行单步仿真（SUMO默认1步=1秒）
            Simulation.step();
            totalSteps++;
            mainFrame.log("仿真推进至第" + totalSteps + "步");
            // 更新仿真数据并同步到界面
            updateSimulationData();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "单步推进失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"), "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("单步推进失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            // 处理连接中断场景
            if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
                mainFrame.updateSumoConnectionStatus(false);
                mainFrame.log("SUMO连接已中断");
            }
        }
    }

    /**
     * 仿真重置方法：重置SUMO状态和统计数据
     */
    public void resetSimulation() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "请先连接SUMO！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 弹出确认对话框
        int option = JOptionPane.showConfirmDialog(mainFrame, "是否重置仿真至初始状态？", "重置确认", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            try {
                // 停止连续仿真（如果正在运行）
                if (mainFrame.getisContinuousRunning()) {
                    mainFrame.stopContinuousSimulation();
                }
                // 重置SUMO仿真引擎状态
                Simulation.close();
                mainFrame.log("已关闭当前仿真连接");
                String[] args = {
                        mainFrame.getSumoGuiPath(),  // SUMO可执行文件路径
                        "-c", mainFrame.getConfigPath(),  // 配置文件路径
                        "--remote-port", String.valueOf(mainFrame.getTraciPort())  // 端口号
                };
                Simulation.start(new StringVector(args));  // 重新启动
                mainFrame.log("仿真已重启至初始状态");
                // 重置统计数据
                totalSteps = 0;
                totalVehicleDistance = 0;
                totalVehicleTime = 0;
                mainFrame.log("仿真已重置至初始状态");
                // 刷新界面数据
                updateSimulationData();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainFrame, "重置失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"), "错误", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("重置失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        }
    }

    /**
     * 核心数据处理方法：获取仿真数据、计算统计指标、同步至界面
     * 实现车辆、交通灯、仿真统计三类数据的整合
     */
    private void updateSimulationData() {
        try {
            // 1. 车辆数据获取与处理（适配无 getTime()/getArrival() 的情况）
            List<String> vehicleIds = Vehicle.getIDList();
            int vehicleTotal = vehicleIds.size();
            int vehicleRunning = 0; // 运行中车辆数（速度>0）
            int vehicleCongested = 0; // 拥堵车辆数（速度<5km/h，注意单位转换）
            double currentDistance = 0; // 本次步骤总行驶距离（米，用double避免精度丢失）
            double currentTime = 0; // 本次步骤总行驶时间（秒）

            // 获取当前仿真时间（用于计算车辆行驶时间）
            double currentSimTime = Simulation.getTime();

            for (String vehicleId : vehicleIds) {
                // 获取车辆当前速度（单位：m/s，需转换为km/h显示）
                double speedMs = Vehicle.getSpeed(vehicleId); // m/s
                double speedKmH = speedMs * 3.6; // 转换为km/h

                // 判断车辆状态
                if (speedMs > 0) { // 速度>0视为运行中
                    vehicleRunning++;
                    if (speedKmH < 5) { // 拥堵判断（<5km/h）
                        vehicleCongested++;
                    }
                }

                // 累加行驶距离（SUMO原生方法，单位：米）
                currentDistance += Vehicle.getDistance(vehicleId);

                // 计算行驶时间（无 getTime() 时，用出发时间到当前时间的差值）
                double departTime = Vehicle.getDeparture(vehicleId); // 车辆出发时间（秒）
                double vehicleTime = currentSimTime - departTime; // 已行驶时间（秒）
                currentTime += Math.max(0, vehicleTime); // 确保非负
            }

            // 更新全局统计数据（改用double避免整数溢出）
            totalVehicleDistance += (long) currentDistance;
            totalVehicleTime += (long)currentTime;

            // 2. 交通灯数据获取与处理（适配无 getCurrentPhase() 的情况）
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

            // 3. 核心统计指标计算
            // 平均车速（km/h）：总距离(km) / 总时间(h)
            double avgSpeed = 0.0;
            if (totalVehicleTime > 0) {
                avgSpeed = (totalVehicleDistance / 1000.0) / (totalVehicleTime / 3600.0);
            }

            // 通行效率（%）：运行中车辆数 / 总车辆数 * 100
            double trafficEfficiency = vehicleTotal > 0 ? (double) vehicleRunning / vehicleTotal * 100 : 0.0;

            // 仿真时间格式化（HH:MM:SS）
            int hours = (int) (currentSimTime / 3600);
            int minutes = (int) ((currentSimTime % 3600) / 60);
            int seconds = (int) (currentSimTime % 60);
            String simulationTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

            // 4. 封装数据并通知界面更新（确保参数与SimulationData构造方法匹配）
            SimulationData data = new SimulationData(
                    vehicleTotal, vehicleRunning, vehicleCongested,
                    tlTotal, tlRed, tlGreen, tlYellow,
                    totalSteps, avgSpeed, trafficEfficiency,
                    simulationTime
            );
            // 确保mainFrame的updateDashboard方法参数为SimulationData类型
            mainFrame.updateDashboard(data);

        } catch (Exception e) {
            mainFrame.log("数据更新失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 地图绘制核心方法：基于Graphics2D实现缩放、平移和元素绘制
     * 绘制路网、交通灯、车辆等核心元素，支持状态可视化
     * @param g 绘图上下文
     * @param canvasSize 画布尺寸
     */
    public void drawMap(Graphics g, Dimension canvasSize) {
        if (!mainFrame.isConnected()) {
            // 未连接时显示提示信息
            g.setColor(Color.GRAY);
            g.setFont(new Font("宋体", Font.PLAIN, 16));
            String tip = "请连接SUMO以加载地图";
            int x = (canvasSize.width - g.getFontMetrics().stringWidth(tip)) / 2;
            int y = canvasSize.height / 2;
            g.drawString(tip, x, y);
            return;
        }

        try {
            Graphics2D g2d = (Graphics2D) g;
            // 保存原始绘图状态（用于后续恢复）
            AffineTransform originalTransform = g2d.getTransform();

            // 设置缩放和平移变换（实现地图居中显示）
            g2d.translate(canvasSize.width / 2 + mapOffset.x, canvasSize.height / 2 + mapOffset.y);
            g2d.scale(mapScale, mapScale);

            // 1. 绘制路网（简化实现：实际可通过SUMO获取路网节点和边数据）
            drawRoadNetwork(g2d);

            // 2. 绘制交通灯（按状态着色，支持显示ID和状态）
            drawTrafficLights(g2d);

            // 3. 绘制车辆（按运行状态着色，支持显示ID）
            drawVehicles(g2d);

            // 恢复原始绘图状态
            g2d.setTransform(originalTransform);
        } catch (Exception e) {
            mainFrame.log("地图绘制失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 绘制路网：简化实现交叉路口路网，实际可扩展为解析SUMO路网数据
     * @param g2d 绘图上下文
     */
    private void drawRoadNetwork(Graphics2D g2d) {
        g2d.setColor(new Color(200, 200, 200));
        int roadWidth = 30;
        // 横向道路
        g2d.fillRect(-500, -15, 1000, roadWidth);
        // 纵向道路
        g2d.fillRect(-15, -500, roadWidth, 1000);
        // 绘制道路边界
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(-500, -15, 1000, roadWidth);
        g2d.drawRect(-15, -500, roadWidth, 1000);
    }

    /**
     * 绘制交通灯：根据状态着色（红/绿/黄），支持显示ID和状态标签
     * @param g2d 绘图上下文
     */
    private void drawTrafficLights(Graphics2D g2d) {
        List<String> tlIds = TrafficLight.getIDList();
        for (String tlId : tlIds) {
            // 获取交通灯位置（SUMO坐标系）
            StringVector controlledJunctions = TrafficLight.getControlledJunctions(tlId);
            if(controlledJunctions.isEmpty()){
                continue;
            }
            String junctionID=controlledJunctions.get(0);
            TraCIPosition pos=Junction.getPosition(junctionID);
            int x = (int) pos[0];
            int y = (int) pos[1];

            // 根据状态设置颜色
            String phase = TrafficLight.getPhaseName(tlId);
            if (phase.contains("r")) {
                g2d.setColor(Color.RED);
            } else if (phase.contains("g")) {
                g2d.setColor(Color.GREEN);
            } else if (phase.contains("y")) {
                g2d.setColor(Color.YELLOW);
            }

            // 绘制交通灯（圆形）
            g2d.fillOval(x - 12, y - 12, 24, 24);
            g2d.setColor(Color.BLACK);
            g2d.drawOval(x - 12, y - 12, 23, 23);

            // 显示交通灯ID和状态（可通过开关控制）
            if (showTLStatus) {
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("宋体", Font.PLAIN, 10));
                // 绘制ID
                g2d.drawString(tlId, x - 15, y - 15);
                // 绘制状态文字
                String status = phase.contains("r") ? "红" : phase.contains("g") ? "绿" : "黄";
                g2d.drawString(status, x - 4, y + 30);
            }
        }
    }

    /**
     * 绘制车辆：根据运行状态着色（运行/拥堵/静止），支持显示ID
     * @param g2d 绘图上下文
     */
    private void drawVehicles(Graphics2D g2d) {
        List<String> vehicleIds = Vehicle.getIDList();
        for (String vehicleId : vehicleIds) {
            // 获取车辆位置和行驶方向
            double[] pos = Vehicle.getPosition(vehicleId);
            double angle = Vehicle.getAngle(vehicleId);
            int x = (int) pos[0];
            int y = (int) pos[1];

            // 根据速度设置车辆颜色
            double speed = Vehicle.getSpeed(vehicleId);
            if (speed > 0) {
                g2d.setColor(speed < 5 ? Color.ORANGE : Color.BLUE); // 拥堵为橙色，运行为蓝色
            } else {
                g2d.setColor(Color.GRAY); // 静止为灰色
            }

            // 保存当前变换状态（用于旋转车辆方向）
            AffineTransform originalTransform = g2d.getTransform();
            // 绕车辆中心旋转（匹配行驶方向）
            g2d.rotate(Math.toRadians(angle), x, y);
            // 绘制车辆（三角形表示）
            int[] xs = {x, x - 15, x + 15};
            int[] ys = {y - 20, y + 10, y + 10};
            g2d.fillPolygon(xs, ys, 3);
            g2d.setColor(Color.BLACK);
            g2d.drawPolygon(xs, ys, 3);
            // 恢复变换状态
            g2d.setTransform(originalTransform);

            // 显示车辆ID（可通过开关控制）
            if (showVehicleLabel) {
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("宋体", Font.PLAIN, 10));
                g2d.drawString(vehicleId, x - 15, y - 25);
            }
        }
    }

    /**
     * 地图缩放方法：按比例缩放，限制缩放范围
     * @param scaleFactor 缩放系数（>1放大，<1缩小）
     */
    public void zoomMap(float scaleFactor) {
        mapScale *= scaleFactor;
        // 限制缩放范围（0.1倍到5.0倍）
        mapScale = Math.max(0.1f, Math.min(5.0f, mapScale));
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint();
        }
        mainFrame.log("地图缩放至" + String.format("%.1f", mapScale) + "倍");
    }

    /**
     * 切换地图平移模式：开启/关闭平移功能
     */
    public void togglePanMode() {
        isPanMode = !isPanMode;
        mainFrame.log("地图平移模式：" + (isPanMode ? "开启" : "关闭"));
        // 切换鼠标光标样式
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.setCursor(isPanMode ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());
        }
    }

    /**
     * 重置地图视图：恢复默认缩放和位置
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
        mainFrame.log("地图视图已重置");
    }

    /**
     * 切换车辆标签显示：开启/关闭车辆ID显示
     */
    public void toggleVehicleLabel() {
        showVehicleLabel = !showVehicleLabel;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint();
        }
        mainFrame.log("车辆标签显示：" + (showVehicleLabel ? "开启" : "关闭"));
    }

    /**
     * 切换交通灯状态显示：开启/关闭交通灯ID和状态显示
     */
    public void toggleTLStatusLabel() {
        showTLStatus = !showTLStatus;
        JPanel mapCanvas = getMapCanvas();
        if (mapCanvas != null) {
            mapCanvas.repaint();
        }
        mainFrame.log("交通灯状态显示：" + (showTLStatus ? "开启" : "关闭"));
    }

    /**
     * 保存仿真数据：将当前统计数据导出为CSV文件
     * 支持自定义保存路径，数据格式规范
     */
    public void saveSimulationData() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "请先连接SUMO并运行仿真！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        // 过滤CSV文件
        chooser.setFileFilter(new FileNameExtensionFilter("CSV文件 (*.csv)", "csv"));
        // 自动生成默认文件名（包含时间戳）
        String defaultFileName = "仿真数据_" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".csv";
        chooser.setSelectedFile(new File(defaultFileName));

        int result = chooser.showSaveDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            // 确保文件后缀为.csv
            String filePath = file.getAbsolutePath();
            if (!filePath.endsWith(".csv")) {
                filePath += ".csv";
                file = new File(filePath);
            }

            try (FileWriter writer = new FileWriter(file)) {
                // 写入CSV表头
                writer.write("仿真时间,总步数,车辆总数,运行车辆数,拥堵车辆数,交通灯总数,红灯数,绿灯数,黄灯数,平均车速(km/h),通行效率(%)\n");
                // 获取当前仿真数据
                SimulationData data = getCurrentSimulationData();
                // 写入数据行（使用Locale.US确保小数位为点号）
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
                JOptionPane.showMessageDialog(mainFrame, "数据已保存至：" + filePath, "保存成功", JOptionPane.INFORMATION_MESSAGE);
                mainFrame.log("仿真数据保存成功：" + filePath);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainFrame, "数据保存失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("数据保存失败：" + e.getMessage());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(mainFrame, "数据获取失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("数据获取失败：" + e.getMessage());
            }
        }
    }

    /**
     * 加载仿真场景：从文件夹选择.sumocfg配置文件
     * 支持批量场景管理，自动提取场景名称
     */
    public void loadSimulationScenario() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择场景文件夹（包含.sumocfg文件）");

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File scenarioDir = chooser.getSelectedFile();
            // 过滤.sumocfg文件
            File[] configFiles = scenarioDir.listFiles((dir, name) -> name.endsWith(".sumocfg"));

            if (configFiles == null || configFiles.length == 0) {
                JOptionPane.showMessageDialog(mainFrame, "该文件夹中未找到.sumocfg配置文件！", "错误", JOptionPane.ERROR_MESSAGE);
                mainFrame.log("场景加载失败：文件夹中无配置文件");
                return;
            }

            // 构建场景文件列表提示
            StringBuilder fileList = new StringBuilder("场景包含以下配置文件：\n");
            for (int i = 0; i < configFiles.length; i++) {
                fileList.append(i + 1).append(". ").append(configFiles[i].getName()).append("\n");
            }
            fileList.append("请输入要加载的文件序号：");

            // 输入序号选择文件
            String input = JOptionPane.showInputDialog(mainFrame, fileList.toString(), "选择配置文件", JOptionPane.PLAIN_MESSAGE);
            if (input == null || input.isEmpty()) {
                return;
            }

            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < configFiles.length) {
                    File selectedFile = configFiles[index];
                    String configPath = selectedFile.getAbsolutePath();
                    // 更新界面配置信息
                    mainFrame.setConfigPath(configPath);
                    mainFrame.setScenarioName(selectedFile.getName().replace(".sumocfg", ""));
                    mainFrame.log("场景加载成功：" + selectedFile.getName());
                    JOptionPane.showMessageDialog(mainFrame, "已加载配置文件：" + selectedFile.getName(), "加载成功", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(mainFrame, "输入的序号无效！", "错误", JOptionPane.ERROR_MESSAGE);
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(mainFrame, "请输入有效的数字序号！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 设置SUMO路径：选择sumo-gui.exe可执行文件
     */
    public void setSumoPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("可执行文件 (*.exe)", "exe"));
        chooser.setDialogTitle("选择sumo-gui.exe文件");

        int result = chooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File sumoFile = chooser.getSelectedFile();
            if (sumoFile.getName().equals("sumo-gui.exe")) {
                mainFrame.setSumoGuiPath(sumoFile.getAbsolutePath());
                JOptionPane.showMessageDialog(mainFrame, "SUMO路径已设置为：" + sumoFile.getAbsolutePath(), "设置成功", JOptionPane.INFORMATION_MESSAGE);
                mainFrame.log("SUMO路径更新：" + sumoFile.getAbsolutePath());
            } else {
                JOptionPane.showMessageDialog(mainFrame, "请选择sumo-gui.exe文件！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 设置TRACI端口：配置SUMO与程序通信的端口
     * 校验端口范围（1024-65535）
     */
    public void setTraciPort() {
        String input = JOptionPane.showInputDialog(mainFrame, "请输入TRACI端口号（1024-65535）：", mainFrame.getTraciPort());
        if (input == null || input.isEmpty()) {
            return;
        }

        try {
            int port = Integer.parseInt(input);
            if (port >= 1024 && port <= 65535) {
                mainFrame.setTraciPort(port);
                JOptionPane.showMessageDialog(mainFrame, "TRACI端口已设置为：" + port, "设置成功", JOptionPane.INFORMATION_MESSAGE);
                mainFrame.log("TRACI端口更新：" + port);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "端口号必须在1024-65535之间！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(mainFrame, "请输入有效的数字端口号！", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 恢复默认配置：重置SUMO路径、端口和地图参数
     */
    public void restoreDefaultConfig() {
        int option = JOptionPane.showConfirmDialog(mainFrame, "是否恢复默认配置？（SUMO路径、端口等将重置）", "恢复默认", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            // 重置SUMO路径和端口
            mainFrame.setSumoGuiPath("D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe");
            mainFrame.setTraciPort(8813);
            // 重置地图参数
            resetMapView();
            showVehicleLabel = true;
            showTLStatus = true;
            JOptionPane.showMessageDialog(mainFrame, "已恢复默认配置！", "恢复成功", JOptionPane.INFORMATION_MESSAGE);
            mainFrame.log("系统已恢复默认配置");
            // 刷新地图
            JPanel mapCanvas = getMapCanvas();
            if (mapCanvas != null) {
                mapCanvas.repaint();
            }
        }
    }

    /**
     * 显示使用指南：弹出帮助对话框，说明核心操作流程
     */
    public void showUserGuide() {
        String guide = "智能交通SUMO仿真控制系统使用指南：\n" +
                "1. 配置流程：\n" +
                "   - 点击\"选择文件\"加载.sumocfg配置文件\n" +
                "   - 如需修改SUMO路径或端口，通过\"配置\"菜单设置\n" +
                "2. 仿真控制：\n" +
                "   - 单步模式：连接后点击\"单步推进\"逐步运行\n" +
                "   - 连续模式：切换模式后拖动滑块设置速度，点击\"开始/暂停\"\n" +
                "3. 地图操作：\n" +
                "   - 放大/缩小：调整地图显示比例\n" +
                "   - 平移：开启平移模式后拖动地图\n" +
                "   - 可显示/隐藏车辆标签和交通灯状态\n" +
                "4. 数据查看：\n" +
                "   - 仪表盘显示核心数据，点击卡片查看详情\n" +
                "   - 通过\"文件\"菜单保存仿真数据为CSV文件\n" +
                "5. 场景管理：通过\"文件-加载仿真场景\"批量管理配置文件";
        JOptionPane.showMessageDialog(mainFrame, guide, "使用指南", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 显示关于信息：弹出软件版本和版权信息
     */
    public void showAbout() {
        String about = "智能交通SUMO仿真控制系统 v1.0\n" +
                "开发依赖：SUMO 1.25.0、Java Swing、TraCI SDK\n" +
                "核心功能：SUMO仿真控制、地图可视化、数据统计与导出\n" +
                "设计原则：界面与业务逻辑解耦，模块化架构\n" +
                "版权所有 © 2025 智能交通仿真团队";
        JOptionPane.showMessageDialog(mainFrame, about, "关于系统", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 显示车辆详细数据：弹出对话框展示所有车辆的状态信息
     */
    public void showVehicleDetail() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "请先连接SUMO！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<String> vehicleIds = Vehicle.getIDList();
            if (vehicleIds.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "当前无车辆数据！", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 构建车辆详细信息
            StringBuilder detail = new StringBuilder("车辆详细信息（共" + vehicleIds.size() + "辆）：\n");
            for (String vehicleId : vehicleIds) {
                double speed = Vehicle.getSpeed(vehicleId);
                double distance = Vehicle.getDistance(vehicleId);
                double time = Vehicle.getTime(vehicleId);
                String status = speed > 0 ? (speed < 5 ? "拥堵" : "运行") : "静止";
                // 格式化输出车辆信息
                detail.append(String.format("ID：%s | 状态：%s | 速度：%.1f km/h | 行驶距离：%.1f m | 行驶时间：%.1f s\n",
                        vehicleId, status, speed, distance, time));
            }

            // 使用带滚动条的文本框显示
            JTextArea textArea = new JTextArea(detail.toString());
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(650, 400));
            JOptionPane.showMessageDialog(mainFrame, scrollPane, "车辆详细数据", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "获取车辆详情失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("获取车辆详情失败：" + e.getMessage());
        }
    }

    /**
     * 显示交通灯详细数据：弹出对话框展示所有交通灯的状态信息
     */
    public void showTLDetail() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "请先连接SUMO！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            List<String> tlIds = TrafficLight.getIDList();
            if (tlIds.isEmpty()) {
                JOptionPane.showMessageDialog(mainFrame, "当前无交通灯数据！", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            // 构建交通灯详细信息
            StringBuilder detail = new StringBuilder("交通灯详细信息（共" + tlIds.size() + "个）：\n");
            for (String tlId : tlIds) {
                String phase = TrafficLight.getCurrentPhase(tlId);
                int phaseDuration = TrafficLight.getPhaseDuration(tlId);
                String status = phase.contains("r") ? "红灯" : phase.contains("g") ? "绿灯" : "黄灯";
                // 格式化输出交通灯信息
                detail.append(String.format("ID：%s | 状态：%s | 当前相位：%s | 相位时长：%d s\n",
                        tlId, status, phase, phaseDuration));
            }

            // 使用带滚动条的文本框显示
            JTextArea textArea = new JTextArea(detail.toString());
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(mainFrame, scrollPane, "交通灯详细数据", JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "获取交通灯详情失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("获取交通灯详情失败：" + e.getMessage());
        }
    }

    /**
     * 显示仿真统计详细数据：弹出对话框展示核心统计指标
     */
    public void showStatDetail() {
        if (!mainFrame.isConnected()) {
            JOptionPane.showMessageDialog(mainFrame, "请先连接SUMO！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // 获取当前仿真数据
            SimulationData data = getCurrentSimulationData();
            int vehicleTotal = data.getVehicleTotal();
            int vehicleRunning = data.getVehicleRunning();
            int vehicleCongested = data.getVehicleCongested();
            int tlTotal = data.getTlTotal();

            // 计算扩展统计指标
            int vehicleStatic = vehicleTotal - vehicleRunning; // 静止车辆数
            double staticRate = vehicleTotal > 0 ? (double) vehicleStatic / vehicleTotal * 100 : 0.0; // 静止率
            double congestedRate = vehicleTotal > 0 ? (double) vehicleCongested / vehicleTotal * 100 : 0.0; // 拥堵率
            double greenRate = tlTotal > 0 ? (double) data.getTlGreen() / tlTotal * 100 : 0.0; // 绿灯占比

            // 构建统计详情
            String detail = String.format("仿真统计详细数据\n" +
                            "======================\n" +
                            "1. 基础信息\n" +
                            "   - 仿真时间：%s\n" +
                            "   - 总仿真步数：%d步\n" +
                            "   - 车辆总数：%d辆\n" +
                            "   - 交通灯总数：%d个\n" +
                            "\n2. 车辆运行状态\n" +
                            "   - 运行中：%d辆 (%.1f%%)\n" +
                            "   - 拥堵中：%d辆 (%.1f%%)\n" +
                            "   - 静止：%d辆 (%.1f%%)\n" +
                            "\n3. 交通灯状态\n" +
                            "   - 绿灯：%d个 (%.1f%%)\n" +
                            "   - 红灯：%d个\n" +
                            "   - 黄灯：%d个\n" +
                            "\n4. 性能指标\n" +
                            "   - 平均车速：%.1f km/h\n" +
                            "   - 通行效率：%.1f%%\n" +
                            "   - 总行驶距离：%.1f km\n" +
                            "   - 总行驶时间：%.1f h",
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

            JOptionPane.showMessageDialog(mainFrame, detail, "仿真统计详细数据", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(mainFrame, "获取统计详情失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            mainFrame.log("获取统计详情失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前仿真数据：封装为SimulationData对象
     * @return 仿真数据对象
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

        // 计算车辆状态
        for (String vehicleId : vehicleIds) {
            double speed = Vehicle.getSpeed(vehicleId);
            if (speed > 0) {
                vehicleRunning++;
                if (speed < 5) {
                    vehicleCongested++;
                }
            }
        }

        // 计算交通灯状态
        for (String tlId : tlIds) {
            String phase = TrafficLight.getCurrentPhase(tlId);
            if (phase.contains("r")) tlRed++;
            if (phase.contains("g")) tlGreen++;
            if (phase.contains("y")) tlYellow++;
        }

        // 计算核心指标
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
     * 获取地图画布组件：从界面组件树中定位画布
     * @return 地图画布面板
     */
    private JPanel getMapCanvas() {
        try {
            // 从主窗口组件树中逐层获取地图画布
            JPanel rightPanel = (JPanel) mainFrame.getContentPane().getComponent(0);
            JPanel mapSubPanel = (JPanel) rightPanel.getComponent(0);
            JScrollPane mapScroll = (JScrollPane) mapSubPanel.getComponent(1);
            return (JPanel) mapScroll.getViewport().getView();
        } catch (Exception e) {
            mainFrame.log("获取地图画布失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 仿真数据封装类：用于界面与业务逻辑之间的数据传输
     * 采用不可变设计，确保数据一致性
     */
    public static class SimulationData {
        private final int vehicleTotal;
        private final int vehicleRunning;
        private final int vehicleCongested;
        private final int tlTotal;
        private final int tlRed;
        private final int tlGreen;
        private final int tlYellow;
        private final int totalSteps;
        private final double avgSpeed;
        private final double trafficEfficiency;
        private final String simulationTime;

        public SimulationData(int vehicleTotal, int vehicleRunning, int vehicleCongested,
                              int tlTotal, int tlRed, int tlGreen, int tlYellow,
                              int totalSteps, double avgSpeed, double trafficEfficiency,
                              String simulationTime) {
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

        // 所有属性的getter方法（无setter，确保不可变）
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
}