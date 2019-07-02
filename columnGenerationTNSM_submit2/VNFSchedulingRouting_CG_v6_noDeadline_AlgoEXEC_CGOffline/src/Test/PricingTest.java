/**
 * This class will verify if the solution provided by the pricing is valid
 */
package Test;

import ilog.concert.IloException;

import java.util.ArrayList;

import Model.CGPricingModel;
import Model.Configuration;
import NFV.Middlebox;
import NFV.VLink;

public class PricingTest {
	
	private CGPricingModel pricing;
	
	//this will hold the delta at which each middlebox started processing
	private int [] startedProcessing;
	
	//this will hold the time slot at which a flow started transmitting on a virtual link
	private int [] startedTransmission;
	
	public PricingTest (CGPricingModel pricing)
	{
		this.pricing = pricing;
		this.startedProcessing = new int [this.pricing.N];
		this.startedTransmission = new int [this.pricing.E];
		
		//populate the attribute
		this.populateStartedProcessing();
		this.populateStartedTransmition();
	}
	
	
	/**
	 * This function will populate the startedProcessing attribute based on the value of Y
	 */
	private void populateStartedProcessing ()
	{
		
		for (int i=0; i<this.pricing.N;i++)
		{
			for (int j=0; j<this.pricing.F; j++)
			{
				for (int k=0; k<this.pricing.getDELTA(); k++)
				{
					try {
						
						if (this.pricing.cplex.getValue(this.pricing.y[i][j][k]) == 1)
						{
							this.startedProcessing[i] = k;
						}
					}catch (IloException e){}
					
				}
			}
		}
	}
	
	/**
	 * this function will check if source and destination of a virtual link are hosted
	 * on same server based on the value of h
	 * 
	 * @param vl virtual link
	 * @return true if hosted on same server
	 */
	private boolean areHostedOnSameServer(VLink vl)
	{
		boolean hostedOnsameServer = false;
		
		//check if source and destination of E are hosted on the same server = no transmission
		for (int i=0; i<this.pricing.ks; i++)
		{
			try {
				if (this.pricing.cplex.getValue(this.pricing.h[vl.getSource().getId()][i]) ==1)
				{
					//consider that the link is scheduled 
					hostedOnsameServer = true;
					break;
				}
			} catch (IloException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return hostedOnsameServer;
		
	}
	
	/**
	 * This function will populate the StartTransmitting attribute based on the value of theta
	 */
	private void populateStartedTransmition()
	{
		ArrayList <VLink> vlink = this.pricing.getService().getVirtualLinks();
		for (int j=0; j< this.pricing.E; j++)
		{
			if (this.areHostedOnSameServer(vlink.get(j)))
			{
				//set -1 if no transmission happens on this virtual link
				this.startedTransmission[j] = -1; 
				continue;
			}			
			
			for (int i=0; i<this.pricing.getDELTA(); i++)
			{
				try {
					
					if (this.pricing.cplex.getValue(this.pricing.theta[i][j]) ==1)
					{
						this.startedTransmission[j] = i;
						
					}
					
				}catch (IloException e){}
			}
		}
	}
	/**
	 * This function build O based on the values taken by Y and thetaHat by considering the constraint 
	 * written to get O
	 * 
	 * @return o
	 */
	private int [][][] buildO()
	{
		int ks = this.pricing.ks;
		int delta = this.pricing.getDELTA();
		int E = this.pricing.E;
		
		int [][][] o = new int [ks][ks][delta];
		
		for (int i =0; i<ks; i++)
		{
			for (int j=0; j<ks; j++)
			{
				for (int k=0; k<delta; k++)
				{
					for (int x=0; x<E; x++ )
					{
						  try {
							 
							o[i][j][k] += this.pricing.cplex.getValue(this.pricing.l[i][j][x])*this.pricing.cplex.getValue(this.pricing.thetaHat[k][x])*this.pricing.bs;
						} catch (IloException e) {
							// TODO Auto-generated catch block
							//e.printStackTrace();
						}
					}
				}
			}
		}
		return o;
	}
	
	
	/**
	 * This function will return true if the values of O build based on y and thetaHat are equal to 
	 * those returned by the model
	 * 
	 * @return true if o is correct
	 * 
	 */
	private boolean assertO ()
	{
		boolean isCorrect  = true;
		
		int [][][] o = this.buildO();
					
				
		for (int i =0; i<this.pricing.ks; i++)
		{
			for (int j=0; j<this.pricing.ks; j++)
			{
				for (int k=0; k<this.pricing.getDELTA(); k++)
				{
					try {
						assert o[i][j][k] == this.pricing.cplex.getValue(this.pricing.getO()[i][j][k]) : isCorrect=false;
					} catch (IloException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		
		return isCorrect;
	
	}
	
    
	/**
	 * This function build r based on the value of z given by the pricing model
	 * @return r
	 */
	public int[][] buildR()
	{
		int [][] r = new int [this.pricing.F][this.pricing.getDELTA()];
		
		for (int x=0; x<this.pricing.N; x++)
		{
			for (int i=0; i<this.pricing.F; i++ )
			{
				for (int j=0; j<this.pricing.getDELTA(); j++)
				{
					if (this.pricing.mns[x] == this.pricing.tf[i])
					{
						try {
							r[i][j]+= this.pricing.cplex.getValue(this.pricing.z[x][i][j]);
						} catch (IloException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		}

		return r;
	}
	
	/**
	 * This function verifies that the value of R generate by the pricing is = to that build based on the 
	 * value of z
	 * @return true if values of r are correct
	 */
	public boolean assertR()
	{
		int [][]r = this.buildR();
		boolean isCorrect = true;
		
		for (int i=0; i<r.length; i++)
		{
			for (int j=0; j<r[i].length; j++)
			{
				try {
					assert (r[i][j] == this.pricing.cplex.getValue(this.pricing.getR()[i][j])):isCorrect = false;
				} catch (IloException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
						
			}
		}
		return isCorrect;
	}
	
	/**
	 * this function returns true if the value of V is equal to the one calculated based on the value of y
	 * @return
	 */
	public boolean assertV()
	{
		boolean isCorrect = true;
		int v =0;
		for (int i=0; i<this.pricing.F; i++)
		{
			for (int j=0; j<this.pricing.getDELTA(); j++)
			{
				try {
					v+=this.pricing.cplex.getValue(this.pricing.y[this.pricing.N-1][i][j])*(j+this.pricing.ps[this.pricing.N-1]);
				} catch (IloException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		try {
			assert (v == this.pricing.cplex.getValue(this.pricing.getV())):isCorrect = false;
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return isCorrect ;
				
	}
	
	/**
	 * This function will verify the correct mapping and scheduling of midlleboxes (values of y and z)
	 * @return true if values of y and z are correct
	 */
	public boolean verifyVNFMapping ()
	{
		boolean isMapped = false;
		for (int i=0; i<this.pricing.N;i++)
		{
			isMapped = false;
			for (int j=0; j<this.pricing.F; j++)
			{
				for (int k=0; k<this.pricing.getDELTA(); k++)
				{
					try {
						
						if (this.pricing.cplex.getValue(this.pricing.y[i][j][k]) == 1)
						{
							//verify that a middlebox is mapped to a VNF of the same type
							if (this.pricing.mns[i] != this.pricing.tf[j])
							{System.out.println("mapp in here8");
								return false;
							}
							
							isMapped = true;
							
													
							//verify that z is assigned during all the processing time of the middlebox
							for (int d = k; d<k+this.pricing.ps[i]; d++)
							{
								if (this.pricing.cplex.getValue(this.pricing.z[i][j][k]) != 1)
								{System.out.println("mapp in here9");
									return false;
								}
							}
							
						}
						
						
					} catch (IloException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			
			if (!isMapped)
			{System.out.println("mapp in here10");
				return false;
			}
		}
		
		return true;
	}
	
	
	/**
	 * This function verifies that the virtual links are mapped and scheduled based on the transmission time
	 * it verifies the values of l, theta, thetahat and makes sure of the capacity constraint
	 * @return
	 */
	public boolean verifyLinkMapping()
	{
		boolean isScheduled = false;
		boolean isMapped = false;
		
		ArrayList <VLink> vlink = this.pricing.getService().getVirtualLinks();
		int transmissionTime = (int) Math.ceil(this.pricing.ws/this.pricing.bs);
		double l;
		VLink vl;
		int sourcePm, destinationPm;
		int linkBw =0;
		for (int k=0; k< this.pricing.E; k++)
		{
			isMapped = false;
			vl = vlink.get(k);
						
			if (this.areHostedOnSameServer(vl))
			{
				isMapped=true;
				continue;
			}
			
			for (int i=0; i<this.pricing.ks; i++)
			{
				for (int j=0; j<this.pricing.ks; j++)
				{
					try {
						
						l = this.pricing.cplex.getValue(this.pricing.l[i][j][k]);
					
						if (l ==1)
						{
							sourcePm = vl.getSource().getMappedToVNF().getPmId();
							destinationPm = vl.getDestination().getMappedToVNF().getPmId();
							
							//set the link bandwidth by checking if link ij or ji is the one that exists
							linkBw = this.pricing.G[i][j] == 1? this.pricing.cij[i][j]: this.pricing.cij[j][i];
							
							if ( linkBw <this.pricing.bs)
							{System.out.println("in here2"+linkBw+" "+this.pricing.bs);
								return false;
							}
							else
							{
								isMapped = true;
							}
						}
					} catch (IloException e) {
						// TODO Auto-generated catch block
						
					}
					
					linkBw =0;
				}
				
			}
			
			if (!isMapped)
			{System.out.println("in here3");
				return false;
			}
		}
		
		//verify the link scheduling based on transmission time
		for (int j=0; j< this.pricing.E; j++)
		{
			if (this.areHostedOnSameServer(vlink.get(j)))
			{
				//consider that the link is scheduled 
				isScheduled = true;
				continue;
			}			
			
			for (int i=0; i<this.pricing.getDELTA(); i++)
			{
				try {
					
					if (this.pricing.cplex.getValue(this.pricing.theta[i][j]) ==1)
					{
						isScheduled = true;
						//check that the link is transmitting during all the transmission time
						for (int k=i; k<i+transmissionTime; k++)
						{
							if (this.pricing.cplex.getValue(this.pricing.theta[i][j]) !=1)
							{System.out.println("in here4");
								return false;
							}
						}
					}
				} catch (IloException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
				}
			}
			
			//return false if the link is not scheduled
			if (!isScheduled)
			{System.out.println("in here5");
				return false;
			}
			
		}
		
		return true;
	}
	

	
	/**
	 * This function will verify the overall service schedule in the sense
	 * that no transmission is done before processing is accomplished on the VNf
	 * @return true if schedule is valid
	 */
	public boolean verifyServiceSchedule ()
	{
		int transmissionTime = (int) Math.ceil(this.pricing.ws/this.pricing.bs);
				
		//loop over middleboxes
		for (int i=0; i< this.pricing.N; i++)
		{
			//the number of links = N-1
			if (i < this.pricing.E)
			{
				//if no transmission on the link continue
				if  (this.startedTransmission[i]==-1)
				{
					continue;
				}
				
				//check that VNF transmitting before processing is done
				if (this.startedTransmission[i]<this.startedProcessing[i]+this.pricing.ps[i])
				{ System.out.println("Sched in here6 "+i+"startedTransmission :"+this.startedTransmission[i]+" startedProcessing "+this.startedProcessing[i]+" ps "+this.pricing.ps[i]);
					return false;
				}
				
				//prevent a null pointer exception if i=0(since we are checking on i-1
				//verify only if transmission exists on the link
				if (i==0 || (i!=0 && this.startedTransmission[i-1]==-1))
				{
					continue;
				}
				//check that the next VNF started processing before transmission on the previous link was finished
				if ( this.startedProcessing[i]<this.startedTransmission[i-1]+transmissionTime)
				{System.out.println("Sched in here7");
					return false;
				}
			}
		}
		
		return true;
		
	}
	
	/**
	 * This function returns true if the values of O,R and V of the pricing are valid
	 * @return
	 */
	public boolean verifyConfiguration ()
	{
		return this.assertO() && this.assertR() &&assertV()
			&&this.verifyLinkMapping()&&this.verifyVNFMapping()&& this.verifyServiceSchedule();
	}
	
	
}
