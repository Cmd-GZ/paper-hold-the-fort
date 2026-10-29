package io.github.cmdgz.holdthefort.map;

import java.util.ArrayList;
import java.util.List;

import io.github.cmdgz.holdthefort.frontline.FrontlineChain;
import io.github.cmdgz.holdthefort.frontline.FrontlineException;
import io.github.cmdgz.holdthefort.frontline.Keyframe;
import io.github.cmdgz.holdthefort.frontline.Zone;

/**
 * 地图校验（§2.4）。
 *
 * <p>分两档：**L 级会拒绝加载**（数据本身说不通），**W 级只是提示**（地图好不好玩、
 * 开不开局平衡，不属于插件的职责）。校验通过时顺带把战线链建好，避免调用方再建一次。
 */
public final class MapValidator {

    /** 提示用的阈值：单段超过全程的这个比例就提醒一句（W1）。 */
    private static final double LONG_SEGMENT_RATIO = 0.25;

    /** 提示用的阈值：一条战线短于这个长度就提醒一句（W2）。 */
    private static final double SHORT_FRONTLINE = 16.0;

    public enum Severity {
        /** L 级：拒绝加载。 */
        ERROR,
        /** W 级：只提示。 */
        WARNING
    }

    public record Problem(Severity severity, String message) {
        @Override
        public String toString() {
            return (severity == Severity.ERROR ? "[L] " : "[W] ") + message;
        }
    }

    /**
     * 校验结果。{@code chain} 只有在没有 L 级问题时才非空。
     */
    public record Result(List<Problem> problems, FrontlineChain chain) {

        public boolean hasErrors() {
            return problems.stream().anyMatch(p -> p.severity() == Severity.ERROR);
        }

        public List<Problem> of(Severity severity) {
            return problems.stream().filter(p -> p.severity() == severity).toList();
        }
    }

    private MapValidator() {
    }

    public static Result validate(MapDefinition map) {
        final List<Problem> problems = new ArrayList<>();

        final int count = map.checkpoints().size();

        // L4：至少三帧，否则起点帧必定在两端
        if (count < 3) {
            return reject(problems, "至少需要 3 个关键帧，实际 " + count);
        }

        // L5：start_index 必须落在 1..N-2
        if (map.startIndex() < 1 || map.startIndex() > count - 2) {
            return reject(problems, "start_index 必须落在 1.." + (count - 2) + "，实际是 " + map.startIndex());
        }

        // 按列表位置换算编号：第 start_index 项编号为 0
        final List<Keyframe> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final CheckpointPair pair = map.checkpoints().get(i);
            final int index = i - map.startIndex();
            try {
                frames.add(new Keyframe(index, pair.p(), pair.q()));
            } catch (IllegalArgumentException e) {
                // L2：两个检查点重合
                return reject(problems, "第 " + i + " 项（编号 " + index + "）：" + e.getMessage());
            }
        }

        final FrontlineChain chain;
        try {
            // L1 / L3 / L7 都在这里检查
            chain = FrontlineChain.build(frames, map.redSpawn().horizontal(), map.epsilon());
        } catch (FrontlineException e) {
            return reject(problems, e.getMessage());
        }

        // ---- 以下都只是提示 ----

        // W1：某段特别长
        final double total = chain.totalLength();
        for (int k = chain.from(); k < chain.to(); k++) {
            final double w = chain.weight(k);
            if (total > 0 && w > total * LONG_SEGMENT_RATIO) {
                problems.add(new Problem(Severity.WARNING,
                        "第 " + k + "→" + (k + 1) + " 段占全程 "
                                + Math.round(w / total * 100) + "%，该段会明显更耗时"));
            }
        }

        // W2：战线太短
        for (Keyframe f : frames) {
            final double length = f.halfLength() * 2;
            if (length < SHORT_FRONTLINE) {
                problems.add(new Problem(Severity.WARNING,
                        "第 " + f.index() + " 帧的战线只有 " + Math.round(length) + " 格，判定范围会很小"));
            }
        }

        // W3 / W4：开局站位
        final Zone redZone = chain.zoneAt(map.redSpawn().horizontal(), 0);
        final Zone blueZone = chain.zoneAt(map.blueSpawn().horizontal(), 0);

        if (redZone == Zone.OUT_OF_RANGE && blueZone == Zone.OUT_OF_RANGE) {
            problems.add(new Problem(Severity.WARNING,
                    "开局时两队出生点都在判定范围外，对局会从「接近阶段」开始"));
        } else if (redZone == blueZone && (redZone == Zone.BLUE || redZone == Zone.RED)) {
            final String side = redZone == Zone.BLUE ? "蓝方区域" : "红方区域";
            problems.add(new Problem(Severity.WARNING,
                    "开局时两队出生点都落在" + side + "，其中一队会立刻被算成入侵者"));
        }

        return new Result(List.copyOf(problems), chain);
    }

    private static Result reject(List<Problem> problems, String message) {
        problems.add(new Problem(Severity.ERROR, message));
        return new Result(List.copyOf(problems), null);
    }
}
