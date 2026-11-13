# Contract: Nocturne Design System (shared)

Package `com.codewave.qullqi.designsystem`. Values in [research.md R1](../research.md).

```kotlin
@Immutable data class QColors(page, bg, s1, s2, s3, ln, tx, tx2, ac, acT, acTx, neg, warn: Color, isDark: Boolean)
object QSpacing { val x2, x4, x6, x8, x10, x12, x14, x16, x18, x20, x22, x24, x28, x32, x36: Dp }
object QRadius { val xs = 6.dp; val sm = 8.dp; val md = 12.dp; val lg = 14.dp; val sheet = 20.dp }
object QMotion { push, tab, sheet, shake, pop, press: durations + easings (STYLEGUIDE §5) }
@Immutable data class QType(titleFlow, titleLg, titleSheet, titleBar, bodyLg, bodyMd, bodySm, caption, overline: TextStyle)

@Composable fun QullqiTheme(dark: Boolean = true, content: @Composable () -> Unit)
object Q { val colors: QColors @Composable get; val type: QType @Composable get }
```

## Components (M2 set)

| Composable | Rule (UI_GUIDELINES) |
|------------|----------------------|
| `QPrimaryButton(text, onClick, enabled)` | 1 dp `ac` border, `ac` 8 % fill, `ac` text, glow; pressed 26 %; disabled alpha .4; height 52; **never solid** |
| `QSecondaryButton`, `QTextButton` | §3.1 |
| `QTextField(value, onChange, label, help, valid: Boolean?)` | §3.5: 46–52 high, `s1`, `ln` border, live `ac`/`neg` border + help |
| `QPinBoxes(length=4, filled, error)` | §3.6: 54×62, current box `ac` border + glow, filled dot 12 dp `ac`; `shake` on error |
| `QCodeBoxes(length=6, value, error)` | §3.6 grid of 6 |
| `QKeypad(onDigit, onDelete, leftKey: (@Composable () -> Unit)?)` | 3×4, keys 54–58 high, `r.sm`; left key = fingerprint on login |
| `QFlowTopBar(step, total, onBack)` | §2.3 segmented 3 dp bar, current segment glow, `n/total` |
| `QBottomSheet(visible, onDismiss, content)` | §3.8: handle 32×4, top radius 20, scrim `#0e0f18` 60 %, max 82 % |
| `QToastHost` / `showToast(text)` | 1.9 s |
| `Modifier.shake(trigger)` | 420 ms keyframes −10, 9, −6, 4 dp |
| `QWordmark()` | `qullqi` Inter 500, −0.04em, followed by an `ac` dot |
| `QIcon(Ph.Fingerprint, …)` | Phosphor regular, sizes STYLEGUIDE §4 |

Screens use only `Q.colors`, `Q.type`, `QSpacing`, `QRadius`; no literal colors or sizes.
Changing a token or component signature is a shared-area change (coordinate with M4 owner).
