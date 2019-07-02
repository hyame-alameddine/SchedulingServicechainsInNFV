/**
 * This class will hold the solution obtained by the Scheduling model
 * Mainly used for online batch scheduling
 */
package HelperClasses;

import java.io.Serializable;

public class SchedulingSolution implements Serializable {
		
	//	this holds the objective value = number of admitted services;
		public int objectiveValue;
		
		//specifies that the traffic of service s is processing at time slot delta  on middlebox n scheduled/mapped to VNF f
		public int[][][][] y;//y[s][n][f][delta]

		
		//designates that a middlebox o(e) of service s begins the transmission of traffic to its successor middlebox d(e)  at time slot \delta  on virtual link e  (1) (or not, 0). 
		public int[][][]theta;//theta [s][delta][e]
		
		// indicates that the virtual link e is used for traffic transmission between middleboxes o(e), d(e) of service s  at time slot \delta (1)   (or not, 0).
		public int[][][]thetaHat;//thetaHat [s][delta][e]
		
		public SchedulingSolution()
		{
			
		}
		public SchedulingSolution (int objectiveValue, int[][][][] y, int[][][]theta, int[][][]thetaHat )
		{
			this.objectiveValue = objectiveValue;
	
			this.y = y;
			this.theta = theta;
			this.thetaHat = thetaHat;
		}
		
		public String toString()
		{
			String st = "";
			st+=String.format("\t\t Objective = %d\n",this.objectiveValue);

			st+="\t\t"+Output.printQuadrupleArray(y,"y[s][N][F][DELTA]", "\t\ty");
				
			st+="\t\t"+Output.printTripleArray(theta,"theta[s][DELTA][E]","\t\ttheta");
			st+="\t\t"+Output.printTripleArray(thetaHat,"thetaHat[s][DELTA][E]","\t\tthetaHat");
			
			return st;
		}
}
