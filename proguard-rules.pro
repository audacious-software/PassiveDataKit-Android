# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class com.audacious_software.passive_data_kit.** { *; }
-keepattributes LineNumberTable,SourceFile

-dontwarn javax.xml.bind.**

-dontwarn com.tobrun.datacompat.annotation.Default
-dontwarn io.grpc.internal.DnsNameResolverProvider
-dontwarn io.grpc.internal.PickFirstLoadBalancerProvider

