import java.util.Properties

plugins {
    id("gabuzomeu.android.application")
    id("gabuzomeu.android.compose")
}

/**
 * Identifiants de signature, hors du dépôt.
 *
 * En local : un fichier `keystore.properties` à la racine (ignoré par git). En CI : des
 * variables d'environnement. Si ni l'un ni l'autre n'est présent, la configuration
 * release reste absente et `assembleRelease` produit un APK non signé — ce qui suffit
 * pour vérifier que R8 n'a rien cassé.
 *
 * Remplace le profil Maven `release` et son `maven-jarsigner-plugin`, dont les
 * propriétés `sign.*` devaient être passées à la main sur la ligne de commande.
 */
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun signingValue(
    key: String,
    environmentVariable: String,
): String? = keystoreProperties.getProperty(key) ?: System.getenv(environmentVariable)

val releaseStoreFile = signingValue("storeFile", "GABUZOMEU_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "GABUZOMEU_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "GABUZOMEU_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "GABUZOMEU_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

android {
    namespace = "eu.ttbox.gabuzomeu"

    defaultConfig {
        applicationId = "eu.ttbox.gabuzomeu"
        // Le projet d'origine était incohérent : versionCode 6 / versionName 0.0.2
        // dans le manifeste, mais version 0.0.1 dans le pom.xml. Source unique ici.
        //
        // Le workflow de release surcharge ces valeurs depuis le tag git, de sorte
        // qu'un tag v1.2.3 produise réellement un artefact 1.2.3.
        versionCode = System.getenv("GABUZOMEU_VERSION_CODE")?.toIntOrNull() ?: 7
        versionName = System.getenv("GABUZOMEU_VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Clé ECDSA P-256 (secp256r1). minSdk 31 : le schéma v1 (JAR) est
                // inutile — et ne serait jamais vérifié — ; v2 couvre Android 7+,
                // v3 / v3.1 permettent une future rotation de clé (Android 9+ / 13+).
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    androidResources {
        // Les Shadoks sont français ; l'anglais est la seule autre langue fournie.
        // Le values-sw/ (swahili) du projet d'origine était un reliquat d'AOSP.
        localeFilters += listOf("fr", "en")
    }
}

dependencies {
    implementation(projects.core.shadok)
    implementation(projects.core.eval)
    // Le widget est une bibliothèque : c'est l'application qui le package, et son
    // manifeste fusionne le receiver et la permission RECEIVE_BOOT_COMPLETED.
    implementation(projects.widget)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowsizeclass)
    implementation(libs.compose.material.icons.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Les tests instrumentés tournent sous JUnit 4 (exigence d'AndroidJUnitRunner),
    // mais kotlin.test offre des assertions plus lisibles que org.junit.Assert.
    androidTestImplementation(kotlin("test"))
    debugImplementation(libs.compose.ui.test.manifest)
}
