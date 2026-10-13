package app;

/**
 * 공동으로 사용하는 생필품 품목을 나타내는 클래스.
 * 실제 구매 이력은 Purchase 리스트에서 품목명으로 조회한다.
 */
public class Item {

    private String name;

    public Item(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}