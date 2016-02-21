package edu.polyu.hazardalert.vad;

import java.util.Iterator;
import org.apache.commons.collections.Buffer;

@SuppressWarnings("unchecked")
public class VAD {
	public static final int MODE_MEAN = 0;
	public static final int MODE_ALL = 1;
	public static final int SILENCE = 0;
	public static final int NONSILENCE = 1;
	public static double threshold = 0.0;

	public static boolean detect(Buffer buf, int mode, double threshold) {
		boolean silence = false;
		int numFrames = buf.size();
		double energy[] = new double[numFrames];
		Iterator<double[]> it = buf.iterator();
		int n = 0;
		while (it.hasNext()) {
			energy[n++] = it.next()[0];				// c0 = log(Energy)
		}

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
	 * Return false (nonsilence) only if all frames in energy[] are larger than the threshold
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

