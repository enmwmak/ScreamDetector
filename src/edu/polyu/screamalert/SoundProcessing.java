/*
 * Authors: Man-Wai MAK and Wing-Lung Henry LEUNG, Dept. of EIE, The Hong Kong Polytechnic University.
 * Version: 1.0
 * Date: March 2015
 * 
 * This file is subject to the terms and conditions defined in
 * file 'license.txt', which is part of this source code package.
 */

package edu.polyu.screamalert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.buffer.CircularFifoBuffer;

import edu.polyu.mfcc.MFCC;
import edu.polyu.pitch.VoiceQuality;
import edu.polyu.pitch.YinPitchDetector;
import edu.polyu.svm.SVMdetector;
import edu.polyu.utils.GMailSender;
import edu.polyu.utils.MyLocationListener;
import edu.polyu.utils.Vec;
import edu.polyu.utils.Wave;
import edu.polyu.vad.VAD;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class SoundProcessing {

	/* Android objects */
	static Notification noti;
	static Context thisContext;
	static NotificationManager notificationManager;
	static ProgressDialog calibrateDialog;
	static int calibrateProgress;
	static Intent mainIntent;
	static PendingIntent pIntent;
	static Handler handler;
	
	/* Variables related to audio recording */
	static AudioRecord recorder;
	static boolean isRecording = false;
 	static int recordBufferSize;									// Device-dependent record buffer size in bytes; 1280 on Galaxy S4 and Nexus 5;
																// 4096 on Galaxy W; 8192 on Zopo
	static int startFrm;
	static Thread recordingThread;								// Runnable thread for recording audio
	static Buffer frmBuf;										// Buffer storing K latest frames of audio signals */

	/* Variables related to time-domain samples */
	static short[] shortData;									// 16bit audio samples
	static int numFrames;
	static ArrayList<short[]> bufferedShortData;					// Multiple frames of audio samples
	static int curFrm;											// Current analysis frame

	/* Variables related to SVM classifier */
	static String svmType;										// Type of SVM, here we use "RbfSVM"	
	static String paraFile;										// Parameter file of SVM detector
	static SVMdetector svmd;

	/* Variables and circular buffers related to MFCC */
	static int K = 5;											// No. of mfcc vectors for computing delta mfcc (must be odd number)
	static int numMfcc = Config.NUM_MFCC;						// No. of MFCCs (excluding delta and delta-delta) per frames
	static double frameRate = Config.FRAME_RATE;					// Frame rate in Hz
	static int samplePerFrm = Config.SAMPLE_PER_FRM;				// Frame size
	static int frameShift = (int) (Config.RECORDER_SAMPLERATE / frameRate);		// (16000/125) = 128
	static int nSubframePerBuf;									// No. of subframes per recording buffer (device-dependent)
	static int nSubframePerMfccFrame = samplePerFrm / frameShift;	// 512/128 = 4
	static float[] x;
	static float[] subx;											// Samples in a sub-frame
	static ArrayList<float[]> subframeList;						// Contains a set of subframes to be processed
	static MFCC mfcc;
	static Buffer ccBuf;											// Buffer storing K mfcc vectors
	static Buffer dccBuf;										// Buffer storing K delta-mfcc vectors

	/* Pitch detector and circular buffer for estimating jitter and shimmer features */
	static YinPitchDetector pDet;
	static Buffer piBuf;
	static Buffer pkBuf;
	static double mu_z[];
	static double sigma_z[];

	/* Storage for acoustic vectors that contain sound */
	static ArrayList<double[]> aList;	

	/* Variables related to VAD and sound event detection */
	static int minNumSndFrms = Config.MIN_NUM_SND_FRMS;      		// Minimum no. of subframes in a sound event 
	static double vadThreshold;									// VAD threshold and energy of sound frames for estimating the threshold
	static short[] vadShortData;									// Samples for estimating the VAD threshold
	static ArrayList<short[]> vadBufferedShortData;
	static ArrayList<Double> vadBufEnergy;						// Array containing the energy profile for estimating the VAD threshold
	static boolean isPreparingVAD;								// Used for signaling the vadThread that enough frames have been collect for estimating VAD threshold
	static Thread vadThread;										// Runnable thread for computing energy profile for estimating VAD threshold
	static VAD vad;							 					// VAD object for detecting the start and end of sound event	
	static double energy = 0.0;									// Accumulated energy and average energy of detected sound events
	static int enBufSize = Config.ENBUFSIZE;
	static Buffer enBuf; 										// Circular buffer storing energy profile of the latest K frames
	
	/** Variables related to file IO */
	static FileOutputStream os = null;
	static String fullTmpFilename;

	/** LocationListener Object */
	static MyLocationListener gps;
	
	/*
	 * Method for finding the Audio Recorder of this device. Return an AudioRecord object
	 */
	public static AudioRecord findAudioRecord() {
		try {
			recordBufferSize = AudioRecord.getMinBufferSize(Config.RECORDER_SAMPLERATE, 
					Config.RECORDER_CHANNELS, Config.RECORDER_AUDIO_ENCODING);
			nSubframePerBuf = recordBufferSize / frameShift / 2; // e.g., 8192/128/2 = 32             
			System.out.println("recordBufferSize: " + recordBufferSize);
			if (recordBufferSize != AudioRecord.ERROR_BAD_VALUE) {
				// check if we can instantiate and have a success
				AudioRecord recorder = new AudioRecord(AudioSource.DEFAULT, Config.RECORDER_SAMPLERATE, 
						Config.RECORDER_CHANNELS, Config.RECORDER_AUDIO_ENCODING, recordBufferSize);
				if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
					return recorder;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		Toast.makeText(thisContext, "Fail to create AudioRecord object", Toast.LENGTH_LONG).show();
		return null;
	}

	/*
	 * Create objects, circular buffers, and Android's objects. This function should be called in the 
	 * onCreate() method of the Android Activity class. All objects are gender-independent.
	 */
	@SuppressWarnings("unchecked")
	public static void initialize(Context context) {
		thisContext = context;
		x = new float[samplePerFrm];
		subx = new float[frameShift];							// Samples in a sub-frame
		mfcc = new MFCC(samplePerFrm, Config.RECORDER_SAMPLERATE, numMfcc);
		ccBuf = new CircularFifoBuffer(K);						// Buffer storing K mfcc vectors
		dccBuf = new CircularFifoBuffer(K);						// Buffer storing K delta-mfcc vectors
		pDet = new YinPitchDetector(Config.RECORDER_SAMPLERATE, samplePerFrm);
		piBuf = new CircularFifoBuffer(K);
		pkBuf = new CircularFifoBuffer(K);
		mu_z = new double[] { VoiceQuality.JITTER_MEAN, VoiceQuality.SHIMMER_MEAN };	// Jitter and shimmer mean
		sigma_z = new double[] { VoiceQuality.JITTER_STD, VoiceQuality.SHIMMER_STD };	// Jitter and shimmer stddev
		aList = new ArrayList<double[]>();	
		enBuf = new CircularFifoBuffer(enBufSize); 				// Circular buffer storing energy profile of the latest K frames
		frmBuf = new CircularFifoBuffer(K);						// Buffer storing K latest frames of audio signals */
		
		for (int k = 0; k < K; k++) {
			ccBuf.add(new double[numMfcc + 1]);					// Initialize MFCC FIFO buffers
			dccBuf.add(new double[numMfcc + 1]);					// Initialize delta MFCC FIFO buffers
			piBuf.add(-1.0D);									// Initialize pitch FIFO buffer for computing jitter and shimmer
			pkBuf.add(0.0D);										// Initialize peak amplitude FIFO buffer for computing shimmer
			frmBuf.add(new double[samplePerFrm]);				// Initialize frame buffer
		}
		for (int k = 0; k < enBufSize; k++) {
			enBuf.add(new double[1]);							// Initialize energy buffer
		}
		mainIntent = new Intent(thisContext, SoundProcessingSetting.class);
		mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		pIntent = PendingIntent.getActivity(thisContext, 0, mainIntent, 0);		// Go back to MainActivity when user press the notification
		noti = new NotificationCompat.Builder(thisContext).setContentTitle(thisContext.getString(R.string.app_name) + " " + thisContext.getString(R.string.running))
				.setContentText(thisContext.getString(R.string.configure)).setSmallIcon(R.drawable.ic_launcher).setContentIntent(pIntent).build();
		noti.flags = Notification.FLAG_FOREGROUND_SERVICE;
		if (SoundProcessingActivity.thisActivity == null)
			calibrateDialog = new ProgressDialog(Exchanger.thisContext);
		else
			calibrateDialog = new ProgressDialog(SoundProcessingActivity.thisActivity);
	}

	/*
	 * Called by SoundProcessingActivity.java and SoundProcessingService.java. Starting point of this class.
	 * Initialize FIFO buffers, find VAD threshold (if not done yet) and start recording audio
	 */
	public static void startRecord(final Context context, boolean started) {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		bufferedShortData = new ArrayList<short[]>();
		subframeList = new ArrayList<float[]>();
		recorder = findAudioRecord();
		if (recorder == null) {
			System.out.println("Recorder Unavailable");
			return;
		} else {
			curFrm = 0;
			startFrm = 0;
			recorder.startRecording();
			isRecording = true;
			handler = new Handler();
			if (started) {
				vad = new VAD(Exchanger.vadThreshold, Config.endEventTh);		// Note: this part will be executed when startRecord is 
																			// called by SoundProcessingService.onStartCommand()
				handler.post(startDetection);								// A VAD threshold has been defined, start detection right away.
			} else {
				setupVAD();													// Will be execute when called by calibrateClick() and onOffClick()
			}
		}
	}

	/*
	 * Note: AudioRecorder object could be null if the service is running in background while performing 
	 * calibration (possibly bug in this app). One solution is the check whether the reference `recorder` is
	 * null. It works for that part (see below) but the other references could also become null (reason unknown).
	 * The best solution is to forbit users to perform calibration while sound processing service is running in
	 * the background.
	 */
	public static void setupVAD() {
		calibrateProgress = 0;
		calibrateDialog.setMessage(thisContext.getString(R.string.calibrateMsg));
		calibrateDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		calibrateDialog.setCancelable(false);
		calibrateDialog.setMax(100);
		calibrateDialog.setProgress(calibrateProgress);
		vadShortData = new short[recordBufferSize / 2];
		vadBufferedShortData = new ArrayList<short[]>();
		vadBufEnergy = new ArrayList<Double>();				// Storing energy profile
		isPreparingVAD = true;								
		vadThreshold = Config.VAD_DEFAULT_TH; 				// Default VAD threshold 
		Exchanger.vadThUpdated = false;						// Will be set to true after the vadThreshold has been updated.
		vadThread = new Thread(new Runnable() {				// Execute if vadThread == 0, see below
			@Override
			public void run() {
				int vadCounter = 0;					// Count how many times the following loop runs so that we can ignore the first K frames
				while (isPreparingVAD) {				// Will become false when computeVadThreshold thread is run
					if (recorder == null) {			
						System.out.println("Recorder: recorder is null");
					} else {							
						recorder.read(vadShortData, 0, recordBufferSize / 2);				// Fill vadShortData[]
						SoundProcessingActivity.waveformView.updateAudioData(vadShortData);	// Display current waveform
					}
					vadBufferedShortData.add(vadShortData);
					numFrames = vadBufferedShortData.get(0).length / samplePerFrm;	// No. of frames in vadShortData (no frame overlapping)
					for (int n = 0; n < numFrames; n++) {
						int k = 0;
						float[] x = new float[samplePerFrm];
						for (int i = (n * samplePerFrm); i < (n + 1) * samplePerFrm; i++) {	// Copy one frame to x[]
							x[k] = (float) vadBufferedShortData.get(0)[i];
							k++;
						}
						if (vadCounter > K) {											// Ignore the first K frames
							double currEnergy = MFCC.compLogEnergy(x);
							vadBufEnergy.add(currEnergy);
							System.out.println("VadCounter: " + vadCounter + "; currEnergy: " + currEnergy);
						}
						vadCounter++;
					}
					vadBufferedShortData.remove(0);
				}
				System.out.println("VAD: Finish preparing VAD.");
			}
		});
		if (vadThreshold == Config.VAD_DEFAULT_TH) {						// We need to estimate threshold
			calibrateDialog.show();										// Note: this is always true in this version of the app, see code above
			handler.post(updateCalibrateDialog);							// Execute run() of updateCalibrateDialog		
		} else {
			vad = new VAD(vadThreshold, Config.endEventTh);				// Create VAD object for detecting start and end of events
			handler.post(startDetection);								// A VAD threshold has been defined, start detection right away.
		}
	}

	static Runnable updateCalibrateDialog = new Runnable() {
		public void run() {
			int updateInterval = (int)(Config.STABILIZE_TIME + Config.RECORD_TIME)*10;		// Time (in ms) for each update of the progress bar
			int stbTime = (int)Config.STABILIZE_TIME*10;					// Time (in ms) for the A/D to stabilize
			calibrateDialog.setProgress(calibrateProgress += 1);
			if (calibrateProgress == 100) {
				calibrateProgress = 0;
				calibrateDialog.dismiss();
				handler.post(computeVadThreshold);			// Execute run() in computeVadThreshold after STABILIZE_TIME+RECORD_TIME seconds so 
															// that vadThread will have enough time to collect background sound
			}
			else if (calibrateProgress == stbTime) {					
				vadThread.start();									// Start estimating VAD threshold after STABILIZE_TIME seconds
				handler.postDelayed(updateCalibrateDialog, updateInterval);
			} else {
				handler.postDelayed(updateCalibrateDialog, updateInterval);	// The first 2 seconds is for the A/D converter to stabilize
			}
		}
	};
	
	
	static Runnable computeVadThreshold = new Runnable() {	// Execute if necessary to estimate VAD threshold
		public void run() {
			isPreparingVAD = false;							// This will cause vadThread to stop recording
			try {
				vadThread.join();							// Wait for vadThread to finish so that no more
			} catch (InterruptedException e) {				// energy will be added to vadBufEnergy
				e.printStackTrace();
			}
			double vadEnergy = 0;
			for (double e : vadBufEnergy) {
				vadEnergy += e;
			}
			vadEnergy /= vadBufEnergy.size();				// Get averagy energy in the energy profile
			double stddev = 0;
			for (int i = 0; i < vadBufEnergy.size(); i++) {
				stddev += (vadBufEnergy.get(i) - vadEnergy) * (vadBufEnergy.get(i) - vadEnergy);
			}

			/*
			 * Use the mean and stddev to set the VAD threshold is not good for Nexus 5,
			 * because the background noise recorded by this device has high energy variance. Specifically,
			 * the device records high-energy noise between 0 and 1.6 seconds and high noise variation after 6 seconds.
			 * A better strategy is to limit the VAD threshold as follows.
			 */
			double th1 = vadEnergy + Config.VAD_STD_FACTOR * Math.sqrt(stddev / vadBufEnergy.size());
			double th2 = vadEnergy * Config.VAD_MEAN_FACTOR;
			vadThreshold = (th1 < th2) ? th1 : th2;						// Set vad threshold to the min of th1 and th2
			System.out.printf("th1 = %.3f; th2 = %.3f\n", th1, th2);
			
			String contentStr = String.format(Locale.getDefault(), "VAD Threshold: %.3f", vadThreshold);
			sendNotification(thisContext, 2, contentStr);

			for (double e : vadBufEnergy) {
				System.out.printf("VAD: %.2f\n", e);
			}
			System.out.printf("VAD: %.2f, %.2f, %d\n", vadEnergy, Math.sqrt(stddev / vadBufEnergy.size()), vadBufEnergy.size());		
			System.out.println("VAD: Threshold = " + vadThreshold);
			Exchanger.vadThreshold = vadThreshold;
			if (vad == null)
				vad = new VAD(vadThreshold, Config.endEventTh);	// Create VAD object once the threshold is found
			else {
				vad.setThreshold(vadThreshold);				// Update current energy threshold of current VAD object
				vad.reset();
			}
		
			handler.post(startDetection);					// Execute run() in startDetection. Do it here so that we can be
															// sure that VAD has been setup
			Exchanger.vadThUpdated = true;					// Acknowledge SoundProcessingActivity.calibrateClick (if it is running) 
															// that the VAD threshold has been updated
			Toast.makeText(thisContext, String.format("VAD Threshold: %.2f", vadThreshold), Toast.LENGTH_LONG).show();
			SoundProcessingActivity.waveformView.compMaxAmpToDraw(Math.sqrt(Math.exp(vadEnergy)));
		}					
	};

	static Runnable startDetection = new Runnable() {
		public void run() {
			if (recordingThread != null) {
				recordingThread = null;
			}
			recordingThread = new Thread(new Runnable() {	// Thread for recording audio for sound detection
						public void run() {					// Execute when recordingThread is started
							try {
								executeMainLoop(thisContext);	// Execute sound detection main loop and write audio data to file	
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}, "AudioRecorder Thread");
			recordingThread.start();							// Start recording audio and sound detection
		}
	};

	/*
	 * This is the main loop of the activity/service. It captures the audio, save the
	 * audio data to a temp binary file for debugging. It divides each analysis
	 * frame into a number of subframes and advances the processing frame index
	 * by one subframe for each iteration. The number of subframes per analysis frame
	 * depends on the frameShift. For example, if frameShift is 128 samples and an
	 * analysis frame has 512 samples, the no. of subframes is 512/128 = 4. If
	 * the VAD detector detects a sound event, the mean and stddev of 
	 *        MFCC+dMFCC+ddMFCC+jitter+shimmer+djitter+dshimmer 
	 * of the whole sound event are concatenated and passed to an SVM detector. If
	 * the SVM score is larger than decisionTh, a notification showing scream sound is
	 * detected will be sent to the Android Notification; otherwise the
	 * notification will show non-scream sound.
	 */
	private static void executeMainLoop(Context context) throws Exception {
		try {
			prepareTempFile();
			os = new FileOutputStream(getTempFilename());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		paraFile = Exchanger.SVM_PARA_FILE;
		int dim = numMfcc * 3 + 2;	 								// (=38) MFCC+dMFCC+ddMFCC+jitter+shimmer without energy, dEnergy, and ddEnergy 
		svmd = new SVMdetector(dim * 2, Config.SVM_TYPE, 0.0);		// Create an SVM detector, input vector contains both mean and stddev vectors
		AssetManager assetManager = context.getResources().getAssets(); // Use AsstManager to load SVM detector parameters into memory
		svmd.createSVMdetector(assetManager.open(paraFile));			// Load parameter file to SVM detector

		while (isRecording) {
			shortData = new short[recordBufferSize / 2];				// Main loop of sound detector
			if (recorder == null) {
				System.out.println("Recorder: null recorder in executeMainLoop");
			} else {
				recorder.read(shortData, 0, recordBufferSize / 2);		// Read audio and fill in shortData[] buffer in short int format
				bufferedShortData.add(shortData);						// Add to ArrayList for processing 
				handler.post(processData);								// Start processing data in bufferedShortData
				SoundProcessingActivity.waveformView.updateAudioData(shortData);  // Display current waveform
			}
		}
	}

	static Runnable processData = new Runnable() {
		@Override
		public void run() {
			while (bufferedShortData.size() >= 1) {					// Still have data to process.
				for (int n = 0; n < nSubframePerBuf; n++) {			// Process audio signal in ArrayList and shift by one subframe each time
					int k = 0;
					subx = new float[frameShift];					// Allocate buffer to store data in one subframe in float.
					for (int i = (n * frameShift); i < (n + 1) * frameShift; i++) {
						subx[k] = bufferedShortData.get(0)[i];
						k++;
					}
					subframeList.add(subx);							// Add the current subframe to the subframe list. Later, a number of
				}													// subframes will be concatenated to form a analysis frame for extracting MFCC
				handler.post(detectEvent);							// Start detecting event
				bufferedShortData.remove(0);							// Remove the buffer after it has been processed.
			}
		}

	};

	static Runnable detectEvent = new Runnable() {
		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			String fullTmpFilename = getTempFilename();
			double duration = 0.0;
			String decision = "Nonscream";								// Default decision
			while (subframeList.size() >= nSubframePerMfccFrame) {		// Need at least nSubframePerMfccFrame to get one analysis frame
				x = extractOneFrameFromList(nSubframePerMfccFrame);		// Extract nSubframePerMfccFrame (4) from the subframe list to form one analysis frame
				frmBuf.add(x);											// Add current frame to FIFO buffer for computing MFCC if event has started			
				double[] en = new double[1];
				en[0] = MFCC.compLogEnergy(x);							// Compute energy of current frame
				enBuf.add(en);											// Add current energy to FIFO buffer for VAD

				if (curFrm >= enBufSize) {								// Start the VAD process once we have enough frames in the energy profile
					vad.update(enBuf, VAD.MODE_ALL);						// Update VAD status and prepare for detecting the start of sound event
					SoundProcessingActivity.energyTextView.setText(String.format("E = %.2f", en[0]));	// Display energy value
					if (vad.isEventStarted() == true) {					// Check if event already started							
						energy += en[0];
						writeSubframeToFile(fullTmpFilename, x);			// Write the first subframe to file for debugging
						if (startFrm == 0) {								// Check if it is the first detection of this event, making 
							startFrm = curFrm - Config.ROLL_BACK;		// sure that tList has enough frames for rolling back
							rollBackAcousticVectors();					// Convert the current and last R-1 frames to acoustic vector
						} else {
							aList.add(compAcousticVector(x));			// Not first detected; append the acoustic vector of the current frame
							if (aList.size() > 7500) {					// Event should not exceed 1 minute
								vad.setEndOfEvent();						// Force the event to end
							}
						}
						if (vad.isEventEnded() == true) {
							for (int i = 0; i < Config.endEventTh; i++) {// Event has ended, roll-back endEventTh frames
								if (aList.size() > 0) {					// Avoid remove element from empty list
									aList.remove(aList.size() - 1);		// Remove the tailing silence frames in aList
								}
							}
							double score = getEventScore(aList);			// Classify the event and send notification to Android
							if (score > Config.MIN_SCORE) {				// Send notification to Android
								energy /= aList.size();
								duration = aList.size() / frameRate;
								SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(thisContext);
								double decisionTh = Double.parseDouble(prefs.getString("sensitivity", "-0.25"));
								decision = (score > decisionTh) ? "Scream" : "Nonscream";
								String contentStr = String.format(Locale.getDefault(), "%s: %.2fs E: %.2f Scr: %.3f", decision, duration, energy, score);
								sendNotification(thisContext, 3, contentStr);
								System.out.println("Decision: " + contentStr);
								Toast.makeText(thisContext, contentStr, Toast.LENGTH_LONG).show();
								if (score > decisionTh) {
									gps = new MyLocationListener(thisContext);
									sendEmail();
									sendSMS();
									callPhone();
									gps.removeLocationUpdates();			// Save battery
								}
							}
							try {
								os.close();								// Close the temp binary file
								os = null;								// So that the next iteration will open it (see above).
							} catch (IOException e) {
								e.printStackTrace();
							}
							if (score > Config.MIN_SCORE) {				// Saving event to wave file and score to log file
								SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Exchanger.thisContext);
								boolean logScore = prefs.getBoolean("logScore", false);
								boolean saveEvent = prefs.getBoolean("saveEvent", false);
								String eventID = getEventID();
								if (saveEvent) {
									String fullname = getFullname(eventID);
									Wave.copyTmpfileToWavfile(fullTmpFilename, fullname + ".wav", 
											Config.RECORDER_SAMPLERATE, recordBufferSize);
								}
								if (logScore) {
									saveScoreToTextFile(eventID, Config.LOG_FILE_NAME, score, duration, decision);
								}
								Wave.deleteTempFile(fullTmpFilename);	// Delete the temp file
							}
							aList.removeAll(aList);
							vad.reset();									// Prepare for the next event 
							startFrm = 0;
							energy = 0.0;								// Reset for the next sound event
						}
					}
				}
				if (curFrm == Integer.MAX_VALUE)
					curFrm = enBufSize;
				else
					curFrm++;
				subframeList.remove(0);									// Remove the current subframe to shift the next frame by one subframe
			}
			/*** END of while loop for sound detection ***/
		}
	};

	/*
	 * Convert the acoustic features from the ArrayList into an SVM input vector, 
	 * and present the vector to the SVM detector. If the number of frames is too
	 * small, the function return the minimum score, which will cause the SVM detector
	 * to output "Non-scream"
	 */
	private static double getEventScore(ArrayList<double[]> aList) {
		int numFrms = aList.size();
		double[] vec; 													// Input vector to SVM
		double score = Config.MIN_SCORE;									// SVM score
		if (numFrms > minNumSndFrms) {									// If the detected event is long enough
			vec = Vec.getInputVector(aList, numMfcc*3, mu_z, sigma_z);	// Get mean and stddev from ArrayList and perform Znorm on jitter and shimmer
			score = svmd.getScore(vec);									// Compute SVM score
		}
		System.out.printf("Score: No. of Sound subframes = %d; Score = %f\n", numFrms, score);
		return score;
	}

	private static void sendNotification(Context context, int notiID, String contentStr) {
		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		Notification noti = new NotificationCompat.Builder(Exchanger.thisContext).setContentTitle("Scream Detector").setContentText(contentStr)
				.setSmallIcon(R.drawable.ic_launcher).setContentIntent(pIntent).build();
		//n1.defaults = Notification.DEFAULT_VIBRATE;
		//noti.flags |= Notification.FLAG_AUTO_CANCEL;
		notificationManager.notify(notiID, noti);
	}

	@SuppressWarnings("unchecked")
	private static void rollBackAcousticVectors() {
		int i = 0;
		Iterator<float[]> it = frmBuf.iterator();
		while (it.hasNext()) {
			if (i < enBufSize - Config.ROLL_BACK) {
				it.next();
			} else {
				aList.add(compAcousticVector(it.next()));			// Store the acoustic vector in aList for SVM classification
			}
			i++;
		}
	}

	@SuppressWarnings("unchecked")
	private static double[] compAcousticVector(float[] x) {
		double[] cc = mfcc.doMFCC(x);								// Compute MFCC
		ccBuf.add(cc);												// Add to FIFO buffer for computing delta MFCC
		double[] dcc = mfcc.compDeltaMfcc(ccBuf, numMfcc + 1);		// Compute delta MFCC
		dccBuf.add(dcc);
		double[] ddcc = mfcc.compDeltaMfcc(dccBuf, numMfcc + 1);	// Compute delta delta MFCC

		/* newFeatures */
		double pitch = (double) pDet.getPitch(x).getPitch();
		piBuf.add(pitch);
		double jitter = VoiceQuality.getJitter(piBuf);				// Compute jitter of current frame
		pkBuf.add((double) getAbsMax(x));
		double shimmer = VoiceQuality.getShimmer(piBuf, pkBuf);		// Compute shimmer of current frame
		double vq[] = new double[2];
		vq[0] = jitter;
		vq[1] = shimmer;

		/* Validation of expected output after implementation of new features */
		System.out.println("piBuf: " + piBuf.toString());
		System.out.println("pkBuf: " + pkBuf.toString());
		System.out.printf("Jitter: %f \n", jitter);
		System.out.printf("Shimmer: %f \n", shimmer);

		return (Vec.concateVectors(cc, dcc, ddcc, vq));				// Concatenate 3 vectors to form an acoustic vector, ignore energy, de, and dee
	}

	private static float[] extractOneFrameFromList(int M) {
		float x[] = new float[samplePerFrm];
		int n = 0;
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < subframeList.get(i).length; j++) {
				x[n] = subframeList.get(i)[j];
				n++;
			}
		}
		return x;
	}

	private static void prepareTempFile() {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, Config.AUDIO_RECORDER_FOLDER);
		if (!file.exists()) {
			file.mkdirs();
		}
		File tempFile = new File(filepath, Config.AUDIO_RECORDER_TEMP_FILE);
		if (tempFile.exists()) {
			tempFile.delete();
		}
	}

	private static String getTempFilename() {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, Config.AUDIO_RECORDER_FOLDER);
		return (file.getAbsolutePath() + "/" + Config.AUDIO_RECORDER_TEMP_FILE);
	}



	/*
	 * This method use the date and time information (from Date object) as the
	 * event ID It will also append the path of the external storage directory
	 * as the prefix.
	 */
	private static String getEventID() {
		Date now = new Date();
		return ("" + now.getTime());
	}

	/*
	 * Return the full name of a wave file to be saved, with full path to the SD
	 * card as prefix. Create folder if necessary
	 */
	private static String getFullname(String eventID) {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, Config.AUDIO_RECORDER_FOLDER);
		if (!file.exists()) {
			file.mkdirs();
		}
		return file.getAbsolutePath() + "/" + eventID;
	}

	/*
	 * Save event ID and score to a log file.
	 */
	private static void saveScoreToTextFile(String eventID, String logFilename, double score, 
											double duration, String decision) {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, Config.AUDIO_RECORDER_FOLDER);
		if (!file.exists()) {
			file.mkdirs();
		}
		String fullname = file.getAbsolutePath() + "/" + logFilename;
		try {
			PrintWriter pw = new PrintWriter(new FileWriter(fullname, true));
			String dec = (decision.equals("Scream")  ? "S" : "NS");
			pw.printf("%s,%.2f,%.3f,%s\n", eventID, duration, score, dec);
			pw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static byte[] short2byte(short[] sData) {
		int shortArrsize = sData.length;
		byte[] bytes = new byte[shortArrsize * 2];
		for (int i = 0; i < shortArrsize; i++) {
			bytes[i * 2] = (byte) (sData[i] & 0x00FF);
			bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
		}
		return bytes;

	}

	/** newFeatures */
	private static float getAbsMax(float array[]) {
		float max = 0.0F;
		for (float y : array) {
			if (y > max) {
				max = y;
			}
		}
		return max;
	}

	/*
	 * Stop the recording activity
	 */
	public static void stopRecording() {
		if (null != recorder) {
			if (vadThread != null) {					
				try {										
					vadThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			isRecording = false;
			recorder.stop();
			recorder.release();
			recorder = null;
			if (recordingThread != null) {					// recordingThread could be null if the user press the Stop button
				try {										// before the startDetection Runnable is executed.
					recordingThread.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			recordingThread = null;
			vadShortData = null;							
			vadBufferedShortData = null;
			vadBufEnergy = null;
			handler.removeCallbacks(processData);
			handler.removeCallbacks(detectEvent);
			if (bufferedShortData != null) {
				bufferedShortData.clear();
			}
			if (subframeList != null) {
				subframeList.clear();
			}
		}
	}

	private static void writeSubframeToFile(String filename, float[] x) {
		short[] xWav = new short[frameShift];			// Advance by frameShift for each computation of MFCC
		for (int i = 0; i < frameShift; i++) {
			xWav[i] = (short) x[i];						// Get the first subframe from x[] for writing to file
		}	
		byte[] soundData = short2byte(xWav);				// Convert current subframe into byte array for saving to wavefile
		try {
			if (os == null) {
				os = new FileOutputStream(filename);		// Make sure it is open before writing
			}
			os.write(soundData);							// Write audio data to temp binary file. 
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	private static void sendSMS() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Exchanger.thisContext);
		Boolean enableSMS = prefs.getBoolean("enableSMS", false);
		String phoneNumber = prefs.getString("phoneNumber", null);
		TelephonyManager phoneMgr = (TelephonyManager) thisContext.getSystemService(Context.TELEPHONY_SERVICE);
		if (enableSMS == true && phoneNumber != null && phoneNumber.trim().length() > 0 &&
				phoneMgr.getSimState() != TelephonyManager.SIM_STATE_ABSENT) {
			SmsManager smsManager = SmsManager.getDefault();
			smsManager.sendTextMessage(phoneNumber, null, "Scream detected. Longitude: "+gps.getLongitude() + 
											"; Latitude: "+gps.getLatitude() +
											". Please try to contact the mobile user", null, null);
		}
	}
	
	private static void callPhone() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Exchanger.thisContext);
		String phoneNumber = prefs.getString("phoneNumber", null);
		Boolean enableCall = prefs.getBoolean("enableCall", false);
		if (phoneNumber != null && enableCall) {
			if (phoneNumber.trim().length() > 0) {		// Avoid empty string or white spaces in the preference field
				TelephonyManager phoneMgr = (TelephonyManager) thisContext.getSystemService(Context.TELEPHONY_SERVICE);
				if (phoneMgr.getCallState() == TelephonyManager.CALL_STATE_IDLE && 
					phoneMgr.getSimState() != TelephonyManager.SIM_STATE_ABSENT) {
					Intent callIntent = new Intent(Intent.ACTION_CALL);
					callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					callIntent.setData(Uri.parse("tel:" + phoneNumber));
					Exchanger.thisContext.startActivity(callIntent);
				}
			}
		}
	}
	
	private static void sendEmail()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Exchanger.thisContext);
		Boolean enableEmail = prefs.getBoolean("enableEmail", false);
		String toEmailAdd = prefs.getString("emailToAddress", null); // Recipient of the email
		String fromEmailAdd = prefs.getString("emailFromAddress", null); // Sender of email
		String fromPassword = prefs.getString("emailFromPassword", null); // Password of the email sender account
		System.out.println("Email: Preparing email");
		String emailBody = "Scream detected. Longitude: "+gps.getLongitude()+"; Latitude: "+gps.getLatitude()
						+". Please try to contact the mobile user";		

		if(enableEmail) {
			if (fromEmailAdd.trim().length() > 0 && fromPassword.trim().length() > 0) {
				try {
					GMailSender sender = new GMailSender(fromEmailAdd, fromPassword);
					sender.sendMail("Scream Detected", emailBody, fromEmailAdd, toEmailAdd);
				} catch (Exception e) {
					Log.e("SendMail", e.getMessage(), e);
				}
			}
		}

	}

}
