package ru.pavel.coldsnap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import me.padej.jumper.interp.Config;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ColdsnapPlugin extends JavaPlugin {

    private final Map<UUID, PlayerState> states = new HashMap<>();
    private volatile Settings settings;
    private FurArmor fur;
    private BukkitTask coldTask;
    private WorldGuardHook worldGuard;
    private NexoHook nexo;

    @Override
    public void onLoad() {
        // Флаг региона нужно регистрировать до включения WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                worldGuard = WorldGuardHook.register(getLogger());
            } catch (Throwable t) {
                getLogger().warning("WorldGuard найден, но интеграция не удалась: " + t);
                worldGuard = null;
            }
        }
    }

    @Override
    public void onEnable() {
        settings = new Settings(loadConfig(null));
        fur = new FurArmor(this);
        try {
            nexo = NexoHook.create(getLogger());
        } catch (Throwable t) {
            nexo = null;
        }
        fur.registerRecipes(settings);

        Bukkit.getPluginManager().registerEvents(new ColdListener(this), this);

        ColdsnapCommand cmd = new ColdsnapCommand(this);
        PluginCommand pc = getCommand("coldsnap");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }

        startTasks();
        restoreVanillaSnow();
        getLogger().info("Coldsnap включён. Интервал пересчёта: " + settings.interval + " тик(ов).");
    }

    @Override
    public void onDisable() {
        if (coldTask != null) coldTask.cancel();
        if (fur != null) fur.unregisterRecipes();
        states.clear();
    }

    /** @return null — успешно, иначе текст ошибки (старые настройки остаются). */
    public String reload() {
        String[] error = new String[1];
        ColdConfig cfg = loadConfig(error);
        if (cfg == null) return error[0];
        settings = new Settings(cfg);
        fur.registerRecipes(settings);
        for (PlayerState st : states.values()) {
            st.armorDirty = true;
            st.cacheValid = false;
        }
        startTasks();
        return null;
    }

    /**
     * Читает plugins/Coldsnap/config.jmc (Jumper Config). Нет файла — создаёт его
     * (из старого config.yml, если он есть). Недостающие настройки дописываются в файл с комментариями.
     * При ошибке: на старте — значения по умолчанию, при /coldsnap reload — null и текст ошибки.
     */
    private ColdConfig loadConfig(String[] errorOut) {
        File folder = getDataFolder();
        folder.mkdirs();
        Path jmc = new File(folder, "config.jmc").toPath();
        File yml = new File(folder, "config.yml");
        try {
            if (!Files.exists(jmc) && yml.isFile()) {
                ColdConfig migrated = Settings.fromYaml(YamlConfiguration.loadConfiguration(yml));
                Config.write(jmc, migrated);
                File old = new File(folder, "config.yml.old");
                if (yml.renameTo(old)) {
                    getLogger().info("config.yml перенесён в config.jmc (старый файл: config.yml.old).");
                }
            }
            return Config.loadOrCreate(jmc, ColdConfig.DEFAULTS);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            getLogger().severe("Ошибка в config.jmc: " + msg);
            if (errorOut != null) {
                errorOut[0] = msg;
                return null;
            }
            getLogger().severe("Использую настройки по умолчанию. Исправьте файл и введите /coldsnap reload.");
            return ColdConfig.DEFAULTS;
        }
    }

    /**
     * Датапак Coldsnap менял max_snow_accumulation_height (1 ↔ 2). Плагин снег не трогает,
     * но один раз возвращает ванильное значение 1, если датапак оставил 2.
     * После этого создаётся файл-метка, и правило больше никогда не трогается.
     */
    private void restoreVanillaSnow() {
        File marker = new File(getDataFolder(), ".vanilla-snow-restored");
        if (marker.exists()) return;
        for (World w : Bukkit.getWorlds()) {
            Integer cur = w.getGameRuleValue(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
            if (cur != null && cur != 1) {
                w.setGameRule(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT, 1);
                getLogger().info("Мир " + w.getName() + ": max_snow_accumulation_height " + cur + " -> 1 (ванильный снег)");
            }
        }
        try {
            getDataFolder().mkdirs();
            marker.createNewFile();
        } catch (IOException e) {
            getLogger().warning("Не удалось создать " + marker + ": " + e.getMessage());
        }
    }

    private void startTasks() {
        if (coldTask != null) coldTask.cancel();
        coldTask = Bukkit.getScheduler().runTaskTimer(this, new ColdTask(this), 1L, 1L);
    }

    public Settings settings() {
        return settings;
    }

    /** false — игрок в регионе WorldGuard с флагом coldsnap-cold deny. */
    public boolean coldAllowedHere(Player p) {
        WorldGuardHook wg = worldGuard;
        if (wg == null) return true;
        try {
            return wg.coldAllowed(p);
        } catch (Throwable t) {
            return true;
        }
    }

    public NexoHook nexo() {
        return nexo;
    }

    public FurArmor fur() {
        return fur;
    }

    public PlayerState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new PlayerState());
    }

    public PlayerState stateIfPresent(Player p) {
        return states.get(p.getUniqueId());
    }

    public void removeState(Player p) {
        states.remove(p.getUniqueId());
    }

    public Component mm(String text) {
        return MiniMessage.miniMessage().deserialize(text);
    }
}
