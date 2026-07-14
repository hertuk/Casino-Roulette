// roulette.go - Симулятор европейской рулетки на Go
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"strconv"
	"strings"
	"time"
)

var Numbers = make([]int, 37)
var RedNumbers = map[int]bool{}
var BlackNumbers = map[int]bool{}

func init() {
	for i := 0; i < 37; i++ {
		Numbers[i] = i
	}
	reds := []int{1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
	blacks := []int{2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35}
	for _, n := range reds {
		RedNumbers[n] = true
	}
	for _, n := range blacks {
		BlackNumbers[n] = true
	}
}

type Bet struct {
	Type   string
	Amount int
	Value  int
}

type Roulette struct {
	balance     int
	history     []map[string]interface{}
	totalSpins  int
	totalWins   int
	pendingBets []Bet
	rand        *rand.Rand
}

func NewRoulette(initialBalance int) *Roulette {
	r := &Roulette{
		balance:     initialBalance,
		history:     []map[string]interface{}{},
		totalSpins:  0,
		totalWins:   0,
		pendingBets: []Bet{},
		rand:        rand.New(rand.NewSource(time.Now().UnixNano())),
	}
	r.loadState()
	return r
}

func (r *Roulette) Spin() int {
	return Numbers[r.rand.Intn(len(Numbers))]
}

func (r *Roulette) GetColor(number int) string {
	if number == 0 {
		return "зелёное"
	}
	if RedNumbers[number] {
		return "красное"
	}
	return "чёрное"
}

func (r *Roulette) PlaceBet(betType string, amount int, value int) error {
	if amount > r.balance {
		return fmt.Errorf("недостаточно средств")
	}
	r.balance -= amount
	r.pendingBets = append(r.pendingBets, Bet{Type: betType, Amount: amount, Value: value})
	return nil
}

func (r *Roulette) ResolveBets(number int) int {
	totalWin := 0
	color := r.GetColor(number)
	for _, bet := range r.pendingBets {
		win := r.calcWin(bet, number, color)
		totalWin += win
		r.balance += win
		entry := map[string]interface{}{
			"number":   number,
			"color":    color,
			"betType":  bet.Type,
			"betValue": bet.Value,
			"amount":   bet.Amount,
			"win":      win,
		}
		r.history = append(r.history, entry)
	}
	r.pendingBets = []Bet{}
	r.totalSpins++
	if totalWin > 0 {
		r.totalWins++
	}
	return totalWin
}

func (r *Roulette) calcWin(bet Bet, number int, color string) int {
	switch bet.Type {
	case "STRAIGHT":
		if bet.Value == number {
			return bet.Amount * 36
		}
	case "COLOR":
		if (bet.Value == 0 && color == "красное") || (bet.Value == 1 && color == "чёрное") {
			return bet.Amount * 2
		}
	case "PARITY":
		if (bet.Value == 0 && number%2 == 0 && number != 0) ||
			(bet.Value == 1 && number%2 == 1) {
			return bet.Amount * 2
		}
	case "HIGHLOW":
		if (bet.Value == 0 && number >= 1 && number <= 18) ||
			(bet.Value == 1 && number >= 19 && number <= 36) {
			return bet.Amount * 2
		}
	case "DOZEN":
		if (bet.Value == 1 && number >= 1 && number <= 12) ||
			(bet.Value == 2 && number >= 13 && number <= 24) ||
			(bet.Value == 3 && number >= 25 && number <= 36) {
			return bet.Amount * 3
		}
	case "COLUMN":
		if number == 0 {
			return 0
		}
		col := (number-1)%3 + 1
		if bet.Value == col {
			return bet.Amount * 3
		}
	}
	return 0
}

func (r *Roulette) GetStats() string {
	if r.totalSpins == 0 {
		return "Нет спинов."
	}
	winRate := float64(r.totalWins) / float64(r.totalSpins) * 100
	return fmt.Sprintf("Всего спинов: %d, Выигрышных: %d (%.1f%%), Баланс: %d",
		r.totalSpins, r.totalWins, winRate, r.balance)
}

func (r *Roulette) SaveState(filename string) error {
	data := map[string]interface{}{
		"balance":    r.balance,
		"history":    r.history,
		"totalSpins": r.totalSpins,
		"totalWins":  r.totalWins,
	}
	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filename, jsonData, 0644)
}

func (r *Roulette) loadState() {
	data, err := os.ReadFile("roulette_state.json")
	if err != nil {
		return
	}
	var obj map[string]interface{}
	if err := json.Unmarshal(data, &obj); err != nil {
		return
	}
	if val, ok := obj["balance"].(float64); ok {
		r.balance = int(val)
	}
	if val, ok := obj["totalSpins"].(float64); ok {
		r.totalSpins = int(val)
	}
	if val, ok := obj["totalWins"].(float64); ok {
		r.totalWins = int(val)
	}
	// history пропускаем для простоты
}

func (r *Roulette) DisplayHistory(n int) {
	if len(r.history) == 0 {
		fmt.Println("История пуста.")
		return
	}
	fmt.Println("\x1b[36mПоследние спины:\x1b[0m")
	start := len(r.history) - n
	if start < 0 {
		start = 0
	}
	for i := start; i < len(r.history); i++ {
		entry := r.history[i]
		color := entry["color"].(string)
		colorCode := "\x1b[31m"
		if color == "чёрное" {
			colorCode = "\x1b[30m"
		} else if color == "зелёное" {
			colorCode = "\x1b[32m"
		}
		fmt.Printf("%d (%s%s\x1b[0m) Ставка: %s %d Сумма: %d -> Выигрыш: %d\n",
			entry["number"], colorCode, color,
			entry["betType"], entry["betValue"],
			entry["amount"], entry["win"])
	}
}

func (r *Roulette) HasPendingBets() bool {
	return len(r.pendingBets) > 0
}

func readLine() string {
	scanner := bufio.NewScanner(os.Stdin)
	scanner.Scan()
	return strings.TrimSpace(scanner.Text())
}

func main() {
	if len(os.Args) > 1 && os.Args[1] == "--auto" {
		r := NewRoulette(1000)
		fmt.Println("Автоигра: ставка на красное по 10 фишек, 20 спинов")
		for i := 0; i < 20; i++ {
			if r.balance < 10 {
				break
			}
			r.PlaceBet("COLOR", 10, 0)
			number := r.Spin()
			win := r.ResolveBets(number)
			fmt.Printf("Спин %d: %d %s, выигрыш: %d, баланс: %d\n", i+1, number, r.GetColor(number), win, r.balance)
			time.Sleep(500 * time.Millisecond)
		}
		r.SaveState("roulette_state.json")
		fmt.Println("Автоигра завершена.")
		return
	}

	r := NewRoulette(1000)
	fmt.Println("\x1b[33m🎰 Добро пожаловать в Европейскую рулетку!\x1b[0m")
	for {
		fmt.Printf("\nБаланс: %d фишек\n", r.balance)
		fmt.Println("\nДоступные ставки:")
		fmt.Println("1. Прямая (номер)")
		fmt.Println("2. Цвет (красное/чёрное)")
		fmt.Println("3. Чёт/Нечет")
		fmt.Println("4. Большое/Малое")
		fmt.Println("5. Дюжина")
		fmt.Println("6. Колонка")
		fmt.Println("7. Спин")
		fmt.Println("8. Статистика")
		fmt.Println("9. История")
		fmt.Println("10. Сохранить и выйти")
		fmt.Print("Выберите действие: ")
		choice := readLine()

		switch choice {
		case "10":
			r.SaveState("roulette_state.json")
			fmt.Println("Состояние сохранено. До свидания!")
			return
		case "8":
			fmt.Println("\x1b[35m" + r.GetStats() + "\x1b[0m")
			continue
		case "9":
			r.DisplayHistory(10)
			continue
		case "7":
			if !r.HasPendingBets() {
				fmt.Println("\x1b[31mСначала сделайте ставку!\x1b[0m")
				continue
			}
			fmt.Println("Крутим колесо...")
			time.Sleep(1 * time.Second)
			number := r.Spin()
			color := r.GetColor(number)
			colorCode := "\x1b[31m"
			if color == "чёрное" {
				colorCode = "\x1b[30m"
			} else if color == "зелёное" {
				colorCode = "\x1b[32m"
			}
			fmt.Printf("Выпало: %d (%s%s\x1b[0m)\n", number, colorCode, color)
			win := r.ResolveBets(number)
			if win > 0 {
				fmt.Printf("\x1b[32mВы выиграли %d фишек!\x1b[0m\n", win)
			} else {
				fmt.Println("\x1b[31mВы проиграли ставку.\x1b[0m")
			}
			continue
		}

		// Ставки
		betType, err := strconv.Atoi(choice)
		if err != nil || betType < 1 || betType > 6 {
			fmt.Println("\x1b[31mНеверный выбор.\x1b[0m")
			continue
		}
		fmt.Print("Сумма ставки: ")
		amountStr := readLine()
		amount, err := strconv.Atoi(amountStr)
		if err != nil || amount <= 0 || amount > r.balance {
			fmt.Println("\x1b[31mНекорректная сумма.\x1b[0m")
			continue
		}
		var value int
		var ok bool
		switch betType {
		case 1:
			fmt.Print("Введите номер (0-36): ")
			valStr := readLine()
			value, err = strconv.Atoi(valStr)
			if err != nil || value < 0 || value > 36 {
				fmt.Println("\x1b[31mНомер от 0 до 36.\x1b[0m")
				continue
			}
			r.PlaceBet("STRAIGHT", amount, value)
			ok = true
		case 2:
			fmt.Print("0 - красное, 1 - чёрное: ")
			valStr := readLine()
			value, err = strconv.Atoi(valStr)
			if err != nil || (value != 0 && value != 1) {
				fmt.Println("\x1b[31mВведите 0 или 1.\x1b[0m")
				continue
			}
			r.PlaceBet("COLOR", amount, value)
			ok = true
		case 3:
			fmt.Print("0 - чёт, 1 - нечет: ")
			valStr := readLine()
			value, err = strconv.Atoi(valStr)
			if err != nil || (value != 0 && value != 1) {
				fmt.Println("\x1b[31mВведите 0 или 1.\x1b[0m")
				continue
			}
			r.PlaceBet("PARITY", amount, value)
			ok = true
		case 4:
			fmt.Print("0 - малое (1-18), 1 - большое (19-36): ")
			valStr := readLine()
			value, err = strconv.Atoi(valStr)
			if err != nil || (value != 0 && value != 1) {
				fmt.Println("\x1b[31mВведите 0 или 1.\x1b[0m")
				continue
			}
			r.PlaceBet("HIGHLOW", amount, value)
			ok = true
		case 5:
			fmt.Print("Дюжина (1,2,3): ")
			valStr := readLine()
			value, err = strconv.Atoi(valStr)
			if err != nil || value < 1 || value > 3 {
				fmt.Println("\x1b[31mВведите 1,2 или 3.\x1b[0m")
				continue
			}
			r.PlaceBet("DOZEN", amount, value)
			ok = true
		case 6:
			fmt.Print("Колонка (1,2,3): ")
			valStr := readLine()
			value, err = strconv.Atoi(valStr)
			if err != nil || value < 1 || value > 3 {
				fmt.Println("\x1b[31mВведите 1,2 или 3.\x1b[0m")
				continue
			}
			r.PlaceBet("COLUMN", amount, value)
			ok = true
		}
		if ok {
			fmt.Println("\x1b[32mСтавка принята.\x1b[0m")
		}
	}
}
