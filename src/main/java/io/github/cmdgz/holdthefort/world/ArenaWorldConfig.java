package io.github.cmdgz.holdthefort.world;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;

import io.github.cmdgz.holdthefort.map.MapDefinition;

/**
 * 对**已存在的**战场世界做局部配置（不创建世界、不卸载世界）。
 *
 * <p>世界由服务器管理员或世界管理插件负责，本插件只按小游戏的需要调整规则。
 * 全部设置都可以被关闭（{@code match.configure_world: false}），以便与其它玩法共用一个世界。
 *
 * <p>本类不出现任何世界名 —— 具体是哪个世界由地图文件声明。
 */
public final class ArenaWorldConfig {

    private ArenaWorldConfig() {
    }

    /**
     * 把战场世界调成「适合打一局」的状态。
     *
     * @param world 地图声明的战场世界
     * @param map   地图定义，用它的出生点作为世界出生点
     */
    public static void apply(World world, MapDefinition map) {
        // 时间与天气固定，免得打着打着天黑下雨
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);

        // 竞技场常规：不刷野怪、不破坏地形、不掉落
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SPAWN_RADIUS, 0);

        // 重生等待由插件自己管（§7），不要用立即重生
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);

        world.setDifficulty(Difficulty.NORMAL);
        // 注意：PvP 自 1.21.9 起改由游戏规则控制，World#setPVP 已废弃且不再影响伤害。
        // 本插件**不**替世界打开 PvP —— 那是世界归属方（如 GameWorlds）的职责。

        // 世界出生点放到战线起点，方便从世界列表进入时落到场地中央
        world.setSpawnLocation(
                (int) Math.floor(map.redSpawn().x()),
                (int) Math.floor(map.redSpawn().y()),
                (int) Math.floor(map.redSpawn().z()));
    }
}
