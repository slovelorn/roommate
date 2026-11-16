package app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import db.DataStorage;
import model.Item;
import model.Purchase;

/**
 * 프로그램 전체에서 함께 사용하는 데이터(룸메이트 / 품목 / 구매기록)를 보관하는 클래스.
 * 모든 탭(패널)이 같은 AppData 객체를 공유하므로, 한 탭에서 등록한 내용을 다른 탭에서도 볼 수 있다.
 * 데이터를 추가·삭제·정산 처리할 때마다 DataStorage를 통해 즉시 DB에 저장한다.
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

    /** 전체 구매 기록 (정산 완료된 기록 포함, 소진 예측에 사용) */
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

    /** 아직 정산되지 않은 구매 기록만 반환 (정산 금액 계산은 이 기록들로만 한다) */
    public List<Purchase> getUnsettledPurchases() {
        List<Purchase> result = new ArrayList<>();
        for (Purchase p : purchases) {
            if (!p.isSettled()) {
                result.add(p);
            }
        }
        return result;
    }

    /** 가장 최근에 정산 완료 처리한 날짜 (정산한 적이 없으면 null) */
    public LocalDate getLastSettledDate() {
        LocalDate last = null;
        for (Purchase p : purchases) {
            if (p.isSettled() && (last == null || p.getSettledDate().isAfter(last))) {
                last = p.getSettledDate();
            }
        }
        return last;
    }

    /** 해당 룸메이트가 구매자인 미정산 기록 수 */
    public int countUnsettledPurchasesBy(String buyer) {
        int count = 0;
        for (Purchase p : getUnsettledPurchases()) {
            if (p.getBuyer().equals(buyer)) {
                count++;
            }
        }
        return count;
    }

    /** 해당 품목의 구매 기록 수 (정산 여부 무관) */
    public int countPurchasesOf(String itemName) {
        int count = 0;
        for (Purchase p : purchases) {
            if (p.getItemName().equals(itemName)) {
                count++;
            }
        }
        return count;
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

    // ---------- 삭제 (삭제 즉시 DB에 저장) ----------
    // 룸메이트나 품목을 삭제해도 그와 관련된 구매 기록은 지우지 않는다. (기록 보관 및 정산 금액 보존)

    public void removeMember(String name) {
        members.remove(name);
        DataStorage.saveMembers(members);
    }

    public void removeItem(String name) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getName().equals(name)) {
                items.remove(i);
                break;
            }
        }
        DataStorage.saveItems(items);
    }

    public void removePurchases(List<Purchase> targets) {
        purchases.removeAll(targets);
        DataStorage.savePurchases(purchases);
    }

    // ---------- 정산 완료 처리 ----------

    /**
     * 현재 미정산인 구매 기록을 모두 정산 완료로 표시한다.
     * 이후의 정산 금액 계산에서는 이 기록들이 제외된다.
     * @return 정산 완료로 처리한 기록 수
     */
    public int settleAll(LocalDate settledDate) {
        int count = 0;
        for (Purchase p : purchases) {
            if (!p.isSettled()) {
                p.markSettled(settledDate);
                count++;
            }
        }
        DataStorage.savePurchases(purchases);
        return count;
    }
}