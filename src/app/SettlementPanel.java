package app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * [정산] 탭 (콘솔 메뉴 6번).
 * 개인별 지출 현황(1/N 기준)과 최소 송금 정산 결과를 보여준다.
 * 탭을 열 때 자동으로 계산되며, [정산하기] 버튼으로 다시 계산할 수도 있다.
 */
public class SettlementPanel extends JPanel implements Refreshable {

    private AppData data;

    private JTextArea paidArea = new JTextArea();
    private JTextArea transactionArea = new JTextArea();

    public SettlementPanel(AppData data) {
        this.data = data;

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
        top.add(settleButton);

        paidArea.setEditable(false);
        transactionArea.setEditable(false);

        JScrollPane paidScroll = new JScrollPane(paidArea);
        paidScroll.setBorder(BorderFactory.createTitledBorder("개인별 지출 현황 (1/N 기준)"));
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
        List<Purchase> purchases = data.getPurchases();

        if (members.size() < 2) {
            paidArea.setText("정산하려면 룸메이트가 2명 이상 등록되어 있어야 합니다.");
            return;
        }
        if (purchases.isEmpty()) {
            paidArea.setText("정산할 구매 기록이 없습니다.");
            return;
        }

        Map<String, Integer> paid = SettlementEngine.calculatePaid(purchases, members);
        Map<String, Integer> shouldPay = SettlementEngine.calculateShouldPay(purchases, members);

        StringBuilder sb = new StringBuilder();
        for (String m : members) {
            sb.append(m).append(" : 낸 금액 ").append(String.format("%,d", paid.get(m)))
              .append("원 / 부담해야 할 금액 ").append(String.format("%,d", shouldPay.get(m))).append("원\n");
        }
        paidArea.setText(sb.toString());

        List<SettlementEngine.Transaction> transactions = SettlementEngine.settleMinCashFlow(purchases, members);
        if (transactions.isEmpty()) {
            transactionArea.setText("이미 모두 정산된 상태입니다.");
        } else {
            StringBuilder tb = new StringBuilder("총 " + transactions.size() + "건\n\n");
            for (SettlementEngine.Transaction t : transactions) {
                tb.append(t.from).append(" → ").append(t.to).append(" : ")
                  .append(String.format("%,d", t.amount)).append("원\n");
            }
            transactionArea.setText(tb.toString());
        }
    }

    @Override
    public void refresh() {
        settle();
    }
}