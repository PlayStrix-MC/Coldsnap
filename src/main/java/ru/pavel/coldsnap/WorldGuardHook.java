package ru.pavel.coldsnap;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Интеграция с WorldGuard: флаг региона «coldsnap-cold».
 * /rg flag <регион> coldsnap-cold deny  — в регионе холода нет.
 * Класс загружается только если WorldGuard установлен.
 */
public final class WorldGuardHook {

    public static final String FLAG_NAME = "coldsnap-cold";

    private final StateFlag flag;

    private WorldGuardHook(StateFlag flag) {
        this.flag = flag;
    }

    /** Вызывать в onLoad(): WorldGuard принимает новые флаги только до своего включения. */
    public static WorldGuardHook register(Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag f = new StateFlag(FLAG_NAME, true); // по умолчанию холод разрешён
            registry.register(f);
            log.info("Зарегистрирован флаг WorldGuard: " + FLAG_NAME);
            return new WorldGuardHook(f);
        } catch (FlagConflictException | IllegalStateException e) {
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag sf) return new WorldGuardHook(sf);
            log.warning("Не удалось зарегистрировать флаг " + FLAG_NAME + ": " + e.getMessage());
            return null;
        }
    }

    /** true — в точке, где стоит игрок, холод разрешён. */
    public boolean coldAllowed(Player p) {
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.testState(BukkitAdapter.adapt(p.getLocation()), WorldGuardPlugin.inst().wrapPlayer(p), flag);
    }
}
