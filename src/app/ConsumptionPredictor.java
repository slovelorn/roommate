package app;

import java.time.LocalDate; 
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 구매 이력(날짜 리스트)을 바탕으로 다음 소진(재구매) 예상 시점을 계산하는 엔진.
 * - 단순 평균 기반 예측 (기본)
 * - 가중 이동평균 기반 예측 (최근 구매 간격에 더 큰 가중치 부여, 심화)
 *
 * 두 메서드 모두 날짜가 오래된 순으로 정렬되어 있다고 가정한다.
 */
public class ConsumptionPredictor {

    /**
     * 단순 평균 기반 다음 구매 예상일 계산.
     * 전체 구매 간격의 평균을 마지막 구매일에 더한다.
     */
    public static LocalDate predictSimpleAverage(List<LocalDate> dates) {
        if (dates == null || dates.size() < 2) {
            return null; // 예측하려면 구매 기록이 2건 이상 필요
        }

        long totalDays = 0;
        for (int i = 1; i < dates.size(); i++) {
            totalDays += ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
        }
        double avgInterval = (double) totalDays / (dates.size() - 1);

        LocalDate lastDate = dates.get(dates.size() - 1);
        return lastDate.plusDays(Math.round(avgInterval));
    }

    /**
     * 가중 이동평균 기반 다음 구매 예상일 계산.
     * 최근 구매 간격일수록 더 큰 가중치(1, 2, 3, ...)를 부여하여
     * 최근 소비 패턴 변화를 더 민감하게 반영한다.
     */
    public static LocalDate predictWeightedMovingAverage(List<LocalDate> dates) {
        if (dates == null || dates.size() < 2) {
            return null;
        }

        int n = dates.size();
        int intervalCount = n - 1;
        long[] intervals = new long[intervalCount];
        for (int i = 0; i < intervalCount; i++) {
            intervals[i] = ChronoUnit.DAYS.between(dates.get(i), dates.get(i + 1));
        }

        long weightedSum = 0;
        long weightTotal = 0;
        for (int i = 0; i < intervalCount; i++) {
            long weight = i + 1; // 오래된 간격=1, 최근 간격일수록 가중치 증가
            weightedSum += intervals[i] * weight;
            weightTotal += weight;
        }

        double weightedAvg = (double) weightedSum / weightTotal;
        LocalDate lastDate = dates.get(n - 1);
        return lastDate.plusDays(Math.round(weightedAvg));
    }

    /**
     * 오늘부터 예측일까지 남은 일수(D-day)를 계산.
     * 음수면 이미 예상 소진일이 지났다는 의미.
     */
    public static long daysUntil(LocalDate predictedDate) {
        if (predictedDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), predictedDate);
    }
}