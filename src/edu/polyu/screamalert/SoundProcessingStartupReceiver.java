package edu.polyu.screamalert;
/**
 * @author Leung, Wing-Lung Henry, The Hong Kong Polytechnic University.
 */
import edu.polyu.screamalert.AlarmReceiver;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/*
 * This class has been registered in AndroidManifest.xml. The onReceive()
 * method will be called after booting has been completed.
 */
public class SoundProcessingStartupReceiver extends BroadcastReceiver {
	
	//This is to be invoked of Android startup
	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.getBoolean("autoLaunch", false)) {		// Retrieve the boolean value of "autoLaunch"
			AlarmReceiver.acquireLock(context);
			Intent processingService = new Intent(context, SoundProcessingService.class);
			context.startService(processingService);
		}
	}
	
}
