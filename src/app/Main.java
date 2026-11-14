package app;

/**
 * 룸메이트 생필품 소진예측 + 비용 정산 시스템의 실행 클래스 (GUI 버전).
 * 프로그램 시작 시 DB에 저장된 데이터를 불러오고 메인 창을 띄운다.
 * 데이터는 등록할 때마다 즉시 DB에 저장되므로 종료 시 별도의 저장 과정이 없다.
 */
public class Main {

    public static void main(String[] args) {
        AppData data = new AppData();
        new MainFrame(data);
    }
}