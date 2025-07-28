pluginManagement {
    repositories {
        // Compose, Android Gradle Plugin, etc.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // プロジェクト内のリポジトリ定義を許可しない（推奨）
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// プロジェクト名
rootProject.name = "Llama"

// モジュール定義
include(":app")
include(":nativelib")
