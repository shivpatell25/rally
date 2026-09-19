# Retrofit and Gson inspect these signatures and annotations at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# API payloads are decoded reflectively by Gson.
-keep class com.shiv.rally.data.remote.** { *; }

# Keep Retrofit service interfaces while allowing the rest of the app to shrink.
-keep,allowoptimization,allowshrinking interface com.shiv.rally.data.remote.** { *; }

# Retrofit 2.9's bundled rules predate the R8 full-mode rules for suspend APIs.
# Preserve the Continuation type argument and reflected response types.
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

# Release builds must not emit provider URLs, event metadata, or network failures to logcat.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}
