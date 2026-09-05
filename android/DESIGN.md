# Guardia design system

**Signal Cyan on obsidian, with shadcn's bones.**

The structure is shadcn/ui's — a neutral ramp carrying background / card / muted / border, tight
radii, dense type, a hairline instead of a shadow — because that is what keeps a screen with twelve
cards on it legible. The *character* is the app's own: one electric cyan that appears where the
system is watching or the user acts, ambient brand light drifting behind every page, and motion
that always means something happened.

Two rules hold the whole thing together:

1. **Cyan means alive.** The primary action, the status gauge, the "live" badge, the selected tab.
   If a screen has cyan somewhere that isn't status or the primary action, it's wrong.
2. **One thing moves at a time.** The aurora drifts so slowly it reads as light rather than motion;
   the status gauge is the only element that visibly animates on its own. Because nothing else
   moves, that movement means "a check is running".

## Colour (`ui/theme/Color.kt`)

The neutral ramp is deliberately not pure gray — it carries a few degrees of cyan, so brand light
bleeding across a surface looks like it belongs rather than like a coloured film over gray. In dark
mode that is the difference between "an app with a neon accent" and "an instrument lit from inside".

| Token | Light | Dark | Meaning |
|---|---|---|---|
| `brand` | `#0F766E` | `#2CF5D8` | Signal Cyan. The system is watching, or this is the thing to press |
| `background` | `#F6FAFA` | `#050B0D` | Page canvas |
| `card` / `cardTop` / `cardBottom` | white → `#FAFDFD` | `#131E21` → `#0A1214` | A card is a two-stop fill, lighter at the top |
| `borderHighlight` | white | 12% white | The lit top edge that makes a card read as glass |
| `muted` / `mutedForeground` | `#EFF4F4` / `#5A6A6A` | `#162124` / `#8FA3A5` | Secondary fills and secondary text |
| `success` | emerald | emerald | Granted, verified, trusted, low risk |
| `warning` | amber | amber | Paused, guest, medium risk |
| `destructive` | rose | rose | Intruder, blocked, missing permission |
| `info` | blue | blue | Security tooling, neutral notices |
| `violet` | violet | violet | What Guardia *does* in response |

The expressive tokens — `gradientBrand`, `gradientDanger`, `gradientCard`, `aurora`, `brandGlow` —
are exposed as colour *stops* rather than `Brush`es, so a caller can build a linear, radial or sweep
gradient from the same ramp without the theme deciding the geometry.

**Light mode runs one step darker than Tailwind's defaults.** The 600 steps are what shadcn reaches
for on white, and on this page they measured 3.0–4.5:1 — under what 1.4.3 asks of 14sp text. The
700s keep the hue and clear it. Same reasoning for `gradientBrandAction` and `gradientDangerAction`:
`gradientBrand` is picked for how it looks and its bright end put a white button label at **1.86:1**,
so anything with text on it uses the trimmed ramp and decoration keeps the full one.
`ContrastTest` holds every one of these ratios, because a colour chosen against a mock drifts and a
colour chosen against a number does not.

Read everything through `Guardia.colors` (`ui/theme/Theme.kt`), which also maps the Material 3 roles
so stock Material widgets land on the right colours.

## Motion (`ui/components/Motion.kt`)

Every animation is gated on `rememberReducedMotion()`, which reads the system animator scale. When
the user has turned animations off, the ambient pieces are not paused — they are never composed, so
no animation clock starts.

- **`AuroraBackdrop`** — three wide fields of brand light drifting on unrelated periods (23s / 31s /
  19s, so the composition never visibly repeats). Each blob is a remembered brush moved inside a
  `graphicsLayer` *block*, which reads its animation during the layer phase instead of during
  composition: an animation that runs for the whole life of the app recomposes nothing and rebuilds
  no gradients. Drawn once at the root; every screen keeps a transparent container.
- **`Modifier.animateEntrance(index, enabled)`** — staggered fade-up, 45ms apart, capped at seven
  steps. `enabled` exists for lazy lists: an item scrolled back into view is a fresh composition, so
  callers pass false once the screen has settled and the entrance stays at the moment of arrival.
- **`Modifier.glow(color, shape)`** — a coloured halo via the platform's tinted shadow, which is
  GPU-drawn. Tinting landed in API 28; on 26–27 it degrades to a normal shadow, which is why a glow
  is never the thing carrying meaning.
- **`Modifier.shimmer()`** — light travelling across a loading placeholder.
- **`animatedCount(value)`** — counts up instead of swapping, so a changed number is visible without
  a badge to announce it.
- Touch: springs; buttons compress to 0.98, cards to 0.985. Navigation: slide-fade shared axis.
  State changes pair animation with haptics.

## Surfaces and chrome

A card is a two-stop vertical fill under a border that fades from a lit top edge into the ordinary
hairline — glass catching light from above. Corners are 12dp. `ShCard(glowColor = …)` can put a halo
behind at most one card on a screen; use it sparingly, or it stops meaning anything.

**Chrome does not seal the page.** The header, the pinned action bar and the bottom navigation are
all transparent or translucent, because an opaque strip with a hairline rule cuts the backdrop off
at a hard edge and makes every screen open and close on a slab.

- **Header** (`GuardiaScaffold`) — no fill and no rule. A vertical scrim, solid behind the status
  bar and faded to nothing by the bottom of the title row, keeps the title legible while content
  scrolls up and dissolves underneath it. Actions and the back arrow are `GlassIconButton`s:
  translucent discs with a hairline, because a bare icon vanishes against the aurora and a filled
  button is far too loud for a back arrow.
- **Bottom navigation** — a floating bar, inset from all three edges and translucent, so the aurora
  keeps moving under it. The selected tab gets a brand capsule and a halo; it is the only chrome in
  the app allowed to carry the brand colour. Scrolling screens clear it with
  `Spacing.bottomBarClearance`.
- **The dashboard hero is not a card.** It sits directly on the backdrop with a wide brand glow
  bleeding out behind the gauge. Putting a border around it made it a peer of the battery readout
  below; the whole point is that it is the subject, not the first item in a list. The state word is
  set at display size for the same reason.

## Type (`ui/theme/Type.kt`)

Platform sans at Tailwind's steps; headings `font-semibold` with tight tracking; **body defaults to
14sp**, which is a large part of why the layout reads as tidy. JetBrains Mono for numbers and machine
states (`StatusReadout`, `DataDisplay`, `MonoCaption`). Space Grotesk stays for the wordmark.

## Shape (`ui/theme/Shape.kt`)

One radius variable: `sm 4 / md 6 / lg 8 / xl 12`. Cards `xl`, buttons/inputs/badges `md`. Circles
are reserved for avatars, status dots, progress tracks and the PIN keypad.

## Components

- `ui/components/Shadcn.kt` — the primitives (`ShButton`, `ShCard`, `ShBadge`, `ShInput`, `ShTabs`,
  `ShAlert`, `ShStatCard`, `ShIconBox`, …), each a translation of the shadcn component with the
  original class strings quoted in its doc comment.
- `ui/components/MaterialAdapters.kt` — composables named `Button`, `OutlinedTextField`, `Switch`,
  `FilterChip` that shadow the Material 3 ones, because a Material button is a pill whatever
  `MaterialTheme.shapes` says. A screen converts by changing one import.
- `ui/components/Kit.kt` + `GuardiaComponents.kt` — the app's original component names built on those
  primitives, so screens inherit the system without being rewritten.
- `ui/components/StatusOrb.kt` — the dashboard hero: track ring, brand-ramp indicator arc, two sonar
  rings on an offset cycle, and a comet riding the ring. The moving layers compose only while the
  guard runs.
- `ui/components/Disclosures.kt` — `rememberAccessibilityOptIn()`, the prominent disclosure Play
  requires before the accessibility service is enabled. Every entry point in the app routes through
  it, so the consent cannot be bypassed by taking a different path through the UI.

## Accessibility

The design leans on things a screen reader cannot see — a hairline seam, a gauge that pulses, a
tinted icon — so each one has a spoken equivalent rather than an apology.

- **A row is one node.** Settings rows hand their interaction to `toggleable` / `selectable` with a
  `Role`, so TalkBack says "Auto-lock, on, switch" instead of reading a label and then an unlabelled
  toggle. The trailing `ShSwitch` keeps a null callback: it is the *picture* of the state, not a
  second control. Rows are `heightIn(min = 48.dp)` and carry the platform ripple; a title-only row
  used to be 44dp with no press feedback at all.
- **Guard state is a live region.** The dashboard's state word is `LiveRegionMode.Polite`, so the
  guard stopping is announced rather than merely drawn. `StatusOrb` is `clearAndSetSemantics { }` —
  it says the same thing in a picture, and announcing it twice is worse than not at all.
- **Section labels are headings**, so a screen reader can jump between them instead of swiping
  through every row of a long settings page.
- **High contrast is a setting, not a default.** The hairline border is 1.2:1 — a seam, deliberately,
  which is what keeps twelve cards from reading as a grid of boxes, and well under the 3:1 of 1.4.11.
  Settings › Appearance › Accessibility swaps in `LightBorderHC` / `DarkBorderHC`. Only edges change:
  the fills stay, because a preference should not turn Guardia into a different app.

## Colour-coding

Settings categories are grouped by hue (`SettingsScreen.tintFor`): cyan is the guard itself, violet
is what happens in response, amber is what reaches outside the phone, blue is security tooling, green
is who's allowed, red is who never is, and device plumbing stays neutral. A long list of identical
gray icons has nothing for the eye to aim at.

## Pro

Guardia currently ships with every feature unlocked. `EntitlementManager.allFeaturesUnlocked` is the
single switch; `PremiumBadge` and `UpgradeButton` render nothing, and there is no paywall route or
subscription settings entry. The `premium = true` markers stay on the rows they belong to, so
restoring the paid tier means changing those three places, not re-annotating the app.

## The one exception

`ui/screens/decoy/DecoyScreen.kt` uses its own hard-coded colours and no backdrop: it must *not*
look like Guardia.
