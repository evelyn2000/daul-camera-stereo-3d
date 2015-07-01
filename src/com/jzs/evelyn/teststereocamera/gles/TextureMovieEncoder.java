/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jzs.evelyn.teststereocamera.gles;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;


import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import com.android.evelyn.lycore.common.utils.logger.Logger;
import com.android.evelyn.stereo.devjni.NativeFullFrameRect;
import com.android.evelyn.stereo.devjni.NativeHelper;
import com.android.evelyn.stereo.devjni.NativeTexture2dProgram;
import com.android.evelyn.stereo.devjni.StereoCameraNativeHelper;
import com.android.evelyn.stereo.devjni.StereoVideoNativeHelper;


/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 * <p>
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 * <p>
 * To use:
 * <ul>
 * <li>create TextureMovieEncoder object
 * <li>create an EncoderConfig
 * <li>call TextureMovieEncoder#startRecording() with the config
 * <li>call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 * <li>for each frame, after latching it with SurfaceTexture#updateTexImage(),
 *     call TextureMovieEncoder#frameAvailable().
 * </ul>
 *
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
public class TextureMovieEncoder implements Runnable {
    private static final String TAG = "TextureMovieEncoder";
    private static final boolean VERBOSE = false;

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_QUIT = 5;
    private static final int MSG_UPDATE_LABEL_TEXT = 6;
    private static final int MSG_CAPTURE_SCREEN = 7;
    private static final int MSG_PAUSE_RECORDING = 8;
    private static final int MSG_RESUME_RECORDING = 9;

    // ----- accessed exclusively by encoder thread -----
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;
    //private NativeFullFrameRect mFullScreen;
    private StereoCameraNativeHelper mStereoCameraNativeHelper;
    private int mLeftTextureId;
    private int mRightTextureId;
    private int mFrameNum;
    private VideoEncoderCore mVideoEncoder;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;
    private boolean mStoping;
    private boolean mRecPaused;
    private boolean mIsInited = false;
//    private LabelTexture mLabelTexture;
    private String mLastLabelString;
    private Context mContext;
    public TextureMovieEncoder(Context context){
    	mContext = context;
    	mStoping = false;
    }
    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     *       with reasonable defaults for those and bit rate.
     */
    public static class EncoderConfig {
        final String mOutputFile;
        final int mWidth;
        final int mHeight;
        final int mBitRate;
        final EGLContext mEglContext;
        final boolean isStereo;

        public EncoderConfig(String outputFile, int width, int height, int bitRate,
                EGLContext sharedEglContext, boolean isStereo) {
            mOutputFile = outputFile;
            mWidth = width;
            mHeight = height;
            mBitRate = bitRate;
            mEglContext = sharedEglContext;
            this.isStereo = isStereo;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mWidth + "x" + mHeight + " @" + mBitRate +
                    " to '" + mOutputFile.toString() + "' ctxt=" + mEglContext;
        }
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    public void startRecording(EncoderConfig config, boolean sync) {
        Logger.d(TAG, "Encoder: startRecording()");     
        mStoping = false;
        synchronized (mReadyFence) {
            if (mRunning) {
                Logger.w(TAG, "Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(this, "TextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
        
        if(mStoping){
        	return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
        if(sync){
        	waitDone();
        }
    }
    
    public void startRecording(EncoderConfig config) {
    	startRecording(config, false);
    }
    
    public void pauseRecording(){
    	synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
    	
    	mHandler.sendMessage(mHandler.obtainMessage(MSG_PAUSE_RECORDING));
    	
    	synchronized (mReadyFence) {
    		mRecPaused = true;
        }
    }
    
    public void restartRecording(String outputFile){
    	synchronized (mReadyFence) {
            if (!mReady || !mRecPaused) {
                return;
            }
        }
    	
    	mHandler.sendMessage(mHandler.obtainMessage(MSG_RESUME_RECORDING, outputFile));    	  
    }
    
    public String getRecordingFile(){
    	synchronized (mReadyFence) {
            if (!mReady) {
                return null;
            }
        }
    	if(mVideoEncoder != null)
    		return mVideoEncoder.getRecordingFile();
    	return null;
    }
    
    public long getTotalRecordingTimes(){
    	synchronized (mReadyFence) {
            if (!mReady) {
                return 0;
            }
        }
    	if(mVideoEncoder != null)
    		return mVideoEncoder.getTotalRecordingTimes();
    	return 0;
    }    

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public boolean stopRecording(boolean sync) {
    	mStoping = true;
    	synchronized (mReadyFence) {
            if(!mReady) return false;
        }
    	
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        if(sync){
        	waitDone();
        }
        
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
        
        return true;
    }
    
    public boolean stopRecording() {
    	return stopRecording(false);
    }
    
    public void setLabelText(String text){
    	mLastLabelString = text;
    	synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
    	mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_LABEL_TEXT, text));
    }
    
    public void setCaptureScreen(String file){
    	synchronized (mReadyFence) {
            if (!mReady || mRecPaused) {
                return;
            }
        }
    	mHandler.sendMessage(mHandler.obtainMessage(MSG_CAPTURE_SCREEN, file));
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized (mReadyFence) {
            return (mRunning && !mRecPaused);
        }
    }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    public void updateSharedContext(EGLContext sharedContext) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * TODO: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    public void frameAvailable(SurfaceTexture stLeft, SurfaceTexture stRight) {
        synchronized (mReadyFence) {
            if (!mReady || mRecPaused) {
                return;
            }
        }

        float[] transform = new float[32];      // TODO - avoid alloc every frame
        float[] transLeftform = new float[16];      // TODO - avoid alloc every frame
        
        
        
        stLeft.getTransformMatrix(transLeftform);
        if(stRight != null) {
        	float[] transRightform = new float[16]; 
        	stRight.getTransformMatrix(transRightform);
        	System.arraycopy(transRightform, 0, transform, 16, 16);
        }
        
        System.arraycopy(transLeftform, 0, transform, 0, 16);
        
        
        long timestamp = stLeft.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Logger.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, transform));
    }
    
    public void frameAvailable(float[] stLeftMatrix, float[] stRightMatrix, long leftTimestamp, long rightTimestamp) {
        synchronized (mReadyFence) {
            if (!mReady || mRecPaused) {
                return;
            }
        }

        float[] transform = new float[32];      // TODO - avoid alloc every frame
        System.arraycopy(stLeftMatrix, 0, transform, 0, 16);
        
        if(stRightMatrix != null)
        	System.arraycopy(stRightMatrix, 0, transform, 16, 16);
        
        long timestamp = leftTimestamp;//stLeft.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Logger.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, transform));
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(int leftId, int rightId) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, leftId, rightId, null));
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     * @see java.lang.Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Logger.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }


    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<TextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(TextureMovieEncoder encoder) {
            mWeakEncoder = new WeakReference<TextureMovieEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                Logger.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    break;
                case MSG_PAUSE_RECORDING:
                	encoder.handlePauseRecording();
                	break;
                case MSG_RESUME_RECORDING:
                	encoder.handleResumeRecording((String) obj);
                	break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    encoder.handleFrameAvailable((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    encoder.handleSetTexture(inputMessage.arg1, inputMessage.arg2);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;
                case MSG_UPDATE_LABEL_TEXT:
                	encoder.handleLabelText(inputMessage.obj.toString());
                	break;
                case MSG_CAPTURE_SCREEN:
                	encoder.handleCaptureScreen(inputMessage.obj.toString());
                	break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }
    
    private void handlePauseRecording() {
    	Logger.d(TAG, "handlePauseRecording");
        mVideoEncoder.drainEncoder(true);
    }
    
    private void handleResumeRecording(String outputFile) {
    	mFrameNum = 0;
    	try {
    		mVideoEncoder.startRecording(outputFile);
    	} catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    	
    	synchronized (mReadyFence) {
    		mRecPaused = false;
        }
    }

    /**
     * Starts recording.
     */
    private void handleStartRecording(EncoderConfig config) {
        Logger.d(TAG, "handleStartRecording " + config);
        mFrameNum = 0;
        prepareEncoder(config.mEglContext, config.mWidth, config.mHeight, config.mBitRate,
                config.mOutputFile, config.isStereo);
    }

    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     * @param transform The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(float[] transform, long timestampNanos) {
        if (VERBOSE) Logger.d(TAG, "handleFrameAvailable mIsInited=" + mIsInited);
        
        if(!mIsInited) return;
        
        mVideoEncoder.drainEncoder(false);
        float[] transformLeft = new float[16];
        float[] transformRight = new float[16];
        
        System.arraycopy(transform, 0, transformLeft, 0, 16);
        if(mRightTextureId > 0)
        	System.arraycopy(transform, 16, transformRight, 0, 16);
        
        if(mStereoCameraNativeHelper != null){
        	//mStereoCameraNativeHelper.setViewport(0, 0, mVideoEncoder.getWidth(), mVideoEncoder.getHeight());
        	if(mRightTextureId > 0)
        		mStereoCameraNativeHelper.drawFrame(mLeftTextureId, transformLeft, mRightTextureId, transformRight);
        	else
        		mStereoCameraNativeHelper.drawFrame(mLeftTextureId, transformLeft, 0, null);
        }
        //mFullScreen.drawFrame(mLeftTextureId, transformLeft);
        if(false){
        	if(++mFrameNum % 6 == 0)
        		drawBox(0);
        }
        
//        if(mLabelTexture != null){
//        	mLabelTexture.draw(transform);
//        }
        
        mInputWindowSurface.setPresentationTime(timestampNanos);
        mInputWindowSurface.swapBuffers();
        
        //++mFrameNum;
    }

    public void handleLabelText(String text){
//    	if(mLabelTexture != null){
//    		mLabelTexture.setText(text);
//    	}
    }
    
    public void handleCaptureScreen(String filename){
    	if(!TextUtils.isEmpty(filename)){
    		File file = new File(filename);
    		if(file.exists()) file.delete();
    		try {
    			mInputWindowSurface.saveFrame(file);
    		} catch(IOException ex){
    			
    		}
    	}
    }
    
    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        Logger.d(TAG, "handleStopRecording");
        mVideoEncoder.drainEncoder(true);
        releaseEncoder();
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int leftId, int rightId) {
        //Logger.d(TAG, "handleSetTexture " + id);
        mLeftTextureId = leftId;
        mRightTextureId = rightId;
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     * <p>
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        Logger.d(TAG, "handleUpdatedSharedContext " + newSharedContext);

        int dispMode = mStereoCameraNativeHelper.getStereoDisplayMode();
        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        //mFullScreen.release(true);
        mStereoCameraNativeHelper.releaseTexture();
        mEglCore.release();
        
        //mLabelTexture.release();
        //mCanvas.deleteRecycledResources();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new context.
//        mFullScreen = new NativeFullFrameRect(
//                new NativeTexture2dProgram(NativeTexture2dProgram.ProgramType.TEXTURE_EXT_DOUBLE_CAM));
        if(true){
	        mStereoCameraNativeHelper.initTexture(dispMode, 
	        			StereoVideoNativeHelper.STEREO_MEDIA_TYPE_UNKNOWN, false, false);
        }
        //mCanvas = new GLES20Canvas();
        //mLabelTexture = StringTexture.newInstance("Test Label St", 32.0f, 0xFFFFFFFF);
    }

    private void prepareEncoder(EGLContext sharedContext, int width, int height, int bitRate, String outputFile, boolean isStereo) {
        try {
            mVideoEncoder = new VideoEncoderCore(width, height, bitRate, outputFile);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEglCore = new EglCore(sharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();

//        mFullScreen = new NativeFullFrameRect(
//                new NativeTexture2dProgram(NativeTexture2dProgram.ProgramType.TEXTURE_EXT_DOUBLE_CAM));
        if(true){
	        mStereoCameraNativeHelper = NativeHelper.cameraNative(mContext);//new StereoCameraNativeHelper(mContext);
	        mStereoCameraNativeHelper.setTextureSize(width, height);
	        mStereoCameraNativeHelper.setWindowSize(width, height);
	        mStereoCameraNativeHelper.initTexture(isStereo ? StereoCameraNativeHelper.STEREO_DISPALY_SIDE_BY_SIDE : StereoCameraNativeHelper.STEREO_DISPLAY_2D, 
	    			StereoVideoNativeHelper.STEREO_MEDIA_TYPE_UNKNOWN, false, false);
        }
        //mStereoCameraNativeHelper.setStereoDisplayMode(StereoCameraNativeHelper.STEREO_DISPALY_SIDE_BY_SIDE);
        
        //mLabelTexture = new LabelTexture(TextUtils.isEmpty(mLastLabelString) ? "Test" : mLastLabelString);
//        if(mLabelTexture != null){
//        	mLabelTexture.createTexture(width, height);
//        }
        mIsInited = true;
    }

    private void releaseEncoder() {
    	mIsInited = false;
        mVideoEncoder.release();
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }

        if(mStereoCameraNativeHelper != null){
        	mStereoCameraNativeHelper.releaseTexture();
        	mStereoCameraNativeHelper.release();
        }
        
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        
    }

    /**
     * Draws a box, with position offset.
     */
    private void drawBox(int posn) {
        final int width = mInputWindowSurface.getWidth();
        int xpos = (posn * 4) % (width - 30);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, 60, 60);
        GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
    
    private boolean waitDone() {
        final Object waitDoneLock = new Object();
        final Runnable unlockRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (waitDoneLock) {
                    waitDoneLock.notifyAll();
                }
            }
        };

        synchronized (waitDoneLock) {
        	mHandler.post(unlockRunnable);
            try {
                waitDoneLock.wait();
            } catch (InterruptedException ex) {
            	Logger.e(TAG, "waitDone interrupted");
                return false;
            }
        }
        return true;
    }
}
