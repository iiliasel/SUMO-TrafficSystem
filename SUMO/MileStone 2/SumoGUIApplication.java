import javax.swing.*;
import org.apache.logging.log4j.LogManager;
import com.sumo.log.TextAreaAppender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.layout.PatternLayout;
/**
 * SUMO simulation control startup class
 * only responsible for GUI startup and main window initialization
 */
public class SumoGUIApplication {
    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(SumoGUIApplication.class);
    public static void main(String[] args) {
        // 测试日志输出
        System.setProperty("log4j.configurationFile", "resources/log4j2.xml");
        logger.info("SumoGUIApplication started");
        registerTextAreaAppender();
        // start the GUI using Swing thread-safe method
        SwingUtilities.invokeLater(() -> {
            // main window initialization
            SumoMainFrame mainFrame = new SumoMainFrame();
            mainFrame.setTitle("SUMO Simulation Control System");
            mainFrame.setSize(1200, 800); // window size adapted to the "three-section,two column" layout
            mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            mainFrame.setLocationRelativeTo(null); // center the window
            mainFrame.setVisible(true);
        });
    }
    private static void registerTextAreaAppender() {
        // 获取Log4j2的上下文
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

        // 创建日志格式（与控制台保持一致）
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%d{HH:mm:ss} [%level] %msg%n")
                .withConfiguration(config)
                .build();

        // 创建自定义Appender
        TextAreaAppender textAreaAppender = new TextAreaAppender("TextAreaAppender", null, layout);
        textAreaAppender.start(); // 启动Appender

        // 将Appender添加到配置中
        config.addAppender(textAreaAppender);

        // 将Appender关联到根日志器
        org.apache.logging.log4j.core.Logger rootLogger = context.getRootLogger();
        rootLogger.addAppender(textAreaAppender);

        // 更新配置
        context.updateLoggers(config);
    }
}