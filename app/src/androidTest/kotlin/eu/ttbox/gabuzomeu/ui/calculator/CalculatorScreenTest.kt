package eu.ttbox.gabuzomeu.ui.calculator

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.core.eval.CalculationMode
import eu.ttbox.gabuzomeu.core.eval.EvalError
import eu.ttbox.gabuzomeu.core.eval.NumberNotation
import eu.ttbox.gabuzomeu.core.eval.Operator
import eu.ttbox.gabuzomeu.core.shadok.ShadokDigit
import eu.ttbox.gabuzomeu.data.DisplaySettings
import eu.ttbox.gabuzomeu.ui.ActionTags
import eu.ttbox.gabuzomeu.ui.DisplayTags
import eu.ttbox.gabuzomeu.ui.KeypadTags
import eu.ttbox.gabuzomeu.ui.NotationTags
import eu.ttbox.gabuzomeu.ui.SettingsTags
import eu.ttbox.gabuzomeu.ui.calculator.components.ValueWriting
import eu.ttbox.gabuzomeu.ui.theme.GabuzomeuTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests instrumentés de l'écran.
 *
 * Deux principes, appris à la dure :
 *
 * 1. **Rien en dur qui dépende de la langue.** Une première version comparait des
 *    chaînes françaises ; elles passaient sur un téléphone français et échouaient sur
 *    l'émulateur `en-US` de la CI, qui sert `values-en/`.
 * 2. **Pas de recherche par texte sur les touches.** Le pavé applique
 *    `clearAndSetSemantics`, ce qui efface la sémantique de texte des descendants : une
 *    touche est introuvable par `onNodeWithText`. On passe donc par des `testTag`
 *    dérivés de l'action.
 *
 * Le test d'origine, `CalculatorHitSomeButtons`, ne pouvait de toute façon plus passer :
 * il castait `display.getCurrentView()` en `EditText` alors que les enfants du
 * `ViewSwitcher` étaient devenus des `CalculatorConverterDisplay` (`LinearLayout`) —
 * `ClassCastException` garantie.
 */
class CalculatorScreenTest {

    /**
     * `createAndroidComposeRule` de l'API **v2** : elle s'appuie sur
     * `StandardTestDispatcher`, aligné sur le comportement standard des coroutines, là
     * où la v1 — dépréciée — exécutait les tâches immédiatement via
     * `UnconfinedTestDispatcher`.
     */
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Les rappels de l'écran, regroupés.
     *
     * Un test n'en observe jamais qu'un ; les autres gardent leur lambda vide. Les passer
     * en bloc évite aussi de faire grandir la liste de paramètres de [setScreen] à chaque
     * nouvelle interaction — detekt s'en plaint, à juste titre.
     */
    private class Callbacks(
        val onKey: (KeyAction) -> Unit = {},
        val onNotationChange: (NumberNotation) -> Unit = {},
        val onModeChange: (CalculationMode) -> Unit = {},
        val onSettingsChange: (DisplaySettings) -> Unit = {},
        val onPaste: (String) -> Unit = {},
        val onOpenHelp: () -> Unit = {},
    )

    /**
     * Un nœud de l'afficheur, cherché dans l'arbre **non fusionné**.
     *
     * Depuis que l'afficheur est copiable, il est enveloppé d'un `combinedClickable` — et un
     * modificateur cliquable fusionne la sémantique de ses descendants, ce qui fait disparaître
     * leurs `testTag` de l'arbre fusionné. Chercher sans fusion vaut dans les deux cas et
     * vérifie le nœud lui-même plutôt qu'un agrégat.
     */
    private fun displayNode(tag: String) =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun setScreen(
        state: CalculatorUiState,
        widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
        callbacks: Callbacks = Callbacks(),
    ) {
        composeTestRule.setContent {
            // dynamicColor = false : un rendu stable, indépendant du fond d'écran.
            GabuzomeuTheme(dynamicColor = false) {
                CalculatorScreen(
                    state = state,
                    widthSizeClass = widthSizeClass,
                    onKey = callbacks.onKey,
                    onNotationChange = callbacks.onNotationChange,
                    onModeChange = callbacks.onModeChange,
                    onSettingsChange = callbacks.onSettingsChange,
                    onPaste = callbacks.onPaste,
                    onOpenHelp = callbacks.onOpenHelp,
                )
            }
        }
    }

    // ------------------------------------------------------------------ affichage

    @Test
    fun leShadokEstLaLignePrincipaleEtLeDecimalEstSecondaire() {
        setScreen(CalculatorUiState(glyphs = "_⅃", labels = "BuZo", decimal = "6"))

        // La ligne de glyphes est vectorielle : elle s'annonce par ses noms.
        displayNode(DisplayTags.GLYPHS)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("BuZo")
        displayNode(DisplayTags.LABELS).assertIsDisplayed()
        displayNode(DisplayTags.DECIMAL).assertIsDisplayed()
    }

    @Test
    fun masquerLesNomsShadokRetireLaLigneCorrespondante() {
        setScreen(
            CalculatorUiState(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                settings = DisplaySettings(showShadokLabels = false),
            ),
        )

        displayNode(DisplayTags.LABELS).assertDoesNotExist()
        // Les glyphes restent : ils ne sont pas masquables.
        displayNode(DisplayTags.GLYPHS).assertIsDisplayed()
        displayNode(DisplayTags.DECIMAL).assertIsDisplayed()
    }

    @Test
    fun masquerLeDecimalRetireLaLigneCorrespondante() {
        setScreen(
            CalculatorUiState(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                settings = DisplaySettings(showDecimal = false),
            ),
        )

        displayNode(DisplayTags.DECIMAL).assertDoesNotExist()
        displayNode(DisplayTags.GLYPHS).assertIsDisplayed()
        displayNode(DisplayTags.LABELS).assertIsDisplayed()
    }

    @Test
    fun lesGlyphesRestentVisiblesMemeToutMasque() {
        setScreen(
            CalculatorUiState(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                settings = DisplaySettings(showShadokLabels = false, showDecimal = false),
            ),
        )

        displayNode(DisplayTags.GLYPHS).assertIsDisplayed()
    }

    // -------------------------------------------------------------------- réglages

    @Test
    fun leMenuPermetDeBasculerLesReglages() {
        var requested: DisplaySettings? = null
        setScreen(CalculatorUiState(), callbacks = Callbacks(onSettingsChange = { requested = it }))

        composeTestRule.onNodeWithTag(SettingsTags.MENU_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SettingsTags.TOGGLE_LABELS).performClick()

        assertEquals(DisplaySettings(showShadokLabels = false), requested)
    }

    @Test
    fun leMenuPermetDeMasquerLeDecimal() {
        var requested: DisplaySettings? = null
        setScreen(CalculatorUiState(), callbacks = Callbacks(onSettingsChange = { requested = it }))

        composeTestRule.onNodeWithTag(SettingsTags.MENU_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SettingsTags.TOGGLE_DECIMAL).performClick()

        assertEquals(DisplaySettings(showDecimal = false), requested)
    }

    // ------------------------------------------------------------------------ pavé

    @Test
    fun lePaveDecimalEmetLesBonnesActions() {
        val pressed = mutableListOf<KeyAction>()
        setScreen(CalculatorUiState(), callbacks = Callbacks(onKey = { pressed += it }))

        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit('7'))).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Op(Operator.TIMES))).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit('6'))).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Evaluate)).performClick()

        assertEquals(
            listOf(
                KeyAction.Digit('7'),
                KeyAction.Op(Operator.TIMES),
                KeyAction.Digit('6'),
                KeyAction.Evaluate,
            ),
            pressed,
        )
    }

    /**
     * Les touches Shadok s'annoncent par leur **nom**, pas par leur forme.
     *
     * C'est la garantie structurelle contre le bug d'origine : dans
     * `res/layout-port/shadok_pad.xml:32-42`, `@+id/digitMeu` portait
     * `contentDescription="@string/digitNameMeu"` mais `text="@string/digitZo"` — les
     * descriptions de Zo et Meu étaient croisées. Elles dérivent désormais de
     * [ShadokDigit.label], donc l'inversion est impossible.
     */
    @Test
    fun lesTouchesShadokSAnnoncentParLeurNom() {
        val pressed = mutableListOf<KeyAction>()
        setScreen(
            CalculatorUiState(notation = NumberNotation.SHADOK),
            callbacks = Callbacks(onKey = {
                pressed +=
                    it
            }),
        )

        ShadokDigit.entries.forEach { digit ->
            pressed.clear()
            // Trouvée par son nom prononcé, cliquée, et vérifiée sur le glyphe émis.
            composeTestRule.onNodeWithContentDescription(digit.label).performClick()
            assertEquals(
                KeyAction.Digit(digit.glyph),
                pressed.single(),
                "la touche annoncée « ${digit.label} » doit saisir le glyphe ${digit.glyph}",
            )
        }
    }

    @Test
    fun lePaveShadokNAffichePasDeChiffresDecimaux() {
        setScreen(CalculatorUiState(notation = NumberNotation.SHADOK))

        // En base 4, les chiffres 4 à 9 n'existent pas.
        "456789".forEach { absent ->
            composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit(absent)))
                .assertDoesNotExist()
        }
        // Les quatre chiffres Shadok, eux, sont bien là.
        ShadokDigit.entries.forEach { digit ->
            composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit(digit.glyph)))
                .assertIsDisplayed()
        }
    }

    @Test
    fun leSelecteurDeModeChangeDeNotation() {
        var requested: NumberNotation? = null
        setScreen(CalculatorUiState(), callbacks = Callbacks(onNotationChange = { requested = it }))

        composeTestRule.onNodeWithTag(NotationTags.of(NumberNotation.SHADOK)).performClick()

        assertEquals(NumberNotation.SHADOK, requested)
    }

    /**
     * Sur grand écran, le pavé Shadok doit être là. Les dispositions tablette du projet
     * d'origine (`res/layout-sw600dp/main.xml` et `-sw600dp-land`) n'avaient aucune
     * touche Shadok ni `@+id/panelswitch` : la fonctionnalité était inaccessible.
     */
    @Test
    fun lePaveShadokResteAccessibleSurGrandEcran() {
        setScreen(
            CalculatorUiState(notation = NumberNotation.SHADOK),
            widthSizeClass = WindowWidthSizeClass.Expanded,
        )

        ShadokDigit.entries.forEach { digit ->
            composeTestRule.onNodeWithContentDescription(digit.label).assertIsDisplayed()
        }
    }

    // ------------------------------------------------------ notation polonaise inverse

    private val rpn = CalculatorUiState(mode = CalculationMode.RPN)

    private val simpleShadok = CalculatorUiState(
        mode = CalculationMode.SIMPLE,
        notation = NumberNotation.SHADOK,
    )

    @Test
    fun leMenuPermetDeChoisirLaNotationPolonaiseInverse() {
        var requested: CalculationMode? = null
        setScreen(CalculatorUiState(), callbacks = Callbacks(onModeChange = { requested = it }))

        composeTestRule.onNodeWithTag(SettingsTags.MENU_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SettingsTags.MODE_RPN).performClick()

        assertEquals(CalculationMode.RPN, requested)
    }

    @Test
    fun leMenuPermetDeRevenirALaCalculatriceClassique() {
        var requested: CalculationMode? = null
        setScreen(rpn, callbacks = Callbacks(onModeChange = { requested = it }))

        composeTestRule.onNodeWithTag(SettingsTags.MENU_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SettingsTags.MODE_CLASSIC).performClick()

        assertEquals(CalculationMode.CLASSIC, requested)
    }

    /**
     * En postfixe, l'ordre de frappe est l'ordre de calcul : il n'y a rien à grouper ni à
     * déclencher. Les touches correspondantes doivent donc disparaître, et non rester
     * présentes sans effet.
     */
    @Test
    fun lePaveNpiNAffichePasDeParenthesesNiDEgale() {
        setScreen(rpn)

        listOf(KeyAction.LeftParen, KeyAction.RightParen, KeyAction.Evaluate).forEach { absent ->
            composeTestRule.onNodeWithTag(KeypadTags.of(absent)).assertDoesNotExist()
        }
        listOf(KeyAction.Enter, KeyAction.Swap, KeyAction.Drop, KeyAction.Negate).forEach { key ->
            composeTestRule.onNodeWithTag(KeypadTags.of(key)).assertIsDisplayed()
        }
    }

    @Test
    fun lePaveClassiqueNAffichePasLesTouchesDePile() {
        setScreen(CalculatorUiState())

        listOf(
            KeyAction.Enter,
            KeyAction.Swap,
            KeyAction.Drop,
            KeyAction.Negate,
        ).forEach { absent ->
            composeTestRule.onNodeWithTag(KeypadTags.of(absent)).assertDoesNotExist()
        }
    }

    // ------------------------------------------------------------------ mode Simple

    /**
     * Le pavé de l'affiche : quatre chiffres, quatre opérateurs, C, ⌫ et POMPER.
     *
     * Ce qu'il n'a pas compte autant que ce qu'il a : ni parenthèses, ni séparateur, ni
     * touches de pile. Une touche sans effet serait pire qu'absente.
     */
    @Test
    fun lePaveSimpleShadokSeLimiteAuxTouchesDeLAffiche() {
        setScreen(simpleShadok)

        ShadokDigit.entries.forEach { digit ->
            composeTestRule.onNodeWithContentDescription(digit.label).assertIsDisplayed()
        }
        listOf(KeyAction.Clear, KeyAction.Delete, KeyAction.Evaluate).forEach { key ->
            composeTestRule.onNodeWithTag(KeypadTags.of(key)).assertIsDisplayed()
        }
        listOf(
            KeyAction.LeftParen,
            KeyAction.RightParen,
            KeyAction.Separator,
            KeyAction.Enter,
            KeyAction.Swap,
            KeyAction.Drop,
            KeyAction.Negate,
        ).forEach { absent ->
            composeTestRule.onNodeWithTag(KeypadTags.of(absent)).assertDoesNotExist()
        }
    }

    @Test
    fun lePaveSimpleEmetLesBonnesActions() {
        val pressed = mutableListOf<KeyAction>()
        setScreen(simpleShadok, callbacks = Callbacks(onKey = { pressed += it }))

        val bu = KeyAction.Digit(ShadokDigit.BU.glyph)
        val zo = KeyAction.Digit(ShadokDigit.ZO.glyph)
        composeTestRule.onNodeWithTag(KeypadTags.of(bu)).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Op(Operator.PLUS))).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(zo)).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Evaluate)).performClick()

        assertEquals(listOf(bu, KeyAction.Op(Operator.PLUS), zo, KeyAction.Evaluate), pressed)
    }

    /**
     * Le point de conception du mode : appuyer sur un opérateur doit **se voir**.
     *
     * La grande valeur, elle, ne bouge pas — après `Bu +`, l'écran montre toujours `Bu`.
     * Sans ce repère, les deux états produiraient exactement la même image, et l'appui
     * semblerait n'avoir rien fait. C'est le défaut corrigé pour la NPI en 2.0.0-RC5.
     */
    @Test
    fun uneOperationEnAttenteSeVoitEtDisparaitAuResultat() {
        var state by mutableStateOf(simpleShadok.copy(glyphs = "_", labels = "Bu", decimal = "1"))
        composeTestRule.setContent {
            GabuzomeuTheme(dynamicColor = false) {
                CalculatorScreen(
                    state = state,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onKey = {},
                    onNotationChange = {},
                    onModeChange = {},
                    onSettingsChange = {},
                )
            }
        }

        displayNode(DisplayTags.PENDING).assertDoesNotExist()

        state = state.copy(pending = Operator.PLUS)
        composeTestRule.waitForIdle()

        // Annoncé par le nom de l'opérateur, jamais par la forme du symbole. L'attendu est
        // lu dans les ressources : l'assertion vaut donc aussi sur l'émulateur `en-US`.
        val name = composeTestRule.activity.getString(R.string.key_plus)
        displayNode(DisplayTags.PENDING)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(
                composeTestRule.activity.getString(R.string.display_pending, name),
            )

        state = state.copy(pending = null, glyphs = "⅃", labels = "Zo", decimal = "2")
        composeTestRule.waitForIdle()

        displayNode(DisplayTags.PENDING).assertDoesNotExist()
    }

    @Test
    fun leModeSimpleMontreLaValeurDansLesTroisEcritures() {
        setScreen(simpleShadok.copy(glyphs = "_⅃", labels = "BuZo", decimal = "6"))

        displayNode(DisplayTags.GLYPHS).assertIsDisplayed().assertContentDescriptionEquals("BuZo")
        displayNode(DisplayTags.LABELS).assertIsDisplayed()
        displayNode(DisplayTags.DECIMAL).assertIsDisplayed()
        // Pas de pile : ce mode n'en a pas.
        displayNode(DisplayTags.STACK).assertDoesNotExist()
    }

    @Test
    fun leMenuOffreLesTroisModes() {
        val requested = mutableListOf<CalculationMode>()
        setScreen(CalculatorUiState(), callbacks = Callbacks(onModeChange = { requested += it }))

        composeTestRule.onNodeWithTag(SettingsTags.MENU_BUTTON).performClick()
        listOf(
            SettingsTags.MODE_SIMPLE,
            SettingsTags.MODE_CLASSIC,
            SettingsTags.MODE_RPN,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        composeTestRule.onNodeWithTag(SettingsTags.MODE_SIMPLE).performClick()

        assertEquals(listOf(CalculationMode.SIMPLE), requested)
    }

    @Test
    fun lePaveNpiEmetLesBonnesActions() {
        val pressed = mutableListOf<KeyAction>()
        setScreen(rpn, callbacks = Callbacks(onKey = { pressed += it }))

        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit('6'))).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Enter)).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit('7'))).performClick()
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Op(Operator.TIMES))).performClick()

        assertEquals(
            listOf(
                KeyAction.Digit('6'),
                KeyAction.Enter,
                KeyAction.Digit('7'),
                KeyAction.Op(Operator.TIMES),
            ),
            pressed,
        )
    }

    @Test
    fun lePaveNpiShadokGardeSesQuatreChiffresEtSesTouchesDePile() {
        setScreen(
            CalculatorUiState(mode = CalculationMode.RPN, notation = NumberNotation.SHADOK),
            widthSizeClass = WindowWidthSizeClass.Expanded,
        )

        ShadokDigit.entries.forEach { digit ->
            composeTestRule.onNodeWithContentDescription(digit.label).assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Enter)).assertIsDisplayed()
        // En base 4, les chiffres 4 a 9 n'existent pas, NPI ou non.
        "456789".forEach { absent ->
            composeTestRule.onNodeWithTag(KeypadTags.of(KeyAction.Digit(absent)))
                .assertDoesNotExist()
        }
    }

    // -------------------------------------------------------------------- la pile

    @Test
    fun lesNiveauxDePileSontAffichesDuSommetVersLeFond() {
        setScreen(
            rpn.copy(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                stack = listOf(
                    StackLevel(glyphs = "⅃", labels = "Zo", decimal = "2"),
                    StackLevel(glyphs = "_◿", labels = "BuMeu", decimal = "7"),
                ),
            ),
        )

        displayNode(DisplayTags.STACK).assertIsDisplayed()
        // L'indice 0 est le niveau immediatement sous le sommet, donc le dernier de la liste.
        displayNode(DisplayTags.stackLevel(0))
            .assertIsDisplayed()
            .assertContentDescriptionContains("BuMeu", substring = true)
        // Sur un ecran court, le niveau le plus profond sort du cadre : depuis que la zone
        // de frappe reste dessinee sous le trait — le prix d'un trait qui ne bouge pas — la
        // liste dispose d'un bloc de moins. La garantie n'est donc pas qu'un niveau donne
        // soit visible, mais qu'aucun ne soit perdu : la pile defile sans limite de
        // profondeur. C'est l'emulateur de la CI, plus court que le telephone, qui a
        // rappele la nuance.
        displayNode(DisplayTags.stackLevel(1))
            .performScrollTo()
            .assertIsDisplayed()
            .assertContentDescriptionContains("Zo", substring = true)
        // Et le sommet reste la ligne principale.
        displayNode(DisplayTags.GLYPHS).assertContentDescriptionEquals("BuZo")
    }

    /**
     * Le réglage vaut pour toute la colonne : le masquer sur X et le laisser sur les
     * niveaux au-dessus donnerait un afficheur mi-figue mi-raisin.
     */
    @Test
    fun masquerLeDecimalLeRetireAussiDeLaPile() {
        setScreen(
            rpn.copy(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                stack = listOf(StackLevel(glyphs = "⅃", labels = "Zo", decimal = "2")),
                settings = DisplaySettings(showDecimal = false),
            ),
        )

        displayNode(DisplayTags.DECIMAL).assertDoesNotExist()
        // Le niveau reste affiché, mais ne s'annonce plus que par ses noms Shadok.
        displayNode(DisplayTags.stackLevel(0))
            .assertIsDisplayed()
            .assertContentDescriptionContains("Zo", substring = true)
    }

    /**
     * Le défaut que ces deux tests verrouillent.
     *
     * `6` tapé et `6` empilé se dessinaient **exactement pareil** : le grand affichage
     * montrait « la frappe, ou à défaut le sommet ». Rien ne bougeant au premier ENTER, on
     * appuyait une seconde fois — et la duplication du sommet, convention HP, laissait un
     * doublon au fond de la pile pour tout le calcul suivant.
     *
     * Le rang est ce qui distingue les deux états : une frappe n'en a pas.
     */
    @Test
    fun uneFrappeEnCoursNaPasDeRang() {
        setScreen(
            rpn.copy(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                entering = true,
                // Pendant une frappe, la pile entière est dans la liste : son sommet aussi.
                stack = listOf(StackLevel(glyphs = "⅃", labels = "Zo", decimal = "2")),
            ),
        )

        displayNode(DisplayTags.X_LEVEL).assertDoesNotExist()
        displayNode(DisplayTags.stackLevel(0)).assertIsDisplayed()
    }

    @Test
    fun uneValeurEmpileePorteSonRang() {
        setScreen(rpn.copy(glyphs = "_⅃", labels = "BuZo", decimal = "6", entering = false))

        displayNode(DisplayTags.X_LEVEL).assertIsDisplayed()
        // Elle est le sommet : plus rien ne reste dans la liste au-dessus du séparateur.
        displayNode(DisplayTags.stackLevel(0)).assertDoesNotExist()
    }

    /**
     * La mesure de ce qu'ENTER produit : **la grande valeur change de côté du trait.**
     *
     * C'est le seul invariant qui tienne à toutes les tailles d'écran, et c'est bien lui
     * qui porte le sens : sous le trait, le nombre est encore sous le doigt ; au-dessus, il
     * est dans la pile. Une première version exigeait en plus que le trait ne bouge pas,
     * le nombre montant seul — il fallait pour cela réserver sous le trait une zone de
     * frappe vide de la hauteur d'une valeur, et sur un écran de 360 × 640 la pile n'avait
     * alors plus de place du tout. Le rapport des deux change de la même façon dans les
     * deux cas ; c'est donc lui, et lui seul, qu'on vérifie.
     */
    @Test
    fun enterFaitPasserLaValeurDeLAutreCoteDuTrait() {
        val typing = rpn.copy(
            glyphs = "_⅃",
            labels = "BuZo",
            decimal = "6",
            entering = true,
            stack = listOf(StackLevel(glyphs = "⅃", labels = "Zo", decimal = "2")),
        )
        // Un état mutable : ENTER ne change que ce drapeau, et il faut mesurer la même
        // instance d'écran avant et après pour que les positions soient comparables.
        var state by mutableStateOf(typing)
        composeTestRule.setContent {
            GabuzomeuTheme(dynamicColor = false) {
                CalculatorScreen(
                    state = state,
                    widthSizeClass = WindowWidthSizeClass.Compact,
                    onKey = {},
                    onNotationChange = {},
                    onModeChange = {},
                    onSettingsChange = {},
                )
            }
        }

        val limitWhileTyping = displayNode(DisplayTags.STACK_LIMIT).getUnclippedBoundsInRoot().top
        val valueWhileTyping = displayNode(DisplayTags.GLYPHS).getUnclippedBoundsInRoot().top
        assertTrue(
            limitWhileTyping < valueWhileTyping,
            "la frappe est sous le trait : $limitWhileTyping puis $valueWhileTyping",
        )

        // ENTER : la frappe devient le sommet de la pile.
        state = state.copy(entering = false)
        composeTestRule.waitForIdle()

        val limitOnceStacked = displayNode(DisplayTags.STACK_LIMIT).getUnclippedBoundsInRoot().top
        val valueOnceStacked = displayNode(DisplayTags.GLYPHS).getUnclippedBoundsInRoot().top
        assertTrue(
            valueOnceStacked < limitOnceStacked,
            "la valeur empilée est au-dessus du trait : $valueOnceStacked puis $limitOnceStacked",
        )
    }

    @Test
    fun laPileNExistePasEnModeClassique() {
        setScreen(CalculatorUiState(glyphs = "_⅃", labels = "BuZo", decimal = "6"))

        displayNode(DisplayTags.STACK).assertDoesNotExist()
    }

    // ------------------------------------------------------------- presse-papiers

    private val six = CalculatorUiState(glyphs = "_⅃", labels = "BuZo", base4 = "12", decimal = "6")

    @Test
    fun unAppuiLongSurLAfficheurOuvreLesEcrituresCopiables() {
        setScreen(six)

        composeTestRule.onNodeWithTag(DisplayTags.ACTIONS).performTouchInput { longClick() }

        // Une entrée par écriture, et chacune retrouvée par un repère dérivé de l'écriture :
        // aucune assertion sur un libellé traduit, la CI tourne en en-US.
        ValueWriting.entries.forEach { writing ->
            composeTestRule.onNodeWithTag(ActionTags.copy(writing)).assertIsDisplayed()
        }
        // Les écritures copiables sont celles que l'afficheur montre : la base 4 n'est pas
        // proposée seule, elle ne voyage que dans « Partager… ».
        assertEquals(
            listOf(ValueWriting.GLYPHS, ValueWriting.LABELS, ValueWriting.DECIMAL),
            ValueWriting.entries,
        )
        // Le repère de l'item retiré, écrit en dur : l'énumération ne peut plus le nommer, et
        // c'est la seule façon de vérifier qu'il a bien disparu du menu.
        composeTestRule.onNodeWithTag("action-copy-base4").assertDoesNotExist()
        composeTestRule.onNodeWithTag(ActionTags.SHARE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ActionTags.PASTE).assertIsDisplayed()
    }

    @Test
    fun leMenuNeSOuvrePasAvantQuOnLeDemande() {
        setScreen(six)

        composeTestRule.onNodeWithTag(ActionTags.copy(ValueWriting.DECIMAL)).assertDoesNotExist()
    }

    @Test
    fun surUnAfficheurVideCopierEstInactifMaisCollerReste() {
        setScreen(CalculatorUiState())

        composeTestRule.onNodeWithTag(DisplayTags.ACTIONS).performTouchInput { longClick() }

        // Rien à copier, mais coller garde un sens : c'est précisément l'écran vide qu'on
        // remplit depuis le presse-papiers.
        composeTestRule.onNodeWithTag(ActionTags.copy(ValueWriting.DECIMAL)).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ActionTags.SHARE).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(ActionTags.PASTE).assertIsDisplayed()
    }

    @Test
    fun unNiveauDePileSeCopiePourLuiMeme() {
        setScreen(
            rpn.copy(
                glyphs = "_⅃",
                labels = "BuZo",
                decimal = "6",
                stack = listOf(StackLevel(glyphs = "⅃", labels = "Zo", base4 = "2", decimal = "2")),
            ),
        )

        composeTestRule.onNodeWithTag(DisplayTags.stackLevelActions(0))
            .performTouchInput { longClick() }

        // On ne colle pas au milieu d'une pile : l'item n'existe pas, il n'est pas grisé.
        composeTestRule.onNodeWithTag(ActionTags.copy(ValueWriting.LABELS)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ActionTags.PASTE).assertDoesNotExist()
    }

    @Test
    fun leMenuMeneALAide() {
        var opened = false
        setScreen(CalculatorUiState(), callbacks = Callbacks(onOpenHelp = { opened = true }))

        composeTestRule.onNodeWithTag(SettingsTags.MENU_BUTTON).performClick()
        composeTestRule.onNodeWithTag(SettingsTags.HELP).performClick()

        assertEquals(true, opened)
    }

    // ------------------------------------------------------------- défauts corrigés

    /**
     * Téléphone couché : largeur `Medium`, hauteur `Compact`. L'empilement y laissait aux sept
     * rangées NPI des touches de 10 à 15 dp ; elles doivent garder la taille d'une cible.
     */
    @Test
    fun enPaysageLesTouchesNpiGardentQuaranteHuitDp() {
        composeTestRule.setContent {
            GabuzomeuTheme(dynamicColor = false) {
                CalculatorScreen(
                    state = rpn,
                    widthSizeClass = WindowWidthSizeClass.Medium,
                    heightSizeClass = WindowHeightSizeClass.Compact,
                    onKey = {},
                    onNotationChange = {},
                    onModeChange = {},
                    onSettingsChange = {},
                    modifier = Modifier.requiredSize(width = 800.dp, height = 360.dp),
                )
            }
        }

        listOf(KeyAction.Digit('7'), KeyAction.Enter).forEach { action ->
            composeTestRule.onNodeWithTag(KeypadTags.of(action)).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun uneErreurEstAnnonceeParTalkBack() {
        setScreen(CalculatorUiState(error = EvalError.DIVISION_BY_ZERO))

        displayNode(DisplayTags.ERROR)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            )
    }

    @Test
    fun unResultatEstAnnonceParTalkBack() {
        setScreen(six.copy(showingResult = true))

        displayNode(DisplayTags.GLYPHS)
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            )
    }

    @Test
    fun uneSaisieEnCoursNEstPasRelueAChaqueChiffre() {
        setScreen(six)

        displayNode(DisplayTags.GLYPHS)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /**
     * Ouvrir le menu ne lit plus le presse-papiers (ce qui affichait l'avertissement
     * d'Android 12+) : le texte n'est lu qu'au clic sur « Coller ».
     */
    @Test
    fun collerLitLePressePapiersAuClic() {
        var pasted: String? = null
        val activity = composeTestRule.activity
        activity.runOnUiThread {
            activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("test", "12"))
        }
        setScreen(six, callbacks = Callbacks(onPaste = { pasted = it }))

        composeTestRule.onNodeWithTag(DisplayTags.ACTIONS).performTouchInput { longClick() }
        composeTestRule.waitUntil {
            runCatching {
                composeTestRule.onNodeWithTag(ActionTags.PASTE).assertIsEnabled()
            }.isSuccess
        }
        composeTestRule.onNodeWithTag(ActionTags.PASTE).performClick()
        composeTestRule.waitUntil { pasted != null }

        assertEquals("12", pasted)
    }
}
