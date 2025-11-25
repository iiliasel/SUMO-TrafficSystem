import javax.swing.*;

/**
 * SUMO仿真控制系统启动类
 * 职责：仅负责GUI启动和主窗口初始化，不包含业务逻辑
 */
public class SumoGUIApplication {
    public static void main(String[] args) {
        // 采用Swing线程安全方式启动GUI
        SwingUtilities.invokeLater(() -> {
            // 初始化主窗口
            SumoMainFrame mainFrame = new SumoMainFrame();
            mainFrame.setTitle("智能交通SUMO仿真控制系统");
            mainFrame.setSize(1200, 800); // 适配"三区两栏"布局的窗口尺寸
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setLocationRelativeTo(null); // 窗口居中
            mainFrame.setVisible(true);
        });
    }
}
