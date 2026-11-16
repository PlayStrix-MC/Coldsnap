package ru.pavel.coldsnap;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public final class ColdListener implements Listener {

    private final ColdsnapPlugin plugin;

    public ColdListener(ColdsnapPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- Дроп шерсти (замена loot_table goat / polar_bear) ----------
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        EntityType type = ent.getType();
        if (type != EntityType.GOAT && type != EntityType.POLAR_BEAR) return;
        if (ent instanceof Ageable a && !a.isAdult()) return; // детёныши лут не роняют
        if (Boolean.FALSE.equals(ent.getWorld().getGameRuleValue(GameRules.MOB_DROPS))) return;
        Settings s = plugin.settings();
        int amount = 0;
        if (type == EntityType.GOAT) {
            if (s.goatWoolChance > 0 && ThreadLocalRandom.current().nextDouble() < s.goatWoolChance) amount = 1;
        } else {
            amount = s.polarBearWool;
        }
        if (amount > 0) e.getDrops().add(new ItemStack(Material.WHITE_WOOL, amount));
    }

    // Броня сменилась — пересчитать утепление на следующем цикле
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent e) {
        PlayerState st = plugin.stateIfPresent(e.getPlayer());
        if (st != null) st.armorDirty = true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.removeState(e.getPlayer());
    }
}
