package io.github.cmdgz.holdthefort.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import io.github.cmdgz.holdthefort.HoldTheFortPlugin;

/**
 * 排查用：把玩家之间的每一次伤害尝试打到日志里。
 *
 * <p>在 {@code MONITOR} 优先级记录，能看到**所有插件处理完之后**的最终结果
 * （{@code cancelled} 为 true 就说明有插件取消了它）。默认关闭。
 */
public final class DamageDebugListener implements Listener {

    private final HoldTheFortPlugin plugin;

    public DamageDebugListener(HoldTheFortPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        plugin.getLogger().info("[伤害调试] " + attacker.getName()
                + "（" + plugin.teamOf(attacker) + "，游戏模式 " + attacker.getGameMode() + "）"
                + " → " + victim.getName()
                + "（" + plugin.teamOf(victim) + "）"
                + " 世界=" + victim.getWorld().getName()
                + " pvp规则=" + victim.getWorld().getGameRuleValue(org.bukkit.GameRule.PVP)
                + " 伤害=" + String.format("%.2f", event.getDamage())
                + " 最终取消=" + event.isCancelled());
    }
}
