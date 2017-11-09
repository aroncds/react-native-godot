package com.react.godotengine;

import android.app.Activity;
import android.support.annotation.Nullable;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.views.view.ReactViewManager;

import org.godotengine.godot.Godot;


public class GodotViewManager extends SimpleViewManager<Godot> {

    private ThemedReactContext mContext = null;
    public static final String REACT_CLASS = "RCTGodotView";

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @Override
    protected Godot createViewInstance(ThemedReactContext reactContext) {
        Godot view = new Godot(reactContext, reactContext.getCurrentActivity());
        mContext = reactContext;
        return view;
    }

    @ReactProp(name = "package")
  	public void setPackage(Godot view, @Nullable String name) {
        view.setPackage(name);
  	}
}
