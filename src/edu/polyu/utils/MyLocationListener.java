package edu.polyu.utils;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class MyLocationListener implements LocationListener {

	private Context context;

	// flag for GPS status
	boolean isGPSEnabled = false;

	// flag for network status
	boolean isNetworkEnabled = false;

	Location currentLocation = null; // location
	double latitude = 0.0; // latitude
	double longitude = 0.0; // longitude

	// The minimum distance to change Updates in meters
	private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 5; // 5 meters

	// The minimum time between updates in milliseconds
	private static final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1; // 1 minute

	// Declaring a Location Manager
	protected LocationManager locationManager;

	public MyLocationListener(Context context) {
		this.context = context;
		this.getLocation();
	}

	public void getLocation() {
		try {
			locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

			// getting GPS status
			isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

			// getting network status
			isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

			if (!isGPSEnabled && !isNetworkEnabled) {
				// no network provider is enabled
			} else {
				// First get location from Network Provider
				if (isNetworkEnabled) {
					locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
					if (locationManager != null) {
						currentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
						if (currentLocation != null) {							
							latitude = currentLocation.getLatitude();
							longitude = currentLocation.getLongitude();
							System.out.printf("GPS: Network latitude = %f; longitude = %f\n",latitude,longitude);
						}
					}
				}
				// if GPS Enabled get lat/long using GPS Services
				if (isGPSEnabled) {
					if (currentLocation == null) {
						locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME_BW_UPDATES, MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
						if (locationManager != null) {
							currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
							if (currentLocation != null) {
								latitude = currentLocation.getLatitude();
								longitude = currentLocation.getLongitude();
								System.out.printf("GPS: GPS latitude = %f; longitude = %f\n",latitude,longitude);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Function to stop updating location
	public void stopUsingGPS() {
		if (locationManager != null) {
			locationManager.removeUpdates(MyLocationListener.this);
		}
	}

	// Function to return latitude
	public double getLatitude() {
		if (currentLocation != null) {
			latitude = currentLocation.getLatitude();
		}
		return latitude;
	}

	// Function to return longitude
	public double getLongitude() {
		if (currentLocation != null) {
			longitude = currentLocation.getLongitude();
		}
		return longitude;
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onLocationChanged(Location newLocation) {

		// Judge whether the newLocation is a better fix or not
		if (isBetterLocation(newLocation, currentLocation)) {
			currentLocation = null;
			currentLocation = newLocation;
		}
	}

	public void removeLocationUpdates() {
		locationManager.removeUpdates(this);
	}
	
	private final int TWO_MINUTES = 1000 * 60 * 2; // To judge whether the updated location is significantly newer than the currentLocation 

	protected boolean isBetterLocation(Location newLocation, Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = newLocation.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
		boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location, use the new location
		// because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDifference = (int) (newLocation.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isSignificantlyLessAccurate = accuracyDifference > 200;
		boolean isMoreAccurate = accuracyDifference < 0;

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate) {
			return true;
		} else if (isNewer && !isSignificantlyLessAccurate) {
			return true;
		}
		return false;
	}
}
