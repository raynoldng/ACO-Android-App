package com.cslabs.antcolonyoptimization;

import org.andengine.entity.primitive.Rectangle;
import org.andengine.input.touch.TouchEvent;
import org.andengine.util.adt.color.Color;
import org.andengine.util.debug.Debug;

public class CityRect extends Rectangle {
	
	private MainActivity mActivity;
	private final static String TAG = "CityRect";
	private final static float mSideLength = 25;

	public CityRect(float pX, float pY,  MainActivity pActivity) {
		super(pX, pY, mSideLength, mSideLength, pActivity.getVertexBufferObjectManager());
		
		this.mActivity = pActivity;
		this.setColor(Color.BLACK);
		
		mActivity.mScene.registerTouchArea(this);
		mActivity.mScene.attachChild(this);
	}
	
	public void detach() {
//		mActivity.runOnUpdateThread(new Runnable() {
//			
//			@Override
//			public void run() {
//				//mActivity.mScene.detachChild(CityRect.this);
//				CityRect.this.detach();
//				CityRect.this.dispose();
//			}
//		});
	}

	@Override
	public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
			float pTouchAreaLocalX, float pTouchAreaLocalY) {
		
		this.setPosition(pSceneTouchEvent.getX(), pSceneTouchEvent.getY());
		this.setColor(Color.RED);
		
		if(pSceneTouchEvent.isActionUp()) {
			this.setColor(Color.BLACK);
		}
		
		return true;
	}
	
	
	

}
