package edu.polyu.svm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Scanner;

import edu.polyu.screamalert.Exchanger;

import android.content.res.AssetManager;

/**
 * @author MAK Man-Wai, The Hong Kong Polytechnic University.
 */

public class SVMclassifier {
	private SVM[] svm;
	private int numClasses;
	private int dim;

	/**
	 * Use Java Reflection to create an SVMclassifier of type given by svmType.
	 * Use constructor with with one input (int) parameter
	 * @param numClasses No. of classes in the SVM classifier
	 * @param dim Input dimension
	 * @param svmType Type of SVM, can be "LinearSVM", "PolySVM", or "RbfSVM"	
	 */
	public SVMclassifier(int numClasses, int dim, String svmType) {
		this.numClasses = numClasses;
		this.dim = dim;
		svm = new SVM[numClasses];
		Class<?> cls = null;
		try {
			cls = Class.forName(svmType);
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		for (int i = 0; i < numClasses; i++) {
			try {
				svm[i] = (SVM)cls.getConstructor(int.class).newInstance(dim);	
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
	}

	/**
	 * Create a 1-vs-rest SVM classifier whose parameters are stored in a set of
	 * weight files, one for each class.
	 */
	public void createSVMclassifier(String[] paraFiles) {
		for (int i = 0; i < svm.length; i++) {
			svm[i].readSVM(paraFiles[i]);
		}
	}

	/**
	 * Create a 1-vs-rest SVM classifier whose parameters are stored in one file,
	 * one line per class.
	 */
	public void createSVMclassifier(String compositeParaFile) {
		Scanner s = null;
		try {
			AssetManager assetManager = Exchanger.thisContext.getResources().getAssets();
			InputStream inputStream = null;
			inputStream = assetManager.open(compositeParaFile);
			s = new Scanner(inputStream);

			//s = new Scanner(new File(compositeParaFile));
			for (int i = 0; i < numClasses; i++) {
				svm[i].loadSVM(s.nextLine());
			}
		} catch (IOException e) {
			System.err.println(e);
			System.exit(1);
		} finally {
			if (s != null) {
				s.close();
			}
		}
	}

	public double[] getScores(double[] x) {
		double[] scores = new double[numClasses];
		for (int i = 0; i < numClasses; i++) {
			scores[i] = svm[i].compScore(x);
		}
		return scores;
	}

	public int getClassLabel(double[] x) {
		double[] scores = this.getScores(x);
		return (maxPos(scores) + 1);
	}

	private static int maxPos(double[] z) {
		double maximum = z[0];
		int p = 0;
		for (int i = 1; i < z.length; i++) {
			if (z[i] > maximum) {
				maximum = z[i]; // new maximum
				p = i;
			}
		}
		return p;
	}

	public static double getMaxScore(double[] z) {
		double maximum = z[0];
		for (int i = 1; i < z.length; i++) {
			if (z[i] > maximum) {
				maximum = z[i]; // new maximum
			}
		}
		return maximum;
	}
	
	public static double getAbsSumScore(double[] z){
		double sum = 0.0;
		for (int i = 0; i < z.length; i++) {
			sum = sum + Math.abs(z[i]);
		}
		return sum;
	}
	
	public int getNumClasses() {
		return numClasses;
	}
	
	/**
	 * For testing the classifier
	 */
	public static void main(String[] args) {
		int nClasses = 6;
		int dim = 12;
		String svmType = "edu.polyu.hazardalert.svm.PolySVM";
		String paraFile = "data/svm/svmcl_poly6.dat";			
		//String svmType = "edu.polyu.hazardalert.svm.LinearSVM";
		//String paraFile = "data/svm/svmcl_linear6.dat";		// Text file containing nClasses x (dim+1) matrix		
		//String svmType = "edu.polyu.hazardalert.svm.RbfSVM";
		//String paraFile = "data/svm/svmcl_rbf6.dat";				
		String dataFile = "data/svm/X1.dat";
		SVMclassifier svmc = new SVMclassifier(nClasses, dim, svmType);
		svmc.testSVM(svmc, paraFile, dataFile);
	}

	private void testSVM(SVMclassifier svmc, String paraFile, String dataFile) {
		svmc.createSVMclassifier(paraFile);
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
			label[i] = svmc.getClassLabel(x[i]);
			double scores[] = getScores(x[i]);
			System.out.printf("Sample %d: ",i+1);
			for (int k=0; k<svmc.getNumClasses(); k++) {
				System.out.printf("%.4f ",scores[k]);
			}
			System.out.println("Class " + label[i]);
		}
	}
	
}
