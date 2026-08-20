# Preserve source and line metadata so Crashlytics can deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# Kakao SDK uses Retrofit dynamic proxies that read generic return types and
# HTTP annotations at runtime. Keep this metadata while retaining R8 shrinking
# and obfuscation for implementation classes.
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Keep the compact package layout that AGP 9.1 enables by default while the
# project remains on Android Studio Panda 1-compatible AGP 9.0.
-repackageclasses
