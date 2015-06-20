package com.cslabs.antcolonyoptimization;

import java.io.IOException;
import java.util.ArrayList;

import org.andengine.engine.camera.Camera;
import org.andengine.engine.handler.IUpdateHandler;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.andengine.entity.Entity;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.scene.IOnAreaTouchListener;
import org.andengine.entity.scene.IOnSceneTouchListener;
import org.andengine.entity.scene.ITouchArea;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.Background;
import org.andengine.entity.sprite.Sprite;
import org.andengine.entity.text.Text;
import org.andengine.entity.util.FPSLogger;
import org.andengine.input.touch.TouchEvent;
import org.andengine.opengl.font.Font;
import org.andengine.opengl.font.FontFactory;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlasTextureRegionFactory;
import org.andengine.opengl.texture.atlas.bitmap.BuildableBitmapTextureAtlas;
import org.andengine.opengl.texture.atlas.bitmap.source.IBitmapTextureAtlasSource;
import org.andengine.opengl.texture.atlas.buildable.builder.BlackPawnTextureAtlasBuilder;
import org.andengine.opengl.texture.atlas.buildable.builder.ITextureAtlasBuilder.TextureAtlasBuilderException;
import org.andengine.opengl.texture.region.ITextureRegion;
import org.andengine.ui.activity.BaseGameActivity;
import org.andengine.util.adt.color.Color;
import org.andengine.util.debug.Debug;
import org.andengine.util.math.MathUtils;

import android.graphics.Typeface;

public class MainActivity extends BaseGameActivity implements IOnSceneTouchListener, IOnAreaTouchListener{

	// ===========================================================
	// Constants
	// ===========================================================
	private static final String TAG = "MainActivity";
	
	private static final int CAMERA_WIDTH = 720;
	private static final int CAMERA_HEIGHT = 480;

	// ===========================================================
	// Variables
	// ===========================================================
	Scene mScene;
	
	Thread mACOBkgdThread = null;
	
	volatile ArrayList<CityRect> mCities = new ArrayList<CityRect>();
	
	private ACO mACO;
	
	private BuildableBitmapTextureAtlas mBitmapTextureAtlas;
	private ITextureRegion mRunAntsTR;
	private ITextureRegion mClearAllTR;

	private float mTime = 0.0f;
	
	public Font mFont, mFontHeader;
	public Text mTitle;
	public Text mCitiesText;
	public Text mShortestPathText;
	public Text mTimerText;
	public Text mCyclesText;
	public Sprite mRunAntsButton;
	public Sprite mClearAllButton;
	
	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public EngineOptions onCreateEngineOptions() {
		final Camera camera = new Camera(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT);

		return new EngineOptions(true, ScreenOrientation.LANDSCAPE_SENSOR,
				new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT), camera);
	}

	@Override
	public void onCreateResources(
			OnCreateResourcesCallback pOnCreateResourcesCallback)
			throws IOException {
		
		BitmapTextureAtlasTextureRegionFactory.setAssetBasePath("gfx/");
		
		this.mBitmapTextureAtlas = new BuildableBitmapTextureAtlas(this.getTextureManager(), 512, 512);
		this.mRunAntsTR = BitmapTextureAtlasTextureRegionFactory.createFromAsset(this.mBitmapTextureAtlas, this, "run_ants.png");
		this.mClearAllTR = BitmapTextureAtlasTextureRegionFactory.createFromAsset(this.mBitmapTextureAtlas, this, "clear_all.png");
		
		try {
			this.mBitmapTextureAtlas.build(new BlackPawnTextureAtlasBuilder<IBitmapTextureAtlasSource, BitmapTextureAtlas>(0, 0, 0));
			this.mBitmapTextureAtlas.load();
		} catch (TextureAtlasBuilderException e) {
			Debug.e(e);
		}
		
		mFont = FontFactory.create(mEngine.getFontManager(),
				mEngine.getTextureManager(), 256, 256,
				Typeface.create(Typeface.DEFAULT, Typeface.NORMAL), 16f);
		mFontHeader = FontFactory.create(mEngine.getFontManager(),
				mEngine.getTextureManager(), 256, 256,
				Typeface.create(Typeface.DEFAULT, Typeface.NORMAL), 20f);
		mFont.load();
		mFontHeader.load();

		pOnCreateResourcesCallback.onCreateResourcesFinished();

	}

	@Override
	public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback)
			throws IOException {
		this.mEngine.registerUpdateHandler(new FPSLogger());

		this.mScene = new Scene();
		this.mScene.setBackground(new Background(Color.WHITE));
		
		// Initialise text objects
		float mHeaderY = 462;
		float mTitleY = 465;
		
		
		mTitle = new Text(60, mTitleY, mFontHeader, "Ant Colony", mEngine.getVertexBufferObjectManager());
		mCitiesText = new Text(150, mHeaderY, mFont, "Cities: ", 12, mEngine.getVertexBufferObjectManager());
		mShortestPathText = new Text(300, mHeaderY, mFont, "Best Path: ", 28, mEngine.getVertexBufferObjectManager());
		mTimerText = new Text(500, mHeaderY, mFont, "Timer(s): ", 16, mEngine.getVertexBufferObjectManager());
		mCyclesText = new Text(620, mHeaderY, mFont, "Cycles: ", 15, mEngine.getVertexBufferObjectManager());

		this.mScene.attachChild(mTitle);
		this.mScene.attachChild(mCitiesText);
		this.mScene.attachChild(mShortestPathText);
		this.mScene.attachChild(mTimerText);
		this.mScene.attachChild(mCyclesText);
		
		// initialise buttons
		mRunAntsButton = new Sprite(50, 25, mRunAntsTR, mEngine.getVertexBufferObjectManager());
		mClearAllButton = new Sprite(160, 25, mClearAllTR, mEngine.getVertexBufferObjectManager());
		
		mRunAntsButton.setScale(0.25f);
		mClearAllButton.setScale(0.25f);
		
		mRunAntsButton.setUserData(new String("Run Ants"));
		mClearAllButton.setUserData(new String("Clear All"));

		// bounding box
		Rectangle mboundBox = new Rectangle(CAMERA_WIDTH/2, CAMERA_HEIGHT/2, CAMERA_WIDTH, (float) (0.8 * CAMERA_HEIGHT), mEngine.getVertexBufferObjectManager());
		this.mScene.registerTouchArea(mRunAntsButton);
		this.mScene.registerTouchArea(mClearAllButton);
		
		this.mScene.attachChild(mRunAntsButton);
		this.mScene.attachChild(mClearAllButton);
		this.mScene.attachChild(mboundBox);
		
		this.mScene.setOnAreaTouchListener(this);
		this.mScene.setOnSceneTouchListener(this);
		this.mScene.setTouchAreaBindingOnActionDownEnabled(true); 
		
		// Create ACO object
		//mACO = new ACO();
		//mACO.initACO(this);

		pOnCreateSceneCallback.onCreateSceneFinished(mScene);
	}

	@Override
	public void onPopulateScene(Scene pScene,
			OnPopulateSceneCallback pOnPopulateSceneCallback)
			throws IOException {
		
		pOnPopulateSceneCallback.onPopulateSceneFinished();
	}

	@Override
	public boolean onAreaTouched(TouchEvent pSceneTouchEvent,
			ITouchArea pTouchArea, float pTouchAreaLocalX,
			float pTouchAreaLocalY) {
		if (pSceneTouchEvent.isActionDown()) {
			
			((Entity) pTouchArea).clearEntityModifiers();
			
			if (pTouchArea instanceof Sprite) {
				// button
				if (((Sprite) pTouchArea).getUserData().equals("Run Ants") && mCities.size() != 0) {
					Debug.i(TAG, "Run Ants button clicked");
					
					// is there already a ACO background thread running?
					if(mACOBkgdThread != null) {
						mACO.removeEdges();
						mACO = null;
						Thread dummy = mACOBkgdThread;
						mACOBkgdThread = null;
						dummy.interrupt();
					}
					
					mACOBkgdThread = new Thread(new Runnable() {

						@Override
						public void run() {
							mACO = new ACO();
							mACO.initACO(MainActivity.this);
							mACO.start(); // this previous hangs the main thread
						}

					});
					
					mTime = 0.0f;
					this.mScene.registerUpdateHandler(mTimerTextUpdate);

					mACOBkgdThread.start();
				}
				
				
				if (((Sprite) pTouchArea).getUserData().equals("Clear All")) {
					Debug.i(TAG, "Clear all button clicked");
					
					if(mACO != null) mACO.removeEdges();
					for (int i = mCities.size() - 1; i >= 0; i--) {
						mScene.detachChild(mCities.get(i));
						mCities.remove(i);
					}

				}
			}
		}

		
		return false;
	}

	@Override
	public boolean onSceneTouchEvent(Scene pScene, TouchEvent pSceneTouchEvent) {
		
		if (pSceneTouchEvent.isActionDown() && (pSceneTouchEvent.getY() > 0.1 * CAMERA_HEIGHT &&pSceneTouchEvent.getY() < 0.9 * CAMERA_HEIGHT) ) {
			// add a new CityRect
			Debug.i(TAG, "Scene touched");
			CityRect pRect = new CityRect(pSceneTouchEvent.getX(),
					pSceneTouchEvent.getY(), this);
			this.mCities.add(pRect);
			Debug.i(TAG, "Number of cities now: " + this.mCities.size());
		}
		return false;
	}
	
	
	public void unregisterTimerUpdateHandler() {
		this.mScene.unregisterUpdateHandler(mTimerTextUpdate);
	}
	
	
	IUpdateHandler mTimerTextUpdate = new IUpdateHandler() {

		@Override
		public void onUpdate(float pSecondsElapsed) {
			
			if (mACO != null) {
				mTime += pSecondsElapsed;
				mTimerText.setText("Time(s): " + (int) mTime);
			}
			
		}

		@Override
		public void reset() {
			// TODO Auto-generated method stub
			
		}
		
	};

}
