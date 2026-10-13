package app;

import java.time.LocalDate;

/**
 * 한 번의 구매 기록을 나타내는 클래스.
 * 어떤 품목을, 누가, 얼마에, 언제 샀는지를 저장한다.
 */
public class Purchase {

    private String itemName;
    private String buyer;
    private int cost;
    private LocalDate date;

    public Purchase(String itemName, String buyer, int cost, LocalDate date) {
        this.itemName = itemName;
        this.buyer = buyer;
        this.cost = cost;
        this.date = date;
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

    @Override
    public String toString() {
        return date + " | " + itemName + " | " + buyer + " | " + cost + "원";
    }
}