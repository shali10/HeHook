# 保留 Xposed 模块入口
-keep class top.hehook.app.core.MainRouter { *; }
-keep class top.hehook.app.core.LegacyInit { *; }

# 保留 DexKit 相关类与 JNI 符号
-keep class org.luckypray.dexkit.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 Kotlin 反射和序列化元数据
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
