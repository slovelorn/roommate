package app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 룸메이트 / 품목 / 구매기록을 로컬 텍스트 파일로 저장하고 불러오는 클래스.
 * 프로그램을 종료했다가 다시 실행해도 이전 데이터가 유지되도록 한다.
 */
public class DataStorage {

    private static final String MEMBER_FILE = "members.txt";
    private static final String ITEM_FILE = "items.txt";
    private static final String PURCHASE_FILE = "purchases.txt";

    // ---------- 룸메이트 ----------

    public static void saveMembers(List<String> members) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(MEMBER_FILE))) {
            for (String m : members) {
                pw.println(m);
            }
        } catch (IOException e) {
            System.out.println("룸메이트 저장 중 오류: " + e.getMessage());
        }
    }

    public static List<String> loadMembers() {
        List<String> members = new ArrayList<>();
        File file = new File(MEMBER_FILE);
        if (!file.exists()) {
            return members;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    members.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.out.println("룸메이트 불러오기 중 오류: " + e.getMessage());
        }
        return members;
    }

    // ---------- 품목 ----------

    public static void saveItems(List<Item> items) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(ITEM_FILE))) {
            for (Item item : items) {
                pw.println(item.getName());
            }
        } catch (IOException e) {
            System.out.println("품목 저장 중 오류: " + e.getMessage());
        }
    }

    public static List<Item> loadItems() {
        List<Item> items = new ArrayList<>();
        File file = new File(ITEM_FILE);
        if (!file.exists()) {
            return items;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) {
                    items.add(new Item(line.trim()));
                }
            }
        } catch (IOException e) {
            System.out.println("품목 불러오기 중 오류: " + e.getMessage());
        }
        return items;
    }

    // ---------- 구매 기록 ----------
    // 형식: 품목명,구매자,금액,날짜(yyyy-MM-dd)

    public static void savePurchases(List<Purchase> purchases) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(PURCHASE_FILE))) {
            for (Purchase p : purchases) {
                pw.println(p.getItemName() + "," + p.getBuyer() + "," + p.getCost() + "," + p.getDate());
            }
        } catch (IOException e) {
            System.out.println("구매 기록 저장 중 오류: " + e.getMessage());
        }
    }

    public static List<Purchase> loadPurchases() {
        List<Purchase> purchases = new ArrayList<>();
        File file = new File(PURCHASE_FILE);
        if (!file.exists()) {
            return purchases;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] tokens = line.split(",");
                if (tokens.length < 4) {
                    continue; // 형식이 올바르지 않은 줄은 건너뜀
                }
                String itemName = tokens[0];
                String buyer = tokens[1];
                int cost = Integer.parseInt(tokens[2]);
                LocalDate date = LocalDate.parse(tokens[3]);
                purchases.add(new Purchase(itemName, buyer, cost, date));
            }
        } catch (IOException e) {
            System.out.println("구매 기록 불러오기 중 오류: " + e.getMessage());
        }
        return purchases;
    }
}