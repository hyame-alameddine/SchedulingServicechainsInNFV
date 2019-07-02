/**
 * This is a helper class that will allow us to stop the pricing when getting 
 * a feasible solution with a certain gap from the optimal.
 * 
 */
package CallBacks;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;

public class MyMIPCallBack extends IloCplex.MIPCallback{
			
		double acceptableGap;
		boolean _aborted;
		
		public MyMIPCallBack(){
		
		}
		
		//to be used when we want to stop the model whenever we reach a solution with the acceptable gap
		public MyMIPCallBack(double _acceptableGap){
			acceptableGap = _acceptableGap;
		}
		
		public double getGap() throws IloException{
			double gap = ((getBestObjValue() - getIncumbentObjValue())/ (1e-10 + Math.abs(getBestObjValue())));		
			return gap;
		}
	
		
	    public void main() throws IloException {
	       
	    	//abort when the gap is reached
	      if((getGap() <= acceptableGap)){
	    	  System.out.println("");
	          System.out.println("Good enough solution at gap = "+ (getGap()*100)  + "%, quitting."); 
	          abort();
	      } 
	      else
	    	  System.out.println("Gap is still = "+ (getGap()));
	    }
	    
	    
	   
 }


