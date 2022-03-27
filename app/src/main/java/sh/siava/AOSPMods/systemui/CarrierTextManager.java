package sh.siava.AOSPMods.systemui;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import sh.siava.AOSPMods.IXposedModPack;
import sh.siava.AOSPMods.XPrefs;

public class CarrierTextManager implements IXposedModPack {
    public static final String listenPackage = "com.android.systemui";
    public static boolean isEnabled = false;
    public static String customText = "";

    public void updatePrefs()
    {
        if(XPrefs.Xprefs == null) return;
        isEnabled = XPrefs.Xprefs.getBoolean("carrierTextMod", false);
        customText = XPrefs.Xprefs.getString("carrierTextValue", "");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if(!lpparam.packageName.equals(listenPackage)) return;

        Class CarrierTextCallbackInfo = XposedHelpers.findClass("com.android.keyguard.CarrierTextManager$CarrierTextCallbackInfo", lpparam.classLoader);

        XposedBridge.hookAllConstructors(CarrierTextCallbackInfo, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if(isEnabled)
                {
                    XposedHelpers.setObjectField(param.thisObject, "carrierText", customText);
                    XposedHelpers.setObjectField(param.thisObject, "listOfCarriers", new CharSequence[0]);
                    XposedHelpers.setObjectField(param.thisObject, "anySimReady", true);
                    XposedHelpers.setObjectField(param.thisObject, "subscriptionIds", new int[0]);
                    XposedHelpers.setObjectField(param.thisObject, "airplaneMode", false);
                    param.setResult(null);
                }
            }
        });

    }

    @Override
    public String getListenPack() {
        return listenPackage;
    }

}