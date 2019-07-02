/**
 * This ILP is used to compare against the game theory method 
 * We consider mapping and scheduling VNF while accounting for transmission delays
 * We overlook transmission scheduling
 * The model does not consider deadlines in its constraint to make sure it is not pushing the completion time to the deadline in contrast to the game
 * which minimizes the completion time of the services
 * The objective the model is to minimize the maximum completion time of all services which pushes the completion time of each
 */
package Model;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

import java.io.IOException;
import java.util.ArrayList;

import HelperClasses.FileManipulation;
import HelperClasses.Output;
import HelperClasses.ServicesDriver;
import NFV.Service;
import Network.Network;
import Network.PhysicalMachine;

public class ILPMinMaxCT {
	public IloCplex cplex;
	public double objectiveValue;
	int F;
	int ks;
	int S;
	int maxMiddlebox;
	int DELTA;
	
/***************************************************************************************************************************
 * ****************************************PARAMETERS***********************************************************************
 * *************************************************************************************************************************/

	//the random connected network. This array will specify if a link exists between 2 nodes
	int[][] G;		
	
	//set of types on VNFs. This is formed as double array to be able to return it 
	int [] tf ;						
	
	//specifies which VNF is hosted on which server
	int [][] xfk;		
	
	//this contains the number of middleboxes for each service
	int []N;
	/**
	 * type of middlebox n of service s. if set to -1, then this middlebox is not for the service.
	 * maxMiddlebox is the maximum number of middleboxes a service can have
	 */
	int[][] msn;
	
	/**
	 * processing time (nb of timeslots) of middlebox n of service s. if set to -1, then this middlebox is not for the service.
	 * maxMiddlebox is the maximum number of middleboxes a service can have
	 */
	int [][] psn;
		
	//bandwidth required by service s.This is formed as double array to be able to return it 
	int [] bs ;
	
	// size of traffic of service s. This is formed as double array to be able to return it 
	int [] ws ;
				
	// deadline time of service s (in time slots).This is formed as double array to be able to return it 
	int [] us; // not used in model needed to determine admission				
	

	/************************************************/
	
	ArrayList<Service> services;
	Network physicalNetwork;

	
	/***************************************************************************************************************************
	 * ****************************************INITIALZE DECISION VRIABLES******************************************************
	 * *************************************************************************************************************************/
	//holds the maximum completion time of all services
	public IloIntVar rho;

	
	//specifies that the traffic of service s started processing at time slot delta  on middlebox n scheduled/mapped to VNF f
	public IloIntVar[][][][] y;
	
	
	//indicates that middleboxes n, (n+1)  of service s are hosted on the same node k(1) (or not, 0).\\
	public IloIntVar[][][] h;
	
	//specifies that the middlebox n of service s is mapped to a VNF instance hosted on physical server k (1) (or not, 0).
	public IloIntVar[][][]q;
	public IloIntVar[][][]z;
	
	/**
	 * This constructor is used when we have a manual input
	 * @param G
	 * @param xfk
	 * @param tf
	 * @param N
	 * @param bs
	 * @param ws
	 * @param us
	 * @param psn
	 * @param msn
	 * @param delta
	 * @param maxMiddlebox
	 * @throws IloException
	 */
    public ILPMinMaxCT(int[][]G, int [][]xfk,  int[]tf, int[]N, int []bs, int []ws, int [] us, int[][] psn,int[][]msn, int delta, int maxMiddlebox) throws IloException
    {
    	this.objectiveValue = -1; //set to -1 by default
    	this.cplex = new IloCplex();
    	services = new ArrayList<Service>();
    	this.physicalNetwork = new Network();
    	
    	this.G = G;
    	this.xfk=xfk;    
    	this.tf = tf;
        	
    	this.bs = bs;
    	this.ws = ws;
    	this.us =us;
    	this.psn = psn;
    	this.msn = msn;
    	this.N = N;
    	this.F = this.xfk.length;
    	this.ks = this.G.length;   	
    	this.DELTA = delta;
    	this.S = N.length;
    	this.maxMiddlebox = maxMiddlebox;
 	  	//setting the decision variables			
  			
		y = new IloIntVar[S][maxMiddlebox][F][DELTA];	
		h = new IloIntVar[S][maxMiddlebox][ks];
		q = new IloIntVar[S][maxMiddlebox][ks];
		z = new IloIntVar[S][maxMiddlebox][DELTA];
		
    }
	
    
    public ILPMinMaxCT(Network network, ArrayList<Service> services, int delta) throws IloException
	{ 	
	
		PhysicalMachine pm;
		this.objectiveValue = -1; //set to -1 by default
		this.maxMiddlebox =0;
	   	this.cplex = new IloCplex();
		this.services = services;
		
		this.S = this.services.size();			
		this.bs = new int[S];
		this.ws = new int[S];
		this.us = new int[S];
		this. N = new int[S];
		
		Service s;
		for (int i=0; i<this.services.size(); i++)
		{
			s= this.services.get(i);
			this.bs[i] = s.getBandwdith();
	    	this.ws[i] = s.getTrafficSize();
	    	this.N[i] = s.getN();
	    	this.us[i] = s.getDeadlineTime();
	 
	    	
	    	if (s.getN()>this.maxMiddlebox)
	    	{
	    		this.maxMiddlebox = s.getN();
	    	}
		}
	
    	this.psn = new int [S][this.maxMiddlebox];
    	this.msn= new int [S][this.maxMiddlebox];
    
    
    	for (int i=0; i<this.services.size(); i++)
		{
    		s= this.services.get(i);
	    	for (int j=0; j<this.maxMiddlebox; j++)
	    	{
	    		this.psn [i][j] = (j<s.getN())? s.getMiddleboxes().get(j).getProcessingTime(): -1;
	    		this.msn[i][j] = (j<s.getN())?s.getMiddleboxes().get(j).getType():-1;
	    	}
	    	
	    
		}
    	
    	
    	
		this.physicalNetwork = network;
	   	ArrayList<int[][]>networkArray = this.physicalNetwork.getPhysicalNetworkArray();
    	
    	//setting the parameters
    	this.G = networkArray.get(0);   	
    	this.tf = new int[network.getVNFNb()];
    	for(int i=0; i<network.getNodeList().length;i++)
    	{
    		pm = (PhysicalMachine)network.getNodeList()[i];
    		for(int j=0;j<pm.getHostedVNFs().size(); j++)
    		{
    			tf[pm.getHostedVNFs().get(j).getId()] = pm.getHostedVNFs().get(j).getType();
    		}
    	}
    	this.xfk = networkArray.get(3);
    	 
		this.DELTA = delta;    	
     	this.F = network.getVNFNb();
    	this.ks = this.G.length;
   	    	this.toString();
    	//setting the decision variables			
   	   
		y = new IloIntVar[S][this.maxMiddlebox][F][DELTA];
		
		h = new IloIntVar[S][this.maxMiddlebox][ks];
		q = new IloIntVar[S][this.maxMiddlebox][ks];
		z = new IloIntVar[S][maxMiddlebox][DELTA];
		
	}

    /**
     * This function initialize the decision variables
     * 
     * @throws IloException
     */
    public void initializeDecisionVariables() throws IloException
    {
    	rho =cplex.intVar(0, Integer.MAX_VALUE,"rho");
    	
    
 	    //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	 //loop over middleboxes
 	    	for (int j=0; j<maxMiddlebox; j++)
 	    	{
 	    		 //loop over VNF
 	    		for (int f=0; f<F; f++)
 		    	{
 	    			 //loop over time slots
 	    			for (int delta=0; delta<DELTA; delta++)
 			    	{
 	    				y[i][j][f][delta] = cplex.intVar(0, 1,"y "+i+j+f+delta);	
 	    				
 	    				//force y = 0 for middleboxes that does not exist for the service (n not in Ns) (Prevent bject is unknown to IloCplex)
 	    				if(j>=N[i])
 	    				{
 	    					cplex.addEq(y[i][j][f][delta], 0);
 	    				}
 			    	}
 		    	}
 	    	}	    			
 	    }
 	    
 	  
 	    
 	    //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	 //loop over middleboxes
 	    	for (int j=0; j<maxMiddlebox; j++)
 	    	{
 	    		for (int k=0; k<ks; k++)
 	    		{
 	    			h[i][j][k] = cplex.intVar(0, 1,"h "+i+j+k);
 		    		
 		    		//force h = 0 for middleboxes that does not exist for the service (n not in Ns). 
 		    		//Since hns specified that n and n+1 are on the same server, then h[n-1][s] should have a value but not h[n][s] since n+1 does not exist (Prevent bject is unknown to IloCplex)
 		    		if(j>=N[i]-1)
 					{
 						cplex.addEq(h[i][j][k], 0);
 					}
 	    		}
 	    		
 	    	}
 	    }
 	    
 	    //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	 //loop over middleboxes
 	    	for (int j=0; j<maxMiddlebox; j++)
 	    	{
 	    		//loop over servers
 	    		for (int k=0; k<ks; k++)
 		    	{
 	    			q[i][j][k] = cplex.intVar(0, 1,"q "+i+j+k);
 	    			
 	    			//force q = 0 for middleboxes that does not exist for the service (n not in Ns) (Prevent bject is unknown to IloCplex)
     				if(j>=N[i])
     				{
     					cplex.addEq(q[i][j][k], 0);
     				}
 		    	}
 	    	}
 	    }
 	    
 	    
 	    //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	 //loop over middleboxes
 	    	for (int j=0; j<maxMiddlebox; j++)
 	    	{
 	    		 //loop over VNF
 	    		for (int f=0; f<F; f++)
 		    	{
 	    			 //loop over time slots
 	    			for (int delta=0; delta<DELTA; delta++)
 			    	{
 	    				for (int k=0; k<ks; k++)
 	    				{
 	    					z[i][j][delta] = cplex.intVar(0, 1,"z "+i+j+delta);	
 	    				}
 	    				
 	    			
 			    	}
 		    	}
 	    	}	    			
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
	
		
	    //set objective function to maximize the number of admitted services
		cplex.addMinimize(this.rho);
	

		  /***************************************************************************************************************************
		   * *************************************************Constraints*****************************************************
		   * *************************************************************************************************************************/
	   
		/**
		 * C2 : ensures that a middlebox $n$ of service $s$ should be mapped to exactly one VNF instance $f$ of the same type if the service $s$ is admitted to the network
		 * sum over f sum over delta y[s][n][f][delta] = a[s] for all n for all s
		 */
		//loop over services
	    for(int i =0; i<S; i++)
	    {
	    	 //loop over middleboxes of the service
	    	for (int j=0; j<N[i]; j++)
	    	{
	    		IloNumExpr middleboxMappedConstraint = cplex.numExpr();
	    		
	    		 //loop over VNF
	    		for (int f=0; f<F; f++)
		    	{
	    			 //loop over time slots
	    			for (int delta=0; delta<DELTA; delta++)
			    	{
	    				middleboxMappedConstraint = cplex.sum(middleboxMappedConstraint, y[i][j][f][delta]);	
			    	}
	    			
		    	}
	    		
	    		cplex.addEq(middleboxMappedConstraint, 1, "Const2_sn ["+i+"]["+j+"]");//a[i]
	    	}	    			
	    }
	    
	    
		/**
		 * C3 :ensures that a middlebox $n$ of service $s$ should be mapped to a VNF instance $f$ of the same type if the service $s$ is admitted to the network
		 * sum over f sum over delta y[s][n][f][delta]t[f] = a[s]m[s][n] for all n for all s
		 */
		//loop over services
	   for(int i =0; i<S; i++)
	    {
	    	 //loop over middleboxes of the service
	    	for (int j=0; j<N[i]; j++)
	    	{
	    		IloNumExpr middleboxTypeConstraint = cplex.numExpr();
	    		
	    		 //loop over VNF
	    		for (int f=0; f<F; f++)
		    	{
	    			 //loop over time slots
	    			for (int delta=0; delta<DELTA; delta++)
			    	{
	    				middleboxTypeConstraint = cplex.sum(middleboxTypeConstraint, cplex.prod(y[i][j][f][delta], tf[f]));	
			    	}
	    			
		    	}
	    		
	    		cplex.addEq (middleboxTypeConstraint, msn[i][j], "Const3_sn ["+i+"]["+j+"]");// cplex.prod(a[i],msn[i][j])
	    	}	    			
	    }
	    
	    
	 
	    /**
		 * C4 :prevents a middlebox $(n+1)$ to start processing traffic of service $s$ before its precedent middlebox $n$ finishes its execution
		 * sum over f y[s][n+1][f][delta'] <= 1-sum over f y[s][n][f][delta] for all n,n+1, for all s, for all delta, delta'; delta'<delta+psn[s][n]
		 */
	    //loop over services
	   for(int i =0; i<S; i++)
	    {
	    	 //loop over middleboxes of the service N[i] specifies how many middlebox service i has.
	    	for (int j=0; j<N[i]-1; j++)
	    	{
	    	
	    		//loop over timeslots
    			for (int deltaPrime=0; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=0; delta<DELTA; delta++)
    		    	{
    	    			//we only need to add contraint for delta< deltaPrime+p[s][oe(e)]
    	    			if(deltaPrime>=delta+psn[i][j])
    	    			{
    	    				continue;
    	    			}
    	    			
    	    			IloNumExpr processStartOfnConstraint = cplex.numExpr();
    	    			IloNumExpr  processStartOfnPlusConstraint = cplex.numExpr();
    	    			 //loop over VNF
    		    		for (int f=0; f<F; f++)
    			    	{
    		    			processStartOfnPlusConstraint = cplex.sum (processStartOfnPlusConstraint, y[i][j+1][f][deltaPrime]);
    		    			processStartOfnConstraint = cplex.sum (processStartOfnConstraint, y[i][j][f][delta]);
    			    	}
    		    		
    		    		cplex.addLe (processStartOfnPlusConstraint, cplex.sum(1,cplex.prod(-1, processStartOfnConstraint)),"Const4_snDeltaprimeDelta ["+i+"]["+j+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}	
	    			
	    	}	    			
	    }
	    
	    
	    /**
  		 * C5 : specifies the physical server $k$ on which a middlebox $n$ of service $s$ is mapped
  		 *  q[s][n][k] = sum over f sum over delta y[s][n][f][delta]x[f][k]
  		 */	     
		     //loop over services
		   for(int i =0; i<S; i++)
		    {
		    	 //loop over middleboxes
		    	for (int j=0; j<N[i]; j++)
		    	{
		    		//loop over servers
		    		for (int k=0; k<ks; k++)
			    	{
		    			IloNumExpr mappingConstraint = cplex.numExpr();
		    			//loop over timeslots
						for (int delta=0; delta<DELTA; delta++)
				    	{
							//loop over timeslots
							for (int f=0; f<F; f++)
					    	{
								mappingConstraint = cplex.sum (mappingConstraint,cplex.prod(y[i][j][f][delta],xfk[f][k]));
					    	}
				    	}
		    			
						cplex.addEq(q[i][j][k], mappingConstraint,"Const5_snk ["+i+"]["+j+"]["+k+"]" );
			    	}
		    	}
		    }
	    
	    /**
  		 * C6 :specifies if two consecutive middleboxes $n$ and $(n+1)$ of the same service $s$ are mapped to VNFs $f$ and $f^\prime$ hosted on the same physical server
  		 * h[s][n] = q[s][j][k]  q[s][j+1][k] for all n, s, k->lineraize
  		 * Const12_1: h[s][j][k] <= q[s][j][k] for all n, s, k 
  		 * Const12_2:h[s][j][k] <= q[s][j+1][k] for all n, s, k 
  		 * Const12_3: h[s][j][k] >=  q[s][j][k] + q[s][j+1][k -1 for all n, s, f , f'
  		 * 
  		 */
  
		  for(int s =0; s<S; s++)
		   {
		    	 //loop over middleboxes
		    	for (int j=0; j<maxMiddlebox-1; j++)
		    	{
		    		for (int k=0; k<ks; k++)
			    	{
		    			cplex.addLe(h[s][j][k], q[s][j][k],"Const6_1_nSk ["+j+"][["+s+"]["+k+"]" );
		    			cplex.addLe(h[s][j][k], q[s][j+1][k],"Const6_2_nSk ["+j+"][["+s+"]["+k+"]" );
		    			cplex.addGe(h[s][j][k], cplex.sum (cplex.sum(q[s][j][k],q[s][j+1][k]), -1),"Const6_3_nSffPrime ["+j+"][["+s+"]["+k+"]" );
			    	
			    	}
		    	}
		    }

	    
			 
	    /**
		 * C7 :prevents a middlebox $(n+1)$ to start processing traffic of service $s$ before its precedent middlebox $n$ finishes its execution and transmission
		 * sum over f y[s][n+1][f][delta'] <= 1-sum over k h[s][n][k] sum over f y[s][n][f][delta] for all n,n+1, for all s, for all delta, delta'; delta'<delta+psn[s][n]
		 */
	    //loop over services
	  for(int i =0; i<S; i++)
	    {
	    	 //loop over middleboxes of the service N[i] specifies how many middlebox service i has.
	    	for (int j=0; j<N[i]-1; j++)
	    	{
	    		IloNumExpr sameServerConstraint = cplex.numExpr();
	    		for (int k=0; k<ks; k++)
		    	{
	    			sameServerConstraint = cplex.sum (sameServerConstraint, h[i][j][k]);
		    	}
	    		//loop over timeslots
	    		for (int delta=0; delta<DELTA; delta++)
		    	{
	    			IloNumExpr processStartOfnConstraint = cplex.numExpr();
	    			
	    			 //loop over VNF
		    		for (int f=0; f<F; f++)
			    	{		    			
		    			processStartOfnConstraint = cplex.sum (processStartOfnConstraint, y[i][j][f][delta]);
			    	}
		    		
	    			cplex.addLe(z[i][j][delta], sameServerConstraint,"Const7_1_sndelta ["+i+"][["+j+"]["+delta+"]" );
	    			cplex.addLe(z[i][j][delta], processStartOfnConstraint,"Const7_2_sndelta ["+i+"][["+j+"]["+delta+"]" );
	    			cplex.addGe(z[i][j][delta], cplex.sum (cplex.sum(sameServerConstraint,processStartOfnConstraint), -1),"Const7_3_sndelta ["+i+"][["+j+"]["+delta+"]" );
	    			
		    		//loop over timeslots
	    			for (int deltaPrime=0; deltaPrime<DELTA; deltaPrime++ )
	    	    	{
	    				//we only need to add constraint for delta+psn<=deltaPrime< delta+p[s][oe(e)]+Math.ceil((double)ws[i]/(double)bs[i]
    	    			if(deltaPrime<delta+psn[i][j] ||deltaPrime>=delta+psn[i][j]+Math.ceil((double)ws[i]/(double)bs[i]))
    	    			{
    	    				continue;
    	    			}
    	    			
    	    		
    	    			IloNumExpr  processStartOfnPlusConstraint = cplex.numExpr();
    	    			 //loop over VNF
    		    		for (int f=0; f<F; f++)
    			    	{
    		    			processStartOfnPlusConstraint = cplex.sum (processStartOfnPlusConstraint, y[i][j+1][f][deltaPrime]);
    		    		
    			    	}
    		    		   		    		
		    			cplex.addLe(processStartOfnPlusConstraint,cplex.sum(cplex.diff(1,processStartOfnConstraint),z[i][j][delta]),"Const7_sndelta ["+i+"][["+j+"]["+delta+"]" );
		    	
    		    	}
    	    	}	
	    			
	    	}	    			
	    }
		  
	    
	 /**
	  * This constraint ensures that a function canot process 2 services at the same time and ensures that it is processing a service during all the processing time
	  * C8-sum over s'different than s,over n' y[n'][s'][f][deltaprime]<=1-y[n][s][f][delta] for all s for all n for all f, for all delta, delta<=deltaprime<delta+psn
	  */
	 
	    for(int s =0; s<S; s++)
	    {
	    	//loop over middleboxes of the service s
	   		for (int i=0; i<N[s]; i++)
	    	{
	    	
			    for (int f=0; f<F; f++)
			    {
			    	//allow ingress and egress node to process at the same time
					if (tf[f]==0 ||tf[f] ==1)
					{
						continue;
					}
					//loop over timeslots
			    	for (int delta=0; delta<DELTA; delta++)
				    {
			    		for (int deltaPrime=delta; deltaPrime<delta+psn[s][i]&&deltaPrime<DELTA; deltaPrime++)
					    {   			
					    	
				    		IloNumExpr processSameVNFConstraint = cplex.numExpr();
				    		
				    		for(int sp =0; sp<S; sp++)
				      	    {

						    	if (s == sp)
						    	{
						    		continue;
						    	}
						    	
						    	//loop over middleboxes of the service s
						   		for (int ip=0; ip<N[sp]; ip++)
						    	{
						   			processSameVNFConstraint = this.cplex.sum (processSameVNFConstraint, y[sp][ip][f][deltaPrime]);
						    	}
				    			
				      	    }
				    		
				    		cplex.addLe(processSameVNFConstraint ,cplex.diff(1,y[s][i][f][delta]),"Const8_SNFDelta ["+s+"]["+i+"]["+f+"]["+delta+"]" );
					    }
			    		
				    }
			    }
	    	}
	    }
	 /**
	  * C9: Ensure that a VNf can process the traffic of one service at a time NOT needed cover by const 8
	  * sum over all s, over all n of the same type of f y[s][n][f][delta] <=1 for all f for all delta
	  */
	 //loop over VNF
	/*for (int f=0; f<F; f++)
    {
		//allow ingress and egress node to process at the same time
		if (tf[f]==0 ||tf[f] ==1)
		{
			continue;
		}
		//loop over timeslots
    	for (int delta=0; delta<DELTA; delta++)
	    {
    		IloNumExpr processSameVNFConstraint = cplex.numExpr();
    		
    		for(int s =0; s<S; s++)
      	    {
    			//loop over middleboxes of the service s
 	 	   		for (int i=0; i<N[s]; i++)
 	 	    	{
 	 	   			if (msn[s][i] !=tf[f] )
	 	    		{ 
 	 	   				continue;
	 	    		}
 	 	   			
 	 	   			processSameVNFConstraint = cplex.sum(processSameVNFConstraint, y[s][i][f][delta]); 	 	   			
 	 	    	}
      	    }
    		
    		cplex.addLe(processSameVNFConstraint, 1,"Const9_DeltaF ["+delta+"]["+f+"]" );
	    }
    }*/
	 
	
	  
  	    /**
  		 * C10 :specifies the completion time of a service
  		 * sum over f sum over delta rho>=y[s][|Ns|][f][delta](delta+psn[s][|Ns|]  for all s, 
  		 */
	    //loop over services
	    for(int i =0; i<S; i++)
	    {
	    	IloNumExpr processedBeforeDeadlineConstraint = cplex.numExpr();
	    	
	    	//loop over VNF
	    	for (int f=0; f<F; f++)
		    {
    			 //loop over time slots
    			for (int delta=0; delta<DELTA; delta++)
		    	{
    				processedBeforeDeadlineConstraint = cplex.sum(processedBeforeDeadlineConstraint, cplex.prod(y[i][N[i]-1][f][delta], delta+psn[i][N[i]-1])) ;	
		    	}
	    	}
	    	
	    	cplex.addGe(rho, processedBeforeDeadlineConstraint, "Const10_S ["+i+"]");
	    
	    }
	      
	  
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
			this.cplex.exportModel("ILPMinMaxCT_InputResults.lp");
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
    
    
    /**
     * This method will report the ILP inputs and results to a file called ILP/ILPResults_setId
     * It will also print the results to the console
     * 
     * @param filePath path to the file  where to write the results ("ILP/ILPResults_"+setId+".txt")
     */
    public void reportResults(String filePath){
    	String st ="";
    			
    	st+=this.toString();
    	
    	//print results
    	st+="\t RESULTS\n";
		try {
			st+=String.format("\t\t Objective = %f\n",this.cplex.getObjValue());
		//	st+="\t\t"+Output.printArray(a,"a[s]", "\t\ta",this.cplex);
			st+="\t\t"+Output.printQuadrupleArray(y,"y[s][n][F][DELTA]", "\t\ty",this.cplex);
			
			st+="\t\t"+Output.printTripleArray(q,"q[s][n][ks]","\t\tq", this.cplex); 
			st+="\t\t"+Output.printTripleArray(h,"h[s][n][ks]","\t\th", this.cplex);
			st+="\t\t"+Output.printTripleArray(z,"z[s][n][delta]","\t\tz", this.cplex);
			st+="\t\t Services Completion Times "+this.printCompletionTimes();
			st+="\n\t\t Nb of Admitted Services: "+this.getNbOfAdmittedServices();
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
    
    
    /**
     * This method will return a string with the completion time of the services
     * @return
     * @throws UnknownObjectException
     * @throws IloException
     */
    public String  printCompletionTimes() throws UnknownObjectException, IloException
    {
    	String st ="";
      	  //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=0; delta<DELTA; delta++)
		    	{
    				 if (y[i][N[i]-1][f][delta]!=null && cplex.getValue(y[i][N[i]-1][f][delta]) >0.9)//to handle rounding values around 1
    				{
    					 st+=String.format(delta+"\t");  
    				}
		    	}
    	
	    	}	    			
 	    }
 	    
 	    return st;
    }
    
    
    
    /**
     * This method will return the number of admitted services by checking the deadline vs completion time
     * @return number of admitted services
     * @throws UnknownObjectException
     * @throws IloException
     */
    public double  getNbOfAdmittedServices() throws UnknownObjectException, IloException
    {
    	int nbOfadmittedServices =0;
    	
      	  //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	 //loop over VNF
    		for (int f=0; f<F; f++)
	    	{
    			 //loop over time slots
    			for (int delta=0; delta<DELTA; delta++)
		    	{
    				 if (y[i][N[i]-1][f][delta]!=null && cplex.getValue(y[i][N[i]-1][f][delta]) >0.9)//to handle rounding values around 1
    				{
    					 if (delta<=this.us[i])
    					 {
    						 nbOfadmittedServices++;
    					 }
    				}
		    	}
    	
	    	}	    			
 	    }
 	    
 	    return nbOfadmittedServices;
    }
    
    public String toString()
    {
    	String st="";
    	st+=" ILP MODEL \n";
    	st+= "\t Parameters:\n";	    	
		st+= "\t\t"+Output.printDoubleMatrice(G,"GRAPH");		
		st+= "\t\t"+Output.printTable(tf, "VNF TYPES");
		st+= "\t\t"+Output.printDoubleMatrice(xfk,"VNF MAPPING TO PHYSICAL SERVERS");
		st+= Output.printTable(N, "NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE");
		st+= Output.printDoubleMatrice(msn,"SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)");
		st+= Output.printDoubleMatrice(psn,"PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE");
		st+= Output.printTable(bs, "BANDWIDTH REQUIRED BY EACH SERVICE");
		st+= Output.printTable(ws, "TRAFFIC SIZE OF EACH SERVICE");
		st+= Output.printTable(us, "DEADLINE TIME OF EACH SERVICE");
		
		st+="\t\t"+this.physicalNetwork.toString()+"\n";
		
		st+="\t\t Services\n";
		for (int i=0; i<this.services.size(); i++)
		{
			st+= "\t\t"+this.services.get(i).toString();
		}
		
		return st;
		
    }

    /**
     * This function is use to run the model and return an array containing the number of admitted services and the execution time
     * @param network
     * @param services
     * @param resultsFile file to dump the input and results of the model
     * @return double [obj][exectime]
     * @throws IloException
     */
	public static double[] runModelForResults (Network network, ArrayList<Service> services, String resultsFile ) throws IloException
	{
		double []  res = new double[3];
		double startTime = 0;
		double endTime = 0;
		double execTime =0;
		
		int delta = ServicesDriver.getHighestDeadline(services)*2;
		ILPMinMaxCT model = new ILPMinMaxCT(network, services, delta);
		
		model.buildILPModel();
		
		startTime = System.currentTimeMillis();
		
		model.runILPModel(resultsFile);		
		
		endTime = System.currentTimeMillis();
		execTime = endTime - startTime;
		
		
		res[0] = model.getNbOfAdmittedServices();
		res[1] = execTime;
		res[2] = model.objectiveValue;
		return res;
	}
	
	
	public static void main(String[] args) throws Exception 
	{
		double []  res = new double[3];
		Network network = new Network(7,5,5,5,500,500,1,0);
		ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();
		System.out.println (network);
		int [] tf = physicalNetwork.get(2)[0];
		
		ServicesDriver driver = new ServicesDriver (4,  300, 3,5, 300,500, 5 , 1, 500, 1500,tf);
		 ArrayList<Service>services = driver.generateServices(20.0,10.0, 100, 14);
		 
		res = ILPMinMaxCT.runModelForResults (network, services,"ILPRes.txt");
		
		int objective = (int)res[2];
		double execTime = res[1];
		int admittedServicesNb = (int) res[0];
		
		System.out.println ("max CT ="+objective+"\t exec Time ="+execTime+"\t admittedServicesNb ="+admittedServicesNb);
		
		
		
	}

}



