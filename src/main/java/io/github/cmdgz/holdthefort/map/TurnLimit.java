package io.github.cmdgz.holdthefort.map;

/**
 * 转向模式（§6.3）。
 */
public enum TurnLimit {

    /** 默认：锚定起点帧后向两侧传播，相邻夹角保证 ≤ 90°。 */
    NINETY("90"),

    /** 关闭连续性约束，每帧独立取候选 A，区域会随扫描对调。 */
    FREE("free");

    private final String yamlValue;

    TurnLimit(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }

    /**
     * 解析地图文件里的取值，**只接受** {@code 90} 或 {@code free}（L6）。
     *
     * @throws IllegalArgumentException 其它取值
     */
    public static TurnLimit parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("turn_limit 不能为空，应为 90 或 free");
        }
        final String v = raw.trim();
        for (TurnLimit t : values()) {
            if (t.yamlValue.equalsIgnoreCase(v)) {
                return t;
            }
        }
        throw new IllegalArgumentException("turn_limit 只接受 90 或 free，实际是 " + raw);
    }
}
