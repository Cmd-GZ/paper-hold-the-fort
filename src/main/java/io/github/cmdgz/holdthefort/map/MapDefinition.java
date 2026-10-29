package io.github.cmdgz.holdthefort.map;

import java.util.List;

/**
 * 一张地图的原始定义，对应 §8.2 的地图文件。
 *
 * <p>这里只做数据承载，**所有校验都在 {@link MapValidator}**；缺省值（速度、中性带）
 * 由调用方在构造前从全局配置合并进来。
 *
 * @param name         地图名
 * @param world        所在地图的世界名；只有该世界的玩家参与计数
 * @param redSpawn     红方出生点，同时决定「哪边是红方」（§6.2 第一步）
 * @param blueSpawn    蓝方出生点
 * @param turnLimit    转向模式
 * @param speed        物理速度 $V_0$（格/秒）
 * @param epsilon      中性带半宽（格）
 * @param startIndex   起点帧在 {@code checkpoints} 里是第几项（从 0 数）
 * @param checkpoints  关键帧坐标列表，顺序即编号
 */
public record MapDefinition(
        String name,
        String world,
        Spawn redSpawn,
        Spawn blueSpawn,
        TurnLimit turnLimit,
        double speed,
        double epsilon,
        int startIndex,
        List<CheckpointPair> checkpoints) {
}
