package com.jzs.evelyn.teststereocamera;


import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.EGLConfig;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

public class GLRootView  extends GLSurfaceView
					implements GLSurfaceView.Renderer{
	public static final String TAG = "Jzs.GLRootView";
	
	public GLRootView(Context context) {
        this(context, null);
    }

    public GLRootView(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        setEGLContextClientVersion(2);     // select GLES 2.0
        setRenderer(this);
    }
    
    @Override
    public void onSurfaceChanged(GL10 gl1, int width, int height) {
        Log.i(TAG, "onSurfaceChanged: " + width + "x" + height
                + ", gl10: " + gl1.toString());
        
    }
    
    @Override
    public void onSurfaceCreated(GL10 gl1, EGLConfig config) {
    	Log.d(TAG, "onSurfaceCreated: ");
    }
    
    @Override
    public void onDrawFrame(GL10 unused) {
        Log.d(TAG, "onDrawFrame tex=");
        
    }
}
