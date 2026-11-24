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
    components {
        listOf("Core", "Design", "Browser", "Donations", "FreetimeWarn", "Notifications").forEach { module ->
            withModule("com.github.FreetimeMaker.Freetime-Core:$module") {
                if (id.version == "4.0.2") {
                    listOf("androidApiElements-published", "androidRuntimeElements-published").forEach { variant ->
                        withVariant(variant) {
                            withFiles {
                                removeAllFiles()
                                addFile("$module-${id.version}-android.aar")
                            }
                        }
                    }
                }
            }
        }
    }
    repositories {
        google()
        mavenCentral()

        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.FreetimeMaker.Freetime-Core")
            }
        }
    }
}

rootProject.name = "Luma Store"
include(":app")
