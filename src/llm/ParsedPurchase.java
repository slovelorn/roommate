package llm;

import java.time.LocalDate;

/**
 * LLM이 자연어 문장에서 뽑아낸 구매 정보.
 * 알아내지 못한 값은 null 이다. (화면에서는 null이 아닌 값만 채운다)
 */
public record ParsedPurchase(String item, Integer price, String buyer, LocalDate date) {}