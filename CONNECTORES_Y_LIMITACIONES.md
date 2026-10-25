# Conectores Moorty Voice Android

## TikTok LIVE
La aplicación integra el conector comunitario TikTokLiveJava para salas LIVE públicas, con callbacks para comentarios, regalos, follows, shares, joins, likes y suscripciones cuando el conector y la sala entregan esos eventos. No es una API oficial de TikTok; puede dejar de funcionar si TikTok cambia el protocolo o bloquea la conexión. El estado de suscriptor de quien entra no siempre está expuesto. Moorty no lo adivina: el sonido de suscriptor solo se usa cuando el evento entrega un booleano explícito.

## YouTube LIVE
El conector usa YouTube Data API v3 con la API key del usuario y el ID de canal. Busca directos públicos, lee chat y eventos públicos de membresías/Super Chat y consulta el contador de espectadores cuando YouTube lo expone. Usa el `pollingIntervalMillis` devuelto por la API para no consultar el chat más rápido de lo permitido.

### Suscriptores de YouTube
La API key no permite consultar la lista de suscriptores recientes del propietario. Para alertas de nuevos suscriptores públicos hace falta añadir autorización OAuth del canal con el alcance correspondiente. Esta entrega no finge que esa función ya está autenticada; el estado queda documentado como pendiente de OAuth.

## Pruebas
El código se preparó como integración de fuente, pero no se pudo ejecutar Gradle/Android SDK en este entorno. Hay que compilar en GitHub Actions y corregir cualquier incompatibilidad que reporte la versión de TikTokLiveJava/Android. Los conectores necesitan internet y un directo público activo para validarse de extremo a extremo.


## Cambio para compatibilidad Android

El conector directo `TikTokLiveJava` fue retirado del APK porque depende de `java.net.http.HttpClient`, que no está disponible como en Java de escritorio en Android. Ahora `TikTokConnector.kt` usa OkHttp WebSocket para comunicarse con el servicio `relay/` (Node.js). YouTubeConnector.kt se mantiene sin cambios. Para recibir eventos de TikTok es necesario desplegar el relay en un servidor con HTTPS/WSS y configurar la URL en la app. Esta fuente no se ha probado contra un directo real.
