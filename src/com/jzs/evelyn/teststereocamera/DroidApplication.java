package com.jzs.evelyn.teststereocamera;


import com.android.evelyn.stereo.devjni.NativeHelper;
import com.android.evelyn.lycore.common.utils.logger.Logger;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;


public class DroidApplication extends Application{
	private final static String TAG = "Application";
	
	@Override
	public void onCreate() {
		super.onCreate();
		Logger logger = Logger.getLogger(TAG, "StereoWorld.");
		//logger.setTranceLevel(Logger.DEBUG_LEVEL);//.WARN_LEVEL);

		//VMRuntime.getRuntime().setMinimumHeapSize(30 * 1024 * 1024);

		try{
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			logger.info(">>>>>>>onCreate(), version:" + info.versionName);
		} catch (Exception e) {
		}
		 
		NativeHelper helper = NativeHelper.InitNativeHelper(this);
		logger.info(">>>>>>>onCreate(), native version:" + helper.utilsNative().getNativeVersionString());
	}
	
	@Override
	public void onTerminate() {
		// TODO Auto-generated method stub
		NativeHelper.utilsNative().terminateApplication();
		
		super.onTerminate();
		Logger.e(TAG, "<<<<<<<<onTerminate()");
		//super.unregisterReceiver(mBatteryInfoReceiver);
	}

	private BroadcastReceiver mBatteryInfoReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			//batteryLevel = intent.getIntExtra("level", 0);
		}
	};
}
