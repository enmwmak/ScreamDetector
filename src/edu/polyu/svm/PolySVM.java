package edu.polyu.svm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * @author MAK Man-Wai, The Hong Kong Polytechnic University.
 */


public class PolySVM implements SVM {
	private double[][] sv; 			// support vectors
	private double[] a;				// alpha_i * y_i
	private double bias;
	private int dim;
	private int numSV;
	private int p;					// Poly degree

	public PolySVM(int dim, int p) {
		this.dim = dim;
		bias = 0.0;
		this.p = p;
	}
	
	public PolySVM() {
	}

	public PolySVM(int dim) {
		this.dim = dim;
		this.bias = 0.0;
		this.p = 2;
	}

	/**
	 * Read input stream containing one line corresponding to degree, bias, a[], and sv[][], as follows
	 * p b:a_1 x_11 ... x_1D:a_2 x_21 ... x_2D: ...
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
	 * Read weight file containing one line corresponding to degree, bias, a[], and sv[][], as follows
	 * p b:a_1 x_11 ... x_1D:a_2 x_21 ... x_2D: ...
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
	 * Given a line (String object) containing poly svm parameters, 
	 * extract p, bias, a[], amd sv[][] from the line 
	 */
	public void loadSVM(String line) {
		String[] svParaSet = line.trim().split(":");
		this.numSV = svParaSet.length-1;		// 1st group contains p and bias
		String[] values = svParaSet[0].trim().split("\\s+");
		this.p = Integer.parseInt(values[0]);
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
	 * Compute the score of the PolySVM, given input x[]
	 */
	public double compScore(double[] x) {
		double score = 0;		
		for (int i = 0; i < numSV; i++) {	// sum_i a_i(x_i' x + 1)^p
			score += a[i]*Math.pow(dot(sv[i], x)+1,p);
		}
		score = score + bias;
		return score;
	}
	
	private double dot(double[] x, double y[]) {
		int N = x.length;
		double sum = 0.0;
		for (int j=0; j<N; j++) {
			sum += x[j]*y[j];
		}
		return sum;
	}
}
