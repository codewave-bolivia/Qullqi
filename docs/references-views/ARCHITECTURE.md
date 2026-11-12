# Qullqi Pay — Arquitectura del frontend

Billetera digital móvil para Bolivia (BOB principal, USD secundaria). Este documento describe las vistas, la navegación y los flujos de datos del frontend. Todo funciona con **datos mock**: no hay backend ni servicios financieros reales.

Referencia visual: `Qullqi Pay Auth.dc.html` (actividad de acceso) y `Qullqi Pay.dc.html` (actividad principal).

---

## 1. Stack objetivo

| Capa | Tecnología |
| --- | --- |
| Lógica compartida | Kotlin Multiplatform (`shared/`) |
| Build | Gradle (Kotlin DSL) + version catalog |
| Android | Jetpack Compose, Material 3 (solo como base; el tema es propio) |
| iOS | SwiftUI consumiendo `shared` vía framework |
| Estado | Flujo unidireccional (UDF / MVI): `State` inmutable + `Intent` + `Effect` |
| Concurrencia | Coroutines + `StateFlow` |
| DI | Koin (multiplatform) |
| Persistencia local | DataStore (Android) / `NSUserDefaults` (iOS) detrás de `expect/actual` |

### Estructura de módulos

```
qullqi/
├── shared/
│   ├── commonMain/
│   │   ├── domain/        # modelos, casos de uso, reglas (KYC gate, límites)
│   │   ├── data/          # repositorios mock, fuentes en memoria
│   │   ├── presentation/  # ViewModels compartidos (State/Intent/Effect)
│   │   └── designsystem/  # tokens: colores, tipografía, espaciado, motion
│   ├── androidMain/       # actual: almacenamiento, biometría, cámara
│   └── iosMain/
├── androidApp/            # AuthActivity, MainActivity, pantallas Compose
└── iosApp/                # AuthFlow, MainFlow, vistas SwiftUI
```

---

## 2. Actividades y grafo de navegación

La app tiene **dos actividades** (en iOS, dos `NavigationStack` raíz intercambiados por la sesión):

```
┌──────────────────────┐      sesión válida       ┌───────────────────────┐
│     AuthActivity      │ ───────────────────────▶ │     MainActivity       │
│ splash · login · alta │ ◀─────────────────────── │ 4 pestañas + flujos    │
│ verificación (KYC)    │   logout / "verificar"   │                        │
└──────────────────────┘                           └───────────────────────┘
```

El paso entre actividades usa una transición con velo (sección 6). Cuando el usuario sin verificar toca "Verificar", MainActivity abre AuthActivity directamente en `kycIntro`, y al terminar vuelve a MainActivity.

### 2.1 AuthActivity

| Ruta | Vista | Tipo | Sale a |
| --- | --- | --- | --- |
| `splash` | Splash con wordmark y barra de carga (1.9 s) | Pantalla | `login` si hay sesión guardada; si no, `welcome` |
| `welcome` | Propuesta de valor, Crear cuenta / Ya tengo cuenta | Pantalla | `regPhone`, `login` |
| `login` | PIN de 4 dígitos, huella, recuperar PIN, cambiar de cuenta | Pantalla | MainActivity |
| `regPhone` | Celular +591 (8 dígitos, empieza con 6 o 7) | Flujo 1/4 | `regOtp` |
| `regOtp` | Código SMS de 6 dígitos, reenvío a los 30 s | Flujo 2/4 | `regInfo` |
| `regInfo` | Nombre, correo, aceptación de términos | Flujo 3/4 | `regPin` |
| `regPin` | Crear y confirmar PIN | Flujo 4/4 | `regDone` |
| `regDone` | Cuenta creada · estado "Sin verificar" · elegir KYC o más tarde | Pantalla | `kycIntro` o MainActivity (`kyc = none`) |
| `kycIntro` | Requisitos y privacidad | Pantalla | `kycDoc` |
| `kycDoc` | CI o pasaporte | Flujo 1/n | `kycFront` |
| `kycFront` / `kycBack` | Captura de documento (el reverso solo para CI) | Flujo | `kycSelfie` |
| `kycSelfie` | Prueba de vida con anillo de progreso | Flujo | `kycData` |
| `kycData` | Datos extraídos + declaración jurada | Flujo | `kycProc` |
| `kycProc` | Verificación con checklist animado | Pantalla | `kycResult` |
| `kycResult` | Aprobado · En revisión · Rechazado | Pantalla | MainActivity o reintento |

### 2.2 MainActivity

Barra inferior con **exactamente cuatro destinos**. El botón central de escaneo es una **acción** global (Pagar QR), no un destino.

```
[ Todo ]  [ Actividad ]  ( ⌖ Pagar QR )  [ Cobrar ]  [ Tarjetas ]
```

Cada pestaña tiene su propia pila. Mientras hay una pantalla apilada, la barra inferior se oculta. Las rutas apiladas son compartidas (por ejemplo, `txDetail` se abre desde Todo, Actividad o Tarjetas).

#### Todo (`home`)
| Ruta | Escena del brief | Tipo |
| --- | --- | --- |
| `home` | Home Dashboard | Raíz |
| `balance` | Balance Overview | Pantalla |
| sheet `accounts` | Account Selector | Bottom sheet |
| `acct` | Account Details + Currency Details | Pantalla |
| `add` | Add Money / Deposit Method | Pantalla |
| `depInstr` | Deposit Instructions (banco, QR interbancario, efectivo) | Pantalla |
| `sendRecip` | Send Money / Recipient Selection | Pantalla |
| sheet `recip` | Recipient Details | Bottom sheet |
| `amount` (flow=`send`) | Transfer Amount | Pantalla |
| `review` | Transfer Review | Pantalla |
| overlay `biometric` | Transfer Confirmation | Overlay |
| `proc` → `result` | Transfer Result | Pantalla |
| `amount` (flow=`exchange`) | Exchange Currency / Exchange Amount | Pantalla |
| sheet `pickCur` | Selección de moneda | Bottom sheet |
| `review` → `result` | Exchange Review / Exchange Result | Pantalla |

#### Actividad (`activity`)
| Ruta | Escena |
| --- | --- |
| `activity` | Activity Overview · All · Income · Expenses · Transfers · Payments (chips) · Transaction Search (input) |
| sheet `filters` | Transaction Filters: fecha, importe, moneda, estado |
| `txDetail` | Transaction Details + Transaction Timeline |
| `receipt` | Transaction Receipt |

#### Cobrar (`receive`)
| Ruta | Escena |
| --- | --- |
| `receive` | Receive Money |
| `myQR` | Personal QR (incluye una simulación de pago entrante) |
| `amount` (flow=`request`) | Generate Payment Request / Request Amount |
| `reqDesc` | Request Description |
| `reqShare` | Share QR + Share Payment Link |
| `reqHistory` | Request History |
| `reqDetail` | Request Details |
| `result` (`received` / `qrIn`) | Payment Received / QR Payment Result |

#### Tarjetas (`cards`)
| Ruta | Escena |
| --- | --- |
| `cards` | Cards Overview · Virtual Card · Physical Card (control segmentado) · Card Transactions |
| sheet `cardData` | Card Details (PAN, vencimiento, CVV) |
| sheet `freeze` | Freeze Card |
| `pinPad` (pinFor=`activate`) → `result` | Card Activation |
| `list` (key=`cardSettings`) | Card Settings |
| `cardLimits` | Card Limits |
| `list` (key=`cardSecurity`) | Card Security |
| `txDetail` | Card Transaction Details |

#### Pagar QR (acción global)
| Ruta | Escena |
| --- | --- |
| `scan` (estado `search`) | QR Scanner |
| `scan` (estado `found`) | QR Recognition |
| sheet `merchant` | Merchant Details |
| `amount` (flow=`pay`) | Payment Amount |
| `review` | Payment Review |
| overlay `biometric` | Payment Confirmation |
| `proc` | Payment Processing |
| `result` (`pay` / `payError`) | Payment Success / Payment Error |
| `receipt` | Payment Receipt |

#### Perfil (se abre desde el avatar; no ocupa un destino de la barra)
| Ruta | Escena |
| --- | --- |
| `profile` | Profile |
| `list` (key=`personal`) | Personal Information |
| `kyc` | Identity Verification + KYC Status |
| `list` (key=`security`) | Security Center |
| `list` (key=`bio`) | Biometric Settings |
| `pinPad` (pinFor=`app`) | PIN Management |
| `list` (key=`devices`) | Active Devices |
| `list` (key=`notifPrefs`) | Notifications (preferencias) |
| `notifs` | Bandeja de notificaciones (campana del encabezado) |
| `list` (key=`prefs`) | Preferences |
| `appearance` | Appearance (Oscuro / Claro) |
| `help` | Help Center |
| `support` | Support (chat) |
| `list` (key=`legal`) | Legal |
| sheet `logout` | Logout |

> `list` es una pantalla genérica guiada por datos (grupos → filas de valor, toggle, acción o navegación). En código, `SettingsListScreen(config: ListConfig)`.

---

## 3. Modelo de dominio

```kotlin
enum class Currency(val symbol: String) { BOB("Bs"), USD("$") }
enum class KycStatus { NONE, REVIEW, VERIFIED }
enum class TxType { INCOME, EXPENSE, TRANSFER, PAYMENT }
enum class TxStatus { DONE, PENDING, FAILED }
enum class RequestStatus { PENDING, PAID, EXPIRED, CANCELLED }

data class Session(
    val name: String, val email: String, val phone: String,
    val pinHash: String, val kyc: KycStatus, val isNew: Boolean
)
data class Account(val currency: Currency, val balance: BigDecimal, val number: String)
data class Transaction(
    val id: String, val name: String, val subtitle: String, val type: TxType,
    val amount: BigDecimal, val currency: Currency, val status: TxStatus,
    val instant: Instant, val method: String, val reference: String,
    val cardId: String? = null
)
data class Recipient(val id: String, val name: String, val bank: String, val account: String)
data class Merchant(val name: String, val nit: String, val address: String, val verified: Boolean)
data class PaymentRequest(
    val id: String, val from: String, val description: String,
    val amount: BigDecimal, val currency: Currency, val status: RequestStatus,
    val link: String, val createdAt: Instant
)
data class Card(
    val id: String, val kind: CardKind /* VIRTUAL, PHYSICAL */, val last4: String,
    val active: Boolean, val frozen: Boolean, val limits: CardLimits, val settings: CardSettings
)
data class CardLimits(val daily: Int, val online: Int, val monthly: Int, val atm: Int?)
data class CardSettings(val online: Boolean, val international: Boolean, val contactless: Boolean, val atm: Boolean, val geoLock: Boolean)
```

Tipo de cambio mock: `1 USD = Bs 6.96` (venta) / `Bs 6.86` (compra). Sin comisión.

---

## 4. Repositorios (mock)

Todos exponen `StateFlow` y simulan latencia con `delay()`.

| Repositorio | Responsabilidad | Latencia mock |
| --- | --- | --- |
| `SessionRepository` | Sesión, PIN, estado KYC, logout | 0 ms |
| `AuthRepository` | Envío y verificación de OTP, alta de usuario | 600 ms |
| `KycRepository` | Captura, envío y resultado (`approved` / `review` / `rejected`) | 2.8 s (4 pasos × 700 ms) |
| `AccountRepository` | Saldos por moneda, datos de cuenta | 0 ms |
| `TransactionRepository` | Lista, filtros, alta de movimientos | 600 ms al entrar en Actividad |
| `TransferRepository` | Enviar, cambiar, pagar QR | 1.5 s |
| `RequestRepository` | Crear, cancelar y marcar pagadas las solicitudes | 0 ms |
| `CardRepository` | Congelar, límites, ajustes, activación | 1.4 s (activación) |

Semilla de datos: un usuario existente (`isNew = false`) recibe 14 movimientos, 4 solicitudes y los saldos `Bs 12,480.50` / `$ 1,245.80`. Un usuario recién registrado (`isNew = true`) empieza con saldos en 0 y listas vacías, lo que muestra los estados vacíos.

---

## 5. Flujos de datos

### 5.1 Patrón por pantalla

```
UI ──Intent──▶ ViewModel ──llama──▶ UseCase ──▶ Repository (mock)
 ▲                 │                                   │
 └────State────────┴──────────◀── StateFlow ◀──────────┘
                   └──Effect (navegar, toast, háptica)──▶ UI
```

- `State` es inmutable y la pantalla se renderiza solo a partir de él.
- `Effect` cubre lo que ocurre una sola vez: navegar, mostrar un toast o abrir un sheet.
- Los valores derivados (saldo total, grupos por día, conteo de filtros activos, validaciones) se calculan en el ViewModel, nunca en la vista.

### 5.2 Sesión y arranque

```
Splash ─▶ SessionRepository.current()
          ├─ null      ─▶ Welcome
          └─ Session   ─▶ Login(PIN)
Login: 4 dígitos ─▶ verifyPin()
          ├─ ok        ─▶ estado "Ingresando…" (380 ms) ─▶ velo ─▶ MainActivity
          └─ error     ─▶ intentos-1, shake; con 0 intentos ─▶ bloqueado (recuperar PIN)
```

### 5.3 Registro

```
phone ─(valida ^[67]\d{7}$)─▶ requestOtp ─▶ otp(6) ─▶ verifyOtp
      ─▶ name + email (regex) + términos ─▶ PIN ×2 (deben coincidir)
      ─▶ createUser(isNew = true, kyc = NONE)
      ─▶ regDone: [Verificar ahora → KYC] | [Más tarde → Main]
```

### 5.4 KYC y bloqueo de operaciones

`KycStatus` controla todas las operaciones de dinero. La regla vive en `domain/KycGate.kt`:

```kotlin
fun canOperate(s: KycStatus) = s == KycStatus.VERIFIED
```

| Acción | NONE | REVIEW | VERIFIED |
| --- | --- | --- | --- |
| Ver saldo, actividad, perfil | ✓ | ✓ | ✓ |
| Agregar dinero (depositar) | sheet `kycGate` | sheet (solo informativo) | ✓ |
| Enviar / retirar / cambiar | sheet `kycGate` | sheet | ✓ |
| Pagar QR (escáner) | sheet `kycGate` | sheet | ✓ |
| Cobrar (QR, solicitar, recibir por transferencia) | sheet `kycGate` | sheet | ✓ |
| Acciones de tarjeta y activación | sheet `kycGate` | sheet | ✓ |

Además, Home muestra un banner de estado cuando el usuario no está verificado. El botón "Verificar" del banner o del sheet abre AuthActivity en `kycIntro`. El resultado del KYC actualiza `Session.kyc` y vuelve a MainActivity.

### 5.5 Flujo financiero genérico

Enviar, Cambiar y Pagar QR comparten tres pantallas configuradas por `flow`:

```
amount(flow) ─▶ review(flow) ─▶ biometric overlay (1.2 s; si la biometría está activa)
             ─▶ proc (reemplaza review) ─▶ Repository.execute()
             ─▶ result(kind) ─┬─ Listo           ─▶ vuelve a la raíz de la pestaña
                              └─ Ver comprobante ─▶ receipt(lastTxId)
```

Validaciones en `amount`: monto mayor que 0, como máximo 2 decimales y 8 dígitos. Si el monto supera el saldo de la cuenta origen, el monto y el texto de disponible se muestran en color negativo y Continuar queda deshabilitado.

Efectos al confirmar:
- `send` / `pay`: `balance[origen] -= monto` y se agrega una transacción al inicio de la lista.
- `exchange`: `balance[from] -= monto`, `balance[to] += monto × tasa` y se agrega una transacción.
- `payError` (simulado): no hay efectos. Reintentar vuelve a `review`.

### 5.6 Cobros

```
request: amount(flow=request, moneda elegible) ─▶ reqDesc ─▶ create() ─▶ reqShare(QR + link)
reqDetail(pending) ─▶ [Compartir de nuevo | Simular pago | Cancelar]
Simular pago ─▶ request.status = PAID, balance += monto, tx INCOME ─▶ result(received)
myQR ─▶ Simular pago entrante ─▶ tx INCOME ─▶ result(qrIn)
```

### 5.7 Actividad: búsqueda y filtros

```
filtered = txs
  .filter { tipo == chip || chip == ALL }
  .filter { day <= rango }               // 7 · 30 · 90 · todo
  .filter { moneda }                     // todas · BOB · USD
  .filter { estado }                     // todos · completado · pendiente · fallido
  .filter { importeBOB en bucket }       // <100 · 100–1,000 · >1,000
  .filter { query in name+subtitle }
grouped = filtered.groupBy { díaLegible }
```

Cada cambio de chip o la entrada a la pestaña muestra un skeleton durante 600 ms. Si no hay resultados se muestra un estado vacío: con filtros activos ofrece "Limpiar filtros"; sin movimientos, un mensaje de bienvenida.

---

## 6. Navegación: reglas y transiciones

| Caso | Animación |
| --- | --- |
| Cambio de pestaña | Fundido de 250 ms |
| Apilar pantalla | Entra desde la derecha, 26 px + fundido, 300 ms |
| Volver | Entra desde la izquierda, 26 px + fundido, 300 ms |
| Bottom sheet | Sube 100 %→0 en 320 ms; el fondo aparece con fundido de 200 ms |
| Cambio de actividad | El origen se cubre con un velo de color fondo (0→1, 450–480 ms) y el destino lo descubre (1→0, 600 ms); Home entra con `qEnter` (750 ms) y bloques escalonados (+80 ms) |
| Resultado | Pop del ícono (500 ms, overshoot) y textos que suben de forma escalonada (+60 ms) |

Reglas:
1. Selecciones y acciones pequeñas van en **bottom sheet**: cuenta, moneda, filtros, destinatario, comercio, datos de tarjeta, congelar, cerrar sesión y bloqueo KYC.
2. Los flujos financieros van en **pantalla completa**.
3. `proc` **reemplaza** a `review` en la pila, así que volver desde un resultado no repite la operación.
4. La barra inferior solo es visible en la raíz de cada pestaña.
