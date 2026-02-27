package i18nupdatemod.core;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.entity.LoadStage;
import i18nupdatemod.util.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;

public class LoadDetailUI {
    private static volatile LoadDetailUI instance;
    private final JFrame frame;
    private final JProgressBar statusBar;
    private final JTextArea logArea;
    private final boolean useGUI;

    private LoadDetailUI() {
        useGUI = !Boolean.parseBoolean(System.getProperty("java.awt.headless", "false"));

        if (!useGUI) {
            frame = null;
            statusBar = null;
            logArea = null;
            return;
        }

        frame = new JFrame();
        frame.setTitle("I18nUpdateMod-资源包下载进度");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setSize(854, 480);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });


        URL iconURL = getClass().getResource("/icons/CFPA.png");
        if (iconURL != null) {
            Image icon = Toolkit.getDefaultToolkit().getImage(iconURL);
            frame.setIconImage(icon);
        }

        // 主面板
        JPanel panel = new JPanel();
        panel.setBackground(new Color(220, 220, 220));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 状态栏
        statusBar = new JProgressBar();
        statusBar.setString(LoadStage.getDescription(LoadStage.INIT));
        statusBar.setStringPainted(true);
        statusBar.setMaximum(LoadStage.values().length - 1);
        statusBar.setValue(0);
        statusBar.setForeground(new Color(102, 255, 102));
        panel.add(statusBar);
        panel.add(Box.createVerticalStrut(10));

        // 日志输出区
        logArea = new JTextArea(6, 30);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.add(scrollPane);
        panel.add(Box.createVerticalStrut(10));

        // 提示文字
        JLabel tip = new JLabel("如遇到进度卡住，可以点击下方按钮/关闭此窗口停止此次下载。");
        tip.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(tip);
        panel.add(Box.createVerticalStrut(10));

        // 停止按钮
        JButton stopButton = new JButton("停止此次下载");
        stopButton.setFocusPainted(false);
        stopButton.setBackground(Color.WHITE);
        stopButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        stopButton.addActionListener((ActionEvent e) -> shutdown());
        panel.add(stopButton);

        frame.add(panel, BorderLayout.CENTER);
    }

    public static LoadDetailUI getInstance() {
        if (instance == null) {
            synchronized (LoadDetailUI.class) {
                if (instance == null) {
                    instance = new LoadDetailUI();
                }
            }
        }
        return instance;
    }

    public static void show() {
        LoadDetailUI gui = getInstance();
        if (!gui.useGUI || gui.frame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> gui.frame.setVisible(true));
    }

    public static void hide() {
        LoadDetailUI gui = getInstance();
        if (!gui.useGUI || gui.frame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> gui.frame.setVisible(false));
    }

    private void shutdown() {
        I18nUpdateMod.shouldShutdown = true;
        Log.info("User shutdown task");
        if (!useGUI) {
            return;
        }
        hide();
    }

    public static void setStage(LoadStage stage) {
        LoadDetailUI gui = getInstance();
        if (!gui.useGUI || gui.statusBar == null || gui.logArea == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            gui.statusBar.setString(LoadStage.getDescription(stage));
            gui.statusBar.setValue(stage.getValue());
            gui.logArea.append("当前阶段: " + LoadStage.getDescription(stage) + "\n");
            gui.logArea.setCaretPosition(gui.logArea.getDocument().getLength());
        });
    }

    public static void appendLog(String log) {
        LoadDetailUI gui = getInstance();
        if (!gui.useGUI || gui.logArea == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            gui.logArea.append(log + "\n");
            gui.logArea.setCaretPosition(gui.logArea.getDocument().getLength());
        });
    }

    public static void autoClose(int delayTime){
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Thread.sleep(delayTime);
                return null;
            }

            @Override
            protected void done() {
                hide();
            }
        };
        worker.execute();
    }
}