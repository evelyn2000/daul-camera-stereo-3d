package com.jzs.evelyn.teststereocamera;

import java.io.IOException;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

public class TextureViewActivity extends Activity implements TextureView.SurfaceTextureListener{
	private final static String TAG = "TextureViewActivity";
    //private Camera mCamera;
    //private TextureView mTextureViewLeft;
    //private TextureView mTextureViewRight;
    
    private final static int TEXTURE_LEFT = 0;
    private final static int TEXTURE_RIGHT = 1;
    
    private TextureView[] mTextureViews = new TextureView[2];
    private Camera[] mCameras = new Camera[2];
    private Camera.Parameters 	mParameters;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_texture);
        mTextureViews[TEXTURE_LEFT] = (TextureView)findViewById(R.id.textureViewLeft);
        mTextureViews[TEXTURE_RIGHT] = (TextureView)findViewById(R.id.textureViewRight);
        
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera camera = Camera.open(CameraInfo.CAMERA_FACING_BACK);
        if(camera != null){
        	mParameters = camera.getParameters();
        	if(mParameters != null){
        		Log.i(TAG, "onCreate(0), "+mParameters);
        		if(true){
        			mParameters.setPreviewSize(1280, 720);
	            	//mParameters.setPictureSize(640, 480);
	            	//mParameters.setPreviewFpsRange(10, 15);
	            	//mParameters.setPreviewFrameRate(15);
        		} else {
	            	mParameters.setPreviewSize(480, 320);
	            	mParameters.setPictureSize(640, 480);
        		}
            	Log.v(TAG, "onCreate(1), "+mParameters);
        	} else {
        		Log.e(TAG, "onCreate(2), get Parameters fail");
        	}
        	camera.release();
        } else {
        	Log.e(TAG, "onCreate(3), open camera fail");
        }
        
        if(mTextureViews[TEXTURE_LEFT] != null && numberOfCameras > 0){
        	mTextureViews[TEXTURE_LEFT].setSurfaceTextureListener(this);
        }
        
        if(mTextureViews[TEXTURE_RIGHT] != null){
        	if(numberOfCameras > 1)
        		mTextureViews[TEXTURE_RIGHT].setSurfaceTextureListener(this);
        	else
        		mTextureViews[TEXTURE_RIGHT].setVisibility(View.GONE);
        }
        
        
        //mTextureView = new TextureView(this);
        //mTextureView.setSurfaceTextureListener(this);

        //setContentView(mTextureView);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
    	
    	int id = TEXTURE_LEFT;
    	int cameraid = CameraInfo.CAMERA_FACING_BACK;
    	if(mTextureViews[TEXTURE_RIGHT].getSurfaceTexture() == surface){
    		id = TEXTURE_RIGHT;
    		cameraid = CameraInfo.CAMERA_FACING_FRONT;
    	} 
    	
    	try{
	    	for(int i=0; i<3; i++){
		    	mCameras[id] = Camera.open(cameraid);
		    	if(mCameras[id] == null){
		    		Thread.sleep(1000);
		    		continue;
		    	}
	    	}
    	} catch(Exception ex){
    		
    	}
    	
    	if(mCameras[id] == null){
    		Log.e(TAG, "onSurfaceTextureAvailable(), create "+(cameraid == CameraInfo.CAMERA_FACING_BACK ? "back" : "front")+" camera fail.");
    		return;
    	}
    	
    	int newWidth = 1;
        int newHeight = 1;
        if(mParameters == null)
        	mParameters = mCameras[id].getParameters();
        else
        	mCameras[id].setParameters(mParameters);
        
        Log.i(TAG, "onSurfaceTextureAvailable(), camera: "+(cameraid == CameraInfo.CAMERA_FACING_BACK ? "back" : "front")+" mParameters:"+mParameters.flatten());
        // any case, we should get the preview size set to native
        Camera.Size previewSize = mParameters.getPreviewSize();
        if (previewSize != null) {
        	newWidth = previewSize.width;
        	newHeight = previewSize.height;
        }
        
        Log.i(TAG, "onSurfaceTextureAvailable(1), previewSize: "+previewSize.width+"X"+previewSize.height);
        
        mTextureViews[id].setLayoutParams(new FrameLayout.LayoutParams(
                previewSize.width, previewSize.height, Gravity.CENTER));

        try {
        	mCameras[id].setPreviewTexture(surface);
        } catch (IOException t) {
        }

        mCameras[id].startPreview();

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, the Camera does all the work for us
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
    	int id = TEXTURE_LEFT;
    	if(mTextureViews[TEXTURE_RIGHT].getSurfaceTexture() == surface){
    		id = TEXTURE_RIGHT;
    	} 
    	
    	if(mCameras[id] != null){
	        mCameras[id].stopPreview();
	        mCameras[id].release();
    	}
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Update your view here!
    }

    final Object mScreenshotLock = new Object();
    Handler mHandler = new Handler();
    ServiceConnection mScreenshotConnection = null;

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                	TextureViewActivity.this.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };
    
    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, android.os.IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(mHandler.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                    	TextureViewActivity.this.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        mHandler.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;
//                        if (mStatusBar != null && mStatusBar.isVisibleLw())
//                            msg.arg1 = 1;
//                        if (mNavigationBar != null && mNavigationBar.isVisibleLw())
//                            msg.arg2 = 1;
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {}
            };
            if (bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                mHandler.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }
}
