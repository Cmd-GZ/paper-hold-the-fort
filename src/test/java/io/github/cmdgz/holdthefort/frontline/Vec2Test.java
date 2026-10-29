package io.github.cmdgz.holdthefort.frontline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Vec2：基础几何与角度处理")
class Vec2Test {

    private static final double EPS = 1e-9;

    @Test
    @DisplayName("rot90 按 (x,z) → (−z,x) 旋转")
    void rot90() {
        assertEquals(new Vec2(0, 1), new Vec2(1, 0).rot90());
        assertEquals(new Vec2(-1, 0), new Vec2(0, 1).rot90());
    }

    @Test
    @DisplayName("归一化后长度为 1")
    void normalized() {
        assertEquals(1.0, new Vec2(3, 4).normalized().length(), EPS);
    }

    @Test
    @DisplayName("零向量归一化会抛异常")
    void normalizedZero() {
        try {
            Vec2.ZERO.normalized();
            throw new AssertionError("应当抛出异常");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    @DisplayName("signedAngle 取最短弧：差 20° 不会算成 340°")
    void signedAngleTakesShortestArc() {
        final Vec2 a = Vec2.fromYawRadians(Math.toRadians(-10));
        final Vec2 b = Vec2.fromYawRadians(Math.toRadians(10));
        assertEquals(Math.toRadians(20), Math.abs(Vec2.signedAngle(a, b)), 1e-9);
    }

    @Test
    @DisplayName("signedAngle 在有符号意义下正确")
    void signedAngleSign() {
        final Vec2 alongX = new Vec2(1, 0);
        final Vec2 alongZ = new Vec2(0, 1);
        assertEquals(Math.PI / 2, Vec2.signedAngle(alongX, alongZ), 1e-9);
        assertEquals(-Math.PI / 2, Vec2.signedAngle(alongZ, alongX), 1e-9);
    }

    @Test
    @DisplayName("yaw 与朝向可以来回换算")
    void yawRoundTrip() {
        for (double deg = -180; deg <= 180; deg += 15) {
            final Vec2 n = Vec2.fromYawRadians(Math.toRadians(deg));
            assertEquals(Math.toRadians(deg), n.toYawRadians(), 1e-9);
            assertEquals(1.0, n.length(), EPS);
        }
    }

    @Test
    @DisplayName("yaw=0 指向 +z，yaw=90° 指向 −x（Minecraft 约定）")
    void yawConvention() {
        assertEquals(0.0, Vec2.fromYawRadians(0).x(), EPS);
        assertEquals(1.0, Vec2.fromYawRadians(0).z(), EPS);
        assertEquals(-1.0, Vec2.fromYawRadians(Math.PI / 2).x(), EPS);
        assertTrue(Math.abs(Vec2.fromYawRadians(Math.PI / 2).z()) < EPS);
    }
}
