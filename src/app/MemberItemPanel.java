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

/**
 * [룸메이트·품목] 탭 (콘솔 메뉴 1, 2번).
 * 화면을 좌우로 나누어 왼쪽은 룸메이트, 오른쪽은 공동 생필품 품목을 등록하고 목록(JList)으로 보여준다.
 */
public class MemberItemPanel extends JPanel implements Refreshable {

    private AppData data;
    private Runnable onDataChanged;

    private JTextField memberField = new JTextField(12);
    private DefaultListModel<String> memberModel = new DefaultListModel<>();

    private JTextField itemField = new JTextField(12);
    private DefaultListModel<String> itemModel = new DefaultListModel<>();

    public MemberItemPanel(AppData data, Runnable onDataChanged) {
        this.data = data;
        this.onDataChanged = onDataChanged;

        setLayout(new GridLayout(1, 2, 10, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 등록 버튼 클릭 또는 입력칸에서 Enter 시 등록
        ActionListener memberAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                registerMember();
            }
        };
        ActionListener itemAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                registerItem();
            }
        };

        add(createSection("룸메이트", memberField, memberModel, memberAction));
        add(createSection("공동 생필품 품목", itemField, itemModel, itemAction));
    }

    /** [입력칸 + 등록 버튼] 과 [목록] 으로 구성된 영역 하나를 만든다. */
    private JPanel createSection(String title, JTextField field, DefaultListModel<String> model,
                                 ActionListener action) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createTitledBorder(title));

        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton button = new JButton("등록");
        field.addActionListener(action);
        button.addActionListener(action);
        inputPanel.add(field);
        inputPanel.add(button);

        JList<String> list = new JList<>(model);

        panel.add(inputPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
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