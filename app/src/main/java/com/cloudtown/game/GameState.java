package com.cloudtown.game;

/** Deterministic simulation; no network, wall clock rewards, or Android dependencies. */
public final class GameState {
    public static final int SIZE = 9;
    public static final int EMPTY = 0, HOUSE = 1, FARM = 2, SHOP = 3, PARK = 4, ROAD = 5, WATER = 6;
    public static final String[] NAMES = {"Лужайка", "Домик", "Ферма", "Магазин", "Парк", "Дорога", "Река"};
    public static final int[] COST = {0, 90, 120, 150, 70, 12, 0};
    public final int[][] tiles = new int[SIZE][SIZE];
    public final int[][] levels = new int[SIZE][SIZE];
    public int coins = 400, food = 30, ticks = 0, quest = 0;
    public float elapsed = 0;
    public boolean paused = false, sound = true;

    public GameState() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                tiles[r][c] = r == 7 ? WATER : (r == 4 || c == 4 ? ROAD : EMPTY);
            }
        }
        tiles[7][4] = ROAD;
        put(3, 3, HOUSE); put(5, 3, FARM); put(3, 5, SHOP); put(5, 5, PARK);
    }
    private void put(int r, int c, int kind) { tiles[r][c] = kind; levels[r][c] = 1; }
    public boolean valid(int r, int c) { return r >= 0 && c >= 0 && r < SIZE && c < SIZE; }
    public int count(int kind) {
        int n = 0;
        for (int[] row : tiles) for (int t : row) if (t == kind) n++;
        return n;
    }
    public int units(int kind) {
        int n = 0;
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) if (tiles[r][c] == kind) n += levels[r][c];
        return n;
    }
    public int population() { return units(HOUSE) * 4; }
    public int foodUse() { return units(HOUSE); }
    public int foodProduction() { return units(FARM) * 4; }
    public int happiness() { return Math.min(100, Math.max(20, 65 + units(PARK) * 9 - units(HOUSE) * 3 - (food == 0 ? 25 : 0))); }
    public int income() { return 8 + units(HOUSE) * 5 + units(SHOP) * 9 + happiness() / 10; }
    public boolean nearRoad(int r, int c) {
        int[][] ds = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] d : ds) if (valid(r + d[0], c + d[1]) && tiles[r+d[0]][c+d[1]] == ROAD) return true;
        return false;
    }
    public String build(int r, int c, int kind) {
        if (!valid(r, c) || kind < HOUSE || kind > ROAD) return "Выберите участок на острове";
        if (tiles[r][c] != EMPTY) return "Этот участок уже занят";
        if (kind != ROAD && kind != PARK && !nearRoad(r, c)) return "Здание должно стоять рядом с дорогой";
        if (coins < COST[kind]) return "Не хватает монет. Город приносит доход каждые 10 секунд";
        coins -= COST[kind]; put(r, c, kind);
        return null;
    }
    public int upgradeCost(int r, int c) { return COST[tiles[r][c]] * (levels[r][c] + 1); }
    public String upgrade(int r, int c) {
        if (!valid(r, c) || tiles[r][c] < HOUSE || tiles[r][c] > PARK) return "Это здание нельзя улучшить";
        if (levels[r][c] >= 3) return "Уже максимальный уровень";
        int cost = upgradeCost(r, c);
        if (coins < cost) return "Для улучшения нужно " + cost + " монет";
        coins -= cost; levels[r][c]++;
        return null;
    }
    public String demolish(int r, int c) {
        if (!valid(r, c) || tiles[r][c] < HOUSE || tiles[r][c] > ROAD) return "Здесь нечего сносить";
        if (tiles[r][c] == ROAD) return "Главные дороги оставим для жителей";
        coins = Math.min(999999, coins + COST[tiles[r][c]] * levels[r][c] / 2);
        tiles[r][c] = EMPTY; levels[r][c] = 0;
        return null;
    }
    public boolean advance(float seconds) {
        if (paused || seconds <= 0) return false;
        elapsed += Math.min(seconds, 1f);
        if (elapsed < 10) return false;
        elapsed -= 10; ticks++;
        food = Math.max(0, Math.min(999, food + foodProduction() - foodUse()));
        coins = Math.min(999999, coins + income());
        return true;
    }
    public String questTitle() {
        switch (quest) {
            case 0: return "Первые соседи";
            case 1: return "Своя маленькая ферма";
            case 2: return "Живая торговая улица";
            case 3: return "Город становится выше";
            case 4: return "Больше зелени";
            default: return "Уютный городок";
        }
    }
    public int questProgress() {
        switch (quest) {
            case 0: return Math.min(3, count(HOUSE));
            case 1: return Math.min(2, count(FARM));
            case 2: return Math.min(2, count(SHOP));
            case 3:
                int n = 0;
                for (int[] row : levels) for (int l : row) if (l >= 2) n++;
                return Math.min(2, n);
            case 4: return Math.min(3, count(PARK));
            default: return Math.min(60, population());
        }
    }
    public int questTarget() { return quest == 0 || quest == 4 ? 3 : (quest >= 5 ? 60 : 2); }
    public int questReward() { return 100 + quest * 60; }
    public String questHint() {
        switch (quest) {
            case 0: return "Постройте 3 домика";
            case 1: return "Постройте 2 фермы";
            case 2: return "Откройте 2 магазина";
            case 3: return "Улучшите 2 здания до уровня 2";
            case 4: return "Посадите 3 парка";
            default: return "Поселите 60 жителей";
        }
    }
    public boolean canClaim() { return quest <= 5 && questProgress() >= questTarget(); }
    public int claim() {
        if (!canClaim()) return 0;
        int reward = questReward(); coins = Math.min(999999, coins + reward); quest++; return reward;
    }
    public String encode() {
        StringBuilder b = new StringBuilder("1;").append(coins).append(';').append(food).append(';')
            .append(ticks).append(';').append(quest).append(';').append(elapsed).append(';')
            .append(sound ? 1 : 0).append(';');
        for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) b.append(tiles[r][c]).append(',').append(levels[r][c]).append(';');
        return b.toString();
    }
    public static GameState decode(String data) {
        if (data == null) return new GameState();
        try {
            String[] parts = data.split(";");
            if (parts.length != 7 + SIZE * SIZE || !parts[0].equals("1")) return new GameState();
            GameState s = new GameState();
            s.coins = Integer.parseInt(parts[1]); s.food = Integer.parseInt(parts[2]);
            s.ticks = Integer.parseInt(parts[3]); s.quest = Integer.parseInt(parts[4]);
            s.elapsed = Float.parseFloat(parts[5]); s.sound = parts[6].equals("1");
            // Index 7 is the first tile. Trailing delimiter is omitted by split().
            for (int r = 0; r < SIZE; r++) for (int c = 0; c < SIZE; c++) {
                String[] cell = parts[7+r*SIZE+c].split(",");
                int t = Integer.parseInt(cell[0]), l = Integer.parseInt(cell[1]);
                if (t < EMPTY || t > WATER || l < 0 || l > 3 || (t >= HOUSE && t <= PARK && l == 0)) return new GameState();
                s.tiles[r][c] = t; s.levels[r][c] = l;
            }
            if (s.coins < 0 || s.coins > 999999 || s.food < 0 || s.food > 999 || s.ticks < 0 || s.quest < 0 || s.quest > 6 || Float.isNaN(s.elapsed) || Float.isInfinite(s.elapsed) || s.elapsed < 0 || s.elapsed >= 10) return new GameState();
            return s;
        } catch (RuntimeException e) { return new GameState(); }
    }
}
