package app;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 룸메이트 생필품 소진예측 + 비용 정산 시스템의 콘솔 실행 클래스.
 * 프로그램 시작 시 저장된 데이터를 불러오고, 종료 시 다시 파일에 저장한다.
 */
public class Main {

    private static final Scanner sc = new Scanner(System.in);
    private static List<String> members = new ArrayList<>();
    private static List<Item> items = new ArrayList<>();
    private static List<Purchase> purchases = new ArrayList<>();

    public static void main(String[] args) {
        loadAll();
        System.out.println("저장된 데이터를 불러왔습니다. (룸메이트 " + members.size()
                + "명 / 품목 " + items.size() + "개 / 구매기록 " + purchases.size() + "건)");

        boolean running = true;
        while (running) {
            printMenu();
            String input = sc.nextLine().trim();

            switch (input) {
                case "1" -> registerMember();
                case "2" -> registerItem();
                case "3" -> addPurchase();
                case "4" -> printAllPurchases();
                case "5" -> predictConsumption();
                case "6" -> settle();
                case "0" -> {
                    saveAll();
                    running = false;
                    System.out.println("저장 완료. 프로그램을 종료합니다.");
                }
                default -> System.out.println("올바른 번호를 입력하세요.");
            }
        }
        sc.close();
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("===== 룸메이트 생필품 소진예측 + 정산 시스템 =====");
        System.out.println("1. 룸메이트 등록");
        System.out.println("2. 공동 생필품 등록");
        System.out.println("3. 구매 기록 추가");
        System.out.println("4. 전체 구매 기록 보기");
        System.out.println("5. 품목별 소진 시점 예측");
        System.out.println("6. 비용 정산하기");
        System.out.println("0. 저장 후 종료");
        System.out.print("선택: ");
    }

    private static void registerMember() {
        System.out.print("등록할 룸메이트 이름: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("이름을 입력하세요.");
            return;
        }
        if (members.contains(name)) {
            System.out.println("이미 등록된 룸메이트입니다.");
            return;
        }
        members.add(name);
        System.out.println(name + " 등록 완료. 현재 룸메이트: " + members);
    }

    private static void registerItem() {
        System.out.print("등록할 품목명: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) {
            System.out.println("품목명을 입력하세요.");
            return;
        }
        boolean exists = items.stream().anyMatch(i -> i.getName().equals(name));
        if (exists) {
            System.out.println("이미 등록된 품목입니다.");
            return;
        }
        items.add(new Item(name));
        System.out.println(name + " 품목 등록 완료.");
    }

    private static void addPurchase() {
        if (members.isEmpty()) {
            System.out.println("먼저 룸메이트를 등록하세요. (메뉴 1번)");
            return;
        }
        if (items.isEmpty()) {
            System.out.println("먼저 품목을 등록하세요. (메뉴 2번)");
            return;
        }

        System.out.print("품목명: ");
        String itemName = sc.nextLine().trim();
        boolean itemExists = items.stream().anyMatch(i -> i.getName().equals(itemName));
        if (!itemExists) {
            System.out.println("등록되지 않은 품목입니다. 먼저 품목을 등록하세요.");
            return;
        }

        System.out.print("구매자(룸메이트 이름): ");
        String buyer = sc.nextLine().trim();
        if (!members.contains(buyer)) {
            System.out.println("등록되지 않은 룸메이트입니다.");
            return;
        }

        System.out.print("금액(원): ");
        int cost;
        try {
            cost = Integer.parseInt(sc.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("금액은 숫자로 입력하세요.");
            return;
        }

        System.out.print("구매일(YYYY-MM-DD, 비워두면 오늘 날짜): ");
        String dateInput = sc.nextLine().trim();
        LocalDate date;
        try {
            date = dateInput.isEmpty() ? LocalDate.now() : LocalDate.parse(dateInput);
        } catch (DateTimeParseException e) {
            System.out.println("날짜 형식이 올바르지 않습니다. (예: 2026-10-13)");
            return;
        }

        purchases.add(new Purchase(itemName, buyer, cost, date));
        System.out.println("구매 기록 추가 완료: " + itemName + " / " + buyer + " / " + cost + "원 / " + date);
    }

    private static void printAllPurchases() {
        if (purchases.isEmpty()) {
            System.out.println("구매 기록이 없습니다.");
            return;
        }
        System.out.println("----- 전체 구매 기록 -----");
        List<Purchase> sorted = new ArrayList<>(purchases);
        sorted.sort(Comparator.comparing(Purchase::getDate));
        for (Purchase p : sorted) {
            System.out.println(p);
        }
    }

    private static void predictConsumption() {
        if (items.isEmpty()) {
            System.out.println("등록된 품목이 없습니다.");
            return;
        }
        System.out.print("예측할 품목명: ");
        String itemName = sc.nextLine().trim();

        List<LocalDate> dates = new ArrayList<>();
        for (Purchase p : purchases) {
            if (p.getItemName().equals(itemName)) {
                dates.add(p.getDate());
            }
        }
        dates.sort(Comparator.naturalOrder());

        if (dates.size() < 2) {
            System.out.println("예측하려면 해당 품목의 구매 기록이 2건 이상 필요합니다. (현재 " + dates.size() + "건)");
            return;
        }

        LocalDate simplePrediction = ConsumptionPredictor.predictSimpleAverage(dates);
        LocalDate weightedPrediction = ConsumptionPredictor.predictWeightedMovingAverage(dates);

        System.out.println("----- " + itemName + " 소진 예측 결과 -----");
        System.out.println("단순 평균 기반 다음 구매 예상일    : " + simplePrediction
                + " (D-day " + ConsumptionPredictor.daysUntil(simplePrediction) + "일)");
        System.out.println("가중 이동평균 기반 다음 구매 예상일 : " + weightedPrediction
                + " (D-day " + ConsumptionPredictor.daysUntil(weightedPrediction) + "일)");

        long dday = ConsumptionPredictor.daysUntil(weightedPrediction);
        if (dday <= 3) {
            System.out.println(">> 소진 임박! 곧 " + itemName + "을(를) 구매해야 할 것 같습니다.");
        }
    }

    private static void settle() {
        if (members.size() < 2) {
            System.out.println("정산하려면 룸메이트가 2명 이상 등록되어 있어야 합니다.");
            return;
        }
        if (purchases.isEmpty()) {
            System.out.println("정산할 구매 기록이 없습니다.");
            return;
        }

        Map<String, Integer> paid = SettlementEngine.calculatePaid(purchases, members);
        Map<String, Integer> shouldPay = SettlementEngine.calculateShouldPay(purchases, members);

        System.out.println("----- 개인별 지출 현황 -----");
        for (String m : members) {
            System.out.println(m + " : 낸 금액 " + paid.get(m) + "원 / 부담해야 할 금액 " + shouldPay.get(m) + "원");
        }

        List<SettlementEngine.Transaction> transactions = SettlementEngine.settleMinCashFlow(purchases, members);

        System.out.println("----- 최소 송금 정산 결과 (" + transactions.size() + "건) -----");
        if (transactions.isEmpty()) {
            System.out.println("이미 모두 정산된 상태입니다.");
        } else {
            for (SettlementEngine.Transaction t : transactions) {
                System.out.println(t);
            }
        }
    }

    private static void loadAll() {
        members = DataStorage.loadMembers();
        items = DataStorage.loadItems();
        purchases = DataStorage.loadPurchases();
    }

    private static void saveAll() {
        DataStorage.saveMembers(members);
        DataStorage.saveItems(items);
        DataStorage.savePurchases(purchases);
    }
}