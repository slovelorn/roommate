package app;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import db.DataStorage;
import logic.BuyerRecommender;
import logic.ConsumptionPredictor;
import model.Item;
import model.Purchase;

/**
 * 프로그램 전체에서 함께 사용하는 데이터(룸메이트 / 품목 / 구매기록)를 보관하는 클래스.
 * 모든 탭(패널)이 같은 AppData 객체를 공유하므로, 한 탭에서 등록한 내용을 다른 탭에서도 볼 수 있다.
 * 데이터를 추가·삭제·정산 처리할 때마다 DataStorage를 통해 즉시 DB에 저장한다.
 *
 * 시작할 때 DB에 연결하지 못했다면(loaded = false) 빈 목록으로 시작하고, 그 상태에서는 데이터를 바꾸지 않는다.
 * (빈 목록을 그대로 저장해서 DB에 있던 데이터를 지워 버리는 일을 막기 위함)
 * 나중에 연결되면 DB 데이터를 먼저 다시 불러오고, 사용자가 최신 화면에서 작업을 다시 하도록 안내한다.
 */
public class AppData {

    private List<String> members = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private List<Purchase> purchases = new ArrayList<>();

    private boolean loaded;      // DB에서 데이터를 정상적으로 불러왔는지
    private String loadError;    // 불러오지 못했을 때의 원인

    private Runnable onReloaded;          // DB 데이터를 새로 불러왔을 때 화면에 알리는 동작
    private Consumer<String> onBlocked;   // DB 연결이 없어 변경을 막았을 때 화면에 알리는 동작 (원인 전달)

    /** 생성 시 DB에 저장된 데이터를 불러온다. */
    public AppData() {
        reload();
    }

    /**
     * DB에서 데이터를 다시 불러온다. 연결에 실패하면 현재 메모리의 데이터는 그대로 둔다.
     * @return 불러오기 성공 여부
     */
    public boolean reload() {
        loadError = DataStorage.checkConnection();
        if (loadError != null) {
            loaded = false;
            return false;
        }
        members = DataStorage.loadMembers();
        items = DataStorage.loadItems();
        purchases = DataStorage.loadPurchases();
        loaded = true;
        return true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public String getLoadError() {
        return loadError;
    }

    public void setOnReloaded(Runnable onReloaded) {
        this.onReloaded = onReloaded;
    }

    public void setOnBlocked(Consumer<String> onBlocked) {
        this.onBlocked = onBlocked;
    }

    /**
     * 데이터를 바꿔도 되는 상태인지 확인한다.
     * - 정상적으로 불러온 상태면 true
     * - 시작할 때 불러오지 못한 상태면 DB에서 다시 불러와 보고, 이번 작업은 중단(false)한다.
     *   (불러오기에 성공하면 화면을 최신 데이터로 바꾼 뒤 작업을 다시 하도록 안내)
     */
    private boolean readyToChange() {
        if (loaded) {
            return true;
        }
        if (reload()) {
            if (onReloaded != null) {
                onReloaded.run();
            }
        } else if (onBlocked != null) {
            onBlocked.accept(loadError);
        }
        return false;
    }

    /**
     * 현재 메모리의 데이터 전체를 DB에 다시 저장한다. (저장 실패 후 연결이 복구됐을 때 사용)
     * @return 세 가지 모두 저장에 성공했는지
     */
    public boolean saveAll() {
        if (!loaded) {
            return false; // DB 데이터를 불러오지 못한 상태에서 덮어쓰면 기존 데이터가 사라지므로 저장하지 않음
        }
        boolean ok = DataStorage.saveMembers(members);
        ok = DataStorage.saveItems(items) && ok;
        ok = DataStorage.savePurchases(purchases) && ok;
        return ok;
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

    /**
     * 품목 이름을 소진이 가까운 순서로 정렬해서 반환한다. (가중 이동평균 예측 기준)
     * 예측할 수 없는 품목(구매 기록 2건 미만)은 등록 순서대로 맨 뒤에 둔다.
     */
    public List<String> getItemNamesByUrgency() {
        List<String> predicted = new ArrayList<>();
        List<Long> ddays = new ArrayList<>();
        List<String> lacking = new ArrayList<>();

        for (Item item : items) {
            List<LocalDate> dates = getSortedDates(item.getName());
            if (dates.size() < 2) {
                lacking.add(item.getName());
                continue;
            }
            long dday = ConsumptionPredictor.daysUntil(ConsumptionPredictor.predictWeightedMovingAverage(dates));
            int index = 0;
            while (index < ddays.size() && ddays.get(index) <= dday) {
                index++;
            }
            ddays.add(index, dday);
            predicted.add(index, item.getName());
        }
        predicted.addAll(lacking);
        return predicted;
    }

    /**
     * 가중 이동평균 예측 기준으로 오늘부터 예상 소진일까지 남은 일수(D-day)를 반환한다.
     * 음수면 예상일이 지난 것이고, 구매 기록이 2건 미만이라 예측할 수 없으면 null.
     */
    public Long getDday(String itemName) {
        List<LocalDate> dates = getSortedDates(itemName);
        if (dates.size() < 2) {
            return null;
        }
        return ConsumptionPredictor.daysUntil(ConsumptionPredictor.predictWeightedMovingAverage(dates));
    }

    /**
     * 소진 임박 품목 (D-day가 days일 이내이거나 예상일이 지난 품목)을 소진이 가까운 순으로 반환한다.
     * 시작 시 소진 임박 알림에 사용한다.
     */
    public List<String> getImminentItems(int days) {
        List<String> result = new ArrayList<>();
        for (String name : getItemNamesByUrgency()) {
            Long dday = getDday(name);
            if (dday != null && dday <= days) {
                result.add(name);
            }
        }
        return result;
    }

    /** 품목별 구매 담당자 추천 결과 (품목 이름 → 추천). 한눈에 보기와 소진 예측 탭이 같은 결과를 쓴다. */
    public Map<String, BuyerRecommender.Recommendation> getRecommendations() {
        return BuyerRecommender.recommend(getItemNamesByUrgency(), members, purchases);
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
    // 반환값 : 변경이 적용되었으면 true, DB 연결 문제로 막혔으면 false (입력 내용을 지우지 않기 위해 사용)

    public boolean addMember(String name) {
        if (!readyToChange()) {
            return false;
        }
        members.add(name);
        DataStorage.saveMembers(members);
        return true;
    }

    public boolean addItem(String name) {
        if (!readyToChange()) {
            return false;
        }
        items.add(new Item(name));
        DataStorage.saveItems(items);
        return true;
    }

    public boolean addPurchase(Purchase purchase) {
        if (!readyToChange()) {
            return false;
        }
        purchases.add(purchase);
        DataStorage.savePurchases(purchases);
        return true;
    }

    // ---------- 삭제 (삭제 즉시 DB에 저장) ----------
    // 룸메이트나 품목을 삭제해도 그와 관련된 구매 기록은 지우지 않는다. (기록 보관 및 정산 금액 보존)

    public boolean removeMembers(List<String> names) {
        if (!readyToChange()) {
            return false;
        }
        members.removeAll(names);
        DataStorage.saveMembers(members);
        return true;
    }

    public boolean removeItems(List<String> names) {
        if (!readyToChange()) {
            return false;
        }
        items.removeIf(item -> names.contains(item.getName()));
        DataStorage.saveItems(items);
        return true;
    }

    public boolean removePurchases(List<Purchase> targets) {
        if (!readyToChange()) {
            return false;
        }
        purchases.removeAll(targets);
        DataStorage.savePurchases(purchases);
        return true;
    }

    // ---------- 정산 완료 처리 ----------

    /**
     * 현재 미정산인 구매 기록을 모두 정산 완료로 표시한다.
     * 이후의 정산 금액 계산에서는 이 기록들이 제외된다.
     * @return 정산 완료로 처리한 기록 수 (DB 연결 문제로 처리하지 못했으면 -1)
     */
    public int settleAll(LocalDate settledDate) {
        if (!readyToChange()) {
            return -1;
        }
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