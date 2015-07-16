/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.mygdx.game;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.linearmath.LinearMath;
import com.badlogic.gdx.physics.bullet.linearmath.btIDebugDraw.DebugDrawModes;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** @author xoppa */
public class BaseBulletTest extends BulletTest 
{
	// Set this to the path of the lib to use it on desktop instead of default lib.
	private final static String customDesktopLib = null;//"D:\\Xoppa\\code\\libgdx\\extensions\\gdx-bullet\\jni\\vs\\gdxBullet\\x64\\Debug\\gdxBullet.dll";

	private static boolean initialized = false;
	
	public static boolean shadows = true;
	
	public static void init () 
	{
		if (initialized) return;
		// Need to initialize bullet before using it.
		if (Gdx.app.getType() == ApplicationType.Desktop && customDesktopLib != null) 
		{
			System.load(customDesktopLib);
		} else
			Bullet.init();
		Gdx.app.log("Bullet", "Version = " + LinearMath.btGetVersion());
		initialized = true;
	}

	public Environment environment;
	public DirectionalLight light;
	public ModelBatch shadowBatch;
	public Matrix4 character;
	public SkyDome sky;
	private TerrainManager terrainManager;
	
	public final Vector3 tmp = new Vector3();

	public BulletWorld world;
	public ObjLoader objLoader = new ObjLoader();
	public ModelBuilder modelBuilder = new ModelBuilder();
	public ModelBatch modelBatch;
	public Array<Disposable> disposables = new Array<Disposable>();
	private int debugMode = DebugDrawModes.DBG_NoDebug;
	private AndroidController controller;
	private boolean android;
	private Vector3 playerPosition = new Vector3();
	
	protected final static Vector3 tmpV1 = new Vector3(), tmpV2 = new Vector3();

	public BulletWorld createWorld () 
	{
		return new BulletWorld();
	}
	
	public boolean isAndroid(){
		return android;
	}
	
	public AndroidController getController()
	{
		return controller;
	}

	@Override
	public void create () 
	{
		init();
		
		// Find out if we are playing on desktop or android
		switch(Gdx.app.getType())
		{
			case Android:
				android = true;
				controller = new AndroidController();
			default:
				Gdx.input.setCursorCatched(true);
				break;
		}

		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1.f));
		light = shadows ? new DirectionalShadowLight(1024, 1024, 20f, 20f, 1f, 300f) : new DirectionalLight();
		light.set(0.8f, 0.8f, 0.8f, -0.5f, -1f, -0.5f);
		environment.add(light);
		if (shadows)
			environment.shadowMap = (DirectionalShadowLight)light;
		shadowBatch = new ModelBatch(new DepthShaderProvider());
		
		modelBatch = new ModelBatch();

		world = createWorld();
		world.performanceCounter = performanceCounter;
		
		final float width = Gdx.graphics.getWidth();
		final float height = Gdx.graphics.getHeight();
		if (width > height)
			camera = new ChaseCamera(67f, 3f * width / height, 3f);
		else
			camera = new ChaseCamera(67f, 3f, 3f * height / width);
		camera.near = 0.01f;
		camera.far = 5000f;
		camera.update();

		terrainManager = new TerrainManager(playerPosition, this);
		sky = new SkyDome(this);
	}

	@Override
	public void dispose () 
	{
		world.dispose();
		world = null;

		for (Disposable disposable : disposables)
			disposable.dispose();
		disposables.clear();

		modelBatch.dispose();
		modelBatch = null;

		shadowBatch.dispose();
		shadowBatch = null;

		if (shadows)
			((DirectionalShadowLight)light).dispose();
		light = null;

		super.dispose();
		controller.dispose();
	}

	@Override
	public void render () 
	{
		render(true);
	}

	public void render (boolean update) 
	{
		if (update) update();

		beginRender(true);
		
		renderWorld();

		Gdx.gl.glDisable(GL30.GL_DEPTH_TEST);
		//if (debugMode != DebugDrawModes.DBG_NoDebug) 
			world.setDebugMode(debugMode);
		Gdx.gl.glEnable(GL30.GL_DEPTH_TEST);
		
		// Only need to update controller if on android
		if(controller != null)
			controller.update();
	}
	
	public void setCharacter(Matrix4 charMatrix)
	{
		character = charMatrix;
	}

	protected void beginRender (boolean lighting) 
	{
		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClearColor(0, 0, 0, 0);
		Gdx.gl.glClear(GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT);
		
		character.getTranslation(playerPosition);
		terrainManager.update(playerPosition);
		
		camera.transform.set(character);
		camera.update();
	}


	protected void renderWorld () 
	{
		if (shadows) 
		{
			((DirectionalShadowLight)light).begin(Vector3.Zero, camera.direction);
			shadowBatch.begin(((DirectionalShadowLight)light).getCamera());
			world.render(shadowBatch, null);
			shadowBatch.end();
			((DirectionalShadowLight)light).end();
		}

		modelBatch.begin(camera);
		world.render(modelBatch, environment);
		modelBatch.end();
	}

	public void update () 
	{
		world.update();
	}
}