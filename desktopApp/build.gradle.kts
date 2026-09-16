import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(platform("io.github.jan-tennert.supabase:bom:3.8.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

compose.desktop {
    application {
        mainClass = "com.freetime.lumastore.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm,
            )
            packageName = "Luma Store"
            packageVersion = "2.0.1"
            description = "Luma Store desktop client"
            vendor = "Freetime Maker"
            copyright = "Copyright © 2026 Freetime Maker"

            windows {
                // Make the installed app findable: Start menu entry + desktop shortcut
                menu = true
                menuGroup = "Luma Store"
                shortcut = true
                // Let the user see/pick the install folder in the setup wizard
                dirChooser = true
                // Per-user install: no admin rights needed, lands in %LOCALAPPDATA%
                perUserInstall = true
                // Fixed upgrade code so future versions upgrade instead of installing side-by-side
                upgradeUuid = "446ac3ee-60c3-457c-b11d-7a335f8bf8eb"
                iconFile.set(file("icons/icon.ico"))
            }

            linux {
                iconFile.set(file("icons/icon.png"))
            }
        }
    }
}
