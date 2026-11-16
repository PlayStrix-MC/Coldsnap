package ru.pavel.coldsnap;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.Set;

/** Снимок конфига: читаем один раз, а не в каждом тике. */
public final class Settings {

    public final int interval;
    public final Set<String> disabledWorlds;
    public final boolean cacheEnabled;
    public final int cacheMaxAge;

    public final boolean damageEnabled;
    public final double damageAmount;

    public final boolean indicatorEnabled;
    public final String indicatorSymbol;
    public final net.kyori.adventure.text.Component indWarm, indChilly, indFreezing, indChillyDmg, indFreezingDmg;


    public final int leatherWarmth;
    public final int furWarmth;
    public final java.util.Map<String, Integer> nexoWarmth;

    public final boolean furRecipes;
    public final String nameHelmet, nameChest, nameLegs, nameBoots;

    public final double goatWoolChance;
    public final int polarBearWool;

    public final String prefix, msgShown, msgHidden, msgReloaded, msgPlayersOnly, msgNoPerm;

    public Settings(ColdConfig c) {
        ColdConfig d = ColdConfig.DEFAULTS;
        interval = clamp(c.updateInterval(), 1, 100);
        disabledWorlds = new HashSet<>(or(c.disabledWorlds(), d.disabledWorlds()));
        ColdConfig.Cache cache = or(c.cache(), d.cache());
        cacheEnabled = cache.enabled();
        cacheMaxAge = clamp(cache.refreshCycles(), 1, 1000);

        ColdConfig.Damage dmg = or(c.damage(), d.damage());
        damageEnabled = dmg.enabled();
        damageAmount = Math.max(0.0, dmg.amount());

        indicatorEnabled = c.indicator();
        ColdConfig.IndicatorStyle is = or(c.indicatorStyle(), d.indicatorStyle());
        ColdConfig.IndicatorStyle idf = d.indicatorStyle();
        indicatorSymbol = or(is.symbol(), idf.symbol());
        indWarm = indicator(or(is.warm(), idf.warm()));
        indChilly = indicator(or(is.chilly(), idf.chilly()));
        indFreezing = indicator(or(is.freezing(), idf.freezing()));
        indChillyDmg = indicator(or(is.chillyDamage(), idf.chillyDamage()));
        indFreezingDmg = indicator(or(is.freezingDamage(), idf.freezingDamage()));

        ColdConfig.Warmth w = or(c.warmth(), d.warmth());
        leatherWarmth = clamp(w.leather(), 0, 100);
        furWarmth = clamp(w.fur(), 0, 100);
        java.util.Map<String, Integer> nexo = new java.util.HashMap<>();
        if (w.nexoItems() != null) {
            w.nexoItems().forEach((id, v) -> { if (id != null && v != null) nexo.put(id, clamp(v, -100, 100)); });
        }
        nexoWarmth = java.util.Collections.unmodifiableMap(nexo);

        ColdConfig.FurArmor fa = or(c.furArmor(), d.furArmor());
        ColdConfig.FurArmor fd = d.furArmor();
        furRecipes = fa.recipes();
        nameHelmet = or(fa.helmet(), fd.helmet());
        nameChest = or(fa.chestplate(), fd.chestplate());
        nameLegs = or(fa.leggings(), fd.leggings());
        nameBoots = or(fa.boots(), fd.boots());

        ColdConfig.MobDrops md = or(c.mobDrops(), d.mobDrops());
        goatWoolChance = Math.max(0.0, Math.min(1.0, md.goatWoolChance()));
        polarBearWool = clamp(md.polarBearWool(), 0, 64);

        ColdConfig.Messages m = or(c.messages(), d.messages());
        ColdConfig.Messages md2 = d.messages();
        prefix = or(m.prefix(), md2.prefix());
        msgShown = or(m.shown(), md2.shown());
        msgHidden = or(m.hidden(), md2.hidden());
        msgReloaded = or(m.reloaded(), md2.reloaded());
        msgPlayersOnly = or(m.playersOnly(), md2.playersOnly());
        msgNoPerm = or(m.noPermission(), md2.noPermission());
    }

    /**
     * Перенос старого config.yml (версии до 1.5.0) в новую схему.
     */
    public static ColdConfig fromYaml(FileConfiguration c) {
        ColdConfig d = ColdConfig.DEFAULTS;
        java.util.Map<String, Integer> nexo = new java.util.LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection sec = c.getConfigurationSection("warmth.nexo-items");
        if (sec != null) for (String id : sec.getKeys(false)) nexo.put(id, sec.getInt(id, 0));
        return new ColdConfig(
                c.getInt("update-interval", d.updateInterval()),
                new ColdConfig.Cache(c.getBoolean("cache.enabled", true), c.getInt("cache.refresh-cycles", 10)),
                c.getStringList("disabled-worlds"),
                new ColdConfig.Damage(c.getBoolean("damage.enabled", true), c.getDouble("damage.amount", 1.0)),
                c.getBoolean("indicator.enabled", true),
                d.indicatorStyle(),
                new ColdConfig.Warmth(c.getInt("warmth.leather", 1), c.getInt("warmth.fur", 2), nexo),
                new ColdConfig.FurArmor(c.getBoolean("fur-armor.recipes", true),
                        c.getString("fur-armor.names.helmet", d.furArmor().helmet()),
                        c.getString("fur-armor.names.chestplate", d.furArmor().chestplate()),
                        c.getString("fur-armor.names.leggings", d.furArmor().leggings()),
                        c.getString("fur-armor.names.boots", d.furArmor().boots())),
                new ColdConfig.MobDrops(c.getDouble("mob-drops.goat-wool-chance", 0.5), c.getInt("mob-drops.polar-bear-wool", 1)),
                new ColdConfig.Messages(
                        c.getString("messages.prefix", d.messages().prefix()),
                        c.getString("messages.shown", d.messages().shown()),
                        c.getString("messages.hidden", d.messages().hidden()),
                        c.getString("messages.reloaded", d.messages().reloaded()),
                        c.getString("messages.players-only", d.messages().playersOnly()),
                        c.getString("messages.no-permission", d.messages().noPermission())));
    }

    /** Разбирается один раз при загрузке конфига, а не при каждой отправке. */
    private net.kyori.adventure.text.Component indicator(String template) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(template.replace("{symbol}", indicatorSymbol));
    }

    private static <T> T or(T v, T def) {
        return v != null ? v : def;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
