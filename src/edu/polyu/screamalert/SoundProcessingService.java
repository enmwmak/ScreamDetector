package edu.polyu.screamalert;
/**
 * @author LEUNG Wing-Lung Henry and MAK Man-Wai, The Hong Kong Polytechnic University.
 */
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class SoundProcessingService extends Service{

	
	
    // Methods of this Android service
	@Override
	public void onCreate() {
		System.out.println("serviceCreated");
		Exchanger.thisContext = this;					// For sharing context reference to other activities of this App
		
		//SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		//Log.i("Pref Log", "Preference: " + pref.getBoolean("onOffSwitch",false));
	}

	@Override
	public IBinder onBind(Intent intnet) {
		return null;
	}
	
	/*
	 * The system destroys the service when the MainActivity calls stopService() Nobinding in this app.
	 */
	@Override
	public void onDestroy() {
		SoundProcessing.stopRecording();
		stopForeground(true);
	}
	
	/*
	 * Called by the system when MainActivity.java executes startService(processingService);
	 * This method may be called multiple times
	 */	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		AlarmReceiver.releaseLock();
		
		/** Test SMS functionality */
		//sendSMS();
		
		// add PhoneStateListener
		PhoneCallListener phoneListener = new PhoneCallListener();
		TelephonyManager telephonyManager = (TelephonyManager) this
			.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(phoneListener,PhoneStateListener.LISTEN_CALL_STATE);
		SoundProcessing.startRecord(Exchanger.thisContext, true);	//true because recording has been started in foreground
		
		// Running the service in the foreground during development stage
		startForeground(1, SoundProcessing.noti);

		// The service should only remain running while processing any commands sent to it.
		return Service.START_NOT_STICKY;	
	}
	
	

}
