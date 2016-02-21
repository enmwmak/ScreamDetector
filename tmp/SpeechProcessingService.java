package edu.polyu.hazardalert;
/**
 * @author Leung, Wing-Lung Henry, The Hong Kong Polytechnic University.
 */
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.collections.Buffer;
import org.apache.commons.collections.buffer.CircularFifoBuffer;

import edu.polyu.hazardalert.mfcc.MFCC;
import edu.polyu.hazardalert.svm.SVMdetector;
import edu.polyu.hazardalert.vad.VAD;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class SpeechProcessingService extends Service{
	
	// Constant and Variables related to this Android service
    static String AUDIO_RECORDER_FOLDER = "HazardAlert";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp";
    private static final String LOG_FILE_NAME = "logfile.txt";
    private static final double VAD_DEFAULT_TH = 10.0;  // 14.82;
    private static final String SVM_PARA_FILE = "svm/svmdet_rbf2.dat";
    private static final String SVM_TYPE = "edu.polyu.hazardalert.svm.RbfSVM";
    private static final int MIN_NUM_SND_FRMS = 50;		// 50*8ms = 0.4s 
	static Service thisService;
	Notification noti;
	NotificationManager notificationManager;
	Intent mainIntent;
	PendingIntent pIntent;
    Handler handler;
	
	// Variables related to audio recording
    private static final int RECORDER_SAMPLERATE = 16000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;   
	private AudioRecord recorder;
	private Thread recordingThread;						// Thread for recording audio for sound detection
	private boolean isRecording = false;
	static int samplePerFrame = 512; 					// 256 for 32ms per frame at 8kHz; 512 for 32ms per frame at 16kHz
	static int recordBufferSize;							// Device-dependent record buffer size in bytes

	// Variables related to time-domain samples
	short[] shortData;
	static byte[] soundData;								// Byte buffer for writing to temp binary file (for debugging) 
	static int numFrames;
	static ArrayList<short[]> bufferedShortData;
	static int counter, yCounter;
	
	// Variables related to SVM classifier
	static String svmType;								// Type of SVM, here we use "RbfSVM"	
	static String paraFile;								// Parameter file of SVM detector
	SVMdetector svmd;
	double score = 0.0;									// Score of SVM detector
	
	// Variables related to MFCC extraction
	static int numMfcc = 12;								// 12 MFCC (not include C0) ==> 39-dim acoustic vectors
	static double frameRate = 125;						// 125Hz frame rate ==> frameShift is 8ms <=> 128 samples at 16kHz
	static int frameShift = (int)(RECORDER_SAMPLERATE/frameRate);		// (16000/125) = 128
	static int nSubframePerBuf;							// No. of subframes per recording buffer (device-dependent)
	static int nSubframePerMfccFrame = samplePerFrame/frameShift;		// 512/128 = 4
	static float[] x = new float[samplePerFrame];
	static float[] subx = new float[frameShift];			// Samples in a sub-frame
	static ArrayList<float[]> subframeList;				// Contains a set of subframes to be processed
	static double[] y = new double[numMfcc*3];			// Acoustic vector, excluding e, de, and dde
	static double[] yAvg = new double[numMfcc*3];		// Average of y[] of the whole sound event, exclude energy, dEnergy, ddEnergy
	static MFCC mfcc = new MFCC(samplePerFrame, RECORDER_SAMPLERATE, numMfcc);
	static int K = 5;									// No. of mfcc vectors for computing delta mfcc (must be odd number)
	static double cc[];                                  // Array storing MFCC of the current frame cc[0..numMfcc], cc[0] will be ignored
	static double dcc[];								    // Array storing detla-cepstrum
	static double ddcc[];								// Array storing delta-delta cepstrum
	static Buffer ccBuf = new CircularFifoBuffer(K);	    // Buffer storing K mfcc vectors
	static Buffer dccBuf = new CircularFifoBuffer(K);	// Buffer storing K delta-mfcc vectors

	/* Variables related to VAD and sound event detection */
	int minNumSndFrms = MIN_NUM_SND_FRMS;	            // Minimum no. of subframes in a sound event 
	boolean silence = false;							    // =true if current frame is silence
	int preState = VAD.SILENCE;							// State of previous frame
	int curState = VAD.SILENCE;							// State of current frame
	boolean soundEvent = false;							// =true if sound event is detected
	double vadThreshold, vadEnergy;						// VAD threshold and energy of sound frames for estimating the threshold
	short[] vadShortData;								// Samples for estimating the VAD threshold
	static ArrayList<short[]> vadBufferedShortData;
	static ArrayList<Double> vadBufferedEnergy;
	MFCC vadMfcc = new MFCC(samplePerFrame, RECORDER_SAMPLERATE, numMfcc);	// For computing log-energy 
    int vadCounter = 0;
    boolean isPreparingVAD;
    Thread vadThread;
    	
    // Variables related to file IO
    static FileOutputStream os = null;   
	
    // Methods of this Android service
	@SuppressWarnings("unchecked")
	@Override
	public void onCreate() {
		Exchanger.thisContext = this;					// For sharing context reference to other activities of this App
		thisService = this;
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		handler = new Handler();
		for (int k=0; k<K; k++) {					
			ccBuf.add(new double[numMfcc+1]);			// Initialize the FIFO buffer
			dccBuf.add(new double[numMfcc+1]);			
		}
	}

	@Override
	public IBinder onBind(Intent intnet) {
		return null;
	}
	
	@Override
	public void onDestroy() {
        Exchanger.stopping = false;
		stopRecording();
		stopForeground(true);
	}
	
	/*
	 * Called by the system when MainActivity.java executes startService(processingService);
	 */	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		AlarmReceiver.releaseLock();
		mainIntent = new Intent(this, MainActivity.class);
		pIntent = PendingIntent.getActivity(this, 0, mainIntent, 0);		// Go back to MainActivity when user press the notification
		noti = new NotificationCompat.Builder(this)
        		.setContentTitle(getString(R.string.app_name)+" "+getString(R.string.running))
        		.setContentText(getString(R.string.configure))
        		.setSmallIcon(R.drawable.ic_launcher)
        		.setContentIntent(pIntent).build();
		noti.flags = Notification.FLAG_FOREGROUND_SERVICE;

		// Running the service in the foreground
		startForeground(1, noti);
		
		startRecord();
		// The service should only remain running while processing any commands sent to it.
		return Service.START_NOT_STICKY;	
	}
	
	public AudioRecord findAudioRecord() {
        try {
            recordBufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
            nSubframePerBuf = recordBufferSize/frameShift/2;   	   		// e.g., 8192/128/2 = 32             
    	    		System.out.println("nSubframePerBuf: "+nSubframePerBuf);
            if (recordBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                // check if we can instantiate and have a success
                AudioRecord recorder = new AudioRecord(AudioSource.DEFAULT, RECORDER_SAMPLERATE, RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING, recordBufferSize);
                if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                    return recorder;
            }
        } catch (Exception e) {
        		e.printStackTrace();
        }
	    return null;
	}
	
	public void startRecord() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		bufferedShortData = new ArrayList<short[]>();
		subframeList = new ArrayList<float[]>();
	    recorder = findAudioRecord();
	    if (recorder == null){
	    		System.out.println("Recorder Unavailable");
	    }
	    else {
		    counter = 0;
		    yCounter = 0;
		    recorder.startRecording();
		    isRecording = true;
		    recordingThread = new Thread(new Runnable() {
		        public void run() {							// Execute when recordingThread is started
		            try {
						executeMainLoop();					// Execute sound detection main loop and write audio 	
					} catch (Exception e) {					// data to file
						e.printStackTrace();
					}
		        }
		    }, "AudioRecorder Thread");
		    setupVAD();
	    }
	}
	
	private void setupVAD() {
		vadShortData = new short[recordBufferSize/2];
    		vadBufferedShortData = new ArrayList<short[]>();
    		vadBufferedEnergy = new ArrayList<Double>();			// Storing energy profile
		vadEnergy = 0;
    		isPreparingVAD = true;
    		vadThreshold = VAD_DEFAULT_TH; 						// Default VAD threshold 
		vadThread = new Thread(new Runnable() {				// Execute if vadThread == 0, see below
			@Override
			public void run() {
			    	while (isPreparingVAD) {
			    		recorder.read(vadShortData, 0, recordBufferSize/2);				// Fill vadShortData[]
					vadBufferedShortData.add(vadShortData);
					numFrames = vadBufferedShortData.get(0).length/samplePerFrame;	// No. of frames in vadShortData (no frame overlapping)
					for (int n=0; n<numFrames; n++) {
						int k = 0;
						for (int i=(n*samplePerFrame); i<(n+1)*samplePerFrame; i++) {	// Append one frame to x[]
							x[k] = vadBufferedShortData.get(0)[i];
							k++;
						}
						if (vadCounter>5) {											// Ignore the first 5 frames
							double currEnergy = vadMfcc.compLogEnergy(x);
							vadBufferedEnergy.add(currEnergy);
							vadEnergy += currEnergy;
							System.out.println("VadCounter: "+vadCounter+"; VadEnergy: "+currEnergy);
						}
						vadCounter++;
					}
					vadBufferedShortData.remove(0);
				}
			}});
	    if (vadThreshold == 0) {								// If vadThread is 0, we need to estimate 
	    		vadThread.start();								// the VAD threshold based on background sound
	    		handler.postDelayed(computeVadThresold, 5000);	// Execute run() in computeVadThreshold after 5 seconds
	    		handler.postDelayed(startDetection, 5000);		// Execute run() in startDetection after 5 seconds
    		}
	    	else {
	    		handler.post(startDetection);					// A VAD threshold has been defined, start detection right away.
	    	}
	}
	
	Runnable computeVadThresold = new Runnable() {			// Execute if necessary to estimate VAD threshold
		public void run() {
			isPreparingVAD = false;
			vadEnergy /= vadBufferedEnergy.size();
			double stddev = 0;
			for (int i=0; i<vadBufferedEnergy.size(); i++) {
				stddev += (vadBufferedEnergy.get(i) - vadEnergy)*(vadBufferedEnergy.get(i) - vadEnergy);
			}
			vadThreshold = vadEnergy + 5*Math.sqrt(stddev/vadBufferedEnergy.size());	// Threshold is background + 5 stddev(background)
			System.out.println("FrameSize: "+vadBufferedEnergy.size()+"; VadThreshold = " + vadThreshold);
		}
	};
	
	Runnable startDetection = new Runnable() {
		public void run() {
		    recordingThread.start();							// Start recording audio and sound detection
		}
	};
	
	/*
	 * This is the main loop of the service. It captures the audio, save the audio data to a temp binary file
	 * for debugging. It divides each analysis frame into a number of subframes and advances the processing frame index 
	 * by one subframe for iteration. The number of subframes per analysis frame depends the frameShift. For example, if
	 * frameShift is 128 samples and an analysis frames have 512 samples, the no. of subframes is 512/128 = 4.
	 * If the VAD detector detects a sound event, the MFCC of the whole sound event is averaged and the resulting vector
	 * is passed to the SVM detector. If the SVM score is larger than 0, a notification showing scream sound is detected
	 * will be sent to the Android Notification; otherwise the notification will show non-scream sound 
	 */
	private void executeMainLoop() throws Exception {
		System.out.println("vadThreshold: "+vadThreshold);
		soundData = new byte[recordBufferSize];
		try {
			String filename = getTempFilename(); 	// Temp file for storing binary audio data
			os = new FileOutputStream(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		paraFile = SVM_PARA_FILE;
		int dim = numMfcc*3;	 						// MFCC+dMFCC+ddMFCC without energy, dEnergy, and ddEnergy
		String svmType = SVM_TYPE;					
		svmd = new SVMdetector(dim, svmType, 0.0);	// Create an SVM detector
		svmd.createSVMdetector(paraFile);			// Load parameter file to SVM detector
		
	    while (isRecording) {
	    		shortData = new short[recordBufferSize/2];
	        recorder.read(shortData, 0, recordBufferSize/2);		// Read audio and fill in shortData[] buffer in short int format
	        bufferedShortData.add(shortData);					// Add to ArrayList for processing 
			handler.post(processData);							// Start processing data in bufferedShortData
        }
	    	    
	}

	Runnable processData = new Runnable() {		
		@Override
		public void run() {
			while (bufferedShortData.size() >= 1) {
				for (int n=0; n<nSubframePerBuf; n++) {			// Process audio signal in ArrayList and shift by one subframe each time
					int k = 0;					
					subx = new float[frameShift];				// Allocate buffer to store data in one subframe in float.
					for (int i=(n*frameShift); i<(n+1)*frameShift; i++) {
						subx[k] = bufferedShortData.get(0)[i];
						k++;
					}										
					subframeList.add(subx);						// Add the current subframe to the subframe list. Later, a number of
				}												// subframes will be concatenated to form a analysis frame for extracting MFCC
				handler.post(detectEvent);
				bufferedShortData.remove(0);						// Remove the buffer after it has been processed.
			}
		}
		
	};
	
	Runnable detectEvent = new Runnable() {
		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			while (subframeList.size() >= nSubframePerMfccFrame) {	// Need at least nSubframePerMfccFrame to get one analysis frame
				x = extractOneFrameFromList(nSubframePerMfccFrame);	// Extract nSubframePerMfccFrame (4) from the subframe list to form
				short[] xWav = new short[frameShift];				// one analysis frame. Advance by frameShift for each iteration
				for (int i=0; i<frameShift; i++) {
					xWav[i] = (short) x[i];							// Get the first subframe from x[] for writing to file
				}

			    cc = mfcc.doMFCC(x);
				ccBuf.add(cc);
				dcc = mfcc.compDeltaMfcc(ccBuf, numMfcc+1);
				dccBuf.add(dcc);
				ddcc = mfcc.compDeltaMfcc(dccBuf, numMfcc+1);
																
				silence = VAD.detect(ccBuf, VAD.MODE_ALL, vadThreshold);	// Note that the threshold could be background-dependent
				if (counter >= K )  {									// Ignore the first K-1 frames so that dcc and ddcc will be correct
					if (silence == false) {								// Sound detect, accumulate the MFCC vector y[]
						try {
							soundData = short2byte(xWav);
							System.out.println("Frame logEnergy: " + String.valueOf(cc[0]));
							if (os == null) {
								os = new FileOutputStream(getTempFilename());	// Make sure it is open before writing
							}
							os.write(soundData);							// Write audio data to temp binary file. Note that os never close????? 
						} catch (IOException e) {
							e.printStackTrace();
						}
						preState = curState; 
						curState = VAD.NONSILENCE;
						concateMfcc(cc, dcc, ddcc, y);					// Pack cc, dcc, ddcc to y[], excluding e, de, and dde
						accumulateMfcc(y, yAvg);							// yAvg[] = yAvg[] + y[]
						yCounter++;										// Count no. of sound subframes so far
					} else {
						preState = curState; 
						curState = VAD.SILENCE;
					}
					if (preState == VAD.NONSILENCE && curState == VAD.SILENCE) {	// End of sound event
						if (yCounter > minNumSndFrms) {					// If the detected event is longer than 1.6s
							averageMfcc(yAvg, yCounter);					// Compute average MFCC and store average in yAvg[]
							score = svmd.getScore(yAvg);					// Compute SVM score
							System.out.printf("No. of Sound subframes = %d; Score = %f\n", yCounter, score);

							try {
								os.close();								// Close the temp binary file
								os = null;								// So that the next iteration will open it (see above).
							} catch (IOException e) {
								e.printStackTrace();
							}
							String eventID = getEventID();
							String fullname = getFullname(eventID);
					        saveAudioToWaveFile(getTempFilename(), fullname+".wav");	// Copy the temp file to .wav file
					        deleteTempFile();							// Delete the temp file
				            saveScoreToTextFile(eventID, LOG_FILE_NAME, score);
					        
							for (int i=0;i<yAvg.length;i++)
								System.out.print(yAvg[i]+" ");
							System.out.println();
							
							if (score > 0.0) 
							{
								Notification n1 = new NotificationCompat.Builder(Exchanger.thisContext)
						        .setContentTitle("Scream Alert")
						        .setContentText("frame no: "+yCounter+";score: "+ score+";")
						        .setSmallIcon(R.drawable.scream)
						        .setContentIntent(pIntent).build();
								// Hide the notification after its selected
								n1.defaults = Notification.DEFAULT_VIBRATE;
								n1.flags |= Notification.FLAG_AUTO_CANCEL;
								notificationManager.notify(111, n1);
							}
							else {
								Notification n2 = new NotificationCompat.Builder(Exchanger.thisContext)
						        .setContentTitle("HazardDetector Alert")
						        .setContentText("frame no: "+yCounter+";score: "+ score+";")
						        .setSmallIcon(R.drawable.noscream)
						        .setContentIntent(pIntent).build();
								// Hide the notification after its selected
								n2.flags |= Notification.FLAG_AUTO_CANCEL;
								notificationManager.notify(222, n2);
								
							}
							yCounter = 0;	// Reset counter for computing yAvg[] of the next sound event
							yAvg = new double[numMfcc*3];
						}
					}
				}
				counter++;				
			}
		    
			/***END of sound detection***/
			
		}
		
	};
	

	private static void concateMfcc(double[] cc, double[] dcc, double[] ddcc, double[] y) {
		int numMfcc = cc.length-1;
		for (int i=1; i<cc.length; i++) {
			y[i-1] = cc[i];
			y[numMfcc+i-1] = dcc[i];
			y[2*numMfcc+i-1] = ddcc[i];
		}
	}
	
	private static void accumulateMfcc(double[] y, double[] yAvg) {
		for (int i=0; i<y.length; i++) {
			yAvg[i] += y[i];
		}
	}
	
	private static void averageMfcc(double[] yAvg, int numFrames) {
		for (int i=0; i<yAvg.length; i++) {
			yAvg[i] /= numFrames;
		}
	}

	private static float[] extractOneFrameFromList(int M) {
		float x[] = new float[samplePerFrame];
		int n = 0;
		for (int i=0; i<M; i++) {						
			for (int j=0; j<subframeList.get(i).length; j++) {
				x[n] = subframeList.get(i)[j];
				n++;
			}
		}
		subframeList.remove(0);		// Remove the current subframe to shift the next frame by one subframe
		return x;
	}
	
	private String getTempFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);
         
        if(!file.exists()){
            file.mkdirs();
        }         
        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);         
        if(tempFile.exists()) {
        		tempFile.delete();
        }
        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
	}
			
	private void saveAudioToWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = 16 * RECORDER_SAMPLERATE * channels/8;         
        byte[] data = new byte[recordBufferSize];
         
        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;
            writeWaveFileHeader(out, totalAudioLen, totalDataLen, longSampleRate, channels, byteRate);             
            while(in.read(data) != -1){
            		out.write(data);
            }             
            in.close();									
            out.close();
        } catch (FileNotFoundException e) {
                e.printStackTrace();
        } catch (IOException e) {
                e.printStackTrace();
        }
	}
	private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
	}
	private void writeWaveFileHeader(
        FileOutputStream out, long totalAudioLen,
        long totalDataLen, long longSampleRate, int channels,
        long byteRate) throws IOException {
     
	    byte[] header = new byte[44];
	     
	    header[0] = 'R';  // RIFF/WAVE header
	    header[1] = 'I';
	    header[2] = 'F';
	    header[3] = 'F';
	    header[4] = (byte) (totalDataLen & 0xff);
	    header[5] = (byte) ((totalDataLen >> 8) & 0xff);
	    header[6] = (byte) ((totalDataLen >> 16) & 0xff);
	    header[7] = (byte) ((totalDataLen >> 24) & 0xff);
	    header[8] = 'W';
	    header[9] = 'A';
	    header[10] = 'V';
	    header[11] = 'E';
	    header[12] = 'f';  // 'fmt ' chunk
	    header[13] = 'm';
	    header[14] = 't';
	    header[15] = ' ';
	    header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
	    header[17] = 0;
	    header[18] = 0;
	    header[19] = 0;
	    header[20] = 1;  // format = 1
	    header[21] = 0;
	    header[22] = (byte) channels;
	    header[23] = 0;
	    header[24] = (byte) (longSampleRate & 0xff);
	    header[25] = (byte) ((longSampleRate >> 8) & 0xff);
	    header[26] = (byte) ((longSampleRate >> 16) & 0xff);
	    header[27] = (byte) ((longSampleRate >> 24) & 0xff);
	    header[28] = (byte) (byteRate & 0xff);
	    header[29] = (byte) ((byteRate >> 8) & 0xff);
	    header[30] = (byte) ((byteRate >> 16) & 0xff);
	    header[31] = (byte) ((byteRate >> 24) & 0xff);
	    header[32] = (byte) (1 * 16 / 8);  // block align
	    header[33] = 0;
	    header[34] = 16;  // bits per sample
	    header[35] = 0;
	    header[36] = 'd';
	    header[37] = 'a';
	    header[38] = 't';
	    header[39] = 'a';
	    header[40] = (byte) (totalAudioLen & 0xff);
	    header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
	    header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
	    header[43] = (byte) ((totalAudioLen >> 24) & 0xff);	
	    out.write(header, 0, 44);
	}
	
	/*
	 * This method use the date and time information (from Date object) as the event ID
	 * It will also append the path of the external storage directory as the prefix.
	 */
	private String getEventID(){
        Date now = new Date();
        return (""+now.getTime());
	}

	/*
	 * Return the full name of a wave file to be saved, with full path to the SD card
	 * as prefix. Create folder if necessary
	 */
	private String getFullname(String eventID) {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath,AUDIO_RECORDER_FOLDER);        
		if(!file.exists()){
    			file.mkdirs();
		}
		return file.getAbsolutePath() + "/" + eventID;
	}
			
	/*
	 * Save event ID and score to a log file. 
	 */
	private void saveScoreToTextFile(String eventID, String logFilename, double score) {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);        
        if(!file.exists()){
        		file.mkdirs();
        } 
        String fullname = file.getAbsolutePath() + "/" + logFilename; 
		try {
			PrintWriter pw = new PrintWriter(new FileWriter(fullname, true));
			pw.printf("%s %10.3f\n",eventID, score);
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
	
	
	public void stopRecording() {	
	    // stops the recording activity
	    if (null != recorder) {
	    		isRecording = false;
	        recorder.stop();
	        recorder.release();
	        recorder = null;
	        recordingThread = null;
	        vadShortData = null;
	        vadBufferedShortData = null;
	        vadBufferedEnergy = null;
	        handler.removeCallbacks(processData);
	        if (bufferedShortData != null) {
	        		bufferedShortData.clear();
		    }
	        if (subframeList != null) {
		        subframeList.clear();
	        }
	    }
	}

}
