pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.mozilla.org/maven2/") {
            content {
                includeGroup("org.mozilla.geckoview")
            }
        }
        maven("https://maven.aliyun.com/repository/public") {
            content {
                includeModule("com.github.promeg", "tinypinyin")
            }
        }
    }
}

rootProject.name = "Via"
include(":app", ":engine-api", ":engine-webview", ":engine-gecko")
