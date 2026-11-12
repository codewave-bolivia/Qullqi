# Qullqi Pay — Guía de estilo

Basada en el sistema de diseño **Nocturne** (`_ds/nocturne-…/styles.css`): fondo oscuro neutro azul-gris, Inter en peso medio, radios suaves y un acento que se usa como **línea y brillo**, no como relleno. Qullqi le suma un modo claro, un acento alternativo Electric Lime y una escala tipográfica para mobile.

Regla general: todo color, fuente, radio y sombra sale de un token. Nunca escribas un hex suelto en una pantalla.

---

## 1. Tipografía

**Familia única: Inter** (`--font-heading` y `--font-body`). Pesos permitidos: **400 (Regular)** y **500 (Medium)**. Nunca uses 600 o más: la jerarquía se construye con tamaño y espacio, no con negrita.

Los números de dinero siempre llevan `font-variant-numeric: tabular-nums` para que las cifras no salten al cambiar.

### 1.1 Escala

| Token | Tamaño / interlineado | Peso | Tracking | Uso |
| --- | --- | --- | --- | --- |
| `display.xl` | 60 / 60 | 500 | −0.045em | Saldo principal en Home |
| `display.lg` | 46–48 / 1.05 | 500 | −0.04em | Monto en Review, Resultado, Detalle, Cuenta, Resumen |
| `display.md` | 40–42 / 1.08 | 500 | −0.035em | Titular de bienvenida, monto en solicitud y comprobante |
| `amount.input` | 60 → 50 → 40 | 500 | −0.045em | Monto en teclado; se reduce con la longitud (>8 y >11 caracteres) |
| `title.tab` | 32 / 1.1 | 500 | −0.03em | Título de pestaña raíz (Actividad, Cobrar, Tarjetas) |
| `title.flow` | 28 / 1.15 | 500 | −0.025em | Pregunta de un paso (“¿Cuál es tu número de celular?”) |
| `title.result` | 28–30 / 1.12 | 500 | −0.03em | Título de pantalla de resultado |
| `title.lg` | 24–26 | 500 | −0.02em | Saludo en login, título del PIN pad |
| `title.md` | 22 | 500 | −0.02em | Nombre en perfil y en QR, título de cámara KYC |
| `title.sheet` | 20 | 500 | 0 | Título de bottom sheet y de estado vacío |
| `title.bar` | 18 | 500 | −0.01em | Título de la barra superior en pantallas apiladas |
| `amount.row` | 19 | 500 | −0.02em | Saldo de cuenta en tarjeta, montos de resumen, límites |
| `body.lg` | 15–15.5 | 400/500 | 0 | Botones (500), inputs, párrafos de introducción |
| `body.md` | 14.5 | 500 | 0 | Título de fila de lista, monto de fila |
| `body.sm` | 14 | 400 | 0 | Etiqueta de fila clave/valor, texto secundario de cuerpo |
| `caption` | 12–13 | 400 | 0 | Subtítulo de fila, metadatos, ayudas bajo inputs |
| `label.nav` | 11 | 500 | 0 | Etiquetas de la barra inferior |
| `overline` | 11 | 500 | +0.08em, MAYÚSCULAS | Encabezados de sección (“CUENTAS”, “ACTIVIDAD RECIENTE”) |
| `overline.accent` | 10.5–11 | 500 | +0.10–0.12em | Kicker en acento (“TU QR PERSONAL”, “VIRTUAL”) |

### 1.2 Reglas
- Interlineado de párrafos: 1.5. En titulares grandes, 1.05–1.15.
- El símbolo de moneda en el saldo va en `26/400` color `--tx2` y los decimales en `28` color `--tx2`; la parte entera es el foco.
- Alinea a la izquierda. Centra solo en estados de procesamiento o en el teclado.
- Escribe montos con el símbolo antes y un espacio: `Bs 1,245.80`, `$ 65.00`. Los signos van con espacio: `+ Bs 350.00`, `− Bs 24.50` (signo menos tipográfico U+2212).

### 1.3 Compose / SwiftUI
```kotlin
val Inter = FontFamily(Font(R.font.inter_regular, W400), Font(R.font.inter_medium, W500))
val DisplayXL = TextStyle(fontFamily = Inter, fontWeight = W500, fontSize = 60.sp,
    lineHeight = 60.sp, letterSpacing = (-0.045).em, fontFeatureSettings = "tnum")
val Overline = TextStyle(fontFamily = Inter, fontWeight = W500, fontSize = 11.sp, letterSpacing = 0.08.em)
```
```swift
extension Font { static let displayXL = Font.custom("Inter-Medium", size: 60).monospacedDigit() }
```

---

## 2. Color

### 2.1 Tokens semánticos

| Token | Oscuro (por defecto) | Claro | Uso |
| --- | --- | --- | --- |
| `--bg` | `#161826` (`--color-bg`) | `#e4e7f5` (neutral‑200) | Fondo de pantalla |
| `--s1` | `#232532` (`--color-surface`) | `#f3f5fe` (neutral‑100) | Tarjetas, listas, inputs, sheets |
| `--s2` | `#2c2e3c` | `#d9ddee` | Hover, chip de ícono, superficie dentro de un sheet |
| `--s3` | `#3f424d` (neutral‑800) | `#cfd3e5` (neutral‑300) | Pistas de progreso, bordes punteados, toggles apagados |
| `--ln` | texto al 16 % | `#161826` al 13 % | Bordes y separadores |
| `--tx` | `#e9e9ed` | `#161826` | Texto primario |
| `--tx2` | `#9397ab` (neutral‑500) | `#595d6c` (neutral‑700) | Texto secundario |
| `--ac` | `#9184d9` | `#796cbf` (accent‑600) | Acento: bordes de botón, íconos activos, brillos |
| `--ac-t` | `#2b2741` (accent‑900) | `#e7e5fe` (accent‑200) | Relleno teñido (avatar, chip de ícono, pastilla de navegación) |
| `--ac-tx` | `#d2cefd` (accent‑300) | `#5d5294` (accent‑700) | Texto sobre tinte; montos positivos |
| `--neg` | `oklch(0.72 0.13 25)` | `oklch(0.55 0.16 25)` | Errores, fallidos, acciones destructivas |
| `--warn` | `oklch(0.82 0.11 80)` | `oklch(0.56 0.12 70)` | Pendiente, en revisión, sin verificar |

### 2.2 Acento alternativo · Electric Lime
| Token | Oscuro | Claro |
| --- | --- | --- |
| `--ac` | `oklch(0.90 0.20 125)` | `oklch(0.52 0.15 130)` |
| `--ac-t` | `oklch(0.30 0.06 125)` | `oklch(0.92 0.10 125)` |
| `--ac-tx` | `oklch(0.90 0.17 125)` | `oklch(0.40 0.12 130)` |

### 2.3 Reglas de color
- **Nunca rellenes grandes áreas con el acento.** El acento va en bordes de 1 px, íconos, marcas cortas (16×2 px) y brillos (`box-shadow: 0 0 28px -12px var(--ac)`).
- Excepción permitida: el arte de la tarjeta virtual (`--color-section` → `--color-section-glow`), que funciona como un objeto con presencia.
- Montos de ingreso en `--ac-tx`; egresos en `--tx`; fallidos en `--tx2`.
- Estados: completado `--ac-tx`, pendiente `--warn`, fallido `--neg`.
- Sin negro ni blanco puros. El QR y el comprobante usan `#f3f5fe` y `#161826`.
- Contraste: el acento sobre fondo llega a ≥ 3:1 (chrome e íconos). Para texto de párrafo en acento usa `--ac-tx`.

---

## 3. Espaciado, radios y elevación

### 3.1 Espaciado (base 2 px)
`2 · 4 · 6 · 8 · 10 · 12 · 14 · 16 · 18 · 20 · 22 · 24 · 28 · 32 · 36`

| Uso | Valor |
| --- | --- |
| Margen horizontal de pantalla | 16 (listas) · 20 (títulos) · 24–28 (pasos de onboarding) |
| Padding de tarjeta | 14 (filas) · 16–22 (tarjetas destacadas) |
| Separación entre tarjetas | 8 |
| Separación encabezado de sección → contenido | 8–10 |
| Separación entre secciones | 24–32 |
| Padding vertical de fila (`--rp`) | compacto 8 · regular 12 · cómodo 14 |

### 3.2 Radios
| Token | Valor | Uso |
| --- | --- | --- |
| `r.xs` | 6 | Pastillas de estado, opciones de segmentado, casillas |
| `r.sm` | 8 | Botones, inputs, chips, botones de ícono, teclas |
| `r.md` | 10–12 | Chip de ícono en filas, logo de comercio |
| `r.lg` | 14 | Tarjetas, listas, tarjeta bancaria |
| `r.sheet` | 20 (solo arriba) | Bottom sheets |
| `r.full` | 50 % | Avatares, puntos, anillos |

### 3.3 Elevación
- En oscuro, la elevación es **un borde y oscuridad ambiental**, no sombras apiladas. Usa `--shadow-sm/md/lg`.
- Sheet: `0 -1px 0 var(--s3), 0 -16px 40px rgba(0,0,0,.4)`.
- Brillo de acento (estados activos o destacados): `0 0 24–30px -6…-12px var(--ac)`.

---

## 4. Iconografía

**Phosphor Icons** en estilo `regular`. `fill` se reserva para estados activos (barra inferior), sellos de verificación y checks.

| Contexto | Tamaño |
| --- | --- |
| Barra inferior | 22 |
| Botón de escaneo central | 26 |
| Botón de la barra superior | 21–22 |
| Acción rápida / tile | 22–24 |
| Chip de ícono en fila (40×40) | 20 |
| Ícono inline con texto | 13–18 |
| Ícono de resultado | 38–40 |

Íconos clave: `house`, `list-bullets`, `hand-coins`, `credit-card`, `scan`, `arrow-up-right` (salida), `arrow-down-left` (entrada), `arrows-left-right` (cambio), `fingerprint`, `seal-check`, `shield-check`, `snowflake`, `qr-code`.

---

## 5. Movimiento

| Token | Duración | Curva | Uso |
| --- | --- | --- | --- |
| `motion.push` | 300 ms | `cubic-bezier(.2,.8,.2,1)` | Apilar o volver (±26 px + fundido) |
| `motion.tab` | 250 ms | `ease` | Cambio de pestaña |
| `motion.sheet` | 320 ms | `cubic-bezier(.2,.9,.2,1)` | Bottom sheet |
| `motion.veil.out` | 450–520 ms | `cubic-bezier(.4,0,.2,1)` | Salida entre actividades |
| `motion.veil.in` | 600 ms | `cubic-bezier(.4,0,.2,1)` | Entrada entre actividades |
| `motion.enter` | 750 ms | `cubic-bezier(.2,.8,.2,1)` | Primera entrada al Home (18 px, escala .985) |
| `motion.stagger` | +80 ms | — | Bloques de Home y textos de resultado |
| `motion.pop` | 500 ms | `cubic-bezier(.2,.9,.3,1.2)` | Ícono de resultado |
| `motion.press` | 100–120 ms | — | Escala de pulsación (.93–.95) |
| `motion.shake` | 420 ms | `ease` | PIN incorrecto |
| `motion.skeleton` | 1.1 s, en bucle | `ease-in-out` | Carga de listas |

Respeta `reduce motion`: reemplaza desplazamientos y escalas por fundidos de 150 ms.

---

## 6. Marca

- **Wordmark:** `qullqi` en minúsculas, Inter 500, tracking −0.03…−0.05em, seguido de un punto en `--ac`.
- **Marca corta:** una línea de 24×2 px en `--ac` con brillo, bajo el wordmark en el splash y en el login.
- **Monograma en QR:** una `q` en `--ac` sobre una tesela `#161826`, centrada en el código.
