plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.0"
    id("org.jetbrains.intellij") version "1.16.0"
}

group = "com.github.cyrnicolase"
version = "1.0.11"

repositories {
    mavenCentral()
}

dependencies {
    // 添加 Gson 用于 JSON 格式化
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    // 方式一：使用本地 DataGrip 安装路径（推荐）
    // 取消下面的注释并设置正确的 DataGrip 安装路径
    // macOS 示例：
    // localPath.set("/Applications/DataGrip.app")
    // Windows 示例：
    // localPath.set("C:\\Program Files\\JetBrains\\DataGrip 2023.3")
    // Linux 示例：
    // localPath.set("/opt/datagrip")
    
    // 方式二：使用 IntelliJ IDEA Ultimate（包含数据库插件）
    // 如果使用本地路径，请注释掉下面的两行
    version.set("2023.3")
    type.set("IU") // IntelliJ IDEA Ultimate (包含数据库插件，DataGrip 基于此)
    plugins.set(listOf("com.intellij.database"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("254.*")  // 支持 DataGrip 253 及更高版本
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

