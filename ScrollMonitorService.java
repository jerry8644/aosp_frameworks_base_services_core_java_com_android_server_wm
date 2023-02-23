package com.android.server.wm; 
import android.os.RemoteException;

import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.lang.System;

import com.android.server.input.InputManagerService;

import android.hardware.input.InputManager;
import android.view.InputChannel;
import android.view.InputMonitor;
import android.view.InputEventReceiver;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.IScrollMonitorService;
import android.view.Display;
import android.view.SurfaceControl;
import static android.view.Display.DEFAULT_DISPLAY;
import android.widget.Scroller;

import android.view.ViewConfiguration;
import java.io.FileDescriptor;
import android.os.ShellCallback;
import android.os.ResultReceiver;
import android.os.IBinder;
import android.os.Process;

public class ScrollMonitorService extends IScrollMonitorService.Stub{
    	public boolean isDesignEnabled = false;
	public boolean isFramerateEnable = false;
	public boolean isResolutionEnable = false;
	public int resolutionFR = 0;
	
	private static final String TAG = "ScrollMonitorService";
	private static final int MSG_SCROLL_STOPPED = 1;
	private static final int MSG_FLINGING = 2;
	private final int pid;

	
	private Context mContext;  
	private final WindowManagerService mWindowManagerService;  
	private final InputManagerService mInputManagerService;
	private final HandlerThread mInputHandlerThread;
	private final HandlerThread mStatusHandlerThread;
	private final InputChannel mInputChannel;
	private final InputEventReceiver mInputEventReceiver;
	private final StatusCheckHandler mStatusCheckHandler;
	private final WindowFocusChangeListener mWindowFocusChangeListener;
	private VelocityTracker mVelocityTracker = null;
	private WindowState mFocusedWindow;
	private final Display mDisplay;
	private static boolean isScrolling;

	private final Scroller mScroller;
	private final int maxFlingVelocity = 28000;

	private float scaleFactor = 1.0f;
	private float desiredFrameRate = 60.0f;
	private final float ydpi;
	private final int heightPixels;

	private final IBinder mDisplayToken; 
	private int viewId;
	private final int[] allowedConfigs = new int[1];
	private int frameTime = 11;
	private final Map<Float, Config> availableConfigs = new HashMap<Float, Config>();
	private Map<Integer, Float> model = new HashMap<Integer, Float>();
	private final int[] velocityCandidates = {1000, 2000, 4000, 6000, 8000, 10000, 12500, 15000, 17500, 20000};
	
	private class Config {
		public int mode;
		public int divisor;
		public Config(int m, int d) {
			mode = m;
			divisor = d;
		}
	}

	public ScrollMonitorService(Context ctx, WindowManagerService wm, InputManagerService inputManager){
		mContext = ctx;
		mWindowManagerService = wm;
		mInputManagerService = inputManager;
		pid = Process.myPid();

		mInputHandlerThread = new HandlerThread("InputReceiverThread");
		mInputHandlerThread.start();

		mInputChannel = mInputManagerService.monitorGestureInput(TAG, DEFAULT_DISPLAY).getInputChannel();
		mInputEventReceiver = new ScrollInputReceiver(mInputChannel, mInputHandlerThread.getLooper());

		mStatusHandlerThread = new HandlerThread("StatusMonitorThread");
		mStatusHandlerThread.start();
		mStatusCheckHandler = new StatusCheckHandler(mStatusHandlerThread.getLooper());

		if(mVelocityTracker == null){
			mVelocityTracker = VelocityTracker.obtain();
		}
		
		mWindowFocusChangeListener = new WindowFocusChangeListener();
		mWindowManagerService.addWindowChangeListener(mWindowFocusChangeListener);

		mScroller = new Scroller(mContext);
		
		mDisplay = mContext.getDisplay();
		mDisplayToken = SurfaceControl.getInternalDisplayToken();

		ydpi = Math.round(mContext.getResources().getDisplayMetrics().ydpi);
		heightPixels = mContext.getResources().getDisplayMetrics().heightPixels;
		
		availableConfigs.put(90.0f, new Config(0, 1));
		availableConfigs.put(60.0f, new Config(1, 1));
		availableConfigs.put(45.0f, new Config(0, 2));
		availableConfigs.put(30.0f, new Config(1, 2));
		availableConfigs.put(22.5f, new Config(0, 4));
		availableConfigs.put(20.0f, new Config(1, 4));

		resetModel();

		Log.d(TAG, "initialized! mContext=" + mContext);
	}

	private class ScrollInputReceiver extends InputEventReceiver {
		private MotionEvent me;
		private int velocityY;
		private int velocityX;
		private float velocityYAbs;
		private float velocityYCm;
		private float ppi;
		private Config config;
		//private float sf;
		private int duration;
		private Message msg;
		public ScrollInputReceiver(InputChannel inputChannel, Looper looper) {
			super(inputChannel, looper);
		}
		@Override
		public void onInputEvent(InputEvent e) { 
			if(e instanceof MotionEvent) {
				me = (MotionEvent) e;
				final int action = me.getAction() & MotionEvent.ACTION_MASK;
				mVelocityTracker.addMovement(me);	
				switch(action) {
					case MotionEvent.ACTION_DOWN:
						mVelocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
						velocityY = (int) mVelocityTracker.getYVelocity();
						Log.i(TAG, "DOWN");
						Log.i(TAG, "velocityY_DOWN=" + velocityY);
						if(!mScroller.isFinished()){
							mScroller.abortAnimation();
						}
						break;
					case MotionEvent.ACTION_MOVE:
						mVelocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
						velocityY = (int) mVelocityTracker.getYVelocity();	
						Log.i(TAG, "MOVE");
						Log.i(TAG, "velocityY_MOVE=" + velocityY);				
						break;
					case MotionEvent.ACTION_UP:
					case MotionEvent.ACTION_CANCEL:
						mVelocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
						velocityY = (int) mVelocityTracker.getYVelocity();
						Log.i(TAG, "UP");
						Log.i(TAG, "velocityY_UP=" + velocityY);						
						if (isDesignEnabled && mFocusedWindow != null) {
							//float upX = me.getX();
                			//float upY = me.getY();
							//int scrollX = mScroller.getScrollX();
							//int scrollY = mScroller.getScrollY();
							mVelocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
							velocityY = (int) mVelocityTracker.getYVelocity();
							Log.i(TAG, "velocityY_CANCLE=" + velocityY);
							//velocityX = (int) mVelocityTracker.getXVelocity();
							velocityYAbs = Math.abs(velocityY);
							//Log.i(TAG, "upX=" + upX + " upY=" + upY + " scrollX=" + scrollX + " scrollY=" + scrollY);
							//Log.d(TAG, "upX=" + upX + " upY=" + upY + " width=" + mFocusedWindow.mRequestedWidth + " height=" + mFocusedWindow.mRequestedHeight);
							if(velocityYAbs >= 1000) {
								//mScroller.fling(scrollX, scrollY, -velocityX, -velocityY, 0, mFocusedWindow.getAttrs().width, 0, mFocusedWindow.getAttrs().height);
								velocityYCm = pixelToCm(velocityYAbs);
								if(isResolutionEnable == true){
							            switch(resolutionFR){
							            case 0:
							            	desiredFrameRate = 90.0f;
							            	break;
							            case 1:
							            	desiredFrameRate = 60.0f;
							            	break;
							            case 2:
							            	desiredFrameRate = 45.0f;
							            	break;
							            case 3:
							            	desiredFrameRate = 30.0f;
							            	break;
							            case 4:
							           	desiredFrameRate = 22.5f;
							            	break;
							              case 5:
							           	desiredFrameRate = 20.0f;
							            	break;
							            }
								}
								else{
								    desiredFrameRate = getDesiredFrameRate(velocityY);
								    Log.i(TAG,"desiredFrameRate = "+desiredFrameRate);
								}
								if(isFramerateEnable == true)
								{   
								    scaleFactor = 1.0f;
								}
								else{
								scaleFactor = getScaleFactor(velocityYCm);
								}
								//duration = mScroller.getSplineFlingDuration(velocityY);
								//Log.w(TAG, "Duration="+ duration);								
								config = availableConfigs.get(desiredFrameRate);
								frameTime = (int)(1.0f / desiredFrameRate * 1000.0f);
								Log.i(TAG,"frameTime = " + frameTime);
								allowedConfigs[0] = config.mode;
								try {
									//mFocusedWindow.mClient.dispatchModeChanged(scaleFactor, frameTime, config.divisor);
									//Log.i(TAG,"scalefactor1="+scaleFactor);
									mFocusedWindow.mClient.dispatchModeChanged(scaleFactor, frameTime, config.divisor);
									Log.i(TAG, "scalefactor2 = " + scaleFactor);
									SurfaceControl.setAllowedDisplayConfigs(mDisplayToken, allowedConfigs);//guess change framerate 
									Log.i(TAG, "try_scrollmoniter");
								} catch (RemoteException re) {
									Log.e(TAG, "RPC error: " + re);
								}
									
								//Log.d(TAG, "velocityY=" + velocityY + " velocityYCm=" + velocityYCm + " duration=" + duration + " scaleFactor=" + scaleFactor + " desiredFrameRate=" + desiredFrameRate);
							}
						}
						break;
				}				
			}
		}
	}

	@Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out, FileDescriptor err,
            String[] args, ShellCallback callback, ResultReceiver result) {
        new ScrollMonitorShellCommand(this).exec(this, in, out, err, args, callback, result);
    }

	private class WindowFocusChangeListener implements WindowManagerService.WindowChangeListener {
		@Override
		public void windowsChanged() {
			return;
		}
		@Override
		public void focusChanged() {
			mFocusedWindow = mWindowManagerService.getFocusedWindow();
			if (mFocusedWindow != null) {
				if ((mFocusedWindow.getAttrs().type == 1 || mFocusedWindow.getAttrs().type == 2) && !mFocusedWindow.getWindowTag().toString().contains("launcher")) {
					Log.d(TAG, "title=" + mFocusedWindow.getWindowTag() + " ApplicationInfo=" + mContext.getApplicationInfo());
				} else {
					mFocusedWindow = null;
				}
			}
		}
	}

	private float pixelToCm(float pixels) {
		return pixels / ydpi * 2.54f;
	}

	private float getDesiredFrameRate (int velocity) {
		int velocityAbs = Math.abs(velocity);
		int minDistance = Math.abs(velocityAbs - velocityCandidates[0]);
		int closestVelocity = velocityCandidates[0];

		for(int v : velocityCandidates) {
			int distance = Math.abs(velocityAbs - v);
			if (distance <= minDistance) {
				closestVelocity = v;
				minDistance = distance;
			}
		}
		if(model != null) {
			return model.get(closestVelocity);
		}
		else {
			return 60.0f;
		}
	}

	//parameters for pixel 4 xl only
	private float getScaleFactor(float velocityYCm) {
		double velocityYAngle = Math.atan((velocityYCm / 2) / 35) * (180 / 3.1416) * 2;
		double acuity = 1 + 3.089 * 0.000001 * velocityYAngle * velocityYAngle * velocityYAngle;
		float scaleFactor = (float) (1 / (2  *35 *537 * Math.tan((((acuity / 60) * 3.1416) / 180) / 2)));
		scaleFactor = Math.round(scaleFactor * 1000.0f) / 1000.0f;
		return scaleFactor;
	}

	public Display.Mode[] getDisplayModes () {
		Display.Mode[] modes = mDisplay.getSupportedModes();
		return modes;
	}

	@Override
	public boolean setFrameRate(float frameRate) {
		if(!availableConfigs.containsKey(frameRate)) 
			return false;
		Config c = availableConfigs.get(frameRate);
		frameTime = (int)(1.0f / frameRate * 1000.0f);
		allowedConfigs[0] = c.mode;
		if (mFocusedWindow != null) {
			try {
				mFocusedWindow.mClient.setFrameRate(c.divisor);			
				SurfaceControl.setAllowedDisplayConfigs(mDisplayToken, allowedConfigs);
			} catch (RemoteException re) {
				Log.e(TAG, "RPC error: " + re);
				return false;
			}
		} else {
			return false;
		}
		return true;
	}

	public WindowState getFocusedWindow() {
		return mFocusedWindow;
	}

	public boolean setScaleFactor(float scale) {
		if(scale > 0.0f && scale <= 1.0f) {
			scaleFactor = scale;
			return true;
		} else {
			return false;
		}
	}

	private class StatusCheckHandler extends Handler {
		public StatusCheckHandler(Looper looper) {
			super(looper);
		}
		@Override
		public void handleMessage(Message msg){
			super.handleMessage(msg);
			switch (msg.what) {
				case MSG_SCROLL_STOPPED:						
					Log.d(TAG, "scroll stopped!!!!!!");
					//+
					Config c = availableConfigs.get(90.0f);
					if(resolutionFR == 0){
					c = availableConfigs.get(90.0f);
					frameTime = (int)(1.0f / 90.0f * 1000.0f);
					allowedConfigs[0] = c.mode;
					}else if (resolutionFR == 1){
					c = availableConfigs.get(60.0f);
					frameTime = (int)(1.0f / 60.0f * 1000.0f);
					allowedConfigs[0] = c.mode;
					}else if (resolutionFR == 2){
					c = availableConfigs.get(45.0f);
					frameTime = (int)(1.0f / 45.0f * 1000.0f);
					allowedConfigs[0] = c.mode;
					}else if (resolutionFR == 3){
					c = availableConfigs.get(30.0f);
					frameTime = (int)(1.0f / 30.0f * 1000.0f);
					allowedConfigs[0] = c.mode;
					}else if (resolutionFR == 4){
					c = availableConfigs.get(22.5f);
					frameTime = (int)(1.0f / 22.5f * 1000.0f);
					allowedConfigs[0] = c.mode;
					}
					
					//mFocusedWindow.mClient.setFrameRate(c.divisor);	
					//SurfaceControl.setAllowedDisplayConfigs(mDisplayToken, allowedConfigs);
					if (mFocusedWindow != null) {
						try {
							mFocusedWindow.mClient.setFrameRate(c.divisor);			
							SurfaceControl.setAllowedDisplayConfigs(mDisplayToken, allowedConfigs);
						} catch (RemoteException re) {
						Log.e(TAG, "RPC error: " + re);
						return ;
						}
					} else {
						return ;
					}
					//Log.d(TAG, " STOP FrameRate= "  );
					break;
			}			
		}
	}

	public void resetModel() {
		if (model == null) {
			model = new HashMap<Integer, Float>();
		}
		model.clear();
		model.put(1000, 22.5f);
		model.put(2000, 30.0f);
		model.put(4000, 30.0f);
		model.put(6000, 45.0f);
		model.put(8000, 60.0f);
		model.put(10000, 60.0f);
		model.put(12500, 60.0f);
		model.put(15000, 60.0f);
		model.put(17500, 60.0f);
		model.put(20000, 60.0f);
	}

	public boolean setFixedFrameRate(float frameRate) {
		if(!availableConfigs.containsKey(frameRate)) 
			return false;
		if (model == null) {
			model = new HashMap<Integer, Float>();
		}
		model.clear();
		for (int v : velocityCandidates) {
			model.put(v, frameRate);
		}
		return true;
	}

	@Override
	public boolean setModel(Map speedToFR) {
		if (speedToFR != null && model != null) {
			model.clear();
			for (Integer velocity : velocityCandidates) {
				Float frameRate = (Float) speedToFR.get(velocity);
				if (frameRate != null && availableConfigs.containsKey(frameRate)) {
					model.put(velocity, frameRate);
				} else {
					model.clear();
					resetModel();
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public Map<Integer, Float> getModel() {
		return model;
	}

	@Override
	public void notifyScrollStopped() {
		Message msg =mStatusCheckHandler.obtainMessage(MSG_SCROLL_STOPPED);
		mStatusCheckHandler.sendMessageAtFrontOfQueue(msg);
	}

	public String getServeceInfo() {
		return "pid=" + pid;
	}
} 
