package eu.ttbox.gabuzomeu.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Accès au catalogue de versions depuis un convention plugin. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.versionInt(alias: String): Int =
    libs.findVersion(alias).get().requiredVersion.toInt()

internal val Project.javaVersion: JavaVersion
    get() = JavaVersion.toVersion(libs.findVersion("jvmTarget").get().requiredVersion)

/**
 * Aligne la cible JVM de Kotlin sur celle de Java et fait échouer le build sur les
 * warnings du compilateur : sur un projet neuf il n'y a aucune dette à tolérer.
 */
internal fun Project.configureKotlinCompiler() {
    val target = JvmTarget.fromTarget(libs.findVersion("jvmTarget").get().requiredVersion)
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(target)
            allWarningsAsErrors.set(true)
        }
    }
}

internal fun Project.configureJUnitPlatform() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

/**
 * Relève, dans l'outil de lint d'Android (configuration `androidLintTool`), des
 * dépendances transitives touchées par des CVE. Elles ne sont jamais embarquées dans
 * l'APK : elles ne servent qu'à exécuter lint pendant le build. Les contraintes forcent
 * la première version corrigée sans toucher au reste de la résolution.
 *
 * À retirer dès qu'une version stable d'AGP (9.6) embarque ces versions corrigées.
 * Un jeu de versions voisin est déclaré dans le build.gradle.kts racine (classpath du
 * build) : les deux listes évoluent ensemble.
 */
internal fun Project.constrainLintToolVulnerableDependencies() {
    val fixed = listOf(
        "org.bouncycastle:bcprov-jdk18on:1.86",
        "org.bouncycastle:bcpkix-jdk18on:1.86",
        "org.bouncycastle:bcutil-jdk18on:1.86",
        "com.google.guava:guava:33.7.2-jre",
        "org.apache.commons:commons-lang3:3.18.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
    )
    fixed.forEach { dependencies.constraints.add("androidLintTool", it) }
}
