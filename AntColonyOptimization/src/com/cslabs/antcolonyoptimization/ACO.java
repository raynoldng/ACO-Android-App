package com.cslabs.antcolonyoptimization;

import java.util.ArrayList;
import java.util.Random;

import org.andengine.entity.primitive.Line;
import org.andengine.entity.primitive.Rectangle;
import org.andengine.entity.text.Text;
import org.andengine.util.adt.color.Color;
import org.andengine.util.debug.Debug;

public class ACO {

	private static final String TAG = "ACO";

	Random random = new Random(System.currentTimeMillis());

	public int num_cities;
	public int num_ants;

	public static final double LINE_SCALE = 0.0005;

	// ACO-equation variables
	// keep alpha,beta and rho within [0,1]
	float alpha_value = 1.0f; // higher value -> more scouting behaviour
	float beta_value = 1.0f; // higher value -> higher importance of distance
								// cost
	float rho = 0.8f; // decay rate
	float qval = 500; // amount of pherom to spread on tour.

	City cities[];
	Ant ants[];
	float precomp_distance[][]; // edge length, store distances between cities
	float pherom[][]; // pherom levels on each edge
	float prevPherom[][];
	float base_pherom; // minimum pherom level

	double best_tour; // length of best tour found
	int best_index = -1; // index of ants[] with best tour
	int best_tour_history[]; // save ant tour history here
	int prev_best_tour_history[];

	int iterations; // no of iterations used on ACO

	float iterationTimer = 0; // remember time
	float iterationTimeLength = 0.05f; // how fast to iterate in seconds
	int grabbedNode = -1;
	
	MainActivity mActivity;
	Line mCitiesEdges[][];
	private final float mLINE_THICKNESS_COEFFICENT = 10f;
	private final float mLINE_BEST_TOUR_COEFFICENT = 5f;


	void start() {

		int time = 0;
		int iter_counter = 0;
		while (time < (num_ants * num_cities)) {
			time++;

			if (moveAnts() == 0) { // all the ants stopped moving
				iter_counter++;
				
				evaporatePheromoneTrails();
				intensifyPheromoneTrails();
				backupPheromoneTrails();
				findBestTour();
				initAnts();

				// here should be hook to update the graphics
				updateGraphics(iter_counter);
				
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		// end of execution
		mActivity.unregisterTimerUpdateHandler();
	}

	void updateGraphics(int pTime) {
	
		/*
		 * update edges
		 * bolden best trail
		 * */
		updateEdges();
		updateBestTour();
		updateText(pTime);
	}

	

	
	private void updateEdges() {
		for (int from = 0; from < num_cities - 1; from++) {
			for (int to = from + 1; to < num_cities; to++) {
				Line pLine = mCitiesEdges[from][to];
				pLine.setLineWidth(pherom[from][to] * mLINE_THICKNESS_COEFFICENT);
				pLine.setColor(Color.BLACK);
			}
		}
	}

	private void updateBestTour() {
		
		int startCity = best_tour_history[0];
		int nextCity = 0;
		
		for(int i = 0; i < num_cities; i++) {
			nextCity = best_tour_history[i];
			Line pLine = mCitiesEdges[startCity][nextCity];
			pLine.setLineWidth(mLINE_BEST_TOUR_COEFFICENT);
			pLine.setColor(Color.GREEN);
			startCity = nextCity;
		}
		
		// close the loop
		startCity = best_tour_history[0];
		nextCity = best_tour_history[num_cities - 1];
		Line pLine = mCitiesEdges[startCity][nextCity];
		pLine.setLineWidth(mLINE_BEST_TOUR_COEFFICENT);
		pLine.setColor(Color.GREEN);
		
		// update shortest path text
		mActivity.mShortestPathText.setText("Best Path: " + String.format("%.2f", best_tour));
		
	}
	
	void updateText(int pTime) {
		
		mActivity.mCyclesText.setText("Cycles: " + pTime);
		
		
	}

	void initACO(MainActivity pActivity) {
		
		mActivity = pActivity;
		ArrayList<CityRect> mCityRects = mActivity.mCities;
		
		/*
		 * Many modifications to take place to allow
		 * for modifications
		 * */
		iterations = 0;
		//num_cities = 10;
		num_cities = mCityRects.size();
		Debug.i(TAG, "number of cities = " + num_cities);
		num_ants = (int) (mCityRects.size() * 1.2); 

		best_tour = 100000.0f;
		base_pherom = (float) (1.0 / num_cities);

		cities = new City[num_cities];
		ants = new Ant[num_ants];
		for (int i = 0; i < num_ants; i++) {
			ants[i] = new Ant();
		}

		precomp_distance = new float[num_cities][num_cities];
		pherom = new float[num_cities][num_cities];
		prevPherom = new float[num_cities][num_cities];
		best_tour_history = new int[num_cities];
		prev_best_tour_history = new int[num_cities];

		//initCities();
		initCities(mCityRects);
		initAnts();
		
		initCitiesEdges();
		initDraw();
		
	}

	void initCities() {
		// randomly place cities on the map
		for (int i = 0; i < num_cities; i++) {
			cities[i] = new City();
			cities[i].x = random.nextInt(720);
			cities[i].y = random.nextInt(480);
		}

		resetPherom();

		computeCityDistances();

	}
	
	void initCities(ArrayList<CityRect> pCityRects) {
		for (int i = 0; i < num_cities; i++) {
			cities[i] = new City();
			cities[i].x = pCityRects.get(i).getX();
			cities[i].y = pCityRects.get(i).getY();
		}

		resetPherom();

		computeCityDistances();
	}

	void computeCityDistances() {
		for (int from = 0; from < num_cities; from++) {
			for (int to = 0; to < num_cities; to++) {
				float dx = Math.abs(cities[from].x - cities[to].x);
				float dy = Math.abs(cities[from].y - cities[to].y);
				float distance = (float) Math.sqrt((dx * dx) + (dy * dy));
				precomp_distance[from][to] = distance;
				precomp_distance[to][from] = distance;
			}
		}
	}

	void resetPherom() {
		for (int from = 0; from < num_cities; from++) {
			for (int to = 0; to < num_cities; to++) {
				pherom[from][to] = base_pherom; 
				pherom[to][from] = base_pherom;
			}
		}
	}
	
	void initCitiesEdges() {
		mCitiesEdges = new Line[num_cities][num_cities];
		
		for(int from = 0; from < num_cities; from++) {
			for(int to = 0; to < num_cities; to++) {
				if(mCitiesEdges[from][to] != null) continue;
				
				float x1 = cities[from].x, 	y1 = cities[from].y;
				float x2 = cities[to].x, 	y2 = cities[to].y;
				Line pLine = new Line(x1, y1, x2, y2, mActivity.getVertexBufferObjectManager());
				pLine.setColor(Color.BLACK);
				
				mCitiesEdges[from][to] = pLine;
				mCitiesEdges[to][from] = pLine;
				
				mActivity.mScene.attachChild(pLine);
			}
		}
	}

	void findBestTour() {
		
		// save the values 
		for(int i  = 0; i < num_cities; i++) {
			prev_best_tour_history[i] = best_tour_history[i];
		}
		
		// linear search for possible shorter path
		for (int i = 0; i < num_ants; i++) {
			if (ants[i].tour_length < best_tour) {
				best_tour = ants[i].tour_length;
				best_index = i;

				for (int j = 0; j < num_cities; j++) // remember tour
				{
					best_tour_history[j] = ants[i].tour[j];
				}
			}
		}

		// System.out.println("New best length of " + best_tour);
	}

	void initAnts() {
		int city = 0;
		// place ants throughout the cities (evenly if possible)
		for (int i = 0; i < num_ants; i++) {

			for (int j = 0; j < num_cities; j++) {
				ants[i].tabu[j] = 0;
				ants[i].tour[j] = 0;
			}
			// place this ant in a city, and reflect it in the tabu
			ants[i].current_city = city; // (int)random(0,num_cities);//city;
			// ants[i].next_city = city; // will be set on choosenext
			city++;
			city %= num_cities;
			ants[i].tabu[ants[i].current_city] = 1;

			// update the tour, and current tour length given the current path
			ants[i].tour[0] = ants[i].current_city;
			ants[i].tour_index = 1;
			ants[i].tour_length = 0.0;
		}
	}

	void chooseNextCity(Ant ant) {
		double d = 0.0;
		double p = 0.0;

		int from = ant.current_city;
		int to = 0;

		for (to = 0; to < num_cities; to++) {
			if (ant.tabu[to] == 0) {
				// city not yet visited
				d += Math.pow(pherom[from][to], (float) alpha_value)
						* Math.pow((1.0 / precomp_distance[from][to]),
								(float) beta_value);
			}
		}

		// probabilistically select the next city
		to = 0;
		while (true) {
			if (ant.tabu[to] == 0) {
				p = Math.pow(pherom[from][to], (float) alpha_value)
						* Math.pow((1.0 / precomp_distance[from][to]),
								(float) beta_value) / d;
				if (random.nextDouble() <= p)
					break;
			}
			to++;
			to %= num_cities;
		}

		// we have our new destination, update for the new city
		ant.next_city = to;
		ant.tabu[ant.next_city] = 1; // mark as visited
		ant.tour[ant.tour_index] = ant.next_city; // update tour log
		ant.tour_index++;
		ant.tour_length += precomp_distance[ant.current_city][ant.next_city];

		// visited all cities, add distance from start to end.
		if (ant.tour_index == num_cities) {
			ant.tour_length += precomp_distance[ant.tour[num_cities - 1]][ant.tour[0]];
		}
		ant.current_city = ant.next_city; // !!!
	}

	int moveAnts() {
		int moved = 0;
		for (int i = 0; i < num_ants; i++) {
			if (ants[i].tour_index < num_cities) {
				chooseNextCity(ants[i]);
				moved++;
			}
		}
		return moved; // if we couldnt move, we have visited all. We need to
						// re-init. Return 0
	}

	void backupPheromoneTrails() {
		for (int from = 0; from < num_cities; from++) {
			for (int to = 0; to < num_cities; to++) {
				prevPherom[from][to] = pherom[from][to];
			}
		}
	}

	void evaporatePheromoneTrails() {
		for (int from = 0; from < num_cities; from++) {
			for (int to = 0; to < num_cities; to++) {
				// equation 14.4
				pherom[from][to] *= (1.0 - rho);
				if (pherom[from][to] < 0.0) {
					pherom[from][to] = base_pherom;
				}
			}
		}
	}

	void intensifyPheromoneTrails() {
		for (int i = 0; i < num_ants; i++) {
			for (int city = 0; city < num_cities; city++) {
				int from = ants[i].tour[city];
				int to = ants[i].tour[((city + 1) % num_cities)];

				// eq 14.2 / 14.3
				pherom[from][to] += (qval / ants[i].tour_length) * rho;
				pherom[to][from] = pherom[from][to];
			}
		}
	}
	
	public void initDraw() {
		// called when ACO is first created, draws the lines and 
//		Rectangle[] mGraphCities = new Rectangle[num_cities];
//		
//		int mCitySideLength = 25;
//		for(int i = 0; i < num_cities; i++) {
//			Rectangle rect = new Rectangle(cities[i].x, cities[i].y, mCitySideLength, mCitySideLength, mActivity.getVertexBufferObjectManager());
//			rect.setColor(Color.BLACK);
//			mActivity.mScene.attachChild(rect);
//			
//		}
		
		// initialise text
		mActivity.mCitiesText.setText("Cities: " + num_cities);
	}
	
	
	/**
	 * detached the edges (lines) from the scene
	 */
	public void removeEdges() {
		for(int from = 0; from < num_cities; from++) {
			for(int to = from + 1; to < num_cities; to++) {
				mActivity.mScene.detachChild(mCitiesEdges[to][from]);
			}
		}
	}
	

	// INNER CLASSES

	class City {
		float x, y;
	}

	class Ant {
		int current_city;
		int next_city;
		int tabu[];
		int tour_index;
		int tour[];
		double tour_length;

		Ant() {
			tabu = new int[num_cities];
			tour = new int[num_cities];
		}
	}


}
