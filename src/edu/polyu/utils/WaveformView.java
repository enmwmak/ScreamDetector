/**
 * A view that displays audio data on the screen as a waveform.
 * 
 * Acknowledgment: This waveform display class is dervied from the WaveformView.java 
 * developed by Tony Allevato (https://github.com/allevato)
 * https://github.com/googleglass/gdk-waveform-sample/blob/master/src/com/google/android/glass/sample/waveform/WaveformView.java
 */

package edu.polyu.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceView;

import java.util.Iterator;
import java.util.LinkedList;
 
public class WaveformView extends SurfaceView {

	// The number of buffer frames to keep around (for a nice fade-out visualization).
	private static final int HISTORY_SIZE = 1;

	// To make quieter sounds still show up well on the display, the MAX_AMP_TO_DRAW should be
	// smaller than the maxium possible range for 16bit int (+/- 32768).
	// Any samples that have magnitude higher than this limit will simply be clipped during drawing.
	// However, a small max value for this parameter (e.g., MAX_AMP_TO_DRAW = 8192.0f) will cause 
	// some device to reach this value for background sound in a noisy environment.
	// To solve this problem, the default of MAX_AMP_TO_DRAW is set to half of 16bit max range (32768) and
	// a function compMaxAmpToDraw(double bkgMag) is added to change this value to a lower one in
	// case the background has low amplitude or in the devices with low gain in their A/D coverter.
	private static float MAX_AMP_TO_DRAW = 16384.0f;
	
	// The queue that will hold historical audio data.
	private final LinkedList<short[]> mAudioData;

	private final Paint mPaint;
	
	
	public WaveformView(Context context) {
		this(context, null, 0);
	}

	public WaveformView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public WaveformView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mAudioData = new LinkedList<short[]>();
		mPaint = new Paint();
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setColor(Color.WHITE);
		mPaint.setStrokeWidth(0);
		mPaint.setAntiAlias(true);
	}

	/**
	 * Updates the waveform view with a new "frame" of samples and renders it.
	 * The new frame gets added to the front of the rendering queue, pushing the
	 * previous frames back, causing them to be faded out visually.
	 *
	 * @param buffer
	 *            the most recent buffer of audio samples
	 */
	public synchronized void updateAudioData(short[] buffer) {
		short[] newBuffer;

		// We want to keep a small amount of history in the view to provide a nice fading effect.
		// We use a linked list that we treat as a queue for this.
		if (mAudioData.size() == HISTORY_SIZE) {
			newBuffer = mAudioData.removeFirst();
			System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
		} else {
			newBuffer = buffer.clone();
		}		
		mAudioData.addLast(newBuffer);

		// Update the display.
		Canvas canvas = getHolder().lockCanvas();
		if (canvas != null) {
			drawWaveform(canvas);
			getHolder().unlockCanvasAndPost(canvas);
		}
	}

	/**
	 * Repaints the view's surface.
	 *
	 * @param canvas
	 *            the {@link Canvas} object on which to draw
	 */
	private void drawWaveform(Canvas canvas) {
		// Clear the screen each time because SurfaceView won't do this for us.
		canvas.drawColor(Color.BLACK);

		float width = getWidth();
		float height = getHeight();
		float centerY = height / 2;

		// We draw the history from oldest to newest so that the older audio data is further back
		// and darker than the most recent data.
		int colorDelta = 255 / (HISTORY_SIZE + 1);
		int brightness = colorDelta;

		mPaint.setStrokeWidth(4);

		// Rescale the amplitude of buffer so that it max is <= MAX_AMP_TO_DRAW
		rescaleAudioData(mAudioData, MAX_AMP_TO_DRAW);
		
		for (short[] buffer : mAudioData) {
			mPaint.setColor(Color.argb(brightness, 255, 255, 255));
			float lastX = -1;
			float lastY = -1;
			
			// For efficiency, we don't draw all of the samples in the buffer, but only the ones
			// that align with pixel boundaries. To further reduce computation overhead, only draw 
			// once every 4 pixels. This solves the jittering problem in Samsung G4 where the minBufSize is
			// very small (40ms)
			int xinc = 1;
			if (buffer.length <= 640) {
				xinc = 4;
			}
			for (int x = 0; x < width; x+=xinc) {
				int index = (int) ((x / width) * buffer.length);
				short sample = buffer[index];
				float y = (float) ((sample / MAX_AMP_TO_DRAW) * centerY + centerY);
				if (lastX != -1) {
					canvas.drawLine(lastX, lastY, x, y, mPaint);
				}
				lastX = x;
				lastY = y;
			}
			brightness += colorDelta;
		}
	}
	
	/*
	 * Rescale the audio data so that the maximum amplitude is less than or equal to input maxAmp
	 */
	private void rescaleAudioData(LinkedList<short[]> audioData, final float maxAmp) {
		float newMaxAmp = maxAmp;
		Iterator<short[]> it = audioData.iterator();
		while (it.hasNext()) {
			short buf[] = it.next();
			for (short s : buf) {
				float as = (float)Math.abs(s);
				if (as > newMaxAmp) {
					newMaxAmp = as;
				}
			}
		}
		if (newMaxAmp > maxAmp) {
			it = audioData.iterator();
			while (it.hasNext()) {
				short buf[] = it.next();
				for (int i=0; i<buf.length; i++) {
					buf[i] = (short)((maxAmp/newMaxAmp)*buf[i]);
				}
			}			
		}
	}
	
	/*
	 * Set the static variable MAX_AMP_TO_DRAW according to the background magnitude. 
	 * Limit MAX_AMP_TO_DRAW to 16bit max (32767). The 
	 */
	public void compMaxAmpToDraw(double bkgMag) {
		double newMaxAmp = 500*bkgMag;
		if (newMaxAmp < MAX_AMP_TO_DRAW)
			MAX_AMP_TO_DRAW = (float)newMaxAmp;
	}
}
