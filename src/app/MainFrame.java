package app;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * 프로그램의 메인 창.
 * - 상단 : 메뉴바 (파일 > 새로고침 / 종료)
 * - 가운데 : JTabbedPane (한눈에 보기 / 룸메이트·품목 / 구매 기록 / 소진 예측 / 정산)
 * - 하단 : 상태 표시줄 (룸메이트 수 / 품목 수 / 구매기록 수)
 */
public class MainFrame extends JFrame {

    private AppData data;
    private JTabbedPane tabs;
    private JLabel statusLabel;

    public MainFrame(AppData data) {
        super("룸메이트 생필품 소진예측 + 정산 시스템");
        this.data = data;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createMenu();

        // 데이터가 바뀌었을 때 상태 표시줄을 갱신하도록 각 패널에 전달하는 동작
        Runnable onDataChanged = () -> updateStatus();

        tabs = new JTabbedPane();
        tabs.addTab("한눈에 보기", new DashboardPanel(data));
        tabs.addTab("룸메이트·품목", new MemberItemPanel(data, onDataChanged));
        tabs.addTab("구매 기록", new PurchasePanel(data, onDataChanged));
        tabs.addTab("소진 예측", new PredictionPanel(data));
        tabs.addTab("정산", new SettlementPanel(data));

        // 탭을 바꿀 때마다 해당 탭을 최신 데이터로 갱신
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                refreshCurrentTab();
            }
        });
        add(tabs, BorderLayout.CENTER);

        statusLabel = new JLabel();
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        add(statusLabel, BorderLayout.SOUTH);

        refreshCurrentTab();

        setSize(900, 620);
        setLocationRelativeTo(null); // 화면 가운데에 띄우기
        setVisible(true);
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

        JMenuItem exitItem = new JMenuItem("종료");
        exitItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0); // 등록할 때마다 저장되므로 바로 종료해도 된다
            }
        });

        fileMenu.add(refreshItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

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