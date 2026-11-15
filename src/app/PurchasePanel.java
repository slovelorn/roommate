package app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import llm.NlInputPanel;

/**
 * [구매 기록] 탭 (콘솔 메뉴 3, 4번).
 * - 맨 위 : 말로 입력하기 (LLM이 문장을 분석해 아래 입력 폼을 자동으로 채움, llm 패키지)
 * - 가운데 : 구매 기록 입력 폼 (품목·구매자는 콤보박스로 선택)
 * - 아래 : 전체 구매 기록 표 (JTable, 열 제목을 클릭하면 정렬)
 */
public class PurchasePanel extends JPanel implements Refreshable {

    private AppData data;
    private Runnable onDataChanged;

    private JComboBox<String> itemCombo = new JComboBox<>();
    private JComboBox<String> buyerCombo = new JComboBox<>();
    private JTextField costField = new JTextField(8);
    private JTextField dateField = new JTextField(10);

    private DefaultTableModel tableModel;
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
        String[] columns = {"구매일", "품목", "구매자", "금액(원)"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 표에서 직접 수정하지 못하게 함
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 3 ? Integer.class : String.class; // 금액은 숫자로 정렬되도록
            }
        };
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true); // 열 제목 클릭 시 정렬

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("전체 구매 기록 (날짜순)"));
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
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

        data.addPurchase(new Purchase(itemName, buyer, cost, date));
        refreshTable();
        costField.setText("");
        dateField.setText(LocalDate.now().toString());
        nlInputPanel.clear();
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
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        List<Purchase> sorted = data.getPurchasesSortedByDate();
        for (Purchase p : sorted) {
            tableModel.addRow(new Object[]{p.getDate().toString(), p.getItemName(), p.getBuyer(), p.getCost()});
        }
    }
}