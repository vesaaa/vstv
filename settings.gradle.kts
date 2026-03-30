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
        // 腾讯 X5（TBS）SDK（双镜像：GitHub Actions 等环境访问主镜像偶发失败时可回落）
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-public/")
            content {
                includeGroup("com.tencent.tbs")
            }
        }
        maven {
            url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
            content {
                includeGroup("com.tencent.tbs")
            }
        }
    }
}

rootProject.name = "vstv"
include(":app")
 