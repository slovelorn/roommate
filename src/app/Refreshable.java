package app;

/**
 * 탭이 선택될 때 화면을 최신 데이터로 다시 그려야 하는 패널이 구현하는 인터페이스.
 * 예) 룸메이트 탭에서 사람을 추가한 뒤 구매 기록 탭으로 가면, 구매자 콤보박스에 새 사람이 보여야 한다.
 */
public interface Refreshable {
    void refresh();
}