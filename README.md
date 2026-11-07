# 📺 VisionTV IP (Dahua Android TV Viewer)

Aplicación nativa para **Android TV / Google TV** diseñada para visualizar grabadores **NVR / XVR Dahua** y cámaras IP en tiempo real con soporte para:

- **Cuadrícula Dinámica Multicámara (1, 4, 8, 9, 12 y 16 canales)** con detección automática de señal en vivo.
- **Modo Cuadrícula en Pantalla Completa (Sin Bordes):** Expansión de toda la cuadrícula al 100% de la pantalla (regresa a la cuadrícula normal con el botón **Atrás** del control remoto).
- **Organizador Rápido de Cámaras:** Cualquier usuario puede reordenar o intercambiar cámaras desde la pantalla principal sin requerir PIN de administrador.
- **Selector de Calidad SD / HD Independiente:** Alterna entre Sub Stream (`SD`) y Main Stream (`HD`) tanto en la cuadrícula general como de forma exclusiva en la cámara activa en pantalla completa.
- **Actualizaciones Automáticas OTA vía GitHub Releases:** Verifica, descarga e instala nuevas versiones directamente desde este repositorio (`iamblanquet/VisionTVip`).

---

## 🚀 Generación Automática de APK y Actualizaciones (GitHub Actions)

Cada vez que se sube un cambio a la rama `main`:
1. El flujo [`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) compila y firma automáticamente el APK de producción usando `app/visiontv.keystore`.
2. Publica un **GitHub Release** con la versión definida en `versionName` dentro de [`app/build.gradle.kts`](app/build.gradle.kts).
3. Todas las televisiones con **VisionTV** detectan la nueva versión y permiten actualizar con un clic desde el control remoto.

### ¿Cómo publicar una nueva actualización para las TVs?
1. Incrementa `versionCode` y `versionName` (ej. `"1.0.17"`) en `app/build.gradle.kts`.
2. Haz `git commit` y `git push origin main`.
3. GitHub Actions generará el APK y publicará el Release automáticamente.
