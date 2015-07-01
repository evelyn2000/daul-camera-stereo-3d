package com.jzs.evelyn.teststereocamera;

import java.io.IOException;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;

public class CameraImpl {
	private final int mCameraId;
	private Camera mCamera;
	private boolean mIsPreviewing;
	
	public CameraImpl(int cameraid){
		mCameraId = cameraid;
	}
	
	public Camera open(){		
		if(mCamera == null)
			mCamera = Camera.open(mCameraId);
		
		return mCamera;
	}
	
	public Camera.Parameters getParameters(){
		if(mCamera != null) 
			return mCamera.getParameters();
		
		return null;
	}
	
	public void setParameters(Camera.Parameters params){
		if(mCamera != null) 
			mCamera.setParameters(params);
	}
	
	public void startPreview(SurfaceTexture surface){
		
		open();
		
		try {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
            mIsPreviewing = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	public void release(){
		if(mCamera != null){
			if(mIsPreviewing){
				mCamera.stopPreview();
				mIsPreviewing = false;
			}
			mCamera.release();
			mCamera = null;
		}
	}
}
