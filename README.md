# GaBuZoMeu — la calculatrice en base 4 des Shadoks

[![CI](https://github.com/tt-box/gabuzomeu-android/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/tt-box/gabuzomeu-android/actions/workflows/ci.yml)
[![Couverture](https://codecov.io/gh/tt-box/gabuzomeu-android/branch/main/graph/badge.svg)](https://codecov.io/gh/tt-box/gabuzomeu-android)
[![CVE](https://github.com/tt-box/gabuzomeu-android/actions/workflows/security.yml/badge.svg?branch=main)](https://github.com/tt-box/gabuzomeu-android/actions/workflows/security.yml)
[![Release](https://github.com/tt-box/gabuzomeu-android/actions/workflows/release.yml/badge.svg)](https://github.com/tt-box/gabuzomeu-android/actions/workflows/release.yml)
[![Dernière version](https://img.shields.io/github/v/release/tt-box/gabuzomeu-android?include_prereleases&sort=semver&label=version)](https://github.com/tt-box/gabuzomeu-android/releases)
[![Dependabot](https://img.shields.io/badge/Dependabot-actif-025E8C?logo=dependabot)](https://github.com/tt-box/gabuzomeu-android/network/updates)
[![Kotlin](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Ftt-box%2Fgabuzomeu-android%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.kotlin&label=Kotlin&logo=kotlin&color=7F52FF)](https://kotlinlang.org)
[![API min](https://img.shields.io/badge/dynamic/toml?url=https%3A%2F%2Fraw.githubusercontent.com%2Ftt-box%2Fgabuzomeu-android%2Fmain%2Fgradle%2Flibs.versions.toml&query=%24.versions.minSdk&label=API%20min&logo=android&color=3DDC84)](https://developer.android.com/tools/releases/platforms)

> Quand il n'y a pas de Shadoks, on dit **GA**
> Quand il y a un shadok de plus, on dit **BU**
> Quand il y a encore un shadok de plus, on dit **ZO**
> Et quand il y a encore un autre, on dit **MEU**.

Ne disposant que de quatre mots, les Shadoks comptent en base 4. Cette application
convertit dans les deux sens, en temps réel, entre l'écriture décimale et l'écriture
Shadok — glyphes et noms prononcés.

| Valeur | 0 | 1 | 2 | 3 |
|---|---|---|---|---|
| Nom | `Ga` | `Bu` | `Zo` | `Meu` |
| Glyphe | ◯ | _ | ⅃ | ◿ |

Ainsi 6 s'écrit `12` en base 4, donc **`BuZo`** — ou `_⅃`.

Trois modes de calcul, par difficulté croissante :

| Mode | Ce que c'est | `Bu + Zo × Meu` puis `POMPER ↵` |
|---|---|---|
| **Simple** | La machine de l'affiche : quatre chiffres, quatre opérateurs. Exécution immédiate, sans priorité. | 9 |
| **Classique** | Notation infixe, avec parenthèses et priorité des opérateurs. | 7 |
| **Polonaise inverse** | La pile des HP : `x↔y`, `x↓`, `±`. | — |

Les deux premiers ne diffèrent pas par le nombre de touches mais par la machine : en
exécution immédiate, chaque opérateur résout le précédent au lieu de l'accumuler.

**`POMPER ↵`** est la touche qui fait avancer la machine, et elle porte le même nom dans les
trois modes — « il vaut mieux pomper même s'il ne se passe rien ». Ce qu'elle *fait* diffère,
évaluer ou empiler, et c'est sa description d'accessibilité qui le dit.

La leçon d'origine, celle qui a inspiré cette application, est en ligne :
[« Comment compter comme les Shadoks ? »](https://www.youtube.com/watch?v=lP9PaDs2xgQ),
chaîne Archive INA. L'application y mène depuis son écran « Comprendre les Shadoks ».

Voir [`docs/shadok-reference.md`](docs/shadok-reference.md) pour la table de référence
complète, les règles de saisie et la convention du widget.

## Prérequis

- **JDK 17**
- **Android SDK** avec la plateforme `android-37.1` (`compileSdk` 37.1)
- Rien d'autre : le wrapper Gradle est versionné.

## Compiler et tester

```bash
# Logique métier seule — Kotlin pur, aucune dépendance Android, quelques secondes
./gradlew :core:shadok:test :core:eval:test

# Build complet : compilation, lint (en warningsAsErrors), tests unitaires
./gradlew build

# Formatage
./gradlew spotlessApply     # corrige
./gradlew spotlessCheck     # vérifie

# Sur un appareil ou un émulateur (API 31 minimum)
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

## Architecture

```
:core:shadok   Kotlin pur — chiffres Shadok, Rational, conversion base 4, horloge
:core:eval     Kotlin pur — lexique, analyse, évaluation exacte, lecture du presse-papiers
:app           Compose Material 3, ViewModel, DataStore, tuile Réglages rapides
:widget        Glance AppWidget — les trois horloges Shadok
```

```
:core:shadok  ←  :core:eval  ←  :app  →  :widget  →  :core:shadok
```

Les deux modules `:core` **n'ont aucune dépendance Android**. C'est délibéré : toute la
logique de conversion et de calcul se teste sur la JVM, sans émulateur ni Robolectric.

Deux choix structurants méritent une note.

**L'arithmétique est exacte.** Le type numérique interne est une fraction (`Rational`),
pas un flottant ni un `BigDecimal`. Ce n'est pas de la coquetterie : comme 4 = 2², une
fraction irréductible `p/q` a un développement fini en base 4 **si et seulement si `q`
est une puissance de deux**. Un tiers reste donc exactement un tiers et se rend
`Ga.BuBuBu…` — son vrai développement périodique — au lieu de propager les chiffres de
queue d'un arrondi décimal. Quand un développement ne termine pas, l'affichage le dit
avec un `≈` plutôt que de présenter un arrondi comme une valeur exacte.

**Une seule source de vérité.** `ExpressionBuffer` contient l'expression ; les trois
lignes affichées en sont des projections pures. La persistance stocke la *frappe*, que
la restauration rejoue à travers les mêmes règles de saisie — un état stocké ne peut donc
pas reconstruire un tampon invalide.

## Signature de la version release

La clé de release est une clé **ECDSA P-256** (secp256r1), au format PKCS12, signée
en v2 + v3/v3.1 (+ v4). Pour en générer une :

```bash
keytool -genkeypair -keystore gabuzomeu-release.p12 -storetype PKCS12 \
  -alias gabuzomeu-release -keyalg EC -groupname secp256r1 \
  -sigalg SHA256withECDSA -validity 10000
```

En PKCS12, le mot de passe de la clé est celui du keystore.

Ne jamais mettre d'identifiants dans le dépôt. En local, créer un `keystore.properties`
à la racine (il est dans `.gitignore`) :

```properties
storeFile=/chemin/vers/gabuzomeu-release.p12
storePassword=…
keyAlias=…
keyPassword=…
```

En CI, utiliser les variables d'environnement `GABUZOMEU_STORE_FILE`,
`GABUZOMEU_STORE_PASSWORD`, `GABUZOMEU_KEY_ALIAS`, `GABUZOMEU_KEY_PASSWORD`.

Sans l'un ni l'autre, `assembleRelease` produit un APK non signé — suffisant pour
vérifier que R8 n'a rien supprimé de nécessaire.

## Intégration et livraison continues

Quatre workflows GitHub Actions, dans `.github/workflows/` :

| Workflow | Déclencheur | Rôle |
|---|---|---|
| `ci.yml` | push sur `main`, chaque pull request | Tests JVM de la logique métier, puis `spotlessCheck`, `detekt`, `build` (compilation + lint + tests) et APK release. Un troisième job lance les tests instrumentés sur un émulateur API 31. |
| `release.yml` | push d'un tag `1.2.3` / `v1.2.3` (reprise manuelle possible) | Rejoue toutes les vérifications, construit l'AAB et l'APK **signés**, plus un APK **non signé** (`-unsigned.apk`, à signer avec sa propre clé), et crée la release GitHub avec les artefacts et leurs empreintes SHA-256. |
| `security.yml` | push sur `main`, chaque pull request, chaque lundi | OSV-Scanner contrôle les CVE connues des dépendances du catalogue (badge « CVE ») ; le graphe Gradle complet est soumis à GitHub pour les alertes Dependabot. |
| `dependabot-automerge.yml` | PR ouverte par Dependabot | Arme l'auto-merge (squash) : la PR est fusionnée dès que les vérifications obligatoires sont vertes. |

**Couverture** : [Kover](https://github.com/Kotlin/kotlinx-kover) instrumente les tests
unitaires des quatre modules ; `./gradlew koverXmlReport` (ou `koverHtmlReport`) produit
le rapport agrégé dans `build/reports/kover/`, que la CI publie sur Codecov (via OIDC,
sans secret). Les tests instrumentés sur émulateur n'y sont pas comptés.

**Dépendances** : Dependabot (`.github/dependabot.yml`) propose chaque lundi les mises à
jour Gradle et GitHub Actions, regroupées par famille. Une fois la CI verte, la PR est
fusionnée automatiquement. Cela suppose, dans les réglages du dépôt :

- *Settings ▸ General ▸ Allow auto-merge* coché ;
- une règle de protection de `main` exigeant les vérifications `Logique métier (JVM)`,
  `Build, lint et style`, `Tests instrumentés (émulateur API 31)` et `CVE (OSV-Scanner)`.
  Sans vérification obligatoire, l'auto-merge fusionnerait **immédiatement**.

Publier une version : il suffit de pousser un tag, le workflow crée la release.
Ne pas créer la release à la main depuis l'interface GitHub.

```bash
git tag v1.0.0
git push origin v1.0.0
```

Le tag pilote le numéro de version : `v1.2.3` produit un artefact `1.2.3`. Le
`versionCode`, lui, est le nombre de commits — monotone par construction.

Secrets à définir dans les paramètres du dépôt pour que la release soit signée
(`Settings ▸ Secrets and variables ▸ Actions`) :

| Secret | Contenu |
|---|---|
| `KEYSTORE_BASE64` | le keystore encodé : `base64 -w0 gabuzomeu-release.p12` |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias de la clé |
| `KEY_PASSWORD` | mot de passe de la clé |

En leur absence, la release **échoue** : un APK non signé ne s'installe pas. Le workflow
vérifie aussi que l'APK et l'AAB sont signés par le certificat attendu (empreinte SHA-256
épinglée dans `release.yml`).

## Historique

Ce projet a été entièrement réécrit. La version d'origine (2012-2014) était un fork de
l'AOSP Calculator2 en projet Eclipse ADT construit par Maven : `minSdk 14`,
`targetSdk 16`, Java 1.6, thème Holo, aucun Gradle. Elle n'était plus compilable — dépôt
de plugins disparu, deux dépendances à installer à la main — et environ 60 % de son code
était mort ou cassé.

Cet état reste accessible sous le tag **`legacy-adt`** :

```bash
git show legacy-adt:src/eu/ttbox/gabuzomeu/service/GabuzomeuConverter.java
```

La réécriture a corrigé au passage plusieurs bugs de fond, chacun couvert par un test —
notamment la conversion des parties fractionnaires, qui n'avait jamais été implémentée
(le code produisait `Ga.BuBu` pour 0.5, et le test de l'époque en attendait `Ga.Bu`, ce
qui est faux aussi : la bonne réponse est `Ga.Zo`).

## Licence

Le code d'origine dérive de l'AOSP Calculator2, sous licence Apache 2.0.
