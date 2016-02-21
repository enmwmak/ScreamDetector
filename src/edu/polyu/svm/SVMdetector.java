package edu.polyu.svm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Scanner;

/**
 * @author MAK Man-Wai, The Hong Kong Polytechnic University.
 */


public class SVMdetector {
	private SVM svm;
	private int dim;
	private double threshold;			// Threshold over which the detector will consider the unknown pattern
										// as produced from the positive class

	/**
	 * Use Java Reflection to create an SVMclassifier of type given by svmType.
	 * Use constructor with with one input (int) parameter
	 * @param dim Input dimension
	 * @param svmType Type of SVM, can be "LinearSVM", "PolySVM", or "RbfSVM"	
	 */
	public SVMdetector(int dim, String svmType, double threshold) {
		this.dim = dim;
		this.threshold = threshold;
		Class<?> cls = null;
		try {
			cls = Class.forName(svmType);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		try {
			svm = (SVM) cls.getConstructor(int.class).newInstance(dim);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create an SVM detector (binary SVM) whose parameters are stored in a 
	 * parameter file
	 */
	public void createSVMdetector(String paraFile) {
		svm.readSVM(paraFile);
	}

	/**
	 * Create an SVM detector (binary SVM) whose parameters are stored in a 
	 * parameter file with InputStream 'is'. This function is suitable for
	 * calling from Android device because the files in assets/ folder can
	 * be accessed by 
	 *   	AssetManager assetManager = context.getResources().getAssets();
	 *		InputStream 	inputStream = assetManager.open(paraFile);
	 */
	public void createSVMdetector(InputStream is) {
		svm.readSVM(is);
	}
	
	
	public double getScore(double[] x) {
		double score = svm.compScore(x);
		return score;
	}

	public int getClassLabel(double[] x) {
		double score = this.getScore(x);
		if (score > threshold)
			return 1;
		return 2;
	}

	
	/**
	 * For testing the detector
	 */
	public static void main(String[] args) {
		int dim = 36;		// MFCC+dMFCC+ddMFCC without energy, dEnergy, and ddEnergy
		//String svmType = "edu.polyu.svm.PolySVM";
		//String paraFile = "data/svm/svmdet_poly2.dat";			
		//String svmType = "edu.polyu.svm.LinearSVM";
		//String paraFile = "data/svm/svmdet_linear2.dat";		// Text file containing one row		
		String svmType = "edu.polyu.svm.RbfSVM";
		String paraFile = "data/svm/svmdet_rbf2.dat";				
		String dataFile = "data/svm/Scream1030.dat";
		SVMdetector svmd = new SVMdetector(dim, svmType, 0.0);
		svmd.testSVM(svmd, paraFile, dataFile);
	}

	private void testSVM(SVMdetector svmd, String paraFile, String dataFile) {
		svmd.createSVMdetector(paraFile);
		Scanner scanner = null;
		int N = 0;
		double x[][] = null;
		try {
			scanner = new Scanner(new File(dataFile));		// Obtaining no. of samples
			String line = scanner.nextLine();
			String values[] = line.trim().split("\\s+");
			N = values.length;		
			x = new double[N][dim];
			int j = 0;
			scanner.close();
			scanner = new Scanner(new File(dataFile));		
			while (scanner.hasNextLine()) {					// Reading data into x[][]
				line = scanner.nextLine();
				values = line.trim().split("\\s+");
				for (int i=0; i<values.length; i++) {
					x[i][j] = Double.parseDouble(values[i]);
				}
				j++;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			if (scanner != null) {
				scanner.close();
			}
		}

		// Presenting test vectors to SVM classifier
		int label[] = new int[N];
		for (int i=0; i<N; i++) {
			label[i] = svmd.getClassLabel(x[i]);
			double score = getScore(x[i]);
			System.out.printf("Sample %d: ",i+1);
			System.out.printf("%.4f ",score);
			System.out.println("Class " + label[i]);
		}
	}
}
