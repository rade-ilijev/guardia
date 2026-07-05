# Guardia R8 / ProGuard rules.
#
# Most AndroidX/Hilt/Room/ML Kit artifacts ship their own consumer rules, so we only add keeps for
# libraries that rely on reflection or JNI and would otherwise be stripped/renamed.

# Keep crash-stack line numbers readable (the app has an opt-in local crash log).
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- JavaMail (com.sun.mail) + javax.activation ---
# Uses reflection over provider classes and META-INF service descriptors.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class mymail.** { *; }
-dontwarn com.sun.mail.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**
-dontwarn java.awt.**
-dontwarn javax.security.**

# --- Picovoice Porcupine (voice safeword) ---
# JNI bindings resolve native methods by class/method name.
-keep class ai.picovoice.** { *; }
-dontwarn ai.picovoice.**

# --- LiteRT / TensorFlow Lite (on-device face embedder) ---
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn com.google.ai.edge.litert.**

# --- Google Play Billing ---
-keep class com.android.billingclient.api.** { *; }

# --- Kotlin coroutines internals occasionally referenced reflectively ---
-dontwarn kotlinx.coroutines.**
