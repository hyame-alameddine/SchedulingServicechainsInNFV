/**
 * This model take a set of services mapped and routed and perform their scheduling while minimzing the schedule length
 * The model support online scheduling by considering not starting  the service schedule before a start time (batch scheduling)
 * However, when running for a btach all services of previous batch should be send and their decision variables should be set based on optimal solution
 * 
 * The model supports have services having many middleboxes of the same type in the chain 
 */
package Model;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;
import ilog.cplex.IloCplexModeler.Exception;

import java.io.IOException;
import java.util.ArrayList;

import HelperClasses.FileManipulation;
import HelperClasses.Output;
import HelperClasses.SchedulingSolution;
import HelperClasses.ServicesDriver;
//import HelperClasses.SchedulingSolution;
import NFV.Middlebox;
import NFV.Service;
import NFV.VLink;
import Network.Link;
import Network.Network;
import Network.PhysicalMachine;


public class SchedulingModel {
	
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
	
	//specifies capacity of physical links interconnecting the servers 
	int [][] cij;		
	
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
	int [] us;				
	
	//list of virtual links for all services  (E[1] = 2 mean that edge 1 is for service 2)
	int [] E;
	
	//this holds the middlebox which are origin of edge e (oe [1] = 2 means that edge 1 has middlebox 2 as origin)
	int[]oe;
	
	//this holds the middlebox which are destination of edge e (de [1] = 2 means that edge 1 has middlebox 2 as destination)
	int []de;
	
	//r[s][n][f] indicates that NF n of service s is mapped to VNF f
	int [][][]r;
	
	//h[s][n][k] indicates that middleboxes n, (n+1)  of service s are hosted on the same node k(1) (or not, 0).\\
	int[][][] h;
	
	//l[i][j][e] specifies that the virtual link e  of service s  is routed through physical link (ij)  (1) (or not, (0)).
	int [][][]l ;
	
	

	/************************************************/
	
	ArrayList<Service> services;
	Network physicalNetwork;
	
	/***************************************************************************************************************************
	 * ****************************************INITIALZE DECISION VRIABLES******************************************************
	 * *************************************************************************************************************************/
	//rho specifies the makespan earliest completion time of al the services
	public IloIntVar rho;
	
	//specifies that the traffic of service s started processing at time slot delta  on middlebox n scheduled/mapped to VNF f
	public IloIntVar[][][][] y;

	//designates that a middlebox o(e) of service s begins the transmission of traffic to its successor middlebox d(e)  at time slot \delta  on virtual link e  (1) (or not, 0). 
	public IloIntVar[][][]theta;
	
	// indicates that the virtual link e is used for traffic transmission between middleboxes o(e), d(e) of service s  at time slot \delta (1)   (or not, 0).
	public IloIntVar[][][]thetaHat;
	
	//time before which  services can not be scheduled- used for batch
	public int startSchedule;
	
	//needed to initialize variables in case of batch, this is the first service to schedule in a batch
	public int firstServiceInBatchId;
	
	
    public SchedulingModel(int[][]G, int [][]xfk, int[][]cij, int[]tf, int[]N, int []E, int []oe, int []de, int []bs, int []ws, int [] us, int[][] psn,int[][]msn, int delta, int maxMiddlebox, int [][][] l, int [][][]h, int[][][]r,  int startSchedule) throws IloException
    {
    	this.objectiveValue = -1; //set to -1 by default
    	this.cplex = new IloCplex();
    	services = new ArrayList<Service>();
    	this.physicalNetwork = new Network();
    	
    	this.G = G;
    	this.xfk=xfk;
    	this.cij = cij;
    	this.tf = tf;
    	this.E= E;
     	this.oe = oe;
     	this.de = de;	     	
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
    	this.l = l;
    	this.h =h;
    	this.r=r;
    	this.startSchedule = startSchedule;
		y = new IloIntVar[S][this.maxMiddlebox][F][DELTA];
		
		theta = new IloIntVar[S][DELTA][E.length];
		thetaHat = new IloIntVar[S][DELTA][E.length];
	
    }
    
    /**
     * firstServiceInBatchId set to 0 if no batch
     * @param network
     * @param services services that are mapped and routed
     * @param delta
     * @throws IloException
     */
    public SchedulingModel(Network network, ArrayList<Service> services, int delta, int startSchedule, int firstServiceInBatchId) throws IloException
	{ 	
    	    	
		int totalVirtualLinks=0;
		int count =0; 
		int countinit =0;
		VLink vl = null;
		Link pl = null;
		PhysicalMachine pm ;
		
		this.objectiveValue = -1; //set to -1 by default
		this.maxMiddlebox =0;
	   	this.cplex = new IloCplex();
		this.services = services;
		this.startSchedule = startSchedule;
    	this.firstServiceInBatchId = firstServiceInBatchId;
    	
		this.physicalNetwork = network;
	   	ArrayList<int[][]>networkArray = this.physicalNetwork.getPhysicalNetworkArray();
    	
    	//setting the parameters
    	this.G = networkArray.get(0);
    	this.cij = networkArray.get(1);		
    
		//this tf is different from the one in the network array. The networkarray contains the list of type
    	//here, tf should have the type of each vnf. if we have 4 vnf and 3 types; tf = [0,1,2,3,3] by network array tf [0,1,2,3]
    	this.tf = new int[network.getVNFNb()];
    	for(int i=0; i<network.getPmList().length;i++)
    	{
    		pm = network.getPmList()[i];
    		for(int j=0;j<pm.getHostedVNFs().size(); j++)
    		{
    			tf[pm.getHostedVNFs().get(j).getId()] = pm.getHostedVNFs().get(j).getType();
    		}
    	}
    	this.xfk = networkArray.get(3);
    	 
		this.DELTA = delta;    	
     	this.F = network.getVNFNb();
    	this.ks = this.G.length;
    
	
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
	    	totalVirtualLinks+=s.getVirtualLinks().size();
	    	
	    	if (s.getN()>this.maxMiddlebox)
	    	{
	    		this.maxMiddlebox = s.getN();
	    	}
		}

    	this.psn = new int [S][this.maxMiddlebox];
    	this.msn= new int [S][this.maxMiddlebox];
    	this.oe = new int [totalVirtualLinks];
    	this.de = new int [totalVirtualLinks];
    	this.E = new int[totalVirtualLinks];
    	
    	this.l = new int [ks][ks][E.length];
    	this.r = new int [S][this.maxMiddlebox][this.xfk.length];
		this.h = new int [S][this.maxMiddlebox][this.ks];
    	
		for (int i=0; i<this.services.size(); i++)
		{
    		s= this.services.get(i);
	    	for (int j=0; j<this.maxMiddlebox; j++)
	    	{
	    		
	    		this.psn [i][j] = (j<s.getN())? s.getMiddleboxes().get(j).getProcessingTime(): -1;
	    		this.msn[i][j] = (j<s.getN())?s.getMiddleboxes().get(j).getType():-1;
	    	
	    		//populate r
	    		if(j<s.getN())
	    		{
	    			this.r[i][j][s.getMiddleboxes().get(j).getMappedToVNF().getId()]=1;
	    		}
	    	
	    		//populate h for links who has source and destination mapped to the same server
	    		if (j<s.getN()-1)
	    		{
	    			this.h[i][j][s.getVirtualLinks().get(j).getSource().getMappedToVNF().getPmId()] = s.getVirtualLinks().get(j).areHostedOnSameServer() ? 1:0;
	    		}
	    	}
	    	
	    	for (int j=count; j<count+s.getVirtualLinks().size(); j++)
	    	{
	    		vl = s.getVirtualLinks().get(countinit);
	    		this.oe[j] =  vl.getSource().getId();
	    		this.de[j] =  vl.getDestination().getId();
	    		this.E[j] =  s.getId();
	    			    		
	    		//set l
	    		for (int k=0; k<vl.getRoutedThrough().size(); k++ )
	    		{
	    			//get physical links
	    			pl = vl.getRoutedThrough().get(k);    			
	    			
	    			this.l[pl.getSource()][pl.getDestination()][j] = 1 ;
	    			pl = null;
	    		}
	    		
	    		
	    		countinit++;
	    	}
	    	countinit =0;
	    	count+=s.getVirtualLinks().size();
		}
    	
    	

   	    	//System.out.println(this.toString());
    	//setting the decision variables			
   	    
		y = new IloIntVar[S][this.maxMiddlebox][F][DELTA];
	
		theta = new IloIntVar[S][DELTA][E.length];
		thetaHat = new IloIntVar[S][DELTA][E.length];
	
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
 	    				 	    			
 	    				
 	    				//start scheduling at startSchedule and set the variables only for services of the new batch, old batches services will be handled in opyimalSolution
 	    				if ((i>=this.firstServiceInBatchId)&&(delta<this.startSchedule||j>=N[i]))
 	    				{
 	    					cplex.addEq(y[i][j][f][delta], 0);
 	    				}
 			    	}
 		    	}
 	    	}	    			
 	    }
 	    
 	   
 	    
 	    	    
 	    //loop over services s
 	    for(int i =0; i<S; i++)
 	    {
 	    	//loop over timeslots
 			for (int delta=0; delta<DELTA; delta++)
 	    	{
 	    		//loop over edges
 	    		for (int e=0; e<E.length; e++)
 		    	{
 	    			theta[i][delta][e] = cplex.intVar(0, 1,"theta "+i+delta+e);
 	    			
 	    			//If the virtual link is not for the service than make sure to set it to 0 (no transmission is possible on it)
 	    			if (E[e]!=i)
 	    			{
 	    				cplex.addEq(theta[i][delta][e], 0);
 	    			}
 	    			
 	    			//start scheduling at arrival time
 	    			if (this.services.size()!=0 && i>=this.firstServiceInBatchId && delta<startSchedule)
 	 				{
 	    				cplex.addEq(theta[i][delta][e], 0);
 	 				}
 		    	}
 	    	}
 	    }
 	    
 	    //loop over services s
 	    for(int i =0; i<S; i++)
 	    {
 	    	//loop over timeslots
 			for (int delta=0; delta<DELTA; delta++)
 	    	{
 	    		//loop over edges
 	    		for (int e=0; e<E.length; e++)
 		    	{
 	    			thetaHat[i][delta][e] = cplex.intVar(0, 1,"thetaHat "+i+delta+e);
 	    			//If the virtual link is not for the service than make sure to set it to 0 (no transmission is possible on it)
 	    			if (E[e]!=i)
 	    			{
 	    				cplex.addEq(thetaHat[i][delta][e], 0);
 	    			}
 	    			
 	    			//start scheduling at arrival time
 	    			if (this.services.size()!=0 && i>=this.firstServiceInBatchId&& delta<startSchedule)
 	 				{
 	    				cplex.addEq(thetaHat[i][delta][e], 0);
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
	   
	    
		//set the objective 
	   	cplex.addMinimize(rho);
		
	  
		
		
		  /***************************************************************************************************************************
		   * *************************************************Constraints*****************************************************
		   * *************************************************************************************************************************/

		    
	    /**
		 * C1 :prevents the transmission of traffic of a service $s$ between two consecutive middleboxes $n$ and ($n+1$), if its processing on $n$ has not been completed
		 * theta [s][delta'][e] <= 1-sum over f y[s][o(e)][f][delta] r[s][o(e)][f] for all e, for all s, for all delta, delta'; delta'<delta+p[s][o(e)]
		 */
	    //loop over services s
	   for(int i =0; i<S; i++)
	    {
	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{
    			//don't add contraints for edges that are not for the service
    			if (E[e] != i)
    			{
    				continue;
    			}
    			
    			//loop over timeslots
    			for (int deltaPrime=0; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=0; delta<DELTA; delta++)
    		    	{
    	    			//we only need to add contraint for delta< deltaPrime+p[s][oe(e)]
    	    			if(deltaPrime>=(delta+psn[i][oe[e]]))
    	    			{
    	    				continue;
    	    			}
    	    			
    	    			IloNumExpr transmissionStartConstraint = cplex.numExpr();
    	    			
    	    			 //loop over VNF
    		    		for (int f=0; f<F; f++)
    			    	{
    		    			transmissionStartConstraint = cplex.sum(transmissionStartConstraint, cplex.prod(r[i][oe[e]][f], y[i][oe[e]][f][delta]));
    			    	}
    		    	
    		    		cplex.addLe( theta[i][deltaPrime][e],cplex.sum(1, cplex.prod(-1,transmissionStartConstraint)), "Const1_seDeltaprimeDelta ["+i+"]["+e+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}
    			
	    	}
	    	
	    }
	    
	    /**
		 * C2 :ensures that traffic of a service $s$ can not be processed by a middlebox $(n+1)$ if it was not transmitted to it by its precedent middlebox $n$
		 * sum over f y[s][n][f][delta']r[s][d(e)][f] <= 1-theta[s][delta][e] for all e, for all s, for all delta, delta'; delta'<delta+ws[s]/bs[s]
		 */
	    //loop over services s
	  for(int i =0; i<S; i++)
	    {
	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{
    			//don't add contraints for edges that are not for the service
    			if (E[e] != i)
    			{
    				continue;
    			}
    			
    			//loop over timeslots
    			for (int deltaPrime=0; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=0; delta<DELTA; delta++)
    		    	{
    	    			//we only need to add contraint for delta< deltaPrime+ws[i]/bs[i]
    	    			if(deltaPrime>=delta+Math.ceil((double)ws[i]/(double)bs[i]))
    	    			{
    	    				continue;
    	    			}
    	    			
    	    			IloNumExpr transmissionFinishedConstraint = cplex.numExpr();
    	    			
    	    			 //loop over VNF
    		    		for (int f=0; f<F; f++)
    			    	{
    		    			transmissionFinishedConstraint = cplex.sum(transmissionFinishedConstraint, cplex.prod(y[i][de[e]][f][deltaPrime], r[i][de[e]][f]));
    			    	}
    		    		
    		    		cplex.addLe(transmissionFinishedConstraint, cplex.sum(1, cplex.prod(-1,theta[i][delta][e])),"Const2_seDeltaprimeDelta ["+i+"]["+e+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}
    			
	    	}
	    	
	    }
	    
	    /**
		 * C3 :prevents a middlebox $(n+1)$ to start processing traffic of service $s$ before its precedent middlebox $n$ finishes its execution
		 * sum over f y[s][n+1][f][delta']r[s][n+1][f] <= 1-sum over f y[s][n][f][delta]r[s][n][f] for all s, for all delta, delta'; delta'<delta+psn[s][n]
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
    		    		
    		    			processStartOfnPlusConstraint = cplex.sum (processStartOfnPlusConstraint, cplex.prod(y[i][j+1][f][deltaPrime], r[i][j+1][f]));
    		    			processStartOfnConstraint = cplex.sum (processStartOfnConstraint, cplex.prod(y[i][j][f][delta], r[i][j][f]));
    			    	}
    		    		
    		    		cplex.addLe (processStartOfnPlusConstraint, cplex.sum(1,cplex.prod(-1, processStartOfnConstraint)),"Const3_snDeltaprimeDelta ["+i+"]["+j+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}	
	    			
	    	}	    			
	    }

		/**
		 * C11 :ensures that a service start processing on a VNF instance $f$ to which it is mapped
		 * sum over f sum over delta y[s][n][f][delta]r[s][n][f] = 1 for all n for all s
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
	    				    					
	    				middleboxTypeConstraint = cplex.sum(middleboxTypeConstraint, cplex.prod(y[i][j][f][delta], r[i][j][f]));	
	    				
			    	}
	    			
		    	}
	    		cplex.addEq (middleboxTypeConstraint, 1, "Const11_sn ["+i+"]");
	    		
	    	}	    			
	    }

	    /**
	     * C4 makes sure that a VNF f can start processing one service at a time exception for ingress and egress
	     * sum over s , n y[s][n][f][delta] <=1 for all f for all delta
	     */

		
		 //loop over VNF
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
		    		
		    		cplex.addLe(processSameVNFConstraint, 1,"Const4_DeltaF ["+delta+"]["+f+"]" );
			    }
		    }
			 
	
	    /**
	     * C5 makes sure that a VNF f can start processing one service at a time during all processing period (exception for ingress and egress)
	     * sum over s',n' y[s'][n'][f][deltaPrime] <=1- y[s][n][f][delta]r[s][n][f] for all f for all delta<=deltaprime<delta+pns for all s for all n
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
						   		for (int np=0; np<N[sp]; np++)
						    	{
						   			processSameVNFConstraint = cplex.sum (processSameVNFConstraint, y[sp][np][f][deltaPrime]);
						    	}
				    			
				      	    }
				    		
				    		cplex.addLe(processSameVNFConstraint ,cplex.diff(1,cplex.prod(y[s][i][f][delta], r[s][i][f])),"Const5_SNFDelta ["+s+"]["+i+"]["+f+"]["+delta+"]" );
					    }
			    		
				    }
			    }
	    	}
	    }
	   
  	    /**
  		 * C6 :ensures that a service should be able to be processed before its deadline $u_s$ to be admitted in the network
  		 * rho>=sum over f sum over delta y[s][|Ns|][f][delta]r[s][|Ns|][f](delta+psn[s][|Ns|]  for all s, 
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
    				processedBeforeDeadlineConstraint = cplex.sum(processedBeforeDeadlineConstraint, cplex.prod(cplex.prod(y[i][N[i]-1][f][delta],r[i][N[i]-1][f]), delta+psn[i][N[i]-1])) ;	
		    	}
	    	}
	    	
	    	cplex.addGe(rho, processedBeforeDeadlineConstraint,"Const6_S ["+i+"]");
	    }
	    
	   
	    
	    /**
  		 * C7 :prevents the start of the transmission of traffic of a service $s$ between two consecutive middleboxes $o(e)$ and $d(e)$ if they are mapped to VNFs hosted on the same physical server 
  		 * sum over delta theta[s][delta][e] = (1-sum over k h[o(e)][s][k]) for all e for all s
  	
  		 */
	
	    //loop over services s
	    for(int i =0; i<S; i++)
	    {
	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{ 
    			//add constraint only for edges of the service
    			if (E[e]!=i)
    			{
    				continue;
    			}
    			
    			IloNumExpr preventTransmissionConstraint = cplex.numExpr();
    			IloNumExpr physicalServerMappingConstraint = cplex.numExpr();
    			//loop over timeslots
				for (int delta=0; delta<DELTA; delta++)
		    	{		    		
					preventTransmissionConstraint = cplex.sum(preventTransmissionConstraint, theta[i][delta][e]);			    	
		    	}
				
				for (int k=0; k<ks; k++)
		    	{
					physicalServerMappingConstraint = cplex.sum(physicalServerMappingConstraint, h[i][oe[e]][k]);
		    	}
				cplex.addEq(preventTransmissionConstraint, cplex.diff(1, physicalServerMappingConstraint ),"Const7_se ["+i+"]["+e+"]" );
				
	    	}
	    }
  	    
	    /**
  		 * C8 :specifies that the virtual link $e$ is used to transmit traffic of service $s$ during all the required transmission time ($\frac{w_s}{b_s}$)
  		 *  between middleboxes o(e) and d(e) if they were mapped to VNFs hosted on different physical servers.
  		 *  It prevents the transmission in case both middleboxes $o(e)$ and $d(e)$ are mapped to VNFs hosted on the same physical server
  		 *  
  		 *  sum over delta thetaHat[s][delta][e] = ws[s]/bs[s] sum over delta theta[s][delta][e]  for all e for all s
  		 */
	    //loop over services s
	   for(int i =0; i<S; i++)
	    {
	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{
    			//add constraint only for edges of the service
    			if (E[e]!=i)
    			{
    				continue;
    			}
    			
    			IloNumExpr transmissionTimeConstraint = cplex.numExpr();
    			IloNumExpr transmissionStartConstraint = cplex.numExpr();
    			//loop over timeslots
				for (int delta=0; delta<DELTA; delta++)
		    	{		    		
					transmissionTimeConstraint = cplex.sum(transmissionTimeConstraint, thetaHat[i][delta][e]);	
					transmissionStartConstraint = cplex.sum(transmissionStartConstraint, theta[i][delta][e]);
		    	}
				
				cplex.addEq(transmissionTimeConstraint, cplex.prod (Math.ceil((double)ws[i]/(double)bs[i]),transmissionStartConstraint),"Const8_se ["+i+"]["+e+"]" );
	    	}
	    }
	    
	    
	    /**
  		 * C9 : ensures the virtual link $e$ is used for transmission of traffic of service $s$ during all the transmission delay
  		 *  ($\frac{w_s}{b_s}$) starting at time slot $\delta$ when the traffic transfer begun.
  		 *  sum over deltaPrime from delta to delta+ws[s]/bs[s]-1 thetaHat[s][deltaPrime][e] >= ws[s]/bs[s]theta[s][delta][e]  for all e for all s for all delta
  		 */
	     //loop over services s
	   for(int i =0; i<S; i++)
	     {
	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{
    			//add constraint only for edges of the service
    			if (E[e]!=i)
    			{
    				continue;
    			}
    			
		    	//loop over timeslots
				for (int delta=0; delta<DELTA; delta++)
		    	{	
	    			IloNumExpr transmissionTimeConstraint = cplex.numExpr();
	    			
	    			//prevent a null pointer exception with thetaHat[i][deltaPrime][e]
	    			if (delta+Math.ceil((double)ws[i]/(double)bs[i])-1 >=DELTA)
	    			{
	    				continue;
	    			}
	    			
					//loop over timeslots
					for (int deltaPrime=delta; deltaPrime<=delta+Math.ceil((double)ws[i]/(double)bs[i])-1; deltaPrime++)
			    	{
						transmissionTimeConstraint = cplex.sum(transmissionTimeConstraint, thetaHat[i][deltaPrime][e]);	
			    	}
					
					cplex.addGe(transmissionTimeConstraint, cplex.prod (Math.ceil((double)ws[i]/(double)bs[i]),theta[i][delta][e]),"Const9_seDelta ["+i+"]["+e+"]["+delta+"]" );
		    	}				
	    	}
	    }
    
	    
	     /**
  		 * C10 : ensures that the physical links capacity constraint is not violated
  		 * sum over s sum over e l[i][j][e]thetaHat[s][delta][e] b[s] <= cij[i][j] for all delat , for all (ij) in L->linearize
  		 *
  		 */
	     
	  	   //loop over timeslots
			for (int delta=0; delta<DELTA; delta++)
	    	{
				//loop over servers
	   		    for(int i =0; i<ks; i++)
	   		    {
	   		    	 //loop over servers
	   		    	for (int j=0; j<ks; j++)
	   		    	{
	   		    		//make sure the physical link exists -- check only on G[i][j] or G[j][i] because we only have one link ij. 
	   		    		if (G[i][j]==1 ) 
	   		    		{
		   		    		IloNumExpr bandwidthConstraint = cplex.numExpr();
		   		    		
			   		    	 //loop over services s
			   			     for(int s =0; s<S; s++)
			   			     {
			   			    	//loop over edges
			   			   		for (int e=0; e<E.length; e++)
			   				    {
			   			   			//add constraint only for edges of the service
			   			   			if (E[e]!=s)
			   			   			{
			   			   				continue;
			   			   			}
			   			   			
			   			   		
			   			   			bandwidthConstraint = cplex.sum(bandwidthConstraint, cplex.prod(thetaHat[s][delta][e],l[i][j][e]*bs[s]));
			   			   					   			   	
			   				    }
			   			     }   			    
			   			     
			   			  cplex.addLe(bandwidthConstraint, cij[i][j],"Const10_DeltaIJ ["+delta+"]["+i+"]["+j+"]" );
			   		
	   		    		}
	   		    	
	   		    	}
	   		    }
	    	} 
	  
	}
    
    /**
     * this method will update the schedule of the services 
     * 
     * @throws IloException 
     * @throws UnknownObjectException 
     * 
     */
    public void updateServices() throws UnknownObjectException, IloException
    {
    	
		Service s = null;
		Middlebox m;
		int count =0;
		boolean firstLink = false;
		int isTransmitting = 0;
		
		//loop over services 
    	for(int i=0; i<this.services.size();i++)
		{
			s = services.get(i);
				
			
			 //loop over VNF
			//loop over middleboxes
 	    	for (int j=0; j<maxMiddlebox; j++)
 	    	{
 	    		//reset count for the service to know which middlebox is for the service and we need to set started processing
 	    		if (count >s.getN())
 	    		{
 	    			count =0;
 	    		}
 	    		
 	    		 //loop over VNF
 	    		for (int f=0; f<F; f++)
 		    	{
 	    			 //loop over time slots
 	    			for (int delta=0; delta<DELTA; delta++)
 			    	{
 	    				
 	    				//force y = 0 for functions to which the service is not mapped (Prevent object is unknown to IloCplex)
 	    				if(r[i][j][f] ==0 || this.cplex.getValue(this.y[i][j][f][delta] ) ==0)
 	    				{
 	    					continue;
 	    				}
 	    				
 	    				m = s.getMiddleboxes().get(count);
 	    				m.setStartedProcessing(delta);		
 	    				
 			    	}
 		    	}
 	    		count++;
 	    	}	    	
 	    	
 	    	//update is processing
 	    	for(int j=0; j<s.getMiddleboxes().size(); j++)
 	    	{
 	    		m = s.getMiddleboxes().get(j);
 	    		for (int k=0; k<m.getIsProcessing().length; k++) 	    	
				{ 	    			
 	    			m.getIsProcessing()[k] = m.getStartedProcessing()+k;
				}
 	    	}
 	    	
 	    	//set to 0 because we need to use  it again
 	    	count =0;
 	    	
 	    	//update routing schedule
 	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{
    			if (E[e]!=s.getId())
    			{
    				//reset to false as soon as i reach a vlink not for the service
    				firstLink = false;
    				continue;
    			}
    			else 
    			{
    				if (firstLink == false)
    					firstLink= true;
    			}    			
    			
	 	    	//loop over timeslots
	 			for (int delta=0; delta<DELTA; delta++)
	 	    	{	    			
 	    			
 	    			if (this.cplex.getValue(this.theta[i][delta][e]) == 1)
 	    			{
 	    				s.getVirtualLinks().get(count).setStartedTransmition(delta);
 	    			}
 	    				    			
 	    			
 	    			if (this.cplex.getValue(this.thetaHat[i][delta][e]) == 1)
 	    			{
 	 	    			s.getVirtualLinks().get(count).getIsTransmitting()[isTransmitting] = delta;
 	 	    			//increment as long as we started transmission and is transmitting is valid (thetaHat = 1)
 	 	    			isTransmitting++;
 	    			}
 	    			
 		    	}
	 			
	 			//increment the count because the next link might be for the service as well
    			if(firstLink == true)
    			{
    				count++;
    			}
	 			
	 			isTransmitting =0;
	    	}
 	    	
    		s.setCompletionTime(s.getMiddleboxes().get(s.getN()-1).getStartedProcessing());
    		
 	    	//reset
		
			s = null;
			count =0;
		}
    
    }
	
    
    /**
     * This method will return an array vnf[f][delta] specifying if a vnf can be used at delta(0) or not (1)
     * by considering startSchedule and solution
     * @param solution
     * @return vnf[f][delta]
     */
    /*public int[][] isVNFAvailable (SchedulingSolution solution)
    {
    	int [][] vnfuse = new int [this.F][this.DELTA];
    	
    	//set Vnfs as used (0) before startTimeslot so none of the services can be scheduled before startTimeslot and not used after start Time slot 
    	 //loop over VNF
 		for (int f=0; f<this.F; f++)
	    {
	    	for (int delta=0; delta<this.DELTA ; delta++)
	    	{
	    		vnfuse[f][delta]= delta<this.startSchedule ? 0: 1;
	    	}
	    }
 		
 		if (solution == null)
 		{
 			return vnfuse;
 		}
 		
 		//set vnf as used by checking the previous solution
    	  //loop over services
 	    for(int i =0; i<solution.y.length; i++)
 	    {
 	    	 //loop over middleboxes
    		for (int n=0; n<solution.y[i].length; n++)
	    	{
	    		 //loop over VNF
 	    		for (int f=0; f<solution.y[i][n].length; f++)
 		    	{
 	    			 //loop over time slots
 	    			for (int delta=0; delta<solution.y[i][f].length; delta++)
 			    	{	 
 	    				//already handled before, don't put delta= startSchedule to prevent an exception if start>solution.y[i][f].length
 	    				if (delta< this.startSchedule)
 	    				{
 	    					continue;
 	    				}
 	    				//prevent the vnf to be used if it is used by at least one service at delta
 	    				if ( solution.y[i][n][f][delta]==1)
 	    				{
 	    					vnfuse[f][delta] =0;
 	    				}
 			    	}
 		    	}
	    	}
    	}	
 	    
 	  return vnfuse;
    }
    
    
    public int[][][] getAvailableBw (SchedulingSolution solution)
    {
    	int [][][]cij = new int [this.ks][this.ks][this.DELTA];
    	
    	//initialize it to the link capacity
    	
 	    for(int i =0; i<this.ks; i++)
 	    {
 	    	
    		for (int j=0; j<this.ks; j++)
	    	{
	    		 //loop over delta
 	    		for (int delta=0; delta<this.DELTA; delta++)
 		    	{
 	    			if (G[i][j]==1 ) 
   		    		{
 	    				if (delta<this.startSchedule)
 	    				{
 	    					cij[i][j][delta] = 0;
 	    				}
 	    				else
 	    				{
 	    					cij[i][j][delta] = this.cij[i][j];
 	    				}
   		    		} 	    			
 		    	}
	    	}
 	    }
    	
 	    
 	    if (solution == null)
 	    {
 	    	return cij;
 	    }
 	    
 	    //handle optimal solution
 	    
    	return cij;
    }*/
    
    
    /**
     * This method is used for online batch scheduling mainly to set the variables of the previous batches to a defined value
     * 
     * set the variables of the optimal solution (of previous batches)
     * @param solution containing vaules for the previous batches
     * @throws IloException
     */
	
	public void setDecisionVariables(SchedulingSolution solution) throws IloException
	{
		 
	 	    //loop over services
	 	    for(int i =0; i<solution.y.length; i++)
	 	    {
	 	    	 //loop over middleboxes
 	    		for (int n=0; n<solution.y[i].length; n++)
 		    	{
 	    		 //loop over VNF
	 	    		for (int f=0; f<solution.y[i][n].length; f++)
	 		    	{
	 	    			 //loop over time slots
	 	    			for (int delta=0; delta<solution.y[i][n][f].length; delta++)
	 			    	{	 	  
	 	    					cplex.addEq(y[i][n][f][delta], solution.y[i][n][f][delta], "coSNFDelta["+i+"]["+n+"]["+f+"]["+delta+"]");	 
	 	    					
	 			    	}
	 		    	}
 		    	}
 	    	}	    			
 	   	    
	 	    	    
	 	    //loop over services s
	 	    for(int i =0; i<solution.theta.length; i++)
	 	    {
	 	    	//loop over timeslots
	 			for (int delta=0; delta<solution.theta[i].length; delta++)
	 	    	{
	 	    		//loop over edges
	 	    		for (int e=0; e<solution.theta[i][delta].length; e++)
	 		    	{ 
	 	    			cplex.addEq(theta[i][delta][e], solution.theta[i][delta][e],"coSDeltaE["+i+"]["+delta+"]["+e+"]");	 	    			
	 	    			
	 		    	}
	 	    	}
	 	    }
	 	    
	 	    //loop over services s
	 	    for(int i =0; i<solution.thetaHat.length; i++)
	 	    {
	 	    	//loop over timeslots
	 			for (int delta=0; delta<solution.thetaHat[i].length; delta++)
	 	    	{
	 	    		//loop over edges
	 	    		for (int e=0; e<solution.thetaHat[i][delta].length; e++)
	 		    	{
	 	    			cplex.addEq(thetaHat[i][delta][e], solution.thetaHat[i][delta][e],"coHatSDeltaE["+i+"]["+delta+"]["+e+"]");
	 	    			
	 		    	}
	 	    	}
	 	    }
	}
    
	
	/**
	 * This method will build the optimal solution
	 * @return
	 * @throws IloException 
	 * @throws UnknownObjectException 
	 */
	public SchedulingSolution buildOptimalSolution() throws UnknownObjectException, IloException
	{
		SchedulingSolution optimalSolution = null;
		//set the size of the solution to the max schedule length (objective) so for the next batch we either keep it to objective or to estimated delta
		//of new batch. This timeline is important to set to objective and not DELTA, because DELTA of future batch may be less than DELTA or objective of current batch
		// check OnlineScheduline.scheduleServicesILP(Batch)
		int timeline = (int)Math.ceil(this.objectiveValue)+1;
		
		int [][][][]	y = new int [S][this.maxMiddlebox][F][timeline];	
			
		int [][][]	theta = new int [S][timeline][E.length];
		int	[][][] thetaHat = new int [S][timeline][E.length];
		
		
 	    //loop over services
 	    for(int i =0; i<S; i++)
 	    {
 	    	for(int n=0;n<this.maxMiddlebox; n++)
 	    	{
 	    		//loop over VNF
 	    	
	    		for (int f=0; f<F; f++)
		    	{
	    			 //loop over time slots
	    			for (int delta=0; delta<timeline; delta++)
			    	{
	    				try
	    				{
	    					y[i][n][f][delta] = cplex.getValue(this.y[i][n][f][delta])>0.9? 1:0;
	    					
	    				}catch (Exception e){}
	    				
			    	}
		    	}
 	    	}
 	    		    			
 	    }
	 	  	 	    	    
 	    //loop over services s
 	    for(int i =0; i<S; i++)
 	    {
 	    	//loop over timeslots
 			for (int delta=0; delta<timeline; delta++)
 	    	{
 	    		//loop over edges
 	    		for (int e=0; e<E.length; e++)
 		    	{
 	    			try
    				{
 	    				theta[i][delta][e] = cplex.getValue(this.theta[i][delta][e])>0.9?1:0;
    				}catch (Exception er){}
 	    			
 		    	}
 	    	}
 	    }
 	    
 	    //loop over services s
 	    for(int i =0; i<S; i++)
 	    {
 	    	//loop over timeslots
 			for (int delta=0; delta<timeline; delta++)
 	    	{
 	    		//loop over edges
 	    		for (int e=0; e<E.length; e++)
 		    	{
 	    			try{
 	    				thetaHat[i][delta][e] = cplex.getValue(this.thetaHat[i][delta][e])>0.9?1:0;
 	    			}
 	    			catch (Exception ed){}
 	    			
 		    	}
 	    	}
 	    }
	 	    	 
		optimalSolution = new SchedulingSolution((int)Math.ceil(this.cplex.getObjValue()), y, theta, thetaHat);//ceil to prevent error
		
		return optimalSolution;
	}
    
	
	/**
	 * This method will take mapped routed and scheduled services and build the optimal solution based on them
	 * @param services
	 * @param bnfNb in network
	 * @return
	 */
    public static SchedulingSolution buildOptimalSolution(ArrayList<Service>services, int vnfNb)
    {
    	int totalVirtualLinks =0;
    	int totalMiddleboxes = 0;
    	int totalServices = services.size();
    	int timeline =0;
    	int mcount =0, lcount=0;
    	Middlebox m;
    	VLink vl;
    	Service s;
    	
    	for (int i=0; i<services.size(); i++)
		{
			s= services.get(i);
			
	    	totalVirtualLinks+=s.getVirtualLinks().size();
	    	
	    	if (s.getN()>totalMiddleboxes)
	    	{
	    		totalMiddleboxes = s.getN();
	    	}
	    
	    	
	    	if (s.getCompletionTime()>timeline)
	    	{
	    		timeline = s.getCompletionTime()+1;
	    	}
		}
    	
 
		int [][][][]	y = new int [totalServices][totalMiddleboxes][vnfNb][timeline];	

		int [][][]	theta = new int [totalServices][timeline][totalVirtualLinks];
		int	[][][] thetaHat = new int [totalServices][timeline][totalVirtualLinks];
		
		
    	for (int i=0; i<services.size(); i++)
    	{
    		mcount =0;
    		s= services.get(i);
    		for (int j=0; j<s.getMiddleboxes().size(); j++)
    		{
    			m = s.getMiddleboxes().get(j);
    			
    			//y should only hold started processing
    			y[i][mcount][m.getMappedToVNF().getId()][m.getStartedProcessing()] = 1;//handling those with processing time 0
    		
    			
    			mcount++;
    		}
    		
    		
    		for (int j=0; j<s.getVirtualLinks().size(); j++)
    		{
    			vl = s.getVirtualLinks().get(j) ;
    			
    			if (vl.getRoutedThrough().size()!=0)
    			{
    				theta[i][vl.getStartedTransmitting()][lcount] = 1;
        			
        			for (int k =0; k<vl.getIsTransmitting().length; k++)
        			{
        				thetaHat[i][vl.getIsTransmitting()[k]][lcount] = 1;    				
        			}
    			}
    			
    			
    			lcount++;
    		}
    	}
    	
    	SchedulingSolution optimalSolution = new SchedulingSolution(timeline-1, y, theta, thetaHat);
		//System.out.println(optimalSolution);
		return optimalSolution;
    }

    
    /**
     * This method will run the ILP model and update the services to set admitted and schedule
     * Export the model to a file called SchedulinRoutingDeadline.lp
     * Report the results (values of the variables) to a file called SchedulinRoutingDeadlineResult.lp
     * 
     * @param online to specify if we are running for online batch in this case we do not release resources if not admiited
     * @param resultsFile path to the file where to dump results
     * @throws IloException 
     */
    public void runILPModel(String resultsFile) throws IloException
    {

    	try {
			this.cplex.exportModel("SchedulingDeadline.lp");
			if (cplex.solve()) {
				this.objectiveValue = this.cplex.getObjValue();
				System.out.println("solved "+this.cplex.getObjValue());	
				//System.out.println(this.cplex.getStatus());
				
				//print results (values of the decision variables)
				 this.updateServices();
				this.reportResults(resultsFile);					
			}
			else
			{
				this.objectiveValue = -1;
				//System.out.println(this.cplex.getStatus());
				System.out.println(" NOT solved ");	
			}
		//	this.cplex.end();	
			
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
		
			st+="\t\t"+Output.printQuadrupleArray(y,"y[s][n][F][DELTA]", "\t\ty",this.cplex);
				
			st+="\t\t"+Output.printTripleArray(theta,"theta[s][DELTA][E]","\t\ttheta", this.cplex);
			st+="\t\t"+Output.printTripleArray(thetaHat,"thetaHat[s][DELTA][E]","\t\tthetaHat", this.cplex);
					
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
    
    public String toString()
    {
    	String st="";
    	st+=" ILP MODEL \n";
    	st+= "\t Parameters:\n";	    	
		st+= "\t\t"+Output.printDoubleMatrice(G,"GRAPH");
		st+= "\t\t"+Output.printDoubleMatrice(cij,"PHYSICAL LINK CAPACITY");
		st+= "\t\t"+Output.printTable(tf, "VNF TYPES");
		st+= "\t\t"+Output.printDoubleMatrice(xfk,"VNF MAPPING TO PHYSICAL SERVERS");
		st+= Output.printTable(N, "NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE");
		st+= Output.printDoubleMatrice(msn,"SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)");
		st+= Output.printDoubleMatrice(psn,"PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE");
		st+= Output.printTable(bs, "BANDWIDTH REQUIRED BY EACH SERVICE");
		st+= Output.printTable(ws, "TRAFFIC SIZE OF EACH SERVICE");
		st+= Output.printTable(us, "DEADLINE TIME OF EACH SERVICE");
		st+= Output.printTable(E, "VIRTUAL LINKS ASSIGNMENT FOR SERVICES");
		st+= Output.printTable(oe, "ORIGIN OF EACH VIRTUAL LINK");
		st+= Output.printTable(de, "DESTINATION OF EACH VIRTUAL LINK");
		st+="\t\t"+Output.printTripleArray(l,"l[ks][ks][E]","\t\tl");
	    st+="\t\t"+Output.printTripleArray(r,"r[s][n][f]","\t\tr"); 
		st+="\t\t"+Output.printTripleArray(h,"h[s][n][ks]","\t\th");
	
		st+="\t\t"+this.physicalNetwork.toString()+"\n";
		
		st+="\t\t Services\n";
		for (int i=0; i<this.services.size(); i++)
		{
			st+= "\t\t"+this.services.get(i).toString();
		}
		
		return st;
		
    }

   public static void main (String []args) throws IOException, IloException
   {
	   	 int vnfTypesNb= 7;
		 Network  network = new Network(8,vnfTypesNb,4,5,200,200,1,0);
		 ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();			 
		 System.out.println(network);
			 
		 int[]	tf = physicalNetwork.get(2)[0];		
		ServicesDriver driver = new ServicesDriver (3,  100, 3, 4, 50, 100, 4 , 1, 100, 200,tf);
		//ArrayList<int[][]>  services = driver.generateServicesForModel();
		//ArrayList<Service> servicesObjects = driver.convertGeneratedServices(services);
		ArrayList<Service> servicesObjects = driver.generateServices(50, 100);
		

   }
}
