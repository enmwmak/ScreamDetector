package edu.polyu.screamalert;
/**
 * @author Leung, Wing-Lung Henry, The Hong Kong Polytechnic University.
 */
import android.os.Bundle;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;

public class SoundProcessingSetting extends PreferenceActivity {
    
	Activity thisActivity;
    Intent processingService = null;
    Handler handler = null;
    
	@SuppressLint("NewApi")
	@SuppressWarnings({ "deprecation" })
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        thisActivity = this;
		System.out.println("Calling onCreate in MainActivity");
        if (processingService == null && handler == null) {
            handler = new Handler();
            processingService = new Intent(SoundProcessingSetting.this, SoundProcessingService.class);               
        		addPreferencesFromResource(R.layout.setting);							
        }
		final Preference about = findPreference("about");
        //
        // Set callback function when the "About" preference is click
        about.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(thisActivity);
				aboutBuilder.setTitle(getString(R.string.abtTitle));
				aboutBuilder.setMessage(R.string.credits);
				AlertDialog aboutDialog = aboutBuilder.create();
				aboutDialog.show();
				return true;
			}        	
        });   
                
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Exchanger.thisContext);
        ListPreference sensitivity = (ListPreference) findPreference("sensitivity");
        int genderPref = prefs.getInt("gender", 99);
        switch (genderPref) {
        	case 99:
        		sensitivity.setEnabled(false);
        		break;
        	case 1:
        		sensitivity.setEntryValues(Config.femaleDecTh);
        		break;
        	case 2:
        		sensitivity.setEntryValues(Config.maleDecTh);
        		break;
        }
        System.out.println("decisionTh: onCreate() "+prefs.getString("sensitivity", "0.0"));
                        
	}
		
}
