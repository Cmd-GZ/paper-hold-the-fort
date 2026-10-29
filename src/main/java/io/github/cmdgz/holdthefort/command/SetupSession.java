package io.github.cmdgz.holdthefort.command;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;

import io.github.cmdgz.holdthefort.frontline.Vec2;
import io.github.cmdgz.holdthefort.map.CheckpointPair;
import io.github.cmdgz.holdthefort.map.Spawn;

/**
 * 一次游戏内建场的进度（{@code /htf setup}）。
 *
 * <p>操作者用标尺木棍点两下 = 一帧；再单独记两个出生点，最后 {@code done} 落盘。
 */
public final class SetupSession {

    private final String worldName;
    private final List<CheckpointPair> frames = new ArrayList<>();

    /** 已点到、还没配上对的第一个点。 */
    private Vec2 pending;

    private Spawn redSpawn;
    private Spawn blueSpawn;
    private int startIndex = -1;

    public SetupSession(String worldName) {
        this.worldName = worldName;
    }

    public String worldName() {
        return worldName;
    }

    /** 记录一个检查点。成对时返回刚组成的那一帧，否则返回 {@code null}。 */
    public CheckpointPair recordPoint(Vec2 point) {
        if (pending == null) {
            pending = point;
            return null;
        }
        final CheckpointPair pair = new CheckpointPair(pending, point);
        frames.add(pair);
        pending = null;
        return pair;
    }

    /** 撤销：优先撤掉没配对的点，否则撤掉最后一帧。 */
    public String undo() {
        if (pending != null) {
            pending = null;
            return "已撤销待配对的那个点";
        }
        if (!frames.isEmpty()) {
            frames.removeLast();
            return "已撤销第 " + frames.size() + " 帧之后的一帧（现共 " + frames.size() + " 帧）";
        }
        return "没有可撤销的内容";
    }

    public void setSpawn(boolean red, Location location) {
        final Spawn spawn = new Spawn(location.getX(), location.getY(), location.getZ());
        if (red) {
            redSpawn = spawn;
        } else {
            blueSpawn = spawn;
        }
    }

    /** 起始帧编号；{@code -1} 表示取中间那一帧。 */
    public void setStartIndex(int index) {
        this.startIndex = index;
    }

    public int resolvedStartIndex() {
        if (startIndex >= 0) {
            return startIndex;
        }
        return Math.max(1, Math.min(frames.size() - 2, (frames.size() - 1) / 2));
    }

    public List<CheckpointPair> frames() {
        return List.copyOf(frames);
    }

    public Spawn redSpawn() {
        return redSpawn;
    }

    public Spawn blueSpawn() {
        return blueSpawn;
    }

    public boolean hasPending() {
        return pending != null;
    }

    /** 还差什么才能落盘；返回 {@code null} 表示可以完成。 */
    public String missing() {
        if (frames.size() < 3) {
            return "还需要至少 3 帧（当前 " + frames.size() + " 帧）";
        }
        if (pending != null) {
            return "有一个点还没配对，再点一下组成一帧";
        }
        if (redSpawn == null) {
            return "还没记红方出生点（/htf setup spawn red）";
        }
        if (blueSpawn == null) {
            return "还没记蓝方出生点（/htf setup spawn blue）";
        }
        return null;
    }
}
