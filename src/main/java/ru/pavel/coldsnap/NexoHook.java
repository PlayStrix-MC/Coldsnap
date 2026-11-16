package ru.pavel.coldsnap;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Узнаёт ID предмета Nexo. Без жёсткой зависимости от Nexo:
 * сначала официальный API NexoItems.idFromItem(ItemStack) через отражение,
 * если не вышло — метка nexo:id в PublicBukkitValues предмета.
 */
public final class NexoHook {

    private static final NamespacedKey NEXO_ID = new NamespacedKey("nexo", "id");

    private final MethodHandle idFromItem;

    private NexoHook(MethodHandle idFromItem) {
        this.idFromItem = idFromItem;
    }

    /** null — Nexo не установлен. */
    public static NexoHook create(java.util.logging.Logger log) {
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) return null;
        MethodHandle mh = null;
        try {
            Class<?> c = Class.forName("com.nexomc.nexo.api.NexoItems");
            mh = MethodHandles.publicLookup().findStatic(c, "idFromItem",
                    MethodType.methodType(String.class, ItemStack.class));
            log.info("Nexo найден: тепло для предметов Nexo включено.");
        } catch (Throwable t) {
            log.info("Nexo найден, API недоступен — ID предметов читаются из метки nexo:id.");
        }
        return new NexoHook(mh);
    }

    public String idOf(ItemStack item, ItemMeta meta) {
        if (idFromItem != null) {
            try {
                Object id = idFromItem.invoke(item);
                if (id != null) return (String) id;
            } catch (Throwable ignored) {
                // упадём на метку
            }
        }
        try {
            return meta.getPersistentDataContainer().get(NEXO_ID, PersistentDataType.STRING);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
