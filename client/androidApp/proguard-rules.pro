# Preserve source and line metadata so Crashlytics can deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# TODO(RINGOUT_ACCOUNT): 카카오 로그인을 재도입할 때 SDK의 R8 규칙을 함께 복구한다.

# Keep the compact package layout that AGP 9.1 enables by default while the
# project remains on Android Studio Panda 1-compatible AGP 9.0.
-repackageclasses
