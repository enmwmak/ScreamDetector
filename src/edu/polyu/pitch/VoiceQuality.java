package edu.polyu.pitch;

import java.util.Iterator;

import org.apache.commons.collections.Buffer;

public class VoiceQuality {
	public static final double JITTER_MEAN = 0.0125;
	public static final double JITTER_STD = 0.0213;
	public static final double SHIMMER_MEAN = 0.0175;
	public static final double SHIMMER_STD = 0.0223;
	
	public static double getJitter(Buffer pitchBuf) {
		double pitchArray[] = bufferToArray(pitchBuf);
		double jitter = 0.0;
		int bufSize = pitchBuf.size();
		double sum = 0.0;
		for (int i=0; i<bufSize; i++) {
			if (pitchArray[i] == -1) {
				return 0.0;
			}
			sum += pitchArray[i];
		}
		for (int i=0; i<bufSize-1; i++) {
			jitter += Math.abs(pitchArray[i+1] - pitchArray[i]);
		}
		jitter = (jitter/(bufSize-1)) / (sum/bufSize);
		return jitter;
	}
	
	public static double getShimmer(Buffer pitchBuf, Buffer peakBuf) {
		double pitchArray[] = bufferToArray(pitchBuf);
		double peakArray[] = bufferToArray(peakBuf);
		double shimmer = 0.0;
		int bufSize = pitchBuf.size();
		double sum = 0.0;
		for (int i=0; i<bufSize; i++) {
			if (pitchArray[i] == -1) {
				return 0.0;
			}
			sum += peakArray[i];
		}
		for (int i=0; i<bufSize-1; i++) {
			shimmer += Math.abs(peakArray[i+1] - peakArray[i]);
		}
		shimmer = (shimmer/(bufSize-1)) / (sum/bufSize);
		return shimmer;		
	}
	
	/*
	 * Convert FIFO buffer to double array. 
	 */
	@SuppressWarnings("unchecked")
	private static double[] bufferToArray(Buffer buf) {
		int bufSize = buf.size();
		Iterator<Double> it = buf.iterator();
		double array[] = new double[bufSize];
		int i = 0;
		while (it.hasNext()) {
			array[i++] = it.next();
		}
		return array;
	}
}
