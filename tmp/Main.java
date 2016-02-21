package edu.polyu.screamalert;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class Main extends Activity{

	static Activity thisActivity;
	Intent processingService;
	Handler handler;
	boolean status;
	Button calibrateButton, onOffButton;
	Button enableBgService;
	EditText sysStatus;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		thisActivity = this;
		if (processingService == null && handler == null) {
	        handler = new Handler();
	      	processingService = new Intent(Main.this, SoundProcessingService.class);  
		}
		Exchanger.thisContext = this;					// For sharing context reference to other activities of this App
		status = false;
		calibrateButton = (Button) findViewById(R.id.calibrateButton);
		onOffButton = (Button) findViewById(R.id.onOffButton);
		enableBgService = (Button) findViewById(R.id.enableBgButton);
		sysStatus = (EditText) findViewById(R.id.sysStatus);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (isProcessingServiceRunning()) {
			status = true;
			calibrateButton.setEnabled(status);
			onOffButton.setText(R.string.onOffBg);
			onOffButton.setEnabled(false);
			enableBgService.setEnabled(true);
			enableBgService.setEnabled(true);
			sysStatus.setText(R.string.sysStatusService);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		status = false;
		calibrateButton.setEnabled(status);
		onOffButton.setText(R.string.onOffStart);
		sysStatus.setText(R.string.sysStatus);
	}
	
	public void changeCharacterClick(final View v){
		if (((Button)v).getText().equals(getString(R.string.femaleOrChild))){
			((Button)v).setText(R.string.male);
			AlertDialog.Builder characterAlert = new AlertDialog.Builder(Main.this);
			characterAlert
			.setMessage("Sorry, only female or child is supported at this moment.")
			.setNegativeButton("OK", new OnClickListener(){

				@Override
				public void onClick(DialogInterface dialog, int which) {
					((Button)v).setText(R.string.femaleOrChild);
				}
				
			})
			.setOnCancelListener(new OnCancelListener(){

				@Override
				public void onCancel(DialogInterface dialog) {
					((Button)v).setText(R.string.femaleOrChild);
				}
				
			});
			AlertDialog characterAlertDialog = characterAlert.create();
			characterAlertDialog.show();
		}
		else
			((Button)v).setText(R.string.femaleOrChild);
	}
	
	public void calibrateClick(View v){
		if (status) {
			if (isProcessingServiceRunning()) {
				enableBgService.setEnabled(false);
				System.out.println("Stopping sound processing service");
				if (isProcessingServiceRunning()) {
					stopService(processingService);
				}
				handler.post(new Runnable() {
					@Override
					public void run() {
						while (isProcessingServiceRunning());
						calibrateButton.setEnabled(false);
						enableBgService.setText(R.string.bgService);
						onOffButton.setText(R.string.onOffStart);
						onOffButton.setEnabled(true);
						sysStatus.setText(R.string.sysStatus);
						
						status = true;
						calibrateButton.setEnabled(status);
						onOffButton.setText(R.string.onOffStop);
						SoundProcessing.startRecord(thisActivity, false);
						enableBgService.setEnabled(true);
						sysStatus.setText(R.string.sysStatusStarted);
						
						handler.postDelayed(new Runnable() {

							@Override
							public void run() {
								SoundProcessing.stopRecording();
								startService(processingService);
								enableBgService.setText(R.string.onOffStop);
								onOffButton.setText(R.string.onOffBg);
								onOffButton.setEnabled(false);
								sysStatus.setText(R.string.sysStatusService);
							}
							
						}, 6000);
					}
				});
			}
			else {
				status = false;
				calibrateButton.setEnabled(status);
				onOffButton.setText(R.string.onOffStart);
				SoundProcessing.stopRecording();
				enableBgService.setEnabled(false);
				sysStatus.setText(R.string.sysStatus);
				
				status = true;
				Exchanger.thisContext = this;
				calibrateButton.setEnabled(status);
				onOffButton.setText(R.string.onOffStop);
				SoundProcessing.startRecord(Exchanger.thisContext, false);
				enableBgService.setEnabled(true);
				sysStatus.setText(R.string.sysStatusStarted);
			}
		}
	}
	
	public void onOffClick(View v){
		if (((Button)v).getText().equals(getString(R.string.onOffStart))) {	//start button pressed
			status = true;
			Exchanger.thisContext = this;
			calibrateButton.setEnabled(status);
			((Button)v).setText(R.string.onOffStop);
			SoundProcessing.startRecord(Exchanger.thisContext, false);
			enableBgService.setEnabled(true);
			sysStatus.setText(R.string.sysStatusStarted);
		}
		else {	//stop button pressed
			status = false;
			calibrateButton.setEnabled(status);
			((Button)v).setText(R.string.onOffStart);
			SoundProcessing.stopRecording();
			enableBgService.setEnabled(false);
			sysStatus.setText(R.string.sysStatus);
		}
	}
	
	public void bgClick(final View v){
		if (((Button)v).getText().equals(getString(R.string.bgService))) { //Run in Background pressed
			if (!isProcessingServiceRunning()) {
				SoundProcessing.stopRecording();
				startService(processingService);
				((Button)v).setText(R.string.onOffStop);
				onOffButton.setText(R.string.onOffBg);
				onOffButton.setEnabled(false);
				sysStatus.setText(R.string.sysStatusService);
			}
		}
		else {
			v.setEnabled(false);
			System.out.println("Stopping sound processing service");
			if (isProcessingServiceRunning()) {
				stopService(processingService);
			}
			handler.post(new Runnable() {
				@Override
				public void run() {
					while (isProcessingServiceRunning());
					calibrateButton.setEnabled(false);
					((Button)v).setText(R.string.bgService);
					onOffButton.setText(R.string.onOffStart);
					onOffButton.setEnabled(true);
					sysStatus.setText(R.string.sysStatus);
				}
			});
		}
	}
	
	public void settingClick(View v){
		Intent settingActivity = new Intent(this, Setting.class);
		startActivity(settingActivity);
	}
	
	public boolean isProcessingServiceRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if ((thisActivity.getPackageName()+".SoundProcessingService").equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
}
