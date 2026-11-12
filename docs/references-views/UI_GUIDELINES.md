# Qullqi Pay — Pautas de interfaz

Cómo se construyen y se comportan las pantallas. Los valores (colores, tamaños, curvas) están en `STYLEGUIDE.md`; las rutas y los flujos de datos, en `ARCHITECTURE.md`.

---

## 1. Principios

1. **Home no es un panel bancario.** Muestra solo saldo, moneda, acciones rápidas, cuentas, actividad reciente y el acceso a perfil y notificaciones. Lo demás vive en su pestaña.
2. **Cuatro destinos, ni uno más:** Todo · Actividad · Cobrar · Tarjetas. Pagar QR es una acción, no un destino.
3. **Lo que entra y lo que sale se ven distintos.** Cobrar usa tintes de acento (`--ac-t`) y flechas de entrada (`arrow-down-left`). Los pagos y envíos usan superficies neutras y flechas de salida.
4. **Escenas independientes para procesos complejos**, sheets para decisiones pequeñas.
5. **Alineado a la izquierda y asimétrico.** Los titulares van al ras izquierdo y el espacio libre queda a la derecha.

---

## 2. Estructura de pantalla

### 2.1 Raíz de pestaña
```
status bar (38)
título de pestaña 32 · subtítulo opcional
contenido desplazable (padding inferior 110 para la barra)
barra inferior (86, desenfoque 18 px, línea superior desvanecida)
```

### 2.2 Pantalla apilada
```
status bar
barra superior: [← 42×42]  Título 18/500 (alineado a la izquierda)  [acción opcional]
cuerpo desplazable (padding 16)
pie fijo con CTA (padding 10/16/28)
```
Sin barra inferior. El botón volver es siempre `arrow-left`; en modales de cierre (comprobante, perfil, compartir) se usa `x`.

### 2.3 Paso de flujo (registro / KYC)
Barra superior con volver, una **barra de progreso segmentada** (3 px, segmento actual con brillo) y el contador `2/4`. El título del paso es una pregunta (`title.flow`) seguida de una línea de ayuda.

---

## 3. Componentes

### 3.1 Botones
| Variante | Estilo | Uso |
| --- | --- | --- |
| **Primario** | Borde 1 px `--ac`, fondo `--ac` al 8 %, texto `--ac`, brillo; hover 16 %, presionado 26 % | Una sola acción principal por pantalla |
| **Secundario** | Borde 1 px `--ln`, fondo transparente, texto `--tx`; hover con texto al 7 % | Alternativa (Repetir, Guardar PDF) |
| **Texto** | Sin borde, `--ac` o `--tx2` | Ver todo, Historial, Reenviar código |
| **Destructivo** | Texto `--neg` (o borde `--neg` en sheets) | Cancelar solicitud, Cerrar sesión |
| **Demo** | Borde punteado `--s3`, texto `--tx2`, sufijo “(demo)” | Simulaciones (pago entrante) |

- Altura: 52 (CTA de pie), 46–50 (dentro de tarjetas), 36–40 (inline).
- **El primario nunca es un relleno sólido.**
- Deshabilitado: opacidad 0.4 sin cambiar el color; se ignoran los toques.

### 3.2 Filas de lista
Chip de ícono 40×40 (`r.md`, `--s2`) · título 14.5/500 · subtítulo 12/400 `--tx2` · a la derecha, monto, valor, toggle, acción o `caret-right`. El padding vertical sigue la densidad (`--rp`). Hover: `--s2`.

### 3.3 Toggle
Pista de 44×26 con borde. Encendido: borde `--ac`, relleno `--ac` al 22 % y perilla `--ac` con brillo. Apagado: borde `--s3` y perilla `--tx2`. La perilla se mueve 18 px con una curva con rebote (`.3,1.4,.5,1`).

### 3.4 Chips y segmentados
- Chips de filtro: alto 32–34, `r.sm`. Seleccionado: borde y texto `--ac` con fondo al 12 %.
- Segmentado (Virtual / Física): contenedor con borde `--ln` y la opción activa en `--s1`.

### 3.5 Inputs
Alto 46–52, `r.sm`, fondo `--s1`, borde `--ln`, cursor `--ac`. Validación en vivo: borde `--ac` si es válido y `--neg` si no, con la ayuda debajo en el mismo color. El campo de concepto usa una línea inferior en `--ac`, sin caja.

### 3.6 PIN y OTP
Casillas de 54×62 (PIN) o una grilla de 6 (OTP). La casilla actual lleva borde `--ac` con brillo y la llena muestra un punto de 12 px en `--ac`. Usan el teclado propio de 3×4 (teclas de 54–58 de alto). En login, la tecla inferior izquierda es la huella. Un error dispara `shake` y borra la entrada.

### 3.7 Teclado de monto
Muestra el monto grande con un kicker en mayúsculas (“MONTO A ENVIAR”) y debajo la cuenta origen, que abre el selector de cuentas. Si el monto supera el saldo, el número y la línea de disponible pasan a `--neg` y el CTA se deshabilita.

### 3.8 Bottom sheets
Tirador de 32×4, radio superior de 20, padding 18/30, alto máximo del 82 %. El fondo se cubre con `#0e0f18` al 60 %; tocarlo cierra el sheet. Úsalos para: cuenta, moneda, filtros (aplican en vivo y el CTA dice “Ver N resultados”), destinatario, comercio, datos de tarjeta, congelar, cerrar sesión y **bloqueo KYC**.

### 3.9 Tarjetas bancarias
Proporción aproximada de 1.6:1 (alto 224), `r.lg`. Virtual: degradado `--color-section` con arcos de 1 px en acento. Física: metal oscuro con chip. Las superposiciones de estado son “Tarjeta congelada” (desenfoque y `snowflake`) y “POR ACTIVAR”.

### 3.10 Barra inferior
Ítem activo: ícono `fill` dentro de una pastilla de 56×30 en `--ac-t` y etiqueta `--tx`. Inactivo: `--tx2`. El botón de escaneo central es de 56×56 con `r` 16, borde `--ac`, brillo, y está elevado 18 px.

---

## 4. Estados

Toda vista con datos implementa sus cuatro estados:

| Estado | Patrón |
| --- | --- |
| **Carga** | Skeleton con la forma de la fila (chip 40×40 + dos barras + monto) en `--s2` pulsando 1.1 s. Para operaciones: anillo de 56–68 px con el borde superior en `--ac`, “Procesando…” y “No cierres la aplicación”. |
| **Vacío** | Ícono en tile de 56 px (o borde punteado) + título `title.sheet` + una línea explicativa + una acción si aplica. El texto distingue entre “Sin movimientos” (por filtros; ofrece “Limpiar filtros”) y “Aún no tienes movimientos” (cuenta nueva). |
| **Error** | Pantalla de resultado con tono `--neg`, ícono `x`, causa en una frase y aclaración del impacto (“No se hizo ningún cargo”). Primario: Reintentar. Secundario: Volver al inicio. |
| **Éxito** | Pantalla de resultado con un halo radial del acento, ícono `check` con pop, título, monto grande y una línea de contexto. Primario: Listo. Secundario: Ver comprobante. |

Los mensajes breves (copiado, guardado, PIN actualizado) se muestran como **toast** sobre la barra inferior durante 1.9 s.

---

## 5. Verificación de identidad (KYC)

- La verificación es **opcional al registrarse**: el usuario elige “Verificar mi identidad” o “Hacerlo más tarde”.
- **Sin KYC no hay operaciones de dinero.** Depositar, retirar, enviar, cambiar, pagar, cobrar y las acciones de tarjeta abren el sheet `kycGate`. Nunca escondas los botones: se ven, y al tocarlos explican por qué no están disponibles.
- Home muestra un banner con borde `--warn` mientras la cuenta no esté verificada:
  - `NONE`: “Verifica tu identidad”, con CTA Verificar.
  - `REVIEW`: “Verificación en revisión”, sin CTA; abre el estado KYC.
- La insignia del perfil refleja el estado: “Sin verificar”, “Verificación en revisión” o “Verificado · Nivel 2”.
- Captura de documento: fondo de cámara oscuro, marco con esquinas en acento, botón de disparo de 72 px, destello al capturar y revisión con “Repetir” / “Se ve bien”.
- Prueba de vida: óvalo con un anillo de progreso cónico e instrucciones cambiantes (centrar, girar a la izquierda, a la derecha, parpadear).

---

## 6. Seguridad e interacción

- Las operaciones de dinero se confirman con la **huella** (overlay inferior). Si la biometría está desactivada, el botón dice “Confirmar”.
- Los datos sensibles de tarjeta solo aparecen en un sheet y se ocultan al cerrarlo.
- “Ocultar saldo al abrir” reemplaza las cifras por `••••`. El ícono de ojo junto al saldo lo alterna.
- El login permite 3 intentos y luego se bloquea con la opción de recuperar el PIN.
- Hit targets de al menos 44×44. Los botones de la barra superior son de 42 con área táctil extendida.
- El foco de teclado usa `outline 2px var(--ac)` con 2 px de separación, nunca el anillo azul del sistema.

---

## 7. Textos

- **Tono cercano, de tú:** “Verifica tu identidad”, “¿Ya te llegó?”.
- Los títulos de paso van como pregunta y la ayuda en una sola línea.
- Los CTA usan verbos concretos: Enviar código, Crear solicitud, Confirmar con huella. Evita “Aceptar” y “OK”.
- En los errores, di qué pasó y qué ocurrió con el dinero.
- Fechas cortas: `Hoy 09:14`, `Ayer 22:40`, `mar 10 nov`. Encabezados de grupo: `Martes 10 de nov`.
- Localización: español de Bolivia, `+591`, CI, NIT, transferencia ACH, `Bs`.

---

## 8. Temas y densidad

- **Oscuro** es el tema por defecto; **Claro** se deriva de los pasos claros de las rampas neutras. Ambos comparten las mismas reglas.
- La densidad solo cambia el padding vertical de las filas (`--rp`). Los tamaños de texto no cambian.
- Acento: Nocturne (violeta, por defecto) o Electric Lime. Se cambia solo a nivel de token.
