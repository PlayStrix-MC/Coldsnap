package ru.pavel.coldsnap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /coldsnap — замена диалога датапака (Show / Hide / About).
 */
public final class ColdsnapCommand implements CommandExecutor, TabCompleter {

    private final ColdsnapPlugin plugin;

    public ColdsnapCommand(ColdsnapPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Settings s = plugin.settings();
        String sub = args.length > 0 ? args[0].toLowerCase() : "";

        if (sub.equals("reload")) {
            if (!sender.hasPermission("coldsnap.admin")) { msg(sender, s.msgNoPerm); return true; }
            String err = plugin.reload();
            if (err == null) msg(sender, plugin.settings().msgReloaded);
            else msg(sender, "<red>Ошибка в config.jmc, оставлены прежние настройки:</red> <gray>"
                    + err.replace("<", "\\<") + "</gray>");
            return true;
        }

        if (!(sender instanceof Player p)) { msg(sender, s.msgPlayersOnly); return true; }
        if (!p.hasPermission("coldsnap.use")) { msg(sender, s.msgNoPerm); return true; }

        switch (sub) {
            case "show" -> {
                p.removeScoreboardTag(ColdTask.HIDE_TAG);
                msg(p, s.msgShown);
            }
            case "hide" -> {
                p.addScoreboardTag(ColdTask.HIDE_TAG);
                msg(p, s.msgHidden);
            }
            case "status" -> status(p);
            default -> menu(p);
        }
        return true;
    }

    private void msg(CommandSender to, String text) {
        to.sendMessage(plugin.mm(plugin.settings().prefix + text));
    }

    private void menu(Player p) {
        Settings s = plugin.settings();
        Component title = Component.text("Coldsnap — индикатор холода", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
        Component legend = Component.text("Показывает, насколько вам холодно:\n", NamedTextColor.WHITE)
                .append(s.indWarm).append(Component.text(" не холодно\n", NamedTextColor.GRAY))
                .append(s.indChilly).append(Component.text(" прохладно\n", NamedTextColor.GRAY))
                .append(s.indFreezing).append(Component.text(" мороз\n", NamedTextColor.GRAY))
                .append(s.indFreezingDmg).append(Component.text(" — сейчас получите урон", NamedTextColor.GRAY));
        Component show = Component.text("[Показать]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/coldsnap show"))
                .hoverEvent(HoverEvent.showText(legend));
        Component hide = Component.text("[Скрыть]", NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/coldsnap hide"))
                .hoverEvent(HoverEvent.showText(Component.text("Скрыть индикатор для себя", NamedTextColor.GRAY)));
        Component stat = Component.text("[Статус]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/coldsnap status"))
                .hoverEvent(HoverEvent.showText(Component.text("Текущая температура", NamedTextColor.GRAY)));

        p.sendMessage(title);
        p.sendMessage(show.append(Component.text("  ")).append(hide).append(Component.text("  ")).append(stat));
    }

    private void status(Player p) {
        if (!plugin.coldAllowedHere(p)) {
            msg(p, "Вы в тёплом регионе — холод здесь отключён.");
            return;
        }
        ColdCalculator.Result r = new ColdCalculator.Result();
        ColdCalculator.computeFull(p, plugin.fur(), plugin.settings(), plugin.nexo(), r);
        String state = r.temperature <= ColdCalculator.FREEZING ? "<blue>мороз</blue>"
                : r.temperature <= ColdCalculator.CHILLY ? "<aqua>прохладно</aqua>" : "<gray>не холодно</gray>";
        String ins = r.insulationComputed ? String.valueOf(r.insulation) : "не требуется";
        msg(p, "Температура: <white>" + r.temperature + "</white> (" + state + ")"
                + " <dark_gray>|</dark_gray> Холод: <white>" + r.exposure + "</white>"
                + " <dark_gray>|</dark_gray> Утепление: <white>" + ins + "</white>"
                + " <dark_gray>|</dark_gray> Снежный биом: <white>" + (r.snowBiome ? "да" : "нет") + "</white>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a = args[0].toLowerCase();
            for (String opt : new String[]{"show", "hide", "status"}) if (opt.startsWith(a)) out.add(opt);
            if (sender.hasPermission("coldsnap.admin") && "reload".startsWith(a)) out.add("reload");
        }
        return out;
    }
}
