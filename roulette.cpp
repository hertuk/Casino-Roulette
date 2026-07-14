// roulette.cpp - Симулятор европейской рулетки на C++17
#include <iostream>
#include <vector>
#include <set>
#include <random>
#include <string>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <chrono>
#include <thread>
#include <map>
#include <variant>

// Для JSON используем nlohmann/json (скачать json.hpp)
#include <nlohmann/json.hpp>
using json = nlohmann::json;

#ifdef _WIN32
    #include <windows.h>
#else
    #include <unistd.h>
#endif

#define RESET   "\033[0m"
#define RED     "\033[31m"
#define GREEN   "\033[32m"
#define YELLOW  "\033[33m"
#define BLUE    "\033[34m"
#define MAGENTA "\033[35m"
#define CYAN    "\033[36m"
#define BLACK   "\033[30m"

const std::set<int> RED_NUMBERS = {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36};
const std::set<int> BLACK_NUMBERS = {2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35};

struct Bet {
    std::string type;
    int amount;
    int value;
};

class Roulette {
private:
    int balance;
    std::vector<json> history;
    int totalSpins;
    int totalWins;
    std::vector<Bet> pendingBets;
    std::mt19937 rng;

public:
    Roulette(int initialBalance = 1000) : balance(initialBalance), totalSpins(0), totalWins(0), rng(std::random_device{}()) {
        loadState();
    }

    int spin() {
        std::uniform_int_distribution<int> dist(0, 36);
        return dist(rng);
    }

    std::string getColor(int number) {
        if (number == 0) return "зелёное";
        if (RED_NUMBERS.count(number)) return "красное";
        return "чёрное";
    }

    void placeBet(const std::string& type, int amount, int value) {
        if (amount > balance) throw std::runtime_error("Недостаточно средств");
        balance -= amount;
        pendingBets.push_back({type, amount, value});
    }

    int resolveBets(int number) {
        int totalWin = 0;
        std::string color = getColor(number);
        for (const auto& bet : pendingBets) {
            int win = calcWin(bet, number, color);
            totalWin += win;
            balance += win;
            json entry;
            entry["number"] = number;
            entry["color"] = color;
            entry["betType"] = bet.type;
            entry["betValue"] = bet.value;
            entry["amount"] = bet.amount;
            entry["win"] = win;
            history.push_back(entry);
        }
        pendingBets.clear();
        totalSpins++;
        if (totalWin > 0) totalWins++;
        return totalWin;
    }

    int calcWin(const Bet& bet, int number, const std::string& color) {
        if (bet.type == "STRAIGHT") {
            return bet.value == number ? bet.amount * 36 : 0;
        } else if (bet.type == "COLOR") {
            if ((bet.value == 0 && color == "красное") || (bet.value == 1 && color == "чёрное"))
                return bet.amount * 2;
            return 0;
        } else if (bet.type == "PARITY") {
            if ((bet.value == 0 && number % 2 == 0 && number != 0) ||
                (bet.value == 1 && number % 2 == 1))
                return bet.amount * 2;
            return 0;
        } else if (bet.type == "HIGHLOW") {
            if ((bet.value == 0 && number >= 1 && number <= 18) ||
                (bet.value == 1 && number >= 19 && number <= 36))
                return bet.amount * 2;
            return 0;
        } else if (bet.type == "DOZEN") {
            if ((bet.value == 1 && number >= 1 && number <= 12) ||
                (bet.value == 2 && number >= 13 && number <= 24) ||
                (bet.value == 3 && number >= 25 && number <= 36))
                return bet.amount * 3;
            return 0;
        } else if (bet.type == "COLUMN") {
            if (number == 0) return 0;
            int col = (number - 1) % 3 + 1;
            return bet.value == col ? bet.amount * 3 : 0;
        }
        return 0;
    }

    std::string getStats() const {
        if (totalSpins == 0) return "Нет спинов.";
        double winRate = (double)totalWins / totalSpins * 100;
        std::ostringstream oss;
        oss << "Всего спинов: " << totalSpins << ", Выигрышных: " << totalWins
            << " (" << std::fixed << std::setprecision(1) << winRate << "%), Баланс: " << balance;
        return oss.str();
    }

    void saveState(const std::string& filename = "roulette_state.json") {
        json data;
        data["balance"] = balance;
        data["history"] = history;
        data["totalSpins"] = totalSpins;
        data["totalWins"] = totalWins;
        std::ofstream out(filename);
        out << data.dump(2);
    }

    void loadState(const std::string& filename = "roulette_state.json") {
        std::ifstream in(filename);
        if (!in) return;
        json data;
        try {
            in >> data;
            balance = data.value("balance", 1000);
            history = data.value("history", json::array());
            totalSpins = data.value("totalSpins", 0);
            totalWins = data.value("totalWins", 0);
        } catch (...) {}
    }

    void displayHistory(int n = 10) const {
        if (history.empty()) {
            std::cout << "История пуста." << std::endl;
            return;
        }
        std::cout << CYAN << "Последние спины:" << RESET << std::endl;
        int start = std::max(0, (int)history.size() - n);
        for (int i = start; i < (int)history.size(); ++i) {
            const auto& entry = history[i];
            int number = entry["number"];
            std::string color = entry["color"];
            std::string colorCode = color == "красное" ? RED : (color == "чёрное" ? BLACK : GREEN);
            std::cout << number << " (" << colorCode << color << RESET << ") "
                      << "Ставка: " << entry["betType"].get<std::string>() << " " << entry["betValue"] << " "
                      << "Сумма: " << entry["amount"] << " -> Выигрыш: " << entry["win"] << std::endl;
        }
    }

    int getBalance() const { return balance; }
    bool hasPendingBets() const { return !pendingBets.empty(); }
    void clearPendingBets() { pendingBets.clear(); }
};

void clearScreen() {
#ifdef _WIN32
    system("cls");
#else
    system("clear");
#endif
}

void interactiveMenu() {
    Roulette roulette;
    std::cout << YELLOW << "🎰 Добро пожаловать в Европейскую рулетку!" << RESET << std::endl;
    std::string input;
    while (true) {
        std::cout << "\nБаланс: " << roulette.getBalance() << " фишек" << std::endl;
        std::cout << "\nДоступные ставки:\n";
        std::cout << "1. Прямая (номер)\n";
        std::cout << "2. Цвет (красное/чёрное)\n";
        std::cout << "3. Чёт/Нечет\n";
        std::cout << "4. Большое/Малое\n";
        std::cout << "5. Дюжина\n";
        std::cout << "6. Колонка\n";
        std::cout << "7. Спин\n";
        std::cout << "8. Статистика\n";
        std::cout << "9. История\n";
        std::cout << "10. Сохранить и выйти\n";
        std::cout << "Выберите действие: ";
        std::getline(std::cin, input);

        if (input == "10") {
            roulette.saveState();
            std::cout << "Состояние сохранено. До свидания!" << std::endl;
            break;
        } else if (input == "8") {
            std::cout << MAGENTA << roulette.getStats() << RESET << std::endl;
            continue;
        } else if (input == "9") {
            roulette.displayHistory();
            continue;
        } else if (input == "7") {
            if (!roulette.hasPendingBets()) {
                std::cout << RED << "Сначала сделайте ставку!" << RESET << std::endl;
                continue;
            }
            std::cout << "Крутим колесо..." << std::endl;
            std::this_thread::sleep_for(std::chrono::seconds(1));
            int number = roulette.spin();
            std::string color = roulette.getColor(number);
            std::string colorCode = color == "красное" ? RED : (color == "чёрное" ? BLACK : GREEN);
            std::cout << "Выпало: " << number << " (" << colorCode << color << RESET << ")" << std::endl;
            int win = roulette.resolveBets(number);
            if (win > 0) std::cout << GREEN << "Вы выиграли " << win << " фишек!" << RESET << std::endl;
            else std::cout << RED << "Вы проиграли ставку." << RESET << std::endl;
            continue;
        }

        // Ставки
        int betType = std::stoi(input);
        if (betType < 1 || betType > 6) {
            std::cout << RED << "Неверный выбор." << RESET << std::endl;
            continue;
        }
        std::cout << "Сумма ставки: ";
        std::getline(std::cin, input);
        int amount = std::stoi(input);
        if (amount <= 0 || amount > roulette.getBalance()) {
            std::cout << RED << "Некорректная сумма." << RESET << std::endl;
            continue;
        }
        int value = 0;
        bool ok = true;
        switch (betType) {
            case 1:
                std::cout << "Введите номер (0-36): ";
                std::getline(std::cin, input);
                value = std::stoi(input);
                if (value < 0 || value > 36) { std::cout << RED << "Номер от 0 до 36." << RESET << std::endl; ok = false; }
                else roulette.placeBet("STRAIGHT", amount, value);
                break;
            case 2:
                std::cout << "0 - красное, 1 - чёрное: ";
                std::getline(std::cin, input);
                value = std::stoi(input);
                if (value != 0 && value != 1) { std::cout << RED << "Введите 0 или 1." << RESET << std::endl; ok = false; }
                else roulette.placeBet("COLOR", amount, value);
                break;
            case 3:
                std::cout << "0 - чёт, 1 - нечет: ";
                std::getline(std::cin, input);
                value = std::stoi(input);
                if (value != 0 && value != 1) { std::cout << RED << "Введите 0 или 1." << RESET << std::endl; ok = false; }
                else roulette.placeBet("PARITY", amount, value);
                break;
            case 4:
                std::cout << "0 - малое (1-18), 1 - большое (19-36): ";
                std::getline(std::cin, input);
                value = std::stoi(input);
                if (value != 0 && value != 1) { std::cout << RED << "Введите 0 или 1." << RESET << std::endl; ok = false; }
                else roulette.placeBet("HIGHLOW", amount, value);
                break;
            case 5:
                std::cout << "Дюжина (1,2,3): ";
                std::getline(std::cin, input);
                value = std::stoi(input);
                if (value < 1 || value > 3) { std::cout << RED << "Введите 1,2 или 3." << RESET << std::endl; ok = false; }
                else roulette.placeBet("DOZEN", amount, value);
                break;
            case 6:
                std::cout << "Колонка (1,2,3): ";
                std::getline(std::cin, input);
                value = std::stoi(input);
                if (value < 1 || value > 3) { std::cout << RED << "Введите 1,2 или 3." << RESET << std::endl; ok = false; }
                else roulette.placeBet("COLUMN", amount, value);
                break;
        }
        if (ok) std::cout << GREEN << "Ставка принята." << RESET << std::endl;
    }
}

int main(int argc, char* argv[]) {
    if (argc > 1 && std::string(argv[1]) == "--auto") {
        Roulette roulette;
        std::cout << "Автоигра: ставка на красное по 10 фишек, 20 спинов" << std::endl;
        for (int i = 0; i < 20; ++i) {
            if (roulette.getBalance() < 10) break;
            roulette.placeBet("COLOR", 10, 0);
            int number = roulette.spin();
            int win = roulette.resolveBets(number);
            std::cout << "Спин " << i+1 << ": " << number << " " << roulette.getColor(number)
                      << ", выигрыш: " << win << ", баланс: " << roulette.getBalance() << std::endl;
            std::this_thread::sleep_for(std::chrono::milliseconds(500));
        }
        roulette.saveState();
        std::cout << "Автоигра завершена." << std::endl;
        return 0;
    }
    interactiveMenu();
    return 0;
}
