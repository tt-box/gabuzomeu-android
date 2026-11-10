import com.android.build.api.dsl.ApplicationExtension
import eu.ttbox.gabuzomeu.buildlogic.configureJUnitPlatform
import eu.ttbox.gabuzomeu.buildlogic.configureKotlinCompiler
import eu.ttbox.gabuzomeu.buildlogic.constrainLintToolVulnerableDependencies
import eu.ttbox.gabuzomeu.buildlogic.javaVersion
import eu.ttbox.gabuzomeu.buildlogic.versionInt
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // Depuis AGP 9.0, le support de Kotlin est intégré : appliquer
        // org.jetbrains.kotlin.android est désormais une erreur de configuration.
        // Voir https://kotl.in/gradle/agp-built-in-kotlin
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = versionInt("compileSdk")
            compileSdkMinor = versionInt("compileSdkMinor")

            defaultConfig {
                minSdk = versionInt("minSdk")
                targetSdk = versionInt("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }

            lint {
                // Le projet d'origine avait lint.xml dans .gitignore : le lint n'était
                // pas suivi. Sur une base neuve, aucun warning n'est toléré.
                warningsAsErrors = true
                abortOnError = true
                checkDependencies = true
                // Les sources de test ne sont pas livrées : checkTestSources est déjà à
                // false, leur analyse ne rapportait presque rien. ignoreTestSources
                // supprime purement les tâches lintAnalyze*UnitTest / *AndroidTest.
                ignoreTestSources = true

                // targetSdk est volontairement figé à 36 (niveau exigé par Play, dont
                // les comportements d'exécution sont testés) tandis que compileSdk suit
                // les AndroidX en 37.1. OldTargetApi signalerait cet écart assumé à
                // chaque build ; le relever se décide, il ne se subit pas.
                disable += "OldTargetApi"

                // « Une version plus récente existe » : ces contrôles interrogent les
                // dépôts au moment du build, si bien qu'un code inchangé passe au rouge
                // le jour où une dépendance publie une version. Les mises à jour sont
                // l'affaire de Dependabot, qui les propose en PR testées.
                disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
            }
        }

        constrainLintToolVulnerableDependencies()
        configureKotlinCompiler()
        configureJUnitPlatform()
    }
}
