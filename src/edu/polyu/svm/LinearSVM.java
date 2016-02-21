package edu.polyu.svm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

/**
 * @author MAK Man-Wai, The Hong Kong Polytechnic University.
 */

public class LinearSVM implements SVM {
	private double[] weights; // \sum_i\in{SV} alpha_i * y_i * x_i
	private double bias;
	private int dim;

	public LinearSVM(int dim) {
		this.dim = dim;
		weights = new double[dim];
		bias = 0.0;
	}

	public LinearSVM() {
	}

	/**
	 * Read weight file containing one line corresponding to the weights and
	 * bias
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
	 * Read input stream containing one line corresponding to the weights and
	 * bias
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
	 * Given a line (String object) containing space separating values, extract
	 * the weights and bias term from the line
	 */
	public void loadSVM(String line) {
		String[] values = line.trim().split("\\s+");
		int numWeights = values.length - 1;
		if (numWeights == dim) {
			for (int i = 0; i < numWeights; i++) {
				weights[i] = Double.parseDouble(values[i]);
			}
			bias = Double.parseDouble(values[numWeights]);
		}
	}

	/**
	 * Compute the score of the linear SVM given input x[]
	 */
	public double compScore(double[] x) {
		double score = 0;
		for (int i = 0; i < dim; i++) {
			score += weights[i] * x[i];
		}
		score = score + bias;
		return score;
	}
}
