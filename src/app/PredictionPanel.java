package app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * [소진 예측] 탭 (콘솔 메뉴 5번).
 * 품목을 선택하고 [예측하기]를 누르면 단순 평균 / 가중 이동평균 기반 다음 구매 예상일을 보여준다.
 */
public class PredictionPanel extends JPanel implements Refreshable {

    private AppData data;

    private JComboBox<String> itemCombo = new JComboBox<>();
    private JLabel countLabel = new JLabel(" ");
    private JLabel simpleLabel = new JLabel(" ");
    private JLabel weightedLabel = new JLabel(" ");
    private JLabel warningLabel = new JLabel(" ");

    public PredictionPanel(AppData data) {
        this.data = data;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton predictButton = new JButton("예측하기");
        predictButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                predict();
            }
        });
        top.add(new JLabel("예측할 품목"));
        top.add(itemCombo);
        top.add(predictButton);

        Font resultFont = new Font("Dialog", Font.PLAIN, 16);
        simpleLabel.setFont(resultFont);
        weightedLabel.setFont(resultFont);
        warningLabel.setFont(resultFont.deriveFont(Font.BOLD));
        warningLabel.setForeground(Color.RED);

        JPanel result = new JPanel(new GridLayout(4, 1, 0, 5));
        result.setBorder(BorderFactory.createTitledBorder("소진 예측 결과"));
        result.add(countLabel);
        result.add(simpleLabel);
        result.add(weightedLabel);
        result.add(warningLabel);

        JPanel resultWrapper = new JPanel(new BorderLayout());
        resultWrapper.add(result, BorderLayout.NORTH);

        add(top, BorderLayout.NORTH);
        add(resultWrapper, BorderLayout.CENTER);
    }

    private void predict() {
        String itemName = (String) itemCombo.getSelectedItem();
        clearResult();
        if (itemName == null) {
            countLabel.setText("등록된 품목이 없습니다.");
            return;
        }

        List<LocalDate> dates = data.getSortedDates(itemName);
        countLabel.setText(itemName + " 구매 기록 " + dates.size() + "건");

        if (dates.size() < 2) {
            simpleLabel.setText("예측하려면 해당 품목의 구매 기록이 2건 이상 필요합니다.");
            return;
        }

        LocalDate simplePrediction = ConsumptionPredictor.predictSimpleAverage(dates);
        LocalDate weightedPrediction = ConsumptionPredictor.predictWeightedMovingAverage(dates);
        long simpleDday = ConsumptionPredictor.daysUntil(simplePrediction);
        long weightedDday = ConsumptionPredictor.daysUntil(weightedPrediction);

        simpleLabel.setText("단순 평균 기반 다음 구매 예상일      : " + simplePrediction + "  (" + formatDday(simpleDday) + ")");
        weightedLabel.setText("가중 이동평균 기반 다음 구매 예상일 : " + weightedPrediction + "  (" + formatDday(weightedDday) + ")");

        if (weightedDday <= 3) {
            warningLabel.setText("소진 임박! 곧 " + itemName + "을(를) 구매해야 할 것 같습니다.");
        }
    }

    private void clearResult() {
        countLabel.setText(" ");
        simpleLabel.setText(" ");
        weightedLabel.setText(" ");
        warningLabel.setText(" ");
    }

    /** D-day 표시 형식 : 남음 → D-5, 오늘 → D-Day, 지남 → D+2 */
    public static String formatDday(long dday) {
        if (dday > 0) {
            return "D-" + dday;
        } else if (dday == 0) {
            return "D-Day";
        } else {
            return "D+" + (-dday);
        }
    }

    @Override
    public void refresh() {
        Object selected = itemCombo.getSelectedItem();
        itemCombo.removeAllItems();
        for (Item item : data.getItems()) {
            itemCombo.addItem(item.getName());
        }
        if (selected != null) {
            itemCombo.setSelectedItem(selected);
        }
        clearResult();
    }
}