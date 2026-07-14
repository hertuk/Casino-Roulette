// roulette.js - Симулятор европейской рулетки на JavaScript (Node.js)
const readline = require('readline').createInterface({
    input: process.stdin,
    output: process.stdout
});
const fs = require('fs').promises;

// Константы
const NUMBERS = Array.from({length: 37}, (_, i) => i);
const RED_NUMBERS = new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
const BLACK_NUMBERS = new Set([2,4,6,8,10,11,13,15,17,20,22,24,26,28,29,31,33,35]);

class Roulette {
    constructor(initialBalance = 1000) {
        this.balance = initialBalance;
        this.history = [];
        this.totalSpins = 0;
        this.totalWins = 0;
        this.pendingBets = [];
        this.loadState();
    }

    spin() {
        return NUMBERS[Math.floor(Math.random() * NUMBERS.length)];
    }

    getColor(number) {
        if (number === 0) return 'зелёное';
        if (RED_NUMBERS.has(number)) return 'красное';
        return 'чёрное';
    }

    placeBet(type, amount, value) {
        if (amount > this.balance) throw new Error('Недостаточно средств');
        this.balance -= amount;
        this.pendingBets.push({ type, amount, value });
    }

    resolveBets(number) {
        let totalWin = 0;
        const color = this.getColor(number);
        for (const bet of this.pendingBets) {
            const win = this.calcWin(bet, number, color);
            totalWin += win;
            this.balance += win;
            this.history.push({
                number,
                color,
                betType: bet.type,
                betValue: bet.value,
                amount: bet.amount,
                win
            });
        }
        this.pendingBets = [];
        this.totalSpins++;
        if (totalWin > 0) this.totalWins++;
        return totalWin;
    }

    calcWin(bet, number, color) {
        switch (bet.type) {
            case 'STRAIGHT':
                return bet.value === number ? bet.amount * 36 : 0;
            case 'COLOR':
                if ((bet.value === 0 && color === 'красное') || (bet.value === 1 && color === 'чёрное'))
                    return bet.amount * 2;
                return 0;
            case 'PARITY':
                if ((bet.value === 0 && number % 2 === 0 && number !== 0) ||
                    (bet.value === 1 && number % 2 === 1))
                    return bet.amount * 2;
                return 0;
            case 'HIGHLOW':
                if ((bet.value === 0 && number >= 1 && number <= 18) ||
                    (bet.value === 1 && number >= 19 && number <= 36))
                    return bet.amount * 2;
                return 0;
            case 'DOZEN':
                if ((bet.value === 1 && number >= 1 && number <= 12) ||
                    (bet.value === 2 && number >= 13 && number <= 24) ||
                    (bet.value === 3 && number >= 25 && number <= 36))
                    return bet.amount * 3;
                return 0;
            case 'COLUMN':
                if (number === 0) return 0;
                const col = (number - 1) % 3 + 1;
                return bet.value === col ? bet.amount * 3 : 0;
            default:
                return 0;
        }
    }

    getStats() {
        if (this.totalSpins === 0) return 'Нет спинов.';
        const winRate = this.totalWins / this.totalSpins * 100;
        return `Всего спинов: ${this.totalSpins}, Выигрышных: ${this.totalWins} (${winRate.toFixed(1)}%), Баланс: ${this.balance}`;
    }

    async saveState(filename = 'roulette_state.json') {
        const data = {
            balance: this.balance,
            history: this.history,
            totalSpins: this.totalSpins,
            totalWins: this.totalWins
        };
        await fs.writeFile(filename, JSON.stringify(data, null, 2));
    }

    async loadState(filename = 'roulette_state.json') {
        try {
            const data = await fs.readFile(filename, 'utf8');
            const obj = JSON.parse(data);
            this.balance = obj.balance || 1000;
            this.history = obj.history || [];
            this.totalSpins = obj.totalSpins || 0;
            this.totalWins = obj.totalWins || 0;
        } catch {}
    }

    displayHistory(n = 10) {
        if (this.history.length === 0) {
            console.log('История пуста.');
            return;
        }
        console.log('\x1b[36mПоследние спины:\x1b[0m');
        const entries = this.history.slice(-n);
        for (const entry of entries) {
            const colorCode = entry.color === 'красное' ? '\x1b[31m' :
                              entry.color === 'чёрное' ? '\x1b[30m' : '\x1b[32m';
            console.log(`${entry.number} (${colorCode}${entry.color}\x1b[0m) Ставка: ${entry.betType} ${entry.betValue} Сумма: ${entry.amount} -> Выигрыш: ${entry.win}`);
        }
    }
}

function askQuestion(query) {
    return new Promise(resolve => readline.question(query, resolve));
}

async function interactiveMenu() {
    const roulette = new Roulette();
    console.log('\x1b[33m🎰 Добро пожаловать в Европейскую рулетку!\x1b[0m');
    while (true) {
        console.log(`\nБаланс: ${roulette.balance} фишек`);
        console.log('\nДоступные ставки:');
        console.log('1. Прямая (номер)');
        console.log('2. Цвет (красное/чёрное)');
        console.log('3. Чёт/Нечет');
        console.log('4. Большое/Малое');
        console.log('5. Дюжина');
        console.log('6. Колонка');
        console.log('7. Спин');
        console.log('8. Статистика');
        console.log('9. История');
        console.log('10. Сохранить и выйти');
        const choice = await askQuestion('Выберите действие: ');

        if (choice === '10') {
            await roulette.saveState();
            console.log('Состояние сохранено. До свидания!');
            readline.close();
            process.exit(0);
        } else if (choice === '8') {
            console.log('\x1b[35m' + roulette.getStats() + '\x1b[0m');
            continue;
        } else if (choice === '9') {
            roulette.displayHistory();
            continue;
        } else if (choice === '7') {
            if (roulette.pendingBets.length === 0) {
                console.log('\x1b[31mСначала сделайте ставку!\x1b[0m');
                continue;
            }
            console.log('Крутим колесо...');
            await new Promise(resolve => setTimeout(resolve, 1000));
            const number = roulette.spin();
            const color = roulette.getColor(number);
            const colorCode = color === 'красное' ? '\x1b[31m' :
                              color === 'чёрное' ? '\x1b[30m' : '\x1b[32m';
            console.log(`Выпало: ${number} (${colorCode}${color}\x1b[0m)`);
            const win = roulette.resolveBets(number);
            if (win > 0) console.log(`\x1b[32mВы выиграли ${win} фишек!\x1b[0m`);
            else console.log('\x1b[31mВы проиграли ставку.\x1b[0m');
            continue;
        }

        // Ставки
        const betType = parseInt(choice);
        if (isNaN(betType) || betType < 1 || betType > 6) {
            console.log('\x1b[31mНеверный выбор.\x1b[0m');
            continue;
        }
        const amount = parseInt(await askQuestion('Сумма ставки: '));
        if (isNaN(amount) || amount <= 0 || amount > roulette.balance) {
            console.log('\x1b[31mНекорректная сумма.\x1b[0m');
            continue;
        }
        let value;
        switch (betType) {
            case 1: // STRAIGHT
                value = parseInt(await askQuestion('Введите номер (0-36): '));
                if (isNaN(value) || value < 0 || value > 36) {
                    console.log('\x1b[31mНомер от 0 до 36.\x1b[0m');
                    continue;
                }
                roulette.placeBet('STRAIGHT', amount, value);
                break;
            case 2: // COLOR
                const c = await askQuestion('0 - красное, 1 - чёрное: ');
                if (!['0','1'].includes(c)) { console.log('\x1b[31mВведите 0 или 1.\x1b[0m'); continue; }
                roulette.placeBet('COLOR', amount, parseInt(c));
                break;
            case 3: // PARITY
                const p = await askQuestion('0 - чёт, 1 - нечет: ');
                if (!['0','1'].includes(p)) { console.log('\x1b[31mВведите 0 или 1.\x1b[0m'); continue; }
                roulette.placeBet('PARITY', amount, parseInt(p));
                break;
            case 4: // HIGHLOW
                const hl = await askQuestion('0 - малое (1-18), 1 - большое (19-36): ');
                if (!['0','1'].includes(hl)) { console.log('\x1b[31mВведите 0 или 1.\x1b[0m'); continue; }
                roulette.placeBet('HIGHLOW', amount, parseInt(hl));
                break;
            case 5: // DOZEN
                const d = await askQuestion('Дюжина (1,2,3): ');
                if (!['1','2','3'].includes(d)) { console.log('\x1b[31mВведите 1,2 или 3.\x1b[0m'); continue; }
                roulette.placeBet('DOZEN', amount, parseInt(d));
                break;
            case 6: // COLUMN
                const col = await askQuestion('Колонка (1,2,3): ');
                if (!['1','2','3'].includes(col)) { console.log('\x1b[31mВведите 1,2 или 3.\x1b[0m'); continue; }
                roulette.placeBet('COLUMN', amount, parseInt(col));
                break;
        }
        console.log(`\x1b[32mСтавка принята.\x1b[0m`);
    }
}

// Автоигра
if (process.argv.includes('--auto')) {
    (async () => {
        const roulette = new Roulette();
        console.log('Автоигра: ставка на красное по 10 фишек, 20 спинов');
        for (let i = 0; i < 20; i++) {
            if (roulette.balance < 10) break;
            roulette.placeBet('COLOR', 10, 0);
            const number = roulette.spin();
            const win = roulette.resolveBets(number);
            console.log(`Спин ${i+1}: ${number} ${roulette.getColor(number)}, выигрыш: ${win}, баланс: ${roulette.balance}`);
            await new Promise(resolve => setTimeout(resolve, 500));
        }
        await roulette.saveState();
        console.log('Автоигра завершена.');
        readline.close();
    })();
} else {
    interactiveMenu();
}
