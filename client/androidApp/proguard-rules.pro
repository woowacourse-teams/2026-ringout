# Preserve source and line metadata so Crashlytics can deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# Keep the compact package layout that AGP 9.1 enables by default while the
# project remains on Android Studio Panda 1-compatible AGP 9.0.
-repackageclasses
