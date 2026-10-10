# LSPosed/libxposed 模块规则
# 关键：模块入口类由 LSPosed 反射实例化并调用所有回调方法，
# 必须保留 XposedModule 子类及全部覆写回调，禁止裁剪/混淆。

-keepattributes RuntimeVisibleAnnotations
-keepattributes InnerClasses,Signature,EnclosingMethod

# 保留所有继承 XposedModule 的入口类及其全部成员（回调方法由反射调用）
-keep public class * extends io.github.libxposed.api.XposedModule {
    public <init>(...);
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onSystemServerStarting(...);
    public void onSystemServerLoaded(...);
    public void onAttach(...);
    *;
}

# 保留注解驱动的 hook 回调
-keep,allowoptimization,allowobfuscation @io.github.libxposed.api.annotations.* class * {
    @io.github.libxposed.api.annotations.BeforeInvocation <methods>;
    @io.github.libxposed.api.annotations.AfterInvocation <methods>;
}

# 保留整个业务包（避免后续开启 R8 时裁剪）
-keep class com.fairyxh.hotspotturboxposed.** { *; }

# 保留 libxposed api 接口（compileOnly 场景，运行时由框架提供）
-dontwarn io.github.libxposed.api.**

# 资源文件适配（java_init.list 内容用于运行时反射类名，禁止改写）
-adaptresourcefilecontents META-INF/xposed/java_init.list
-adaptresourcefilenames META-INF/xposed/**

-dontwarn androidx.annotation.**