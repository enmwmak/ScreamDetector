/*
 * Authors: Man-Wai MAK and Wing-Lung Henry LEUNG, Dept. of EIE, The Hong Kong Polytechnic University.
 * Version: 1.0
 * Date: March 2015
 * 
 * This file is subject to the terms and conditions defined in
 * file 'license.txt', which is part of this source code package.
 */

package edu.polyu.screamalert;

import android.media.AudioFormat;

public class Config {
	
	// Filenames and folder related to this app
	static final String LOG_FILE_NAME = "logfile.txt";		// Set default log file name
	static final String SVM_PARA_FILE_FEMALE = "svm/female_svmdet_rbf_Zopo980_20130809_mfcc+vq.dat";
	static final String SVM_PARA_FILE_MALE = "svm/male_svmdet_rbf_Zopo980_20130809_mfcc+vq.dat";
	static final String AUDIO_RECORDER_FOLDER = "ScreamDetector";
	static final String AUDIO_RECORDER_TEMP_FILE = "record_temp";
	
	// Constant and Variables related to VAD */
	static final double VAD_DEFAULT_TH = 0.0;   				// 12.5 for debugging; max is 14.82; 0.0 for auto finding threshold
	static final int endEventTh = 50;						// No. of silence frames after the event, above which the event is considered finished.
	static final int MIN_NUM_SND_FRMS = 40;					// 40*8ms = 0.32s after VAD
	static final double MIN_SCORE = -1000.0;					// Minimum possible score
	static final double VAD_MEAN_FACTOR = 2.0;
	static final double VAD_STD_FACTOR = 7.0;
	static final int ROLL_BACK = 40;							// No. of frames to rollback when start of event is detected	(must be <= enBufSize)
	static final double STABILIZE_TIME = 2.0;				// Time (in sec) for the A/D convert to stabilize (reduce this value to 1 for debugging)
	static final double RECORD_TIME = 3;						// Time (in sec) to record after A/D stabilize time (reduce this value to 1 for debugging)
	static final int ENBUFSIZE = 50;

	// Variables related to audio recording */
	static final int RECORDER_SAMPLERATE = 16000;
	static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
	static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

	// Variables related to MFCC and spectral analysis */
	static final int NUM_MFCC = 12;							// 12 MFCC (not include C0) ==> 39-dim acoustic vectors
	static final double FRAME_RATE = 125;					// 125Hz frame rate ==> frameShift is 8ms <=> 128 samples at 16kHz
	static final int SAMPLE_PER_FRM = 512; 					// 256 for 32ms per frame at 8kHz; 512 for 32ms per frame at 16kHz

	// Variables related to SVM
	static final String SVM_TYPE = "edu.polyu.svm.RbfSVM";	// Use RBF-SVM by default
	
	// Decision thresholds for high, medium, and low sensitivity. 
	// As ListPreference does not support double, so I store the thresholds as String array
	static final String femaleDecTh[] = new String[]{"-0.8", "-0.25", "0.2"};
	//static final String maleDecTh[] = new String[]{"-0.25", "-0.45", "-1.0"};	// These numbers should be reversed. Henry may make a mistake here
	static final String maleDecTh[] = new String[]{"-2", "-1.8", "-1.2"};
}
