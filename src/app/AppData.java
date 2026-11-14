package app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 프로그램 전체에서 함께 사용하는 데이터(룸메이트 / 품목 / 구매기록)를 보관하는 클래스.
 * 모든 탭(패널)이 같은 AppData 객체를 공유하므로, 한 탭에서 등록한 내용을 다른 탭에서도 볼 수 있다.
 * 데이터를 추가할 때마다 DataStorage를 통해 즉시 DB에 저장한다.
 */
public class AppData {

    private List<String> members;
    private List<Item> items;
    private List<Purchase> purchases;

    /** 생성 시 DB에 저장된 데이터를 불러온다. */
    public AppData() {
        members = DataStorage.loadMembers();
        items = DataStorage.loadItems();
        purchases = DataStorage.loadPurchases();
    }

    public List<String> getMembers() {
        return members;
    }

    public List<Item> getItems() {
        return items;
    }

    public List<Purchase> getPurchases() {
        return purchases;
    }

    // ---------- 조회 ----------

    public boolean hasMember(String name) {
        return members.contains(name);
    }

    public boolean hasItem(String name) {
        for (Item item : items) {
            if (item.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** 특정 품목의 구매 날짜들을 오래된 순으로 정렬하여 반환 (소진 예측에 사용) */
    public List<LocalDate> getSortedDates(String itemName) {
        List<LocalDate> dates = new ArrayList<>();
        for (Purchase p : purchases) {
            if (p.getItemName().equals(itemName)) {
                dates.add(p.getDate());
            }
        }
        dates.sort(Comparator.naturalOrder());
        return dates;
    }

    /** 구매 기록을 날짜순(오래된 순)으로 정렬한 복사본 반환 */
    public List<Purchase> getPurchasesSortedByDate() {
        List<Purchase> sorted = new ArrayList<>(purchases);
        sorted.sort(Comparator.comparing(Purchase::getDate));
        return sorted;
    }

    // ---------- 추가 (추가 즉시 DB에 저장) ----------

    public void addMember(String name) {
        members.add(name);
        DataStorage.saveMembers(members);
    }

    public void addItem(String name) {
        items.add(new Item(name));
        DataStorage.saveItems(items);
    }

    public void addPurchase(Purchase purchase) {
        purchases.add(purchase);
        DataStorage.savePurchases(purchases);
    }
}