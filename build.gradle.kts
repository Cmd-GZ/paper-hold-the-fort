plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.cmdgz"
version = "0.1.0"

repositories {
    mavenCentral()
    // Paper API 与运行期依赖
    maven("https://repo.papermc.io/repository/maven-public/")
    // 公共库 MiniGameLib 的本地仓库：主仓库的 tools/build.sh 会把 com.hub:minigame-lib
    // install 到 <主仓库>/.server/m2repo，这里直接把它当仓库用 —— 离线也能构建。
    // （本工程是 submodule 的 checkout，所以相对本工程往上找一级到主仓库的 .server/）
    maven {
        name = "localMiniGameLib"
        url = uri(file("../.server/m2repo"))
    }
}

dependencies {
    // Paper 26.1+ 要求 JDK 25（1.20 ~ 1.21.11 为 JDK 21）
    compileOnly("io.papermc.paper:paper-api:26.2.build.126-stable")
    // 公共库 MiniGameLib（与其它 23 个小游戏一致）：HubAPI 做主城对接、Worlds 解析世界 token。
    // compileOnly = Maven 的 provided —— 运行期由服务器 plugins/ 下的 MiniGameLib.jar 提供，
    // 由 plugin.yml 的 depend 保证加载顺序；不要打进本插件 jar。
    compileOnly("com.hub:minigame-lib:1.7.3")
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
