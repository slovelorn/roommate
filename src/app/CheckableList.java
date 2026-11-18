package app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;

/**
 * 체크박스로 여러 항목을 골라 삭제할 수 있는 목록 (룸메이트·품목 목록에 사용).
 *
 * 평소 : 일반 목록 + [선택] 버튼
 * [선택]을 누르면 : 각 항목 앞에 체크박스가 나타나고 버튼이 [취소]로 바뀜
 * 하나 이상 체크하면 : [선택 삭제 (N)] 버튼이 나타나고, 체크한 항목은 붉은 배경으로 강조됨
 */
public class CheckableList extends JPanel {

    private static final Color CHECKED_BACKGROUND = new Color(255, 228, 228);

    private DefaultListModel<String> model = new DefaultListModel<>();
    private JList<String> list = new JList<>(model);
    private Set<String> checked = new LinkedHashSet<>();
    private boolean selectMode;

    private JLabel hintLabel = new JLabel(" ");
    private JButton deleteButton = new JButton("선택 삭제");
    private JButton selectButton = new JButton("선택");

    /**
     * @param onDelete [선택 삭제]를 눌렀을 때 체크한 항목 목록을 받아 실제 삭제를 처리하는 코드
     */
    public CheckableList(Consumer<List<String>> onDelete) {
        setLayout(new BorderLayout(0, 5));

        list.setCellRenderer(new CheckRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!selectMode) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    toggle(model.get(index));
                }
            }
        });

        selectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setSelectMode(!selectMode);
            }
        });
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!checked.isEmpty()) {
                    onDelete.accept(new ArrayList<>(checked));
                }
            }
        });

        hintLabel.setForeground(Color.GRAY);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(hintLabel);
        bottom.add(deleteButton);
        bottom.add(selectButton);

        add(new JScrollPane(list), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        updateButtons();
    }

    /** 목록 내용을 바꾼다. (선택 모드는 해제됨) */
    public void setItems(List<String> items) {
        model.clear();
        for (String item : items) {
            model.addElement(item);
        }
        setSelectMode(false);
    }

    /** 선택 모드를 켜고 끈다. 끌 때는 체크한 항목을 모두 해제한다. */
    public void setSelectMode(boolean on) {
        selectMode = on;
        checked.clear();
        list.clearSelection();
        updateButtons();
    }

    private void toggle(String item) {
        if (checked.contains(item)) {
            checked.remove(item);
        } else {
            checked.add(item);
        }
        list.clearSelection();
        updateButtons();
    }

    private void updateButtons() {
        selectButton.setText(selectMode ? "취소" : "선택");
        deleteButton.setText("선택 삭제 (" + checked.size() + ")");
        deleteButton.setVisible(selectMode && !checked.isEmpty());
        hintLabel.setText(selectMode && checked.isEmpty() ? "삭제할 항목을 체크하세요" : " ");
        list.repaint();
        revalidate();
    }

    /** 선택 모드일 때는 체크박스로, 평소에는 일반 글자로 항목을 그린다. */
    private class CheckRenderer implements ListCellRenderer<String> {
        private DefaultListCellRenderer normal = new DefaultListCellRenderer();
        private JCheckBox box = new JCheckBox();

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (!selectMode) {
                return normal.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
            boolean isChecked = checked.contains(value);
            box.setText(value);
            box.setSelected(isChecked);
            box.setOpaque(true);
            box.setBackground(isChecked ? CHECKED_BACKGROUND : list.getBackground());
            box.setForeground(list.getForeground());
            box.setFont(list.getFont());
            return box;
        }
    }
}