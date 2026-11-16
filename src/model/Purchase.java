package model;

import java.time.LocalDate;

/**
 * 한 번의 구매 기록을 나타내는 클래스.
 * 어떤 품목을, 누가, 얼마에, 언제 샀는지와 정산 완료 여부를 저장한다.
 */
public class Purchase {

    private String itemName;
    private String buyer;
    private int cost;
    private LocalDate date;
    private LocalDate settledDate; // 정산 완료일 (null이면 아직 정산되지 않은 기록)

    /** 새 구매 기록 (아직 정산되지 않은 상태) */
    public Purchase(String itemName, String buyer, int cost, LocalDate date) {
        this(itemName, buyer, cost, date, null);
    }

    /** DB에서 불러올 때 사용 (정산일 포함) */
    public Purchase(String itemName, String buyer, int cost, LocalDate date, LocalDate settledDate) {
        this.itemName = itemName;
        this.buyer = buyer;
        this.cost = cost;
        this.date = date;
        this.settledDate = settledDate;
    }

    public String getItemName() {
        return itemName;
    }

    public String getBuyer() {
        return buyer;
    }

    public int getCost() {
        return cost;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalDate getSettledDate() {
        return settledDate;
    }

    public boolean isSettled() {
        return settledDate != null;
    }

    /** 정산 완료로 표시한다. 이후 정산 금액 계산에서 제외된다. */
    public void markSettled(LocalDate settledDate) {
        this.settledDate = settledDate;
    }

    @Override
    public String toString() {
        return date + " | " + itemName + " | " + buyer + " | " + cost + "원"
                + (isSettled() ? " | 정산 완료(" + settledDate + ")" : " | 미정산");
    }
}