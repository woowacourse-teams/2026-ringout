# Preserve source and line metadata so Crashlytics can deobfuscate R8 stack traces.
-keepattributes SourceFile,LineNumberTable

# Kakao SDK uses Retrofit dynamic proxies that read generic return types and
# HTTP annotations at runtime. Keep this metadata while retaining R8 shrinking
# and obfuscation for implementation classes.
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Retrofit 2.9.0 does not bundle the full-mode rule that keeps an HTTP
# endpoint's return wrapper. Without it, R8 reduces Call<T> to raw Call and
# Kakao's UserApi proxy crashes while parsing the method annotations.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Kakao SDK response models and Retrofit's reflective helper types must remain
# eligible for generic-signature inspection in R8 full mode.
-keep class com.kakao.sdk.**.model.* { <fields>; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Keep the compact package layout that AGP 9.1 enables by default while the
# project remains on Android Studio Panda 1-compatible AGP 9.0.
-repackageclasses
