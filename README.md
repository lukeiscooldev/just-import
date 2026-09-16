# Minecraft Pack Importer for Android (Kotlin + Jetpack Compose)

Native Android utility built in Kotlin and Jetpack Compose that imports .mcpack and .mcaddon files directly into Minecraft package directories using Android's Storage Access Framework (SAF).

## 🚀 Building Without Local RAM (GitHub Actions CI)
If your laptop has limited RAM (e.g. 4GB RAM or older Intel Core i3) and cannot run Android Studio or Gradle locally:
1. Push this project to a GitHub repository:
   ```bash
   git init
   git add .
   git commit -m "Initial Minecraft Pack Importer commit"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo-name>.git
   git push -u origin main
   ```
2. On GitHub, navigate to the **Actions** tab.
3. The workflow `.github/workflows/android.yml` will automatically run using GitHub's cloud runners (16 GB RAM).
4. Once completed (~2 minutes), click the workflow run, scroll to **Artifacts**, and download `Minecraft-Pack-Importer-APK`.
5. Transfer the APK to your phone and install!

## 💻 Building Locally in Android Studio
1. Open this folder in Android Studio (Ladybug / Hedgehog or newer).
2. Allow Gradle to sync.
3. Select an Android device (API 26 to 35) and press **Run** (Shift+F10).

## Architecture Highlights
- **Jetpack Compose Material 3**: Reactive single-activity UI with `ActivityResultContracts.OpenDocument` and `ActivityResultContracts.OpenDocumentTree`.
- **SAF Scoped Storage**: Resolves `Android/data/<package_name>/files/games/com.mojang/resource_packs` and `behavior_packs` even on Android 11–15+ using `takePersistableUriPermission`.
- **Zip Streaming**: Directly streams `.mcpack` and composite `.mcaddon` archives into `DocumentFile` outputs with live byte progress tracking.
