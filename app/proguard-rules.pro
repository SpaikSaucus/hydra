# Hydra ProGuard / R8 rules.

# kotlinx.serialization: keep generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.personal.hydra.** {
    *** Companion;
}
-keepclasseswithmembers class com.personal.hydra.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.personal.hydra.**$$serializer { *; }

# Room generated implementations are kept by the Room consumer rules.
# WorkManager Worker subclasses are instantiated reflectively.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
