// Roulette.java - Симулятор европейской рулетки на Java
import java.util.*;
import java.io.*;
import java.nio.file.*;

public class Roulette {
    private static final int[] NUMBERS = new int[37];
    static {
        for (int i = 0; i < 37; i++) NUMBERS[i] = i;
    }
    private static final Set<Integer> RED_NUMBERS = new HashSet<>(Arrays.asList(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36));
    private static final Set<Integer> BLACK_NUMBERS = new HashSet<>(Arrays.asList(2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35));

    private int balance;
    private List<Map<String, Object>> history;
    private int totalSpins;
    private int totalWins;
    private List<Bet> pendingBets;
    private Random rand;

    private static class Bet {
        String type;
        int amount;
        int value;
        Bet(String t, int a, int v) { type = t; amount = a; value = v; }
    }

    public Roulette(int initialBalance) {
        this.balance = initialBalance;
        this.history = new ArrayList<>();
        this.totalSpins = 0;
        this.totalWins = 0;
        this.pendingBets = new ArrayList<>();
        this.rand = new Random();
        loadState();
    }

    public int spin() {
        return NUMBERS[rand.nextInt(NUMBERS.length)];
    }

    public String getColor(int number) {
        if (number == 0) return "зелёное";
        if (RED_NUMBERS.contains(number)) return "красное";
        return "чёрное";
    }

    public void placeBet(String type, int amount, int value) throws Exception {
        if (amount > balance) throw new Exception("Недостаточно средств");
        balance -= amount;
        pendingBets.add(new Bet(type, amount, value));
    }

    public int resolveBets(int number) {
        int totalWin = 0;
        String color = getColor(number);
        for (Bet bet : pendingBets) {
            int win = calcWin(bet, number, color);
            totalWin += win;
            balance += win;
            Map<String, Object> entry = new HashMap<>();
            entry.put("number", number);
            entry.put("color", color);
            entry.put("betType", bet.type);
            entry.put("betValue", bet.value);
            entry.put("amount", bet.amount);
            entry.put("win", win);
            history.add(entry);
        }
        pendingBets.clear();
        totalSpins++;
        if (totalWin > 0) totalWins++;
        return totalWin;
    }

    private int calcWin(Bet bet, int number, String color) {
        switch (bet.type) {
            case "STRAIGHT":
                return bet.value == number ? bet.amount * 36 : 0;
            case "COLOR":
                if ((bet.value == 0 && color.equals("красное")) || (bet.value == 1 && color.equals("чёрное")))
                    return bet.amount * 2;
                return 0;
            case "PARITY":
                if ((bet.value == 0 && number % 2 == 0 && number != 0) ||
                    (bet.value == 1 && number % 2 == 1))
                    return bet.amount * 2;
                return 0;
            case "HIGHLOW":
                if ((bet.value == 0 && number >= 1 && number <= 18) ||
                    (bet.value == 1 && number >= 19 && number <= 36))
                    return bet.amount * 2;
                return 0;
            case "DOZEN":
                if ((bet.value == 1 && number >= 1 && number <= 12) ||
                    (bet.value == 2 && number >= 13 && number <= 24) ||
                    (bet.value == 3 && number >= 25 && number <= 36))
                    return bet.amount * 3;
                return 0;
            case "COLUMN":
                if (number == 0) return 0;
                int col = (number - 1) % 3 + 1;
                return bet.value == col ? bet.amount * 3 : 0;
            default:
                return 0;
        }
    }

    public String getStats() {
        if (totalSpins == 0) return "Нет спинов.";
        double winRate = (double) totalWins / totalSpins * 100;
        return String.format("Всего спинов: %d, Выигрышных: %d (%.1f%%), Баланс: %d",
                totalSpins, totalWins, winRate, balance);
    }

    public void saveState(String filename) throws IOException {
        Map<String, Object> data = new HashMap<>();
        data.put("balance", balance);
        data.put("history", history);
        data.put("totalSpins", totalSpins);
        data.put("totalWins", totalWins);
        // Простой JSON вручную
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"balance\":").append(balance).append(",");
        sb.append("\"totalSpins\":").append(totalSpins).append(",");
        sb.append("\"totalWins\":").append(totalWins).append(",");
        sb.append("\"history\":").append(historyToString()).append("}");
        Files.write(Paths.get(filename), sb.toString().getBytes());
    }

    private String historyToString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < history.size(); i++) {
            Map<String, Object> entry = history.get(i);
            sb.append("{");
            sb.append("\"number\":").append(entry.get("number")).append(",");
            sb.append("\"color\":\"").append(entry.get("color")).append("\",");
            sb.append("\"betType\":\"").append(entry.get("betType")).append("\",");
            sb.append("\"betValue\":").append(entry.get("betValue")).append(",");
            sb.append("\"amount\":").append(entry.get("amount")).append(",");
            sb.append("\"win\":").append(entry.get("win"));
            sb.append("}");
            if (i < history.size()-1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public void loadState(String filename) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filename)));
            // Простой парсинг (не для продакшена)
            // В реальности лучше использовать библиотеку, но для демо упростим
            // Здесь пропустим загрузку истории, только баланс и счётчики
            // Для полной загрузки нужен полноценный JSON-парсер
            // Ограничимся базовыми полями
            if (content.contains("\"balance\":")) {
                String[] parts = content.split(",");
                for (String p : parts) {
                    if (p.contains("\"balance\":")) {
                        balance = Integer.parseInt(p.split(":")[1].trim());
                    } else if (p.contains("\"totalSpins\":")) {
                        totalSpins = Integer.parseInt(p.split(":")[1].trim());
                    } else if (p.contains("\"totalWins\":")) {
                        totalWins = Integer.parseInt(p.split(":")[1].trim());
                    }
                }
            }
        } catch (IOException e) {}
    }

    public void displayHistory(int n) {
        if (history.isEmpty()) {
            System.out.println("История пуста.");
            return;
        }
        System.out.println("\u001B[36mПоследние спины:\u001B[0m");
        int start = Math.max(0, history.size() - n);
        for (int i = start; i < history.size(); i++) {
            Map<String, Object> entry = history.get(i);
            String color = (String) entry.get("color");
            String colorCode = color.equals("красное") ? "\u001B[31m" :
                               color.equals("чёрное") ? "\u001B[30m" : "\u001B[32m";
            System.out.printf("%d (%s%s\u001B[0m) Ставка: %s %d Сумма: %d -> Выигрыш: %d%n",
                    entry.get("number"), colorCode, color,
                    entry.get("betType"), entry.get("betValue"),
                    entry.get("amount"), entry.get("win"));
        }
    }

    // Основной метод игры (интерактивный)
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        Roulette roulette = new Roulette(1000);

        if (args.length > 0 && args[0].equals("--auto")) {
            // Автоигра
            System.out.println("Автоигра: ставка на красное по 10 фишек, 20 спинов");
            for (int i = 0; i < 20; i++) {
                if (roulette.balance < 10) break;
                roulette.placeBet("COLOR", 10, 0);
                int number = roulette.spin();
                int win = roulette.resolveBets(number);
                System.out.printf("Спин %d: %d %s, выигрыш: %d, баланс: %d%n",
                        i+1, number, roulette.getColor(number), win, roulette.balance);
                Thread.sleep(500);
            }
            roulette.saveState("roulette_state.json");
            System.out.println("Автоигра завершена.");
            return;
        }

        System.out.println("\u001B[33m🎰 Добро пожаловать в Европейскую рулетку!\u001B[0m");
        while (true) {
            System.out.printf("\nБаланс: %d фишек%n", roulette.balance);
            System.out.println("\nДоступные ставки:");
            System.out.println("1. Прямая (номер)");
            System.out.println("2. Цвет (красное/чёрное)");
            System.out.println("3. Чёт/Нечет");
            System.out.println("4. Большое/Малое");
            System.out.println("5. Дюжина");
            System.out.println("6. Колонка");
            System.out.println("7. Спин");
            System.out.println("8. Статистика");
            System.out.println("9. История");
            System.out.println("10. Сохранить и выйти");
            System.out.print("Выберите действие: ");
            String choice = scanner.nextLine().trim();

            if (choice.equals("10")) {
                roulette.saveState("roulette_state.json");
                System.out.println("Состояние сохранено. До свидания!");
                scanner.close();
                return;
            } else if (choice.equals("8")) {
                System.out.println("\u001B[35m" + roulette.getStats() + "\u001B[0m");
                continue;
            } else if (choice.equals("9")) {
                roulette.displayHistory(10);
                continue;
            } else if (choice.equals("7")) {
                if (roulette.pendingBets.isEmpty()) {
                    System.out.println("\u001B[31mСначала сделайте ставку!\u001B[0m");
                    continue;
                }
                System.out.println("Крутим колесо...");
                Thread.sleep(1000);
                int number = roulette.spin();
                String color = roulette.getColor(number);
                String colorCode = color.equals("красное") ? "\u001B[31m" :
                                   color.equals("чёрное") ? "\u001B[30m" : "\u001B[32m";
                System.out.printf("Выпало: %d (%s%s\u001B[0m)%n", number, colorCode, color);
                int win = roulette.resolveBets(number);
                if (win > 0) System.out.printf("\u001B[32mВы выиграли %d фишек!\u001B[0m%n", win);
                else System.out.println("\u001B[31mВы проиграли ставку.\u001B[0m");
                continue;
            }

            // Ставки
            int betType;
            try {
                betType = Integer.parseInt(choice);
                if (betType < 1 || betType > 6) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                System.out.println("\u001B[31mНеверный выбор.\u001B[0m");
                continue;
            }
            System.out.print("Сумма ставки: ");
            int amount = Integer.parseInt(scanner.nextLine().trim());
            if (amount <= 0 || amount > roulette.balance) {
                System.out.println("\u001B[31mНекорректная сумма.\u001B[0m");
                continue;
            }
            int value = 0;
            switch (betType) {
                case 1:
                    System.out.print("Введите номер (0-36): ");
                    value = Integer.parseInt(scanner.nextLine().trim());
                    if (value < 0 || value > 36) {
                        System.out.println("\u001B[31mНомер от 0 до 36.\u001B[0m");
                        continue;
                    }
                    roulette.placeBet("STRAIGHT", amount, value);
                    break;
                case 2:
                    System.out.print("0 - красное, 1 - чёрное: ");
                    value = Integer.parseInt(scanner.nextLine().trim());
                    if (value != 0 && value != 1) {
                        System.out.println("\u001B[31mВведите 0 или 1.\u001B[0m");
                        continue;
                    }
                    roulette.placeBet("COLOR", amount, value);
                    break;
                case 3:
                    System.out.print("0 - чёт, 1 - нечет: ");
                    value = Integer.parseInt(scanner.nextLine().trim());
                    if (value != 0 && value != 1) {
                        System.out.println("\u001B[31mВведите 0 или 1.\u001B[0m");
                        continue;
                    }
                    roulette.placeBet("PARITY", amount, value);
                    break;
                case 4:
                    System.out.print("0 - малое (1-18), 1 - большое (19-36): ");
                    value = Integer.parseInt(scanner.nextLine().trim());
                    if (value != 0 && value != 1) {
                        System.out.println("\u001B[31mВведите 0 или 1.\u001B[0m");
                        continue;
                    }
                    roulette.placeBet("HIGHLOW", amount, value);
                    break;
                case 5:
                    System.out.print("Дюжина (1,2,3): ");
                    value = Integer.parseInt(scanner.nextLine().trim());
                    if (value < 1 || value > 3) {
                        System.out.println("\u001B[31mВведите 1,2 или 3.\u001B[0m");
                        continue;
                    }
                    roulette.placeBet("DOZEN", amount, value);
                    break;
                case 6:
                    System.out.print("Колонка (1,2,3): ");
                    value = Integer.parseInt(scanner.nextLine().trim());
                    if (value < 1 || value > 3) {
                        System.out.println("\u001B[31mВведите 1,2 или 3.\u001B[0m");
                        continue;
                    }
                    roulette.placeBet("COLUMN", amount, value);
                    break;
            }
            System.out.println("\u001B[32mСтавка принята.\u001B[0m");
        }
    }
}
