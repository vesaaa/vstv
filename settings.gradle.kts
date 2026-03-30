pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 腾讯 X5（TBS）SDK
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/")
            content {
                includeGroup("com.tencent.tbs")
            }
        }
    }
}

rootProject.name = "My TV"
include(":app")
 