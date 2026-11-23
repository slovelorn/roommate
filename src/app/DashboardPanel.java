package app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import logic.BuyerRecommender;
import logic.ConsumptionPredictor;
import logic.SettlementEngine;
import model.Purchase;

/**
 * [한눈에 보기] 탭 (프로그램 첫 화면).
 * 다른 탭의 핵심 내용을 2 x 2 칸에 요약해서 보여준다.
 * - 현황 요약 / 소진 임박 품목 + 추천 담당자 (D-day순) / 최근 구매 5건 / 정산 요약 (미정산 기록 기준)
 * 추천 담당자 칸에 마우스를 올리면 추천 이유가 표시된다.
 */
public class DashboardPanel extends JPanel implements Refreshable {

    private static final int RECENT_COUNT = 5;   // 최근 구매 표시 개수
    private static final int WARNING_DAYS = 3;   // D-3 이내면 소진 임박

    private AppData data;

    private JTextArea summaryArea = new JTextArea();
    private DefaultTableModel predictionModel;
    private List<String> recommendReasons = new ArrayList<>(); // 각 행의 추천 이유 (툴팁 표시용)
    private DefaultTableModel recentModel;
    private JTextArea settlementArea = new JTextArea();

    public DashboardPanel(AppData data) {
        this.data = data;

        setLayout(new BorderLayout(0, 5));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        summaryArea.setEditable(false);
        settlementArea.setEditable(false);

        predictionModel = createTableModel(new String[]{"품목", "예상 소진일", "D-day", "상태", "추천 담당자"});
        recentModel = createTableModel(new String[]{"구매일", "품목", "구매자", "금액(원)"});

        JTable predictionTable = new JTable(predictionModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row >= 0 && row < recommendReasons.size()) {
                    return "추천 이유: " + recommendReasons.get(row); // 마우스를 올리면 추천 이유 표시
                }
                return null;
            }
        };
        predictionTable.setDefaultRenderer(Object.class, new WarningRowRenderer());
        JTable recentTable = new JTable(recentModel);

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.add(wrap("현황 요약", summaryArea));
        grid.add(wrap("소진 임박 품목 · 다음 구매 추천 (D-day순)", predictionTable));
        grid.add(wrap("최근 구매 " + RECENT_COUNT + "건", recentTable));
        grid.add(wrap("정산 요약 (미정산 기록, 최소 송금)", settlementArea));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshButton = new JButton("새로고침");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
        bottom.add(refreshButton);

        add(grid, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private DefaultTableModel createTableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JScrollPane wrap(String title, Component component) {
        JScrollPane scroll = new JScrollPane(component);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        return scroll;
    }

    @Override
    public void refresh() {
        refreshSummary();
        refreshPrediction();
        refreshRecent();
        refreshSettlement();
    }

    // ---------- 현황 요약 ----------

    private void refreshSummary() {
        int total = 0;
        int thisMonth = 0;
        YearMonth now = YearMonth.now();
        for (Purchase p : data.getPurchases()) {
            total += p.getCost();
            if (YearMonth.from(p.getDate()).equals(now)) {
                thisMonth += p.getCost();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("룸메이트 ").append(data.getMembers().size()).append("명 : ").append(data.getMembers()).append("\n");
        sb.append("공동 생필품 ").append(data.getItems().size()).append("개\n");
        sb.append("구매 기록 ").append(data.getPurchases().size()).append("건\n\n");
        sb.append("이번 달(").append(now.getMonthValue()).append("월) 지출 : ")
          .append(String.format("%,d", thisMonth)).append("원\n");
        sb.append("전체 누적 지출 : ").append(String.format("%,d", total)).append("원\n\n");

        int unsettledTotal = 0;
        List<Purchase> unsettled = data.getUnsettledPurchases();
        for (Purchase p : unsettled) {
            unsettledTotal += p.getCost();
        }
        LocalDate lastSettled = data.getLastSettledDate();
        sb.append("미정산 : ").append(unsettled.size()).append("건 / ")
          .append(String.format("%,d", unsettledTotal)).append("원\n");
        sb.append("마지막 정산일 : ").append(lastSettled == null ? "없음" : lastSettled.toString());
        summaryArea.setText(sb.toString());
    }

    // ---------- 소진 임박 품목 ----------

    private void refreshPrediction() {
        predictionModel.setRowCount(0);
        recommendReasons.clear();

        // AppData가 소진이 가까운 순으로 정렬한 품목과, 그 순서로 배정한 추천 담당자를 사용
        Map<String, BuyerRecommender.Recommendation> recommendations = data.getRecommendations();

        for (String name : data.getItemNamesByUrgency()) {
            BuyerRecommender.Recommendation rec = recommendations.get(name);
            String member = (rec == null) ? "-" : rec.member;
            recommendReasons.add(rec == null ? "룸메이트를 먼저 등록하세요." : rec.reason);

            List<LocalDate> dates = data.getSortedDates(name);
            if (dates.size() < 2) {
                predictionModel.addRow(new Object[]{name, "-", "-", "기록 부족 (" + dates.size() + "건)", member});
                continue;
            }

            // 최근 소비 패턴을 더 잘 반영하는 가중 이동평균 기준으로 표시
            LocalDate predicted = ConsumptionPredictor.predictWeightedMovingAverage(dates);
            long dday = ConsumptionPredictor.daysUntil(predicted);
            String status;
            if (dday < 0) {
                status = "예상일 지남";
            } else if (dday <= WARNING_DAYS) {
                status = "소진 임박";
            } else {
                status = "여유";
            }
            predictionModel.addRow(new Object[]{name, predicted.toString(),
                    PredictionPanel.formatDday(dday), status, member});
        }
    }

    /** 상태가 '소진 임박' 또는 '예상일 지남'인 행을 빨간 글씨로 표시하는 렌더러 */
    private static class WarningRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            Object status = table.getValueAt(row, 3);
            boolean warning = "소진 임박".equals(status) || "예상일 지남".equals(status);
            if (!isSelected) {
                c.setForeground(warning ? Color.RED : Color.BLACK);
            }
            return c;
        }
    }

    // ---------- 최근 구매 ----------

    private void refreshRecent() {
        recentModel.setRowCount(0);
        List<Purchase> sorted = data.getPurchasesSortedByDate();
        int count = 0;
        for (int i = sorted.size() - 1; i >= 0 && count < RECENT_COUNT; i--, count++) {
            Purchase p = sorted.get(i);
            recentModel.addRow(new Object[]{p.getDate().toString(), p.getItemName(), p.getBuyer(),
                    String.format("%,d", p.getCost())});
        }
    }

    // ---------- 정산 요약 ----------

    private void refreshSettlement() {
        List<String> members = data.getMembers();
        List<Purchase> unsettled = data.getUnsettledPurchases(); // 정산 완료된 기록은 제외

        if (members.size() < 2) {
            settlementArea.setText("룸메이트가 2명 이상 등록되면 정산 결과가 표시됩니다.");
            return;
        }
        if (unsettled.isEmpty()) {
            settlementArea.setText("정산할 구매 기록이 없습니다. (모두 정산 완료)");
            return;
        }

        List<SettlementEngine.Transaction> transactions = SettlementEngine.settleMinCashFlow(unsettled, members);
        if (transactions.isEmpty()) {
            settlementArea.setText("보낼 금액이 없습니다. (모두 같은 금액을 부담한 상태)");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (SettlementEngine.Transaction t : transactions) {
            sb.append(t.from).append(" → ").append(t.to).append(" : ")
              .append(String.format("%,d", t.amount)).append("원\n");
        }
        sb.append("\n자세한 내용은 [정산] 탭에서 확인하세요.");
        settlementArea.setText(sb.toString());
    }
}