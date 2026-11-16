package ru.pavel.coldsnap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Меховая броня: рецепты кузнечного стола и определение «утепляющих» вещей. */
public final class FurArmor {

    /** Цвет меха из датапака (dyed_color 16383998). */
    public static final int FUR_COLOR = 16383998;

    public enum Slot {
        HELMET(Material.LEATHER_HELMET, "fur_cap"),
        CHESTPLATE(Material.LEATHER_CHESTPLATE, "fur_tunic"),
        LEGGINGS(Material.LEATHER_LEGGINGS, "fur_pants"),
        BOOTS(Material.LEATHER_BOOTS, "fur_boots");

        final Material material;
        final String recipeId;

        Slot(Material material, String recipeId) {
            this.material = material;
            this.recipeId = recipeId;
        }
    }

    private final ColdsnapPlugin plugin;
    private final NamespacedKey insulatingKey;
    private final NamespacedKey warmthKey;
    private final List<NamespacedKey> registered = new ArrayList<>();

    public FurArmor(ColdsnapPlugin plugin) {
        this.plugin = plugin;
        this.insulatingKey = new NamespacedKey(plugin, "insulating");
        this.warmthKey = new NamespacedKey(plugin, "warmth");
    }

    private static TrimPattern pattern(Slot s) {
        return switch (s) {
            case HELMET -> TrimPattern.SENTRY;
            case CHESTPLATE -> TrimPattern.VEX;
            case LEGGINGS -> TrimPattern.SILENCE;
            case BOOTS -> TrimPattern.SNOUT;
        };
    }

    private static String name(Settings s, Slot slot) {
        return switch (slot) {
            case HELMET -> s.nameHelmet;
            case CHESTPLATE -> s.nameChest;
            case LEGGINGS -> s.nameLegs;
            case BOOTS -> s.nameBoots;
        };
    }

    public ItemStack createItem(Slot slot, Settings s) {
        ItemStack item = new ItemStack(slot.material);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.fromRGB(FUR_COLOR));
        }
        if (meta instanceof ArmorMeta armor) {
            armor.setTrim(new ArmorTrim(TrimMaterial.IRON, pattern(slot)));
        }
        meta.itemName(Component.text(name(s, slot)).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_DYE, ItemFlag.HIDE_ARMOR_TRIM);
        meta.getPersistentDataContainer().set(insulatingKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public void registerRecipes(Settings s) {
        unregisterRecipes();
        if (!s.furRecipes) return;
        Logger log = plugin.getLogger();
        for (Slot slot : Slot.values()) {
            NamespacedKey key = new NamespacedKey(plugin, slot.recipeId);
            if (Bukkit.getRecipe(key) != null) {
                log.warning("Рецепт " + key + " уже существует — похоже, датапак Coldsnap всё ещё установлен. "
                        + "Удалите датапак из папки мира/datapacks, иначе логика будет работать дважды.");
                continue;
            }
            SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                    key,
                    createItem(slot, s),
                    RecipeChoice.empty(),                              // без шаблона, как в датапаке
                    new RecipeChoice.MaterialChoice(slot.material),
                    new RecipeChoice.MaterialChoice(Material.WHITE_WOOL));
            try {
                if (Bukkit.addRecipe(recipe)) registered.add(key);
            } catch (RuntimeException e) {
                log.warning("Не удалось зарегистрировать рецепт " + key + ": " + e.getMessage());
            }
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : registered) Bukkit.removeRecipe(key);
        registered.clear();
    }

    /**
     * Сколько тепла даёт вещь в слоте брони. Порядок проверки:
     *  1) метка coldsnap:warmth (число) — любая вещь, в т.ч. из Nexo/ItemsAdder/команды;
     *  2) ID предмета Nexo из списка warmth.nexo-items в конфиге;
     *  3) меховая вещь плагина (метка coldsnap:insulating) или датапака (вид) — warmth.fur;
     *  4) обычная кожаная — warmth.leather.
     */
    public int warmth(ItemStack item, Slot slot, Settings s, NexoHook nexo) {
        if (item == null || item.getType().isAir()) return 0;
        boolean leather = item.getType() == slot.material;
        if (!item.hasItemMeta()) return leather ? s.leatherWarmth : 0;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        try {
            Integer w = pdc.get(warmthKey, PersistentDataType.INTEGER);
            if (w != null) return w;
        } catch (RuntimeException ignored) {
            // метка другого типа — игнорируем
        }

        if (nexo != null && !s.nexoWarmth.isEmpty()) {
            String id = nexo.idOf(item, meta);
            if (id != null) {
                Integer w = s.nexoWarmth.get(id);
                if (w != null) return w;
            }
        }

        if (pdc.has(insulatingKey)) return s.furWarmth;
        if (leather && isLegacyFur(meta, slot)) return s.furWarmth;
        return leather ? s.leatherWarmth : 0;
    }

    /** Вещь, скрафченная датапаком: белый цвет + железная отделка нужного узора. */
    private boolean isLegacyFur(ItemMeta meta, Slot slot) {
        if (!(meta instanceof LeatherArmorMeta leather) || !(meta instanceof ArmorMeta armor)) return false;
        Color c = leather.getColor();
        if (c == null || c.asRGB() != FUR_COLOR || !armor.hasTrim()) return false;
        ArmorTrim trim = armor.getTrim();
        return trim != null && TrimMaterial.IRON.equals(trim.getMaterial()) && pattern(slot).equals(trim.getPattern());
    }
}
