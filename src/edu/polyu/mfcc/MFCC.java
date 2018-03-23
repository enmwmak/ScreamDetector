/*
 * The code in this file is based on and in some part re-written from MFCC.java in
 * https://code.google.com/archive/p/speech-recognition-java-hidden-markov-model-vq-mfcc/
 * by G. Tiwari
  	  Email : gtiwari333@gmail.com,
  	  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/" 
 
 * Modified by: Man-Wai MAK, Dept. of EIE, The Hong Kong Polytechnic University.
 * Version: 1.0
 * Date: March 2015 
 */

package edu.polyu.mfcc;
import java.util.Iterator;
import org.apache.commons.collections.Buffer;

public class MFCC {

	private int numMelFilters = 26;// how many filters. Default setting in Voicebox
	private int numCepstra;// number of mfcc coeffs
	private double preEmphasisAlpha = 0.95;
	private double lowerFilterFreq = 80.00;// FmelLow
	private double samplingRate;
	private double upperFilterFreq;
	private double bin[];
	private int samplePerFrame;
	private int cbin[];
	private double mult[][];									// Pre-computed triangular multiplier for efficiency
	private double dctMat[][];       							// Pre-computed DCT matrix for efficiency 
	FFT fft;

	public MFCC(int samplePerFrame, int samplingRate, int numCepstra) {
		this.samplePerFrame = samplePerFrame;
		this.samplingRate = samplingRate;
		this.numCepstra = numCepstra;
		upperFilterFreq = samplingRate / 2.0;
		fft = new FFT(samplePerFrame);
		cbin = fftBinIndices();									// same for all
		mult = getFilterMultipler(cbin);
		dctMat = getDctMatrix();
	}
	
	private double[][] getDctMatrix() {
		int M = this.numMelFilters;
		double dm[][] = new double[this.numCepstra+1][M];
		for (int n=0; n<=this.numCepstra; n++) {
			for (int m=1; m<=this.numMelFilters; m++) {
				dm[n][m-1] = Math.cos(n*(m-0.5)*Math.PI/M);
			}
		}
		return dm;
	}

	/*
	 * Return an MFCC vector [0..numCeptra] for an input frame, where index 0 
	 * corresponds to the log Energy of the frame.
	 */
	public double[] doMFCC(float[] framedSignal) {
		
		// Compute energy first because Hamming window will change the energy
		double logE = compLogEnergy(framedSignal);
				
		// Hamming window
		float [] hx = hamming(framedSignal);
		
		// Magnitude Spectrum
		bin = magnitudeSpectrum(hx);

		// get Mel Filterbank output 
		double fbank[] = getFilterOutput(bin, cbin);		// More efficient than melFilter()

		// Non-linear transformation (log)
		double f[] = nonLinearTransformation(fbank);
		
		// Cepstral coefficients, by DCT. cepc[0...numCepstra]
		double cepc[] = performDCT(f);
		
		// Scale C_0 to C_p according to voicebox
		scaleCep(cepc);
		
		// Replace C_0 by logEnergy, according to melcepst.m in voicebox
		cepc[0] = logE;
		return cepc;
	}

	private double[] performDCT(double[] f) {
		double cepc[] = new double[this.numCepstra+1];
		for (int n=0; n<=this.numCepstra; n++) {
			for (int m=0; m<this.numMelFilters; m++) {
				cepc[n] += this.dctMat[n][m] * f[m];
			}
		}
		return cepc;
	}
	
	public static double compLogEnergy(float[] x) {
		double e = 0.0;
		int N = x.length;
		for (int n=0; n<N; n++) {
			e += Math.pow(x[n],2);
		}
		return Math.log((1e-38 + e)/N);
	}
	
	private float[] hamming(float[] x) {
		int N = x.length;
		float hx[] = new float[N];
		for (int n = 0; n < N; n++) {
			hx[n] = (float)((0.54-0.46*Math.cos(2*Math.PI*n/N))*x[n]);
		}
		return hx;
	}
	
	private void scaleCep(double[] cepc) {
		double temp1 = Math.sqrt(numMelFilters);
		double temp2 = temp1/Math.sqrt(2.0);
		cepc[0] /= temp1;
		for (int n=1; n<numCepstra+1; n++)
			cepc[n] /= temp2;
	}
	
	private double[] magnitudeSpectrum(float frame[]) {
		double magSpectrum[] = new double[frame.length];
		fft.computeFFT(frame);
		float real[] = fft.getReal();
		float imag[] = fft.getImag();
		for (int k = 0; k < frame.length; k++) {
			magSpectrum[k] = Math.sqrt(real[k]*real[k] + imag[k]*imag[k]);
		}
		return magSpectrum;
	}

	@SuppressWarnings("unused")
	private float[] preEmphasis(float inputSignal[]) {
		// System.err.println(" inside pre Emphasis");
		float outputSignal[] = new float[inputSignal.length];
		// apply pre-emphasis to each sample
		for (int n = 1; n < inputSignal.length; n++) {
			outputSignal[n] = (float) (inputSignal[n] - preEmphasisAlpha * inputSignal[n - 1]);
		}
		return outputSignal;
	}

	private int[] fftBinIndices() {
		int cbin[] = new int[numMelFilters + 2];
		cbin[0] = (int) Math.round(lowerFilterFreq / samplingRate * samplePerFrame);// cbin0
		cbin[cbin.length - 1] = (samplePerFrame / 2);// cbin24
		for (int i = 1; i <= numMelFilters; i++) {// from cbin1 to cbin23
			double fc = centerFreq(i);// center freq for i th filter
			cbin[i] = (int) Math.round(fc / samplingRate * samplePerFrame);
		}
		return cbin;
	}

	private double[][] getFilterMultipler(int[] cbin) {
		int I = cbin[numMelFilters+1] + 1;
		int K = numMelFilters + 2;
		double fm[][] = new double[K][I];
		for (int k = 1; k <= numMelFilters; k++) {
			for (int i = cbin[k - 1]; i <= cbin[k]; i++) {
				fm[k][i] = (double)((double)(i - cbin[k-1])/(double)(cbin[k]-cbin[k-1]));
			}
			for (int i = cbin[k]; i <= cbin[k + 1]; i++) {
				fm[k][i] = (double) ((double)(cbin[k+1]-i)/(double)(cbin[k+1]-cbin[k]));
			}
		}
		return fm;
	}
	
	/*
	 * Based on melFilter() in Tiwari's code. The original codes were commented out
	 */
	private double[] getFilterOutput(double bin[], int cbin[]) {
		double temp[] = new double[numMelFilters + 2];
		for (int k = 1; k <= numMelFilters; k++) {
			double num1 = 0.0, num2 = 0.0;
			for (int i = cbin[k - 1]; i <= cbin[k]; i++) {
				//double tmp1 = (double)((double)(i - cbin[k - 1]) / (double)(cbin[k] - cbin[k - 1]));
				//System.out.printf("%d %.3f\n", i, tmp1);
				num1 += this.mult[k][i]*bin[i];
			}

			for (int i = cbin[k] + 1; i <= cbin[k + 1]; i++) {
				//double tmp2 = (double)((double)(cbin[k+1]-i) / (double)(cbin[k + 1] - cbin[k]));
				//System.out.printf("%d %.3f\n", i, tmp2);
				num2 += this.mult[k][i]*bin[i];
			}
			temp[k] = (num1 + num2);
			//temp[k] = (num1 + num2) / (0.5*(cbin[k+1]-cbin[k-1]));		// Same energy for each channel
		}
		double fbank[] = new double[numMelFilters];
		for (int i = 0; i < numMelFilters; i++) {
			fbank[i] = temp[i + 1];
		}
		return fbank;
	}
	
	
	/**
	 * performs mel filter operation
	 * 
	 * @param bin
	 *            magnitude spectrum (| |) of fft
	 * @param cbin
	 *            mel filter coeffs
	 * @return mel filtered coeffs--> filter bank coefficients.
	 */
	
	@SuppressWarnings("unused")
	private double[] melFilter(double bin[], int cbin[]) {
		double temp[] = new double[numMelFilters + 2];
		for (int k = 1; k <= numMelFilters; k++) {
			double num1 = 0.0, num2 = 0.0;
			for (int i = cbin[k - 1]; i <= cbin[k]; i++) {
				//num1 += ((i - cbin[k - 1] + 1) / (cbin[k] - cbin[k - 1] + 1)) * bin[i];
				double tmp1 = (double)((double)(i - cbin[k - 1]) / (double)(cbin[k] - cbin[k - 1]));
				//System.out.printf("%d %.3f\n", i, tmp1);
				num1 += tmp1*bin[i];
			}

			for (int i = cbin[k]; i <= cbin[k + 1]; i++) {
				//num2 += (1 - ((i - cbin[k]) / (cbin[k + 1] - cbin[k] + 1))) * bin[i];
				double tmp2 = (double)((double)(cbin[k+1]-i) / (double)(cbin[k + 1] - cbin[k]));
				//System.out.printf("%d %.3f\n", i, tmp2);
				num2 += tmp2*bin[i];
			}
			temp[k] = (num1 + num2);
			//temp[k] = (num1 + num2) / (0.5*(cbin[k+1]-cbin[k-1]));		// Same energy for each channel
		}
		double fbank[] = new double[numMelFilters];
		for (int i = 0; i < numMelFilters; i++) {
			fbank[i] = temp[i + 1];
		}
		return fbank;
	}

	/**
	 * performs nonlinear transformation
	 * 
	 * @param fbank
	 * @return f log of filter bac
	 */
	private double[] nonLinearTransformation(double fbank[]) {
		double f[] = new double[fbank.length];
		final double FLOOR = -50;
		for (int i = 0; i < fbank.length; i++) {
			f[i] = Math.log(fbank[i]);
			// check if ln() returns a value less than the floor
			if (f[i] < FLOOR) {
				f[i] = FLOOR;
			}
		}
		return f;
	}

	private double centerFreq(int i) {
		double melFLow, melFHigh;
		melFLow = freqToMel(lowerFilterFreq);
		melFHigh = freqToMel(upperFilterFreq);
		double temp = melFLow + ((melFHigh - melFLow) / (numMelFilters + 1)) * i;
		return inverseMel(temp);
	}

	private double inverseMel(double x) {
		double temp = Math.pow(10, x / 2595) - 1;
		return 700 * (temp);
	}

	protected double freqToMel(double freq) {
		return 2595 * log10(1 + freq / 700);
	}

	private double log10(double value) {
		return Math.log(value) / Math.log(10);
	}
	
	@SuppressWarnings("unchecked")
	public double[] compDeltaMfcc(Buffer buf, int dim) {
		int bufSize = buf.size();
		double dMfcc[] = new double[dim];
		double mfcc[][] = new double[bufSize][];
		Iterator<double[]> it = buf.iterator();
		int i = 0;
		while (it.hasNext()) {
			mfcc[i++] = (double[])it.next();
		}
	
		// Implement the computation of delta MFCC here
		int M = (bufSize-1)/2;
		double sum2 = 0.0;
		for (int m = -M; m <= M; m++) {			// Denominator of deltaMFCC formula
			sum2 += Math.pow(m,2);
		}		
		for (int j = 0; j < dim; j++) {			// For each element in the C0,C1,...
			double sum1 = 0.0;
			for (int m = -M; m <= M; m++) {		// Numerator of deltaMFCC formula
				sum1 += m*mfcc[m+M][j];
			}
			if (sum2 != 0.0) {
				dMfcc[j] = sum1/sum2;
			}
		}
		return dMfcc;	
	}
}
