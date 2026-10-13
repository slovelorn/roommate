package app;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 공동 지출을 정산하는 엔진.
 * - calculatePaid / calculateShouldPay : 1/N 기준 지출 현황 계산
 * - settleMinCashFlow : 그리디(Greedy) 알고리즘으로 최소 송금 횟수 정산
 *   (각 단계에서 가장 많이 받을 사람과 가장 많이 줘야 할 사람을 매칭하여 상계 처리)
 */
public class SettlementEngine {

    /** 한 건의 송금(정산) 결과를 나타내는 내부 클래스 */
    public static class Transaction {
        public final String from; // 보내는 사람
        public final String to;   // 받는 사람
        public final int amount;

        public Transaction(String from, String to, int amount) {
            this.from = from;
            this.to = to;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return from + " -> " + to + " : " + amount + "원";
        }
    }

    /** 전체 지출을 인원수로 나눠 각자 부담해야 할 금액(1/N) 계산 */
    public static Map<String, Integer> calculateShouldPay(List<Purchase> purchases, List<String> members) {
        int total = 0;
        for (Purchase p : purchases) {
            total += p.getCost();
        }
        int share = total / members.size();

        Map<String, Integer> shouldPay = new HashMap<>();
        for (String m : members) {
            shouldPay.put(m, share);
        }
        return shouldPay;
        // 참고: total이 인원수로 나누어떨어지지 않으면 나머지는 버려진다.
        // (확장 아이디어: 나머지를 먼저 결제한 사람에게 1원씩 배분하는 방식으로 보완 가능)
    }

    /** 각자 실제로 결제한 금액 합산 */
    public static Map<String, Integer> calculatePaid(List<Purchase> purchases, List<String> members) {
        Map<String, Integer> paid = new HashMap<>();
        for (String m : members) {
            paid.put(m, 0);
        }
        for (Purchase p : purchases) {
            paid.merge(p.getBuyer(), p.getCost(), Integer::sum);
        }
        return paid;
    }

    /**
     * 그리디 기반 최소 송금 정산.
     * 1) 각자의 순잔액(net balance = 낸 금액 - 부담해야 할 금액)을 계산
     * 2) 양수(채권자)는 creditors 큐에, 음수(채무자)는 debtors 큐에 넣음 (최대값 우선)
     * 3) 가장 많이 받을 사람과 가장 많이 줘야 할 사람을 매칭해 상계 처리하고,
     *    남은 금액이 있으면 다시 큐에 넣는 과정을 반복
     */
    public static List<Transaction> settleMinCashFlow(List<Purchase> purchases, List<String> members) {
        Map<String, Integer> paid = calculatePaid(purchases, members);
        Map<String, Integer> shouldPay = calculateShouldPay(purchases, members);

        Map<String, Integer> balance = new HashMap<>();
        for (String m : members) {
            balance.put(m, paid.get(m) - shouldPay.get(m));
        }

        // 최대값이 먼저 나오는 우선순위 큐(최대 힙) 구성
        PriorityQueue<Map.Entry<String, Integer>> creditors =
                new PriorityQueue<>((a, b) -> b.getValue() - a.getValue());
        PriorityQueue<Map.Entry<String, Integer>> debtors =
                new PriorityQueue<>((a, b) -> b.getValue() - a.getValue());

        for (Map.Entry<String, Integer> e : balance.entrySet()) {
            if (e.getValue() > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            } else if (e.getValue() < 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(e.getKey(), -e.getValue()));
            }
        }

        List<Transaction> transactions = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<String, Integer> creditor = creditors.poll();
            Map.Entry<String, Integer> debtor = debtors.poll();

            int settledAmount = Math.min(creditor.getValue(), debtor.getValue());
            if (settledAmount > 0) {
                transactions.add(new Transaction(debtor.getKey(), creditor.getKey(), settledAmount));
            }

            int creditorRemain = creditor.getValue() - settledAmount;
            int debtorRemain = debtor.getValue() - settledAmount;

            if (creditorRemain > 0) {
                creditors.add(new AbstractMap.SimpleEntry<>(creditor.getKey(), creditorRemain));
            }
            if (debtorRemain > 0) {
                debtors.add(new AbstractMap.SimpleEntry<>(debtor.getKey(), debtorRemain));
            }
        }

        return transactions;
    }
}