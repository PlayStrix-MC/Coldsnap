package ru.pavel.coldsnap;

/** Состояние игрока в памяти (вместо scoreboard-объективов датапака). */
public final class PlayerState {
    /** Аналог scoreboard cold_damage. */
    public int coldDamage;
    /** Аналог тега coldsnap (индикатор активен). */
    public boolean indicatorActive;
    /** Урон уже нанесён в текущем цикле накопления. */
    public boolean damagedThisCycle;

    // ---------- кэш позиции ----------
    public boolean cacheValid;
    public org.bukkit.World cacheWorld;
    public int cacheX, cacheY, cacheZ;
    public boolean cacheRain, cacheThunder, cacheNight, cacheOnFire;
    public int cacheAge;

    // ---------- кэш брони ----------
    /** true — броня менялась, нужно пересчитать (ставится событием PlayerArmorChangeEvent). */
    public boolean armorDirty = true;
    public int armorInsulation;

    public final ColdCalculator.Result last = new ColdCalculator.Result();
}
