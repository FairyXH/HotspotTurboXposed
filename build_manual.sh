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

echo "==> 2. d8 转 dex"
java -cp $BT/lib/d8.jar com.android.tools.r8.D8 \
  --release \
  --lib $ANDROID_JAR \
  --min-api 29 \
  --output $OUT/dex \
  $(find $OUT/classes -name '*.class') 2>&1 | head -10
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