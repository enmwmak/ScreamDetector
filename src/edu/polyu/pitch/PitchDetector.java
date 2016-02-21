package edu.polyu.pitch;

public interface PitchDetector {
	/**
	 * Analyzes a buffer with audio information and estimates a pitch in Hz.
	 * Currently this interface only allows one pitch per buffer.
	 * 
	 * @param audioBuffer
	 *            The buffer with audio information. The information in the
	 *            buffer is not modified so it can be (re)used for e.g. FFT
	 *            analysis.
	 * @return An estimation of the pitch in Hz or -1 if no pitch is detected or
	 *         present in the buffer.
	 */
	PitchDetectionResult getPitch(final float[] audioBuffer);
}
