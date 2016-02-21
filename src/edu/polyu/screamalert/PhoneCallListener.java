package edu.polyu.screamalert;

import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneCallListener extends PhoneStateListener {
	private boolean isPhoneCalling = false;
	String LOG_TAG = "Phonecall";
	@Override
	public void onCallStateChanged(int state, String incomingNumber) {
		Context context = Exchanger.thisContext;
		if (TelephonyManager.CALL_STATE_RINGING == state) {
			// phone ringing
			Log.i(LOG_TAG, "RINGING, number: " + incomingNumber);
		}
		if (TelephonyManager.CALL_STATE_OFFHOOK == state) {
			// active
			Log.i(LOG_TAG, "OFFHOOK");
			isPhoneCalling = true;
		}
		if (TelephonyManager.CALL_STATE_IDLE == state) {
			// run when class initial and phone call ended, 
			// need detect flag from CALL_STATE_OFFHOOK
			Log.i(LOG_TAG, "IDLE");
			if (isPhoneCalling) {
				Log.i(LOG_TAG, "restart app");
				Intent i = context.getPackageManager()
					.getLaunchIntentForPackage(context.getPackageName());
				i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				context.startActivity(i);
				isPhoneCalling = false;
			}
		}
	}
}

