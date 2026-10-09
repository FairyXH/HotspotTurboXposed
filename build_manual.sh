#!/bin/bash
# 手工构建 HotspotTurboXposed APK（绕开 AGP/AAPT2 的架构问题）
# 原理：javac 编译 → d8 转 dex → aapt(AAPT1) 打包资源/清单 → apksigner 签名
# 注意：Debian aapt 与 API 23 的 android.jar 兼容（API 36 jar 会导致 aapt 崩溃）
set -e

# 路径
SDK=/opt/android-sdk
BT=$SDK/build-tools/36.0.0
OUT=/tmp/htx_build
APP=com.fairyxh.hotspotturboxposed
SRC=/root/HotspotTurboXposed/app/src/main
ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
LIBXP=/root/HotspotTurboXposed/libs/libxposed-api-102.jar

echo "==> 清理"
rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/apk $OUT/res $OUT/gen

echo "==> 1. javac 编译"
find $SRC/java -name '*.java' > $OUT/sources.txt
javac -source 17 -target 17 \
  -cp "$LIBXP:$ANDROID_JAR" \
  -d $OUT/classes @$OUT/sources.txt 2>&1 | head -20 || true

echo "==> 2. d8 转 dex（内嵌 libxposed API + Service 类！）"
# 关键：libxposed API/Service 类必须打进 dex，否则 LSPosed 加载时
# ClassNotFoundException: io.github.libxposed.api.XposedModule
LXP_CLASSES=/tmp/lxp_classes
SVC_CLASSES=/tmp/lxp_service/svc_classes
if [ ! -d "$LXP_CLASSES" ]; then
  cd /tmp && rm -rf lxp_classes && mkdir lxp_classes
  cd /root/HotspotTurboXposed/libs && unzip -o -q libxposed-api-102.jar -d /tmp/lxp_classes
fi
if [ ! -d "$SVC_CLASSES" ]; then
  echo "!! 缺少 libxposed service classes，请先下载 service aar 解压到 /tmp/lxp_service/svc_classes"
  exit 1
fi
java -cp $BT/lib/d8.jar com.android.tools.r8.D8 \
  --release \
  --lib $ANDROID_JAR \
  --min-api 29 \
  --output $OUT/dex \
  $(find $OUT/classes -name '*.class') \
  $(find $LXP_CLASSES -name '*.class') \
  $(find $SVC_CLASSES -name '*.class') 2>&1 | head -10
ls -la $OUT/dex/classes.dex 2>&1

echo "==> 3. 准备资源"
# 只复制 values（xposed_scope），跳过 mipmap/drawable 图标（LSPosed 模块不需要图标）
mkdir -p $OUT/res/values
cp $SRC/res/values/xposed_scope.xml $OUT/res/values/ 2>/dev/null || true
cp -r $SRC/assets $OUT/ 2>/dev/null || true
# 最小清单
cat > $OUT/AndroidManifest.xml <<'MANIFEST'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.fairyxh.hotspotturboxposed"
    android:versionCode="1"
    android:versionName="1.0.0">
    <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="36"/>
    <application android:label="Hotspot Turbo Xposed"
        android:allowBackup="false">
        <meta-data android:name="xposedmodule" android:value="true" />
        <meta-data android:name="xposedminversion" android:value="102" />
        <meta-data android:name="xposeddescription" android:value="热点加速与网段锁定" />
        <meta-data android:name="xposedscope" android:resource="@array/xposed_scope" />

        <!-- libxposed service：提供作用域推荐/远程配置（LSPosed 新式模块注册） -->
        <provider
            android:name="io.github.libxposed.service.XposedProvider"
            android:authorities="com.fairyxh.hotspotturboxposed.XposedService"
            android:exported="true"
            tools:ignore="ExportedContentProvider" />
    </application>
</manifest>
MANIFEST
cat > $OUT/res/values/xposed_scope.xml <<'SCOPE'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string-array name="xposed_scope">
        <item>com.android.networkstack.process</item>
        <item>com.android.networkstack</item>
        <item>com.android.server</item>
    </string-array>
</resources>
SCOPE

echo "==> 4. aapt 打包 base.apk"
aapt package -f -F $OUT/base.apk -M $OUT/AndroidManifest.xml -S $OUT/res \
  -A $SRC/assets -I $ANDROID_JAR 2>&1 | head -10

echo "==> 4.5 加入 META-INF/xposed/ 现代模块声明"
# 现代 libxposed 模块作用域声明（LSPosed IT 识别依赖这些文件）
mkdir -p $OUT/meta/META-INF/xposed
cat > $OUT/meta/META-INF/xposed/module.prop <<'MP'
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
MP
cat > $OUT/meta/META-INF/xposed/java_init.list <<'JIL'
com.fairyxh.hotspotturboxposed.Entry
JIL
cat > $OUT/meta/META-INF/xposed/scope.list <<'SL'
com.android.networkstack
com.google.android.networkstack
com.android.server
SL
# 加入 base.apk
cd $OUT/meta && zip -q $OUT/base.apk META-INF/xposed/module.prop META-INF/xposed/java_init.list META-INF/xposed/scope.list

echo "==> 5. 加入 dex"
cd $OUT/apk
cp $OUT/dex/classes.dex .
zip -q $OUT/base.apk classes.dex

echo "==> 6. zipalign + 签名"
zipalign -f 4 $OUT/base.apk $OUT/aligned.apk
if [ ! -f /root/.android/htx.keystore ]; then
  keytool -genkeypair -v -keystore /root/.android/htx.keystore \
    -alias htx -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass htxpwd -keypass htxpwd -dname "CN=HTX, OU=HTX, O=HTX, C=CN" 2>&1 | tail -3
fi
apksigner sign --min-sdk-version 29 --ks /root/.android/htx.keystore --ks-pass pass:htxpwd \
  --key-pass pass:htxpwd --out $OUT/htxposed.apk $OUT/aligned.apk 2>&1 | head -5

echo "==> 完成: $OUT/htxposed.apk"
ls -la $OUT/htxposed.apk