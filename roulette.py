#!/usr/bin/env python3
# roulette.py - Симулятор европейской рулетки на Python

import random
import json
import os
import time
from typing import List, Dict, Tuple, Optional
from dataclasses import dataclass, asdict
from enum import Enum

try:
    from colorama import init, Fore, Style
    init(autoreset=True)
    HAS_COLOR = True
except ImportError:
    HAS_COLOR = False
    class Fore:
        RED = GREEN = YELLOW = CYAN = MAGENTA = WHITE = RESET = ''
    Style = Fore

# Константы
NUMBERS = list(range(37))
RED_NUMBERS = {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}
BLACK_NUMBERS = {2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35}
GREEN_NUMBER = 0

class BetType(Enum):
    STRAIGHT = "Прямая"
    COLOR = "Цвет"
    PARITY = "Чёт/Нечет"
    HIGH_LOW = "Большое/Малое"
    DOZEN = "Дюжина"
    COLUMN = "Колонка"

@dataclass
class Bet:
    type: BetType
    amount: int
    value: any  # номер, цвет (0/1), чётность, диапазон, дюжина (1-3), колонка (1-3)

class Roulette:
    def __init__(self, initial_balance=1000):
        self.balance = initial_balance
        self.history: List[Dict] = []  # каждый спин: {number, color, bets, win}
        self.total_spins = 0
        self.total_wins = 0
        self.load_state()

    def spin(self) -> int:
        """Возвращает выпавшее число."""
        return random.choice(NUMBERS)

    def get_color(self, number: int) -> str:
        if number == 0:
            return "зелёное"
        elif number in RED_NUMBERS:
            return "красное"
        else:
            return "чёрное"

    def place_bet(self, bet: Bet) -> None:
        """Ставка принимается, списывается с баланса."""
        if bet.amount > self.balance:
            raise ValueError("Недостаточно средств")
        self.balance -= bet.amount
        # сохраняем ставку во временное хранилище (будет обработана при спине)
        if not hasattr(self, '_pending_bets'):
            self._pending_bets = []
        self._pending_bets.append(bet)

    def resolve_bets(self, number: int) -> int:
        """Рассчитывает выигрыш по всем ожидающим ставкам и возвращает общий выигрыш."""
        total_win = 0
        color = self.get_color(number)
        for bet in self._pending_bets:
            win = self._calc_win(bet, number, color)
            total_win += win
            self.balance += win
            # запись в историю
            self.history.append({
                'number': number,
                'color': color,
                'bet_type': bet.type.value,
                'bet_value': bet.value,
                'amount': bet.amount,
                'win': win
            })
        self._pending_bets.clear()
        self.total_spins += 1
        if total_win > 0:
            self.total_wins += 1
        return total_win

    def _calc_win(self, bet: Bet, number: int, color: str) -> int:
        if bet.type == BetType.STRAIGHT:
            if bet.value == number:
                return bet.amount * 36  # 35:1 + возврат ставки
        elif bet.type == BetType.COLOR:
            if (bet.value == 0 and color == "красное") or (bet.value == 1 and color == "чёрное"):
                return bet.amount * 2  # 1:1 + возврат
            # зелёное проигрывает
        elif bet.type == BetType.PARITY:
            if (bet.value == 0 and number % 2 == 0 and number != 0) or \
               (bet.value == 1 and number % 2 == 1):
                return bet.amount * 2
        elif bet.type == BetType.HIGH_LOW:
            if (bet.value == 0 and 1 <= number <= 18) or \
               (bet.value == 1 and 19 <= number <= 36):
                return bet.amount * 2
        elif bet.type == BetType.DOZEN:
            if (bet.value == 1 and 1 <= number <= 12) or \
               (bet.value == 2 and 13 <= number <= 24) or \
               (bet.value == 3 and 25 <= number <= 36):
                return bet.amount * 3  # 2:1 + возврат
        elif bet.type == BetType.COLUMN:
            col = (number - 1) % 3 + 1 if number != 0 else 0
            if bet.value == col:
                return bet.amount * 3
        return 0

    def get_stats(self) -> str:
        if self.total_spins == 0:
            return "Нет спинов."
        win_rate = self.total_wins / self.total_spins * 100
        return (f"Всего спинов: {self.total_spins}, Выигрышных: {self.total_wins} "
                f"({win_rate:.1f}%), Баланс: {self.balance}")

    def save_state(self, filename="roulette_state.json"):
        data = {
            'balance': self.balance,
            'history': self.history,
            'total_spins': self.total_spins,
            'total_wins': self.total_wins
        }
        with open(filename, 'w') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)

    def load_state(self, filename="roulette_state.json"):
        if os.path.exists(filename):
            try:
                with open(filename, 'r') as f:
                    data = json.load(f)
                self.balance = data.get('balance', 1000)
                self.history = data.get('history', [])
                self.total_spins = data.get('total_spins', 0)
                self.total_wins = data.get('total_wins', 0)
            except:
                pass

    def display_history(self, n=10):
        if not self.history:
            print("История пуста.")
            return
        print(Fore.CYAN + "Последние спины:")
        for entry in self.history[-n:]:
            color = Fore.RED if entry['color'] == 'красное' else Fore.BLACK if entry['color'] == 'чёрное' else Fore.GREEN
            print(f"{entry['number']} ({color}{entry['color']}{Fore.RESET}) "
                  f"Ставка: {entry['bet_type']} {entry['bet_value']} "
                  f"Сумма: {entry['amount']} -> Выигрыш: {entry['win']}")

def interactive_menu():
    roulette = Roulette()
    print(Fore.YELLOW + "🎰 Добро пожаловать в Европейскую рулетку!")
    while True:
        print(f"\nБаланс: {roulette.balance} фишек")
        print("\nДоступные ставки:")
        print("1. Прямая (номер)")
        print("2. Цвет (красное/чёрное)")
        print("3. Чёт/Нечет")
        print("4. Большое/Малое")
        print("5. Дюжина")
        print("6. Колонка")
        print("7. Спин (крутить колесо)")
        print("8. Показать статистику")
        print("9. История (10 последних)")
        print("10. Сохранить и выйти")
        choice = input("Выберите действие: ").strip()

        if choice == '10':
            roulette.save_state()
            print("Состояние сохранено. До свидания!")
            break
        elif choice == '8':
            print(Fore.MAGENTA + roulette.get_stats())
            continue
        elif choice == '9':
            roulette.display_history()
            continue
        elif choice == '7':
            if not hasattr(roulette, '_pending_bets') or not roulette._pending_bets:
                print(Fore.RED + "Сначала сделайте ставку!")
                continue
            # Анимация спина
            print("Крутим колесо...")
            time.sleep(1)
            number = roulette.spin()
            color = roulette.get_color(number)
            color_str = Fore.RED if color == 'красное' else Fore.BLACK if color == 'чёрное' else Fore.GREEN
            print(f"Выпало: {number} ({color_str}{color}{Fore.RESET})")
            win = roulette.resolve_bets(number)
            if win > 0:
                print(Fore.GREEN + f"Вы выиграли {win} фишек!")
            else:
                print(Fore.RED + "Вы проиграли ставку.")
            continue

        # Обработка ставок
        try:
            bet_type = int(choice)
            if bet_type < 1 or bet_type > 6:
                print(Fore.RED + "Неверный выбор.")
                continue
            amount = int(input("Сумма ставки: "))
            if amount <= 0 or amount > roulette.balance:
                print(Fore.RED + "Некорректная сумма.")
                continue
            if bet_type == 1:  # Прямая
                num = int(input("Введите номер (0-36): "))
                if num < 0 or num > 36:
                    print(Fore.RED + "Номер должен быть от 0 до 36.")
                    continue
                bet = Bet(BetType.STRAIGHT, amount, num)
            elif bet_type == 2:  # Цвет
                color_choice = input("Выберите цвет (0 - красное, 1 - чёрное): ")
                if color_choice not in ['0','1']:
                    print(Fore.RED + "Введите 0 или 1.")
                    continue
                bet = Bet(BetType.COLOR, amount, int(color_choice))
            elif bet_type == 3:  # Чёт/Нечет
                parity = input("0 - чёт, 1 - нечет: ")
                if parity not in ['0','1']:
                    print(Fore.RED + "Введите 0 или 1.")
                    continue
                bet = Bet(BetType.PARITY, amount, int(parity))
            elif bet_type == 4:  # Большое/Малое
                hl = input("0 - малое (1-18), 1 - большое (19-36): ")
                if hl not in ['0','1']:
                    print(Fore.RED + "Введите 0 или 1.")
                    continue
                bet = Bet(BetType.HIGH_LOW, amount, int(hl))
            elif bet_type == 5:  # Дюжина
                dozen = input("Дюжина (1, 2 или 3): ")
                if dozen not in ['1','2','3']:
                    print(Fore.RED + "Введите 1, 2 или 3.")
                    continue
                bet = Bet(BetType.DOZEN, amount, int(dozen))
            elif bet_type == 6:  # Колонка
                col = input("Колонка (1, 2 или 3): ")
                if col not in ['1','2','3']:
                    print(Fore.RED + "Введите 1, 2 или 3.")
                    continue
                bet = Bet(BetType.COLUMN, amount, int(col))
            roulette.place_bet(bet)
            print(Fore.GREEN + f"Ставка принята: {bet.type.value} {bet.value} на {amount} фишек.")
        except ValueError:
            print(Fore.RED + "Ошибка ввода.")

if __name__ == "__main__":
    # Автоигра
    import sys
    if len(sys.argv) > 1 and sys.argv[1] == "--auto":
        # Простая стратегия: ставить на красное по 10 фишек
        roulette = Roulette()
        print("Автоигра: ставка на красное по 10 фишек, 20 спинов")
        for _ in range(20):
            if roulette.balance < 10:
                break
            bet = Bet(BetType.COLOR, 10, 0)  # 0 - красное
            roulette.place_bet(bet)
            number = roulette.spin()
            win = roulette.resolve_bets(number)
            print(f"Спин {_+1}: {number} {roulette.get_color(number)}, выигрыш: {win}, баланс: {roulette.balance}")
            time.sleep(0.5)
        roulette.save_state()
        print("Автоигра завершена.")
    else:
        interactive_menu()
