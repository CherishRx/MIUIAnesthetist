package com.xposed.miuianesthetist;

import java.lang.reflect.Field;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;


public class DisableSecurityCheck implements IXposedHookLoadPackage {
//    private static final Object lock = new Object();
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("android")) {

            /* 禁用开机检查自身完整性
             com.miui.server.SecurityManagerService中的checkSystemSelfProtection方法过短，
             无法插入跳转指令进行hook，故hook其方法内创建的匿名内部类的run方法 */
            try {
                findAndHookMethod("com.miui.server.SecurityManagerService$1"
                        , lpparam.classLoader, "run", XC_MethodReplacement.DO_NOTHING);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            /* 禁用检查应用是否可以被禁用 */
            try {
                findAndHookMethod("com.android.server.pm.PackageManagerServiceInjector"
                        , lpparam.classLoader, "isAllowedDisable",
                        String.class, int.class, XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }

            /* 在intent被MIUI处理之前直接原样返回，避免http/https协议被劫持到小米浏览器，
            同时避免market/http/https协议的应用商店链接被劫持，*/
            try {
                Class<?> pmsClazz = XposedHelpers.findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader);
                findAndHookMethod("com.android.server.pm.PackageManagerServiceInjector"
                        , lpparam.classLoader, "checkMiuiIntent",
                        pmsClazz, Intent.class, String.class, int.class, List.class, int.class, new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                Intent intent = (Intent) param.args[1];
                                if(("market".equals(intent.getScheme()) && "android.intent.action.VIEW".equals(intent.getAction()))||
                                        ("http".equals(intent.getScheme()) || "https".equals(intent.getScheme())) &&
                                "android.intent.action.VIEW".equals(intent.getAction())){
                                    XposedBridge.log("checkMiuiIntent return original result");
                                    //返回第一个参数pms的mResolveInfo属性
                                    return XposedHelpers.findField(pmsClazz,"mResolveInfo").get(param.args[0]);
                                }
                                XposedBridge.log("checkMiuiIntent return injected result");
                                return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                            }
                        });
            } catch (Exception e) {
                e.printStackTrace();
            }

            /* 弃用 禁用MIUI SDK 中对URL的处理
            if (lpparam.packageName.equals("com.miui.core")) {
                findAndHookMethod("miui.util.UrlResolver", lpparam.classLoader, "lP",
                        Context.class, boolean.class, PackageManager.class, Object.class, Intent.class,
                        String.class, int.class, List.class, int.class, new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                XposedBridge.log("checkMiuiIntent call a lP method");
                                //TODO 此处不能返回null
                                return null;
                            }
                        });
            }*/

            /* 弃用 禁用小米浏览器劫持http/https链接，当SDK<23时
            try {
                findAndHookMethod("com.android.server.pm.PackageManagerServiceInjector"
                        , lpparam.classLoader, "getBrowserResolveInfo",
                        List.class, XC_MethodReplacement.returnConstant(null));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }*/

            /*弃用 禁用小米应用商店劫持其他应用商店 来自 ccat3z@github [我不要小米应用市场]
            try {
                findAndHookMethod("com.android.server.pm.PackageManagerServiceInjector"
                        , lpparam.classLoader, "getMarketResolveInfo",
                        List.class, XC_MethodReplacement.returnConstant(null));
            } catch (Throwable t) {
                XposedBridge.log(t);
            }*/

            /* 弃用 禁用小米应用商店劫持其他应用商店 来自 跟悟空扯关系@酷安网coolapk.com
                去除MIUI强制调用小米应用商店（非改build）
                https://www.coolapk.com/feed/8492730?shareKey=MjM2ODkyMTI5Zjg4NWNlZDJhMzI~
            try {

                Field IS_INTERNATIONAL_BUILD = XposedHelpers.findField(XposedHelpers.findClass("miui.os.Build", lpparam.classLoader), "IS_INTERNATIONAL_BUILD");
                boolean iib = IS_INTERNATIONAL_BUILD.getBoolean(null);
                if (!iib) {
                    XposedBridge.log("Get MIUI Build: NOT international build");
                    Class<?> pmsClazz = XposedHelpers.findClass("com.android.server.pm.PackageManagerService", lpparam.classLoader);
                    findAndHookMethod("com.android.server.pm.PackageManagerServiceInjector"
                            , lpparam.classLoader, "checkMiuiIntent",
                            pmsClazz, Intent.class, String.class, int.class, List.class, int.class, new XC_MethodReplacement() {
                                @Override
                                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                                    Object result;
                                    synchronized (lock){
                                        IS_INTERNATIONAL_BUILD.setBoolean(null,true);
                                        XposedBridge.log("Set MIUI Build to international build");
                                        result = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                                        IS_INTERNATIONAL_BUILD.setBoolean(null,false);
                                        XposedBridge.log("Set MIUI Build back to NOT international build");
                                    }
                                    return result;
                                }
                            });
                }

            } catch (Throwable t) {
                XposedBridge.log(t);
            }*/

        }

    }
}
