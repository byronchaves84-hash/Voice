# Moorty Relay — despliegue en Render

Este servidor mantiene la conexión comunitaria con TikTok LIVE fuera de Android y reenvía eventos al teléfono por WebSocket seguro.

## Desplegar desde GitHub (sin PC)

1. Sube el contenido de esta carpeta del proyecto a un repositorio GitHub tuyo, conservando `render.yaml` en la raíz y `relay/` como carpeta.
2. Entra en https://dashboard.render.com/ y crea una cuenta o inicia sesión.
3. Pulsa **New + → Blueprint** y conecta el repositorio de GitHub. Render leerá `render.yaml`, creará el servicio `moorty-voice-relay` y generará `MOORTY_TOKEN`.
4. Espera a que el despliegue termine. En la página del servicio, abre la URL pública, por ejemplo `https://NOMBRE.onrender.com/health`. Debe devolver JSON con `ok: true`.
5. En **Environment** del servicio, copia el valor de `MOORTY_TOKEN` solo para uso propio; no lo publiques ni lo compartas en capturas.
6. En Moorty Voice, la dirección WebSocket será `wss://NOMBRE.onrender.com/?token=TU_MOORTY_TOKEN`. Sustituye el dominio y el token por los tuyos.

## Si Render no permite Blueprint

Crea **New + → Web Service**, conecta el repo y configura: Root Directory `relay`, Build Command `npm install`, Start Command `npm start`, Health Check Path `/health`. En Environment crea `MOORTY_TOKEN` con una clave larga y aleatoria. Render admite WebSockets y proporciona TLS para usar `wss://`.

## Importante

- El plan gratuito puede suspender el servicio tras un periodo sin actividad; la primera conexión puede tardar en despertar. Para alertas fiables durante directos, verifica si el plan elegido mantiene el proceso activo.
- La librería `tiktok-live-connector` es no oficial y delega la firma de ciertas peticiones en un servicio comunitario. Puede cambiar, limitarse o fallar; no se garantiza compatibilidad permanente.
- No pegues tu `MOORTY_TOKEN` en chats públicos, repositorios ni capturas.
- Esta arquitectura no requiere dejar tu computadora encendida una vez que el servidor esté desplegado.
