/*
 * Authors: Man-Wai MAK and Wing-Lung Henry LEUNG, Dept. of EIE, The Hong Kong Polytechnic University.
 * Version: 1.0
 * Date: March 2015
 * 
 * This file is subject to the terms and conditions defined in
 * file 'license.txt', which is part of this source code package.
 *  
 */

package edu.polyu.screamalert;
import edu.polyu.utils.WaveformView;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SoundProcessingActivity extends Activity{

	static Activity thisActivity;
	static Intent processingService;
	static Handler handler;
	boolean statusStart;									// == true if the recorder has started to record 
	static Button calibrateButton;
	static Button startStopButton;
	static Button enableBgService;
	static WaveformView waveformView;
	static EditText sysStatus;
	static TextView energyTextView;
	static Spinner spinner;								// Character can be "male" or "female/child"
	static SharedPreferences prefs;
	static int spinnerPos = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		thisActivity = this;
		if (processingService == null && handler == null) {
	        handler = new Handler();
	      	processingService = new Intent(this, SoundProcessingService.class);  
		}
		Exchanger.thisContext = this;					// For sharing context reference to other activities of this App
		statusStart = false;
		calibrateButton = (Button) findViewById(R.id.calibrateButton);
		startStopButton = (Button) findViewById(R.id.startStopButton);
		enableBgService = (Button) findViewById(R.id.enableBgButton);
		waveformView = (WaveformView) findViewById(R.id.waveform_view);
		sysStatus = (EditText) findViewById(R.id.sysStatus);
		energyTextView = (TextView) findViewById(R.id.energy);
		spinner = (Spinner) findViewById(R.id.characterSpinner);	// For selecting male or female (child)
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);		
		int genderPref = prefs.getInt("gender", 99);	// Return 99 if "gender" has not been set
		if (genderPref != 99) 
			spinner.setSelection(genderPref);		// Not first-time-run after installation. Set to the previous selection
		else
			startStopButton.setEnabled(false);		// First-time-run, allow Spinner.OnItemSelectedListener to select gender. 
													// Do not allow user to start the recorder

		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				setGenderAndSensitivity(position, id);					
			}
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
				// Nothing to do with this method
			}
			
		});
		if (prefs.getInt("gender", 99) == 99) {
			startStopButton.setEnabled(false);		// Force the user to select gender first
		}
		SoundProcessing.initialize(this);			// Initialize objects in SoundProcessing objects. Only need to do once.
	}

	/*
	 * The variable 'position' passed to onItemSelected() is 0 if this is the first time the app is 
	 * run after installation. As "gender" has not been set, SharedPreference.getInt() will 
	 * return the default (99). A dialog will be pop-up reminding the user to select gender. 
	 * The user can go to the main activity of the app by pressing the "Back" button on the device. 
	 * Then, the user can choose gender through the Spinner.
	 * 'position' not equal to 0 means that the user has chosen the gender (either through this or previous runs).
	 * If sensitivity has not been set, a dialog will be pop-up requesting user to choose sensitivity. 
	 */		
	private void setGenderAndSensitivity(int position, long id) {
		if (position == 0) {		
			int genderPref = prefs.getInt("gender", 99);
			if (genderPref == 99) {
				AlertDialog.Builder selectCharacter = new AlertDialog.Builder(thisActivity);
				selectCharacter.setTitle(getString(R.string.selectCharacterTitle));	// Title: Welcome
				selectCharacter.setMessage(R.string.selectCharacterMsg);				// Msg: Please select your gender to start
				AlertDialog selectCharacterDialog = selectCharacter.create();			// Create the select-gender dialog
				selectCharacterDialog.show();
			} else {		// Something wrong if the program enters this block because if position is 0 characterPref must be 99
				AlertDialog.Builder invalidCharacter = new AlertDialog.Builder(thisActivity);
				invalidCharacter.setTitle(getString(R.string.invalidSelection));		
				invalidCharacter.setMessage(R.string.invalidSelectMsg);					
				AlertDialog invalidCharacterDialog = invalidCharacter.create();
				invalidCharacterDialog.show();
				startStopButton.setEnabled(false);
			}
		} else {		
			/* 
			 * Gender has been selected. So, get the selection from Spinner and set the key "gender".
			 * If there is no change in gender, the sensitivity dialog will not be shown. Otherwise, the
			 * dialog will ask user to select sensitivity. Note that when the first time the app is run, 
			 * the app considers that the gender has been changed. So, the dialog will be shown.
			 */
			prefs.edit().putInt("gender", spinner.getSelectedItemPosition()).commit();
        		boolean shouldShowSensitivityDialog = true;
			AlertDialog.Builder sensitivitySelector = new AlertDialog.Builder(SoundProcessingActivity.this);
            sensitivitySelector.setIcon(R.drawable.ic_launcher);
            sensitivitySelector.setTitle("Sensitivity of Detection");
            sensitivitySelector.setCancelable(false);
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(SoundProcessingActivity.this,
            									android.R.layout.select_dialog_singlechoice);
            arrayAdapter.add("High");					// Only 3 entries for the Adaptor: "High", "Medium" and "Low"
            arrayAdapter.add("Medium (Default)");
            arrayAdapter.add("Low");
            String th[] = null;		            
            switch (position) {
				case 1:
					Exchanger.SVM_PARA_FILE = Config.SVM_PARA_FILE_FEMALE;
                		th = Config.femaleDecTh;
                		break;
				case 2:
					Exchanger.SVM_PARA_FILE = Config.SVM_PARA_FILE_MALE;
                		th = Config.maleDecTh;
                		break;
            }
            final String thresholds [] = th;           
            /*
             * Display "High", "Medium", and "Low" in the dialog and set up listener to save user selection to 
             * SharedPreferences object (prefs)
             */
            sensitivitySelector.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
		        @Override
		        public void onClick(DialogInterface dialog, int which) {
		        		prefs.edit().putString("sensitivity", thresholds[which]).commit();
		        		System.out.println("decisionTh: "+prefs.getString("sensitivity", "0.0"));
		        }
			});
			if (prefs.getInt("gender", 0) == spinnerPos) {
				shouldShowSensitivityDialog = false;				// No change in gender, no need to show sensitivity diag
			}
			if (shouldShowSensitivityDialog) {
				spinnerPos = prefs.getInt("gender", 0);			// Gender changed, set instance member and show
				sensitivitySelector.show();						// sensitivity selection dialog
			}
            if (!isProcessingServiceRunning())					// If the sound processing service is not running,
            		startStopButton.setEnabled(true);				// user can click the "Start" button to start the service
		}
	}	
	
	/*
	 * The system calls onResume() every time the activity comes into the foreground, 
	 * including when it's created for the first time. If the user put the sound processing service
	 * to run in the background (isProcessingServiceRunning() returns true),
	 * gender selection should be disabled until the user stops the service. When the sound 
	 * processing service is run in the background and the app is resumed to the foreground, 
	 * the user is allowed to perform calibration (by pressing the "Calibrate" button) or 
	 * stop the service (by pressing the "Stop" button).
	 */	
	@Override
	protected void onResume() {
		super.onResume();		
		if (isProcessingServiceRunning()) {
			statusStart = true;								// App resumed and sound processing service is run in the background
			spinner.setEnabled(false);						// Gender selection is disable
			calibrateButton.setEnabled(false);				// Not allow to calibrate when background process is running.
			startStopButton.setText(R.string.runningInBkg);	// Start/Stop button display "Running in background"
			startStopButton.setEnabled(false);				// Make "Running in background" dim
			enableBgService.setEnabled(true);
			enableBgService.setText(R.string.startStopStop);	// Display "Stop" in the "Run in background" button
			sysStatus.setText(R.string.sysStatusBackground);	// Display "Running in background" in status bar at the bottom		
			Exchanger.isBackgroundMode = true;
		} else {
			statusStart = false;								// App resumed but sound processing service is NOT running in the background
			spinner.setEnabled(true);						// Allow user to select gender
			calibrateButton.setEnabled(false);				// Not allow to calibrate because sound processing is not running (stop in onPause())
			startStopButton.setText(R.string.startStopStart);// Display "Start" in the Start/Stop button 
			if (prefs.getInt("gender", 99) == 99)
				startStopButton.setEnabled(false);			// Gender not selected yet, force the user to select gender first
			else
				startStopButton.setEnabled(true);			// Gender selected. Allow user to start recording
			enableBgService.setEnabled(false);				// Service not started yet. So, dim the button "Run in background" 
			enableBgService.setText(R.string.runInBg);		// Display "Run in background"
			sysStatus.setText(R.string.sysStatusIdle);		// Display "Idle" in system status bar	
			Exchanger.isBackgroundMode = false;
			energyTextView.setText(R.string.energy);			// Display "Waveform energy" below the waveform window
		}
	}
	
	/*
	 * The activity is paused and going into the background, but has not (yet) been killed. One way of putting the
	 * activity (not the sound recording service) to pause and to run in the background is to press the Home 
	 * button on the device. The home screen will be display but the app is still running in the background.
	 * In the current implementation, the sound recording service will be stopped if the App is paused, unless the 
	 * user has already put the serivce to run in the background by clicking "Run in background" button.
	 */
	@Override
	protected void onPause() {
		super.onPause();
		statusStart = false;
		spinner.setEnabled(true);							// Allow gender selection
		calibrateButton.setEnabled(false);					// Disallow calibration
		startStopButton.setText(R.string.startStopStart);
		enableBgService.setEnabled(false);					// Recording not started yet, so disable background processing
		sysStatus.setText(R.string.sysStatusIdle);
		if (!isProcessingServiceRunning()) {
			SoundProcessing.stopRecording();					// Background sound recording serive is not running. So, we stop the foreground recording
		}
	}
	
	/*
	 * This function will be executed when the "Calibrate" button is pressed (if it is enabled). The
	 * function will stop the recorder running in the foreground and start it again and call 
	 * SoundProcessing.setupVAD to recalculate the energy threshold for voice activity detection.
	 * During calibration, the user should not speak to the phone.  
	 */
	public void calibrateClick(View v){
		if (statusStart == false) {			
			return;											// Should not perform calibration if status is false. Just in case
		} 													// the user press the Calibration button once it has been enable in the code below

		/*
		 * Null pointer error occurs when the process is running in the background while performing calibration. 
		 * To avoid this error, users are not allowed to perform calibration while the process is running in the background
		 * (see code in onResume() above). Therefore, the Runnable.run() method will not be executed in this version, 
		 * i.e., the else clause will always be executed.
		 */
		if (isProcessingServiceRunning()) {
			enableBgService.setEnabled(false);				// Program can jump to here because the service is running and Calibrate button is pressed.
			stopService(processingService);					// Stop the service so that we can re-estimate the VAD again 
			handler.post(new Runnable() {					// Post the following runnable to start recording with VAD determination
				@Override
				public void run() {
					while (isProcessingServiceRunning()) {
					}
					System.out.println("calibrateClick: Sound processing service stopped");
					calibrateButton.setEnabled(false);
					calibrateButton.setEnabled(true);
					startStopButton.setEnabled(true);
					startStopButton.setText(R.string.startStopStop);			// Sound processing will run in foreground. So, allow user to stop			
					Exchanger.vadThUpdated = false;
					SoundProcessing.startRecord(thisActivity, false);		// false ==> will execute SoundProcessing.setupVAD()
					enableBgService.setEnabled(true);						// Allow user to put run sound processing in background
					enableBgService.setText(R.string.runInBg);				// Display "Run in background"
					Exchanger.isBackgroundMode = false;						// sound processing runs in foreground after calibration
					sysStatus.setText(R.string.sysStatusForeground);
				}
			});
		} else {
			statusStart = false;
			calibrateButton.setEnabled(false);
			startStopButton.setText(R.string.startStopStart);
			SoundProcessing.stopRecording();
			enableBgService.setEnabled(false);
			sysStatus.setText(R.string.sysStatusIdle);
			statusStart = true;
			calibrateButton.setEnabled(true);
			startStopButton.setText(R.string.startStopStop);
			SoundProcessing.startRecord(thisActivity, false);				// This will cause setupVAD() to execute
			enableBgService.setEnabled(true);								// Allow user to put the recording service to the background
			sysStatus.setText(R.string.sysStatusForeground);					// Display "Running in foreground" in status bar
		}
	}

	/*
	 * If the user has put the recording service to run in the background (by pressing the "Run in Background" 
	 * button), the text of this button will be changed to "Run in Background" and is disable. 
	 * Therefore, the else clause will only be executed if the service is not running in the foreground.
	 */
	public void startStopClick(View v){
		if (((Button)v).getText().equals(getString(R.string.startStopStart))) {	
			statusStart = true;												// start button pressed
			spinner.setEnabled(false);
			calibrateButton.setEnabled(true);
			((Button)v).setText(R.string.startStopStop);						// Change text to "Stop" in this button so that user can stop recording
			SoundProcessing.startRecord(thisActivity, false);				// Cause setupVAD() to run
			enableBgService.setEnabled(true);
			sysStatus.setText(R.string.sysStatusForeground);
		} else {	
			statusStart = false;												// "Stop" button pressed
			spinner.setEnabled(true);
			calibrateButton.setEnabled(false);								// No calibration if not started yet
			((Button)v).setText(R.string.startStopStart);					// Change text to "Start" in this button so that user can start recording
			SoundProcessing.stopRecording();
			enableBgService.setEnabled(false);								// Not allow to run in bkg if not start
			sysStatus.setText(R.string.sysStatusIdle);
		}
	}
	
	/*
	 * If the user has pressed "Run in Background", the text of this button will be changed to "Stop".
	 * Therefore, the if clause will only be executed once. All other clicks on this button will cause the
	 * else clause to execute, i.e., stopping the recording service.
	 */		
	public void bgClick(final View v){
		if (((Button)v).getText().equals(getString(R.string.runInBg))) { 
			if (!isProcessingServiceRunning()) {								// "Run in Background" pressed
				Toast.makeText(thisActivity, "Starting sound processing service", Toast.LENGTH_LONG).show();
				Exchanger.isBackgroundMode = true;
				SoundProcessing.stopRecording();
				startService(processingService);								// Start service in background
				((Button)v).setText(R.string.startStopStop);					// Display "Stop" in this button
				startStopButton.setText(R.string.runningInBkg);
				startStopButton.setEnabled(false);
				calibrateButton.setEnabled(false);							// No calibration is allowed when the service is running in background
				sysStatus.setText(R.string.sysStatusBackground);				// Display "Running in background" in status bar
			}
		}
		else {																// Service is not running in background
			v.setEnabled(false);												// We are going to stop the service. So, should disable this button									
			Toast.makeText(thisActivity, "Stopping sound processing service", Toast.LENGTH_LONG).show();
			if (isProcessingServiceRunning()) {
				stopService(processingService);								// Stop the background service
			}
			handler.post(new Runnable() {									// Post a Runnable to wait for the service to stop. Then, we may
				@Override													// enable or disable various buttons for the user to start the
				public void run() {											// recorder again
					while (isProcessingServiceRunning()) {
						// Wait until the service has stopped
					}
					spinner.setEnabled(true);								// Allow gender selection while no recorder is running
					calibrateButton.setEnabled(false);						// Do not allow calibration because no process is running
					((Button)v).setText(R.string.runInBg);
					startStopButton.setText(R.string.startStopStart);		// Display "Start" in the Start/Stop button
					startStopButton.setEnabled(true);						// Allow user to start recording
					sysStatus.setText(R.string.sysStatusIdle);
				}
			});
		}
	}
	
	/*
	 * Button "Advanced Setting" is pressed. Start the settingActivity to change settings.
	 */
	public void settingClick(View v){
		Intent settingActivity = new Intent(this, SoundProcessingSetting.class);
		startActivity(settingActivity);
	}
	
	/*
	 * Return true if the sound processing service is running
	 */
	public boolean isProcessingServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if ((thisActivity.getPackageName()+".SoundProcessingService").equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public static void enterBackgroundMode() {
		SoundProcessing.stopRecording();
		thisActivity.startService(processingService);
		enableBgService.setText(R.string.startStopStop);
		startStopButton.setText(R.string.runningInBkg);
		startStopButton.setEnabled(false);
		sysStatus.setText(R.string.sysStatusBackground);
	}
	
}
