package app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import model.Item;

/**
 * [룸메이트·품목] 탭.
 * 화면을 좌우로 나누어 왼쪽은 룸메이트, 오른쪽은 공동 생필품 품목을 등록·삭제한다.
 * 삭제는 [선택] → 체크박스로 고르기 → [선택 삭제] 순서로 진행한다. (CheckableList)
 * 룸메이트나 품목을 삭제해도 기존 구매 기록은 그대로 남는다.
 */
public class MemberItemPanel extends JPanel implements Refreshable {

    private AppData data;
    private Runnable onDataChanged;

    private JTextField memberField = new JTextField(12);
    private CheckableList memberList = new CheckableList(names -> deleteMembers(names));

    private JTextField itemField = new JTextField(12);
    private CheckableList itemList = new CheckableList(names -> deleteItems(names));

    public MemberItemPanel(AppData data, Runnable onDataChanged) {
        this.data = data;
        this.onDataChanged = onDataChanged;

        setLayout(new GridLayout(1, 2, 10, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 등록 버튼 클릭 또는 입력칸에서 Enter 시 등록
        ActionListener memberAdd = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                registerMember();
            }
        };
        ActionListener itemAdd = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                registerItem();
            }
        };

        add(createSection("룸메이트", memberField, memberList, memberAdd));
        add(createSection("공동 생필품 품목", itemField, itemList, itemAdd));
    }

    /** [입력칸 + 등록 버튼] 과 [체크박스 목록] 으로 구성된 영역 하나를 만든다. */
    private JPanel createSection(String title, JTextField field, CheckableList list, ActionListener addAction) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("등록");
        field.addActionListener(addAction);
        addButton.addActionListener(addAction);
        inputPanel.add(field);
        inputPanel.add(addButton);

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(list, BorderLayout.CENTER);
        return panel;
    }

    private void registerMember() {
        String name = memberField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "이름을 입력하세요.");
            return;
        }
        if (data.hasMember(name)) {
            JOptionPane.showMessageDialog(this, "이미 등록된 룸메이트입니다.");
            return;
        }
        if (!data.addMember(name)) {
            return; // DB 연결 문제로 막힘 : 입력한 이름은 그대로 둠
        }
        memberField.setText("");
        refresh();
        onDataChanged.run();
    }

    private void registerItem() {
        String name = itemField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "품목명을 입력하세요.");
            return;
        }
        if (data.hasItem(name)) {
            JOptionPane.showMessageDialog(this, "이미 등록된 품목입니다.");
            return;
        }
        if (!data.addItem(name)) {
            return; // DB 연결 문제로 막힘 : 입력한 품목명은 그대로 둠
        }
        itemField.setText("");
        refresh();
        onDataChanged.run();
    }

    private void deleteMembers(List<String> names) {
        // 결제한 미정산 기록이 있는 사람은 삭제 시 그 금액을 돌려받을 사람이 사라져 정산이 틀어진다.
        List<String> blocked = new ArrayList<>();
        for (String name : names) {
            int count = data.countUnsettledPurchasesBy(name);
            if (count > 0) {
                blocked.add(name + " (미정산 결제 " + count + "건)");
            }
        }
        if (!blocked.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "다음 룸메이트는 결제한 미정산 구매 기록이 있어 삭제할 수 없습니다.\n\n"
                            + toLines(blocked)
                            + "\n[정산] 탭에서 정산을 완료한 뒤 삭제하세요.",
                    "삭제할 수 없음", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String message = "다음 룸메이트 " + names.size() + "명을 삭제할까요?\n\n" + toLines(names);
        int unsettledCount = data.getUnsettledPurchases().size();
        if (unsettledCount > 0) {
            message += "\n미정산 구매 기록 " + unsettledCount + "건은 남은 인원으로 나누어 다시 계산됩니다."
                    + "\n(이사 등으로 나가는 경우, 먼저 정산을 완료한 뒤 삭제하세요.)";
        }
        if (!confirm(message)) {
            return;
        }
        if (!data.removeMembers(names)) {
            return;
        }
        refresh();
        onDataChanged.run();
    }

    private void deleteItems(List<String> names) {
        String message = "다음 품목 " + names.size() + "개를 삭제할까요?\n\n" + toLines(names);
        int purchaseCount = 0;
        for (String name : names) {
            purchaseCount += data.countPurchasesOf(name);
        }
        if (purchaseCount > 0) {
            message += "\n이 품목들의 구매 기록 " + purchaseCount + "건은 정산과 기록 보관을 위해 그대로 남습니다."
                    + "\n(소진 예측 목록에서만 빠집니다.)";
        }
        if (!confirm(message)) {
            return;
        }
        if (!data.removeItems(names)) {
            return;
        }
        refresh();
        onDataChanged.run();
    }

    /** 목록을 "- 항목" 형태의 여러 줄 문자열로 만든다. (삭제 대상을 명확히 보여주기 위함) */
    private String toLines(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            sb.append("  - ").append(v).append("\n");
        }
        return sb.toString();
    }

    private boolean confirm(String message) {
        int answer = JOptionPane.showConfirmDialog(this, message, "삭제 확인", JOptionPane.YES_NO_OPTION);
        return answer == JOptionPane.YES_OPTION;
    }

    @Override
    public void refresh() {
        memberList.setItems(data.getMembers());
        List<String> itemNames = new ArrayList<>();
        for (Item item : data.getItems()) {
            itemNames.add(item.getName());
        }
        itemList.setItems(itemNames);
    }
}