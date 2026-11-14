package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 룸메이트 / 품목 / 구매기록을 MySQL 데이터베이스에 저장하고 불러오는 클래스.
 * 프로그램을 종료했다가 다시 실행해도 이전 데이터가 유지되도록 한다.
 */
public class DataStorage {

    private static final String URL = "jdbc:mysql://localhost:3306/roommate?serverTimezone=UTC";
    private static final String ID = "root";   // 자신의 MySQL 아이디로 변경
    private static final String PW = "1234";   // 자신의 MySQL 비밀번호로 변경

    /** JDBC 드라이버를 로드하고 데이터베이스와 연결한다. */
    private static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(URL, ID, PW);
    }

    /** SQL 문자열 안에 넣을 값의 작은따옴표와 역슬래시를 이스케이프한다. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    // ---------- 룸메이트 ----------

    public static void saveMembers(List<String> members) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // 저장 도중 오류가 나면 기존 데이터를 유지
            stmt.executeUpdate("delete from member");
            for (String m : members) {
                stmt.executeUpdate("insert into member(name) values('" + escape(m) + "')");
            }
            conn.commit();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("룸메이트 저장 중 오류: " + e.getMessage());
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

    public static void saveItems(List<Item> items) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // 저장 도중 오류가 나면 기존 데이터를 유지
            stmt.executeUpdate("delete from item");
            for (Item item : items) {
                stmt.executeUpdate("insert into item(name) values('" + escape(item.getName()) + "')");
            }
            conn.commit();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("품목 저장 중 오류: " + e.getMessage());
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
    // 컬럼: 품목명(item_name), 구매자(buyer), 금액(cost), 날짜(purchase_date)

    public static void savePurchases(List<Purchase> purchases) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            conn.setAutoCommit(false); // 저장 도중 오류가 나면 기존 데이터를 유지
            stmt.executeUpdate("delete from purchase");
            for (Purchase p : purchases) {
                stmt.executeUpdate("insert into purchase(item_name, buyer, cost, purchase_date) values('"
                        + escape(p.getItemName()) + "','" + escape(p.getBuyer()) + "',"
                        + p.getCost() + ",'" + p.getDate() + "')");
            }
            conn.commit();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("구매 기록 저장 중 오류: " + e.getMessage());
        }
    }

    public static List<Purchase> loadPurchases() {
        List<Purchase> purchases = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet result = stmt.executeQuery(
                     "select item_name, buyer, cost, purchase_date from purchase order by id")) {
            while (result.next()) {
                String itemName = result.getString("item_name");
                String buyer = result.getString("buyer");
                int cost = result.getInt("cost");
                LocalDate date = LocalDate.parse(result.getString("purchase_date"));
                purchases.add(new Purchase(itemName, buyer, cost, date));
            }
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println("구매 기록 불러오기 중 오류: " + e.getMessage());
        }
        return purchases;
    }
}