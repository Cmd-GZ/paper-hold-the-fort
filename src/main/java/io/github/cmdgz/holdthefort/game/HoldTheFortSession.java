package io.github.cmdgz.holdthefort.game;

import com.hub.session.EndReason;
import com.hub.session.GameSession;

/**
 * Hold the Fort 的对局会话（房间化接入口）。
 *
 * <p>为什么需要它：框架的 I1（归属链）/I4（生命周期唯一出口）/D25（对局结束销毁房间）
 * 都以 {@link GameSession} 为锚点。HTF 之前只调了 {@code HubAPI.setGame/clearGame}，
 * 没有会话 —— 于是 doctor 里看不到它的对局，房间也不会在收场后销毁。
 *
 * <p>本类**只管框架对接**，玩法逻辑仍在 {@link MatchManager}：
 * 开局/结算/战场推进完全由它驱动，这里只在"收场"时把结束原因交给框架。
 */
public final class HoldTheFortSession extends GameSession {

    /** 房间化后的地图场地名（一个模板一张图时就是这张图的名字）。 */
    public static final String KEY = "holdthefort";

    private final MatchManager match;

    public HoldTheFortSession(MatchManager match) {
        super(KEY, "世界树战场");
        this.match = match;
    }

    /** 有人报到就进入等待态（框架要求 CREATED → WAITING 才能 join）。 */
    void markWaiting() {
        enterWaiting();
    }

    /** 开局（MatchManager 在倒计时结束时调）。 */
    void markRunning() {
        enterRunning();
    }

    @Override
    protected void onStart() {
        // 开局动作由 MatchManager 驱动，这里不做任何事（避免两处写同一份状态）。
    }

    @Override
    protected void onEnd(EndReason reason) {
        // 框架已经摘完归属链、销毁了房间；这里只把玩家侧的东西收干净。
        match.onSessionEnded(reason);
    }
}
