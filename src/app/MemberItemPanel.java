package app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import model.Item;

/**
 * [룸메이트·품목] 탭.
 * 화면을 좌우로 나누어 왼쪽은 룸메이트, 오른쪽은 공동 생필품 품목을 등록·삭제하고 목록(JList)으로 보여준다.
 * 룸메이트나 품목을 삭제해도 기존 구매 기록은 그대로 남는다.
 */
public class MemberItemPanel extends JPanel implements Refreshable {

    private AppData data;
    private Runnable onDataChanged;

    private JTextField memberField = new JTextField(12);
    private DefaultListModel<String> memberModel = new DefaultListModel<>();
    private JList<String> memberList = new JList<>(memberModel);

    private JTextField itemField = new JTextField(12);
    private DefaultListModel<String> itemModel = new DefaultListModel<>();
    private JList<String> itemList = new JList<>(itemModel);

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
        ActionListener memberDelete = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteMember();
            }
        };
        ActionListener itemAdd = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                registerItem();
            }
        };
        ActionListener itemDelete = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteItem();
            }
        };

        add(createSection("룸메이트", memberField, memberList, memberAdd, memberDelete));
        add(createSection("공동 생필품 품목", itemField, itemList, itemAdd, itemDelete));
    }

    /** [입력칸 + 등록 버튼] / [목록] / [선택 삭제 버튼] 으로 구성된 영역 하나를 만든다. */
    private JPanel createSection(String title, JTextField field, JList<String> list,
                                 ActionListener addAction, ActionListener deleteAction) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("등록");
        field.addActionListener(addAction);
        addButton.addActionListener(addAction);
        inputPanel.add(field);
        inputPanel.add(addButton);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton deleteButton = new JButton("선택 삭제");
        deleteButton.addActionListener(deleteAction);
        bottomPanel.add(deleteButton);

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);
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
        data.addMember(name);
        memberModel.addElement(name);
        memberField.setText("");
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
        data.addItem(name);
        itemModel.addElement(name);
        itemField.setText("");
        onDataChanged.run();
    }

    private void deleteMember() {
        String name = memberList.getSelectedValue();
        if (name == null) {
            JOptionPane.showMessageDialog(this, "삭제할 룸메이트를 목록에서 선택하세요.");
            return;
        }

        // 이 사람이 결제한 미정산 기록이 있으면, 삭제 시 그 금액을 돌려받을 사람이 사라져 정산이 틀어진다.
        int unsettledByMember = data.countUnsettledPurchasesBy(name);
        if (unsettledByMember > 0) {
            JOptionPane.showMessageDialog(this,
                    name + "님이 결제한 미정산 구매 기록이 " + unsettledByMember + "건 있어 삭제할 수 없습니다.\n"
                            + "[정산] 탭에서 정산을 완료한 뒤 삭제하세요.");
            return;
        }

        String message = name + "님을 삭제할까요?";
        int unsettledCount = data.getUnsettledPurchases().size();
        if (unsettledCount > 0) {
            message += "\n\n미정산 구매 기록 " + unsettledCount + "건은 남은 인원으로 나누어 다시 계산됩니다."
                    + "\n(이사 등으로 나가는 경우, 먼저 정산을 완료한 뒤 삭제하세요.)";
        }
        if (!confirm(message)) {
            return;
        }
        data.removeMember(name);
        memberModel.removeElement(name);
        onDataChanged.run();
    }

    private void deleteItem() {
        String name = itemList.getSelectedValue();
        if (name == null) {
            JOptionPane.showMessageDialog(this, "삭제할 품목을 목록에서 선택하세요.");
            return;
        }

        String message = "'" + name + "' 품목을 삭제할까요?";
        int purchaseCount = data.countPurchasesOf(name);
        if (purchaseCount > 0) {
            message += "\n\n이 품목의 구매 기록 " + purchaseCount + "건은 정산과 기록 보관을 위해 그대로 남습니다."
                    + "\n(소진 예측 목록에서만 빠집니다.)";
        }
        if (!confirm(message)) {
            return;
        }
        data.removeItem(name);
        itemModel.removeElement(name);
        onDataChanged.run();
    }

    private boolean confirm(String message) {
        int answer = JOptionPane.showConfirmDialog(this, message, "삭제 확인", JOptionPane.YES_NO_OPTION);
        return answer == JOptionPane.YES_OPTION;
    }

    @Override
    public void refresh() {
        memberModel.clear();
        for (String m : data.getMembers()) {
            memberModel.addElement(m);
        }
        itemModel.clear();
        for (Item item : data.getItems()) {
            itemModel.addElement(item.getName());
        }
    }
}