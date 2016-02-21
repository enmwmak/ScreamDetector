/*
 * Authors: Man-Wai MAK, Dept. of EIE, The Hong Kong Polytechnic University.
 * Version: 1.0
 * Date: March 2015
 * 
 * This file is subject to the terms and conditions defined in
 * file 'license.txt', which is part of this source code package. 
 */

package edu.polyu.vad;

import java.util.Iterator;
import org.apache.commons.collections.Buffer;

/*
 * This class is for detecting the start and end of sound event. It is designed
 * in such a way that it can be worked in real-time, i.e., it makes decision on
 * the start and end of sound events purely on the energy profile obtained from
 * sound signal so far.
 */

@SuppressWarnings("unchecked")
public class VAD {
	public static final int MODE_MEAN = 0;
	public static final int MODE_ALL = 1;
	public static final int SILENCE = 0;
	public static final int NONSILENCE = 1;
	public double threshold = 0.0;						// Energy threshold below which the status is SILENCE
	private int status;									// Status of this VAD object (SILENCE or NONSILENCE)
	private boolean soundDetected;						// True if sound has been detected
	private int numSilFrmsAfterNonSil;					// No. of silence frames after non-silence status
	private int endOfEventTh;							// No. of frames after changing from SILENCE to NONSILENCE 
														// for which the event is considered to be end

	public VAD(double threshold, int endOfEventTh) {
		this.threshold = threshold;
		this.endOfEventTh = endOfEventTh;
		numSilFrmsAfterNonSil = 0;
		soundDetected = false;
	}
	
	/*
	 * Update the VAD energy threshold
	 */
	public void setThreshold(double th) {
		this.threshold = th;
	}
	
	/*
	 * Update the VAD status according to the input energy profile
	 */
	public void update(Buffer buf, int mode) {
		double[] energy = getEnergyProfile(buf);
		if (isSilence(energy, mode) == true) {
			status = SILENCE;
			if (soundDetected == true) {
				numSilFrmsAfterNonSil++;					// Go from NONSILENCE to SILENCE, possibly end of sound event
			}
		} else {
			soundDetected = true;						// If the function reaches this point, sound has been detected,
			status = NONSILENCE;							// so, set status to NONSILENCE
			numSilFrmsAfterNonSil = 0;					// Prepare for detecting end of event
		}
	}
	
	
	/*
	 * Return true if the profile indicates that a sound event has already started OR
	 * if the status goes from NONSILENCE to SILENCE, indicating potential end of sound event.
	 * Otherwise it returns false. Should call updateStatus() before calling this method
	 */
	public boolean isEventStarted() {
		if (status == SILENCE) {						// Event can be either started but energy drop below threshold or not started yet 
			if (soundDetected == true) {
				return true;							// Sound has already been detected, so return true
			}
			return false;							// No sound has been detected so far, so return false
		} 
		return true;									// Sound event has already started.
	}

	/*
	 * Return true if the sound event has ended. The function set the status of the VAD object
	 * accordingly. If the caller want to detect the next event, it must also call reset() to
	 * reset the status variables.
	 */
	public boolean isEventEnded() {
		if (soundDetected == true && status == SILENCE) {
			if (numSilFrmsAfterNonSil > endOfEventTh) {
				return true;
			}
		}
		return false;
	}

	/*
	 * Force the VAD as if it detects an end of sound event
	 */
	public void setEndOfEvent() {
		soundDetected = true;
		status = SILENCE;
		numSilFrmsAfterNonSil = endOfEventTh + 1;
	}
	
	/*
	 * Reset the VAD object status so that it is ready for detecting the next event
	 */
	public void reset() {
		soundDetected = false;
		numSilFrmsAfterNonSil = 0;
		status = SILENCE;
	}
	
	/*
	 * Extract c0 (log-energy) profile from the FIFO object buf. 
	 */
	private double[] getEnergyProfile(Buffer buf) {
		int numFrames = buf.size();
		double energy[] = new double[numFrames];
		Iterator<double[]> it = buf.iterator();
		int n = 0;
		while (it.hasNext()) {
			energy[n++] = it.next()[0];
		}
		return energy;
	}
		
	/*
	 * Return true if the energy profile indicates silence (i.e., no sound); 
	 * otherwise return false
	 */
	private boolean isSilence(double[] energy, int mode) {
		boolean silence = false;
		switch(mode) {
			case MODE_MEAN: 
				silence = detectByMeanEnergy(energy, threshold);
				break;
			case MODE_ALL:
				silence = detectbyAllEnergy(energy, threshold);
				break;
		}
		return silence;
	}
	


	/*
	 * Extract c0 (log-energy) profile from the Fifo object buf. 
	 * Return true if the profile indicates silence (i.e., no sound); 
	 * otherwise return false
	 */
	public static boolean detect(Buffer buf, int mode, double threshold) {
		int numFrames = buf.size();
		double energy[] = new double[numFrames];
		Iterator<double[]> it = buf.iterator();
		int n = 0;
		while (it.hasNext()) {
			energy[n++] = it.next()[0];
		}
		return detect(energy, mode, threshold);
	}
	
	
	/*
	 * Return true if the energy profile indicates silence (i.e., no sound); 
	 * otherwise return false
	 */
	public static boolean detect(double[] energy, int mode, double threshold) {
		boolean silence = false;
		switch(mode) {
			case MODE_MEAN: 
				silence = detectByMeanEnergy(energy, threshold);
				break;
			case MODE_ALL:
				silence = detectbyAllEnergy(energy, threshold);
				break;
		}		
		return silence;
	}
	
	
	/*
	 * Return false (nonsilence) if the mean of energy[] is larger than the threshold
	 */
	private static boolean detectByMeanEnergy(double energy[], double threshold) {
		boolean silence = false;
		double meanEnergy = 0.0;
		for (double e : energy) {
			meanEnergy += e;
		}
		meanEnergy /= energy.length;
		if (meanEnergy < threshold) {
			silence = true;
		}
		return silence;
	}
	
	/*
	 * Return false (non-silence) only if all frames in energy[] are larger than the threshold
	 */
	private static boolean detectbyAllEnergy(double energy[], double threshold) {
		boolean silence = false;
		for (double e : energy) {
			if (e < threshold) 
				silence = true;
		}
		return silence;
	}	
	
}

