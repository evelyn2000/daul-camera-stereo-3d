package com.jzs.evelyn.teststereocamera;

import java.io.IOException;

import com.jzs.evelyn.teststereocamera.util.SystemUiHider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class MainActivity extends Activity implements SurfaceTexture.OnFrameAvailableListener {
// 
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//
//        setContentView(R.layout.activity_fullscreen);
//
//        
//    }
	
	private Camera mCamera;
    private MyGLSurfaceView glSurfaceView;
    private SurfaceTexture surface;
    MyGL20Renderer renderer;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        glSurfaceView = new MyGLSurfaceView(this);
        renderer = glSurfaceView.getRenderer();
        setContentView(glSurfaceView);
    }

    public void startCamera(int texture)
    {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);
        renderer.setSurface(surface);

        mCamera = Camera.open();

        try
        {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
        }
        catch (IOException ioe)
        {
            Log.w("MainActivity","CAM LAUNCH FAILED");
        }
    }

    public void onFrameAvailable(SurfaceTexture surfaceTexture)
    {
        glSurfaceView.requestRender();
    }

    @Override
    public void onPause()
    {
        mCamera.stopPreview();
        mCamera.release();
        System.exit(0);
    }
    
//    @Override
//    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
//        mCamera = Camera.open();
//
//        Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
//        mTextureView.setLayoutParams(new FrameLayout.LayoutParams(
//                previewSize.width, previewSize.height, Gravity.CENTER));
//
//        try {
//            mCamera.setPreviewTexture(surface);
//        } catch (IOException t) {
//        }
//
//        mCamera.startPreview();
//
//    }
//
//    @Override
//    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
//        // Ignored, the Camera does all the work for us
//    }
//
//    @Override
//    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
//        mCamera.stopPreview();
//        mCamera.release();
//        return true;
//    }
//
//    @Override
//    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//        // Update your view here!
//    }

}
