package io.github.cmdgz.holdthefort.map;

/**
 * 地图文件读不出来时抛出。属于 L 级：格式本身有问题，无法继续。
 */
public class MapParseException extends RuntimeException {

    public MapParseException(String message) {
        super(message);
    }
}
