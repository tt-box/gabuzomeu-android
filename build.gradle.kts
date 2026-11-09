import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Dépendances transitives de l'outillage de build (AGP, Kover) touchées par des CVE.
// Elles n'entrent jamais dans l'APK : elles ne vivent que dans le classpath du build.
// Les contraintes forcent la première version corrigée sans changer le reste de la
// résolution. À retirer dès qu'AGP 9.6 stable et une version de Kover embarquent ces
// versions corrigées (`./gradlew buildEnvironment` permet de le vérifier).
buildscript {
    dependencies {
        constraints {
            classpath("org.freemarker:freemarker:2.3.35")
            classpath("org.bouncycastle:bcprov-jdk18on:1.86")
            classpath("org.bouncycastle:bcpkix-jdk18on:1.86")
            classpath("org.bouncycastle:bcutil-jdk18on:1.86")
            classpath("com.google.guava:guava:33.7.2-jre")
            classpath("org.bitbucket.b_c:jose4j:0.9.7")
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.apache.commons:commons-lang3:3.21.0")
        }
    }
}

// Les plugins sont déclarés ici sans être appliqués : cela fixe leur version une
// seule fois pour tout le build. Chaque module applique les convention plugins
// gabuzomeu.* définis dans build-logic/.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

// Formatage uniforme sur tous les modules. Le projet d'origine n'avait aucun garde-fou :
// .gitignore y listait lint.xml et .checkstyle, ce qui revenait à ne suivre ni le lint
// ni le style.
allprojects {
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            targetExclude("**/build/**")
            ktlint().editorConfigOverride(
                mapOf(
                    // Les noms de tests entre backticks sont volontaires et lisibles.
                    "ktlint_standard_function-naming" to "disabled",
                    // Compose veut des composables en PascalCase.
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "max_line_length" to "100",
                ),
            )
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            // Uniquement les fichiers de CE projet. Un motif `**/*.gradle.kts` ferait
            // que chaque module cible aussi les fichiers des autres : les tâches se
            // marcheraient dessus en exécution parallèle.
            target("*.gradle.kts")
            ktlint()
        }
    }
}

// Analyse statique. Spotless traite la forme, Detekt le fond : complexité, code mort,
// pièges de conception. Android Lint, lui, couvre le spécifique plateforme.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }
}

// Couverture de code. Kover instrumente les tests unitaires (JVM et tests locaux
// Android) ; le projet racine agrège les quatre modules en un seul rapport, publié
// sur Codecov par la CI. Les tests instrumentés (émulateur) n'y figurent pas.
subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

dependencies {
    kover(projects.app)
    kover(projects.widget)
    kover(projects.core.shadok)
    kover(projects.core.eval)
}
