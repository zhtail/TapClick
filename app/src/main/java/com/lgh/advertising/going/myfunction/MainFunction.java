package com.lgh.advertising.going.myfunction;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.app.RemoteAction;
import android.app.appsearch.StorageInfo;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.net.Uri;
import android.net.eap.EapSessionConfig;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.lgh.advertising.going.R;
import com.lgh.advertising.going.databinding.ViewAddDataBinding;
import com.lgh.advertising.going.databinding.ViewWidgetSelectBinding;
import com.lgh.advertising.going.myactivity.EditDataActivity;
import com.lgh.advertising.going.mybean.AppDescribe;
import com.lgh.advertising.going.mybean.AutoFinder;
import com.lgh.advertising.going.mybean.Coordinate;
import com.lgh.advertising.going.myclass.DataDao;
import com.lgh.advertising.going.myclass.MyApplication;
import com.lgh.advertising.going.mybean.Widget;

import java.io.File;
import java.security.spec.ECField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * adb shell pm grant com.lgh.advertising.going android.permission.WRITE_SECURE_SETTINGS
 * adb shell settings put secure enabled_accessibility_services com.lgh.advertising.going/com.lgh.advertising.going.myfunction.MyAccessibilityService
 * adb shell settings put secure accessibility_enabled 1
 * <p>
 * Settings.Secure.putString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, getPackageName()+"/"+MyAccessibilityService.class.getName());
 * Settings.Secure.putString(getContentResolver(),Settings.Secure.ACCESSIBILITY_ENABLED, "1");
 */

public class MainFunction {

    private WindowManager windowManager;
    private PackageManager packageManager;
    private DataDao dataDao;
    private Map<String, AppDescribe> appDescribeMap;
    private AppDescribe appDescribe;
    private final AccessibilityService service;
    private String currentPackage;
    private String currentActivity;
    private boolean onOffCoordinate, onOffWidget, onOffAutoFinder;
    private boolean onOffCoordinateSub, onOffWidgetSub;
    private int autoRetrieveNumber;
    private boolean widgetAllNoRepeat;
    private AccessibilityServiceInfo serviceInfo;
    private ScheduledFuture<?> futureCoordinate, futureWidget, futureAutoFinder;
    private ScheduledExecutorService executorService;
    private MyScreenOffReceiver screenOffReceiver;
    private Set<Widget> widgetSet;
    private MyInstallReceiver installReceiver;
    private HashSet<Widget> alreadyClickSet;
    private Map<String, Coordinate> coordinateMap;
    private List<String> keywordList;
    private Map<String, Set<Widget>> widgetSetMap;
    private Coordinate coordinate;

    private WindowManager.LayoutParams aParams, bParams, cParams;
    private ViewAddDataBinding addDataBinding;
    private ViewWidgetSelectBinding widgetSelectBinding;
    private ImageView viewClickPosition;

    private Handler handler = new Handler();

    public MainFunction(AccessibilityService service) {
        this.service = service;
    }

    protected void onServiceConnected() {
        try {
            windowManager = service.getSystemService(WindowManager.class);
            packageManager = service.getPackageManager();
            currentPackage = "Initialize CurrentPackage";
            currentActivity = "Initialize CurrentActivity";
            executorService = Executors.newSingleThreadScheduledExecutor();
            serviceInfo = service.getServiceInfo();
            dataDao = MyApplication.dataDao;
            appDescribeMap = new HashMap<>();
            screenOffReceiver = new MyScreenOffReceiver();
            service.registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
            installReceiver = new MyInstallReceiver();
            IntentFilter filterInstall = new IntentFilter();
            filterInstall.addAction(Intent.ACTION_PACKAGE_ADDED);
            filterInstall.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
            filterInstall.addDataScheme("package");
            service.registerReceiver(installReceiver, filterInstall);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    getRunningData();
                }
            });
            futureCoordinate = futureWidget = futureAutoFinder = executorService.schedule(new Runnable() {
                @Override
                public void run() {
                }
            }, 0, TimeUnit.MILLISECONDS);
        } catch (Throwable throwable) {
            toastErr(throwable);
            throwable.printStackTrace();
        }
    }

    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            switch (event.getEventType()) {
                case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                    AccessibilityNodeInfo root = service.getRootInActiveWindow();
                    String packageName = root != null ? root.getPackageName().toString() : null;
                    String activityName = event.getClassName().toString();
                    if (packageName != null && !packageName.equals(currentPackage)) {
                        currentPackage = packageName;
                        appDescribe = appDescribeMap.get(packageName);
                        futureAutoFinder.cancel(false);
                        futureWidget.cancel(false);
                        futureCoordinate.cancel(false);
                        serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                        service.setServiceInfo(serviceInfo);
                        onOffAutoFinder = false;
                        onOffWidget = false;
                        onOffCoordinate = false;
                        onOffWidgetSub = false;
                        onOffCoordinateSub = false;
                        keywordList = null;
                        widgetSetMap = null;
                        coordinateMap = null;
                        autoRetrieveNumber = 0;
                        if (appDescribe != null && appDescribe.onOff) {
                            keywordList = appDescribe.autoFinder.keywordList;
                            widgetSetMap = appDescribe.widgetSetMap;
                            coordinateMap = appDescribe.coordinateMap;
                            onOffAutoFinder = appDescribe.autoFinderOnOFF && !keywordList.isEmpty();
                            onOffWidget = appDescribe.widgetOnOff && !widgetSetMap.isEmpty();
                            onOffCoordinate = appDescribe.coordinateOnOff && !coordinateMap.isEmpty();

                            if (onOffAutoFinder) {
                                serviceInfo.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                service.setServiceInfo(serviceInfo);
                            }

                            if (onOffAutoFinder && !appDescribe.autoFinderRetrieveAllTime) {
                                futureAutoFinder = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        onOffAutoFinder = false;
                                        if (!onOffWidgetSub) {
                                            serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                            service.setServiceInfo(serviceInfo);
                                        }
                                    }
                                }, appDescribe.autoFinderRetrieveTime, TimeUnit.MILLISECONDS);
                            }

                            if (onOffWidget && !appDescribe.widgetRetrieveAllTime) {
                                futureWidget = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        onOffWidget = false;
                                        onOffWidgetSub = false;
                                        if (!onOffAutoFinder) {
                                            serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                            service.setServiceInfo(serviceInfo);
                                        }
                                    }
                                }, appDescribe.widgetRetrieveTime, TimeUnit.MILLISECONDS);
                            }

                            if (onOffCoordinate && !appDescribe.coordinateRetrieveAllTime) {
                                futureCoordinate = executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        onOffCoordinate = false;
                                        onOffCoordinateSub = false;
                                    }
                                }, appDescribe.coordinateRetrieveTime, TimeUnit.MILLISECONDS);
                            }
                        }
                    }
                    if (!activityName.equals(currentActivity)
                            && !activityName.startsWith("android.widget.")
                            && !activityName.startsWith("android.view.")
                            && !activityName.equals("android.inputmethodservice.SoftInputWindow")) {
                        currentActivity = activityName;
                        alreadyClickSet = new HashSet<>();
                        onOffWidgetSub = false;
                        onOffCoordinateSub = false;
                        widgetAllNoRepeat = true;
                        widgetSet = null;
                        coordinate = null;

                        if (!onOffAutoFinder) {
                            serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                            service.setServiceInfo(serviceInfo);
                        }

                        if (appDescribe != null) {
                            coordinate = coordinateMap != null ? coordinateMap.get(activityName) : null;
                            widgetSet = widgetSetMap != null ? widgetSetMap.get(activityName) : null;
                            onOffCoordinateSub = onOffCoordinate && coordinate != null;
                            onOffWidgetSub = onOffWidget && widgetSet != null;

                            if (widgetSet != null && !widgetSet.isEmpty()) {
                                for (Widget e : widgetSet) {
                                    if (!e.noRepeat) {
                                        widgetAllNoRepeat = false;
                                        break;
                                    }
                                }
                            }

                            if (onOffWidgetSub) {
                                serviceInfo.eventTypes |= AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                service.setServiceInfo(serviceInfo);
                            }

                            if (onOffCoordinateSub) {
                                executorService.scheduleAtFixedRate(new Runnable() {
                                    private final Coordinate coordinateSub = coordinate;
                                    private int num = 0;

                                    @Override
                                    public void run() {
                                        if (++num <= coordinateSub.clickNumber && currentActivity.equals(coordinateSub.appActivity)) {
                                            click(coordinateSub.xPosition, coordinateSub.yPosition, 0, 20);
                                        } else {
                                            throw new RuntimeException();
                                        }
                                    }
                                }, coordinate.clickDelay, coordinate.clickInterval, TimeUnit.MILLISECONDS);
                            }
                        }
                    }
                    if (onOffAutoFinder && appDescribe != null && root != null) {
                        findButtonByText(root, appDescribe.autoFinder);
                    }
                    if (onOffWidgetSub && widgetSet != null && root != null) {
                        findButtonByWidget(root, widgetSet);
                    }
                    Log.i("LinGH_Window", root + "");
                    break;
                case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                    if (event.getPackageName().equals(currentPackage)) {
                        AccessibilityNodeInfo source = event.getSource();
                        if (onOffAutoFinder && appDescribe != null && source != null) {
                            findButtonByText(source, appDescribe.autoFinder);
                        }
                        if (onOffWidgetSub && widgetSet != null && source != null) {
                            findButtonByWidget(source, widgetSet);
                        }
                        Log.i("LinGH_Content", source + "");
                    }
                    break;
            }
        } catch (Throwable throwable) {
            toastErr(throwable);
            throwable.printStackTrace();
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        try {
            if (addDataBinding != null && viewClickPosition != null && widgetSelectBinding != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
                aParams.x = (metrics.widthPixels - aParams.width) / 2;
                aParams.y = metrics.heightPixels - aParams.height;
                bParams.width = metrics.widthPixels;
                bParams.height = metrics.heightPixels;
                cParams.x = (metrics.widthPixels - cParams.width) / 2;
                cParams.y = (metrics.heightPixels - cParams.height) / 2;
                windowManager.updateViewLayout(addDataBinding.getRoot(), aParams);
                windowManager.updateViewLayout(viewClickPosition, cParams);
                widgetSelectBinding.frame.removeAllViews();
                TextView text = new TextView(service);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                text.setGravity(Gravity.CENTER);
                text.setTextColor(0xffff0000);
                text.setText("请重新刷新布局");
                widgetSelectBinding.frame.addView(text, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
                windowManager.updateViewLayout(widgetSelectBinding.frame, bParams);
            }
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }

    }

    public boolean onUnbind(Intent intent) {
        try {
            service.unregisterReceiver(screenOffReceiver);
            service.unregisterReceiver(installReceiver);
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }
        return true;
    }

    /**
     * 自动查找启动广告的
     * “跳过”的控件
     */
    private void findButtonByText(AccessibilityNodeInfo nodeInfo, final AutoFinder autoFinder) {
        try {
            for (int n = 0; n < autoFinder.keywordList.size(); n++) {
                final List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByText(autoFinder.keywordList.get(n));
                if (!list.isEmpty()) {
                    executorService.schedule(new Runnable() {
                        @Override
                        public void run() {
                            if (autoFinder.clickOnly) {
                                for (AccessibilityNodeInfo e : list) {
                                    if (e.refresh()) {
                                        Rect rect = new Rect();
                                        e.getBoundsInScreen(rect);
                                        click(rect.centerX(), rect.centerY(), 0, 20);
                                    }
                                }
                            } else {
                                for (AccessibilityNodeInfo e : list) {
                                    if (e.refresh()) {
                                        if (!e.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                            if (!e.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                                Rect rect = new Rect();
                                                e.getBoundsInScreen(rect);
                                                click(rect.centerX(), rect.centerY(), 0, 20);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }, autoFinder.clickDelay, TimeUnit.MILLISECONDS);
                    if (++autoRetrieveNumber >= autoFinder.retrieveNumber) {
                        onOffAutoFinder = false;
                        if (!onOffWidgetSub) {
                            serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                            service.setServiceInfo(serviceInfo);
                        }
                    }
                    return;
                }
            }
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }
    }

    /**
     * 查找并点击由
     * Widget
     * 定义的控件
     */
    private void findButtonByWidget(AccessibilityNodeInfo root, Set<Widget> widgetSet) {
        try {
            int a = 0;
            int b = 1;
            ArrayList<AccessibilityNodeInfo> listA = new ArrayList<>();
            ArrayList<AccessibilityNodeInfo> listB = new ArrayList<>();
            listA.add(root);
            while (a < b) {
                final AccessibilityNodeInfo node = listA.get(a++);
                if (node != null) {
                    final Rect temRect = new Rect();
                    node.getBoundsInScreen(temRect);
                    CharSequence cId = node.getViewIdResourceName();
                    CharSequence cDescribe = node.getContentDescription();
                    CharSequence cText = node.getText();
                    for (final Widget e : widgetSet) {
                        boolean isFind = false;
                        if (temRect.equals(e.widgetRect)) {
                            isFind = true;
                        } else if (cId != null && !e.widgetId.isEmpty() && cId.toString().equals(e.widgetId)) {
                            isFind = true;
                        } else if (cDescribe != null && !e.widgetDescribe.isEmpty() && cDescribe.toString().contains(e.widgetDescribe)) {
                            isFind = true;
                        } else if (cText != null && !e.widgetText.isEmpty() && cText.toString().contains(e.widgetText)) {
                            isFind = true;
                        }
                        if (isFind) {
                            if (!e.noRepeat || !alreadyClickSet.contains(e)) {
                                alreadyClickSet.add(e);
                                executorService.schedule(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (node.refresh()) {
                                            if (e.clickOnly) {
                                                click(temRect.centerX(), temRect.centerY(), 0, 20);
                                            } else {
                                                if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                                    if (!node.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                                        click(temRect.centerX(), temRect.centerY(), 0, 20);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }, e.clickDelay, TimeUnit.MILLISECONDS);
                                if (!onOffAutoFinder && widgetAllNoRepeat && alreadyClickSet.size() >= widgetSet.size()) {
                                    serviceInfo.eventTypes &= ~AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
                                    service.setServiceInfo(serviceInfo);
                                }
                            }
                            break;
                        }
                    }
                    for (int n = 0; n < node.getChildCount(); n++) {
                        listB.add(node.getChild(n));
                    }
                }
                if (a == b) {
                    a = 0;
                    b = listB.size();
                    listA = listB;
                    listB = new ArrayList<>();
                }
            }
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }
    }

    /**
     * 查找所有
     * 的控件
     */
    private void findAllNode(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> list) {
        try {
            ArrayList<AccessibilityNodeInfo> tem = new ArrayList<>();
            for (AccessibilityNodeInfo e : roots) {
                if (e == null) continue;
                Rect rect = new Rect();
                e.getBoundsInScreen(rect);
                if (rect.width() <= 0 || rect.height() <= 0) continue;
                list.add(e);
                for (int n = 0; n < e.getChildCount(); n++) {
                    tem.add(e.getChild(n));
                }
            }
            if (!tem.isEmpty()) {
                findAllNode(tem, list);
            }
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }
    }

    /**
     * 模拟
     * 点击
     */
    private boolean click(int X, int Y, long startTime, long duration) {
        Path path = new Path();
        path.moveTo(X, Y);
        GestureDescription.Builder builder = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, startTime, duration));
        return service.dispatchGesture(builder.build(), null, null);
    }

    /**
     * 接收到锁屏事件时调用
     */
    public void onScreenOff() {
        currentPackage = "ScreenOff Package";
        currentActivity = "ScreenOff Activity";
    }

    /**
     * android 7.0 以上
     * 避免无障碍服务冲突
     */
    public void closeService() {
        service.disableSelf();
    }

    /**
     * 把数据暴露给其他Activity
     */
    public Map<String, AppDescribe> getAppDescribeMap() {
        return appDescribeMap;
    }

    /**
     * 开启无障碍服务时调用
     * 获取运行时需要的数据
     */
    private void getRunningData() {
        try {
            Set<String> pkgNormalSet = new HashSet<>();
            Set<String> pkgOffSet = new HashSet<>();
            Set<String> pkgNeedRemovedSet = new HashSet<>();
            //所有存在activity的应用
            Set<String> pkgHasActivitySet = packageManager
                    .getInstalledPackages(PackageManager.GET_ACTIVITIES)
                    .stream()
                    .filter(e -> e.activities != null)
                    .map(e -> e.packageName)
                    .collect(Collectors.toSet());
            pkgNormalSet.addAll(pkgHasActivitySet);
            //桌面和本应用需要默认关闭
            Set<String> pkgHasHomeSet = packageManager
                    .queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
                    .stream()
                    .map(e -> e.activityInfo.packageName)
                    .collect(Collectors.toSet());
            pkgOffSet.addAll(pkgHasHomeSet);
            pkgOffSet.add(service.getPackageName());
            //输入法、systemUI相关的应用是需要移除的，不做检测
            Set<String> pkgInputMethodSet = service
                    .getSystemService(InputMethodManager.class)
                    .getInputMethodList()
                    .stream()
                    .map(InputMethodInfo::getPackageName)
                    .collect(Collectors.toSet());
            pkgNeedRemovedSet.addAll(pkgInputMethodSet);
            pkgNeedRemovedSet.add("com.android.systemui");

            List<AppDescribe> appDescribeList = new ArrayList<>();
            List<AutoFinder> autoFinderList = new ArrayList<>();
            for (String e : pkgNormalSet) {
                if (pkgNeedRemovedSet.contains(e)) {
                    continue;
                }
                ApplicationInfo info = packageManager.getApplicationInfo(e, PackageManager.GET_META_DATA);
                AppDescribe appDescribe = new AppDescribe();
                appDescribe.appName = packageManager.getApplicationLabel(info).toString();
                appDescribe.appPackage = info.packageName;
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == ApplicationInfo.FLAG_SYSTEM || pkgOffSet.contains(info.packageName)) {
                    appDescribe.onOff = false;
                    appDescribe.autoFinderOnOFF = false;
                    appDescribe.coordinateOnOff = false;
                    appDescribe.widgetOnOff = false;
                }
                appDescribeList.add(appDescribe);
                AutoFinder autoFinder = new AutoFinder();
                autoFinder.appPackage = info.packageName;
                autoFinder.keywordList = Collections.singletonList("跳过");
                autoFinderList.add(autoFinder);
            }
            dataDao.deleteAppDescribeByNotIn(pkgNormalSet);
            dataDao.insertAppDescribe(appDescribeList);
            dataDao.insertAutoFinder(autoFinderList);
            for (AppDescribe e : dataDao.getAllAppDescribes()) {
                e.getOtherFieldsFromDatabase(dataDao);
                appDescribeMap.put(e.appPackage, e);
            }
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }
    }

    /**
     * 创建规则时调用
     */
    @SuppressLint("ClickableViewAccessibility")
    public void showAddDataFloat() {
        try {
            if (viewClickPosition != null || addDataBinding != null || widgetSelectBinding != null) {
                return;
            }
            final Widget widgetSelect = new Widget();
            final Coordinate coordinateSelect = new Coordinate();
            final LayoutInflater inflater = LayoutInflater.from(service);

            addDataBinding = ViewAddDataBinding.inflate(inflater);
            widgetSelectBinding = ViewWidgetSelectBinding.inflate(inflater);

            viewClickPosition = new ImageView(service);
            viewClickPosition.setImageResource(R.drawable.p);

            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            int width = Math.min(metrics.heightPixels, metrics.widthPixels);
            int height = Math.max(metrics.heightPixels, metrics.widthPixels);

            aParams = new WindowManager.LayoutParams();
            aParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            aParams.format = PixelFormat.TRANSPARENT;
            aParams.gravity = Gravity.START | Gravity.TOP;
            aParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
            aParams.width = width;
            aParams.height = height / 5;
            aParams.x = (metrics.widthPixels - aParams.width) / 2;
            aParams.y = metrics.heightPixels - aParams.height;
            aParams.alpha = 0.9f;

            bParams = new WindowManager.LayoutParams();
            bParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            bParams.format = PixelFormat.TRANSPARENT;
            bParams.gravity = Gravity.START | Gravity.TOP;
            bParams.width = metrics.widthPixels;
            bParams.height = metrics.heightPixels;
            bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            bParams.alpha = 0f;

            cParams = new WindowManager.LayoutParams();
            cParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            cParams.format = PixelFormat.TRANSPARENT;
            cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            cParams.gravity = Gravity.START | Gravity.TOP;
            cParams.width = cParams.height = width / 4;
            cParams.x = (metrics.widthPixels - cParams.width) / 2;
            cParams.y = (metrics.heightPixels - cParams.height) / 2;
            cParams.alpha = 0f;

            addDataBinding.getRoot().setOnTouchListener(new View.OnTouchListener() {
                int startRowX = 0, startRowY = 0, startLpX = 0, startLpY = 0;
                ScheduledFuture<?> future = executorService.schedule(new Runnable() {
                    @Override
                    public void run() {
                    }
                }, 0, TimeUnit.MILLISECONDS);

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startRowX = Math.round(event.getRawX());
                            startRowY = Math.round(event.getRawY());
                            startLpX = aParams.x;
                            startLpY = aParams.y;
                            future = executorService.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (Math.abs(aParams.x - startLpX) < 5 && Math.abs(aParams.y - startLpY) < 5) {
                                        Matcher matcher = Pattern.compile("(\\w|\\.)+").matcher(addDataBinding.pacName.getText().toString());
                                        if (matcher.find()) {
                                            MyApplication.appDescribe = appDescribeMap.get(matcher.group());
                                            if (MyApplication.appDescribe != null) {
                                                Intent intent = new Intent(service, EditDataActivity.class);
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                                service.startActivity(intent);
                                            }
                                        }
                                    }
                                }
                            }, 800, TimeUnit.MILLISECONDS);
                            break;
                        case MotionEvent.ACTION_MOVE:
                            aParams.x = startLpX + (Math.round(event.getRawX()) - startRowX);
                            aParams.y = startLpY + (Math.round(event.getRawY()) - startRowY);
                            windowManager.updateViewLayout(addDataBinding.getRoot(), aParams);
                            break;
                        case MotionEvent.ACTION_UP:
                            DisplayMetrics metrics = new DisplayMetrics();
                            windowManager.getDefaultDisplay().getRealMetrics(metrics);
                            aParams.x = Math.max(aParams.x, 0);
                            aParams.x = Math.min(aParams.x, metrics.widthPixels - aParams.width);
                            aParams.y = Math.max(aParams.y, 0);
                            aParams.y = Math.min(aParams.y, metrics.heightPixels - aParams.height);
                            windowManager.updateViewLayout(addDataBinding.getRoot(), aParams);
                            future.cancel(false);
                            break;
                    }
                    return true;
                }
            });
            viewClickPosition.setOnTouchListener(new View.OnTouchListener() {
                int startRowX = 0, startRowY = 0, startLpX = 0, startLpY = 0;
                final int width = cParams.width / 2;
                final int height = cParams.height / 2;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            addDataBinding.saveAim.setEnabled(true);
                            cParams.alpha = 0.9f;
                            windowManager.updateViewLayout(viewClickPosition, cParams);
                            startRowX = Math.round(event.getRawX());
                            startRowY = Math.round(event.getRawY());
                            startLpX = cParams.x;
                            startLpY = cParams.y;
                            break;
                        case MotionEvent.ACTION_MOVE:
                            cParams.x = startLpX + (Math.round(event.getRawX()) - startRowX);
                            cParams.y = startLpY + (Math.round(event.getRawY()) - startRowY);
                            windowManager.updateViewLayout(viewClickPosition, cParams);
                            coordinateSelect.appPackage = currentPackage;
                            coordinateSelect.appActivity = currentActivity;
                            coordinateSelect.xPosition = cParams.x + width;
                            coordinateSelect.yPosition = cParams.y + height;
                            addDataBinding.pacName.setText(coordinateSelect.appPackage);
                            addDataBinding.actName.setText(coordinateSelect.appActivity);
                            addDataBinding.xy.setText("X轴：" + String.format("%-4d", coordinateSelect.xPosition) + "    " + "Y轴：" + String.format("%-4d", coordinateSelect.yPosition));
                            break;
                        case MotionEvent.ACTION_UP:
                            cParams.alpha = 0.5f;
                            windowManager.updateViewLayout(viewClickPosition, cParams);
                            break;
                    }
                    return true;
                }
            });
            addDataBinding.switchWid.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Button button = (Button) v;
                    if (bParams.alpha == 0) {
                        AccessibilityNodeInfo root = service.getRootInActiveWindow();
                        if (root == null) return;
                        widgetSelect.appPackage = currentPackage;
                        widgetSelect.appActivity = currentActivity;
                        widgetSelectBinding.frame.removeAllViews();
                        ArrayList<AccessibilityNodeInfo> roots = new ArrayList<>();
                        roots.add(root);
                        ArrayList<AccessibilityNodeInfo> nodeList = new ArrayList<>();
                        findAllNode(roots, nodeList);
                        nodeList.sort(new Comparator<AccessibilityNodeInfo>() {
                            @Override
                            public int compare(AccessibilityNodeInfo a, AccessibilityNodeInfo b) {
                                Rect rectA = new Rect();
                                Rect rectB = new Rect();
                                a.getBoundsInScreen(rectA);
                                b.getBoundsInScreen(rectB);
                                return rectB.width() * rectB.height() - rectA.width() * rectA.height();
                            }
                        });
                        for (final AccessibilityNodeInfo e : nodeList) {
                            final Rect temRect = new Rect();
                            e.getBoundsInScreen(temRect);
                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(temRect.width(), temRect.height());
                            params.leftMargin = temRect.left;
                            params.topMargin = temRect.top;
                            final ImageView img = new ImageView(service);
                            img.setBackgroundResource(R.drawable.node);
                            img.setFocusableInTouchMode(true);
                            img.setFocusable(true);
                            img.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    v.requestFocus();
                                }
                            });
                            img.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                                @Override
                                public void onFocusChange(View v, boolean hasFocus) {
                                    if (hasFocus) {
                                        widgetSelect.widgetRect = temRect;
                                        widgetSelect.widgetClickable = e.isClickable();
                                        CharSequence cId = e.getViewIdResourceName();
                                        widgetSelect.widgetId = cId == null ? "" : cId.toString();
                                        CharSequence cDesc = e.getContentDescription();
                                        widgetSelect.widgetDescribe = cDesc == null ? "" : cDesc.toString();
                                        CharSequence cText = e.getText();
                                        widgetSelect.widgetText = cText == null ? "" : cText.toString();
                                        addDataBinding.saveWid.setEnabled(true);
                                        addDataBinding.pacName.setText(widgetSelect.appPackage);
                                        addDataBinding.actName.setText(widgetSelect.appActivity);
                                        addDataBinding.widget.setText("click:" + (e.isClickable() ? "true" : "false") + " " + "bonus:" + temRect.toShortString() + " " + "id:" + (cId == null ? "null" : cId.toString().substring(cId.toString().indexOf("id/") + 3)) + " " + "desc:" + (cDesc == null ? "null" : cDesc.toString()) + " " + "text:" + (cText == null ? "null" : cText.toString()));
                                        v.setBackgroundResource(R.drawable.node_focus);
                                    } else {
                                        v.setBackgroundResource(R.drawable.node);
                                    }
                                }
                            });
                            widgetSelectBinding.frame.addView(img, params);
                        }
                        bParams.alpha = 0.5f;
                        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(widgetSelectBinding.getRoot(), bParams);
                        addDataBinding.pacName.setText(widgetSelect.appPackage);
                        addDataBinding.actName.setText(widgetSelect.appActivity);
                        button.setText("隐藏布局");
                    } else {
                        bParams.alpha = 0f;
                        bParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        windowManager.updateViewLayout(widgetSelectBinding.getRoot(), bParams);
                        addDataBinding.saveWid.setEnabled(false);
                        button.setText("显示布局");
                    }
                }
            });
            addDataBinding.switchAim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Button button = (Button) v;
                    if (cParams.alpha == 0) {
                        coordinateSelect.appPackage = currentPackage;
                        coordinateSelect.appActivity = currentActivity;
                        cParams.alpha = 0.5f;
                        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                        windowManager.updateViewLayout(viewClickPosition, cParams);
                        addDataBinding.pacName.setText(coordinateSelect.appPackage);
                        addDataBinding.actName.setText(coordinateSelect.appActivity);
                        button.setText("隐藏准心");
                    } else {
                        cParams.alpha = 0f;
                        cParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                        windowManager.updateViewLayout(viewClickPosition, cParams);
                        addDataBinding.saveAim.setEnabled(false);
                        button.setText("显示准心");
                    }
                }
            });
            addDataBinding.saveWid.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Widget temWidget = new Widget(widgetSelect);
                    temWidget.createTime = System.currentTimeMillis();
                    dataDao.insertWidget(temWidget);
                    AppDescribe temAppDescribe = appDescribeMap.get(temWidget.appPackage);
                    if (temAppDescribe != null) {
                        temAppDescribe.getWidgetSetMapFromDatabase(dataDao);
                    }
                    addDataBinding.saveWid.setEnabled(false);
                    addDataBinding.pacName.setText(widgetSelect.appPackage + " (以下控件数据已保存)");
                }
            });
            addDataBinding.saveAim.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Coordinate temCoordinate = new Coordinate(coordinateSelect);
                    temCoordinate.createTime = System.currentTimeMillis();
                    dataDao.insertCoordinate(temCoordinate);
                    AppDescribe temAppDescribe = appDescribeMap.get(temCoordinate.appPackage);
                    if (temAppDescribe != null) {
                        temAppDescribe.getCoordinateMapFromDatabase(dataDao);
                    }
                    addDataBinding.saveAim.setEnabled(false);
                    addDataBinding.pacName.setText(coordinateSelect.appPackage + " (以下坐标数据已保存)");
                }
            });
            addDataBinding.quit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    windowManager.removeViewImmediate(widgetSelectBinding.getRoot());
                    windowManager.removeViewImmediate(addDataBinding.getRoot());
                    windowManager.removeViewImmediate(viewClickPosition);
                    widgetSelectBinding = null;
                    addDataBinding = null;
                    viewClickPosition = null;
                    aParams = null;
                    bParams = null;
                    cParams = null;
                }
            });
            windowManager.addView(widgetSelectBinding.getRoot(), bParams);
            windowManager.addView(addDataBinding.getRoot(), aParams);
            windowManager.addView(viewClickPosition, cParams);
        } catch (Throwable e) {
            toastErr(e);
            e.printStackTrace();
        }
    }

    private void toastErr(Throwable throwable) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(service, throwable.toString(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
