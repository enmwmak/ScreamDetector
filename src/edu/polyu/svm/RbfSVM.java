package edu.polyu.svm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * @author MAK Man-Wai, The Hong Kong Polytechnic University.
 */


public class RbfSVM implements SVM {
	private double[][] sv; 			// support vectors
	private double[] a;				// alpha_i * y_i
	private double bias;
	private int dim;
	private int numSV;
	private double sigma;			// RBF width

	public RbfSVM(int dim, double sigma) {
		this.dim = dim;
		bias = 0.0;
		this.sigma = sigma;
	}
	
	public RbfSVM() {
	}

	public RbfSVM(int dim) {
		this.dim = dim;
		bias = 0.0;
		this.sigma = 1.0;
	}

	/**
	 * Read weight file containing one line corresponding to sigma, bias, a[], and sv[][], as follows
	 * sigma b:a_1 x_11 ... x_1D:a_2 x_21 ... x_2D: ...
	 */
	public void readSVM(String svmFile) {
		Scanner s = null;
		try {
			s = new Scanner(new File(svmFile));
			loadSVM(s.nextLine());
		} catch (IOException e) {
			System.err.println(e);
			System.exit(1);
		} finally {
			if (s != null) {
				s.close();
			}
		}
	}

	/**
	 * Read input stream containing one line corresponding to sigma, bias, a[], and sv[][], as follows
	 * sigma b:a_1 x_11 ... x_1D:a_2 x_21 ... x_2D: ...
	 */
	public void readSVM(InputStream is) {
		Scanner s = null;
		try {
			s = new Scanner(is);
			loadSVM(s.nextLine());
		} finally {
			if (s != null) {
				s.close();
			}
		}
	}
	
	
	/**
	 * Given a line (String object) containing poly svm parameters, 
	 * extract p, bias, a[], amd sv[][] from the line 
	 */
	public void loadSVM(String line) {
		String[] svParaSet = line.trim().split(":");
		this.numSV = svParaSet.length-1;		// 1st group contains p and bias
		String[] values = svParaSet[0].trim().split("\\s+");
		this.sigma = Double.parseDouble(values[0]);
		this.bias = Double.parseDouble(values[1]);
		this.a = new double[numSV];
		this.sv = new double[numSV][];
		for (int i = 0; i<numSV; i++) {
			sv[i] = new double[dim];
			values = svParaSet[i+1].trim().split("\\s+");
			if (values.length == dim+1) {	// a_i x_i1 ... x_iD
				this.a[i] = Double.parseDouble(values[0]);
				for (int j = 0; j < dim; j++) {
					sv[i][j] = Double.parseDouble(values[j+1]);
				}
			}
		}
	}

	/**
	 * Compute the score of the RbfSVM, given input x[]
	 */
	public double compScore(double[] x) {
		double score = 0;
		double sigma2 = sigma * sigma;
		for (int i = 0; i < numSV; i++) {	// sum_i a_i*exp{-0.5*(||x_i - x||^2)/sigma^2
			score += a[i]*Math.exp(-0.5*edist2(sv[i], x)/sigma2);
		}
		score = score + bias;
		return score;
	}
	
	/*
	 * Square of Euclidean distance between x[] and y[]
	 */
	private double edist2(double[] x, double y[]) {
		int N = x.length;
		double sum = 0.0;
		for (int j=0; j<N; j++) {
			double d = x[j] - y[j];
			sum += d*d;
		}
		return sum;
	}
}
