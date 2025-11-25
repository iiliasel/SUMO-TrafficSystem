import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.Vehicle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

public class SumoGUIMain extends JFrame {
    // 界面控件
    private JTextField configPathField; // 配置文件路径
    private JTextArea logArea;         // 日志显示
    private JButton selectBtn, connectBtn, stepBtn, closeBtn;
    private JLabel tlCountLabel, vehicleCountLabel; // 数据显示

    // SUMO相关参数
    private String sumoGuiPath = "D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe"; // 替换为你的路径
    private String configPath;
    private boolean isConnected = false;

    public SumoGUIMain() {
        setTitle("简易SUMO控制器");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 窗口居中
        initComponents();
    }

    // 初始化界面组件
    private void initComponents() {
        // 主面板（网格布局，5行1列）
        JPanel mainPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. 配置文件选择区
        JPanel configPanel = new JPanel(new BorderLayout(5, 5));
        configPathField = new JTextField();
        configPathField.setEditable(false);
        selectBtn = new JButton("选择配置文件(.sumocfg)");
        configPanel.add(selectBtn, BorderLayout.WEST);
        configPanel.add(configPathField, BorderLayout.CENTER);
        mainPanel.add(configPanel);

        // 2. 数据显示区（交通灯数+车辆数）
        JPanel dataPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        tlCountLabel = new JLabel("交通灯数量：--");
        vehicleCountLabel = new JLabel("车辆数量：--");
        dataPanel.add(tlCountLabel);
        dataPanel.add(vehicleCountLabel);
        mainPanel.add(dataPanel);

        // 3. 控制按钮区
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        connectBtn = new JButton("连接SUMO");
        stepBtn = new JButton("单步推进");
        closeBtn = new JButton("关闭连接");
        // 初始禁用部分按钮
        stepBtn.setEnabled(false);
        closeBtn.setEnabled(false);
        btnPanel.add(connectBtn);
        btnPanel.add(stepBtn);
        btnPanel.add(closeBtn);
        mainPanel.add(btnPanel);

        // 4. 日志显示区
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("操作日志"));
        mainPanel.add(logScroll);

        add(mainPanel);
        bindEvents(); // 绑定按钮事件
    }

    // 绑定按钮点击事件
    private void bindEvents() {
        // 选择配置文件
        selectBtn.addActionListener((ActionEvent e) -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().endsWith(".sumocfg");
                }

                @Override
                public String getDescription() {
                    return "SUMO配置文件 (.sumocfg)";
                }
            });
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                configPath = chooser.getSelectedFile().getAbsolutePath();
                configPathField.setText(configPath);
                log("已选择配置文件：" + configPath);
            }
        });


        // 连接SUMO按钮的事件绑定部分
        connectBtn.addActionListener((ActionEvent e) -> {
            if (configPath == null || configPath.isEmpty()) {
                log("请先选择配置文件！");
                return;
            }
            try {
                // 关键修正：删除--remote-port参数，避免重复设置
                String[] args = {sumoGuiPath, "-c", configPath};
                Simulation.preloadLibraries();
                Simulation.start(new StringVector(args));
                isConnected = true;
                connectBtn.setEnabled(false);
                stepBtn.setEnabled(true);
                closeBtn.setEnabled(true);
                log("SUMO连接成功！");
                updateData();
            } catch (Exception ex) {
                log("连接失败：" + ex.getMessage());
            }
        });

        // 单步推进
        stepBtn.addActionListener((ActionEvent e) -> {
            if (!isConnected) return;
            try {
                Simulation.step(); // 推进1步
                log("已推进1步仿真");
                updateData(); // 更新数据
            } catch (Exception ex) {
                log("单步失败：" + ex.getMessage());
            }
        });

        // 关闭连接
        closeBtn.addActionListener((ActionEvent e) -> {
            if (!isConnected) return;
            try {
                Simulation.close();
                isConnected = false;
                // 重置按钮状态
                connectBtn.setEnabled(true);
                stepBtn.setEnabled(false);
                closeBtn.setEnabled(false);
                // 清空数据
                tlCountLabel.setText("交通灯数量：--");
                vehicleCountLabel.setText("车辆数量：--");
                log("SUMO连接已关闭");
            } catch (Exception ex) {
                log("关闭失败：" + ex.getMessage());
            }
        });
    }

    // 更新交通灯和车辆数量
    private void updateData() {
        try {
            // 获取交通灯数量
            List<String> tlList = TrafficLight.getIDList();
            tlCountLabel.setText("交通灯数量：" + tlList.size());
            // 获取车辆数量
            List<String> vehicleList = Vehicle.getIDList();
            vehicleCountLabel.setText("车辆数量：" + vehicleList.size());
        } catch (Exception e) {
            log("数据更新失败：" + e.getMessage());
        }
    }

    // 日志输出
    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength()); // 自动滚动到底部
    }

    public static void main(String[] args) {
        // 启动GUI
        SwingUtilities.invokeLater(() -> new SumoGUIMain().setVisible(true));
    }
}