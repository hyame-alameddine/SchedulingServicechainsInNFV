/**
 * The Pricing model generates a configuration to the master 
 * where each service has a mapping, routing and scheduling
 * 
 * IMPORTANT
 * The input should take into consideration that each service requests a VNF that exists in the network
 * The value of O_{ij}^delta considers only ij in the network even if the route needed is ji
 * 
 * If a middlebox m has a processing time = 0; 
 * the model will give y[m][f][delta] =1 but z[m][f][delta] =0 and r[f][delta] =0
 * 
 * This model is designed to start and end the schedule between 2 timeslots; instead of starting at 0 and finishing max at delta
 * it can start at startSchedule and max finish at delta
 */


package Model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import CallBacks.PricingIncumbentCallBack;
import HelperClasses.FileManipulation;
import HelperClasses.Output;
import HelperClasses.ServicesDriver;
import NFV.Middlebox;
import NFV.Service;
import NFV.VLink;
import Network.Link;
import Network.Network;
import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

public class CGPricingModel {
	public IloCplex cplex;
	
	public CGMasterModel masterModel;

	/***************************************************************************************************************************
	 * ****************************************PARAMETERS***********************************************************************
	 * *************************************************************************************************************************/
	
	//the random connected network. This array will specify if a link exists between 2 nodes
	public int[][] G;		
	
	//specifies capacity of physical links interconnecting the servers 
	public int [][] cij;		
	
	//set of types on VNFs. This is formed as double array to be able to return it 
	public int [] tf ;						
	
	//specifies which VNF is hosted on which server
	public int [][] xfk;		
	
	int DELTA;
	int startSchedule;// this will set the timeline to be between startSchedule and DELTA. It will prevent the service to start before startSchedule
	/***
	 * Below attributes are needed to manage manual inputs
	 */
	public int F;
	public int ks;
	public int N;
	public int E;
	public int bs;//bs service bandwidth
	public int ws; //ws traffic size of the service
	public int [] ps;//processing time of service middleboxes
	public int []mns;//type of middleboxes
	public int [] oe;//origin of service virtual links
	public int []de;//destination of service virtual links
	
	/************************************************/
	
	Service service;
	Network physicalNetwork;
	
			
	/**
	 * Below are the dual variables got from the master model
	 */
	//dual variable associated with the column selection constraint of the master problem
	double	 alpha;
	//dual variable associated with VNF constraint of the master problem
	double [][]beta;//f delta
	//dual variable associated with the link capacity constraint of the master problem
	double [][]gamma;//l delta
	//dual variable associated with the makespan constraint of the master problem
	double phi;

	/***************************************************************************************************************************
	 * ****************************************INITIALZE DECISION VRIABLES******************************************************
	 * *************************************************************************************************************************/
	public IloNumExpr ReducedCostObjective ;
			
	// specifies that a VNF f is processing the traffic of the service 
	public IloIntVar[][] r; 
	
	// specifies the amount of bandwidth used by the service at delta on a link (ij) 
	public IloIntVar[][][] o;
	
	// specifies the completion time of the service
	public IloIntVar v ;
		
	//specifies that the traffic of service s started processing at time slot delta  on middlebox n scheduled/mapped to VNF f
	public IloIntVar[][][] y;
	
	//specifies that the traffic of service s is processing at time slot delta  on middlebox n scheduled/mapped to VNF f
	public IloIntVar[][][] z;
	
	//indicates that middleboxes n, (n+1)  of service s are hosted on the same node k(1) (or not, 0).\\
	public IloIntVar[][] h ;
	
	//specifies that the middlebox n of service s is mapped to a VNF instance hosted on physical server k (1) (or not, 0).
	public IloIntVar[][]q ;
			
	//designates that a middlebox o(e) of service s begins the transmission of traffic to its successor middlebox d(e)  at time slot \delta  on virtual link e  (1) (or not, 0). 
	public IloIntVar[][]theta;
	
	// indicates that the virtual link e is used for traffic transmission between middleboxes o(e), d(e) of service s  at time slot \delta (1)   (or not, 0).
	public IloIntVar[][]thetaHat;
	
	// specifies that the virtual link e  of service s  is routed through physical link (ij)  (1) (or not, (0)).
	public IloIntVar[][][]l;
	
	//Needed for linearization
   public  IloIntVar[][][][]g;
    
    
    /**
     * This constructor is to be used when no need for manual input
     * @throws IloException 
     * 
     */
    public CGPricingModel(Service s, Network physicalNetwork, CGMasterModel masterProblem, int delta, int startSchedule) throws IloException
    {
    	this.cplex = new IloCplex();
    	this.masterModel = masterProblem;
    	
    	this.service = s;
    	this.bs = s.getBandwdith();
    	this.ws = s.getTrafficSize();
    	this.ps = new int [this.service.getMiddleboxes().size()];
    	this.mns= new int [this.service.getMiddleboxes().size()];
    	this.oe = new int [this.service.getVirtualLinks().size()];
    	this.de = new int [this.service.getVirtualLinks().size()];
    	
    	for (int i=0; i<this.service.getMiddleboxes().size(); i++)
    	{
    		this.ps [i] = this.service.getMiddleboxes().get(i).getProcessingTime();
    		this.mns[i] = this.service.getMiddleboxes().get(i).getType();
    	}
    	
    	for (int i=0; i<this.service.getVirtualLinks().size(); i++)
    	{
    		this.oe[i] = this.service.getVirtualLinks().get(i).getSource().getId();
    		this.de[i] = this.service.getVirtualLinks().get(i).getDestination().getId();
    	}
    	
    	this.physicalNetwork = physicalNetwork;
    	ArrayList<int[][]>networkArray = this.physicalNetwork.getPhysicalNetworkArray();
    	
    	//setting the parameters
    	this.G = networkArray.get(0);
    	this.cij = networkArray.get(1);		
		//make sure to choose from the VNF types hosted in the network when generating services. 
		//tf should not be null, if null generateServices will generate services with VNf types not in the network
    	this.tf = networkArray.get(2)[0];
    	this.xfk = networkArray.get(3);
 
    	//printDuals(masterProblem);
    	/*this.alpha = masterProblem.cplex.getDual(masterProblem.serviceRange[this.service.getId()]);//masterProblem.cplex.getDuals(masterProblem.serviceRange)[this.service.getId()];
    	this.beta = this.getDuals(masterProblem.VNFRange,masterProblem);
    	this.gamma = this.getDuals(masterProblem.linkCapacityRange,masterProblem);  */
    	
    	//here
    	this.alpha =  masterProblem.alpha[this.service.getId()];
    	this.beta = masterProblem.beta;
    	this.gamma = masterProblem.gamma;
    	this.phi = masterProblem.phi[this.service.getId()];
    
    	//should get the value for the last column added for this specific service-for all c
    	//int lastColumnForservice = this.getDuals(masterProblem.makespanRange, masterProblem)[this.service.getId()].length-1;
    //	this.phi = this.getDuals(masterProblem.makespanRange, masterProblem)[this.service.getId()][lastColumnForservice];   
    	
    	//this.phi = masterProblem.cplex.getDuals(masterProblem.makespanRange)[this.service.getId()];   //sum over c
    	
    	
    	/*this.alpha = 0.6747056806582243;
   		this.phi = 0.019277305161663552;
   		this.beta = new double [7][delta];
		this.gamma = new double [5][delta];
		
		beta[2][8]=-0.443378; 
				beta[3][5]=-0.812860 ;
				beta[3][8]=-1.236960 ;
				beta[3][9]=-0.424101 ;
				beta[4][1]=-2.049820 ;
				beta[5][3]=-0.017842 ;
				beta[5][4]=-0.056697 ;*/
				
		
		this.startSchedule = startSchedule;
    	this.DELTA = delta;    	
     	this.F = this.tf.length;
    	this.ks = this.G.length;
    	this.N = this.service.getMiddleboxes().size();
    	this.E = this.service.getVirtualLinks().size();
    	
    	
    	//setting the decision variables
		 r = new IloIntVar[F][DELTA];    	
		 o = new IloIntVar[ks][ks][DELTA];   	
		 y = new IloIntVar[N][F][DELTA];
		 z = new IloIntVar[N][F][DELTA];
		 h = new IloIntVar[N][ks];
		 q = new IloIntVar[N][ks];
		 theta = new IloIntVar[DELTA][E];
		 thetaHat = new IloIntVar[DELTA][E];
		 l = new IloIntVar[ks][ks][E];
		 g = new IloIntVar[ks][ks][DELTA][E];
    }
    
    
    public void printDuals() throws UnknownObjectException, IloException
    {
    	System.out.println("serviceRange[serviceId]");

		for (int i=0; i<this.masterModel.serviceRange.length; i++)
		{
			System.out.println("serviceRange["+i+"] = "+this.masterModel.cplex.getDual(this.masterModel.serviceRange[i]));
		}
		
		System.out.println("VNFRange[VNFId][timeslot]");
		for (int i=0; i<this.masterModel.VNFRange.length; i++)
		{
			for (int j=0; j<this.masterModel.VNFRange[i].length; j++)
			{
				if(this.masterModel.cplex.getDual(this.masterModel.VNFRange[i][j])!=0.0)
				System.out.println("VNFRange["+i+"]["+j+"] = "+this.masterModel.cplex.getDual(this.masterModel.VNFRange[i][j]));
			}
		}
		
		System.out.println("linkCapacityRange[linkId][timeslot]");
		for (int i=0; i<this.masterModel.linkCapacityRange.length; i++)
		{
			for (int j=0; j<this.masterModel.linkCapacityRange[i].length; j++)
			{
				if (this.masterModel.cplex.getDual(this.masterModel.linkCapacityRange[i][j])!=0.0)
				System.out.println("linkCapacityRange["+i+"]["+j+"] = "+this.masterModel.cplex.getDual(this.masterModel.linkCapacityRange[i][j]));
			}
		}
		
		System.out.println("MakespanRange[serviceID] ");
		for (int i=0; i<this.masterModel.makespanRange.length; i++)
		{
			System.out.println("makespanRange["+i+"] = "+this.masterModel.cplex.getDual(this.masterModel.makespanRange[i]));
		}
		
    }
    
    /**
     * This constructor is used to handle manual input for the model
     * @param G
     * @param xfk
     * @param cij
     * @param tf
     * @param oe
     * @param de
     * @param bs
     * @param ws
     * @param ps
     * @param mns
     * @param alpha
     * @param phi
     * @param gamma
     * @param beta
     * @throws IloException 
     */
    public CGPricingModel(int[][]G, int [][]xfk, int[][]cij, int[]tf,  int []oe, int []de, int bs, int ws, int[]ps,int[]mns, double alpha, double phi, double [][]gamma, double[][]beta, int DELTA, int startSchedule) throws IloException
    {
    	this.cplex = new IloCplex();
    	this.G = G;
    	this.xfk=xfk;
    	this.cij = cij;
    	this.tf = tf;
     	this.oe = oe;
     	this.de = de;
     	this.E= this.oe.length;
    	this.bs = bs;
    	this.ws = ws;
    	this.ps = ps;
    	this.mns = mns;
    	this.N = this.ps.length;
    	this.F = this.xfk.length;
    	this.ks = this.G.length;   
    	this.alpha = alpha;
    	this.gamma = gamma;
    	this.beta = beta;
    	this.DELTA = DELTA;
    	this.startSchedule = startSchedule;
    	
    	//setting the decision variables
		 r = new IloIntVar[F][DELTA];    	
		 o = new IloIntVar[ks][ks][DELTA];
		 y = new IloIntVar[N][F][DELTA];
		 z = new IloIntVar[N][F][DELTA];
		 h = new IloIntVar[N][ks];
		 q = new IloIntVar[N][ks];
		 theta = new IloIntVar[DELTA][E];
		 thetaHat = new IloIntVar[DELTA][E];
		 l = new IloIntVar[ks][ks][E];
		 g = new IloIntVar[ks][ks][DELTA][E];
		
    }
    
    /**
     * This function initialize the decision variables
     * 
     * @throws IloException
     */
    public void initializeDecisionVariables() throws IloException
    {   	
    	v = this.cplex.intVar(0, Integer.MAX_VALUE, "v");
    	 
    	//loop over VNF
 		for (int f=0; f<F; f++)
	    {
 			 //loop over time slots
 			for (int delta=0; delta<this.DELTA; delta++)
		    {
 				r[f][delta] = cplex.intVar(0, 1,"r "+f+delta);	
 				
 				//if the service is not requesting a VNf of the specified type, set the variable to 0
 				if (!this.service.requestsVNFOfType(this.tf[f]) || delta<this.startSchedule)
 				{
 					cplex.addEq(r[f][delta],0,"r "+f+delta+"=0" );
 				}
		    }
	    }
 		
 	    //loop over servers
 	    for(int i =0; i<ks; i++)
 	    {
 	    	 //loop over servers
 	    	for (int j=0; j<ks; j++)
 	    	{
 	    		 //loop over time slots
 	    		for (int d=0; d<this.DELTA; d++)
 		    	{
 	    			o[i][j][d] = cplex.intVar(0, Integer.MAX_VALUE,"o "+i+j+d);	
 	    			
 	    			//set o for delta<start schedule to 0
 	    			if (d<this.startSchedule)
 	 				{
 	 					cplex.addEq(o[i][j][d],0 );
 	 				}
 		    	}
 	    	}
 	    }
 		
    	//loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=0; delta<DELTA; delta++)
		    	{
    				y[j][f][delta] = cplex.intVar(0, 1,"y "+j+f+delta);	
    				
    				//if the service is not requesting a VNf of the specified type, set the variable to 0
    				if (!this.service.requestsVNFOfType(this.tf[f]) || delta<this.startSchedule)
     				{
     					cplex.addEq(y[j][f][delta],0, "y "+j+f+delta+"=0");
     				}
		    	}
	    	}
    	}	    			
 	
    	 //loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=0; delta<DELTA; delta++)
		    	{
    				z[j][f][delta] = cplex.intVar(0, 1,"z "+j+f+delta);	
    				
    				//if the service is not requesting a VNf of the specified type, set the variable to 0
    				if (!this.service.requestsVNFOfType(this.tf[f])|| delta<this.startSchedule)
     				{
     					cplex.addEq(z[j][f][delta],0, "z "+j+f+delta+"=0");
     				}
		    	}
	    	}
    	}	   	    
 
    	 //loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		for (int k=0; k<ks; k++)
    		{
    			h[j][k] = cplex.intVar(0, 1,"h "+j+k);
    			//force h = 0 for middleboxes that does not exist for the service (n not in Ns). 
	    		//Since hns specifies that n and n+1 are on the same server, then h[n-1][s] should have a value but not h[n][s] since n+1 does not exist (Prevent object is unknown to IloCplex)
	    		if(j>=N-1)
				{
					cplex.addEq(h[j][k], 0);
				}
    		}
    		
    	} 	  
 
    	 //loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		//loop over servers
    		for (int k=0; k<ks; k++)
	    	{
    			q[j][k] = cplex.intVar(0, 1,"q "+j+k);
    			
	    	}
    	} 	 
 	    
    	//loop over timeslots
		for (int delta=0; delta<DELTA; delta++)
    	{
    		//loop over edges
    		for (int e=0; e<E; e++)
	    	{
    			theta[delta][e] = cplex.intVar(0, 1,"theta "+delta+e);
    			
    			if (delta<this.startSchedule)
 				{
    				cplex.addEq(theta[delta][e], 0);
 				}
	    	}
    	}
 	
    	//loop over timeslots
		for (int delta=0; delta<DELTA; delta++)
    	{
    		//loop over edges
    		for (int e=0; e<E; e++)
	    	{
    			thetaHat[delta][e] = cplex.intVar(0, 1,"thetaHat "+delta+e);
    			
    			if (delta<this.startSchedule)
 				{
    				cplex.addEq(thetaHat[delta][e], 0);
 				}
	    	}
    	} 	    
 	    
 	    //loop over servers i
 	    for(int i =0; i<ks; i++)
 	    {
 	    	//loop over servers j
 			for (int j=0; j<ks; j++)
 	    	{ 				
 	    		//loop over edges
 	    		for (int e=0; e<E; e++)
 		    	{
 	    			l[i][j][e] = cplex.intVar(0, 1,"l "+i+j+e);
 		    	} 		    	
 	    	}
 	    }
  
 		
 	    //loop over middleboxes
 	    for(int i =0; i<ks; i++)
 	    {
 	    	//loop over servers j
 			for (int j=0; j<ks; j++)
 	    	{ 			
				//loop over VNF time slots
				for (int delta=0; delta<DELTA; delta++)
		    	{
		    		//loop over  edges
		    		for (int e=0; e<E; e++)
			    	{
		    			g[i][j][delta][e] = cplex.intVar(0, 1,"g "+i+j+delta+e);
		    			
		    			if (delta<this.startSchedule)
		 				{
		    				cplex.addEq(g[i][j][delta][e], 0);
		 				}
		    			
			    	}
		    	}
	    	} 			
 	    }
 	   
    }
    
    /**
     * This function is used for testing purposes. It sets the variables to the values got from the ILP model 
     * @param ilp the ilp model
     * @throws UnknownObjectException
     * @throws IloException
     */
    public void setDecisionVariables (ILPModelModified ilp) throws UnknownObjectException, IloException
    {	
    	int count =0;//counter of the virtual links of the service
    	
    	//loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{
    				cplex.addEq(y[j][f][delta],ilp.cplex.getValue(ilp.y[this.service.getId()][j][f][delta]));
		    	}
	    	}
    	}	    			
 	
    	 //loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{
    				cplex.addEq(z[j][f][delta], ilp.cplex.getValue(ilp.z[this.service.getId()][j][f][delta]));	
		    	}
	    	}
    	}	   	    
 
    	 //loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		for (int k=0; k<ks; k++)
    		{
    			cplex.addEq(h[j][k],ilp.cplex.getValue(ilp.h[this.service.getId()][j][k]));
    			
    		}
    		
    	} 	  
 
    	 //loop over middleboxes
    	for (int j=0; j<N; j++)
    	{
    		//loop over servers
    		for (int k=0; k<ks; k++)
	    	{
    			cplex.addEq(q[j][k], ilp.cplex.getValue(ilp.q[this.service.getId()][j][k]) );
    			
	    	}
    	} 	 
 	    
    	//loop over timeslots
		for (int delta=this.startSchedule; delta<DELTA; delta++)
    	{
			//loop over edges of all services from the ilp
    		for (int e=0; e<ilp.E.length; e++)
	    	{
    			//if the edge is for the current service
    			if (ilp.E[e] == this.getService().getId())
    			{
    				cplex.addEq(theta[delta][count], ilp.cplex.getValue(ilp.theta[this.service.getId()][delta][e]));
    				count++;
    			}
	    	} 
    		
    		count =0;
    	
    	}
		
		count =0;
    	//loop over timeslots
		for (int delta=this.startSchedule; delta<DELTA; delta++)
    	{
			//loop over edges of all services from the ilp
    		for (int e=0; e<ilp.E.length; e++)
	    	{
    			//if the edge is for the current service
    			if (ilp.E[e] == this.getService().getId())
    			{
    				cplex.addEq(thetaHat[delta][count],ilp.cplex.getValue(ilp.thetaHat[this.service.getId()][delta][e]));
    				count++;
    			}
	    	} 
    		
    		count =0;
    	} 	    
 	   
		count =0;
 	    //loop over servers i
 	    for(int i =0; i<ks; i++)
 	    {
 	    	//loop over servers j
 			for (int j=0; j<ks; j++)
 	    	{ 		
 				//check only the case where the link exists (for which we have the variable l[i][j] in the ilp)
 				if (G[i][j]!=1 && G[j][i]!=1)
		    	{ 
 					continue;
		    	}
 	    		//loop over edges of all services from the ilp
 	    		for (int e=0; e<ilp.E.length; e++)
 		    	{
 	    			//if the edge is for the current service
 	    			if (ilp.E[e] == this.getService().getId())
 	    			{
 	    				cplex.addEq(l[i][j][count], ilp.cplex.getValue(ilp.l[i][j][e]));
 	    				count++;
 	    			}
 		    	}
 	    		
 	    		count =0;
 	    	}
 	    }
  
 		count =0;
 	    //loop over middleboxes
 	    for(int i =0; i<ks; i++)
 	    {
 	    	//loop over servers j
 			for (int j=0; j<ks; j++)
 	    	{ 			
 				//check only the case where the link exists (for which we have the variable g[i][j] in the ilp)
 				if (G[i][j]!=1 && G[j][i]!=1)
		    	{ 
 					continue;
		    	}
 				
				//loop over VNF time slots
				for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{
					//loop over edges of all services from the ilp
	 	    		for (int e=0; e<ilp.E.length; e++)
	 		    	{
	 	    			//if the edge is for the current service
	 	    			if (ilp.E[e] == this.getService().getId())
	 	    			{
	 	    				cplex.addEq(g[i][j][delta][count], ilp.cplex.getValue(ilp.g[i][j][this.service.getId()][delta][e]));
	 	    				count++;
	 	    			}
	 		    	}
	 	    		
	 	    		count =0;
					
		    	}
	    	} 			
 	    }
    	
    	
    }
    
    
    /**
     * This function will build the pricing model by building the constraints
     * @param ilp the ilp model to use its solution in the pricing. This will allow testing the pricing without an objective
     * 
     * @throws IloException 
     */
    public void buildPricingModel(ILPModelModified ilp) throws IloException
    {    	
    	this.initializeDecisionVariables();
    	
    	// use the ilp to set some decision variables and test the pricing without an objective
    	if(ilp!=null)
    	{
    		this.setDecisionVariables(ilp);
    	}
    	else //add the objective function only when not testing with ilp
    	{
	    	//setting up the objective function    	
	    	IloNumExpr reducedCost = cplex.numExpr();
	    	
	    	IloNumExpr VNFProcessingTerm = cplex.numExpr();
	    	IloNumExpr linkCapacityTerm = cplex.numExpr();
	
	    	int c =0; // this will be used as a counter on the number of links to make the link between (ij) in o[i][j][l][delta] and gamma[l][delta]
	    
			 //loop over VNF
			for (int f=0; f<F; f++)
	    	{
				//loop over time slots
				for (int delta=this.startSchedule; delta<this.DELTA; delta++)
		    	{
	    		
	    			VNFProcessingTerm = cplex.sum(VNFProcessingTerm, cplex.prod(r[f][delta], this.beta[f][delta]));
	
		    	}
	    	}
			
			//loop over time slots
			for (int delta=this.startSchedule; delta<this.DELTA; delta++)
	    	{
	    		//loop over servers
	   		    for(int i =0; i<ks; i++)
	   		    {
	   		    	 //loop over servers
	   		    	for (int j=0; j<ks; j++)
	   		    	{
	   		    		//make sure the physical link exists need to check G[i][j]==1  ||G[j][i] ==1 to have a correct constraint
	   		    		if (G[i][j]==1 )
	   		    		{
	   		    			linkCapacityTerm = cplex.sum(linkCapacityTerm, cplex.prod(o[i][j][delta],this.gamma[c][delta]));
	   		    	
	   		    			c++;
	   		    			
	   		    		}
	   		    	}
	   		    }
	   		    //reset c to zero to loop over the links in the network again
	   		    c=0;
				
	    	}
		
			reducedCost = cplex.sum(this.alpha, cplex.sum(  VNFProcessingTerm,cplex.sum( linkCapacityTerm,cplex.prod(v,-this.phi))));
			cplex.addMaximize(reducedCost);
    	}
    
    	
    	//setting the constraints
		/**
		 * C37 : ensures that a middlebox $n$ of service $s$ should be mapped to exactly one VNF instance $f$  
		 * sum over f sum over delta y[n][f][delta] = 1 for all n 
		 */
	
    	//loop over middleboxes of the service
    	for (int j=0; j<N; j++)
    	{
    		IloNumExpr middleboxMappedConstraint = cplex.numExpr();
    		
    		 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=this.startSchedule; delta<this.DELTA; delta++)
		    	{
    				middleboxMappedConstraint = cplex.sum(middleboxMappedConstraint, y[j][f][delta]);	
		    	}	    			
	    	}
    		
    		cplex.addEq(middleboxMappedConstraint,1, "Const2_n ["+j+"]");
    	}	    			
	    
        
		/**
		 * C38 :ensures that a middlebox $n$ of service $s$ should be mapped to a VNF instance $f$ of the same type 
		 * sum over f sum over delta y[n][f][delta]t[f] = m[n] for all n
		 */
	
	    	 //loop over middleboxes of the service
    		for (int j=0; j<N; j++)
	    	{
	    		IloNumExpr middleboxTypeConstraint = cplex.numExpr();
	    		
	    		 //loop over VNF
	    		for (int f=0; f<F; f++)
		    	{	    			
	    			 //loop over time slots
	    			for (int delta=this.startSchedule; delta<this.DELTA; delta++)
			    	{
	    				middleboxTypeConstraint = cplex.sum(middleboxTypeConstraint, cplex.prod(y[j][f][delta], tf[f]));	
			    	}	    			
		    	}
	    		
	    		cplex.addEq (middleboxTypeConstraint, this.mns[j], "Const3_s ["+j+"]");
	    	}	   			
	
	    	/**
			 * C39 :prevents the transmission of traffic of a service $s$ between two consecutive middleboxes $n$ and ($n+1$), if its processing on $n$ has not been completed
			 * theta [delta'][e] = 1-sum over f y[o(e)][f][delta]  for all e, for all delta, delta'; delta'<delta+p[o(e)]
			 */
	    	
		    	//loop over edges
	    		for (int e=0; e<E; e++)
		    	{	   
	    		
	    			//loop over timeslots
	    			for (int deltaPrime=this.startSchedule; deltaPrime<this.DELTA; deltaPrime++ )
	    	    	{
	    	    		//loop over timeslots
	    	    		for (int delta=this.startSchedule; delta<this.DELTA; delta++)
	    		    	{	    	    			
	    	    			//we only need to add constraint for delta< deltaPrime+p[oe(e)]
	    	    			if(deltaPrime>=(delta+ps[oe[e]]))
	    	    			{
	    	    				continue;
	    	    			}
	    	    			
	    	    			IloNumExpr transmissionStartConstraint = cplex.numExpr();
	    	    			
	    	    			 //loop over VNF
	    		    		for (int f=0; f<F; f++)
	    			    	{
	    		    			transmissionStartConstraint = cplex.sum(transmissionStartConstraint, y[oe[e]][f][delta]);
	    			    	}
	    		    		
	    		    		cplex.addLe( theta[deltaPrime][e],cplex.sum(1, cplex.prod(-1,transmissionStartConstraint)), "Const4_eDeltaprimeDelta ["+e+"]["+deltaPrime+"]["+delta+"]");
	    		    	}
	    	    	}
	    			
		    	}
		    	
    		 /**
    		 * C40 :ensures that traffic of a service $s$ can not be processed by a middlebox $(n+1)$ if it was not transmitted to it by its precedent middlebox $n$
    		 * sum over f y[d(e)][f][delta'] <= 1-theta[delta][e] for all e,  for all delta, delta'; delta'<delta+ws/bs
    		 */
	    
	    	//loop over edges
    		for (int e=0; e<E; e++)
	    	{    
    			    			
    			//loop over timeslots
    			for (int deltaPrime=this.startSchedule; deltaPrime<this.DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=this.startSchedule; delta<this.DELTA; delta++)
    		    	{
    	    			//we only need to add constraint for delta< deltaPrime+ws/bs
    	    			if(deltaPrime>=delta+Math.ceil((double)this.ws/(double)this.bs))
    	    			{
    	    				continue;
    	    			}
    	    			
    	    			IloNumExpr transmissionFinishedConstraint = cplex.numExpr();
    	    			
    	    			 //loop over VNF
    		    		for (int f=0; f<F; f++)
    			    	{
    		    			transmissionFinishedConstraint = cplex.sum(transmissionFinishedConstraint, y[de[e]][f][deltaPrime]);
    			    	}
    		    		
    		    		cplex.addLe(transmissionFinishedConstraint, cplex.sum(1, cplex.prod(-1,theta[delta][e])),"Const5_eDeltaprimeDelta ["+e+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}
    			
	    	}
    	    	
    	    /**
    		 * C41 :prevents a middlebox $(n+1)$ to start processing traffic of service $s$ before its precedent middlebox $n$ finishes its execution
    		 * sum over f y[n+1][f][delta'] <= 1-sum over f y[n][f][delta] for all n,  for all delta, delta'; delta'<delta+psn[n]
    		 */
		 	 //loop over middleboxes of the service N specifies how many middlebox service i has.
	    	for (int j=0; j<N-1; j++)
	    	{    	    		
	    		//loop over timeslots
    			for (int deltaPrime=this.startSchedule; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=this.startSchedule; delta<DELTA; delta++)
    		    	{
    	    			//we only need to add constraint for delta< deltaPrime+pns
    	    			if(deltaPrime>=delta+this.ps[j])
    	    			{
    	    				continue;
    	    			}
    	    			
    	    			IloNumExpr processStartOfnConstraint = cplex.numExpr();
    	    			IloNumExpr  processStartOfnPlusConstraint = cplex.numExpr();
    	    			 //loop over VNF
    		    		for (int f=0; f<F; f++)
    			    	{
    		    			processStartOfnPlusConstraint = cplex.sum (processStartOfnPlusConstraint, y[j+1][f][deltaPrime]);
    		    			processStartOfnConstraint = cplex.sum (processStartOfnConstraint, y[j][f][delta]);
    			    	}
    		    		
    		    		cplex.addLe (processStartOfnPlusConstraint, cplex.sum(1,cplex.prod(-1, processStartOfnConstraint)),"Const6_snDeltaprimeDelta ["+j+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}	   	    	    			
    	    }
	    		
		    
	  	  /**
	  	   * C42: make sure that z is set to 1 only during the processing period
	  	   * sum over delta  z[i][f][delta]= psn sum over delta y[i][f][delta] for all  f, n
	  	   */
	  	  	//loop over middleboxes of the service s
  	   		for (int i=0; i<N; i++)
  	    	{
  				//loop over VNF
  	 	   		for (int f=0; f<F; f++)
  	 		    {
  	 	   			IloNumExpr processSameVNFConstraint = cplex.numExpr();
  	 	   			IloNumExpr startedProcessingConstraint = cplex.numExpr();
  	 	   			//loop over timeslots
  	     	    	for (int delta=this.startSchedule; delta<DELTA; delta++)
  	     		    {
  	     	    		processSameVNFConstraint = cplex.sum (processSameVNFConstraint, z[i][f][delta]);
  	     	    		startedProcessingConstraint = cplex.sum (startedProcessingConstraint, y[i][f][delta]);
  	     		    }
  	     	    	
  	     	    	cplex.addEq(processSameVNFConstraint, cplex.prod(this.ps[i], startedProcessingConstraint), "Const7_nf["+i+"]["+f+"]");
  	 		    }
  	    	}
	  		 
  	      /**
  	       * C43: Specify that a service is processing (set z)
  	       * 
  	       * z[i][f][deltaPrime]>=y[i][f][delta] for all deltaPrime>= delta and deltaPrime<delta+psn for all n for all f
  	       */
  	   		//loop over middleboxes of the service s
  	    	for (int i=0; i<N; i++)
  	      	{
  	  			//loop over VNF
  	   	   		for (int f=0; f<F; f++)
  	   		    {
  	   	   			//loop over timeslots
  	       	    	for (int delta=this.startSchedule; delta<DELTA; delta++)
  	       		    {
  	       	    		//loop over timeslots
  	   	     			for (int deltaPrime=delta; deltaPrime<delta+this.ps[i]&&deltaPrime<DELTA; deltaPrime++ )
  	   	     	    	{
  	   	     				cplex.addGe(z[i][f][deltaPrime], y[i][f][delta], "Const8_NDeltaprimeDeltaF ["+i+"]["+deltaPrime+"]["+delta+"]["+f+"]"); 	     				
  	   	     	    	}
  	       		    }
  	   		    }
  	      	}
  	  	 
  		    /**
  	  		 * C44 : specifies the physical server $k$ on which a middlebox $n$ of service $s$ is mapped
  	  		 *  q[n][k] = sum over f sum over delta y[n][f][delta]x[f][k]
  	  		 */	     
  			
	    	 //loop over middleboxes
	    	for (int j=0; j<N; j++)
	    	{
	    		//loop over servers
	    		for (int k=0; k<ks; k++)
		    	{
	    			IloNumExpr mappingConstraint = cplex.numExpr();
	    			//loop over timeslots
					for (int delta=this.startSchedule; delta<DELTA; delta++)
			    	{
						//loop over timeslots
						for (int f=0; f<F; f++)
				    	{
							mappingConstraint = cplex.sum (mappingConstraint,cplex.prod(y[j][f][delta],xfk[f][k]));
				    	}
			    	}
	    			
					cplex.addEq(q[j][k], mappingConstraint,"Const9_nk ["+j+"]["+k+"]" );
		    	}
	    	}
  			 
		    /**
	  		 * C45 :specifies if two consecutive middleboxes $n$ and $(n+1)$ of the same service $s$ are mapped to VNFs $f$ and $f^\prime$ hosted on the same physical server
	  		 * h[n] = q[j][k]  q[j+1][k] for all n,  k->lineraize
	  		 * Const12_1: h[j][k] <= q[j][k] for all n,  k 
	  		 * Const12_2:h[j][k] <= q[j+1][k] for all n, , k 
	  		 * Const12_3: h[j][k] >=  q[j][k] + q[j+1][k] -1 for all n, , k 
	  		 * 
	  		 */
	  
			 //loop over middleboxes
	    	for (int j=0; j<N-1; j++)
	    	{
	    		for (int k=0; k<ks; k++)
		    	{
	    			cplex.addLe(h[j][k], q[j][k],"Const10_1_nk ["+j+"]["+k+"]" );
	    			cplex.addLe(h[j][k], q[j+1][k],"Const10_2_nk ["+j+"]["+k+"]" );
	    			cplex.addGe(h[j][k], cplex.sum (cplex.sum(q[j][k],q[j+1][k]), -1),"Const10_3_nk ["+j+"]["+k+"]" );
		    	
		    	}
	    	}
			 
		    /**
	  		 * C46 :prevents the start of the transmission of traffic of a service $s$ between two consecutive middleboxes $o(e)$ and $d(e)$ if they are mapped to VNFs hosted on the same physical server
	  		 * sum over delta theta[delta[e] = 1-sum over k h[o(e)][k] for all e
	  		 */		
	    	//loop over edges
    		for (int e=0; e<E; e++)
	    	{	    		
    			
    			IloNumExpr preventTransmissionConstraint = cplex.numExpr();
    			IloNumExpr physicalServerMappingConstraint = cplex.numExpr();
    			//loop over timeslots
				for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{		    		
					preventTransmissionConstraint = cplex.sum(preventTransmissionConstraint, theta[delta][e]);			    	
		    	}
				
				for (int k=0; k<ks; k++)
		    	{
					physicalServerMappingConstraint = cplex.sum(physicalServerMappingConstraint, h[oe[e]][k]);
		    	}
				cplex.addEq(preventTransmissionConstraint, cplex.sum (1, cplex.prod(-1, physicalServerMappingConstraint)),"Const11_e ["+e+"]" );					
	    	}
		   
    	    /**
      		 * C47:specifies that the virtual link $e$ is used to transmit traffic of service $s$ during all the required transmission time ($\frac{w_s}{b_s}$)
      		 *  between middleboxes o(e) and d(e) if they were mapped to VNFs hosted on different physical servers.
      		 *  It prevents the transmission in case both middleboxes $o(e)$ and $d(e)$ are mapped to VNFs hosted on the same physical server
      		 *  
      		 *  sum over delta thetaHat[delta][e] = ws/bs] sum over delta theta[delta][e]  for all e 
      		 */    	
	    	//loop over edges
    		for (int e=0; e<E; e++)
	    	{
    				
    			IloNumExpr transmissionTimeConstraint = cplex.numExpr();
    			IloNumExpr transmissionStartConstraint = cplex.numExpr();
    			//loop over timeslots
				for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{		    		
					transmissionTimeConstraint = cplex.sum(transmissionTimeConstraint, thetaHat[delta][e]);	
					transmissionStartConstraint = cplex.sum(transmissionStartConstraint, theta[delta][e]);
		    	}
				
				cplex.addEq(transmissionTimeConstraint, cplex.prod (Math.ceil((double)this.ws/(double)this.bs),transmissionStartConstraint),"Const12_e ["+e+"]" );
	    	}
    		
    	    /**
      		 * C48 : ensures the virtual link $e$ is used for transmission of traffic of service $s$ during all the transmission delay
      		 *  ($\frac{w_s}{b_s}$) starting at time slot $\delta$ when the traffic transfer begun.
      		 *  sum over deltaPrime from delta to delta+ws/bs-1 thetaHat[deltaPrime][e] >= ws/bs theta[delta][e]  for all e for all delta
      		 */
    	  
	    	//loop over edges
    		for (int e=0; e<E; e++)
	    	{        			
		    	//loop over timeslots
				for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{	
	    			IloNumExpr transmissionTimeConstraint = cplex.numExpr();
	    			
	    			//prevent a null pointer exception with thetaHat[i][deltaPrime][e]
	    			if (delta+Math.ceil((double)this.ws/(double)this.bs)-1 >=DELTA)
	    			{
	    				continue;
	    			}
	    			
					//loop over timeslots
					for (int deltaPrime=delta; deltaPrime<=delta+Math.ceil((double)this.ws/(double)this.bs)-1; deltaPrime++)
			    	{
						transmissionTimeConstraint = cplex.sum(transmissionTimeConstraint, thetaHat[deltaPrime][e]);	
			    	}
					
					cplex.addGe(transmissionTimeConstraint, cplex.prod (Math.ceil((double)this.ws/(double)this.bs),theta[delta][e]),"Const13_eDelta ["+e+"]["+delta+"]" );
		    	}				
	    	} 
    		
    		/**
      		 * C49 : ensures that the physical links capacity constraint is not violated
      		 *  o[i][j][delta]  <= cij[i][j] for all delta , for all (ij) in L
      		 * 
      		 */  
    	  	   			
   			//loop over timeslots
			for (int delta=this.startSchedule; delta<DELTA; delta++)
	    	{
				//loop over servers
	   		    for(int i =0; i<ks; i++)
	   		    {
	   		    	 //loop over servers
	   		    	for (int j=0; j<ks; j++)
	   		    	{
	   		    		//make sure the physical link exists need to check G[i][j]==1  ||G[j][i] ==1 to have a correct constraint
	   		    		if (G[i][j]==1  )
	   		    		{    		   		    		
	   		    			cplex.addLe(o[i][j][delta], cij[i][j], "Const14_ijDelta ["+i+"]["+j+"]["+delta+"]");
	   		    			
	   		    		}
	   		    	}
	   		    }
	    	}
    	       	
   	       
		    /**
	  		 * C50 : decides on the routing
	  		 * IMPORTANT: This constraint may select some links and create subtours that may not be included in the path from source to destination which also may encouter reserving bw dor them
	  		 * even though that can not be actually used. This can be eliminated by adding subtour elemination constraint. However this is not added here because:
	  		 * 1- Those links wont be selected if they are against the objective function
	  		 * 2- adding subtour elimination constraint is costly
	  		 *  sum j:(i,j) in L l[i][j][e] - sum j:(j,i) in L   l[j][i][e] = q[oe[e]][i] - q[de[e]][i] for all i in ks for all e 
	  		 *  
	  		 */	     
	
	    	//loop over edges
	   		for (int e=0; e<E; e++)
		    {		   			
				//loop over servers
	   		    for(int i =0; i<ks; i++)
	   		    {	   				
					IloNumExpr physicalLinkConstraint1 = cplex.numExpr();
					IloNumExpr physicalLinkConstraint2 = cplex.numExpr();
				
					IloNumExpr constraint17_1 = cplex.numExpr();
					IloNumExpr constraint17_2 = cplex.numExpr();
	   		    	 //loop over servers
	   		    	for (int j=0; j<ks; j++)
	   		    	{		   		    		
	   		    		//make sure the physical link exists -- NEED to check ON G[i][j] and  G[j][i]==1 because we are using l[i][j][e] and l[j][i][e]
	   		    		if (G[i][j]==1 || G[j][i]==1)
	   		    		{ 
	   		    			physicalLinkConstraint1 = cplex.sum(physicalLinkConstraint1, l[i][j][e]);
	   		    			physicalLinkConstraint2 = cplex.sum(physicalLinkConstraint2, l[j][i][e]);
	   		    			  			
	   		    		}
	   		    		
	   		    	}	   		    	
	   		    	
	   		    	//sum j:(i,j) in L l[i][j][e][delta] - sum j:(j,i) in L
	   		    	constraint17_1 = cplex.sum(physicalLinkConstraint1, cplex.prod(-1, physicalLinkConstraint2));
	   		    	
	   		    	//q[oe[e]][i] - q[de[e]][i]
		   		    constraint17_2 = cplex.sum( q[oe[e]][i], cplex.prod(-1, q[de[e]][i])); 
		   		    
		   		    cplex.addEq(constraint17_1,constraint17_2, "Const15_ei["+e+"]["+i+"]");
	   		    }		    	
	    	}
		 

		     
		    /**
		     * C51: Make sure that virtual link is not mapped if its source and destination are on the same server
		     * l[i][j][e]<= 1- sum over k h[oe][k] for all ij in L, for all e in Es 
		     */
	    	//loop over edges
	   		for (int e=0; e<E; e++)
		    {	   	
	   			//loop over servers
	   		    for(int i =0; i<ks; i++)
	   		    {	  
	   		    	 //loop over servers
	   		    	for (int j=0; j<ks; j++)
	   		    	{		    		
	   		    		
	   		    		//make sure the physical link exists -- NEED to check ON G[i][j] and  G[j][i]==1 because we are using l[i][j][e] and l[j][i][e]
	   		    		if (G[i][j]==1 || G[j][i]==1 )
	   		    		{ 
	   		    			IloNumExpr physicalServerMappingConstraint = cplex.numExpr();
	   		    			for (int k=0; k<ks; k++)
	   				    	{
	   							physicalServerMappingConstraint = cplex.sum(physicalServerMappingConstraint, h[oe[e]][k]);
	   				    	}
	   		    			cplex.addLe(l[i][j][e], cplex.diff(1,  physicalServerMappingConstraint), "Const16_ije["+i+"]["+j+"]["+e+"]");	   	   		    		
	   		    		}
	   		    	}	   		    	
	   		    	
	   		    }		    	
	    	}
		    
	  	    /**
	  		 * C52 :sets the completion time of all services to be = to be greater or equal to the completion time of each service
	  		 * v=(sum over f sum over delta y[|Ns|][f][delta](delta+psn[|Ns|]) 
	  		 */
		
	    	IloNumExpr processedBeforeDeadlineConstraint = cplex.numExpr();
	    	
	    	//loop over VNF
	    	for (int f=0; f<F; f++)
		    {
				 //loop over time slots
				for (int delta=this.startSchedule; delta<DELTA; delta++)
		    	{
					processedBeforeDeadlineConstraint = cplex.sum(processedBeforeDeadlineConstraint, cplex.prod(y[N-1][f][delta], delta+this.ps[this.N-1])) ;	
		    	}
	    	}
	    	
	    	cplex.addEq(v,processedBeforeDeadlineConstraint, "Const17");
	 
		    
		    
		     /**
	  		 * C53 : sets o the service bandwidth consumption on he links
	  		 * o[i][j][delta] = sum over s sum over e l[i][j][e]thetaHat[s][delta][e] b[s]  for all delat , for all (ij) in L->linearize
	  		 * Const16_1: g[i][j][delta][e] <= l[i][j][e] for all delta, (ij), e, s ->C24
	  		 * Const16_2: g[i][j][delta][e] <= thetaHat[delta][e] for all delta, (ij), e, s ->C25
	  		 * Const16_3: g[i][j][delta][e] >= l[i][j][e]+thetaHat[delta][e] -1 for all delta, (ij), e, s ->C26
	  		 * Const16_4: sum over s sum over e g[i][j][delta][e]bs[s]+ sum over s sum over e g[j][i][delta][e]bs<= c[i][j] for all delta, (ij) -> c27
	  		 */
		     
		    	//loop over edges
		  		for (int e=0; e<E; e++)
			    {
		   			//loop over timeslots
					for (int delta=this.startSchedule; delta<DELTA; delta++)
			    	{
						//loop over servers
			   		    for(int i =0; i<ks; i++)
			   		    {
			   		    	 //loop over servers
			   		    	for (int j=0; j<ks; j++)
			   		    	{
			   		    		//make sure the physical link exists need to check G[i][j]==1  ||G[j][i] ==1 to have a correct constraint
			   		    		if (G[i][j]==1 ||G[j][i] ==1 )
			   		    		{
			   		    			cplex.addLe(g[i][j][delta][e], l[i][j][e], "Const18_1_ijDeltaE ["+i+"]["+j+"]"+delta+"]["+e+"]");
			   		    			cplex.addLe(g[i][j][delta][e], thetaHat[delta][e], "Const18_2_ijDeltaE ["+i+"]["+j+"]["+delta+"]["+e+"]");
			   		    			cplex.addGe(g[i][j][delta][e], cplex.sum(cplex.sum(l[i][j][e],thetaHat[delta][e]),-1), "Const18_3_ijDeltaE ["+i+"]["+j+"["+delta+"]["+e+"]");
			   		    		}
			   		    	}
			   		    }
			    	}
			    }
		    
		   //loop over timeslots
			for (int delta=this.startSchedule; delta<DELTA; delta++)
	    	{
				//loop over servers
	   		    for(int i =0; i<ks; i++)
	   		    {
	   		    	 //loop over servers
	   		    	for (int j=0; j<ks; j++)
	   		    	{
	   		    		//make sure the physical link exists -- check only on G[i][j] or G[j][i] because we only have one link ij. 
	   		    		if (G[i][j]==1  ) 
	   		    		{
		   		    		IloNumExpr bandwidthConstraint = cplex.numExpr();
		   		    		IloNumExpr bandwidthConstraint2 = cplex.numExpr();
			   		    	
			   			    	//loop over edges
			   			   		for (int e=0; e<E; e++)
			   				    {
			   			   			/**
			   			   			 * Make sure to add capacity constraint for link ij and ji 
			   			   			 */
			   			   			bandwidthConstraint = cplex.sum(bandwidthConstraint, cplex.prod(g[i][j][delta][e], this.bs));
			   			   			bandwidthConstraint2 = cplex.sum(bandwidthConstraint2, cplex.prod(g[j][i][delta][e],  this.bs));
			   			   			
			   			   			//g[i][j][delta][e]+g[j][i][delta][e]<=1 a flow of a service can only use the link in one direction at a time slot
			   			   			cplex.addLe(cplex.sum(g[i][j][delta][e],g[j][i][delta][e]),1,"Const18_4_IJDelta ["+i+"]["+j+"]["+delta+"]");
			   				    }	  
			   			     	/**
		   			   			 * Make sure to add capacity constraint for link ij and ji
		   			   			 * keep using cij and no cji because only cij are populated and ij is the same as ji (check graph)
		   			   			 * sum over s sum over e g[i][j][delta][e]bs+ sum over s sum over e g[j][i][s][delta][e]bs<= c[i][j]
		   			   			 * this is to consider that ij and ji are one link so if they are assigned at the same time slot make sure the capacity is respected
		   			   			 */
			   			   		cplex.addEq(o[i][j][delta],cplex.sum(bandwidthConstraint,bandwidthConstraint2), "Const18_5_IJDelta ["+i+"]["+j+"]["+delta+"]" );
			   	   			   
	   		    		}		   		    	
	   		    	}
	   		    }
	    	}
	    
			 /**
			  * C54: Ensure that a VNf can process the traffic of one service at a time
			  * r[f][delta] = sum over  over all n of the same type of f  z[n][f][delta] for all delta ,  all f,
			  */
			//loop over timeslots
			for (int delta=this.startSchedule; delta<DELTA; delta++)
		    {
				//loop over VNF
				for (int f=0; f<F; f++)
			    {
				
		    		IloNumExpr processSameVNFConstraint = cplex.numExpr();
		    	
		    			//loop over middleboxes of the service s
		 	 	   		for (int i=0; i<N; i++)
		 	 	    	{
		 	 	   			if (this.mns[i] !=tf[f] )
			 	    		{ 
		 	 	   				continue;
			 	    		}
		 	 	   			
		 	 	   			processSameVNFConstraint = cplex.sum(processSameVNFConstraint, z[i][f][delta]); 	 	   			
		 	 	    	}
		      	    
		    		cplex.addEq(r[f][delta],processSameVNFConstraint, "Const19_DeltaF ["+delta+"]["+f+"]" );
			    }
		    }  		 
	   
    }
    
    
    /**
     * This method will run the pricing model 
     * Export the model to a file called pricing/Pricing_serviceId
     * Report the results (values of the variables) to a file called pricing/PricingResults_serviceId
     * Return the column to pass to the master
     * 
     * @param filePath path to write the results
     * @param iteration iteration id for the pricing of this service
     * @throws IloException 
     */
    public Configuration runPricingModel(String filePath, int iteration) throws IloException{
    	
    	Configuration c = null;
    	try {
    		
			//this.cplex.exportModel("pricing_"+this.service.getId()+"_"+iteration+".lp");//"pricing/Pricing_"+this.service.getId()+"_"+iteration+".lp"
    		
			if (cplex.solve()) {
				//System.out.println(this.cplex.getObjValue());
				//System.out.println(this.cplex.getStatus());
				
				//build the configuration to pass to the master by creating it and incrementing the configurationId
				c = this.buildConfiguration();
								
				//print results (values of the decision variables)
				this.reportResults(filePath, c);
				
				
				//do not end pricing here because we need to get the reduced cost when running column generation 
				//this.cplex.end();	
			}
			else
			{
				System.out.println(this.cplex.getStatus());
			
				System.out.println(" NOT solved ");	
			}
		} catch (IloException e) {
			System.out.println ("ERROR RUNNING PRICING FOR SERVICE :" +this.service.getId());		
			e.printStackTrace();
		}    	
    	return c;
    	
    }
    
    
  
    /**
     * This method will build and return the configuartion generated by the pricing
     * It will handle some rounding issues in the pricing where integer are set to doubles
     * 
     * @return configuration to pass to the master for the specified service mentined in this pricing object
     */
    public Configuration buildConfiguration ()
    {
    	int F = this.physicalNetwork.getVNFNb();
    	int L = this.physicalNetwork.getLinksNb();
        int sourceId =0;
        int destinationId =0;
        
        //inputs for the configuration
    	int configV =0;
    	int [][] configR = new int [F][this.DELTA];
     	int [][] configO = new int [L][this.DELTA];

     	Configuration c = null ;
     
	
     	try {
     		
     		configV = (int)this.cplex.getValue (this.v);// cast to int will handle the rounding 
     		
			for (int i=0; i<this.r.length; i++){
				for (int j=0; j<this.r[i].length; j++){			
					//r should be 0 or 1. If it was 0.999999 we set it 1 else we set it to 0 (doing some rounding
					configR[i][j] = this.cplex.getValue (this.r[i][j])>0.9? 1:0;
				}
			}			
			
			//loop over links in the network and not 2 loops over servers because we are converting from o_ij^delta to o_l^delta for the master
			for (int i=0; i<L; i++){
				sourceId = this.physicalNetwork.getLinkList()[i].getSource();
				destinationId = this.physicalNetwork.getLinkList()[i].getDestination();
				
				for (int delta=0; delta<this.DELTA; delta++){	
					//set o_l^delta to o_ij^delta where i is the source of l and j is its destination
					//the below code will allo to handle the case where o should be = bs = 379 and it is = 378.9998 or 379.0000001 or o = -9.112710586123285E-13 instead of 0
					configO[i][delta] = this.cplex.getValue (this.o[sourceId][destinationId][delta])>(this.bs-1)?this.bs:0;									
				}
			}    	
			
			//set the configPerServiceId to be able to track which columns were selected
			c = new Configuration(this.service, configV, configR, configO);
			
		} catch (UnknownObjectException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return c;
    }
    
   /**
     * This method will report the pricing inputs and results to a file called PricingResults_serviceId
     * It will also print the results to the console
     * 
     * @param path to dump the results "pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt"
     * @param c, the configuration to print (should not call the build configuration here to print it because it will create a copy and change the configuration id)
     */
    public void reportResults(String filePath, Configuration c ){
    	String st ="";
    	
    	//print parameters only when no manual input
    	if (this.service!=null)
    		st+=this.toString();
    	
    	//print results
    	st+="\t RESULTS\n";
		try {
			st+=String.format("\t\t RC = %f\n",this.cplex.getObjValue());
		
			st+="\t\t"+Output.printTripleArray(y,"y[n][F][DELTA]", "\t\ty",this.cplex);
			st+="\t\t"+Output.printTripleArray(z,"z[n][F][DELTA]", "\t\tz",this.cplex);
			st+="\t\t"+Output.printDoubleArray(q,"q[n][ks]","\t\tq", this.cplex); 
			st+="\t\t"+Output.printDoubleArray(h,"h[n][ks]","\t\th", this.cplex);
			st+="\t\t"+Output.printDoubleArray(theta,"theta[DELTA][E]","\t\ttheta", this.cplex);
			st+="\t\t"+Output.printDoubleArray(thetaHat,"thetaHat[DELTA][E]","\t\tthetaHat", this.cplex);
			st+="\t\t"+Output.printTripleArray(l,"l[ks][ks][E]","\t\tl", this.cplex);
			st+="\t\t"+Output.printQuadrupleArray(g,"g[ks][ks][DELTA][E]","\t\tg", this.cplex);
			st+="\t\t --v---\n \t\t v ="+this.cplex.getValue(v)+"\n\n";
			st+="\t\t"+Output.printDoubleArray(r,"r[F][DELTA]","\t\tr", this.cplex);
			st+="\t\t"+Output.printTripleArray(o,"o[ks][ks][DELTA]","\t\to", this.cplex);
	
			st+=c.toString();		
			//write everything in a file
			FileManipulation outputFile =  new FileManipulation(filePath);
			outputFile.writeInFile(st);
						
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    }
    
    
    /**
     * This is a helper method that allows to get the dual values of a 2 dimensiosn range
     * 
     * @param range IloRange to get the dual values for
     * @param master problem
     * 
     * @return 2-D array with the dual values
     * @throws IloException 
     * @throws UnknownObjectException 
     */
    public double[][] getDuals( IloRange [][] range, CGMasterModel master) throws UnknownObjectException, IloException
    {
    	double [][] dualValues = new double [range.length][range[0].length];
    			
		for (int i =0; i<dualValues.length; i++)
    	{
			dualValues[i] = master.cplex.getDuals(range[i]);
    	}
			    	
    	return dualValues;    	
    }
    
    
    public IloIntVar getV()
    {
    	return this.v;
    }
    
    public IloIntVar [][][] getO()
    {
    	return this.o;
    }
    
    public IloIntVar [][] getR()
    {
    	return this.r;
    }
    
    public int getDELTA()
    {
    	return this.DELTA;
    }
    
    public int getStartSchedule()
    {
    	return this.startSchedule;
    }
    public Service getService()
    {
    	return this.service;
    }
    
    public Network getPhysicalNetwork()
    {
    	return this.physicalNetwork;
    }
    
    /**
     * This method will return a string of all the inputs of the pricing
     */
    public String toString(){
    	String st ="";
    	st+=String.format("Pricing for Service: %4d \n", this.service.getId());
    	st+= "\t Parameters:\n";
    	st+= "\t\t Service Info BASED ON MASTER HEURISTIC INPUT:\n \t"+this.service.toString();
		st+= "\t\t"+Output.printDoubleMatrice(G,"GRAPH");
		st+= "\t\t"+Output.printDoubleMatrice(cij,"PHYSICAL LINK CAPACITY");
		st+= "\t\t"+Output.printTable(tf, "VNF TYPES");
		st+= "\t\t"+Output.printDoubleMatrice(xfk,"VNF MAPPING TO PHYSICAL SERVERS");
		st+= String.format("\t alpha = "+this.alpha+"\n");
		st+= String.format("\t phi = "+this.phi+"\n");		
		st+= "\t\t"+Output.printDoubleArray(this.beta,"BETA [f][delta]", "\t\tbeta");
		st+= "\t\t"+Output.printDoubleArray(this.gamma,"GAMMA [l][delta]","\t\tgamma");
	
	
    	return st;
    }
    
    
    public static ArrayList<int[][]> manualInput()
    {
    	ArrayList<int[][]> parameters = new ArrayList<int[][]>();
    	 /**
		  * Example 1 : 3 services:
		  * S0: has VNF 1 and 2 mapped on the same server 2 -> admitted
		  */
		//physical network
		int [][] G = {
				 {0, 0, 1, 0, },
				 {1, 0, 0, 0, },
				 {0, 1, 0, 1, },
				 {0, 1, 0, 0, },
		/* {0, 1, 1, 0},
		 {0, 0, 1, 0 },
		 {0, 0, 0, 0},
		 {0, 1, 1, 0}, */
		};

		
		//links capacity
		int [][] cij = {
				{0, 0, 500, 0, },
				{500, 0, 0, 0, },
				{0, 500, 0, 500, },
				{0, 500, 0, 0, },
		/* { 0, 1000, 1000, 0 }, 
		 {0, 0, 1000, 0}, 
		 {0, 0, 0, 0}, 
		 {0, 1000, 1000, 0 }*/	
		};
		

		//VNFs types

		int [][] tf = {{0, 1, 5, 5, 3, 3, 4, }};//{2, 3, 4, 6, 8, 3, 1, 5  }};//manual
	
		//VNF MAPPING TO PHYSICAL SERVERS
		int [][] xfk = {
				{0, 0, 1, 0, },
				{0, 0, 0, 1, },
				{0, 1, 0, 0, },
				{0, 0, 1, 0, },
				{0, 0, 1, 0, },
				{0, 0, 0, 1, },
				{1, 0, 0, 0, },
		/*	{0, 0, 0, 1}, 
			{0, 0, 1, 0}, 
			{1, 0, 0, 0},
			{1, 0, 0, 0}, 
			{0, 1, 0, 0}, 
			{0, 1, 0, 0}, 
			{0, 1, 0, 0}, 
			{0, 0, 1, 0}, */
		};
	
		//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
		int[][] msn ={{0,3,1}};//{4, 5, 3,  2 }};

		//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
		int [][] psn = {{0,2,0}};//{0, 3, 1, 0}};
	
		//BANDWIDTH REQUIRED BY EACH SERVICE--
		int [][]bs = {{453}};//{100 }};
		
		//TRAFFIC SIZE OF EACH SERVICE
		int [][]ws =  {{1246}};//{100 }};
	
		//ORIGIN OF EACH VIRTUAL LINK--
		int [][] oe = {{0, 1}};//{0, 1, 2}};
		
		//DESTINATION OF EACH VIRTUAL LINK--
		int [][] de = {{1, 2}};//{1, 2, 3}}; 
		
		/*double alpha = 1;
		double phi = 0.5;
		double [][] beta = {
				{1,0,1,0,},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
		};
		double [][] gamma = {
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
				{1,0,1,0},
		};*/
		parameters.add(G);
		parameters.add(xfk);
		parameters.add(cij);
		parameters.add(tf);
		parameters.add(oe);
		parameters.add(de);
		parameters.add(bs);
		parameters.add(ws);
		parameters.add(psn);
		parameters.add(msn);
		
	
		return parameters;
	
    }
    public static void main (String[]args) throws IloException
    {
    	int delta = 35;
    	double alpha =0.0;// 50;
		double phi = -0.0;//10.5;
		double [][] beta = new double [7][delta];
		double [][] gamma = new double [5][delta];
		/*double [][] beta = {
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
		};

		double [][] gamma = {
				{1,0,1,0,1,0,1,0,1,0,1,0,1,7,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,7,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,7,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,7,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,7,1,0,1,0,1,0,},
				{1,8,1,0,1,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,8,1,0,1,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,8,1,0,1,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,8,1,0,1,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,8,1,0,1,1,1,0,1,0,1,0,1,0,1,0,1,0,1,0,},
				{4,0,1,0,1,0,1,0,1,6,1,0,1,0,1,0,1,0,1,0,4,0,1,0,1,0,1,0,1,6,1,0,1,0,1,0,1,0,1,0,4,0,1,0,1,0,1,0,1,6,1,0,1,0,1,0,1,0,1,0,4,0,1,0,1,0,1,0,1,6,1,0,1,0,1,0,1,0,1,0,4,0,1,0,1,0,1,0,1,6,1,0,1,0,1,0,1,0,1,0,},
				{6,0,1,0,1,0,1,4,1,0,1,0,1,0,1,0,1,0,1,0,6,0,1,0,1,0,1,4,1,0,1,0,1,0,1,0,1,0,1,0,6,0,1,0,1,0,1,4,1,0,1,0,1,0,1,0,1,0,1,0,6,0,1,0,1,0,1,4,1,0,1,0,1,0,1,0,1,0,1,0,6,0,1,0,1,0,1,4,1,0,1,0,1,0,1,0,1,0,1,0,},
				{1,0,1,0,1,0,1,0,1,0,1,2,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,2,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,2,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,2,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1,2,1,0,1,0,1,0,1,0,},
		};*/
    	ArrayList<int[][]> parameters = CGPricingModel.manualInput();
    	//int[][]G, int [][]xfk, int[][]cij, int[]tf,  int []oe, int []de, int bs, int ws, int[]ps,int[]mns, double alpha, double phi, double [][]gamma, double[][]beta
    
    //	CGPricingModel pricing =  new CGPricingModel(parameters.get(0), parameters.get(1),parameters.get(2), parameters.get(3)[0], parameters.get(4)[0], parameters.get(5)[0], parameters.get(6)[0][0], parameters.get(7)[0][0], parameters.get(8)[0], parameters.get(9)[0], alpha, phi, gamma,beta, delta);
		Network  graph = new Network(7,7,4,5,500,500,1,0);//the problem
		ArrayList<int[][]> physicalNetwork = graph.buildPhysicalNetwork();			 
		
		 //get the types of VNFs in the network
		int[] tf = physicalNetwork.get(2)[0];	
    	ServicesDriver driver = new ServicesDriver (5,  delta, 3, 5, 200, 500, 5 , 1, 500, 1500,tf);
		ArrayList<int[][]>  services = driver.generateServicesForModel();
		ArrayList<Service> servicesObjects = driver.convertGeneratedServices(services);
	
		CGPricingModel pricing = new CGPricingModel(servicesObjects.get(2), graph, null, 200,100);
    	pricing.buildPricingModel(null);
    	
    	double startlp =System.currentTimeMillis();
    	pricing.runPricingModel("PricingResults_.txt", 1);
    	double endlp = System.currentTimeMillis();
    	
    	System.out.println("Execution Time:"+(endlp-startlp) );
	/*	ILPModelModified ilp =  new ILPModelModified(graph, servicesObjects, delta);
    	ilp.buildILPModel();
    	ilp.runILPModel("testResults/ILP/ILPResults_1.txt", 0);
		
    	for (int i=0;i<servicesObjects.size(); i++)
    	{
    	
	    	CGPricingModel pricing = new CGPricingModel(servicesObjects.get(2), graph, null, delta);
	    	pricing.buildPricingModel(ilp);
	    	pricing.runPricingModel("PricingResults_"+2+".txt", 1);
    	}*/
    	
    }
}
