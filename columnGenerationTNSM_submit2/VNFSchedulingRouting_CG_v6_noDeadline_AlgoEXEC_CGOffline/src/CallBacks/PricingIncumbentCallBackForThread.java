/**
 * This is the same as PricingIncumbentCallBack but uses CGPricingModelThread
 */
package CallBacks;


import Model.CGMasterModel;
import Model.CGPricingModel;
import Model.CGPricingModelThread;
import Model.Configuration;
import Network.Network;
import ilog.concert.IloException;
import ilog.cplex.IloCplex.UnknownObjectException;

public class PricingIncumbentCallBackForThread extends MyIncumbentCallBack{
	
	private CGPricingModelThread pricingModel;
	private CGMasterModel masterModel;
	public boolean _executed;
	

	
	public PricingIncumbentCallBackForThread(CGPricingModelThread pricing,CGMasterModel masterModel){
		
		this.pricingModel = pricing;
		this.masterModel = masterModel;
		this._executed = false;
		
	}
	
	
	public Configuration buildConfiguration ()
	{
		Configuration c = null;
		Network physicalNetwork = this.pricingModel.getPhysicalNetwork();
		int F = physicalNetwork.getVNFNb();
    	int L = physicalNetwork.getLinksNb();
        int sourceId =0;
        int destinationId =0;
        
        //inputs for the configuration
    	int configV =0;
    	int [][] configR = new int [F][this.pricingModel.getDELTA()];
     	int [][] configO = new int [L][this.pricingModel.getDELTA()];
 
	
     	try {
     		
     		configV = (int)this.getValue (this.pricingModel.v);// cast to int will handle the rounding 
     		
			for (int i=0; i<this.pricingModel.r.length; i++){
				for (int j=0; j<this.pricingModel.r[i].length; j++){			
					//r should be 0 or 1. If it was 0.999999 we set it 1 else we set it to 0 (doing some rounding
					configR[i][j] = this.getValue (this.pricingModel.r[i][j])>0.9? 1:0;
				}
			}			
			
			//loop over links in the network and not 2 loops over servers because we are converting from o_ij^delta to o_l^delta for the master
			for (int i=0; i<L; i++){
				sourceId = physicalNetwork.getLinkList()[i].getSource();
				destinationId = physicalNetwork.getLinkList()[i].getDestination();
				
				for (int delta=0; delta<this.pricingModel.getDELTA(); delta++){	
					//set o_l^delta to o_ij^delta where i is the source of l and j is its destination
					//the below code will allow to handle the case where o should be = bs = 379 and it is = 378.9998 or 379.0000001 or o = -9.112710586123285E-13 instead of 0
					configO[i][delta] = this.getValue (this.pricingModel.o[sourceId][destinationId][delta])>(this.pricingModel.bs-1)?this.pricingModel.bs:0;									
				}
			}    	
			
			//update the configIdPerService to track all configurations created for this service till now and know those which were selected
			c = new Configuration( this.pricingModel.getService(), configV, configR, configO);
			
		} catch (UnknownObjectException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return c;
	}
	
	
	
	public void main() throws IloException
	{
		double incumbentObjValue = this.getIncumbentObjValue();
		//greater than one because we want values with positive reduced cost. 
		if(incumbentObjValue>=1)
    	{
			Configuration c = this.buildConfiguration();   
			
			//store the incumbent solutions without adding them tothe master yet
        	masterModel.incumbentColumns.add(c);
    
    	}
    	
   	}


}
