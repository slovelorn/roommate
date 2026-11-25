package app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import llm.NlInputPanel;
import model.Item;
import model.Purchase;

/**
 * [구매 기록] 탭.
 * - 맨 위 : 말로 입력하기 (LLM이 문장을 분석해 아래 입력 폼을 자동으로 채움, llm 패키지)
 * - 가운데 : 구매 기록 입력 폼 (품목·구매자는 콤보박스로 선택)
 * - 아래 : 전체 구매 기록 표 (JTable, 열 제목을 클릭하면 정렬)
 *
 * 삭제 방식 : [선택]을 누르면 표 맨 앞에 체크박스 열이 나타나고, 하나 이상 체크하면 [선택 삭제 (N)] 버튼이 나타난다.
 *            체크한 행은 붉은 배경으로 강조되며, 이미 정산된 기록은 체크할 수 없다.
 */
public class PurchasePanel extends JPanel implements Refreshable {

    private AppData data;
    private Runnable onDataChanged;

    private JComboBox<String> itemCombo = new JComboBox<>();
    private JComboBox<String> buyerCombo = new JComboBox<>();
    private JTextField costField = new JTextField(8);
    private JTextField dateField = new JTextField(10);

    private static final int CHECK_COLUMN = 0;   // 체크박스 열 번호
    private static final Color CHECKED_BACKGROUND = new Color(255, 228, 228);
    private static final Color SETTLED_FOREGROUND = new Color(150, 150, 150);

    private DefaultTableModel tableModel;
    private JTable table;
    private TableColumn checkColumn;               // 선택 모드에서만 보이는 체크박스 열
    private List<Purchase> rowPurchases = new ArrayList<>(); // 표의 각 행에 해당하는 구매 기록 (삭제 시 사용)

    private boolean selectMode;
    private JButton selectButton = new JButton("선택");
    private JButton deleteButton = new JButton("선택 삭제");
    private JLabel selectHintLabel = new JLabel(" ");
    private NlInputPanel nlInputPanel;

    public PurchasePanel(AppData data, Runnable onDataChanged) {
        this.data = data;
        this.onDataChanged = onDataChanged;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 말로 입력하기 : 분석 결과는 저장하지 않고 아래 입력 폼만 채운다
        nlInputPanel = new NlInputPanel(
                () -> getItemNames(),
                () -> data.getMembers(),
                p -> fillForm(p.item(), p.buyer(), p.price(), p.date()));

        JPanel inputArea = new JPanel(new BorderLayout(0, 5));
        inputArea.add(nlInputPanel, BorderLayout.NORTH);
        inputArea.add(createFormPanel(), BorderLayout.CENTER);

        add(inputArea, BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
    }

    /** 등록된 품목 이름 목록 (LLM에게 이 중에서 고르도록 알려줌) */
    private List<String> getItemNames() {
        List<String> names = new ArrayList<>();
        for (Item item : data.getItems()) {
            names.add(item.getName());
        }
        return names;
    }

    private JPanel createFormPanel() {
        JPanel form = new JPanel(new GridLayout(2, 1));
        form.setBorder(BorderFactory.createTitledBorder("구매 기록 입력"));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("품목"));
        row1.add(itemCombo);
        row1.add(new JLabel("   구매자"));
        row1.add(buyerCombo);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("금액"));
        row2.add(costField);
        row2.add(new JLabel("원    구매일(YYYY-MM-DD)"));
        row2.add(dateField);

        JButton addButton = new JButton("구매 기록 추가");
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addPurchase();
            }
        });
        row2.add(addButton);

        form.add(row1);
        form.add(row2);
        return form;
    }

    private JPanel createTablePanel() {
        String[] columns = {"선택", "구매일", "품목", "구매자", "금액(원)", "정산"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 선택 모드에서 체크박스 열만 수정 가능, 이미 정산된 기록은 체크할 수 없음
                return column == CHECK_COLUMN && selectMode && !rowPurchases.get(row).isSettled();
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == CHECK_COLUMN) {
                    return Boolean.class;   // 체크박스로 표시
                }
                return column == 4 ? Integer.class : String.class; // 금액은 숫자로 정렬되도록
            }
        };
        tableModel.addTableModelListener(e -> updateSelectionBar());

        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true); // 열 제목 클릭 시 정렬
        table.setDefaultRenderer(Boolean.class, new CheckBoxRenderer());
        table.setDefaultRenderer(String.class, new RowRenderer());
        table.setDefaultRenderer(Integer.class, new RowRenderer());

        // 선택 모드에서는 행의 아무 곳이나 클릭해도 체크되도록
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (!selectMode || viewRow < 0
                        || table.convertColumnIndexToModel(viewColumn) == CHECK_COLUMN) {
                    return; // 체크박스 칸은 체크박스가 직접 처리
                }
                int modelRow = table.convertRowIndexToModel(viewRow);
                if (!rowPurchases.get(modelRow).isSettled()) {
                    boolean now = Boolean.TRUE.equals(tableModel.getValueAt(modelRow, CHECK_COLUMN));
                    tableModel.setValueAt(!now, modelRow, CHECK_COLUMN);
                }
            }
        });

        // 체크박스 열은 평소에는 숨겨 둔다
        checkColumn = table.getColumnModel().getColumn(CHECK_COLUMN);
        checkColumn.setMaxWidth(45);
        table.removeColumn(checkColumn);

        selectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setSelectMode(!selectMode);
            }
        });
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteCheckedPurchases();
            }
        });
        selectHintLabel.setForeground(Color.GRAY);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(selectHintLabel);
        bottom.add(deleteButton);
        bottom.add(selectButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("전체 구매 기록 (날짜순)"));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);

        updateSelectionBar();
        return panel;
    }

    /** 선택 모드를 켜고 끈다. 켜면 체크박스 열이 나타나고, 끄면 체크를 모두 해제하고 숨긴다. */
    private void setSelectMode(boolean on) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        if (on && !selectMode) {
            table.addColumn(checkColumn);
            table.moveColumn(table.getColumnCount() - 1, 0); // 맨 앞으로
        } else if (!on && selectMode) {
            table.removeColumn(checkColumn);
        }
        selectMode = on;
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            tableModel.setValueAt(Boolean.FALSE, row, CHECK_COLUMN);
        }
        table.clearSelection();
        table.setRowSelectionAllowed(!on); // 선택 모드에서는 파란 행 선택 대신 체크박스로만 고름
        updateSelectionBar();
    }

    /** 체크한 행 목록 (표의 모델 기준) */
    private List<Purchase> getCheckedPurchases() {
        List<Purchase> result = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (Boolean.TRUE.equals(tableModel.getValueAt(row, CHECK_COLUMN))) {
                result.add(rowPurchases.get(row));
            }
        }
        return result;
    }

    private void updateSelectionBar() {
        int count = getCheckedPurchases().size();
        selectButton.setText(selectMode ? "취소" : "선택");
        deleteButton.setText("선택 삭제 (" + count + ")");
        deleteButton.setVisible(selectMode && count > 0);
        if (selectMode && count == 0) {
            selectHintLabel.setText("삭제할 기록을 체크하세요 (정산 완료 기록은 선택 불가)");
        } else {
            selectHintLabel.setText(" ");
        }
        table.repaint();
    }

    private boolean isSettledRow(int viewRow) {
        return rowPurchases.get(table.convertRowIndexToModel(viewRow)).isSettled();
    }

    private boolean isCheckedRow(int viewRow) {
        return Boolean.TRUE.equals(tableModel.getValueAt(table.convertRowIndexToModel(viewRow), CHECK_COLUMN));
    }

    /** 체크박스 칸 : 정산된 기록은 비활성화된 체크박스로 그림 */
    private class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {
        CheckBoxRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setSelected(Boolean.TRUE.equals(value));
            setEnabled(!isSettledRow(row));
            setBackground(isCheckedRow(row) ? CHECKED_BACKGROUND : table.getBackground());
            return this;
        }
    }

    /** 일반 칸 : 금액은 쉼표로 표시, 체크한 행은 붉은 배경, 선택 모드에서 정산된 기록은 회색 글씨 */
    private class RowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(value instanceof Integer ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (value instanceof Integer) {
                setText(String.format("%,d", (Integer) value)); // 금액은 세 자리마다 쉼표 (값은 숫자 그대로라 정렬도 숫자 기준)
            }
            if (!isSelected) {
                setBackground(isCheckedRow(row) ? CHECKED_BACKGROUND : table.getBackground());
                setForeground(selectMode && isSettledRow(row) ? SETTLED_FOREGROUND : table.getForeground());
            }
            return this;
        }
    }

    private void addPurchase() {
        if (data.getMembers().isEmpty()) {
            JOptionPane.showMessageDialog(this, "먼저 룸메이트를 등록하세요. ([룸메이트·품목] 탭)");
            return;
        }
        if (data.getItems().isEmpty()) {
            JOptionPane.showMessageDialog(this, "먼저 품목을 등록하세요. ([룸메이트·품목] 탭)");
            return;
        }

        String itemName = (String) itemCombo.getSelectedItem();
        String buyer = (String) buyerCombo.getSelectedItem();

        int cost;
        try {
            cost = Integer.parseInt(costField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "금액은 숫자로 입력하세요.");
            return;
        }
        if (cost <= 0) {
            JOptionPane.showMessageDialog(this, "금액은 0보다 커야 합니다.");
            return;
        }

        String dateInput = dateField.getText().trim();
        LocalDate date;
        try {
            date = dateInput.isEmpty() ? LocalDate.now() : LocalDate.parse(dateInput);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "날짜 형식이 올바르지 않습니다. (예: 2026-10-13)");
            return;
        }

        if (!data.addPurchase(new Purchase(itemName, buyer, cost, date))) {
            return; // DB 연결 문제로 막힘 : 입력한 내용은 그대로 둠
        }
        refresh();
        costField.setText("");
        dateField.setText(LocalDate.now().toString());
        nlInputPanel.clear();
        onDataChanged.run();
    }

    /** 체크한 구매 기록을 삭제한다. (정산된 기록은 체크할 수 없으므로 미정산 기록만 대상) */
    private void deleteCheckedPurchases() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        List<Purchase> targets = getCheckedPurchases();
        if (targets.isEmpty()) {
            return;
        }

        // 삭제 대상을 한눈에 확인할 수 있도록 목록으로 보여줌
        StringBuilder sb = new StringBuilder("다음 구매 기록 " + targets.size() + "건을 삭제할까요?\n\n");
        int shown = 0;
        for (Purchase p : targets) {
            if (shown == 10) {
                sb.append("  ... 외 ").append(targets.size() - 10).append("건\n");
                break;
            }
            sb.append("  - ").append(p.getDate()).append("  ").append(p.getItemName()).append("  ")
              .append(p.getBuyer()).append("  ").append(String.format("%,d", p.getCost())).append("원\n");
            shown++;
        }

        int answer = JOptionPane.showConfirmDialog(this, sb.toString(), "삭제 확인", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        if (!data.removePurchases(targets)) {
            return;
        }
        refreshTable();
        setSelectMode(false);
        onDataChanged.run();
    }

    /**
     * 입력 폼을 주어진 값으로 채운다. (사용자가 확인 후 [구매 기록 추가]를 눌러야 저장됨)
     * LLM이 알아내지 못한 값(null)은 건드리지 않고 그대로 둔다.
     */
    public void fillForm(String itemName, String buyer, Integer cost, LocalDate date) {
        if (itemName != null) {
            itemCombo.setSelectedItem(itemName);
        }
        if (buyer != null) {
            buyerCombo.setSelectedItem(buyer);
        }
        if (cost != null) {
            costField.setText(String.valueOf(cost));
        }
        if (date != null) {
            dateField.setText(date.toString());
        }
    }

    @Override
    public void refresh() {
        // 콤보박스 목록 갱신 (기존에 선택한 값은 유지)
        Object selectedItem = itemCombo.getSelectedItem();
        itemCombo.removeAllItems();
        for (Item item : data.getItems()) {
            itemCombo.addItem(item.getName());
        }
        if (selectedItem != null) {
            itemCombo.setSelectedItem(selectedItem);
        }

        Object selectedBuyer = buyerCombo.getSelectedItem();
        buyerCombo.removeAllItems();
        for (String m : data.getMembers()) {
            buyerCombo.addItem(m);
        }
        if (selectedBuyer != null) {
            buyerCombo.setSelectedItem(selectedBuyer);
        }

        if (dateField.getText().isBlank()) {
            dateField.setText(LocalDate.now().toString()); // 기본값: 오늘 날짜
        }
        refreshTable();
        setSelectMode(false);
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        rowPurchases = data.getPurchasesSortedByDate();
        for (Purchase p : rowPurchases) {
            String settled = p.isSettled() ? p.getSettledDate() + " 정산" : "미정산";
            tableModel.addRow(new Object[]{Boolean.FALSE, p.getDate().toString(), p.getItemName(), p.getBuyer(),
                    p.getCost(), settled});
        }
    }
}