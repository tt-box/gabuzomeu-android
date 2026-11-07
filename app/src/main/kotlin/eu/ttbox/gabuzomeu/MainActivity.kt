package eu.ttbox.gabuzomeu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.ttbox.gabuzomeu.ui.calculator.CalculatorScreen
import eu.ttbox.gabuzomeu.ui.calculator.CalculatorViewModel
import eu.ttbox.gabuzomeu.ui.game.ShadokQuizScreen
import eu.ttbox.gabuzomeu.ui.help.ShadokHelpScreen
import eu.ttbox.gabuzomeu.ui.theme.GabuzomeuTheme

/**
 * Les écrans de l'application.
 *
 * Une énumération plutôt qu'un booléen par écran : deux booléens pourraient être vrais en même
 * temps, ce qui n'a aucun sens, et `rememberSaveable` sait déjà sérialiser un `enum`.
 */
private enum class Screen { CALCULATOR, HELP, GAME }

/**
 * L'unique activité de l'application.
 *
 * Le manifeste d'origine en déclarait deux avec le filtre `LAUNCHER` — `Calculator` et
 * `WidgetActivity`, cette dernière n'affichant qu'un `RelativeLayout` vide — donc **deux
 * icônes** installées, dont une menant à un écran blanc. Une troisième activité,
 * `MainActivity`, existait en code mais était entièrement commentée dans le manifeste.
 */
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Bord à bord : l'application dessine sous les barres système, comme l'exige
        // Android 15+. Scaffold réinjecte les encarts.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            GabuzomeuTheme {
                val viewModel: CalculatorViewModel = viewModel(
                    factory = CalculatorViewModel.factory(this),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Trois écrans ne justifient pas une bibliothèque de navigation, ni une seconde
                // activité. `rememberSaveable` suffit : l'écran ouvert survit à la rotation, et
                // le `BackHandler` posé dans chacun rend le retour matériel naturel. Un `when`
                // exhaustif garantit qu'un quatrième écran ne pourrait pas être oublié ici.
                var screen by rememberSaveable { mutableStateOf(Screen.CALCULATOR) }
                val windowSizeClass = calculateWindowSizeClass(this)

                when (screen) {
                    Screen.HELP -> ShadokHelpScreen(onClose = { screen = Screen.CALCULATOR })

                    Screen.GAME -> ShadokQuizScreen(onClose = { screen = Screen.CALCULATOR })

                    Screen.CALCULATOR -> CalculatorScreen(
                        state = state,
                        widthSizeClass = windowSizeClass.widthSizeClass,
                        heightSizeClass = windowSizeClass.heightSizeClass,
                        onKey = viewModel::onKey,
                        onNotationChange = viewModel::onNotationChange,
                        onModeChange = viewModel::onModeChange,
                        onSettingsChange = viewModel::onSettingsChange,
                        onPaste = viewModel::onPaste,
                        onOpenHelp = { screen = Screen.HELP },
                        onOpenGame = { screen = Screen.GAME },
                    )
                }
            }
        }
    }
}
