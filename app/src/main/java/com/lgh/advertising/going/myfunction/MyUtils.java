package com.lgh.advertising.going.myfunction;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Region;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.lgh.advertising.going.BuildConfig;

public class MyUtils {
    private static final String contentProviderAuthority = "content://" + BuildConfig.APPLICATION_ID;
    private static Context mContext;

    public static void init(Context context) {
        mContext = context;
    }

    public static boolean requestShowAddDataWindow() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "showAddDataWindow");
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean isServiceRunning() {
        Cursor cursor = mContext.getContentResolver().query(Uri.parse(contentProviderAuthority), null, null, null, null);
        cursor.moveToFirst();
        int index = cursor.getColumnIndex("isRunning");
        boolean isRunning = cursor.getInt(index) == 1;
        cursor.close();
        return isRunning;
    }

    public static boolean requestUpdateAppDescribe(String packageName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "updateAppDescribe");
        contentValues.put("packageName", packageName);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestUpdateAutoFinder(String packageName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "updateAutoFinder");
        contentValues.put("packageName", packageName);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestUpdateWidget(String packageName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "updateWidget");
        contentValues.put("packageName", packageName);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestUpdateCoordinate(String packageName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "updateCoordinate");
        contentValues.put("packageName", packageName);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestUpdateKeepAliveByNotification(boolean enable) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "keepAliveByNotification");
        contentValues.put("value", enable);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestUpdateKeepAliveByFloatingWindow(boolean enable) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "keepAliveByFloatingWindow");
        contentValues.put("value", enable);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean getKeepAliveByNotification() {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return preferences.getBoolean("keepAliveByNotification", false);
    }

    public static boolean setKeepAliveByNotification(boolean enable) {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        preferences.edit().putBoolean("keepAliveByNotification", enable).apply();
        return true;
    }

    public static boolean getKeepAliveByFloatingWindow() {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return preferences.getBoolean("keepAliveByFloatingWindow", false);
    }

    public static boolean setKeepAliveByFloatingWindow(boolean enable) {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        preferences.edit().putBoolean("keepAliveByFloatingWindow", enable).apply();
        return true;
    }

    public static boolean requestUpdateAllDate() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "allDate");
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestShowDbClickSetting() {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "showDbClickSetting");
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean requestUpdateShowDbClickFloating(boolean enable) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("updateScope", "showDbClickFloating");
        contentValues.put("value", enable);
        int re = mContext.getContentResolver().update(Uri.parse(contentProviderAuthority), contentValues, null, null);
        return re > 0;
    }

    public static boolean getDbClickEnable() {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return preferences.getBoolean("dbClickEnable", false);
    }

    public static boolean setDbClickEnable(boolean enable) {
        SharedPreferences preferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        preferences.edit().putBoolean("dbClickEnable", enable).apply();
        return true;
    }

    public static Rect getDbClickPosition() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mContext.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(displayMetrics);
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        float left = sharedPreferences.getFloat("dbClickLeftPercent", (displayMetrics.widthPixels - 150f) / displayMetrics.widthPixels);
        float top = sharedPreferences.getFloat("dbClickTopPercent", 0);
        float right = sharedPreferences.getFloat("dbClickRightPercent", 1);
        float bottom = sharedPreferences.getFloat("dbClickBottomPercent", 100f / displayMetrics.heightPixels);
        Log.i("LinGH", "left:" + left);
        Rect rect = new Rect((int) (left * displayMetrics.widthPixels), (int) (top * displayMetrics.heightPixels), (int) (right * displayMetrics.widthPixels), (int) (bottom * displayMetrics.heightPixels));
        Log.i("LinGH: ", "rect " + rect);
        return rect;
    }

    public static boolean setDbClickPosition(Rect rect) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        mContext.getSystemService(WindowManager.class).getDefaultDisplay().getRealMetrics(displayMetrics);
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        sharedPreferences.edit().putFloat("dbClickLeftPercent", (float) rect.left / displayMetrics.widthPixels).apply();
        sharedPreferences.edit().putFloat("dbClickTopPercent", (float) rect.top / displayMetrics.heightPixels).apply();
        sharedPreferences.edit().putFloat("dbClickRightPercent", (float) rect.right / displayMetrics.widthPixels).apply();
        sharedPreferences.edit().putFloat("dbClickBottomPercent", (float) rect.bottom / displayMetrics.heightPixels).apply();
        return true;
    }

    public static boolean getIsFirstStart() {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        return sharedPreferences.getBoolean("isFirstStart", true);
    }

    public static void setIsFirstStart(boolean isFirstStart) {
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        sharedPreferences.edit().putBoolean("isFirstStart", isFirstStart).apply();
    }
}
