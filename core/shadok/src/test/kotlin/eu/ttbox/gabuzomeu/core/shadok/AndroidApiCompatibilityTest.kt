package eu.ttbox.gabuzomeu.core.shadok

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Les modules `core` sont du Kotlin pur, compilé contre le JDK : Android Lint (`NewApi`)
 * ne les inspecte pas. Une API Java absente d'Android 12 (minSdk 31) y passe donc la
 * compilation et les tests JVM, puis plante sur l'appareil.
 *
 * C'est arrivé deux fois avec `BigInteger.TWO` (API 33) : dans [Rational], puis dans
 * [ShadokConverter], où un `NoSuchFieldError` à l'initialisation de la classe rendait
 * inutilisables la calculatrice, le quiz et l'aide. Seuls les tests instrumentés de la CI
 * l'avaient vu. Ce test le voit en quelques millisecondes, sur la JVM.
 *
 * La liste vise les API Java 9+ courantes qu'Android 12 n'a pas ; elle s'enrichit au fil
 * des pièges rencontrés.
 */
class AndroidApiCompatibilityTest {

    /** Motif interdit → raison, telle qu'affichée en cas d'échec. */
    private val forbidden = mapOf(
        "BigInteger.TWO" to "API 33 : écrire BigInteger.valueOf(2)",
        "BigDecimal.TWO" to "API 33 : écrire BigDecimal.valueOf(2)",
        "List.of(" to "API 30 sur Android, mais à proscrire : listOf()",
        "Set.of(" to "à proscrire : setOf()",
        "Map.of(" to "à proscrire : mapOf()",
    )

    @Test
    fun `les modules core n'utilisent pas d'API Java absente d'Android 12`() {
        // Le répertoire de travail des tests est celui du module (core/shadok) : on remonte
        // à core/ pour couvrir aussi core/eval.
        val coreDir = File("..").canonicalFile
        val sources = coreDir.walkTopDown()
            .filter {
                it.isFile && it.extension == "kt" &&
                    it.path.contains("src${File.separator}main")
            }
            .toList()
        assertTrue(sources.isNotEmpty(), "aucune source trouvée sous $coreDir")

        val violations = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val code = line.substringBefore("//").trimStart()
                if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                forbidden.entries.firstOrNull { code.contains(it.key) }?.let { (pattern, reason) ->
                    "${file.relativeTo(coreDir)}:${index + 1} : $pattern ($reason)"
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "API absentes d'Android 12 :\n" + violations.joinToString("\n"),
        )
    }
}
