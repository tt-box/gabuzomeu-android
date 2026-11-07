package eu.ttbox.gabuzomeu.ui.calculator.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.ttbox.gabuzomeu.R
import eu.ttbox.gabuzomeu.ui.ActionTags
import eu.ttbox.gabuzomeu.ui.calculator.CalculatorUiState
import eu.ttbox.gabuzomeu.ui.calculator.StackLevel
import eu.ttbox.gabuzomeu.ui.shadok.ShadokGlyphText
import kotlinx.coroutines.launch

/**
 * Une écriture d'une valeur, telle qu'on peut la **copier**.
 *
 * La base 4 n'y figure pas : les trois écritures proposées sont celles que l'afficheur montre,
 * et `12` pour 6 était le seul item du menu qui n'apparaissait nulle part à l'écran. Elle
 * continue de voyager dans « Partager… », qui donne les quatre d'un coup — c'est là qu'elle
 * sert, puisqu'elle se relit sans police particulière.
 */
enum class ValueWriting {
    GLYPHS,
    LABELS,
    DECIMAL,
}

/**
 * Une valeur affichée, dans ses quatre écritures.
 *
 * Le même type sert à X et à un niveau de pile : ce qui se copie ne dépend pas de l'endroit
 * d'où on le copie.
 *
 * Quatre écritures pour trois items de copie : [base4] n'est pas copiable seule, mais reste
 * nécessaire au partage.
 */
data class CopyableValue(
    val glyphs: String,
    val labels: String,
    val base4: String,
    val decimal: String,
) {

    fun textFor(writing: ValueWriting): String = when (writing) {
        ValueWriting.GLYPHS -> glyphs
        ValueWriting.LABELS -> labels
        ValueWriting.DECIMAL -> decimal
    }

    /** Rien à copier : l'afficheur est vide. Coller, en revanche, reste possible. */
    val isEmpty: Boolean get() = decimal.isEmpty() && glyphs.isEmpty()

    companion object {
        fun of(state: CalculatorUiState): CopyableValue = CopyableValue(
            glyphs = state.glyphs,
            labels = state.labels,
            base4 = state.base4,
            decimal = state.decimal,
        )

        fun of(level: StackLevel): CopyableValue = CopyableValue(
            glyphs = level.glyphs,
            labels = level.labels,
            base4 = level.base4,
            decimal = level.decimal,
        )
    }
}

/**
 * Rend [content] copiable : un appui — long ou simple — ouvre le menu des actions.
 *
 * L'appui long est le geste attendu pour copier ; le simple appui ouvre le même menu parce
 * qu'un afficheur en lecture seule n'a rien d'autre à faire d'un tapotement, et que ça rend
 * la fonctionnalité trouvable sans la deviner.
 *
 * `onLongClickLabel` n'est pas décoratif : c'est lui qui fait annoncer l'action par TalkBack
 * et la rend atteignable autrement qu'au doigt. La ligne de glyphes appliquant
 * `clearAndSetSemantics`, le geste se pose ici, sur l'ancêtre — jamais sur elle.
 *
 * @param onPaste `null` là où coller n'a pas de sens : on ne colle pas dans un niveau de pile
 *   enfoui, alors l'item disparaît au lieu d'être grisé.
 */
@Composable
fun DisplayActions(
    value: CopyableValue,
    tag: String,
    modifier: Modifier = Modifier,
    onPaste: ((String) -> Unit)? = null,
    onCopied: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag(tag)
            .combinedClickable(
                onClick = { expanded = true },
                onLongClick = { expanded = true },
                onLongClickLabel = stringResource(R.string.display_actions),
            ),
    ) {
        content()
        ActionsMenu(
            expanded = expanded,
            value = value,
            onPaste = onPaste,
            onCopied = onCopied,
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun ActionsMenu(
    expanded: Boolean,
    value: CopyableValue,
    onPaste: ((String) -> Unit)?,
    onCopied: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        CopyItems(value, onDismiss, onCopied)

        HorizontalDivider()

        // Absent, et non grisé, là où coller n'a pas de sens : on n'écrit pas au milieu
        // d'une pile.
        if (onPaste != null) PasteItem(expanded, onPaste, onDismiss)

        ShareItem(value, onDismiss)
    }
}

/** Les écritures copiables, chacune avec l'aperçu de ce qu'elle copiera. */
@Composable
private fun CopyItems(value: CopyableValue, onDismiss: () -> Unit, onCopied: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.app_name)

    ValueWriting.entries.forEach { writing ->
        val text = value.textFor(writing)
        DropdownMenuItem(
            text = { Text(stringResource(writing.labelRes())) },
            trailingIcon = { WritingPreview(writing, text) },
            enabled = text.isNotEmpty(),
            onClick = {
                onDismiss()
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, text)))
                    // Android 13 affiche lui-même une confirmation de copie : en ajouter une
                    // ferait doublon sur la grande majorité des appareils.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onCopied()
                }
            },
            modifier = Modifier.testTag(ActionTags.copy(writing)),
        )
    }
}

/**
 * Coller — actif seulement si le presse-papiers contient du texte.
 *
 * À l'ouverture du menu, on ne consulte que la **description** du presse-papiers (son type
 * MIME) : depuis Android 12, en lire le contenu affiche le message système « collé depuis le
 * presse-papiers », et ce message apparaissait à chaque ouverture du menu, même pour copier.
 * Le texte n'est donc lu qu'au clic sur « Coller ». Un texte qui n'est pas un nombre est
 * ignoré par le ViewModel, comme toute frappe refusée.
 */
@Composable
private fun PasteItem(expanded: Boolean, onPaste: (String) -> Unit, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    val clipboardManager = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val scope = rememberCoroutineScope()
    var hasText by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        hasText = expanded && clipboardManager?.announcesText() == true
    }

    DropdownMenuItem(
        text = { Text(stringResource(R.string.paste)) },
        enabled = hasText,
        onClick = {
            onDismiss()
            scope.launch { clipboard.plainTextOrNull()?.let(onPaste) }
        },
        modifier = Modifier.testTag(ActionTags.PASTE),
    )
}

/** Partager — les **quatre** écritures d'un coup, là où copier n'en donne qu'une. */
@Composable
private fun ShareItem(value: CopyableValue, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_title)
    val shared = stringResource(
        R.string.share_value,
        value.decimal,
        value.labels,
        value.glyphs,
        value.base4,
    )

    DropdownMenuItem(
        text = { Text(stringResource(R.string.share)) },
        enabled = !value.isEmpty,
        onClick = {
            onDismiss()
            context.startActivity(Intent.createChooser(shareIntent(shared), shareTitle))
        },
        modifier = Modifier.testTag(ActionTags.SHARE),
    )
}

/**
 * L'aperçu de ce que l'item copiera — le menu s'explique ainsi tout seul.
 *
 * Les glyphes passent par [ShadokGlyphText] et non par un `Text` : `⅃` (U+2143) n'est pas
 * garanti dans les polices système, et l'aperçu s'afficherait en tofu là où l'afficheur, lui,
 * dessine ses vecteurs. C'est aussi pourquoi « Partager… » joint la base 4 au message : elle,
 * elle se relit partout.
 */
@Composable
private fun WritingPreview(writing: ValueWriting, text: String) {
    val style = MaterialTheme.typography.labelLarge
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val modifier = Modifier.widthIn(max = PREVIEW_MAX_WIDTH)

    if (writing == ValueWriting.GLYPHS) {
        ShadokGlyphText(
            expression = text,
            // L'item porte déjà son libellé : l'aperçu n'a rien à annoncer de plus.
            semanticsLabel = "",
            style = style,
            color = color,
            operatorColor = MaterialTheme.colorScheme.tertiary,
            modifier = modifier,
        )
    } else {
        Text(text = text, style = style, color = color, maxLines = 1, modifier = modifier)
    }
}

private fun ValueWriting.labelRes(): Int = when (this) {
    ValueWriting.GLYPHS -> R.string.copy_glyphs
    ValueWriting.LABELS -> R.string.copy_labels
    ValueWriting.DECIMAL -> R.string.copy_decimal
}

private fun shareIntent(text: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, text)
}

/**
 * Le presse-papiers annonce-t-il du texte ?
 *
 * Seule la description est lue — jamais le contenu — pour ne pas déclencher l'avertissement
 * d'accès au presse-papiers d'Android 12+.
 */
private fun ClipboardManager.announcesText(): Boolean {
    val description = primaryClipDescription ?: return false
    return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
}

/**
 * Le presse-papiers en texte brut, ou `null`.
 *
 * `Clipboard` n'offre aucun raccourci texte — son unique extension est `firstUriOrNull` —
 * d'où le passage par `ClipData`. Un item sans texte (une image, un URI seul) rend `null`, ce
 * qui grise « Coller » : exactement ce qu'on veut, sans avoir à distinguer les types.
 */
private suspend fun Clipboard.plainTextOrNull(): String? {
    val data = getClipEntry()?.clipData ?: return null
    if (data.itemCount == 0) return null
    return data.getItemAt(0).text?.toString()
}

private val PREVIEW_MAX_WIDTH = 140.dp
