/*************************************************************************/
/*  Godot.java                                                           */
/*************************************************************************/
/*                       This file is part of:                           */
/*                           GODOT ENGINE                                */
/*                      https://godotengine.org                          */
/*************************************************************************/
/* Copyright (c) 2007-2017 Juan Linietsky, Ariel Manzur.                 */
/* Copyright (c) 2014-2017 Godot Engine contributors (cf. AUTHORS.md)    */
/*                                                                       */
/* Permission is hereby granted, free of charge, to any person obtaining */
/* a copy of this software and associated documentation files (the       */
/* "Software"), to deal in the Software without restriction, including   */
/* without limitation the rights to use, copy, modify, merge, publish,   */
/* distribute, sublicense, and/or sell copies of the Software, and to    */
/* permit persons to whom the Software is furnished to do so, subject to */
/* the following conditions:                                             */
/*                                                                       */
/* The above copyright notice and this permission notice shall be        */
/* included in all copies or substantial portions of the Software.       */
/*                                                                       */
/* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,       */
/* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF    */
/* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.*/
/* IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY  */
/* CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,  */
/* TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE     */
/* SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.                */
/*************************************************************************/
package org.godotengine.godot;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.app.*;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.view.*;
import android.os.*;
import android.util.Log;
import android.graphics.*;
import android.hardware.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;

import android.provider.Settings.Secure;
import android.widget.FrameLayout;

import com.facebook.react.ReactRootView;
import com.facebook.react.views.view.ReactViewGroup;

import org.godotengine.godot.input.*;

import java.io.InputStream;
import javax.microedition.khronos.opengles.GL10;
import java.util.LinkedList;


public class Godot extends FrameLayout {
	static final int MAX_SINGLETONS = 64;
    private boolean use_32_bits=false;
    private boolean use_immersive=false;
	private boolean keep_screen_on=true;
	private Activity activity;

    public Godot(Context context, Activity activity) {
        super(context);
        this.activity = activity;
    }

    public Activity getActivity(){
        return activity;
    }

    @Override
    public void onAttachedToWindow(){
        super.onAttachedToWindow();
        this.onCreate(null);
    }

    public ClassLoader getClassLoader(){
    	return getActivity().getClassLoader();
	}

    static public class SingletonBase {

		protected void registerClass(String p_name, String[] p_methods) {

			GodotLib.singleton(p_name,this);

			Class clazz = getClass();
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				boolean found=false;
				Log.d("XXX","METHOD: %s\n" + method.getName());

				for (String s : p_methods) {
				Log.d("XXX", "METHOD CMP WITH: %s\n" + s);
					if (s.equals(method.getName())) {
						found=true;
						Log.d("XXX","METHOD CMP VALID");
						break;
					}
				}
				if (!found)
					continue;

				Log.d("XXX","METHOD FOUND: %s\n" + method.getName());

				List<String> ptr = new ArrayList<String>();

				Class[] paramTypes = method.getParameterTypes();
				for (Class c : paramTypes) {
					ptr.add(c.getName());
				}

				String[] pt = new String[ptr.size()];
				ptr.toArray(pt);

				GodotLib.method(p_name,method.getName(),method.getReturnType().getName(),pt);
			}

			Godot.singletons[Godot.singleton_count++]=this;
		}

		protected void onMainActivityResult(int requestCode, int resultCode, Intent data) {}

		protected void onMainPause() {}
		protected void onMainResume() {}
		protected void onMainDestroy() {}

		protected void onGLDrawFrame(GL10 gl) {}
		protected void onGLSurfaceChanged(GL10 gl, int width, int height) {} // singletons will always miss first onGLSurfaceChanged call
		//protected void onGLSurfaceCreated(GL10 gl, EGLConfig config) {} // singletons won't be ready until first GodotLib.step()

		public void registerMethods() {}
	}


	private String[] command_line;
	private boolean use_apk_expansion;

	public GodotView mView;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private Sensor mMagnetometer;
	private Sensor mGyroscope;

	public FrameLayout layout;
	public RelativeLayout adLayout;

	static public GodotIO io;

	static SingletonBase singletons[] = new SingletonBase[MAX_SINGLETONS];
	static int singleton_count=0;

    String expansion_pack_path;

	public interface ResultCallback {
		public void callback(int requestCode, int resultCode, Intent data);
	};
	public ResultCallback result_callback;

	public void onActivityResult (int requestCode, int resultCode, Intent data) {

		for(int i=0;i<singleton_count;i++) {

			singletons[i].onMainActivityResult(requestCode,resultCode,data);
		}
	};

	public void onVideoInit(boolean use_gl2) {

		this.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

		// GodotEditText layout
		GodotEditText edittext = new GodotEditText(this.getContext());
		   edittext.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
        // ...add to FrameLayout
		this.addView(edittext);

		mView = new GodotView(getContext(), io,use_gl2,use_32_bits, this);

		this.addView(mView,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));

		edittext.setView(mView);
		io.setEdit(edittext);

		final Godot godot = this;
		mView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					Point fullSize = new Point();
					activity.getWindowManager().getDefaultDisplay().getSize(fullSize);
					Rect gameSize = new Rect();
					godot.mView.getWindowVisibleDisplayFrame(gameSize);

					final int keyboardHeight = fullSize.y - gameSize.bottom;
					Log.d("GODOT", "setVirtualKeyboardHeight: " + keyboardHeight);
					GodotLib.setVirtualKeyboardHeight(keyboardHeight);
				}
		});

		// Ad layout
		adLayout = new RelativeLayout(this.getContext());
		adLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
		this.addView(adLayout);

		final String[] current_command_line = command_line;
		final GodotView view = mView;
		mView.queueEvent(new Runnable() {
			@Override
			public void run() {
				GodotLib.setup(current_command_line);
                activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						view.setKeepScreenOn("True".equals(GodotLib.getGlobal("display/driver/keep_screen_on")));
						onResume();
					}
				});
			}
		});
	}

	@Override
	public void onDetachedFromWindow(){
		super.onDetachedFromWindow();
		//this.onBackPressed();
	}

	public void setKeepScreenOn(final boolean p_enabled) {
		keep_screen_on = p_enabled;
		if (mView != null){
            activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mView.setKeepScreenOn(p_enabled);
				}
			});
		}
	}

	public void alert(final String message, final String title) {

        activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				builder.setMessage(message).setTitle(title);
				builder.setPositiveButton(
					"OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							dialog.cancel();
						}
					});
				AlertDialog dialog = builder.create();
				dialog.show();
			}
		});
	}

	private String[] getCommandLine() {
            InputStream is;
            try {
		is = this.getContext().getAssets().open("_cl_");
                byte[] len = new byte[4];
                int r = is.read(len);
		if (r<4) {
                    Log.d("XXX","**ERROR** Wrong cmdline length.\n");
		    Log.d("GODOT", "**ERROR** Wrong cmdline length.\n");
                    return new String[0];
                }
		int argc=((int)(len[3]&0xFF)<<24) | ((int)(len[2]&0xFF)<<16) | ((int)(len[1]&0xFF)<<8) | ((int)(len[0]&0xFF));
                String[] cmdline = new String[argc];

                for(int i=0;i<argc;i++) {
                    r = is.read(len);
                    if (r<4) {

			Log.d("GODOT", "**ERROR** Wrong cmdline param length.\n");
                        return new String[0];
                    }
		    int strlen=((int)(len[3]&0xFF)<<24) | ((int)(len[2]&0xFF)<<16) | ((int)(len[1]&0xFF)<<8) | ((int)(len[0]&0xFF));
                    if (strlen>65535) {
			Log.d("GODOT", "**ERROR** Wrong command len\n");
                        return new String[0];
                    }
		    byte[] arg = new byte[strlen];
                    r = is.read(arg);
		    if (r==strlen) {
                        cmdline[i]=new String(arg,"UTF-8");
		    }
			}
			return cmdline;
		} catch (Exception e) {
		e.printStackTrace();
		Log.d("GODOT", "**ERROR** Exception " + e.getClass().getName() + ":" + e.getMessage());
			return new String[0];
		}
	}


	private void initializeGodot() {

		if (expansion_pack_path!=null) {

			String[] new_cmdline;
			int cll=0;
			if (command_line!=null) {
			        Log.d("GODOT", "initializeGodot: command_line: is not null" );
				new_cmdline = new String[ command_line.length + 2 ];
				cll=command_line.length;
				for(int i=0;i<command_line.length;i++) {
					new_cmdline[i]=command_line[i];
				}
			} else {
			        Log.d("GODOT", "initializeGodot: command_line: is null" );
				new_cmdline = new String[ 2 ];
			}

			new_cmdline[cll]="--main_pack";
			new_cmdline[cll+1]=expansion_pack_path;
			command_line=new_cmdline;
		}

		io = new GodotIO(this);
		io.unique_id = Secure.getString(getContext().getContentResolver(), Secure.ANDROID_ID);
		GodotLib.io=io;
		Log.d("GODOT", "command_line is null? " + ((command_line == null)?"yes":"no"));

		mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

//		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//		mSensorManager.registerListener(getActivity(), mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
//		mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//		mSensorManager.registerListener(getActivity(), mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
//		mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
//		mSensorManager.registerListener(getActivity(), mGyroscope, SensorManager.SENSOR_DELAY_GAME);

		GodotLib.initialize(this, io.needsReloadHooks(), this.getContext().getAssets(), use_apk_expansion);

		result_callback = null;
	}


	public void setPackage(String name){

    }

	public View onCreate(Bundle savedInstanceState) {

		Log.d("GODOT", "** GODOT ACTIVITY CREATED HERE ***\n");
		Window window = activity.getWindow();
		//window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

		if (true) {
			boolean md5mismatch = false;
			command_line = getCommandLine();
			String main_pack_md5 = null;
			String main_pack_key = null;

			List<String> new_args = new LinkedList<String>();


			for (int i = 0; i < command_line.length; i++) {

				boolean has_extra = i < command_line.length - 1;
				if (command_line[i].equals("--use_depth_32")) {
					use_32_bits = true;
				} else if (command_line[i].equals("--use_immersive")) {
					use_immersive = true;
					if (Build.VERSION.SDK_INT >= 19.0) { // check if the application runs on an android 4.4+
						window.getDecorView().setSystemUiVisibility(
								View.SYSTEM_UI_FLAG_LAYOUT_STABLE
										| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
										| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
										| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
										| View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
										| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

						UiChangeListener();
					}
				} else if (command_line[i].equals("--use_apk_expansion")) {
					use_apk_expansion = true;
				} else if (has_extra && command_line[i].equals("--apk_expansion_md5")) {
					main_pack_md5 = command_line[i + 1];
					i++;
				} else if (has_extra && command_line[i].equals("--apk_expansion_key")) {
					main_pack_key = command_line[i + 1];
					SharedPreferences prefs = getContext().getSharedPreferences("app_data_keys", getContext().MODE_PRIVATE);
					Editor editor = prefs.edit();
					editor.putString("store_public_key", main_pack_key);

					editor.commit();
					i++;
				} else if (command_line[i].trim().length() != 0) {
					new_args.add(command_line[i]);
				}
			}

			if (new_args.isEmpty()) {
				command_line = null;
			} else {

				command_line = new_args.toArray(new String[new_args.size()]);
			}
			if (use_apk_expansion && main_pack_md5 != null && main_pack_key != null) {
				//check that environment is ok!
				if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					Log.d("GODOT", "**ERROR! No media mounted!");
					//show popup and die
				}
			}
		}

		initializeGodot();

		return mView;
	}

    public void onDestroy(){

		for(int i=0;i<singleton_count;i++) {
			singletons[i].onMainDestroy();
		}
	}

    public void onPause() {

		mView.onPause();
		mView.queueEvent(new Runnable() {
			@Override
			public void run() {
				GodotLib.focusout();
			}
		});
		// mSensorManager.unregisterListener(getActivity());

		for(int i=0;i<singleton_count;i++) {
			singletons[i].onMainPause();
		}
	}

	public void onResume() {

		mView.onResume();
		mView.queueEvent(new Runnable() {
			@Override
			public void run() {
				GodotLib.focusin();
			}
		});

//		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
//		mSensorManager.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_GAME);
//		mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_GAME);

		if(use_immersive && Build.VERSION.SDK_INT >= 19.0){ // check if the application runs on an android 4.4+
			Window window = activity.getWindow();
			window.getDecorView().setSystemUiVisibility(
					    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
						    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
						    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
						    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
						    | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
						    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
		}

		for(int i=0;i<singleton_count;i++) {
			singletons[i].onMainResume();
		}
	}

	public void UiChangeListener() {
		final View decorView = activity.getWindow().getDecorView();
		decorView.setOnSystemUiVisibilityChangeListener (new View.OnSystemUiVisibilityChangeListener() {
			@Override
			public void onSystemUiVisibilityChange(int visibility) {
				if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
					decorView.setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_FULLSCREEN
					| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
				}
			}
		});
	}

	public void onSensorChanged(SensorEvent event) {
		Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int displayRotation = display.getRotation();

		float[] adjustedValues = new float[3];
		final int axisSwap[][] = {
            { 1, -1,  0,  1},     // ROTATION_0
            {-1, -1,  1,  0},     // ROTATION_90
            {-1,  1,  0,  1},     // ROTATION_180
            { 1,  1,  1,  0}
		}; // ROTATION_270

		final int[] as = axisSwap[displayRotation];
		adjustedValues[0]  =  (float)as[0] * event.values[ as[2] ];
		adjustedValues[1]  =  (float)as[1] * event.values[ as[3] ];
		adjustedValues[2]  =  event.values[2];

		final float x = adjustedValues[0];
		final float y = adjustedValues[1];
		final float z = adjustedValues[2];

		final int typeOfSensor = event.sensor.getType();
		if (mView != null) {
			mView.queueEvent(new Runnable() {
				@Override
				public void run() {
					if (typeOfSensor == Sensor.TYPE_ACCELEROMETER) {
						GodotLib.accelerometer(x,y,z);
					}
					if (typeOfSensor == Sensor.TYPE_MAGNETIC_FIELD) {
						GodotLib.magnetometer(x,y,z);
					}
					if (typeOfSensor == Sensor.TYPE_GYROSCOPE) {
						GodotLib.gyroscope(x,y,z);
					}
				}
			});
		}
	}

	public void onBackPressed() {

		System.out.printf("** BACK REQUEST!\n");
		if (mView != null) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					activity.onBackPressed();
					//GodotLib.back();
				}
			});
		}

	}

	public void forceQuit() {
		System.exit(0);
	}

	//@Override public boolean dispatchTouchEvent (MotionEvent event) {
	public boolean gotTouchEvent(final MotionEvent event) {

		final int evcount=event.getPointerCount();
		if (evcount==0)
			return true;

		if (mView != null) {
			final int[] arr = new int[event.getPointerCount()*3];

			for(int i=0;i<event.getPointerCount();i++) {

				arr[i*3+0]=(int)event.getPointerId(i);
				arr[i*3+1]=(int)event.getX(i);
				arr[i*3+2]=(int)event.getY(i);
			}

			//System.out.printf("gaction: %d\n",event.getAction());
			final int action = event.getAction() & MotionEvent.ACTION_MASK;
			mView.queueEvent(new Runnable() {
				@Override
				public void run() {
					switch(action) {
						case MotionEvent.ACTION_DOWN: {
							GodotLib.touch(0,0,evcount,arr);
							//System.out.printf("action down at: %f,%f\n", event.getX(),event.getY());
						} break;
						case MotionEvent.ACTION_MOVE: {
							GodotLib.touch(1,0,evcount,arr);
							/*
							for(int i=0;i<event.getPointerCount();i++) {
								System.out.printf("%d - moved to: %f,%f\n",i, event.getX(i),event.getY(i));
							}
							*/
						} break;
						case MotionEvent.ACTION_POINTER_UP: {
							final int indexPointUp = event.getActionIndex();
							final int pointer_idx = event.getPointerId(indexPointUp);
							GodotLib.touch(4,pointer_idx,evcount,arr);
							//System.out.printf("%d - s.up at: %f,%f\n",pointer_idx, event.getX(pointer_idx),event.getY(pointer_idx));
						} break;
						case MotionEvent.ACTION_POINTER_DOWN: {
							int pointer_idx = event.getActionIndex();
							GodotLib.touch(3,pointer_idx,evcount,arr);
							//System.out.printf("%d - s.down at: %f,%f\n",pointer_idx, event.getX(pointer_idx),event.getY(pointer_idx));
						} break;
						case MotionEvent.ACTION_CANCEL:
						case MotionEvent.ACTION_UP: {
							GodotLib.touch(2,0,evcount,arr);
							/*
							for(int i=0;i<event.getPointerCount();i++) {
								System.out.printf("%d - up! %f,%f\n",i, event.getX(i),event.getY(i));
							}
							*/
						} break;
					}
				}
			});
		}
		return true;
	}

	private void queueEvent(Runnable runnable) {
		// TODO Auto-generated method stub

	}
}
