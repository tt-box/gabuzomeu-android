plugins {
    id("gabuzomeu.android.library")
    id("gabuzomeu.android.compose")
}

android {
    namespace = "eu.ttbox.gabuzomeu.widget"
}

dependencies {
    // Le widget n'affiche que les NOMS Shadok (Ga/Bu/Zo/Meu), pas les glyphes : il
    // n'a donc besoin ni de la police embarquée, ni du moteur d'évaluation.
    implementation(projects.core.shadok)

    implementation(libs.androidx.core.ktx)
    implementation(libs.glance.appwidget)
    // goAsync() dans le receiver : le rendu Glance est suspendu.
    implementation(libs.kotlinx.coroutines.android)

    // L'écran de configuration d'un widget posé : une vraie activité Compose. Elle vit ici et
    // non dans :app parce que les réglages qu'elle écrit sont ceux de CE module — même principe
    // que les chaînes du widget, qui vivent aussi dans le module qui les utilise.
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    // Les options sont persistées par instance dans l'état Glance, qui est un DataStore
    // Preferences : il faut donc les clés typées.
    implementation(libs.androidx.datastore.preferences)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(kotlin("test"))
}
