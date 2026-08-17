# Hydra

🇪🇸 Español · [🇬🇧 English](README.md)

App Android para registrar la ingesta diaria de agua y recibir recordatorios para tomar,
pensada para **proteger el riñón** (no pasar ~1 L/h, reducir la ingesta de noche) y
**reforzar el hábito**. 100% **offline** — sin permiso de internet ni permisos invasivos.

## Capturas

<p>
  <img src="docs/screenshots/home-light.png" width="200" alt="Inicio (claro)"/>
  <img src="docs/screenshots/home-dark.png" width="200" alt="Inicio (oscuro)"/>
  <img src="docs/screenshots/history-light.png" width="200" alt="Historial (claro)"/>
  <img src="docs/screenshots/history-dark.png" width="200" alt="Historial (oscuro)"/>
  <img src="docs/screenshots/home-paused-light.png" width="200" alt="Inicio en pausa (claro)"/>
  <img src="docs/screenshots/home-paused-dark.png" width="200" alt="Inicio en pausa (oscuro)"/>
  <img src="docs/screenshots/home-muted-light.png" width="200" alt="Inicio con avisos silenciados (claro)"/>
  <img src="docs/screenshots/home-muted-dark.png" width="200" alt="Inicio con avisos silenciados (oscuro)"/>
</p>

Páginas completas — Inicio con la curva de ritmo del día, e historial completo en el **período de
90 días**, para ver cómo un solo control acota todas las tarjetas a la vez (renderizado a partir de
12 semanas de datos de ejemplo):

<p>
  <img src="docs/screenshots/home-full-light.png" width="240" alt="Inicio con curva de ritmo (claro)"/>
  <img src="docs/screenshots/home-full-dark.png" width="240" alt="Inicio con curva de ritmo (oscuro)"/>
</p>
<p>
  <img src="docs/screenshots/charts-light.png" width="240" alt="Gráficos en 90 días (claro)"/>
  <img src="docs/screenshots/charts-dark.png" width="240" alt="Gráficos en 90 días (oscuro)"/>
</p>

La tarjeta de 12 semanas en sus dos lecturas, con un historial que arranca recién hace 60 días: las
semanas sin registro son contornos punteados en el calendario y directamente no aparecen en las
barras, y la primera semana parcial queda baja porque los huecos cuentan como cero:

<p>
  <img src="docs/screenshots/heatmap-styles-light.png" width="240" alt="12 semanas en calendario y barras (claro)"/>
  <img src="docs/screenshots/heatmap-styles-dark.png" width="240" alt="12 semanas en calendario y barras (oscuro)"/>
</p>

Un rango de 21 días ya elegido: el gráfico hace zoom en el rango, la tarjeta se titula con él, y
"Ver 30 días" es la salida del zoom.

<p>
  <img src="docs/screenshots/history-range-light.png" width="240" alt="Rango elegido (claro)"/>
  <img src="docs/screenshots/history-range-dark.png" width="240" alt="Rango elegido (oscuro)"/>
</p>

Una instalación nueva (11 días registrados) eligiendo un rango — casilleros vacíos, un promedio de
7 días que recién arranca cuando hay siete días seguidos, y el día inicial resaltado:

<p>
  <img src="docs/screenshots/history-sparse-light.png" width="240" alt="Instalación nueva (claro)"/>
  <img src="docs/screenshots/history-sparse-dark.png" width="240" alt="Instalación nueva (oscuro)"/>
</p>

## Características

- **Meta diaria** = factor (ml/kg) × peso. Factor 33 normal, 40 en *modo calor*. Ajuste ±20%.
- **El día cambia a la medianoche.** El agua que tomes después de las 00:00 cuenta para el día
  nuevo, y la pantalla sigue el cambio en vivo: si dejás la app abierta cruzando la medianoche, el
  día rota solo. Los horarios de despertar y dormir solo delimitan las horas en que se pueden enviar
  avisos: nunca mueven el día.
- **Recordatorios** (WorkManager) que reparten la ingesta de la mañana a la tarde respetando el
  corte nocturno. Cada uno sugiere una cantidad calculada en el momento: lo que te falta, dividido
  por los recordatorios que todavía entran antes del corte, con tope ~1 L/h para no pedirte más de
  lo que el riñón procesa. Si te atrasás bastante del ritmo, te avisa fuera de turno. Las
  notificaciones traen acciones rápidas ("Registré X" / "Posponer").
- **Silenciar los avisos por hoy** con un toque desde la barra superior de Inicio (ícono de
  campana). No afecta el registro ni las rachas, y se desactiva solo al día siguiente.
- **Aviso de cafeína** en Inicio, desde 9 h antes de tu hora de dormir, sugiriendo cortar con el
  café, el té o el mate. Se expresa en horas antes de dormir y no como una hora fija, así que si
  cambiás el horario el aviso se corre solo. Viene activado y se apaga desde *Ajustes › Horarios*;
  es puramente informativo — no afecta la meta, ni las rachas, ni los recordatorios. Las 9 h
  redondean las 8,8 h que
  [Gardiner et al. (2023)](https://www.sciencedirect.com/science/article/pii/S1087079223000205)
  identifican como el punto donde un café estándar deja de recortar el sueño de forma medible, y
  se plantea como **mínimo** porque la dosis es lo que mueve el número:
  [Drake et al. (2013)](https://pubmed.ncbi.nlm.nih.gov/24235903/) ubican el piso de higiene del
  sueño en 6 h, y un [ensayo cruzado de 2024](https://academic.oup.com/sleep/article/48/4/zsae230/7815486)
  encontró que 400 mg todavía fragmentan el sueño 8 h antes.
- **Inferencia de estación offline** (país por *locale* + fecha → hemisferio → estación) que
  sugiere el modo calor. No lee el clima real (sin internet ni sensores).
- **Modo simple** (valores bloqueados a la fórmula correcta) y **modo avanzado** (factor, ventana,
  frecuencia, tope/hora, % de ajuste y tamaños rápidos editables).
- **Historial y gamificación**: rachas (actual/mejor), logros, lista por día, y una celebración al
  cumplir la meta. Un día cuenta al 100% de la meta.
- **Un solo período para toda la pantalla de historial.** Una tarjeta **Período** arriba —7, 30 o
  90 días, o un rango que dibujás vos— acota el gráfico de barras, sus totales, la lista de días y
  todos los gráficos de abajo. Muestra las fechas exactas que cubre, y cada tarjeta que gobierna
  lleva su propia insignia, así nunca hay que adivinar qué control mueve qué. El heat-map de 12
  semanas es la única excepción a propósito, y lo aclara en pantalla.
- **Siete gráficos**, todos offline y calculados con tus propios datos:
  - **Ritmo de hoy** (Inicio) — lo que tomaste contra el plan, sobre un eje de 24 h, con cuánto vas
    adelantado o atrasado, y un punto por cada toma para ver *en qué momento* cayó cada vaso.
    Dibuja el *mismo* objetivo que usan los recordatorios, así nunca pueden contradecirse.
  - **Ritmo de un día** (Historial) — la misma curva para cualquier día que tengas registrado, con
    su propio selector `‹ fecha ›` que recorre sólo los días con registro. A propósito es
    independiente del control de período, y se reconstruye con el snapshot congelado de ese día
    —meta, hora de despertar, corte nocturno y balance mañana/tarde—, así que cambiar el perfil
    nunca reescribe cómo se ve un día pasado.
  - **Gráfico por día** — un casillero de calendario por cada día del período (un día sin registro
    queda como track vacío, nunca como un 0% falso), más una línea de **promedio móvil de 7 días**.
    La línea solo aparece donde hay una ventana completa de 7 días, así nunca hace pasar un
    promedio de 2 días por uno semanal — y en el período de 7 días no aparece, porque ahí sólo
    podría ser un punto suelto.
  - **Elegir un rango de fechas**: arrastrá sobre el gráfico, o tocá el día inicial y después el
    final. El gráfico **hace zoom** en el rango y toda la página lo sigue; "Ver 30 días" sale del
    zoom. Los días sin registro igual pesan en el promedio diario, así los huecos quedan a la vista.
    Elegir es todo o nada: tocá en cualquier lado fuera de las barras —incluso sobre otro gráfico—
    o apretá "Cancelar" para abandonar una selección a medias, y cambiar de período descarta el
    rango, así que un rango nuevo siempre arranca con dos toques desde cero.
  - **Por día de la semana** — cumplimiento promedio por día en el período, y tu día más flojo.
  - **Últimas 12 semanas** — la vista larga, siempre 12 semanas, en cualquiera de dos lecturas que
    podés alternar (la elección queda guardada):
    - *Calendario* — un cuadrito por día. Cuanto más fuerte el color, más cerca estuviste de la
      meta; un **contorno punteado** es un día sin registro. La rampa se construye alejándose del
      color de la tarjeta, así que se lee igual en tema claro y oscuro.
    - *Barras* — una barra por semana con el promedio de cumplimiento de esa semana, para ver
      tendencia en vez de textura. Los días sin registro cuentan como cero dentro de la semana, así
      una semana que te olvidaste no parece perfecta; una semana sin nada no dibuja barra.
  - **Cuándo tomás agua** — distribución por hora del día en el período. Resalta tu bloque de 3
    horas más cargado y compara el balance mañana/tarde que **lograste** contra el que
    **configuraste**.
  - **Cuándo llegás a la meta** — un punto por día en la hora en que cruzaste el 100%, con tu hora
    típica (mediana). Terminar más temprano deja menos agua para la noche.
- **Balance mañana/tarde**: la meta se reparte 65% en la primera mitad de la ventana despertar→corte
  y 35% en la segunda (ajustable 45–70% en modo avanzado, la tarde es siempre el complemento). El
  adelanto matinal acompaña el ritmo circadiano y, junto con el corte nocturno, reduce las idas al
  baño de madrugada — en línea con las guías clínicas de nocturia (restricción vespertina de líquidos).
- **Pausa de registro** (5, 10 o 30 días): apaga los recordatorios sin desinstalar. Los días en
  pausa **no cortan la racha** (son neutros, salvo que igual cumplas la meta: entonces cuentan),
  podés seguir registrando si querés, y se reanuda solo al vencer o antes con "Reanudar".
- **Modo oscuro moderno** (Material 3), **español e inglés**, **métrico e imperial**, país editable.
- **Backup**: exportá todo tu historial a un archivo JSON y volvé a importarlo, y la app queda
  incluida en el backup propio de Android. Cada exportación e importación termina con una
  confirmación que dice cuántos días y registros movió, o por qué falló (archivo ilegible, no es un
  backup de Hydra, versión más nueva de la app).

## Stack

Kotlin · Jetpack Compose · Material 3 · Room · DataStore · WorkManager.
minSdk 26, target/compile 34, JDK 17, AGP 8.2.2, Gradle 8.5 (mismo toolchain que el proyecto
hermano `cast-bridge`).

## Build

Docker Desktop tiene que estar corriendo (no hace falta JDK / Android SDK locales).

### APK de release (firmado)

```bash
cp .env.example .env      # poné las contraseñas del keystore
./build-apk.sh            # Linux/macOS  (Windows: build-apk.bat)
# Salida: app-output/Hydra.apk
adb install app-output/Hydra.apk
```

El primer build genera `hydra-release.keystore` (git-ignored) en la raíz del proyecto y los
siguientes lo reutilizan, así las versiones nuevas se actualizan sin desinstalar. **Hacé backup
de `.env` y del keystore juntos** — si se pierden, los builds futuros no podrán actualizar la
app instalada.

### Desarrollo (compilar, tests, capturas) con la imagen toolchain reutilizable

```bash
docker build -f docker/toolchain.Dockerfile -t hydra-toolchain docker/
# Compilar + correr todos los escenarios Gherkin
docker run --rm -v "$PWD:/project" -v hydra-gradle:/root/.gradle hydra-toolchain \
    gradle :app:assembleDebug :app:testDebugUnitTest
# Regenerar las capturas del README
docker run --rm -v "$PWD:/project" -v hydra-gradle:/root/.gradle hydra-toolchain \
    gradle :app:recordRoborazziDebug --tests "*ScreenshotTest"
```

Con JDK 17 + Android SDK locales también podés usar el wrapper: `./gradlew test`.

## Testing

El comportamiento se especifica en **Gherkin** (`app/src/test/resources/features/*.feature`) y se
ejecuta con **Cucumber-JVM** (dominio + integración in-memory) — 182 escenarios. Tres tests JUnit
puntuales cubren lo que Gherkin no puede expresar: el re-anclaje de los flujos vivos a la medianoche
(con un reloj atado al tiempo virtual), la regla de cascada de Room para el día abierto, y la
migración de base de datos (un archivo v1 real llevado por la actualización de esquema). Las
capturas se renderizan sin emulador con **Roborazzi + Robolectric**. 210 tests en total.

## Privacidad

La app **no declara INTERNET** ni permisos de ubicación/sensores, así que ningún dato puede salir
del dispositivo (salvo que actives el auto-backup de Android o exportes un JSON a mano). El único
permiso que concede el usuario es `POST_NOTIFICATIONS` (Android 13+). WorkManager agrega algunos
permisos *normales* de instalación (`ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
`FOREGROUND_SERVICE`) para programar los recordatorios de forma confiable; ninguno da acceso a la red.

## Licencia

[MIT](LICENSE) © 2026 Sergio Emanuel Napoli. Libre para usar, modificar y distribuir,
sin garantía. No es consejo médico — ver los descargos dentro de la app.
