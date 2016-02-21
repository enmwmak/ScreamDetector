/*
 * Authors: Man-Wai MAK, Dept. of EIE, The Hong Kong Polytechnic University.
 * Version: 1.0
 * Date: March 2015
 * 
 * This file is subject to the terms and conditions defined in
 * file 'license.txt', which is part of this source code package.
 */

package edu.polyu.utils;
import java.util.ArrayList;

public class Vec {

	public static double[] concateVectors(double[] cc, double[] dcc, double[] ddcc) {
		int numMfcc = cc.length-1;					// No e, de, and dde in acoustic vector
		double[] y = new double[numMfcc*3];			// Acoustic vector for SVM classifier
		for (int i=1; i<cc.length; i++) {
			y[i-1] = cc[i];
			y[numMfcc+i-1] = dcc[i];				// dcc[0..numMfcc], dcc[0] is delta c0
			y[2*numMfcc+i-1] = ddcc[i];				// ddcc[0..numMfcc], ddcc[0] is delta delta c0
		}
		return y;
	}
	
	/*
	 * Concate acoustic vectors and voice quality features, excluding energy, denergy and ddenergy
	 */
	public static double[] concateVectors(double[] cc, double[] dcc, double[] ddcc, double[] vq) {
		int numMfcc = cc.length-1;
		double[] y = new double[numMfcc*3+vq.length];
		for (int i=1; i<cc.length; i++) {
			y[i-1] = cc[i];
			y[numMfcc+i-1] = dcc[i];
			y[2*numMfcc+i-1] = ddcc[i];			
		}
		int vqStartPos = numMfcc*3;
		for (int i=0; i<vq.length; i++) {				// Henry, you did not pack vq[] to y[]
			y[vqStartPos + i] = vq[i];
		}
		return y;
	}
	
	/*
	 * This version accepts variable numbers of vectors as input and concatenates the 
	 * vectors into a single double array as output.
	 */
	public static double[] concateVectors(double[]... vectors) {
		double y[];
		int numVecs = vectors.length;
		int dim = 0;
		for (int i=0; i<numVecs; i++) {
			dim += vectors[i].length;
		}
		y = new double[dim];
		int k = 0;
		for (int i=0; i<numVecs; i++) {
			for (double x : vectors[i]) {
				y[k++] = x;
			}
		}
		return y;
	}

	public static double[] getInputVector(ArrayList<double[]> aList) {
		double[] mu = getMeanVector(aList);
		double[] sigma = getStdVector(aList, mu);
		int dim = mu.length;
		double[] vec = new double[2*dim];
		for (int i=0; i<dim; i++) {
			vec[i] = mu[i];
			vec[dim+i] = sigma[i];
		}
		return vec;
	}

	/*
	 * Construct an input vector from an ArrayList for the SVM detector. Z-norm is applied to
	 * the input vector at position pos, with number of elements subject to Znorm determined by
	 * the length of mu_z[].
	 * Example usage:
	 * 		static final double mu_z[] = new double[]{VoiceQuality.JITTER_MEAN, VoiceQuality.SHIMMER_MEAN}; 
	 * 		static final double sigma_z[] = new double[]{VoiceQuality.JITTER_STD, VoiceQuality.SHIMMER_STD}; 
	 * 		double[] vec = Vec.getInputVector(aList, 36, mu_z, sigma_z);

	 */
	public static double[] getInputVector(ArrayList<double[]> aList, int pos, double[] mu_z, double[] sigma_z) {
		double[] vec = getInputVector(aList);				// Construct un-normalized input vector
		return getZnormVector(vec, pos, mu_z, sigma_z);		// Apply Znorm at position pos
	}
	
	
	public static double[] getMeanVector(ArrayList<double[]> aList) {
		int numFrms = aList.size();
		int dim = aList.get(0).length;
		double[] mu = new double[dim];
		for (double[] y : aList) {
			for (int i=0; i<dim; i++) {
				mu[i] += y[i];
			}
		}
		for (int i=0; i<dim; i++) {
			mu[i] /= numFrms;
		}
		return mu;
	}
	
	
	/*
	 * Apply Z-norm to elements in x[], starting at location pos. Use the
	 * Znorm parameters mu[] and sigma[] for normalization. Note that position in an array start from 0.
	 * Example usage:
	 * 		double vec[] = getMeanVector(aList);
	 *		double mu_z[] = new double[]{VoiceQuality.JITTER_MEAN, VoiceQuality.SHIMMER_MEAN}; 
	 *		double sigma_z[] = new double[]{VoiceQuality.JITTER_STD, VoiceQuality.SHIMMER_STD}; 
	 * 		double vecz[] = getZnormVector(vec, 36, mu_z, sigma_z);
	 */
	public static double[] getZnormVector(double[] x, int pos, double[] mu, double[] sigma) {
		double zx[] = new double[x.length];
		for (int i=0; i<x.length; i++) {
			zx[i] = x[i];
		}
		int numElements = mu.length;		// No. of elements in x[] to be Z-normalized
		for (int i=0; i<numElements; i++) {
			zx[pos+i] = (zx[pos+i] - mu[i])/sigma[i];
		}
		return zx;
	}

	public static double[] getStdVector(ArrayList<double[]> aList, double[] mu) {
		int numFrms = aList.size();
		int dim = mu.length;
		double[] sigma = new double[dim];
		for (double[] y : aList) {
			for (int i=0; i<dim; i++) {
				sigma[i] += y[i]*y[i];
			}
		}
		for (int i=0; i<dim; i++) {
			sigma[i] = Math.sqrt(sigma[i]/numFrms - mu[i]*mu[i]);
		}
		return sigma;
	}

}
