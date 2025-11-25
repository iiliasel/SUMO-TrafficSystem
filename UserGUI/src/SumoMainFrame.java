import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 主窗口类
 * 职责：实现界面布局、组件初始化、事件绑定，调用业务逻辑处理类
 */
public class SumoMainFrame extends JFrame {
    // 业务逻辑处理对象（解耦界面与业务）
    private final SumoBusinessService businessService;

    // 顶部导航栏组件
    private JMenuBar menuBar;
    private JMenu fileMenu, configMenu, helpMenu;
    private JMenuItem openConfigItem, saveDataItem, loadScenarioItem, exitItem;
    private JMenuItem sumoPathItem, portItem, restoreDefaultItem;
    private JMenuItem userGuideItem, aboutItem;

    // 左侧功能面板组件
    private JPanel leftPanel, configSubPanel, controlSubPanel;
    private JLabel configPathLabel, scenarioNameLabel, sumoStatusLabel;
    private JTextField configPathField, scenarioNameField;
    private JButton selectFileBtn;
    private JLabel statusIndicator; // SUMO连接状态指示灯

    // 控制区组件
    private JRadioButton stepModeBtn, continuousModeBtn;
    private ButtonGroup modeGroup;
    private JButton connectBtn, stepForwardBtn, startPauseBtn, resetBtn, disconnectBtn;
    private JSlider speedSlider;
    private JLabel speedLabel;
    private JTextArea logArea;
    private JScrollPane logScroll;

    // 右侧主显示区组件
    private JPanel rightPanel, mapSubPanel, dashboardSubPanel;
    private JPanel mapCanvas; // 地图显示画布
    private JToolBar mapToolBar;
    private JButton zoomInBtn, zoomOutBtn, panBtn, resetViewBtn, showVehicleLabelBtn, showTLLabelBtn;
    private JLabel simulationTimeLabel;

    // 数据仪表盘组件
    private JPanel vehicleCard, tlCard, statCard;
    private JLabel vehicleTotalLabel, vehicleRunningLabel, vehicleCongestedLabel;
    private JLabel tlTotalLabel, tlRedLabel, tlGreenLabel, tlYellowLabel;
    private JLabel statStepLabel, statAvgSpeedLabel, statEfficiencyLabel;

    // 系统配置参数
    private String sumoGuiPath = "D:\\SUMO\\sumo-1.25.0\\bin\\sumo-gui.exe";
    private String configPath = "";
    private int traciPort = 8813;
    private boolean isConnected = false;
    private boolean isContinuousRunning = false;
    private Timer continuousTimer; // 连续模式定时器



    public SumoMainFrame() {
        // 初始化业务逻辑服务
        businessService = new SumoBusinessService(this);
        // 初始化所有组件
        initAllComponents();
        // 布局组件
        layoutComponents();
        // 绑定事件监听
        bindEvents();
        // 初始化状态
        initComponentStatus();
    }

    /**
     * 初始化所有界面组件
     */
    private void initAllComponents() {
        // 1. 初始化顶部导航栏
        initMenuBar();
        // 2. 初始化左侧功能面板
        initLeftPanel();
        // 3. 初始化右侧主显示区
        initRightPanel();
        // 4. 初始化连续模式定时器
        continuousTimer = new Timer(1000, this::handleContinuousStep);
    }

    /**
     * 初始化顶部导航栏
     */
    private void initMenuBar() {
        menuBar = new JMenuBar();

        // 文件菜单
        fileMenu = new JMenu("文件(F)");
        openConfigItem = new JMenuItem("打开配置文件");
        saveDataItem = new JMenuItem("保存仿真数据");
        loadScenarioItem = new JMenuItem("加载仿真场景");
        exitItem = new JMenuItem("退出");
        fileMenu.add(openConfigItem);
        fileMenu.add(saveDataItem);
        fileMenu.add(loadScenarioItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // 配置菜单
        configMenu = new JMenu("配置(C)");
        sumoPathItem = new JMenuItem("SUMO路径设置");
        portItem = new JMenuItem("端口设置");
        restoreDefaultItem = new JMenuItem("恢复默认配置");
        configMenu.add(sumoPathItem);
        configMenu.add(portItem);
        configMenu.add(restoreDefaultItem);

        // 帮助菜单
        helpMenu = new JMenu("帮助(H)");
        userGuideItem = new JMenuItem("使用指南");
        aboutItem = new JMenuItem("关于");
        helpMenu.add(userGuideItem);
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(configMenu);
        menuBar.add(Box.createHorizontalGlue()); // 右对齐帮助菜单
        menuBar.add(helpMenu);
    }

    /**
     * 初始化左侧功能面板（配置区+控制区）
     */
    private void initLeftPanel() {
        leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 初始化配置区
        initConfigSubPanel();

        // 初始化控制区
        initControlSubPanel();

        leftPanel.add(configSubPanel, BorderLayout.NORTH);
        leftPanel.add(controlSubPanel, BorderLayout.CENTER);
    }

    /**
     * 初始化配置区子面板
     */
    private void initConfigSubPanel() {
        configSubPanel = new JPanel(new GridBagLayout());
        configSubPanel.setBorder(new TitledBorder("配置区"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 配置文件路径相关
        configPathLabel = new JLabel("配置文件路径：");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(configPathLabel, gbc);

        configPathField = new JTextField();
        configPathField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        configSubPanel.add(configPathField, gbc);

        selectFileBtn = new JButton("选择文件");
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0;
        configSubPanel.add(selectFileBtn, gbc);

        // 场景名称相关
        scenarioNameLabel = new JLabel("当前场景：");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(scenarioNameLabel, gbc);

        scenarioNameField = new JTextField("未选择场景");
        scenarioNameField.setEditable(false);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        configSubPanel.add(scenarioNameField, gbc);

        // 初始化状态指示灯
        initStatusIndicator(gbc);
    }

    /**
     * 初始化控制区子面板
     */
    private void initControlSubPanel() {
        controlSubPanel = new JPanel(new GridBagLayout());
        controlSubPanel.setBorder(new TitledBorder("控制区"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 3;

        // 连接按钮
        connectBtn = new JButton("连接SUMO");
        connectBtn.setEnabled(false); // 初始禁用（需先选择配置文件）
        gbc.gridy = 0;
        controlSubPanel.add(connectBtn, gbc);

        // 仿真模式选择
        gbc.gridwidth = 1;
        gbc.gridy++;
        gbc.anchor = GridBagConstraints.WEST;
        controlSubPanel.add(new JLabel("仿真模式："), gbc);

        modeGroup = new ButtonGroup();
        stepModeBtn = new JRadioButton("单步");
        continuousModeBtn = new JRadioButton("连续");
        modeGroup.add(stepModeBtn);
        modeGroup.add(continuousModeBtn);
        stepModeBtn.setSelected(true);

        gbc.gridx = 1;
        controlSubPanel.add(stepModeBtn, gbc);
        gbc.gridx = 2;
        controlSubPanel.add(continuousModeBtn, gbc);

        // 控制按钮组
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        JPanel controlBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        stepForwardBtn = new JButton("单步推进");
        stepForwardBtn.setEnabled(false);
        startPauseBtn = new JButton("开始");
        startPauseBtn.setEnabled(false);
        resetBtn = new JButton("重置");
        resetBtn.setEnabled(false);
        disconnectBtn = new JButton("关闭连接");
        disconnectBtn.setEnabled(false);

        controlBtnPanel.add(stepForwardBtn);
        controlBtnPanel.add(startPauseBtn);
        controlBtnPanel.add(resetBtn);
        controlBtnPanel.add(disconnectBtn);
        controlSubPanel.add(controlBtnPanel, gbc);

        // 速度调节
        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        speedLabel = new JLabel("仿真速度：1档");
        controlSubPanel.add(speedLabel, gbc);

        gbc.gridx = 1;
        gbc.gridwidth = 2;
        speedSlider = new JSlider(1, 10, 1);
        speedSlider.setMajorTickSpacing(1);
        speedSlider.setPaintTicks(true);
        speedSlider.setEnabled(false);
        controlSubPanel.add(speedSlider, gbc);

        // 日志输出区
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        logArea = new JTextArea(8, 20);
        logArea.setEditable(false);
        logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("操作日志"));
        controlSubPanel.add(logScroll, gbc);
    }

    /**
     * 初始化SUMO连接状态指示灯
     */
    private void initStatusIndicator(GridBagConstraints gbc) {
        sumoStatusLabel = new JLabel("SUMO连接状态：");
        statusIndicator = new JLabel();
        statusIndicator.setPreferredSize(new Dimension(15, 15));
        // 初始为红色（未连接）
        statusIndicator.setIcon(new ImageIcon(createIndicatorIcon(Color.RED)));

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        configSubPanel.add(sumoStatusLabel, gbc);

        gbc.gridx = 1;
        configSubPanel.add(statusIndicator, gbc);
    }

    /**
     * 创建状态指示灯图标
     */
    private BufferedImage createIndicatorIcon(Color color) {
        BufferedImage icon = new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = icon.createGraphics();
        g2d.setColor(color);
        g2d.fillOval(0, 0, 15, 15);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(0, 0, 14, 14);
        g2d.dispose();
        return icon;
    }

    /**
     * 初始化右侧主显示区（地图区+仪表盘）
     */
    private void initRightPanel() {
        rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 仿真地图区（上半部分）
        mapSubPanel = new JPanel(new BorderLayout());
        mapSubPanel.setBorder(new TitledBorder("仿真地图"));

        // 地图操作工具栏
        mapToolBar = new JToolBar();
        mapToolBar.setFloatable(false);

        zoomInBtn = new JButton("放大");
        zoomOutBtn = new JButton("缩小");
        panBtn = new JButton("平移");
        resetViewBtn = new JButton("重置视图");
        showVehicleLabelBtn = new JButton("显示车辆标签");
        showTLLabelBtn = new JButton("显示交通灯状态");

        // 初始禁用地图工具
        setMapToolsEnabled(false);

        mapToolBar.add(zoomInBtn);
        mapToolBar.add(zoomOutBtn);
        mapToolBar.add(panBtn);
        mapToolBar.add(resetViewBtn);
        mapToolBar.addSeparator();
        mapToolBar.add(showVehicleLabelBtn);
        mapToolBar.add(showTLLabelBtn);

        // 仿真时间显示
        simulationTimeLabel = new JLabel("00:00:00");
        mapToolBar.add(Box.createHorizontalGlue());
        mapToolBar.add(simulationTimeLabel);

        mapSubPanel.add(mapToolBar, BorderLayout.NORTH);

        // 地图显示画布
        mapCanvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (!businessService.isConnected()) {
                    g.drawString("请连接SUMO以加载地图", getWidth()/2 - 60, getHeight()/2);
                } else {
                    businessService.drawMap(g, this.getBounds());
                }
            }
        };
        mapCanvas.setBackground(Color.WHITE);
        mapSubPanel.add(mapCanvas, BorderLayout.CENTER);

        // 数据仪表盘（下半部分）
        initDashboardSubPanel();

        rightPanel.add(mapSubPanel, BorderLayout.CENTER);
        rightPanel.add(dashboardSubPanel, BorderLayout.SOUTH);
    }

    /**
     * 初始化数据仪表盘子面板
     */
    private void initDashboardSubPanel() {
        dashboardSubPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        dashboardSubPanel.setBorder(new TitledBorder("数据仪表盘"));

        // 车辆数据卡片
        vehicleCard = new JPanel(new GridBagLayout());
        vehicleCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints vehicleGbc = new GridBagConstraints();
        vehicleGbc.insets = new Insets(10, 10, 10, 10);
        vehicleGbc.anchor = GridBagConstraints.CENTER;

        JLabel vehicleTitle = new JLabel("车辆数据", SwingConstants.CENTER);
        vehicleTitle.setFont(new Font("宋体", Font.BOLD, 16));
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 0;
        vehicleCard.add(vehicleTitle, vehicleGbc);

        vehicleTotalLabel = new JLabel("总数：-- 辆", SwingConstants.CENTER);
        vehicleTotalLabel.setFont(new Font("宋体", Font.PLAIN, 14));
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 1;
        vehicleCard.add(vehicleTotalLabel, vehicleGbc);

        vehicleRunningLabel = new JLabel("运行：-- 辆", SwingConstants.CENTER);
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 2;
        vehicleCard.add(vehicleRunningLabel, vehicleGbc);

        vehicleCongestedLabel = new JLabel("拥堵：-- 辆", SwingConstants.CENTER);
        vehicleGbc.gridx = 0;
        vehicleGbc.gridy = 3;
        vehicleCard.add(vehicleCongestedLabel, vehicleGbc);

        // 交通灯数据卡片
        tlCard = new JPanel(new GridBagLayout());
        tlCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints tlGbc = new GridBagConstraints();
        tlGbc.insets = new Insets(10, 10, 10, 10);
        tlGbc.anchor = GridBagConstraints.CENTER;

        JLabel tlTitle = new JLabel("交通灯数据", SwingConstants.CENTER);
        tlTitle.setFont(new Font("宋体", Font.BOLD, 16));
        tlGbc.gridx = 0;
        tlGbc.gridy = 0;
        tlCard.add(tlTitle, tlGbc);

        tlTotalLabel = new JLabel("总数：-- 个", SwingConstants.CENTER);
        tlTotalLabel.setFont(new Font("宋体", Font.PLAIN, 14));
        tlGbc.gridx = 0;
        tlGbc.gridy = 1;
        tlCard.add(tlTotalLabel, tlGbc);

        tlRedLabel = new JLabel("红灯：-- 个", SwingConstants.CENTER);
        tlGbc.gridx = 0;
        tlGbc.gridy = 2;
        tlCard.add(tlRedLabel, tlGbc);

        tlGreenLabel = new JLabel("绿灯：-- 个", SwingConstants.CENTER);
        tlGbc.gridx = 0;
        tlGbc.gridy = 3;
        tlCard.add(tlGreenLabel, tlGbc);

        tlYellowLabel = new JLabel("黄灯：-- 个", SwingConstants.CENTER);
        tlGbc.gridx = 0;
        tlGbc.gridy = 4;
        tlCard.add(tlYellowLabel, tlGbc);

        // 仿真统计卡片
        statCard = new JPanel(new GridBagLayout());
        statCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        GridBagConstraints statGbc = new GridBagConstraints();
        statGbc.insets = new Insets(10, 10, 10, 10);
        statGbc.anchor = GridBagConstraints.CENTER;

        JLabel statTitle = new JLabel("仿真统计", SwingConstants.CENTER);
        statTitle.setFont(new Font("宋体", Font.BOLD, 16));
        statGbc.gridx = 0;
        statGbc.gridy = 0;
        statCard.add(statTitle, statGbc);

        statStepLabel = new JLabel("总步数：--", SwingConstants.CENTER);
        statStepLabel.setFont(new Font("宋体", Font.PLAIN, 14));
        statGbc.gridx = 0;
        statGbc.gridy = 1;
        statCard.add(statStepLabel, statGbc);

        statAvgSpeedLabel = new JLabel("平均车速：-- km/h", SwingConstants.CENTER);
        statGbc.gridx = 0;
        statGbc.gridy = 2;
        statCard.add(statAvgSpeedLabel, statGbc);

        statEfficiencyLabel = new JLabel("通行效率：-- %", SwingConstants.CENTER);
        statGbc.gridx = 0;
        statGbc.gridy = 3;
        statCard.add(statEfficiencyLabel, statGbc);

        // 组装仪表盘
        dashboardSubPanel.add(vehicleCard);
        dashboardSubPanel.add(tlCard);
        dashboardSubPanel.add(statCard);
    }

    /**
     * 布局所有组件（实现"三区两栏"结构）
     */
    private void layoutComponents() {
        // 设置主窗口布局为BorderLayout，顶部为导航栏，中间为左右面板
        setJMenuBar(menuBar);
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        // 左侧面板占1/4宽度，右侧占3/4宽度
        contentPanel.add(leftPanel, BorderLayout.WEST);
        contentPanel.add(rightPanel, BorderLayout.CENTER);
        setContentPane(contentPanel);
    }

    /**
     * 绑定所有组件事件监听
     */
    private void bindEvents() {
        // 1. 导航栏事件
        // 文件菜单事件
        openConfigItem.addActionListener(e -> selectConfigFile());
        saveDataItem.addActionListener(e -> businessService.saveSimulationData());
        loadScenarioItem.addActionListener(e -> businessService.loadSimulationScenario());
        exitItem.addActionListener(e -> handleExit());

        // 配置菜单事件
        sumoPathItem.addActionListener(e -> businessService.setSumoPath());
        portItem.addActionListener(e -> businessService.setTraciPort());
        restoreDefaultItem.addActionListener(e -> businessService.restoreDefaultConfig());

        // 帮助菜单事件
        userGuideItem.addActionListener(e -> businessService.showUserGuide());
        aboutItem.addActionListener(e -> businessService.showAbout());

        // 2. 左侧面板事件
        // 配置区事件
        selectFileBtn.addActionListener(e -> selectConfigFile());

        // 控制区事件
        connectBtn.addActionListener(e -> businessService.connectSumo());
        stepForwardBtn.addActionListener(e -> businessService.stepSimulation());
        startPauseBtn.addActionListener(e -> handleStartPause());
        resetBtn.addActionListener(e -> businessService.resetSimulation());
        disconnectBtn.addActionListener(e -> businessService.disconnectSumo());
        stepModeBtn.addActionListener(e -> updateControlStatus());
        continuousModeBtn.addActionListener(e -> updateControlStatus());

        // 速度滑块事件
        speedSlider.addChangeListener(e -> {
            int speed = speedSlider.getValue();
            speedLabel.setText("仿真速度：" + speed + "档");
            // 调整连续模式的时间间隔（1档1000ms，10档100ms）
            continuousTimer.setDelay(1100 - speed * 100);
        });

        // 日志滚动事件（自动滚动到底部）
        logArea.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (!e.getValueIsAdjusting()) {
                e.getAdjustable().setValue(e.getAdjustable().getMaximum());
            }
        });

        // 3. 右侧面板事件
        // 地图工具栏事件
        zoomInBtn.addActionListener(e -> businessService.zoomMap(1.1f));
        zoomOutBtn.addActionListener(e -> businessService.zoomMap(0.9f));
        panBtn.addActionListener(e -> businessService.togglePanMode());
        resetViewBtn.addActionListener(e -> businessService.resetMapView());
        showVehicleLabelBtn.addActionListener(e -> businessService.toggleVehicleLabel());
        showTLLabelBtn.addActionListener(e -> businessService.toggleTLStatusLabel());

        // 数据卡片点击事件（查看详细数据）
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
     * 初始化组件状态（禁用未连接时的功能）
     */
    private void initComponentStatus() {
        // 未连接时禁用的组件
        stepForwardBtn.setEnabled(false);
        startPauseBtn.setEnabled(false);
        resetBtn.setEnabled(false);
        disconnectBtn.setEnabled(false);
        speedSlider.setEnabled(false);
        // 地图工具栏禁用
        setMapToolsEnabled(false);
        // 保存数据禁用
        saveDataItem.setEnabled(false);
    }

    /**
     * 更新控制区组件状态
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

        // 连续模式停止时重置按钮文本
        if (isStepMode && isContinuousRunning) {
            stopContinuousSimulation();
        }
    }

    /**
     * 选择配置文件
     */
    private void selectConfigFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("SUMO配置文件", "sumocfg"));
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File configFile = fileChooser.getSelectedFile();
            configPath = configFile.getAbsolutePath();
            configPathField.setText(configPath);
            // 提取场景名称（去除路径和后缀）
            String scenarioName = configFile.getName().replace(".sumocfg", "");
            scenarioNameField.setText(scenarioName);
            log("已选择配置文件：" + configFile.getName());
            connectBtn.setEnabled(true);
        }
    }

    /**
     * 处理连续模式的开始/暂停
     */
    private void handleStartPause() {
        if (continuousTimer.isRunning()) {
            stopContinuousSimulation();
        } else {
            startContinuousSimulation();
        }
    }

    /**
     * 开始连续仿真
     */
    public void startContinuousSimulation() {
        isContinuousRunning = true;
        startPauseBtn.setText("暂停");
        continuousTimer.start();
        log("连续仿真已开始，速度：" + speedSlider.getValue() + "档");
    }

    /**
     * 停止连续仿真
     */
    public void stopContinuousSimulation() {
        isContinuousRunning = false;
        startPauseBtn.setText("开始");
        continuousTimer.stop();
        log("连续仿真已暂停");
    }

    /**
     * 连续模式的单步处理
     */
    private void handleContinuousStep(ActionEvent e) {
        businessService.stepSimulation();
    }

    /**
     * 处理退出操作
     */
    private void handleExit() {
        int option = JOptionPane.showConfirmDialog(this, "是否终止仿真并退出？", "退出确认", JOptionPane.YES_NO_OPTION);
        if (option == JOptionPane.YES_OPTION) {
            // 退出前关闭连接
            if (isConnected) {
                businessService.disconnectSumo();
            }
            System.exit(0);
        }
    }

    /**
     * 设置地图工具栏启用状态
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
     * 更新SUMO连接状态
     */
    public void updateSumoConnectionStatus(boolean connected) {
        isConnected = connected;
        statusIndicator.setIcon(new ImageIcon(createIndicatorIcon(connected ? Color.GREEN : Color.RED)));
        connectBtn.setEnabled(!connected);
        // 更新其他组件状态
        updateControlStatus();
        // 连接状态变化时刷新地图
        mapCanvas.repaint();
    }

    /**
     * 更新数据仪表盘显示
     */
    public void updateDashboard(SimulationData data) {
        // 更新车辆数据
        vehicleTotalLabel.setText("总数：" + data.getVehicleTotal() + " 辆");
        vehicleRunningLabel.setText("运行：" + data.getVehicleRunning() + " 辆");
        vehicleCongestedLabel.setText("拥堵：" + data.getVehicleCongested() + " 辆");

        // 更新交通灯数据
        tlTotalLabel.setText("总数：" + data.getTlTotal() + " 个");
        tlRedLabel.setText("红灯：" + data.getTlRed() + " 个");
        tlGreenLabel.setText("绿灯：" + data.getTlGreen() + " 个");
        tlYellowLabel.setText("黄灯：" + data.getTlYellow() + " 个");

        // 更新仿真统计数据
        statStepLabel.setText("总步数：" + data.getTotalSteps());
        statAvgSpeedLabel.setText(String.format("平均车速：%.1f km/h", data.getAvgSpeed()));
        statEfficiencyLabel.setText(String.format("通行效率：%.1f %%", data.getTrafficEfficiency()));

        // 更新仿真时间
        simulationTimeLabel.setText(data.getSimulationTime());

        // 刷新地图
        mapCanvas.repaint();
    }

    /**
     * 日志输出
     */
    public void log(String msg) {
        String time = String.format("[%tT]", System.currentTimeMillis());
        logArea.append(time + " " + msg + "\n");
    }

    // ------------------- getter方法（提供给业务服务类访问） -------------------
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