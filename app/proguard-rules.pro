# kotlinx.serialization：保留 @Serializable 類別的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 專案內所有 @Serializable 資料模型（含 sealed 子類）
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.routina.app.model.**$$serializer { *; }
-keepclassmembers class com.routina.app.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.routina.app.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 元件由系統以反射建立
-keep class com.routina.app.RoutinaApp
-keep class com.routina.app.MainActivity
-keep class com.routina.app.engine.AlarmReceiver
-keep class com.routina.app.engine.BootReceiver
-keep class com.routina.app.engine.GeofenceReceiver
-keep class com.routina.app.engine.BtAclReceiver
-keep class com.routina.app.engine.MonitorService
-keep class com.routina.app.engine.ExecutionService
-keep class com.routina.app.engine.NfcDispatchActivity
-keep class com.routina.app.engine.RoutinaNotificationListener
