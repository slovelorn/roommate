package llm;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 자연어 구매 입력 패널 ([구매 기록] 탭 맨 위에 붙어 있음)
 * 예: "어제 마트에서 휴지 15,000원 철수가 계산함" → 품목/가격/구매자/구매일 자동 채우기
 * 자동으로 채우기만 하고 저장은 하지 않는다. 사용자가 확인 후 [구매 기록 추가]를 눌러야 저장된다.
 */
public class NlInputPanel extends JPanel {
    private static final String MODEL = "gemma-4-26b";

    private final JTextField inputField = new JTextField(35);
    private final JButton autoFillButton = new JButton("자동 입력");
    private final JLabel statusLabel = new JLabel(" ");
    private final Color normalColor = statusLabel.getForeground();

    private final Supplier<List<String>> itemsSupplier;
    private final Supplier<List<String>> membersSupplier;
    private final Consumer<ParsedPurchase> onParsed;

    private PurchaseParser parser;   // 처음 사용할 때 생성

    /**
     * @param itemsSupplier   등록된 품목 목록을 돌려주는 코드 (버튼을 누를 때마다 최신 목록을 가져옴)
     * @param membersSupplier 룸메이트 이름 목록을 돌려주는 코드
     * @param onParsed        분석 결과를 받아 기존 화면을 채우는 코드
     */
    public NlInputPanel(Supplier<List<String>> itemsSupplier, Supplier<List<String>> membersSupplier,
                        Consumer<ParsedPurchase> onParsed) {
        this.itemsSupplier = itemsSupplier;
        this.membersSupplier = membersSupplier;
        this.onParsed = onParsed;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("말로 입력하기 (AI 자동 입력)"));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.add(inputField);
        row.add(autoFillButton);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
        add(row, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        inputField.setToolTipText("예: 어제 마트에서 휴지 15,000원 철수가 계산함");
        autoFillButton.addActionListener(e -> runAutoFill());
        inputField.addActionListener(e -> runAutoFill());   // Enter 키로도 실행
    }

    /** 입력칸과 안내 문구를 비운다. (구매 기록을 추가한 뒤 호출) */
    public void clear() {
        inputField.setText("");
        showStatus(" ", false);
    }

    private void runAutoFill() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            showStatus("문장을 입력해 주세요.", true);
            return;
        }

        // 버튼을 누른 시점의 최신 목록을 복사해서 사용 (실행 중 새로 등록한 품목·룸메이트도 반영)
        List<String> items = new ArrayList<>(itemsSupplier.get());
        List<String> members = new ArrayList<>(membersSupplier.get());
        if (items.isEmpty() || members.isEmpty()) {
            showStatus("먼저 [룸메이트·품목] 탭에서 룸메이트와 품목을 등록하세요.", true);
            return;
        }

        autoFillButton.setEnabled(false);
        showStatus("분석 중...", false);

        new SwingWorker<ParsedPurchase, Void>() {
            @Override
            protected ParsedPurchase doInBackground() throws Exception {
                if (parser == null) {
                    parser = new PurchaseParser(new LlmClient(MODEL));   // 키가 없으면 여기서 ApiKeyException
                }
                return parser.parse(text, items, members);
            }

            @Override
            protected void done() {
                try {
                    ParsedPurchase p = get();
                    onParsed.accept(p);
                    showResult(p);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    cause.printStackTrace();   // 자세한 원인은 콘솔에서 확인
                    showStatus(errorMessage(cause), true);
                } catch (InterruptedException ex) {
                    showStatus("자동 입력이 중단되었습니다.", true);
                } finally {
                    autoFillButton.setEnabled(true);
                }
            }
        }.execute();
    }

    /** 분석 결과 안내 : 무엇을 찾지 못했는지 알려줌 */
    private void showResult(ParsedPurchase p) {
        List<String> missing = new ArrayList<>();
        if (p.item() == null) missing.add("품목");
        if (p.price() == null) missing.add("금액");
        if (p.buyer() == null) missing.add("구매자");
        if (p.date() == null) missing.add("구매일");

        if (missing.size() == 4) {
            showStatus("내용을 알아보지 못했어요. 직접 입력해 주세요.", true);
        } else if (missing.isEmpty()) {
            showStatus("자동 입력 완료 — 확인 후 [구매 기록 추가]를 눌러 저장하세요.", false);
        } else {
            showStatus("자동 입력 완료 — " + String.join(", ", missing)
                    + "은(는) 찾지 못했어요. 직접 입력 후 저장하세요.", true);
        }
    }

    /** 오류 종류별 안내 문구 */
    private String errorMessage(Throwable cause) {
        if (cause instanceof LlmClient.ApiKeyException) {
            return cause.getMessage();
        }
        if (cause instanceof IOException) {
            return "LLM 서버에 연결할 수 없습니다. 인터넷 연결을 확인해 주세요.";
        }
        return "자동 입력 실패 — 직접 입력해 주세요.";
    }

    private void showStatus(String message, boolean warning) {
        statusLabel.setText(message);
        statusLabel.setForeground(warning ? Color.RED : normalColor);
    }
}