package edu.polyu.screamalert;
/**
 * @author Leung, Wing-Lung Henry, The Hong Kong Polytechnic University.
 */

import org.json.JSONArray;

import android.content.Context;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;

/*
 * Sharing information between SoundProcessingService, SoundProcessingActivity, and SoundProcessing
 */
public class Exchanger {
	public static Context thisContext;
	public static double vadThreshold;
	public static String SVM_PARA_FILE;
	public static boolean vadThUpdated = false;			// Set to true after the vadThreshold has been updated.
	public static boolean isBackgroundMode;
	public static LocationListener locationListener;
	public static LocationManager locationManager;
	public static Criteria criteria;
	public static JSONArray locationResponse;
}
