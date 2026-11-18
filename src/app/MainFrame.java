package app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import db.DataStorage;

/**
 * 프로그램의 메인 창.
 * - 상단 : 메뉴바 (파일 > 새로고침 / DB 연결 확인 / 종료)
 * - 가운데 : JTabbedPane (한눈에 보기 / 룸메이트·품목 / 구매 기록 / 소진 예측 / 정산)
 * - 하단 : 상태 표시줄 (왼쪽 : 룸메이트·품목·구매기록 수, 오른쪽 : DB 연결 상태)
 *
 * DB 연결 상태 알림
 * - 시작할 때 연결에 성공하면 상태 표시줄에 성공 알림을, 실패하면 원인과 해결 방법을 담은 오류 창을 띄운다.
 * - 저장에 실패하면 즉시 오류 창을 띄우고, 상태 표시줄에 '저장 안 된 변경 있음'을 표시한다.
 * - 상태 표시줄의 DB 상태를 클릭하거나 [파일 > DB 연결 확인]을 누르면 다시 연결을 확인한다.
 */
public class MainFrame extends JFrame {

    private static final Color OK_COLOR = new Color(0, 130, 60);
    private static final Color ERROR_COLOR = new Color(200, 30, 30);
    private static final Color OK_BACKGROUND = new Color(220, 245, 225);

    private AppData data;
    private JTabbedPane tabs;
    private JLabel statusLabel;
    private JLabel dbStatusLabel;

    private boolean unsavedChanges;   // 저장에 실패해서 DB에 반영되지 않은 변경이 있는지
    private boolean batchSaving;      // 한꺼번에 다시 저장하는 중에는 오류 창을 한 번만 띄우기 위함
    private Timer flashTimer;         // 성공 알림을 잠시 보여 준 뒤 원래 표시로 되돌리는 타이머

    public MainFrame(AppData data) {
        super("룸메이트 생필품 소진예측 + 정산 시스템");
        this.data = data;

        // 저장 안 된 변경이 있을 때 종료 전에 확인하기 위해 직접 처리
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitProgram();
            }
        });
        setLayout(new BorderLayout());

        createMenu();

        // 데이터가 바뀌었을 때 상태 표시줄을 갱신하도록 각 패널에 전달하는 동작
        Runnable onDataChanged = () -> updateStatus();

        tabs = new JTabbedPane();
        tabs.addTab("한눈에 보기", new DashboardPanel(data));
        tabs.addTab("룸메이트·품목", new MemberItemPanel(data, onDataChanged));
        tabs.addTab("구매 기록", new PurchasePanel(data, onDataChanged));
        tabs.addTab("소진 예측", new PredictionPanel(data));
        tabs.addTab("정산", new SettlementPanel(data, onDataChanged));

        // 탭을 바꿀 때마다 해당 탭을 최신 데이터로 갱신
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                refreshCurrentTab();
            }
        });
        add(tabs, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        registerDbListeners();
        refreshCurrentTab();

        setSize(900, 620);
        setLocationRelativeTo(null); // 화면 가운데에 띄우기
        setVisible(true);

        // 창이 화면에 뜬 다음 시작 시 연결 결과를 알린다
        SwingUtilities.invokeLater(() -> showStartupConnectionResult());
    }

    private JPanel createStatusBar() {
        statusLabel = new JLabel();
        dbStatusLabel = new JLabel();
        dbStatusLabel.setOpaque(true);
        dbStatusLabel.setFont(dbStatusLabel.getFont().deriveFont(Font.BOLD));
        dbStatusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        dbStatusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dbStatusLabel.setToolTipText("클릭하면 DB 연결을 다시 확인합니다.");
        dbStatusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                checkConnection();
            }
        });

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 6));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(dbStatusLabel, BorderLayout.EAST);
        return bar;
    }

    private void createMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("파일");

        JMenuItem refreshItem = new JMenuItem("새로고침");
        refreshItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshCurrentTab();
            }
        });

        JMenuItem checkItem = new JMenuItem("DB 연결 확인");
        checkItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkConnection();
            }
        });

        JMenuItem exitItem = new JMenuItem("종료");
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exitProgram();
            }
        });

        fileMenu.add(refreshItem);
        fileMenu.add(checkItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    // ---------- DB 연결 상태 ----------

    /** DB 저장 결과와 연결 문제를 화면에서 알 수 있도록 리스너를 등록한다. */
    private void registerDbListeners() {
        DataStorage.setStatusListener(new DataStorage.StatusListener() {
            @Override
            public void onSuccess() {
                if (!unsavedChanges) {
                    showConnected(false);
                }
            }

            @Override
            public void onError(String action, String reason) {
                unsavedChanges = true;
                showDisconnected("DB 저장 실패 (클릭해서 다시 연결)", reason);
                if (!batchSaving) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            action + "에 실패했습니다.\n\n원인: " + reason
                                    + "\n\n화면에는 반영되었지만 DB에는 저장되지 않았습니다."
                                    + "\n문제를 해결한 뒤 하단의 DB 상태를 클릭하면 다시 저장할 수 있습니다.",
                            "DB 저장 실패", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // 시작할 때 연결에 실패한 상태에서, 나중에 연결이 되어 DB 데이터를 다시 불러왔을 때
        data.setOnReloaded(() -> {
            refreshCurrentTab();
            showConnected(true);
            JOptionPane.showMessageDialog(this,
                    "DB에 연결되어 저장된 데이터를 불러왔습니다.\n화면을 최신 내용으로 바꿨으니 방금 작업을 다시 해 주세요.",
                    "DB 연결 성공", JOptionPane.INFORMATION_MESSAGE);
        });

        // 시작할 때부터 연결에 실패한 상태에서 데이터를 바꾸려고 할 때
        data.setOnBlocked(reason -> {
            showDisconnected("DB 연결 안 됨 (클릭해서 다시 연결)", reason);
            JOptionPane.showMessageDialog(this,
                    "DB에 연결되어 있지 않아 변경 내용을 저장할 수 없습니다.\n\n원인: " + reason,
                    "DB 연결 실패", JOptionPane.ERROR_MESSAGE);
        });
    }

    /** 시작할 때 연결 결과 알림 : 성공은 상태 표시줄에 잠시 강조, 실패는 오류 창 */
    private void showStartupConnectionResult() {
        if (data.isLoaded()) {
            showConnected(true);
        } else {
            showDisconnected("DB 연결 안 됨 (클릭해서 다시 연결)", data.getLoadError());
            showConnectionError(data.getLoadError());
        }
    }

    /** [DB 연결 확인] 또는 상태 표시줄 클릭 시 */
    private void checkConnection() {
        String error = DataStorage.checkConnection();
        if (error != null) {
            showDisconnected(unsavedChanges ? "DB 저장 실패 (클릭해서 다시 연결)" : "DB 연결 안 됨 (클릭해서 다시 연결)", error);
            showConnectionError(error);
            return;
        }

        if (!data.isLoaded()) {
            // 시작할 때 불러오지 못했던 데이터를 지금 불러옴
            data.reload();
            refreshCurrentTab();
            showConnected(true);
            JOptionPane.showMessageDialog(this, "DB에 연결되어 저장된 데이터를 불러왔습니다.",
                    "DB 연결 성공", JOptionPane.INFORMATION_MESSAGE);
        } else if (unsavedChanges) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "DB 연결이 복구되었습니다.\n저장하지 못한 변경 내용을 지금 DB에 저장할까요?",
                    "DB 연결 성공", JOptionPane.YES_NO_OPTION);
            if (answer == JOptionPane.YES_OPTION) {
                saveAllAgain();
            }
        } else {
            showConnected(true);
            JOptionPane.showMessageDialog(this, "DB 연결 상태가 정상입니다.",
                    "DB 연결 확인", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** 저장 실패 후 연결이 복구되었을 때, 현재 화면의 데이터 전체를 다시 저장 */
    private void saveAllAgain() {
        batchSaving = true;
        boolean ok = data.saveAll();
        batchSaving = false;
        if (ok) {
            unsavedChanges = false;
            showConnected(true);
            JOptionPane.showMessageDialog(this, "저장하지 못했던 변경 내용을 모두 DB에 저장했습니다.");
        } else {
            JOptionPane.showMessageDialog(this, "다시 저장하지 못했습니다. DB 상태를 확인한 뒤 다시 시도하세요.",
                    "DB 저장 실패", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showConnectionError(String reason) {
        JOptionPane.showMessageDialog(this,
                "DB에 연결할 수 없습니다.\n\n원인: " + reason
                        + "\n\n연결되지 않은 상태에서는 데이터가 저장되지 않습니다."
                        + "\n문제를 해결한 뒤 하단의 DB 상태를 클릭하거나 [파일 > DB 연결 확인]을 누르세요.",
                "DB 연결 실패", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * 연결 정상 표시.
     * @param flash true면 'DB 연결 성공' 알림을 3초간 강조해서 보여 준 뒤 평소 표시로 바꾼다.
     */
    private void showConnected(boolean flash) {
        if (flashTimer != null && flashTimer.isRunning()) {
            if (!flash) {
                return; // 성공 알림을 보여 주는 중이면 그대로 둔다
            }
            flashTimer.stop();
        }
        dbStatusLabel.setForeground(OK_COLOR);
        dbStatusLabel.setToolTipText("클릭하면 DB 연결을 다시 확인합니다.");
        if (flash) {
            dbStatusLabel.setText("● DB 연결 성공");
            dbStatusLabel.setBackground(OK_BACKGROUND);
            flashTimer = new Timer(3000, e -> showConnected(false));
            flashTimer.setRepeats(false);
            flashTimer.start();
        } else {
            dbStatusLabel.setText("● DB 연결됨");
            dbStatusLabel.setBackground(getContentPane().getBackground());
        }
    }

    private void showDisconnected(String text, String reason) {
        if (flashTimer != null) {
            flashTimer.stop();
        }
        dbStatusLabel.setText("● " + text);
        dbStatusLabel.setForeground(ERROR_COLOR);
        dbStatusLabel.setBackground(getContentPane().getBackground());
        dbStatusLabel.setToolTipText("원인: " + reason + "  (클릭하면 다시 확인합니다)");
    }

    /** 저장 안 된 변경이 있으면 종료 전에 한 번 더 확인 */
    private void exitProgram() {
        if (unsavedChanges) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "DB에 저장하지 못한 변경 내용이 있습니다.\n지금 종료하면 이 내용은 사라집니다. 종료할까요?",
                    "종료 확인", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        System.exit(0);
    }

    // ---------- 화면 갱신 ----------

    /** 현재 선택된 탭을 최신 데이터로 다시 그린다. */
    public void refreshCurrentTab() {
        Component current = tabs.getSelectedComponent();
        if (current instanceof Refreshable) {
            ((Refreshable) current).refresh();
        }
        updateStatus();
    }

    private void updateStatus() {
        statusLabel.setText("룸메이트 " + data.getMembers().size() + "명  /  품목 "
                + data.getItems().size() + "개  /  구매기록 " + data.getPurchases().size() + "건");
    }
}