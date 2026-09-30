# Vosk: классы дергаются из нативного кода (JNI/JNA), R8 их трогать не может.
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# JNA: интерфейс VoskCLib ищется по имени в рантайме через рефлексию.
-keep class com.sun.jna.** { *; }
-keep class com.look.chat.voice.** { *; }
-dontwarn com.sun.jna.**

# kotlinx.serialization: сериализаторы генерируются компилятором и
# достаются рефлексией через Companion/serializer().
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class com.look.chat.**$$serializer { *; }
-keepclassmembers class com.look.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.look.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}
