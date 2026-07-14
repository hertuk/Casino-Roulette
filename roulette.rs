// roulette.rs - Симулятор европейской рулетки на Rust
// Для запуска требуется Cargo.toml с зависимостями:
// [dependencies]
// rand = "0.8"
// serde = { version = "1.0", features = ["derive"] }
// serde_json = "1.0"

use std::collections::HashSet;
use std::io::{self, Write, BufRead};
use std::fs;
use rand::Rng;
use serde::{Serialize, Deserialize};

const RED_NUMBERS: [i32; 18] = [1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36];
const BLACK_NUMBERS: [i32; 18] = [2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35];

lazy_static::lazy_static! {
    static ref RED_SET: HashSet<i32> = RED_NUMBERS.iter().cloned().collect();
    static ref BLACK_SET: HashSet<i32> = BLACK_NUMBERS.iter().cloned().collect();
}

#[derive(Clone, Serialize, Deserialize)]
struct Bet {
    bet_type: String,
    amount: i32,
    value: i32,
}

#[derive(Serialize, Deserialize)]
struct Roulette {
    balance: i32,
    history: Vec<serde_json::Value>,
    total_spins: i32,
    total_wins: i32,
    #[serde(skip)]
    pending_bets: Vec<Bet>,
    #[serde(skip)]
    rng: rand::rngs::ThreadRng,
}

impl Roulette {
    fn new(initial_balance: i32) -> Self {
        let mut r = Self {
            balance: initial_balance,
            history: Vec::new(),
            total_spins: 0,
            total_wins: 0,
            pending_bets: Vec::new(),
            rng: rand::thread_rng(),
        };
        r.load_state();
        r
    }

    fn spin(&mut self) -> i32 {
        self.rng.gen_range(0..=36)
    }

    fn get_color(&self, number: i32) -> String {
        if number == 0 { "зелёное".to_string() }
        else if RED_SET.contains(&number) { "красное".to_string() }
        else { "чёрное".to_string() }
    }

    fn place_bet(&mut self, bet_type: &str, amount: i32, value: i32) -> Result<(), String> {
        if amount > self.balance {
            return Err("Недостаточно средств".to_string());
        }
        self.balance -= amount;
        self.pending_bets.push(Bet {
            bet_type: bet_type.to_string(),
            amount,
            value,
        });
        Ok(())
    }

    fn resolve_bets(&mut self, number: i32) -> i32 {
        let mut total_win = 0;
        let color = self.get_color(number);
        let bets = std::mem::take(&mut self.pending_bets);
        for bet in bets {
            let win = self.calc_win(&bet, number, &color);
            total_win += win;
            self.balance += win;
            let entry = serde_json::json!({
                "number": number,
                "color": color,
                "betType": bet.bet_type,
                "betValue": bet.value,
                "amount": bet.amount,
                "win": win
            });
            self.history.push(entry);
        }
        self.total_spins += 1;
        if total_win > 0 {
            self.total_wins += 1;
        }
        total_win
    }

    fn calc_win(&self, bet: &Bet, number: i32, color: &str) -> i32 {
        match bet.bet_type.as_str() {
            "STRAIGHT" => if bet.value == number { bet.amount * 36 } else { 0 },
            "COLOR" => {
                if (bet.value == 0 && color == "красное") || (bet.value == 1 && color == "чёрное") {
                    bet.amount * 2
                } else { 0 }
            }
            "PARITY" => {
                if (bet.value == 0 && number % 2 == 0 && number != 0) ||
                   (bet.value == 1 && number % 2 == 1) {
                    bet.amount * 2
                } else { 0 }
            }
            "HIGHLOW" => {
                if (bet.value == 0 && number >= 1 && number <= 18) ||
                   (bet.value == 1 && number >= 19 && number <= 36) {
                    bet.amount * 2
                } else { 0 }
            }
            "DOZEN" => {
                if (bet.value == 1 && number >= 1 && number <= 12) ||
                   (bet.value == 2 && number >= 13 && number <= 24) ||
                   (bet.value == 3 && number >= 25 && number <= 36) {
                    bet.amount * 3
                } else { 0 }
            }
            "COLUMN" => {
                if number == 0 { return 0; }
                let col = (number - 1) % 3 + 1;
                if bet.value == col { bet.amount * 3 } else { 0 }
            }
            _ => 0,
        }
    }

    fn get_stats(&self) -> String {
        if self.total_spins == 0 {
            return "Нет спинов.".to_string();
        }
        let win_rate = self.total_wins as f64 / self.total_spins as f64 * 100.0;
        format!("Всего спинов: {}, Выигрышных: {} ({:.1}%), Баланс: {}",
                self.total_spins, self.total_wins, win_rate, self.balance)
    }

    fn save_state(&self, filename: &str) -> Result<(), String> {
        let data = serde_json::json!({
            "balance": self.balance,
            "history": self.history,
            "total_spins": self.total_spins,
            "total_wins": self.total_wins,
        });
        let json = serde_json::to_string_pretty(&data).map_err(|e| e.to_string())?;
        fs::write(filename, json).map_err(|e| e.to_string())
    }

    fn load_state(&mut self) {
        if let Ok(data) = fs::read_to_string("roulette_state.json") {
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&data) {
                if let Some(b) = json["balance"].as_i64() { self.balance = b as i32; }
                if let Some(s) = json["total_spins"].as_i64() { self.total_spins = s as i32; }
                if let Some(w) = json["total_wins"].as_i64() { self.total_wins = w as i32; }
                if let Some(h) = json["history"].as_array() {
                    self.history = h.clone();
                }
            }
        }
    }

    fn display_history(&self, n: usize) {
        if self.history.is_empty() {
            println!("История пуста.");
            return;
        }
        println!("\x1b[36mПоследние спины:\x1b[0m");
        let start = if self.history.len() > n { self.history.len() - n } else { 0 };
        for i in start..self.history.len() {
            let entry = &self.history[i];
            let num = entry["number"].as_i64().unwrap_or(0);
            let color = entry["color"].as_str().unwrap_or("");
            let color_code = if color == "красное" { "\x1b[31m" } else if color == "чёрное" { "\x1b[30m" } else { "\x1b[32m" };
            println!("{} ({}{}\x1b[0m) Ставка: {} {} Сумма: {} -> Выигрыш: {}",
                     num, color_code, color,
                     entry["betType"].as_str().unwrap_or(""),
                     entry["betValue"].as_i64().unwrap_or(0),
                     entry["amount"].as_i64().unwrap_or(0),
                     entry["win"].as_i64().unwrap_or(0));
        }
    }

    fn has_pending_bets(&self) -> bool { !self.pending_bets.is_empty() }
}

fn read_line() -> String {
    let stdin = io::stdin();
    let mut line = String::new();
    stdin.lock().read_line(&mut line).expect("Ошибка ввода");
    line.trim().to_string()
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() > 1 && args[1] == "--auto" {
        let mut roulette = Roulette::new(1000);
        println!("Автоигра: ставка на красное по 10 фишек, 20 спинов");
        for i in 0..20 {
            if roulette.balance < 10 { break; }
            roulette.place_bet("COLOR", 10, 0).unwrap();
            let number = roulette.spin();
            let win = roulette.resolve_bets(number);
            println!("Спин {}: {} {}, выигрыш: {}, баланс: {}", i+1, number, roulette.get_color(number), win, roulette.balance);
            std::thread::sleep(std::time::Duration::from_millis(500));
        }
        roulette.save_state("roulette_state.json").unwrap();
        println!("Автоигра завершена.");
        return;
    }

    let mut roulette = Roulette::new(1000);
    println!("\x1b[33m🎰 Добро пожаловать в Европейскую рулетку!\x1b[0m");
    loop {
        println!("\nБаланс: {} фишек", roulette.balance);
        println!("\nДоступные ставки:");
        println!("1. Прямая (номер)");
        println!("2. Цвет (красное/чёрное)");
        println!("3. Чёт/Нечет");
        println!("4. Большое/Малое");
        println!("5. Дюжина");
        println!("6. Колонка");
        println!("7. Спин");
        println!("8. Статистика");
        println!("9. История");
        println!("10. Сохранить и выйти");
        print!("Выберите действие: ");
        io::stdout().flush().unwrap();
        let choice = read_line();

        match choice.as_str() {
            "10" => {
                roulette.save_state("roulette_state.json").unwrap();
                println!("Состояние сохранено. До свидания!");
                break;
            }
            "8" => println!("\x1b[35m{}\x1b[0m", roulette.get_stats()),
            "9" => roulette.display_history(10),
            "7" => {
                if !roulette.has_pending_bets() {
                    println!("\x1b[31mСначала сделайте ставку!\x1b[0m");
                    continue;
                }
                println!("Крутим колесо...");
                std::thread::sleep(std::time::Duration::from_secs(1));
                let number = roulette.spin();
                let color = roulette.get_color(number);
                let color_code = if color == "красное" { "\x1b[31m" } else if color == "чёрное" { "\x1b[30m" } else { "\x1b[32m" };
                println!("Выпало: {} ({}{}\x1b[0m)", number, color_code, color);
                let win = roulette.resolve_bets(number);
                if win > 0 {
                    println!("\x1b[32mВы выиграли {} фишек!\x1b[0m", win);
                } else {
                    println!("\x1b[31mВы проиграли ставку.\x1b[0m");
                }
            }
            _ => {
                // Ставки
                let bet_type: i32 = choice.parse().unwrap_or(0);
                if bet_type < 1 || bet_type > 6 {
                    println!("\x1b[31mНеверный выбор.\x1b[0m");
                    continue;
                }
                print!("Сумма ставки: ");
                io::stdout().flush().unwrap();
                let amount_str = read_line();
                let amount: i32 = amount_str.parse().unwrap_or(0);
                if amount <= 0 || amount > roulette.balance {
                    println!("\x1b[31mНекорректная сумма.\x1b[0m");
                    continue;
                }
                let mut value = 0;
                let mut ok = true;
                match bet_type {
                    1 => {
                        print!("Введите номер (0-36): ");
                        io::stdout().flush().unwrap();
                        value = read_line().parse().unwrap_or(-1);
                        if value < 0 || value > 36 {
                            println!("\x1b[31mНомер от 0 до 36.\x1b[0m");
                            ok = false;
                        } else {
                            roulette.place_bet("STRAIGHT", amount, value).unwrap();
                        }
                    }
                    2 => {
                        print!("0 - красное, 1 - чёрное: ");
                        io::stdout().flush().unwrap();
                        value = read_line().parse().unwrap_or(-1);
                        if value != 0 && value != 1 {
                            println!("\x1b[31mВведите 0 или 1.\x1b[0m");
                            ok = false;
                        } else {
                            roulette.place_bet("COLOR", amount, value).unwrap();
                        }
                    }
                    3 => {
                        print!("0 - чёт, 1 - нечет: ");
                        io::stdout().flush().unwrap();
                        value = read_line().parse().unwrap_or(-1);
                        if value != 0 && value != 1 {
                            println!("\x1b[31mВведите 0 или 1.\x1b[0m");
                            ok = false;
                        } else {
                            roulette.place_bet("PARITY", amount, value).unwrap();
                        }
                    }
                    4 => {
                        print!("0 - малое (1-18), 1 - большое (19-36): ");
                        io::stdout().flush().unwrap();
                        value = read_line().parse().unwrap_or(-1);
                        if value != 0 && value != 1 {
                            println!("\x1b[31mВведите 0 или 1.\x1b[0m");
                            ok = false;
                        } else {
                            roulette.place_bet("HIGHLOW", amount, value).unwrap();
                        }
                    }
                    5 => {
                        print!("Дюжина (1,2,3): ");
                        io::stdout().flush().unwrap();
                        value = read_line().parse().unwrap_or(-1);
                        if value < 1 || value > 3 {
                            println!("\x1b[31mВведите 1,2 или 3.\x1b[0m");
                            ok = false;
                        } else {
                            roulette.place_bet("DOZEN", amount, value).unwrap();
                        }
                    }
                    6 => {
                        print!("Колонка (1,2,3): ");
                        io::stdout().flush().unwrap();
                        value = read_line().parse().unwrap_or(-1);
                        if value < 1 || value > 3 {
                            println!("\x1b[31mВведите 1,2 или 3.\x1b[0m");
                            ok = false;
                        } else {
                            roulette.place_bet("COLUMN", amount, value).unwrap();
                        }
                    }
                    _ => {}
                }
                if ok {
                    println!("\x1b[32mСтавка принята.\x1b[0m");
                }
            }
        }
    }
}
