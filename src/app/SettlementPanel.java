package app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import logic.SettlementEngine;
import model.Purchase;

/**
 * [정산] 탭.
 * 아직 정산되지 않은 구매 기록만으로 개인별 지출 현황(1/N 기준)과 최소 송금 정산 결과를 보여준다.
 * 송금을 마친 뒤 [정산 완료 처리]를 누르면 현재 기록들이 정산 완료로 표시되어 다음 정산부터 제외된다.
 */
public class SettlementPanel extends JPanel implements Refreshable {

    private AppData data;
    private Runnable onDataChanged;

    private JLabel infoLabel = new JLabel(" ");
    private JTextArea paidArea = new JTextArea();
    private JTextArea transactionArea = new JTextArea();
    private JButton completeButton = new JButton("정산 완료 처리");

    public SettlementPanel(AppData data, Runnable onDataChanged) {
        this.data = data;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton settleButton = new JButton("정산하기");
        settleButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                settle();
            }
        });
        completeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                completeSettlement();
            }
        });
        top.add(settleButton);
        top.add(completeButton);
        top.add(infoLabel);

        paidArea.setEditable(false);
        transactionArea.setEditable(false);

        JScrollPane paidScroll = new JScrollPane(paidArea);
        paidScroll.setBorder(BorderFactory.createTitledBorder("개인별 지출 현황 (미정산 기록, 1/N 기준)"));
        JScrollPane transactionScroll = new JScrollPane(transactionArea);
        transactionScroll.setBorder(BorderFactory.createTitledBorder("최소 송금 정산 결과"));

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 10));
        center.add(paidScroll);
        center.add(transactionScroll);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
    }

    private void settle() {
        paidArea.setText("");
        transactionArea.setText("");

        List<String> members = data.getMembers();
        List<Purchase> unsettled = data.getUnsettledPurchases();
        updateInfo(unsettled);
        completeButton.setEnabled(false);

        if (members.size() < 2) {
            paidArea.setText("정산하려면 룸메이트가 2명 이상 등록되어 있어야 합니다.");
            return;
        }
        if (unsettled.isEmpty()) {
            paidArea.setText("정산할 구매 기록이 없습니다. (모든 기록이 정산 완료 상태입니다)");
            return;
        }

        Map<String, Integer> paid = SettlementEngine.calculatePaid(unsettled, members);
        Map<String, Integer> shouldPay = SettlementEngine.calculateShouldPay(unsettled, members);

        StringBuilder sb = new StringBuilder();
        for (String m : members) {
            sb.append(m).append(" : 낸 금액 ").append(String.format("%,d", paid.get(m)))
              .append("원 / 부담해야 할 금액 ").append(String.format("%,d", shouldPay.get(m))).append("원\n");
        }
        paidArea.setText(sb.toString());

        List<SettlementEngine.Transaction> transactions = SettlementEngine.settleMinCashFlow(unsettled, members);
        if (transactions.isEmpty()) {
            transactionArea.setText("보낼 금액이 없습니다. (모두 같은 금액을 부담한 상태)");
        } else {
            StringBuilder tb = new StringBuilder("총 " + transactions.size() + "건\n\n");
            for (SettlementEngine.Transaction t : transactions) {
                tb.append(t.from).append(" → ").append(t.to).append(" : ")
                  .append(String.format("%,d", t.amount)).append("원\n");
            }
            tb.append("\n송금을 모두 마쳤다면 [정산 완료 처리]를 눌러 주세요.");
            transactionArea.setText(tb.toString());
        }
        completeButton.setEnabled(true);
    }

    /** 현재 미정산 기록을 모두 정산 완료로 표시한다. 다음 정산부터는 그 이후 기록만 계산된다. */
    private void completeSettlement() {
        List<Purchase> unsettled = data.getUnsettledPurchases();
        if (unsettled.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "미정산 구매 기록 " + unsettled.size() + "건(총 " + String.format("%,d", total(unsettled))
                        + "원)을 정산 완료로 처리할까요?\n\n"
                        + "위 송금이 모두 끝난 뒤에 눌러 주세요.\n처리한 기록은 다음 정산부터 금액 계산에서 제외됩니다.",
                "정산 완료 확인", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        int count = data.settleAll(LocalDate.now());
        JOptionPane.showMessageDialog(this, count + "건을 정산 완료로 처리했습니다.");
        settle();
        onDataChanged.run();
    }

    private void updateInfo(List<Purchase> unsettled) {
        LocalDate last = data.getLastSettledDate();
        String lastText = (last == null) ? "정산 이력 없음" : "마지막 정산일 " + last;
        infoLabel.setText("   정산 대상: 미정산 " + unsettled.size() + "건 (" + String.format("%,d", total(unsettled))
                + "원)   |   " + lastText);
    }

    private int total(List<Purchase> purchases) {
        int sum = 0;
        for (Purchase p : purchases) {
            sum += p.getCost();
        }
        return sum;
    }

    @Override
    public void refresh() {
        settle();
    }
}