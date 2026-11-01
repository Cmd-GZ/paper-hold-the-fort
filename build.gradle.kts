plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.cmdgz"
// 版本唯一来源：`plugin.yml` 里写的是 ${version}，由 processResources 展开，两者不会不一致。
// 0.2.0 = 坐标解耦写入侧（/htf setup 在房间实例世界里落盘 tpl:<模板键>）。
version = "0.2.0"

repositories {
    mavenCentral()
    // Paper API 与运行期依赖
    maven("https://repo.papermc.io/repository/maven-public/")
    // 公共库 MiniGameLib **随本仓库提供**：libs/minigame-lib-<版本>.jar
    // 不用 <主仓库>/.server/m2repo —— 那个目录是主仓库 gitignore 的，
    // 换机器 / 换工作目录 / IDE 的 Gradle 同步换个 cwd 就解析不到，
    // 症状是 HubAPI / Worlds 全部标红（HoldTheFortPlugin 与 MatchManager 首当其冲）。
    flatDir { dirs("libs") }
}

dependencies {
    // Paper 26.1+ 要求 JDK 25（1.20 ~ 1.21.11 为 JDK 21）
    compileOnly("io.papermc.paper:paper-api:26.2.build.126-stable")
    // 公共库 MiniGameLib（与其它 23 个小游戏一致）：HubAPI 做主城对接、Worlds 解析世界 token。
    // compileOnly = Maven 的 provided —— 运行期由服务器 plugins/ 下的 MiniGameLib.jar 提供，
    // 由 plugin.yml 的 depend 保证加载顺序；不要打进本插件 jar。
    // 收进本仓库的 jar（见 libs/README.md）。升级 MiniGameLib 时同步替换这个文件与版本号。
    compileOnly(files("libs/minigame-lib-1.7.3.jar"))
    // 地图与配置解析。运行期由 Paper 自带，等价于 paper-api 的传递依赖。
    compileOnly("org.yaml:snakeyaml:2.2")
    testImplementation("org.yaml:snakeyaml:2.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }

    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    test {
        useJUnitPlatform()
    }

    // 开发期一键起服：自动下载 Paper 并把刚构建的 jar 装进 plugins/
    runServer {
        minecraftVersion("26.2")
    }
}
