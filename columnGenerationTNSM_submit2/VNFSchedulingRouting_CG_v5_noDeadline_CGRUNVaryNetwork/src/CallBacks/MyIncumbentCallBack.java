/**
 * This is a helper class that will allow us to stop the pricing when getting 
 * a feasible solution with a certain gap from the optimal.
 * 
 */
package CallBacks;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;

public class MyIncumbentCallBack extends IloCplex.IncumbentCallback{

	public MyIncumbentCallBack(){
	
	}

	public boolean incumbentGreaterThanEpsilon () throws IloException
	{
		if (getIncumbentObjValue()>=1 )//>1e-10//1
		{
			return true;
		}
		
		return false;
	}
	
    public void main() throws IloException
    {
    	//abort when whenever I get an objective >epsilon
    	if(incumbentGreaterThanEpsilon())
    	{
    		abort();
    	}
	 }
 }



