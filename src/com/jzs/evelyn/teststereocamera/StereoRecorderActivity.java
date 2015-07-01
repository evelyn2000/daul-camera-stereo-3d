package com.jzs.evelyn.teststereocamera;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.android.evelyn.lycore.common.utils.logger.Logger;
import com.android.evelyn.stereo.devjni.NativeHelper;
import com.android.evelyn.stereo.devjni.NativeSize;
import com.android.evelyn.stereo.devjni.StereoCameraNativeHelper;
import com.android.evelyn.stereo.devjni.StereoNativeHelper;
import com.jzs.evelyn.teststereocamera.gles.GLUtil;
import com.jzs.evelyn.teststereocamera.gles.TextureMovieEncoder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class StereoRecorderActivity extends Activity {
	
	public static final String TAG = "Jzs.StereoRecorderActivity";
	private static final boolean VERBOSE = false;
	
	private final static boolean ENABLE_DOUBLE_CAMERA = true; 
	
	
	private GLSurfaceView mGLView;
	private int mCameraPreviewWidth, mCameraPreviewHeight;
	private CameraHandler mCameraHandler;
	private PowerManager.WakeLock mWakeLock;
	
	private CameraSurfaceRenderer mRenderer;
	
	private Camera[] mCameras = new Camera[2];
    private Camera.Parameters mParameters;
    
    private static HandlerThread           sFrameListener = new HandlerThread("STFrameListener");
    private Object                         mRenderLock = new Object();

    private int	mLeftPreviewTexId = -12345;
    private int	mRightPreviewTexId = -12345;
    
    private SurfaceTexture mLeftSurfaceTexture;
    private SurfaceTexture mRightSurfaceTexture;
    
    private float[] mMVPMatrix = new float[16];
    
    private float[] mLeftTransformMatrix                  = new float[16];
    private float[] mRightTransformMatrix                  = new float[16];
    
    private int mSurfaceUpdated = 0;
    private final static int LEFT_SURFACE_UPDATED_FLAG = 1;
    private final static int RIGHT_SURFACE_UPDATED_FLAG = 2;
    
    private StereoCameraNativeHelper mStereoHelperNative;
    private RadioGroup mRadioGroup;
    private int mFirstInitPreivewMode = StereoNativeHelper.STEREO_DISPLAY_3D;
    private int mCurrentPreviewMode = StereoNativeHelper.STEREO_DISPLAY_3D;
    private int mCurrentPreview2dLeftOrRight = 0;
    
    private String mLastSavedFile = "";
    
    private static final int RECORDING_OFF = 0;
	private static final int RECORDING_ON = 1;
	private static final int RECORDING_RESUMED = 2;
    private TextureMovieEncoder mVideoEncoder;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_preview);
		
		mCameraHandler = new CameraHandler(this);
		
		mRadioGroup = (RadioGroup) findViewById(R.id.radioGroup_PreviewMode);
		mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			             
             @Override
             public void onCheckedChanged(RadioGroup arg0, int arg1) {
                 // TODO Auto-generated method stub
                 int radioButtonId = arg0.getCheckedRadioButtonId();
                 if(radioButtonId == R.id.radio_vr){
                	 mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SURFACE_PREVIEW_MODE_CHANGED, 
                			 StereoNativeHelper.STEREO_DISPALY_SIDE_BY_SIDE_VR, 0));
                 } else if(radioButtonId == R.id.radio_sbs){
                	 mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SURFACE_PREVIEW_MODE_CHANGED, 
                			 StereoNativeHelper.STEREO_DISPALY_SIDE_BY_SIDE, 0));                	 
                 } else if(radioButtonId == R.id.radio_single_left){
                	 mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SURFACE_PREVIEW_MODE_CHANGED, 
                			 StereoNativeHelper.STEREO_DISPLAY_2D, Camera.CameraInfo.CAMERA_FACING_BACK));
                 } else if(radioButtonId == R.id.radio_single_right){
                	 mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SURFACE_PREVIEW_MODE_CHANGED, 
                			 StereoNativeHelper.STEREO_DISPLAY_2D, Camera.CameraInfo.CAMERA_FACING_FRONT));
                 } else {
                	 
                	 mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SURFACE_PREVIEW_MODE_CHANGED, 
                			 StereoNativeHelper.STEREO_DISPLAY_3D, 0));
                 }
             }
         });
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
 		mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "StereoRecorderActivity.wakelock");
 		
 		mStereoHelperNative = NativeHelper.cameraNative(this);
 		
 		mVideoEncoder = new TextureMovieEncoder(this);
 		
 		ToggleButton recVideo = (ToggleButton) findViewById(R.id.toggleRecording_button);
 		if(recVideo != null){
 			recVideo.setOnCheckedChangeListener(new ToggleButton.OnCheckedChangeListener() {
 				
 				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
 					mRenderer.changeRecordingState(isChecked);
 				}
 				
 			});
 		}
 		
 		View capBtn = findViewById(R.id.toggleCapture_button);
 		if(capBtn != null){
 			capBtn.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					v.setEnabled(false);
					mPictureBitmap = null;
					mTopPictureBitmap = null;
					if(mCameras[0] != null){
						mCameras[0].takePicture(null, null, jpegCallback);
					}
					
					if(mCameras[1] != null){
						mCameras[1].takePicture(null, null, jpegCallback);
					}
				}
			});
 		}
 		
 		View viewBtn = findViewById(R.id.view_button);
 		if(viewBtn != null){
 			viewBtn.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					if(!TextUtils.isEmpty(mLastSavedFile)){
						File file = new File(mLastSavedFile);
						if(file.exists()){
							try{
								Intent intent = new Intent(Intent.ACTION_VIEW);
								if(mLastSavedFile.endsWith(".mp4")){
									intent.setDataAndType(Uri.fromFile(file), "video/*");
								} else {
									intent.setDataAndType(Uri.fromFile(file), "image/*");
								}
								startActivity(intent);
							} catch(android.content.ActivityNotFoundException ex){
								
							}
						} else {
							Toast.makeText(StereoRecorderActivity.this, 
									String.format("Image:%s not exist.", file.getPath()), Toast.LENGTH_SHORT).show();
						}
					}
				}
			});
 		}
 		
 		mGLView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
        mGLView.setEGLContextClientVersion(2);     // select GLES 2.0
        mRenderer = new CameraSurfaceRenderer(this, mCameraHandler);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
	}
	
	@Override
    protected void onResume() {
        Log.d(TAG, "onResume -- acquiring camera");
        super.onResume();
        
	     // Lock screen
	    mWakeLock.acquire();
     		
        updateControls();
        final DisplayMetrics dm = getResources().getDisplayMetrics();
        
        openCamera(dm.widthPixels, dm.heightPixels);      // updates mCameraPreviewWidth/Height

        // Set the preview aspect ratio.
        //AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        //layout.setAspectRatio((double) mCameraPreviewWidth / mCameraPreviewHeight);

        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                mRenderer.setCameraPreviewSize(mCameraPreviewWidth, mCameraPreviewHeight);
            }
        });
        
        Log.d(TAG, "onResume complete: " + this);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause -- releasing camera");
        super.onPause();
        mRenderer.changeRecordingState(false);
        releaseCamera();
        
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRenderer.notifyPausing();
            }
        });
        mGLView.onPause();
        Log.d(TAG, "onPause complete");

        if (mWakeLock.isHeld()) mWakeLock.release();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        
        mStereoHelperNative.release();
        
        mCameraHandler.invalidateHandler();
    }
    
    private void openCamera(int desiredWidth, int desiredHeight) {
    	
    	Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        
//        if(mCameras[Camera.CameraInfo.CAMERA_FACING_BACK] != null || mCameras[Camera.CameraInfo.CAMERA_FACING_FRONT] != null){
//        	return;
//        }
        
        Log.i(TAG, "openCamera()==numCameras:"+numCameras+", size:"+desiredWidth+"x"+desiredHeight);
        if(numCameras > 1){
        	
        	openCamera(Camera.CameraInfo.CAMERA_FACING_BACK, desiredWidth, desiredHeight);
        	if(ENABLE_DOUBLE_CAMERA)
        		openCamera(Camera.CameraInfo.CAMERA_FACING_FRONT, desiredWidth, desiredHeight);
        	
        } else if(numCameras > 0){
        	for (int i = 0; i < numCameras; i++) {
                Camera.getCameraInfo(i, info);
                openCamera(info.facing, desiredWidth, desiredHeight);
            }
        }
        
        int[] fpsRange = new int[2];
        Camera.Size mCameraPreviewSize = mParameters.getPreviewSize();
        mParameters.getPreviewFpsRange(fpsRange);
        String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
        if (fpsRange[0] == fpsRange[1]) {
            previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
        } else {
            previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                    " - " + (fpsRange[1] / 1000.0) + "] fps";
        }
        
        previewFacts += ", "+mParameters.getPreviewFrameRate() + " fps";
        TextView text = (TextView) findViewById(R.id.cameraParams_text);
        text.setText(previewFacts);
        
        Log.i(TAG, ">>>>>>>SupportedPictureSizes:");
        for(Camera.Size s : mParameters.getSupportedPictureSizes()){
        	Log.i(TAG, s.width+"x"+s.height);
        }
        
        Log.i(TAG, ">>>>>>>SupportedVideoSizes:");
        for(Camera.Size s : mParameters.getSupportedVideoSizes()){
        	Log.v(TAG, ""+s.width+"x"+s.height);
        }

        mCameraPreviewWidth = mCameraPreviewSize.width;
        mCameraPreviewHeight = mCameraPreviewSize.height;
        
    }
    
    private void openCamera(int cameraid, int desiredWidth, int desiredHeight) {
    	
    	Log.d(TAG, "openCamera["+cameraid+"]"+", size:"+desiredWidth+"x"+desiredHeight);
    	
    	if (cameraid < 0 || cameraid > 1) {
            throw new RuntimeException("cameraid error, only be 0 or 1");
        }
    	
        if (mCameras[cameraid] != null) {
            throw new RuntimeException("camera already initialized");
        }

        mCameras[cameraid] = Camera.open(cameraid);
        
        if (mCameras[cameraid] == null) {
            throw new RuntimeException("Unable to open camera["+cameraid+"]");
        }
        
        Camera.Parameters parms = mParameters == null ? mCameras[cameraid].getParameters() : mParameters;
        
        if(mParameters == null){
        	
        	//mCameras[cameraid].setDisplayOrientation(getDisplayOrientation(0, cameraid));
        	
        	//if(ENABLE_DOUBLE_CAMERA)
        		//parms.setPreviewFpsRange(30, 60);//.setPreviewFrameRate(40);
            
            CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
            //parms.setPictureSize(4160, 3120);
            //parms.setPictureSize(4096, 3076);
            //parms.setPictureSize(3264, 2448);
            //parms.setPictureSize(2560, 1440);
            //parms.setPictureSize(1280, 720);
            
            parms.setPictureSize(desiredWidth, desiredHeight);
            

            // Give the camera a hint that we're recording video.  This can have a big
            // impact on frame rate.
            parms.setRecordingHint(true);
            
        	mParameters = parms;
        } 
        
        // leave the frame rate set to default
        mCameras[cameraid].setParameters(parms);
    }

    public static int getDisplayOrientation(int degrees, int cameraId) {
        // See android.hardware.Camera.setDisplayOrientation for
        // documentation.
    	Log.i("shenyuanqing","getDisplayOrientation");
    	Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
    	Log.i("shenyuanqing","orientation=" + info.orientation);
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
    	Log.i("shenyuanqing","result=" + result);
        return result;
    }
    
    private void releaseCamera() {
    	
    	Camera.CameraInfo info = new Camera.CameraInfo();
    	int numCameras = Camera.getNumberOfCameras();
    	for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            releaseCamera(info.facing);
        }
    }
    
    private void releaseCamera(int cameraid) {
        if (mCameras[cameraid] != null) {
        	mCameras[cameraid].stopPreview();
        	mCameras[cameraid].release();
        	mCameras[cameraid] = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }
    
    /**
     * Updates the on-screen controls to reflect the current state of the app.
     */
    private void updateControls() {
//        Button toggleRelease = (Button) findViewById(R.id.toggleRecording_button);
//        int id = mRecordingEnabled ?
//                R.string.toggleRecordingOff : R.string.toggleRecordingOn;
//        toggleRelease.setText(id);

        //CheckBox cb = (CheckBox) findViewById(R.id.rebindHack_checkbox);
        //cb.setChecked(TextureRender.sWorkAroundContextProblem);
    }
    
    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    private void handleSetSurfaceTexture(SurfaceTexture st, int cameraid) {
    	if(st == null){
    		Log.e(TAG, "SurfaceTexture is null, id:"+cameraid);
    		return ;
    	}
    	Log.i(TAG, "handleSetSurfaceTexture cameraid:"+cameraid+", st:"+st+", mCameras:"+mCameras[cameraid]);
    	if(mCameras[cameraid] == null){
    		Log.e(TAG, "handleSetSurfaceTexture cameraid:"+cameraid+", mCameras is null");
    		openCamera(cameraid, mCameraPreviewWidth, mCameraPreviewHeight);
    		if(mCameras[cameraid] == null){
    			Log.e(TAG, "handleSetSurfaceTexture open again fail");
    			return;
    		}
    	}
        //st.setOnFrameAvailableListener(this);
        try {
        	mCameras[cameraid].setPreviewTexture(st);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCameras[cameraid].startPreview();
    }
    
    private void handleStopSurfaceTexture(int cameraid) {
    	if(mCameras[cameraid] == null){
    		Log.e(TAG, "handleSetSurfaceTexture cameraid:"+cameraid+", mCameras is null");
    		openCamera(cameraid, mCameraPreviewWidth, mCameraPreviewHeight);
    		if(mCameras[cameraid] == null){
    			Log.e(TAG, "handleSetSurfaceTexture open again fail");
    			return;
    		}
    	}
        //st.setOnFrameAvailableListener(this);
        try {
        	mCameras[cameraid].setPreviewTexture(null);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCameras[cameraid].stopPreview();
    }
    
    private Camera.PictureCallback rawCallback = new Camera.PictureCallback(){  
        @Override  
        public void onPictureTaken(byte[] data, Camera camera) {  
            Log.i(TAG,"rawCallback, camera:"+camera);
            // TODO Auto-generated method stub  
            
        }
    };  
    private Bitmap mPictureBitmap;
	private Bitmap mTopPictureBitmap;
	
    private Camera.PictureCallback jpegCallback = new Camera.PictureCallback(){  
        @Override  
        public void onPictureTaken(byte[] data, Camera camera) {  
            Log.i(TAG,"jpegCallback, camera:"+camera);
            // TODO Auto-generated method stub  
            Camera.Parameters ps = camera.getParameters(); 
            Camera.Size size = ps.getPictureSize();
            boolean isLeftCamera = (camera == mCameras[0] ? true : false);
            
            Log.i(TAG,"jpegCallback, isLeftCamera:"+isLeftCamera+
            		", size:"+size.toString()+
            		", Format:"+ps.getPictureFormat()+
            		", HorizontalViewAngle:"+ps.getHorizontalViewAngle());
            
            if(mPictureBitmap == null){
            	mPictureBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } else {
            	mTopPictureBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            }
            
            data = null;
            
            if(mPictureBitmap != null && mTopPictureBitmap != null){
            	final int width = mPictureBitmap.getWidth() * 2;
        		final int height = mPictureBitmap.getHeight();
        		
            	mGLView.queueEvent(new Runnable(){
        			@Override
        			public void run() {
        				ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        				buf.order(ByteOrder.LITTLE_ENDIAN);
        				
        				int[] textureId = new int[2];
        				
        				final StereoCameraNativeHelper nativeHelper = null;//NativeHelper.cameraNative(mContext, false);
        				GLES20.glGenTextures(2, textureId, 0);
        		    	GLUtil.checkGlError("glGenTextures");
        		    	if (textureId[0] == GLES20.GL_FALSE || textureId[1] == GLES20.GL_FALSE){
        		    		Log.e(TAG, "doCapture() glGenTextures failed");
        		    		mPictureBitmap.recycle();
        		    		
        		    		if(mTopPictureBitmap != null)
        		    			mTopPictureBitmap.recycle();

        		    		mCameraHandler.sendEmptyMessage(mCameraHandler.MSG_RESTART_PREVIEW);
        		    		
        		    		mPictureBitmap = null;
        		    		mTopPictureBitmap = null;
        					return;//throw new RuntimeException("Error loading texture");
        		    	}

        				createSimpleBmpTexture2D(mPictureBitmap, textureId[0]);
        				mPictureBitmap.recycle();
        				
        				NativeSize sizeResult = null;
        				createSimpleBmpTexture2D(mTopPictureBitmap, textureId[1]);
        				mTopPictureBitmap.recycle();
  
    					sizeResult = mStereoHelperNative.drawTextureAndReadPixels(StereoCameraNativeHelper.STEREO_DISPALY_SIDE_BY_SIDE, 
    							textureId[0], null, textureId[1], null, width, height, buf, true);
    					
    					buf.rewind();
    					
    					GLES20.glDeleteTextures(2, textureId, 0);
    					long startTime = System.currentTimeMillis();
    					final Bitmap bmp;
    					if(sizeResult != null){
    						bmp = Bitmap.createBitmap(sizeResult.width, sizeResult.height, Bitmap.Config.ARGB_8888);
    						bmp.copyPixelsFromBuffer(buf);
    						Log.i(TAG, "createBitmap(1), "+(System.currentTimeMillis() - startTime)+", count:"+bmp.getByteCount()
    								+", bmp size:"+sizeResult.width+", "+sizeResult.height);
    					} else {
    						bmp = null;
    						Log.w(TAG, "createBitmap(1), error, "+(System.currentTimeMillis() - startTime));
    					}
    					
    					new SavingTask().execute(bmp);
    					
    					mPictureBitmap = null;
    		    		mTopPictureBitmap = null;
        			}
            	});
            }
        }         
    };  
    
    private class SavingTask extends AsyncTask<Bitmap, Void, String> {
    	
    	@Override
        protected String doInBackground(Bitmap... params) {
    		Bitmap bmp = params[0];
    		if(bmp != null){
	    		try {
					File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), 
							"stereo-"+SystemClock.elapsedRealtime() + ".jps"); 
					
					if (file.exists()) { 
						file.delete(); 
					} 
					
					FileOutputStream out = new FileOutputStream(file); 
					if(out != null){
						bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
						out.flush();
						out.close();
					}
					
					Log.i(TAG, "Save file:"+file.getPath());
					return file.getPath();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    		}
    		return "";
    	}
    	
    	@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			
			mCameraHandler.sendEmptyMessage(mCameraHandler.MSG_RESTART_PREVIEW);
			View capBtn = findViewById(R.id.toggleCapture_button);
			capBtn.setEnabled(true);
			mLastSavedFile = result;
    	}
    }
    private Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback(){  
  
        @Override  
        public void onShutter() {  
            Log.i(TAG,"shutterCallback");  
            // TODO Auto-generated method stub  
        }  
    };
  
    private String save(byte[] data, int cameraid){ 
        String path = "";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), System.currentTimeMillis()+".jpg");  
//        try{  
//            if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){  
//                //判断SD卡上是否有足够的空间  
//                                        String storage = Environment.getExternalStorageDirectory().toString();  
//                                  StatFs fs = new StatFs(storage);  
//                                  long available = fs.getAvailableBlocks()*fs.getBlockSize();  
//                                  if(available<data.length){  
//                                      //空间不足直接返回空  
//                                             return null;  
//                                   }  
//                                   File file = new File(path);  
//                                   if(!file.exists())  
//                                     //创建文件  
//                                              file.createNewFile();  
//                                   FileOutputStream fos = new FileOutputStream(file);  
//                                   fos.write(data);  
//                                   fos.close();  
//            }  
//        }catch(Exception e){  
//            e.printStackTrace();  
//            return null;  
//        }  
        return path;  
    }  

	
	private class CameraHandler extends Handler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;
        public static final int MSG_RESTART_PREVIEW = 5;
        public static final int MSG_SURFACE_PREVIEW_MODE_CHANGED = 9;

        // Weak reference to the Activity; only access this from the UI thread.
        private WeakReference<Activity> mWeakActivity;

        public CameraHandler(Activity activity) {
            mWeakActivity = new WeakReference<Activity>(activity);
            
            Matrix.setIdentityM(mMVPMatrix, 0);
        }

        /**
         * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
         * attempts to access a stale Activity through a handler are caught.
         */
        public void invalidateHandler() {
            mWeakActivity.clear();
        }

        @Override  // runs on UI thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            //Log.d(TAG, "CameraHandler [" + this + "]: what=" + what);
            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:{
                	StereoRecorderActivity activity = (StereoRecorderActivity)mWeakActivity.get();
                	if (activity != null) 
                		activity.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj, inputMessage.arg1);
                	}
                    break;
                case MSG_SURFACE_PREVIEW_MODE_CHANGED:{	                	         
	                	if(mStereoHelperNative.setStereoDisplayMode(inputMessage.arg1)){
	                		mCurrentPreviewMode = inputMessage.arg1;
	                	}
		            }
                	break;
                case MSG_RESTART_PREVIEW:
                	if(mCameras[0] != null){
						mCameras[0].startPreview();
					}
					
					if(mCameras[1] != null){
						mCameras[1].startPreview();
					}
                	break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }
	
    private final static int STEREO_RENDER_SURFACE_UPDATED_FLAG = (LEFT_SURFACE_UPDATED_FLAG | RIGHT_SURFACE_UPDATED_FLAG);
	private void requestRender(int flag){
		synchronized (mRenderLock) {
        	mSurfaceUpdated |= flag;
        	
        	if(mCurrentPreviewMode > StereoNativeHelper.STEREO_DISPLAY_2D){
        		if(mSurfaceUpdated == STEREO_RENDER_SURFACE_UPDATED_FLAG){
        			mGLView.requestRender();
        		}
        	} else {
        		mGLView.requestRender();
        	}
        }
	}
	
	private static final int INTERVALS = 300;
    private SurfaceTexture.OnFrameAvailableListener mLeftCamFrameAvailableListener = 
            new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        	if(mLeftSurfaceTexture == null)
        		return;
        	
        	requestRender(LEFT_SURFACE_UPDATED_FLAG);
        }
    };

    private SurfaceTexture.OnFrameAvailableListener mRightCamFrameAvailableListener = 
            new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        	if(mRightSurfaceTexture == null)
        		return;
        	
        	requestRender(RIGHT_SURFACE_UPDATED_FLAG);
        }
    };
	
	private class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
        private static final boolean VERBOSE = false;

        private Context mContext;
        private CameraHandler mCameraHandler;
        
        
        
        
        //private final float[] mSTMatrix = new float[16];
        //private int mTextureId;
        
        private int mRecordingStatus;
        private boolean mRecordingEnabled;
        private int mFrameCount;
        private File mOutPutDir;

        /**
         * Constructs CameraSurfaceRenderer.
         * <p>
         * @param cameraHandler Handler for communicating with UI thread
         * @param movieEncoder video encoder object
         * @param outputFile output file for encoded video; forwarded to movieEncoder
         */
        public CameraSurfaceRenderer(Context context, CameraHandler cameraHandler) {
        	mContext = context;
            mCameraHandler = cameraHandler;
            mRecordingStatus = -1;
            mRecordingEnabled = false;
            mFrameCount = -1;
            mOutPutDir = new File(
    				Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Record");
        }

        /**
         * Notifies the renderer thread that the activity is pausing.
         * <p>
         * For best results, call this *after* disabling Camera preview.
         */
        public void notifyPausing() {


        }
        
        /**
		 * Notifies the renderer that we want to stop or start recording.
		 */
		public void changeRecordingState(boolean isRecording) {
			Log.i(TAG, "changeRecordingState: was " + mRecordingEnabled
					+ " now " + isRecording);
			mRecordingEnabled = isRecording;
		}
        
        /**
         * Records the size of the incoming camera preview frames.
         * <p>
         * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
         * so we assume it could go either way.  (Fortunately they both run on the same thread,
         * so we at least know that they won't execute concurrently.)
         */
        public void setCameraPreviewSize(int width, int height) {
            Log.d(TAG, "setCameraPreviewSize");
        }
         
        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            Log.e(TAG, "onSurfaceCreated32");
            //mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_UPDATE_PIP_TEXTURES));
            mRecordingEnabled = mVideoEncoder.isRecording();
			if (mRecordingEnabled) {
				mRecordingStatus = RECORDING_RESUMED;
			} else {
				mRecordingStatus = RECORDING_OFF;
			}
			
			if (!sFrameListener.isAlive()) {
                sFrameListener.start();
            }
        	
        	if(mStereoHelperNative.initTexture(mCurrentPreviewMode) <= 0){
        		Log.e(TAG, "initCameraTexture failed...");
        		return;
        	}
        	
        	mLeftPreviewTexId = mStereoHelperNative.getLeftCameraTextureId();
        	            	
        	if(ENABLE_DOUBLE_CAMERA && Camera.getNumberOfCameras() > 1){
        		mRightPreviewTexId = mStereoHelperNative.getRightCameraTextureId();
            } else {
            	mRightPreviewTexId = 0;
            }
        	
        	if(false){
        		mStereoHelperNative.setRotation(StereoNativeHelper.ROTATION_0, StereoNativeHelper.ROTATION_270);
        	}
        	
        	Log.i(TAG, "mLeftPreviewTexId:"+mLeftPreviewTexId+", mRightPreviewTexId:"+mRightPreviewTexId);
        	
        	mLeftSurfaceTexture = new SurfaceTexture(mLeftPreviewTexId/*, sFrameListener.getLooper()*/);
        	//mLeftSurfaceTexture = new SurfaceTexture(mLeftPreviewTexId);
            mLeftSurfaceTexture.setOnFrameAvailableListener(mLeftCamFrameAvailableListener);

            // initialize top surface texture
            if(mRightPreviewTexId > 0){
                mRightSurfaceTexture = new SurfaceTexture(mRightPreviewTexId/*, sFrameListener.getLooper()*/);
                mRightSurfaceTexture.setOnFrameAvailableListener(mRightCamFrameAvailableListener);
            }
        	
            mCameraHandler.sendMessage(mCameraHandler.obtainMessage(mCameraHandler.MSG_SET_SURFACE_TEXTURE, CameraInfo.CAMERA_FACING_BACK, 0, mLeftSurfaceTexture));
            if(mRightSurfaceTexture != null)
            	mCameraHandler.sendMessage(mCameraHandler.obtainMessage(mCameraHandler.MSG_SET_SURFACE_TEXTURE, CameraInfo.CAMERA_FACING_FRONT, 0, mRightSurfaceTexture));
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
            //mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_SURFACE_SIZE_CHANGED, width, height));
            mStereoHelperNative.setWindowSize(width, height);
            mStereoHelperNative.setTextureSize(width, height);
            
            //GLES20.glViewport(0, 0, width, height); 
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            //Log.d(TAG, "onDrawFrame tex=33333");

            if (mRecordingEnabled) {
				switch (mRecordingStatus) {
				case RECORDING_OFF:
					Log.e(TAG, "START recording");
					// start recording
					File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), 
							"stereo-"+SystemClock.elapsedRealtime() + ".mp4"); 
					mVideoEncoder.startRecording(new TextureMovieEncoder.EncoderConfig(
							file.getPath(), 1920, 1080, 1000000, EGL14.eglGetCurrentContext(), true));
					mRecordingStatus = RECORDING_ON;
					break;
				case RECORDING_RESUMED:
					Log.d(TAG, "RESUME recording");
					mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
					mRecordingStatus = RECORDING_ON;
					break;
				case RECORDING_ON:
					// yay
					break;
				default:
					throw new RuntimeException("unknown status "+ mRecordingStatus);
				}
			} else {
				switch (mRecordingStatus) {
				case RECORDING_ON:
				case RECORDING_RESUMED:
					// stop recording
					mLastSavedFile = mVideoEncoder.getRecordingFile();
					Log.e(TAG, "STOP recording, "+mLastSavedFile);
					
					mVideoEncoder.stopRecording();
					mRecordingStatus = RECORDING_OFF;
					break;
				case RECORDING_OFF:
					// yay
					break;
				default:
					throw new RuntimeException("unknown status "
							+ mRecordingStatus);
				}
			}
            //mCameraHandler.sendEmptyMessage(CameraHandler.MSG_SURFACE_DRAW_FRAME);
            int updated = mSurfaceUpdated;
            long leftTimestamp = 0, rightTimestamp = 0;
            //synchronized (mRenderLock) {
            	if((mSurfaceUpdated & LEFT_SURFACE_UPDATED_FLAG) == LEFT_SURFACE_UPDATED_FLAG){
            		mLeftSurfaceTexture.updateTexImage();
    	            mLeftSurfaceTexture.getTransformMatrix(mLeftTransformMatrix);
    	            leftTimestamp = mLeftSurfaceTexture.getTimestamp();
    	            mSurfaceUpdated &= ~LEFT_SURFACE_UPDATED_FLAG;
            	}
            	
            	if((mSurfaceUpdated & RIGHT_SURFACE_UPDATED_FLAG) == RIGHT_SURFACE_UPDATED_FLAG){
            		mRightSurfaceTexture.updateTexImage();
    	            mRightSurfaceTexture.getTransformMatrix(mRightTransformMatrix);
    	            rightTimestamp = mRightSurfaceTexture.getTimestamp();
    	            mSurfaceUpdated &= ~RIGHT_SURFACE_UPDATED_FLAG;
            	}
            	
            	if(updated == mSurfaceUpdated){
            		return;
            	}
            //}
            
            if(updated == (LEFT_SURFACE_UPDATED_FLAG|RIGHT_SURFACE_UPDATED_FLAG)){
            	if(mFirstInitPreivewMode == StereoNativeHelper.STEREO_DISPLAY_3D){
            		mStereoHelperNative.enableHorizontalGrating();
            		mFirstInitPreivewMode = StereoNativeHelper.STEREO_DISPLAY_UNKNOWN;
            	}
            	
            	// Set the video encoder's texture name. We only need to do this
    			// once, but in the
    			// current implementation it has to happen after the video encoder
    			// is started, so
    			// we just do it here.
    			//
    			// TODO: be less lame.
    			mVideoEncoder.setTextureId(mLeftPreviewTexId, mRightPreviewTexId);

    			// Tell the video encoder thread that a new frame is available.
    			// This will be ignored if we're not actually recording.
    			mVideoEncoder.frameAvailable(mLeftTransformMatrix, mRightTransformMatrix, leftTimestamp, rightTimestamp);
    			
            	mStereoHelperNative.drawFrame(mLeftTransformMatrix, mRightTransformMatrix);
            } else {
                if((updated & LEFT_SURFACE_UPDATED_FLAG) == LEFT_SURFACE_UPDATED_FLAG){
                	mStereoHelperNative.drawFrame(mLeftTransformMatrix, null);
                }
                
                if((updated & RIGHT_SURFACE_UPDATED_FLAG) == RIGHT_SURFACE_UPDATED_FLAG){
                	mStereoHelperNative.drawFrame(null, mRightTransformMatrix);
                }
            }
            
			if ((mRecordingStatus == RECORDING_ON) && (++mFrameCount & 0x04) == 0) {
				drawBox();
			}
        }
        
        private void drawBox() {
			GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
			GLES20.glScissor(0, 0, 60, 60);
			GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
		}
    }
	
	protected final int createSimpleBmpTexture2D(Bitmap bmp, int id){
    	if(id <= 0){
			int[] textureId = new int[1];
	    	GLES20.glGenTextures(1, textureId, 0);
	    	GLUtil.checkGlError("glGenTextures");
			if (textureId[0] == GLES20.GL_FALSE)
				return 0;//throw new RuntimeException("Error loading texture");
			// bind the texture and set parameters
			id = textureId[0];
		}
    	Log.i(TAG, "createSimpleBmpTexture2D(), id:"+id);
    	//GLES20.glActiveTexture(GLES20.GL_TEXTURE0+2);
    	GLUtil.checkGlError("glActiveTexture");
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
		GLUtil.checkGlError("glBindTexture");
		GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
		GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
		GLUtil.checkGlError("texImage2D");
		return id;
    }
}
