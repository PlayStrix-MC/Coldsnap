package ru.pavel.coldsnap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Set;

/**
 * Точный перенос логики cold_score_calc.mcfunction и предикатов датапака.
 *
 * Оптимизации по сравнению с датапаком:
 *  - всё считается за один проход, без десятков повторных проверок предикатов;
 *  - «укрытость» (covered_above / covered_around) проверяется только в снежном биоме;
 *  - утепление (броня, свет, 27 блоков магмы) считается только если игроку вообще может
 *    быть холодно: утепление всегда >= 0, поэтому при exposure > -5 температура точно > -5.
 */
public final class ColdCalculator {

    private static final Set<Biome> ALWAYS_COLD = Set.of(
            Biome.ICE_SPIKES, Biome.SNOWY_BEACH, Biome.SNOWY_PLAINS, Biome.SNOWY_SLOPES,
            Biome.SNOWY_TAIGA, Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN, Biome.FROZEN_PEAKS,
            Biome.FROZEN_RIVER, Biome.JAGGED_PEAKS);
    private static final Set<Biome> WINDSWEPT = Set.of(
            Biome.WINDSWEPT_GRAVELLY_HILLS, Biome.WINDSWEPT_HILLS, Biome.WINDSWEPT_FOREST, Biome.STONY_SHORE);
    private static final Set<Biome> TAIGA = Set.of(Biome.TAIGA, Biome.OLD_GROWTH_SPRUCE_TAIGA);

    /** Порог «холодно» из датапака. */
    public static final int CHILLY = -5;
    /** Порог «мороз» из датапака. */
    public static final int FREEZING = -10;

    private ColdCalculator() {}

    /** Результат расчёта (переиспользуется, без лишних аллокаций). */
    public static final class Result {
        public boolean snowBiome;
        public boolean coveredAbove;
        public boolean coveredAround;
        public boolean rain;
        public boolean thunder;
        public int exposure;
        public int insulation;
        public int temperature;
        /** true, если утепление реально считали (а не пропустили как ненужное). */
        public boolean insulationComputed;
    }

    /** predicate coldsnap:snow_biome */
    public static boolean isSnowBiome(World w, double x, double y, double z) {
        int bx = floor(x), by = floor(y), bz = floor(z);
        if (by < w.getMinHeight() || by >= w.getMaxHeight()) return false;
        Biome b = w.getComputedBiome(bx, by, bz);
        if (ALWAYS_COLD.contains(b)) return true;
        if (WINDSWEPT.contains(b)) return y >= 113 && y <= 128;
        if (TAIGA.contains(b)) return y >= 153 && y <= 168;
        if (b == Biome.OLD_GROWTH_PINE_TAIGA) return y >= 193 && y <= 208;
        return false;
    }

    /** Полный расчёт без кэшей и ранних выходов (для /coldsnap status). */
    public static void computeFull(Player p, FurArmor fur, Settings cfg, NexoHook nexo, Result r) {
        compute(p, fur, cfg, nexo, r, null, 0);
    }

    /**
     * Расчёт для главного цикла.
     * @param st     состояние игрока с кэшами (null — без кэшей, полный расчёт)
     * @param maxAge через сколько циклов кэш позиции принудительно обновляется
     */
    public static void compute(Player p, FurArmor fur, Settings cfg, NexoHook nexo, Result r, PlayerState st, int maxAge) {
        boolean full = st == null;
        Location loc = p.getLocation();
        World w = loc.getWorld();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        boolean rain = w.hasStorm();
        boolean thunder = w.isThundering();
        long time = w.getTime() % 24000L;
        boolean night = time >= 12786 && time <= 23216;
        boolean onFire = p.getFireTicks() > 0;

        // ---------- Оптимизация 1: кэш позиции ----------
        // Игрок стоит в том же блоке, погода/время суток/броня/огонь не менялись —
        // результат тот же, ни одного блока не читаем.
        if (!full) {
            if (st.cacheValid && st.cacheWorld == w && st.cacheX == x && st.cacheY == y && st.cacheZ == z
                    && st.cacheRain == rain && st.cacheThunder == thunder && st.cacheNight == night
                    && st.cacheOnFire == onFire && !st.armorDirty && st.cacheAge < maxAge) {
                st.cacheAge++;
                return; // r == st.last, значения уже на месте
            }
            st.cacheValid = true;
            st.cacheWorld = w;
            st.cacheX = x; st.cacheY = y; st.cacheZ = z;
            st.cacheRain = rain; st.cacheThunder = thunder; st.cacheNight = night; st.cacheOnFire = onFire;
            st.cacheAge = 0;
        }

        r.rain = rain;
        r.thunder = thunder;
        r.coveredAbove = false;
        r.coveredAround = false;
        r.insulation = 0;
        r.insulationComputed = false;

        // ---------- Оптимизация 2: Незер и Энд ----------
        // Снежных биомов там нет, максимум холода −2 (босиком по льду) — замёрзнуть невозможно.
        if (!full && w.getEnvironment() != World.Environment.NORMAL) {
            r.snowBiome = false;
            r.exposure = 0;
            r.temperature = 0;
            return;
        }

        r.snowBiome = isSnowBiome(w, loc.getX(), loc.getY(), loc.getZ());
        PlayerInventory inv = p.getInventory();

        // ---------- Exposure ----------
        int exposure = 0;
        if (r.snowBiome) exposure -= 2;
        ItemStack boots = inv.getBoots();
        if (isEmpty(boots) && coldFeet(w, x, y, z)) exposure -= 2;

        if (r.snowBiome) {
            r.coveredAbove = coveredAbove(w, x, y, z);
            r.coveredAround = coveredAround(w, x, y, z, r.coveredAbove);
            if (!r.coveredAbove) {
                exposure -= 1;
                if (rain) exposure -= 1;
                if (thunder) exposure -= 2;
                if (night) exposure -= 1;
            }
            if (!r.coveredAround) {
                exposure -= 1;
                if (rain) exposure -= 1;
                if (thunder) exposure -= 2;
                if (night) exposure -= 1;
            }
            exposure += waterPenalty(w.getBlockAt(x, y, z));
            exposure += waterPenalty(w.getBlockAt(x, y + 1, z));
        }
        r.exposure = exposure;

        int temp = onFire ? 0 : exposure;

        // Утепление >= 0: если уже теплее −5, игроку точно не холодно.
        if (temp > CHILLY && !full) {
            r.temperature = temp;
            return;
        }

        // ---------- Insulation, от дешёвого к дорогому, с ранним выходом ----------
        int ins = 0;

        // Оптимизация 3: броня считается заново только после смены брони (PlayerArmorChangeEvent)
        int armor;
        if (!full && !st.armorDirty) {
            armor = st.armorInsulation;
        } else {
            armor = fur.warmth(boots, FurArmor.Slot.BOOTS, cfg, nexo)
                    + fur.warmth(inv.getLeggings(), FurArmor.Slot.LEGGINGS, cfg, nexo)
                    + fur.warmth(inv.getChestplate(), FurArmor.Slot.CHESTPLATE, cfg, nexo)
                    + fur.warmth(inv.getHelmet(), FurArmor.Slot.HELMET, cfg, nexo);
            if (!full) {
                st.armorInsulation = armor;
                st.armorDirty = false;
            }
        }
        ins += armor;

        if (!full && temp + ins > CHILLY) {
            r.insulation = ins;
            r.temperature = temp + ins;
            return;
        }

        // Свет (1 блок): >=10 → +1 ... >=14 → +5, ровно 15 → +6
        int light = w.getBlockAt(x, y, z).getLightLevel();
        if (light >= 10) ins += Math.min(light, 14) - 9 + (light >= 15 ? 1 : 0);

        if (!full && temp + ins > CHILLY) {
            r.insulation = ins;
            r.temperature = temp + ins;
            return;
        }

        // Магма в кубе 3x3x3 (27 блоков) — самое дорогое, поэтому последним и с ранним выходом
        outer:
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (w.getBlockAt(x + dx, y + dy, z + dz).getType() == Material.MAGMA_BLOCK) {
                        ins++;
                        if (!full && temp + ins > CHILLY) break outer;
                    }
                }
            }
        }

        r.insulation = ins;
        r.insulationComputed = true;
        r.temperature = temp + ins;
    }

    // predicate coldsnap:cold_feet (часть про блоки; пустые ботинки проверяются снаружи)
    private static boolean coldFeet(World w, int x, int y, int z) {
        Material at = w.getBlockAt(x, y, z).getType();
        if (at == Material.POWDER_SNOW || at == Material.SNOW || at == Material.POWDER_SNOW_CAULDRON) return true;
        Material below = w.getBlockAt(x, y - 1, z).getType();
        return below == Material.SNOW_BLOCK || below == Material.ICE || below == Material.PACKED_ICE;
    }

    // can_see_sky == false  <=>  небесный свет < 15
    private static boolean covered(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).getLightFromSky() < 15;
    }

    // predicate coldsnap:covered_above (точно как в датапаке, включая повтор смещения -1,1,0)
    private static boolean coveredAbove(World w, int x, int y, int z) {
        return covered(w, x, y, z)
                && covered(w, x, y + 1, z + 1)
                && covered(w, x, y + 1, z - 1)
                && covered(w, x - 1, y + 1, z);
    }

    // predicate coldsnap:covered_around
    private static boolean coveredAround(World w, int x, int y, int z, boolean coveredAbove) {
        if (covered(w, x + 1, y + 1, z + 1) && covered(w, x + 1, y + 1, z - 1)
                && covered(w, x - 1, y + 1, z + 1) && covered(w, x - 1, y + 1, z - 1)) {
            return true;
        }
        if (!coveredAbove) return false;
        boolean allAir = w.getBlockAt(x, y, z + 1).getType().isAir()
                && w.getBlockAt(x, y, z - 1).getType().isAir()
                && w.getBlockAt(x + 1, y, z).getType().isAir()
                && w.getBlockAt(x - 1, y, z).getType().isAir();
        return !allAir;
    }

    // вода: -3, если источник — ещё -5
    private static int waterPenalty(Block b) {
        if (b.getType() != Material.WATER) return 0;
        BlockData d = b.getBlockData();
        if (d instanceof Levelled l && l.getLevel() == 0) return -8;
        return -3;
    }

    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
