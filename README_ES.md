# Moorty Voice Android — conectores integrados (fuente)

Proyecto Android `com.moorty.voice` con servicio de voz en español, sonidos elegibles, conector de TikTok LIVE comunitario y conector de YouTube Data API v3.

## Configuración
1. Abrí este proyecto en Android Studio o súbelo al repositorio `byronchaves84-hash/Voice`.
2. Compila con Java 17 y Android SDK 35 usando el workflow `.github/workflows/build.yml`.
3. En la app, selecciona `@moorty_yt` o `@tocolita` y pulsa **Conectar TikTok LIVE**.
4. Para YouTube, introduce una API key de YouTube Data API v3 y el ID del canal, guarda y pulsa **Conectar YouTube LIVE**.
5. Elige los sonidos de entrada desde el teléfono. El sonido de suscriptor solo se puede clasificar si TikTok informa explícitamente ese estado.

## Limitaciones importantes
- TikTokLiveJava es comunitario/no oficial; la entrega de eventos depende de TikTok y del conector.
- La API key de YouTube permite chat público y contador público de espectadores, pero no la lista de suscriptores recientes. Esa función requiere OAuth del canal y queda pendiente de integrar.
- El ZIP contiene código fuente, no un APK probado. No se ha podido ejecutar una compilación Android en este entorno; primero debe pasar el workflow de GitHub Actions.
- Los conectores se detienen cuando se cierra la actividad en esta versión. Para uso prolongado con pantalla apagada, hay que mover la gestión de conexiones a un servicio foreground persistente; el servicio de voz ya es foreground, pero los conectores aún no están alojados dentro de él.

Consulta `CONNECTORES_Y_LIMITACIONES.md` para más detalles.


## Nueva arquitectura TikTok Relay (Android compatible)

La conexión directa anterior mediante `TikTokLiveJava` se sustituyó por un cliente WebSocket de OkHttp. La conexión a TikTok se ejecuta en `relay/` usando Node.js y `tiktok-live-connector`; Android recibe los eventos y reproduce las voces/sonidos. Antes de conectar, despliega el relay y pega en la app la URL `wss://TU-DOMINIO/?token=TU_CLAVE`. Consulta `relay/README_ES.md`.

**Importante:** el código fuente está preparado para esta arquitectura, pero no se ha validado con un LIVE real ni se incluye un servidor ya desplegado. El relay necesita hospedaje externo y el protocolo comunitario puede cambiar. YouTube no se modificó.


## Despliegue del servidor relay

Se añadió `render.yaml` para crear el servidor en Render desde GitHub. Sigue `relay/README_ES.md`. Render debe generar y guardar `MOORTY_TOKEN`; el servidor se niega a iniciar si no hay token configurado. Aún debes desplegarlo en tu cuenta y comprobar `/health` y una conexión durante un LIVE real.
