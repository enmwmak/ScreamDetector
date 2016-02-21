package edu.polyu.svm;

import java.io.InputStream;

/**
 * @author MAK Man-Wai, The Hong Kong Polytechnic University.
 */


public interface SVM {
	public void readSVM(String weightFile);
	public void readSVM(InputStream is);
	public void loadSVM(String line); 
	public double compScore(double[] x);
}
