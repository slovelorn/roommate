package logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import model.Purchase;

/**
 * 품목별 다음 구매 담당자를 추천하는 엔진.
 *
 * 추천 기준 (혼합 방식)
 * 1) 지출 균형 : 아직 정산되지 않은 기록에서 돈을 가장 적게 낸 사람을 추천한다.
 *    → 그 사람이 사면 정산할 금액이 줄어 송금도 줄어든다.
 * 2) 품목 순번 : 지출이 같으면(예: 정산 직후 모두 0원) 그 품목을 마지막에 산 사람의 다음 순번을 추천한다.
 *
 * 여러 품목을 한꺼번에 추천할 때는 소진이 가까운 품목부터 차례로 배정하고,
 * 배정할 때마다 그 품목의 평균 구매 금액을 담당자의 예상 지출에 더한다.
 * → 한 사람에게 모든 품목이 몰리지 않고 자연스럽게 나뉜다. (정산과 같은 그리디 방식)
 */
public class BuyerRecommender {

    /** 품목 하나에 대한 추천 결과 */
    public static class Recommendation {
        public final String itemName;
        public final String member;   // 추천 담당자
        public final String reason;   // 추천 이유 (화면 표시용)

        public Recommendation(String itemName, String member, String reason) {
            this.itemName = itemName;
            this.member = member;
            this.reason = reason;
        }

        @Override
        public String toString() {
            return itemName + " → " + member + " (" + reason + ")";
        }
    }

    /**
     * @param itemsByUrgency 추천할 품목 이름들 (소진이 가까운 순서로 정렬된 상태)
     * @param members        현재 룸메이트 목록 (등록 순서 = 순번 순서)
     * @param purchases      전체 구매 기록 (정산 완료 기록 포함)
     * @return 품목 이름 → 추천 결과 (룸메이트가 없으면 빈 결과)
     */
    public static Map<String, Recommendation> recommend(List<String> itemsByUrgency, List<String> members,
                                                        List<Purchase> purchases) {
        Map<String, Recommendation> result = new LinkedHashMap<>();
        if (members.isEmpty()) {
            return result;
        }

        // 1) 미정산 기록 기준 현재 지출 (삭제된 룸메이트의 기록은 제외)
        Map<String, Integer> spent = new LinkedHashMap<>();
        for (String m : members) {
            spent.put(m, 0);
        }
        for (Purchase p : purchases) {
            if (!p.isSettled() && spent.containsKey(p.getBuyer())) {
                spent.merge(p.getBuyer(), p.getCost(), Integer::sum);
            }
        }
        Map<String, Integer> projected = new LinkedHashMap<>(spent); // 추천을 반영한 예상 지출

        // 2) 소진이 가까운 품목부터 차례로 배정
        for (String item : itemsByUrgency) {
            int min = Integer.MAX_VALUE;
            for (int value : projected.values()) {
                min = Math.min(min, value);
            }
            List<String> candidates = new ArrayList<>();
            for (String m : members) {
                if (projected.get(m) == min) {
                    candidates.add(m);
                }
            }

            String chosen;
            String reason;
            if (candidates.size() == 1) {
                chosen = candidates.get(0);
                if (projected.get(chosen).equals(spent.get(chosen))) {
                    reason = "미정산 지출이 가장 적음 (" + won(min) + ")";
                } else {
                    reason = "앞선 추천을 반영한 예상 지출이 가장 적음 (" + won(min) + ")";
                }
            } else {
                // 지출이 같은 사람끼리는 품목 순번으로 결정
                String lastBuyer = findLastBuyer(item, purchases, members);
                chosen = nextInTurn(lastBuyer, candidates, members);
                if (lastBuyer == null) {
                    reason = "지출이 같아 등록 순서로 추천";
                } else {
                    reason = "지출이 같아 " + item + " 순번으로 추천 (지난번 구매: " + lastBuyer + ")";
                }
            }

            result.put(item, new Recommendation(item, chosen, reason));
            projected.merge(chosen, averageCost(item, purchases), Integer::sum);
        }
        return result;
    }

    /** 해당 품목을 가장 최근에 산 사람 (현재 룸메이트 중에서, 없으면 null) */
    private static String findLastBuyer(String item, List<Purchase> purchases, List<String> members) {
        Purchase last = null;
        for (Purchase p : purchases) {
            if (p.getItemName().equals(item) && members.contains(p.getBuyer())
                    && (last == null || !p.getDate().isBefore(last.getDate()))) {
                last = p;
            }
        }
        return last == null ? null : last.getBuyer();
    }

    /** 등록 순서상 lastBuyer 다음 사람부터 차례로 돌면서, 후보 중 가장 먼저 나오는 사람 */
    private static String nextInTurn(String lastBuyer, List<String> candidates, List<String> members) {
        int start = (lastBuyer == null) ? 0 : members.indexOf(lastBuyer) + 1;
        for (int i = 0; i < members.size(); i++) {
            String m = members.get((start + i) % members.size());
            if (candidates.contains(m)) {
                return m;
            }
        }
        return candidates.get(0);
    }

    /** 해당 품목의 평균 구매 금액 (구매 기록이 없으면 0) */
    private static int averageCost(String item, List<Purchase> purchases) {
        int sum = 0;
        int count = 0;
        for (Purchase p : purchases) {
            if (p.getItemName().equals(item)) {
                sum += p.getCost();
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static String won(int amount) {
        return String.format("%,d원", amount);
    }
}