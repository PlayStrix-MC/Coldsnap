package ru.pavel.coldsnap;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;


/**
 * Главный цикл. Запускается каждый тик, но каждого игрока обрабатывает
 * только раз в update-interval тиков (игроки «размазаны» по тикам по entityId).
 */
public final class ColdTask implements Runnable {

    public static final String HIDE_TAG = "no_coldsnap_indicator"; // тот же тег, что и в датапаке


    private final ColdsnapPlugin plugin;
    private final DamageSource freeze;
    private long tick;

    public ColdTask(ColdsnapPlugin plugin) {
        this.plugin = plugin;
        this.freeze = DamageSource.builder(DamageType.FREEZE).build();
    }

    @Override
    public void run() {
        Settings s = plugin.settings();
        int interval = s.interval;
        long t = tick++;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (Math.floorMod(p.getEntityId() + t, interval) != 0) continue;
            PlayerState st = plugin.state(p);
            if (p.isDead() || s.disabledWorlds.contains(p.getWorld().getName()) || !plugin.coldAllowedHere(p)) {
                reset(p, st);
                continue;
            }
            process(p, st, s, interval);
        }
    }

    private void process(Player p, PlayerState st, Settings s, int interval) {
        ColdCalculator.Result r = st.last;
        ColdCalculator.compute(p, plugin.fur(), s, plugin.nexo(), r, s.cacheEnabled ? st : null, s.cacheMaxAge);
        int temp = r.temperature;

        // ---------- Урон от холода (симуляция N тиков датапака) ----------
        GameMode gm = p.getGameMode();
        boolean exempt = gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
        boolean doDamage = false;
        int cd = st.coldDamage;
        boolean damaged = st.damagedThisCycle;
        for (int i = 0; i < interval; i++) {
            if (exempt) { cd = 0; damaged = false; continue; }
            if (temp <= ColdCalculator.CHILLY && cd <= 44) cd++;
            if (temp <= ColdCalculator.FREEZING && cd <= 44) cd++;
            if (temp <= ColdCalculator.CHILLY && cd >= 40 && !damaged) { doDamage = true; damaged = true; }
            if (temp <= ColdCalculator.CHILLY && cd >= 45) { cd = 5; damaged = false; }
            if (temp > ColdCalculator.CHILLY) { cd = 0; damaged = false; }
        }
        st.coldDamage = cd;
        st.damagedThisCycle = damaged;
        if (doDamage && s.damageEnabled && s.damageAmount > 0) {
            p.damage(s.damageAmount, freeze);
        }

        // ---------- Индикатор ----------
        if (s.indicatorEnabled && !p.getScoreboardTags().contains(HIDE_TAG)) {
            if (!st.indicatorActive && temp <= ColdCalculator.CHILLY) st.indicatorActive = true;
            if (st.indicatorActive) {
                if (temp > ColdCalculator.CHILLY) {
                    p.sendActionBar(s.indWarm);
                    st.indicatorActive = false;
                } else {
                    boolean warn = doDamage || cd >= 40;
                    boolean freezing = temp <= ColdCalculator.FREEZING;
                    p.sendActionBar(freezing ? (warn ? s.indFreezingDmg : s.indFreezing) : (warn ? s.indChillyDmg : s.indChilly));
                }
            }
        }

    }

    /** Сброс, когда игрок мёртв или в выключенном мире. */
    private void reset(Player p, PlayerState st) {
        st.coldDamage = 0;
        st.damagedThisCycle = false;
        st.cacheValid = false;
        if (st.indicatorActive) {
            // убираем «синюю точку», если игрок зашёл в тёплый регион
            p.sendActionBar(plugin.settings().indWarm);
            st.indicatorActive = false;
        }
    }
}
