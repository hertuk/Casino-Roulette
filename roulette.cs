// roulette.cs - Симулятор европейской рулетки на C#
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.Json;
using System.Threading;

class Roulette
{
    private static readonly int[] Numbers = Enumerable.Range(0, 37).ToArray();
    private static readonly HashSet<int> RedNumbers = new HashSet<int> {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36};
    private static readonly HashSet<int> BlackNumbers = new HashSet<int> {2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35};

    private int balance;
    private List<Dictionary<string, object>> history;
    private int totalSpins;
    private int totalWins;
    private List<Bet> pendingBets;
    private Random rand;

    private class Bet
    {
        public string Type { get; set; }
        public int Amount { get; set; }
        public int Value { get; set; }
    }

    public Roulette(int initialBalance = 1000)
    {
        balance = initialBalance;
        history = new List<Dictionary<string, object>>();
        totalSpins = 0;
        totalWins = 0;
        pendingBets = new List<Bet>();
        rand = new Random();
        LoadState();
    }

    public int Spin() => Numbers[rand.Next(Numbers.Length)];

    public string GetColor(int number)
    {
        if (number == 0) return "зелёное";
        if (RedNumbers.Contains(number)) return "красное";
        return "чёрное";
    }

    public void PlaceBet(string type, int amount, int value)
    {
        if (amount > balance) throw new Exception("Недостаточно средств");
        balance -= amount;
        pendingBets.Add(new Bet { Type = type, Amount = amount, Value = value });
    }

    public int ResolveBets(int number)
    {
        int totalWin = 0;
        string color = GetColor(number);
        foreach (var bet in pendingBets)
        {
            int win = CalcWin(bet, number, color);
            totalWin += win;
            balance += win;
            var entry = new Dictionary<string, object>
            {
                ["number"] = number,
                ["color"] = color,
                ["betType"] = bet.Type,
                ["betValue"] = bet.Value,
                ["amount"] = bet.Amount,
                ["win"] = win
            };
            history.Add(entry);
        }
        pendingBets.Clear();
        totalSpins++;
        if (totalWin > 0) totalWins++;
        return totalWin;
    }

    private int CalcWin(Bet bet, int number, string color)
    {
        switch (bet.Type)
        {
            case "STRAIGHT": return bet.Value == number ? bet.Amount * 36 : 0;
            case "COLOR":
                if ((bet.Value == 0 && color == "красное") || (bet.Value == 1 && color == "чёрное"))
                    return bet.Amount * 2;
                return 0;
            case "PARITY":
                if ((bet.Value == 0 && number % 2 == 0 && number != 0) ||
                    (bet.Value == 1 && number % 2 == 1))
                    return bet.Amount * 2;
                return 0;
            case "HIGHLOW":
                if ((bet.Value == 0 && number >= 1 && number <= 18) ||
                    (bet.Value == 1 && number >= 19 && number <= 36))
                    return bet.Amount * 2;
                return 0;
            case "DOZEN":
                if ((bet.Value == 1 && number >= 1 && number <= 12) ||
                    (bet.Value == 2 && number >= 13 && number <= 24) ||
                    (bet.Value == 3 && number >= 25 && number <= 36))
                    return bet.Amount * 3;
                return 0;
            case "COLUMN":
                if (number == 0) return 0;
                int col = (number - 1) % 3 + 1;
                return bet.Value == col ? bet.Amount * 3 : 0;
            default: return 0;
        }
    }

    public string GetStats()
    {
        if (totalSpins == 0) return "Нет спинов.";
        double winRate = (double)totalWins / totalSpins * 100;
        return $"Всего спинов: {totalSpins}, Выигрышных: {totalWins} ({winRate:F1}%), Баланс: {balance}";
    }

    public void SaveState(string filename = "roulette_state.json")
    {
        var data = new Dictionary<string, object>
        {
            ["balance"] = balance,
            ["history"] = history,
            ["totalSpins"] = totalSpins,
            ["totalWins"] = totalWins
        };
        string json = JsonSerializer.Serialize(data, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(filename, json);
    }

    public void LoadState(string filename = "roulette_state.json")
    {
        if (!File.Exists(filename)) return;
        string json = File.ReadAllText(filename);
        try
        {
            var data = JsonSerializer.Deserialize<Dictionary<string, object>>(json);
            if (data != null)
            {
                balance = Convert.ToInt32(data["balance"]);
                totalSpins = Convert.ToInt32(data["totalSpins"]);
                totalWins = Convert.ToInt32(data["totalWins"]);
                // history загружать сложнее из-за вложенности, для простоты пропустим
                // В реальном проекте лучше использовать конкретные классы
            }
        }
        catch { }
    }

    public void DisplayHistory(int n = 10)
    {
        if (history.Count == 0)
        {
            Console.WriteLine("История пуста.");
            return;
        }
        Console.WriteLine("\x1b[36mПоследние спины:\x1b[0m");
        int start = Math.Max(0, history.Count - n);
        for (int i = start; i < history.Count; i++)
        {
            var entry = history[i];
            string color = (string)entry["color"];
            string colorCode = color == "красное" ? "\x1b[31m" :
                               color == "чёрное" ? "\x1b[30m" : "\x1b[32m";
            Console.WriteLine($"{entry["number"]} ({colorCode}{color}\x1b[0m) Ставка: {entry["betType"]} {entry["betValue"]} Сумма: {entry["amount"]} -> Выигрыш: {entry["win"]}");
        }
    }

    public int Balance => balance;
    public bool HasPendingBets => pendingBets.Count > 0;
}

class Program
{
    static void Main(string[] args)
    {
        if (args.Length > 0 && args[0] == "--auto")
        {
            var roulette = new Roulette();
            Console.WriteLine("Автоигра: ставка на красное по 10 фишек, 20 спинов");
            for (int i = 0; i < 20; i++)
            {
                if (roulette.Balance < 10) break;
                roulette.PlaceBet("COLOR", 10, 0);
                int number = roulette.Spin();
                int win = roulette.ResolveBets(number);
                Console.WriteLine($"Спин {i+1}: {number} {roulette.GetColor(number)}, выигрыш: {win}, баланс: {roulette.Balance}");
                Thread.Sleep(500);
            }
            roulette.SaveState();
            Console.WriteLine("Автоигра завершена.");
            return;
        }

        // Интерактивный режим
        var r = new Roulette();
        Console.WriteLine("\x1b[33m🎰 Добро пожаловать в Европейскую рулетку!\x1b[0m");
        while (true)
        {
            Console.WriteLine($"\nБаланс: {r.Balance} фишек");
            Console.WriteLine("\nДоступные ставки:");
            Console.WriteLine("1. Прямая (номер)");
            Console.WriteLine("2. Цвет (красное/чёрное)");
            Console.WriteLine("3. Чёт/Нечет");
            Console.WriteLine("4. Большое/Малое");
            Console.WriteLine("5. Дюжина");
            Console.WriteLine("6. Колонка");
            Console.WriteLine("7. Спин");
            Console.WriteLine("8. Статистика");
            Console.WriteLine("9. История");
            Console.WriteLine("10. Сохранить и выйти");
            Console.Write("Выберите действие: ");
            string choice = Console.ReadLine().Trim();

            if (choice == "10")
            {
                r.SaveState();
                Console.WriteLine("Состояние сохранено. До свидания!");
                break;
            }
            else if (choice == "8")
            {
                Console.WriteLine("\x1b[35m" + r.GetStats() + "\x1b[0m");
                continue;
            }
            else if (choice == "9")
            {
                r.DisplayHistory();
                continue;
            }
            else if (choice == "7")
            {
                if (!r.HasPendingBets)
                {
                    Console.WriteLine("\x1b[31mСначала сделайте ставку!\x1b[0m");
                    continue;
                }
                Console.WriteLine("Крутим колесо...");
                Thread.Sleep(1000);
                int number = r.Spin();
                string color = r.GetColor(number);
                string colorCode = color == "красное" ? "\x1b[31m" :
                                   color == "чёрное" ? "\x1b[30m" : "\x1b[32m";
                Console.WriteLine($"Выпало: {number} ({colorCode}{color}\x1b[0m)");
                int win = r.ResolveBets(number);
                if (win > 0) Console.WriteLine($"\x1b[32mВы выиграли {win} фишек!\x1b[0m");
                else Console.WriteLine("\x1b[31mВы проиграли ставку.\x1b[0m");
                continue;
            }

            // Ставки
            if (!int.TryParse(choice, out int betType) || betType < 1 || betType > 6)
            {
                Console.WriteLine("\x1b[31mНеверный выбор.\x1b[0m");
                continue;
            }
            Console.Write("Сумма ставки: ");
            string amountStr = Console.ReadLine().Trim();
            if (!int.TryParse(amountStr, out int amount) || amount <= 0 || amount > r.Balance)
            {
                Console.WriteLine("\x1b[31mНекорректная сумма.\x1b[0m");
                continue;
            }
            int value = 0;
            bool ok = true;
            switch (betType)
            {
                case 1:
                    Console.Write("Введите номер (0-36): ");
                    if (!int.TryParse(Console.ReadLine().Trim(), out value) || value < 0 || value > 36)
                    { Console.WriteLine("\x1b[31mНомер от 0 до 36.\x1b[0m"); ok = false; }
                    else r.PlaceBet("STRAIGHT", amount, value);
                    break;
                case 2:
                    Console.Write("0 - красное, 1 - чёрное: ");
                    if (!int.TryParse(Console.ReadLine().Trim(), out value) || (value != 0 && value != 1))
                    { Console.WriteLine("\x1b[31mВведите 0 или 1.\x1b[0m"); ok = false; }
                    else r.PlaceBet("COLOR", amount, value);
                    break;
                case 3:
                    Console.Write("0 - чёт, 1 - нечет: ");
                    if (!int.TryParse(Console.ReadLine().Trim(), out value) || (value != 0 && value != 1))
                    { Console.WriteLine("\x1b[31mВведите 0 или 1.\x1b[0m"); ok = false; }
                    else r.PlaceBet("PARITY", amount, value);
                    break;
                case 4:
                    Console.Write("0 - малое (1-18), 1 - большое (19-36): ");
                    if (!int.TryParse(Console.ReadLine().Trim(), out value) || (value != 0 && value != 1))
                    { Console.WriteLine("\x1b[31mВведите 0 или 1.\x1b[0m"); ok = false; }
                    else r.PlaceBet("HIGHLOW", amount, value);
                    break;
                case 5:
                    Console.Write("Дюжина (1,2,3): ");
                    if (!int.TryParse(Console.ReadLine().Trim(), out value) || value < 1 || value > 3)
                    { Console.WriteLine("\x1b[31mВведите 1,2 или 3.\x1b[0m"); ok = false; }
                    else r.PlaceBet("DOZEN", amount, value);
                    break;
                case 6:
                    Console.Write("Колонка (1,2,3): ");
                    if (!int.TryParse(Console.ReadLine().Trim(), out value) || value < 1 || value > 3)
                    { Console.WriteLine("\x1b[31mВведите 1,2 или 3.\x1b[0m"); ok = false; }
                    else r.PlaceBet("COLUMN", amount, value);
                    break;
            }
            if (ok) Console.WriteLine("\x1b[32mСтавка принята.\x1b[0m");
        }
    }
}
