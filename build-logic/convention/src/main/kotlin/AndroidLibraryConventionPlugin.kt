import com.android.build.api.dsl.LibraryExtension
import eu.ttbox.gabuzomeu.buildlogic.configureJUnitPlatform
import eu.ttbox.gabuzomeu.buildlogic.configureKotlinCompiler
import eu.ttbox.gabuzomeu.buildlogic.javaVersion
import eu.ttbox.gabuzomeu.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Kotlin est intégré à AGP depuis la 9.0 (cf. AndroidApplicationConventionPlugin).
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = versionInt("compileSdk")
            compileSdkMinor = versionInt("compileSdkMinor")

            defaultConfig {
                minSdk = versionInt("minSdk")
                // Pas de targetSdk sur un module bibliothèque : déprécié, c'est
                // l'application qui le porte.
            }

            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }

            lint {
                warningsAsErrors = true
                abortOnError = true
                checkDependencies = true
                // Versions plus récentes : laissées à Dependabot (voir le plugin application).
                disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
            }
        }

        configureKotlinCompiler()
        configureJUnitPlatform()
    }
}
