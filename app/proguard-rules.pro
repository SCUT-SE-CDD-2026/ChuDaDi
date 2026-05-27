# =============================================================
# ONNX Runtime Android - R8/ProGuard keep rules
# =============================================================
# ONNX Runtime 的 C++ native 层通过 JNI 回调 Java 构造函数，
# R8 无法静态分析这些调用路径，必须保留整个 ai.onnxruntime 包。
# 官方参考：https://github.com/microsoft/onnxruntime/pull/14407
# 相关 Issue：https://github.com/microsoft/onnxruntime/issues/14379
# =============================================================

-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
-keep enum ai.onnxruntime.** { *; }

# 保留 native 方法（JNI 桥接）
-keepclasseswithmembernames class * {
    native <methods>;
}

# =============================================================
# 调试辅助
# =============================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile