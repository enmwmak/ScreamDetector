package edu.polyu.screamalert;
/**
 * @author Leung, Wing-Lung Henry, The Hong Kong Polytechnic University.
 */
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

public class Setting extends PreferenceActivity {
    
	Activity thisActivity;
    Intent processingService = null;
    Handler handler = null;
    boolean newAPI;
    
	@SuppressLint("NewApi")
	@SuppressWarnings({ "deprecation" })
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        thisActivity = this;
		System.out.println("Calling onCreate in MainActivity");
        if (processingService == null && handler == null) {
            handler = new Handler();
        	processingService = new Intent(Setting.this, SoundProcessingService.class);               
			addPreferencesFromResource(R.layout.setting);
	    }
		final Preference about = findPreference("about");
        
        // Set callback function when the "About" preference is click
        about.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(thisActivity);
				aboutBuilder.setTitle(getString(R.string.abtTitle));
				aboutBuilder.setMessage("Developed by:" +
						"\n\n- Dr. Mai-Wai MAK\n- Wing-Lung Henry LEUNG\n- Baiying LEI" +
						"\n\nDept. of EIE, The Hong Kong Polytechnic University" );
				AlertDialog aboutDialog = aboutBuilder.create();
				aboutDialog.show();
				return true;
			}        	
        });
	}

}
