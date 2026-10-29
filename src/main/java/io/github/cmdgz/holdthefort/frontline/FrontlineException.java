package io.github.cmdgz.holdthefort.frontline;

/**
 * 某一段关键帧的异常。属于 §2.4 的 L 级：数据自相矛盾，无法继续。
 */
public class FrontlineException extends RuntimeException {

    public FrontlineException(String message) {
        super(message);
    }
}
