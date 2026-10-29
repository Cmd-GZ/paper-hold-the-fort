plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.cmdgz"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // Paper API 与运行期依赖
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper 26.1+ 要求 JDK 25（1.20 ~ 1.21.11 为 JDK 21）
    compileOnly("io.papermc.paper:paper-api:26.2.build.135-stable")
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
