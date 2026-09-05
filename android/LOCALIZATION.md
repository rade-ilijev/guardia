# Localization

Guardia ships in English only. This document is the groundwork for that not being permanent: what
is already in place, the conventions to follow, and the work that remains.

## What is already done

**Structure.** `res/xml/locales_config.xml` is declared on the application, so Android 13+ offers a
per-app language in system Settings and Play lists the app's languages. Adding a language is now
exactly one thing — a `values-xx/strings.xml` — with no manifest or build change to remember.
`supportsRtl="true"` is set, and the app bundle already splits by language.

**Everything the system renders.** All strings Android itself shows — the launcher label, the
foreground-service notification and its channel, the accessibility service description, the device
admin description, the Quick Settings tile, the home-screen widget — live in `strings.xml` already.
These are the ones that *must* be resources: nothing in the app gets a chance to format them.

**Locale correctness.** Several places were folding case or formatting numbers in the device's
locale where they meant ASCII. Those are fixed and the rule is below.

**A worked template.** `LockScreen.kt` and its `strings.xml` block are the reference. They cover the
three shapes that are easy to get wrong: a plural, positional format arguments, and strings read
from non-composable callbacks.

## What remains

Roughly 900 user-visible literals in Compose code. The bulk of it is concentrated:

| File | approx. literals |
| --- | --- |
| `ui/screens/settings/SettingsDetailScreen.kt` | 445 |
| `ui/screens/settings/SettingsScreen.kt` | 142 |
| `ui/screens/onboarding/OnboardingScreen.kt` | 117 |
| `ui/screens/dashboard/DashboardScreen.kt` | 66 |
| `ui/screens/people/PersonDetailScreen.kt` | 57 |
| everything else | the rest, in small handfuls |

Three files account for over half of it, so this is a shorter job than the total suggests. Do it a
screen at a time, keeping each screen's strings together in `strings.xml` under a comment naming the
screen, and never in one sweeping mechanical pass — a literal moved without reading it is how a
format argument goes missing.

## Conventions

**Key names** are `screen_element` in lower snake case: `lock_forgot_pin`, `recovery_title_backup`.
Strings shared across screens get a bare purpose name: `action_continue`, `action_cancel`. Resist
inventing a shared key for two strings that merely happen to read the same today — "Cancel" on a
destructive dialog and "Cancel" on a form can diverge in a language with grammatical gender.

**Plurals are plurals.** English has two forms; Polish has four and Arabic six, so no amount of
appending an "s" is correct:

```xml
<plurals name="lock_subtitle_attempts_left">
    <item quantity="one">Incorrect PIN - %1$d attempt left</item>
    <item quantity="other">Incorrect PIN - %1$d attempts left</item>
</plurals>
```

Compose has no `stringResource` for plurals, so read them through the resources object:

```kotlin
val resources = LocalContext.current.resources
resources.getQuantityString(R.plurals.lock_subtitle_attempts_left, attemptsLeft, attemptsLeft)
```

**Arguments are positional** — `%1$s`, `%2$d`, never a bare `%s`. A translator has to be able to
move the number to wherever their language puts it.

**Never build a sentence by concatenation.** `"Locked for " + n + " seconds"` cannot be translated;
`<string name="...">Locked for %1$d seconds</string>` can.

**Hoist strings out of callbacks.** `stringResource` is `@Composable`; an `onClick` or an
`onValueChange` is not. Read the string in the composable body and capture it:

```kotlin
val wrongCode = stringResource(R.string.recovery_code_wrong)
...
onValueChange = { ...; message = wrongCode }
```

Reading it in the body is also what makes the text follow a language change, since the composable
recomposes when the configuration does.

**ViewModels don't hold English.** A view model that today returns `"PIN must be 4-6 digits"` should
return a message the UI can resolve — a resource id, or a sealed result the screen maps to one.
Injecting a `Context` to call `getString` works but freezes the string at the moment the view model
ran, which is wrong across a language change and untestable besides.

**Mark what must not be translated** with `translatable="false"`: the brand name, the developer's
name and email in the legal documents, and format placeholders such as the recovery code's
`XXXX-XXXX-XXXX`.

## Locale correctness (not the same thing as translation)

An app can be English-only and still be broken by the user's locale. The rule:

- **`Locale.ROOT` when the string is data** — anything compared against an ASCII literal, hashed,
  parsed, or stored. On a Turkish phone `"GOLDFISH".lowercase()` is `goldfısh` with a dotless i, so
  a locale-default fold silently stops matching. This bit the emulator/root detection in
  `IntegrityGuard`, the signature hex it compares, and the recovery-code canonicalisation in
  `PinManager` — where a credential that stops verifying when the user changes language would be
  the worst kind of bug.
- **`Locale.US` for machine-readable numbers**, such as the latitude/longitude of a safe zone: a
  comma decimal separator turns `52.5200, 13.4050` into `52,5200, 13,4050`.
- **The default locale when the text is for a person to read** — dates, times, relative time spans,
  and display case folding. `android.text.format.DateFormat.getTimeFormat(context)` rather than a
  hand-rolled pattern, so the user's 12/24-hour preference is honoured.
- **Sort user-visible names with a `Collator`**, never by `lowercase()`. Lowercase ordering puts
  every accented letter after Z, which makes a German or Swedish user's app list look broken.
- **Enum names are identifiers, not display text.** `PinType.PANIC.name.lowercase()` reads as
  `panıc` in Turkish. Map to a display string explicitly.

## Adding a language

1. Create `app/src/main/res/values-<code>/strings.xml` and translate the values.
2. Add `<locale android:name="<code>" />` to `res/xml/locales_config.xml`.
3. Check the layout at that language's longest strings — German runs about 30% longer than English —
   and in RTL if the language needs it.

Nothing else. No manifest edit, no Gradle edit.
