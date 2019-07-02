/**
 * This model caclculate the probability distribution for the player.
 * However it is not efficient as it is always assigning1 for the path with the lowest completion time
 */
package Model;

import java.io.IOException;

import HelperClasses.FileManipulation;
import HelperClasses.Output;
import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class ProbabilityDistributionLP {
	public int ct []; //completion time of all the paths in the player graph
	public int playerId;//player id for which we are calculating the probability distribution
	public IloCplex cplex;
	public IloNumVar [] x;//probability distribution
	public double objectiveValue;
	
	public ProbabilityDistributionLP (int [] ct, int playerId) throws IloException
	{	
		this.playerId = playerId;
		this.ct=ct;
		this.x = new IloNumVar [this.ct.length];
		this.cplex = new IloCplex();
	}
	
	/**
     * This function initialize the decision variables
     * 
     * @throws IloException
     */
    public void initializeDecisionVariables() throws IloException
    {
    	
    	for(int i =0; i<this.ct.length; i++)
 	    {
 	    	this.x[i] = this.cplex.numVar(0.0, 1.0,"x "+i);			
 	    }
    }
	
    
	  /**
     * This function will build the  model by building the constraints
     * @throws IloException 
     */
    public void buildILPModel() throws IloException
    { 
		this.initializeDecisionVariables();
   
	    
	    /***************************************************************************************************************************
		 * *************************************************Objective function*****************************************************
		 * *************************************************************************************************************************/
	
		//minimize the sum of completion times
		//loop over services
		IloNumExpr obj = cplex.numExpr();
	    for(int i =0; i<this.x.length; i++)
	    {
	    	obj = cplex.sum(obj, cplex.prod(this.x[i], this.ct[i]));
	    }
		cplex.addMinimize(obj);
		
		IloNumExpr const1 = cplex.numExpr();
		 for(int i =0; i<this.x.length; i++)
	    {
			 const1= cplex.sum(const1, this.x[i]);
	    }
		
		 cplex.addEq(const1, 1);
		 
    }
    
    /**
     * This method will run the ILP model 
     * Export the model to a file called SchedulinRoutingDeadline.lp
     * Report the results (values of the variables) to a file called SchedulinRoutingDeadlineResult.lp
     * 
     * @param resultsFile path to the file where to dump results
     * @throws IloException 
     */
    public void runILPModel(String resultsFile) throws IloException
    {
    	
    	try {
			this.cplex.exportModel("probabilityDistributionResults.lp");
			if (cplex.solve()) {
				this.objectiveValue = this.cplex.getObjValue();
				System.out.println("solved "+this.cplex.getObjValue());	
				//System.out.println(this.cplex.getStatus());
				
				//print results (values of the decision variables)
				this.reportResults(resultsFile);					
			}
			else
			{
				this.objectiveValue = -1;
				//System.out.println(this.cplex.getStatus());
				System.out.println(" NOT solved ");	
			}
			//this.cplex.end();	
			
		} catch (IloException e) {
			System.out.println ("ERROR RUNNING ILP Model");		
			e.printStackTrace();
		}    	
    		    	
    }
    
    public String toString()
    {
    	String st="";
    	st+=" Probability distribution LP MODEL \n";
    	st+= "\t Parameters:\n";	
    	st+="\t\t Player Id: "+this.playerId+"\n";
		st+= "\t\t"+Output.printArray(this.ct, "Completion Times Of Paths", "CT");		
		
		return st;
		
    }
    
    /**
     * This method will report the ILP inputs and results to a file called ILP/ProbabilityLPResults_"+playerId
     * It will also print the results to the console
     * 
     * @param filePath path to the file  where to write the results ("testResults/ProbabilityLPResults.txt")
     */
    public void reportResults(String filePath){
    	String st ="";
    			
    	st+=this.toString();
    	
    	//print results
    	st+="\t RESULTS\n";
		try {
			st+=String.format("\t\t Objective = %f\n",this.cplex.getObjValue());
			st+="\t\t"+Output.printArray(this.x,"x[p]", "\t\tc",this.cplex);
			
			//write everything in a file
			FileManipulation outputFile =  new FileManipulation(filePath);
			outputFile.writeInFile(st);
			System.out.println(st);
			
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    }
}
