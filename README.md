# PDF Reader

Lector PDF nativo, personal y offline para Xiaomi con Android 10 (API 29). La aplicación no declara permiso de red ni crea copias de los documentos: conserva el URI elegido mediante Storage Access Framework y guarda únicamente metadatos y trazos pendientes en Room.

## Alcance implementado

- Biblioteca ordenada por importación reciente, con `ACTION_OPEN_DOCUMENT` filtrado a PDF, reimportación por URI y retirada sin borrar el original.
- Visor basado en AndroidX PDF con zoom, desplazamiento, rotación y caché de bitmaps delegados al componente oficial.
- Selección de texto de una sola página con PDFBox-Android, arrastre de selección y copia al portapapeles.
- Modo subrayador explícito: amarillo, verde o azul; grosor continuo de 2–20 puntos; los trazos se guardan inmediatamente en Room.
- Escritura segura en el PDF mediante un trabajo único de WorkManager: copia temporal, incorporación de anotaciones `/Ink` con UUID, validación y sincronización con `fsync`.
- Inversión GPU del viewport, manteniendo colores visibles del subrayador, y liberación de documentos/analizadores al salir del lector.

El port Android de PDFBox 2.0.27 no expone una clase pública `PDAnnotationInk`; por ello se genera la misma anotación estándar `/Ink` usando `PDAnnotationMarkup` y su `InkList`, que sí está disponible en el artefacto Android.

## Estructura

- `data/`: entidades Room, conversores compactos y repositorio SAF.
- `pdf/`: extracción de caracteres/cajas con caché LRU.
- `sync/`: worker serializado por documento y escritura validada en el proveedor SAF.
- `ui/`: biblioteca, lector, overlays Compose y tema Material 3.
- `artwork/` y `res/drawable-nodpi/logo.png`: fuente gráfica del icono.

## Compilar e instalar

Android Studio puede abrir directamente el proyecto. Desde PowerShell:

```powershell
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para una entrega optimizada:

```powershell
./gradlew testDebugUnitTest lintDebug assembleRelease
```

El SDK local se configura en `local.properties` (ese archivo está ignorado por Git). El APK release usa R8 y reducción de recursos.

Para esta distribución personal el release se firma con el keystore debug local de Android Studio; no reutilices esa configuración para publicar en una tienda.

## Límites deliberados

No se incluyen OCR, búsqueda, miniaturas, nube, formularios, firma, notas, goma, lápiz ni edición general. Los PDFs escaneados pueden renderizarse y subrayarse, pero no proporcionan texto para copiar. Un proveedor SAF de solo lectura mantiene la lectura activa y deshabilita el subrayador.
