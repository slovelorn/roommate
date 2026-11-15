package llm;
import com.google.gson.Gson;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

public class PurchaseParser {
    private final LlmClient llm;
    private final Gson gson = new Gson();

    /** LLM이 보내는 JSON 그대로의 형태 (날짜는 문자열로 받은 뒤 LocalDate로 변환) */
    record RawPurchase(String item, Integer price, String buyer, String date) {}

    public PurchaseParser(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * @param text    사용자가 입력한 문장
     * @param items   등록된 품목 목록 (콤보박스에 있는 값들)
     * @param members 룸메이트 이름 목록
     */
    public ParsedPurchase parse(String text, List<String> items, List<String> members) throws Exception {
        // "어제", "지난주 금요일" 같은 표현을 계산할 수 있도록 오늘 날짜와 요일을 알려줌
        LocalDate today = LocalDate.now();
        String dayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);

        String system = """
            너는 생필품 구매 기록 파서야.
            사용자 문장에서 품목, 가격, 구매자, 구매일을 뽑아 JSON 한 줄만 출력해. 설명은 쓰지 마.
            형식: {"item":문자열,"price":정수,"buyer":문자열,"date":"YYYY-MM-DD"}
            - item은 반드시 다음 목록 중 하나로 골라: %s
            - buyer는 반드시 다음 목록 중 하나로 골라: %s
            - 알 수 없는 값은 null로 써.
            - price는 원 단위 정수로 써. (예: "1만5천원" -> 15000)
            - 오늘은 %s(%s)이야. "오늘", "어제", "그저께", "지난주 금요일" 같은 표현은 오늘을 기준으로 계산해.
            - 문장에 날짜 표현이 없으면 date는 오늘 날짜(%s)로 써.
            """.formatted(items, members, today, dayOfWeek, today);

        String answer = llm.chat(system, text);

        // 모델이 ```json ... ``` 이나 설명을 붙여도 { ... } 부분만 꺼냄
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new IllegalStateException("LLM 응답에서 JSON을 찾지 못했습니다: " + answer);
        }
        RawPurchase p = gson.fromJson(answer.substring(start, end + 1), RawPurchase.class);

        // LLM은 틀릴 수 있으니 목록에 없는 값이나 잘못된 값은 버림
        String item  = (p.item() != null && items.contains(p.item())) ? p.item() : null;
        String buyer = (p.buyer() != null && members.contains(p.buyer())) ? p.buyer() : null;
        Integer price = (p.price() != null && p.price() > 0) ? p.price() : null;
        LocalDate date = parseDate(p.date(), today);
        return new ParsedPurchase(item, price, buyer, date);
    }

    /** "YYYY-MM-DD" 문자열을 날짜로 변환. 형식이 틀리거나 미래 날짜면 null */
    private LocalDate parseDate(String text, LocalDate today) {
        if (text == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(text.trim());
            return date.isAfter(today) ? null : date;   // 아직 오지 않은 날에 산 기록은 없음
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}