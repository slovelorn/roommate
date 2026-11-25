package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import model.Item;
import model.Purchase;

/**
 * 룸메이트 / 품목 / 구매기록을 MySQL 데이터베이스에 저장하고 불러오는 클래스.
 * 프로그램을 종료했다가 다시 실행해도 이전 데이터가 유지되도록 한다.
 * 저장에 성공·실패하면 등록된 StatusListener에 알려서 화면이 연결 상태를 표시할 수 있게 한다.
 */
public class DataStorage {

    private static final String URL = "jdbc:mysql://localhost:3306/roommate?serverTimezone=UTC&connectTimeout=3000";
    private static final String ID = "root";   // 자신의 MySQL 아이디로 변경
    private static final String PW = "1234";   // 자신의 MySQL 비밀번호로 변경

    /** DB 작업 결과를 전달받는 쪽(화면)이 구현하는 인터페이스 */
    public interface StatusListener {
        void onSuccess();                            // 저장 성공
        void onError(String action, String reason);  // 저장 실패 (작업 이름, 원인 설명)
    }

    private static StatusListener listener;

    public static void setStatusListener(StatusListener statusListener) {
        listener = statusListener;
    }

    /** JDBC 드라이버를 로드하고 데이터베이스와 연결한다. */
    private static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(URL, ID, PW);
    }

    /** SQL 문자열 안에 넣을 값의 작은따옴표와 역슬래시를 이스케이프한다. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    // ---------- 연결 상태 ----------

    /**
     * DB에 연결해서 프로그램이 쓰는 테이블과 컬럼이 모두 있는지 확인한다.
     * @return 정상이면 null, 문제가 있으면 사용자에게 보여줄 원인 설명
     */
    public static String checkConnection() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("select name from member limit 1").close();
            stmt.executeQuery("select name from item limit 1").close();
            stmt.executeQuery("select item_name, buyer, cost, purchase_date, settled_date from purchase limit 1").close();
            return null;
        } catch (ClassNotFoundException | SQLException e) {
            return describe(e);
        }
    }

    /** 예외를 사용자가 이해할 수 있는 원인 설명으로 바꾼다. */
    public static String describe(Exception e) {
        if (e instanceof ClassNotFoundException) {
            return "MySQL 드라이버(mysql-connector-j JAR)를 찾을 수 없습니다. Build Path를 확인하세요.";
        }
        if (e instanceof SQLException) {
            SQLException se = (SQLException) e;
            String state = se.getSQLState() == null ? "" : se.getSQLState();
            switch (se.getErrorCode()) {
                case 1045:
                    return "MySQL 아이디 또는 비밀번호가 틀렸습니다. (db/DataStorage.java의 ID, PW 확인)";
                case 1049:
                    return "roommate 데이터베이스가 없습니다. sql/schema.sql을 실행하세요.";
                case 1146:
                    return "필요한 테이블이 없습니다. sql/schema.sql을 실행하세요.";
                case 1054:
                    return "DB 구조가 최신이 아닙니다. (purchase 테이블에 settled_date 칸이 없음)\n"
                            + "MySQL에서 다음을 실행하세요: alter table roommate.purchase add column settled_date date NULL;";
                default:
                    if (state.startsWith("08")) {
                        return "MySQL 서버에 연결할 수 없습니다. MySQL이 실행 중인지 확인하세요.";
                    }
                    return "DB 오류: " + se.getMessage();
            }
        }
        return "알 수 없는 오류: " + e.getMessage();
    }

    private static boolean success() {
        if (listener != null) {
            listener.onSuccess();
        }
        return true;
    }

    private static boolean fail(String action, Exception e) {
        System.out.println(action + " 중 오류: " + e.getMessage());
        if (listener != null) {
            listener.onError(action, describe(e));
        }
        return false;
    }

    // ---------- 룸메이트 ----------

    /** @return 저장 성공 여부 */
    public static boolean saveMembers(List<String> members) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // 저장 도중 오류가 나면 기존 데이터를 유지
            stmt.executeUpdate("delete from member");
            for (String m : members) {
                stmt.executeUpdate("insert into member(name) values('" + escape(m) + "')");
            }
            conn.commit();
            return success();
        } catch (ClassNotFoundException | SQLException e) {
            return fail("룸메이트 저장", e);
        }
    }

    public static List<String> loadMembers() {
        List<String> members = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet result = stmt.executeQuery("select name from member order by id")) {
            while (result.next()) {
                members.add(result.getString("name"));
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("룸메이트 불러오기 중 오류: " + e.getMessage());
        }
        return members;
    }

    // ---------- 품목 ----------

    /** @return 저장 성공 여부 */
    public static boolean saveItems(List<Item> items) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // 저장 도중 오류가 나면 기존 데이터를 유지
            stmt.executeUpdate("delete from item");
            for (Item item : items) {
                stmt.executeUpdate("insert into item(name) values('" + escape(item.getName()) + "')");
            }
            conn.commit();
            return success();
        } catch (ClassNotFoundException | SQLException e) {
            return fail("품목 저장", e);
        }
    }

    public static List<Item> loadItems() {
        List<Item> items = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet result = stmt.executeQuery("select name from item order by id")) {
            while (result.next()) {
                items.add(new Item(result.getString("name")));
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("품목 불러오기 중 오류: " + e.getMessage());
        }
        return items;
    }

    // ---------- 구매 기록 ----------
    // 컬럼: 품목명(item_name), 구매자(buyer), 금액(cost), 날짜(purchase_date), 정산 완료일(settled_date, 미정산이면 null)

    /** @return 저장 성공 여부 */
    public static boolean savePurchases(List<Purchase> purchases) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // 저장 도중 오류가 나면 기존 데이터를 유지
            stmt.executeUpdate("delete from purchase");
            for (Purchase p : purchases) {
                String settled = p.isSettled() ? "'" + p.getSettledDate() + "'" : "null";
                stmt.executeUpdate("insert into purchase(item_name, buyer, cost, purchase_date, settled_date) values('"
                        + escape(p.getItemName()) + "','" + escape(p.getBuyer()) + "',"
                        + p.getCost() + ",'" + p.getDate() + "'," + settled + ")");
            }
            conn.commit();
            return success();
        } catch (ClassNotFoundException | SQLException e) {
            return fail("구매 기록 저장", e);
        }
    }

    public static List<Purchase> loadPurchases() {
        List<Purchase> purchases = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet result = stmt.executeQuery(
                     "select item_name, buyer, cost, purchase_date, settled_date from purchase order by id")) {
            while (result.next()) {
                String itemName = result.getString("item_name");
                String buyer = result.getString("buyer");
                int cost = result.getInt("cost");
                LocalDate date = LocalDate.parse(result.getString("purchase_date"));
                String settledText = result.getString("settled_date"); // 미정산이면 null
                LocalDate settledDate = (settledText == null) ? null : LocalDate.parse(settledText);
                purchases.add(new Purchase(itemName, buyer, cost, date, settledDate));
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("구매 기록 불러오기 중 오류: " + e.getMessage());
        }
        return purchases;
    }
}