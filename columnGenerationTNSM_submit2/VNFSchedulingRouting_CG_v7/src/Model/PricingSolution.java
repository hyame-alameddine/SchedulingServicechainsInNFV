/**
 * This class was only needed update the services and used for giving tabu and sequential ilp the same solution for batch exec
 */
package Model;

import HelperClasses.Output;
import NFV.Service;

public class PricingSolution {

	public int id;
	
	//this id will be set when adding column to master
	public int idPerService;//this specifies the configuration id based on each service. this is to be used to know if the configuration was selected or not
	public Service service;
	public int pricingObjective;
	public int v ; //completion time of the service
	public int [][][] o ;//ijdelta
	public int[][][] y ;//nfdelta 
	public int[][][] z;//nfdelta 
	public int[][] theta; //delta e
	public int[][] thetaHat; //delta e
	public int [][][] l ;//ije
	public int [][] h; //nk
	public int [][]q;//nk
	public int [][][][]g;//kkdelta e
	//this will allow setting the configuration ids for all pricing in incremental manner. It will be equal to the total number of configurations passed to the master -1
	public static int PricingSolutionId =0;
	
	public PricingSolution (  Service s,int pricingObjective, int v, int [][][]o, int[][][]y, int [][][]z, int [][]theta, int [][]thetaHat, int [][][]l,int[][]h,int[][] q, int[][][][] g ){
		this.id = PricingSolutionId;
		PricingSolutionId++;
		this.service= s; 
		this.pricingObjective = pricingObjective;
		this.v = v;
		this.o=o;
		this.y =y;
		this.z=z;
		this.theta = theta;
		this.thetaHat = thetaHat;
		this.l=l;
		this.h=h;
		this.q=q;
		this.g=g;
	}
	
	public String toString ()
	{
		String st ="";
		st+=String.format("\t\t RC = %d\n",this.pricingObjective);
		
		st+="\t\t"+Output.printTripleArray(y,"y[n][F][DELTA]", "\t\ty");
		st+="\t\t"+Output.printTripleArray(z,"z[n][F][DELTA]", "\t\tz");
		st+="\t\t"+Output.printDoubleArray(q,"q[n][ks]","\t\tq"); 
		st+="\t\t"+Output.printDoubleArray(h,"h[n][ks]","\t\th");
		st+="\t\t"+Output.printDoubleArray(theta,"theta[DELTA][E]","\t\ttheta");
		st+="\t\t"+Output.printDoubleArray(thetaHat,"thetaHat[DELTA][E]","\t\tthetaHat");
		st+="\t\t"+Output.printTripleArray(l,"l[ks][ks][E]","\t\tl" );
		st+="\t\t"+Output.printQuadrupleArray(g,"g[ks][ks][DELTA][E]","\t\tg");
		st+="\t\t"+Output.printTripleArray(o,"o[ks][ks][DELTA]","\t\to");
		st+="\t\t --v---\n \t\t v ="+v+"\n\n";
		
		return st;
		
	}
}
