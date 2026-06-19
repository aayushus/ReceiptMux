# OpenCV — Java bindings call into native code via JNI; keep them intact.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Apache Commons Net (FTP/FTPS).
-keep class org.apache.commons.net.** { *; }
-dontwarn org.apache.commons.net.**

# jcifs-ng (SMB) relies on reflection and service providers.
-keep class jcifs.** { *; }
-dontwarn jcifs.**

# BouncyCastle is pulled in by jcifs-ng for SMB2/3 crypto and uses heavy reflection.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SLF4J logging facade used by jcifs-ng.
-dontwarn org.slf4j.**

# ML Kit text recognition.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep Kotlin metadata / Coroutines internals that may be accessed reflectively.
-dontwarn kotlinx.coroutines.**
