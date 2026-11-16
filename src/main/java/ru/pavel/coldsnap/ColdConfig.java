package ru.pavel.coldsnap;

import me.padej.jumper.interp.Config.Comment;

import java.util.List;
import java.util.Map;

/**
 * Схема config.jmc (формат Jumper, https://github.com/jumper-lang/jumper).
 * Значения по умолчанию — в {@link #DEFAULTS}. Комментарии @Comment попадают в файл,
 * когда плагин дописывает в него недостающие настройки.
 */
@Comment("Coldsnap — система холода. Порт датапака Coldsnap (AuraAcorn) на Paper/Purpur 1.21.11.\n"
        + "Снег и погода полностью ванильные: плагин их не меняет.\n"
        + "Формат файла — Jumper Config (.jmc). После правки: /coldsnap reload")
public record ColdConfig(
        @Comment("Как часто (в тиках) пересчитывать температуру игрока. 20 тиков = 1 секунда.\n"
                + "Игроки распределяются по разным тикам, нагрузка ровная. Скорость урона от этого не зависит.\n"
                + "Рекомендуется 5–20.")
        int updateInterval,

        @Comment("Кэш позиции: стоящий на месте игрок не пересчитывается (ни одного чтения блоков).\n"
                + "Раз в refreshCycles циклов расчёт всё равно делается заново (10 × 10 тиков = 5 секунд).")
        Cache cache,

        @Comment("Миры, где холод полностью выключен, например {\"world_nether\", \"world_the_end\"}.\n"
                + "Регионы WorldGuard: /rg flag <регион> coldsnap-cold deny")
        List<String> disabledWorlds,

        @Comment("Урон от холода. amount — в полусердечках.")
        Damage damage,

        @Comment("Индикатор холода в action bar (⬤). Игрок может скрыть его командой /coldsnap hide")
        boolean indicator,

        @Comment("Вид индикатора. symbol — символ (любой: ❄, ●, ■, 🌡, ☃ и т.д.).\n"
                + "Остальное — MiniMessage, {symbol} заменяется на символ.\n"
                + "warm — согрелся (мелькает один раз), chilly — прохладно (−5…−9), freezing — мороз (−10 и ниже),\n"
                + "chillyDamage / freezingDamage — в момент получения урона.")
        IndicatorStyle indicatorStyle,

        @Comment("Тепло от одежды (за одну вещь в слоте брони).\n"
                + "leather: 0 и fur: 0 — кожанка и мех не греют, греет только ваша броня.\n"
                + "nexoItems — броня Nexo: { winter_helmet: 3, winter_boots: 2 } (ID как в /nexo give).\n"
                + "Любая вещь с меткой PersistentData coldsnap:warmth (INTEGER) греет на это число.")
        Warmth warmth,

        @Comment("Меховая одежда: кожаная броня + белая шерсть на кузнечном столе (без шаблона).")
        FurArmor furArmor,

        @Comment("Дроп белой шерсти: шанс с козы и количество с белого медведя.")
        MobDrops mobDrops,

        @Comment("Сообщения. Поддерживается MiniMessage: <aqua>, <bold>, <#ff8800> и т.п.")
        Messages messages
) {

    public record Cache(boolean enabled, int refreshCycles) {}

    public record Damage(boolean enabled, double amount) {}

    public record IndicatorStyle(String symbol, String warm, String chilly, String freezing,
                                 String chillyDamage, String freezingDamage) {}

    public record Warmth(int leather, int fur, Map<String, Integer> nexoItems) {}

    public record FurArmor(boolean recipes, String helmet, String chestplate, String leggings, String boots) {}

    public record MobDrops(double goatWoolChance, int polarBearWool) {}

    public record Messages(String prefix, String shown, String hidden, String reloaded,
                           String playersOnly, String noPermission) {}

    public static final ColdConfig DEFAULTS = new ColdConfig(
            10,
            new Cache(true, 10),
            List.of(),
            new Damage(true, 1.0),
            true,
            new IndicatorStyle("\u2B24",
                    "<gray>{symbol}</gray>",
                    "<aqua>{symbol}</aqua>",
                    "<blue>{symbol}</blue>",
                    "<aqua>({symbol})</aqua>",
                    "<blue>({symbol})</blue>"),
            new Warmth(1, 2, Map.of()),
            new FurArmor(true, "Меховая шапка", "Меховая куртка", "Меховые штаны", "Меховые сапоги"),
            new MobDrops(0.5, 1),
            new Messages(
                    "<aqua>❄ Coldsnap</aqua> <dark_gray>»</dark_gray> ",
                    "Индикатор холода включён.",
                    "Индикатор холода скрыт.",
                    "Конфиг перезагружен.",
                    "Эта команда только для игроков.",
                    "Недостаточно прав."));
}
