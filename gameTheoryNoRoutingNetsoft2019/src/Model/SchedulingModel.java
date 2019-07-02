/**
 * This model take a set of services mapped and routed and perform their scheduling while maximizing their admission rate
 * The model support online scheduling by considering not starting  the service schedule before it s arrival time
 * 
 * The model should have services having many middleboxes of the same type in the chain In this case it will reject the service because of constraint const3
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
	int earliestArrivalTime;//this prevents declaring any variables with Delta before the earliestArrivalTime. Help to handle online batch. will be 0 in case of offline mode
/***************************************************************************************************************************
 * ****************************************PARAMETERS***********************************************************************
 * *************************************************************************************************************************/

	//the random connected network. This array will specify if a link exists between 2 nodes
	int[][] G;		
	
	//specifies capacity of physical links interconnecting the servers 
	int [][] cij;		
	
	//specifies capacity of physical links interconnecting the servers at each timeslot mainly used when considering that services exists in the network
	int [][][]cijdelta; 
	
	//specifies if a vnf is used by at least one service at each timelsot (0 if vnf used and 1 if available)mainly used when considering that services exists in the network
	int [][]vnfdelta;
	
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
	//a specifies that if a service is admitted or not
	public IloIntVar[] a;
	
	//specifies that the traffic of service s started processing at time slot delta  on middlebox n scheduled/mapped to VNF f
	public IloIntVar[][][] y;

	
	//designates that a middlebox o(e) of service s begins the transmission of traffic to its successor middlebox d(e)  at time slot \delta  on virtual link e  (1) (or not, 0). 
	public IloIntVar[][][]theta;
	
	// indicates that the virtual link e is used for traffic transmission between middleboxes o(e), d(e) of service s  at time slot \delta (1)   (or not, 0).
	public IloIntVar[][][]thetaHat;
	
	
    public SchedulingModel(int[][]G, int [][]xfk, int[][]cij, int[]tf, int[]N, int []E, int []oe, int []de, int []bs, int []ws, int [] us, int[][] psn,int[][]msn, int delta, int maxMiddlebox, int [][][] l, int [][][]h, int[][][]r, int adjustmentValue) throws IloException
    {
    	this.objectiveValue = -1; //set to -1 by default
    	this.cplex = new IloCplex();
    	services = new ArrayList<Service>();
    	this.physicalNetwork = new Network();
    	this.earliestArrivalTime = 0;
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
    	
       	//populate cijdelta to cij considering that non of the links are used
    	this.cijdelta = new int [this.cij.length][this.cij[0].length][this.DELTA];
    	for (int i=0; i<this.cijdelta.length; i++)
    	{
    		for (int j=0; j<this.cijdelta[i].length; j++)
    		{
    			for (int k=0; k<this.cijdelta[i][j].length; k++)
    			{
    				this.cijdelta [i][j][k] = this.cij[i][j];
    			}
    		}
    	}
    	
    	//set that non of the vnfs is used at any timeslot
    	this.vnfdelta = new int [this.F][this.DELTA];
    	for (int i=0; i<this.vnfdelta.length; i++)
    	{
    		for(int j=0; j<this.vnfdelta[i].length; j++)
    		{
    			this.vnfdelta[i][j] = 1;
    		}
    	}
    	
    	//setting the decision variables			
    	a = new IloIntVar[S];    			
		y = new IloIntVar[S][F][DELTA];
		
		theta = new IloIntVar[S][DELTA][E.length];
		thetaHat = new IloIntVar[S][DELTA][E.length];

		
    }
    
    /**
     * 
     * @param network
     * @param services services that are mapped and routed
     * @param delta
     * @throws IloException
     */
    public SchedulingModel(Network network, ArrayList<Service> services, int delta) throws IloException
	{ 	
    	    	
		int totalVirtualLinks=0;
		int count =0; 
		int countinit =0;
		VLink vl = null;
		Link pl = null;
		PhysicalMachine pm ;
		this.DELTA = delta;
		this.objectiveValue = -1; //set to -1 by default
		this.maxMiddlebox =0;
	   	this.cplex = new IloCplex();
		this.services = services;
		this.earliestArrivalTime = this.getEarliestArrivalTime();
    	
		this.physicalNetwork = network;
	   	ArrayList<int[][]>networkArray = this.physicalNetwork.getPhysicalNetworkArray();
    	
    	//setting the parameters
    	this.G = networkArray.get(0);
    	this.cij = networkArray.get(1);		
       	
    	//populate cijdelta to cij considering that non of the links are used
    	this.cijdelta = new int [this.cij.length][this.cij[0].length][this.DELTA];
    	for (int i=0; i<this.cijdelta.length; i++)
    	{
    		for (int j=0; j<this.cijdelta[i].length; j++)
    		{
    			for (int k=0; k<this.cijdelta[i][j].length; k++)
    			{
    				this.cijdelta [i][j][k] = this.cij[i][j];
    			}
    		}
    	}
    	
    	//set that non of the vnfs is used at any timeslot
    	this.vnfdelta = new int [network.getVNFNb()][this.DELTA];
    	for (int i=0; i<this.vnfdelta.length; i++)
    	{
    		for(int j=0; j<this.vnfdelta[i].length; j++)
    		{
    			this.vnfdelta[i][j] = 1;
    		}
    	}
    	
    	
		//this tf is different from the one in the network array. The networkarray contains the list of type
    	//here, tf should have the type of each vnf. if we have 4 vnf and 3 types; tf = [0,1,2,3,3] by network array tf [0,1,2,3]
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
	    		this.E[j] =  i;//s.getId(); do not use the service id to handle online batch scheduling where service ids will not start from 0 and we end up with problem with the link constrains not being added
	    			    		
	    		//set l
	    		for (int k=0; k<vl.getRoutedThrough().size(); k++ )
	    		{
	    			//get physical links
	    			pl = vl.getRoutedThrough().get(k);    			
	    			
	    			this.l[pl.getSource().getId()][pl.getDestination().getId()][j] = 1 ;
	    			pl = null;
	    		}
	    		
	    		
	    		countinit++;
	    	}
	    	countinit =0;
	    	count+=s.getVirtualLinks().size();
		}
    	
    	

   	    	this.toString();
    	//setting the decision variables			
   	   				
   	    a = new IloIntVar[S]; 
		y = new IloIntVar[S][F][DELTA];
	
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
    	 for(int i =0; i<S; i++)
 	    {
 	    	a[i] = cplex.intVar(0, 1,"a "+i);			
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
 	    			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
 			    	{
 	    				y[i][f][delta] = cplex.intVar(0, 1,"y "+i+f+delta);	
 	    				 	    			
 	    				
 	    				//start scheduling at arrival time
 	    				if (this.services.size()!=0 && delta<this.services.get(i).getArrivalTime())
 	    				{
 	    					cplex.addEq(y[i][f][delta], 0, "Const0_ysfdelta["+i+"]["+f+"]["+delta+"]");
 	    				}
 			    	}
 		    	}
 	    	}	    			
 	    }
 	    

 	    
 	    	    
 	    //loop over services s
 	    for(int i =0; i<S; i++)
 	    {
 	    	//loop over timeslots
 			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
 	    	{
 	    		//loop over edges
 	    		for (int e=0; e<E.length; e++)
 		    	{
 	    			theta[i][delta][e] = cplex.intVar(0, 1,"theta "+i+delta+e);
 	    			
 	    			//If the virtual link is not for the service than make sure to set it to 0 (no transmission is possible on it)
 	    			if (E[e]!=i)
 	    			{
 	    				cplex.addEq(theta[i][delta][e], 0,"Const0_thetaSdeltaE["+i+"]["+delta+"]["+e+"]");
 	    			}//start scheduling at arrival time
 	    			else  if (this.services.size()!=0 && delta<this.services.get(i).getArrivalTime())
 	 				{
 	    				cplex.addEq(theta[i][delta][e], 0,"Const0_thetaSdeltaE["+i+"]["+delta+"]["+e+"]");
 	 				}
 		    	}
 	    	}
 	    }
 	    
 	    //loop over services s
 	    for(int i =0; i<S; i++)
 	    {
 	    	//loop over timeslots
 			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
 	    	{
 	    		//loop over edges
 	    		for (int e=0; e<E.length; e++)
 		    	{
 	    			thetaHat[i][delta][e] = cplex.intVar(0, 1,"thetaHat "+i+delta+e);
 	    			//If the virtual link is not for the service than make sure to set it to 0 (no transmission is possible on it)
 	    			if (E[e]!=i)
 	    			{
 	    				cplex.addEq(thetaHat[i][delta][e], 0,"Const0_thetaHatSdeltaE["+i+"]["+delta+"]["+e+"]");
 	    			}//start scheduling at arrival time
 	    			else if (this.services.size()!=0 && delta<this.services.get(i).getArrivalTime())
 	 				{
 	    				cplex.addEq(thetaHat[i][delta][e], 0, "Const0_thetaHatSdeltaE["+i+"]["+delta+"]["+e+"]");
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
	    IloNumExpr objective = cplex.numExpr();
	    
	    //loop over services
	    for(int i =0; i<S; i++)
	    {
			objective = cplex.sum(objective, a[i]);				
	    }
		
	    //set objective function to maximize the number of admitted services
		cplex.addMaximize(objective);
		
		
		  /***************************************************************************************************************************
		   * *************************************************Constraints*****************************************************
		   * *************************************************************************************************************************/
	   
		    
	    /**
		 * C1 :prevents the transmission of traffic of a service $s$ between two consecutive middleboxes $n$ and ($n+1$), if its processing on $n$ has not been completed
		 * theta [s][delta'][e] = 1-sum over f y[s][f][delta] r[s][o(e)][f] for all e, for all s, for all delta, delta'; delta'<delta+p[s][o(e)]
		 */
	    //loop over services s
	   for(int i =0; i<S; i++)
	    {
	    	//loop over edges
    		for (int e=0; e<E.length; e++)
	    	{
    			//don't add constraints for edges that are not for the service
    			if (E[e] != i)
    			{
    				continue;
    			}
    			
    			//loop over timeslots
    			for (int deltaPrime=this.earliestArrivalTime; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
    		    			transmissionStartConstraint = cplex.sum(transmissionStartConstraint, cplex.prod(r[i][oe[e]][f], y[i][f][delta]));
    			    	}
    		    		
    		    		cplex.addLe( theta[i][deltaPrime][e],cplex.sum(1, cplex.prod(-1,transmissionStartConstraint)), "Const1_seDeltaprimeDelta ["+i+"]["+e+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}
    			
	    	}
	    	
	    }
	    
	    /**
		 * C2 :ensures that traffic of a service $s$ can not be processed by a middlebox $(n+1)$ if it was not transmitted to it by its precedent middlebox $n$
		 * sum over f y[s][f][delta']r[s][d(e)][f] <= 1-theta[s][delta][e] for all e, for all s, for all delta, delta'; delta'<delta+ws[s]/bs[s]
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
    			for (int deltaPrime=this.earliestArrivalTime; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
    		    			transmissionFinishedConstraint = cplex.sum(transmissionFinishedConstraint, cplex.prod(y[i][f][deltaPrime], r[i][de[e]][f]));
    			    	}
    		    		
    		    		cplex.addLe(transmissionFinishedConstraint, cplex.sum(1, cplex.prod(-1,theta[i][delta][e])),"Const2_seDeltaprimeDelta ["+i+"]["+e+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}
    			
	    	}
	    	
	    }
	    
	    /**
		 * C3 :prevents a middlebox $(n+1)$ to start processing traffic of service $s$ before its precedent middlebox $n$ finishes its execution
		 * sum over f y[s][f][delta']r[s][n+1][f] <= 1-sum over f y[s][f][delta]r[s][n][f] for all s, for all delta, delta'; delta'<delta+psn[s][n]
		 */
	    //loop over services
	 for(int i =0; i<S; i++)
	    {
	    	 //loop over middleboxes of the service N[i] specifies how many middlebox service i has.
	    	for (int j=0; j<N[i]-1; j++)
	    	{
	    		
	    		//loop over timeslots
    			for (int deltaPrime=this.earliestArrivalTime; deltaPrime<DELTA; deltaPrime++ )
    	    	{
    	    		//loop over timeslots
    	    		for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
    		    		
    		    			processStartOfnPlusConstraint = cplex.sum (processStartOfnPlusConstraint, cplex.prod(y[i][f][deltaPrime], r[i][j+1][f]));
    		    			processStartOfnConstraint = cplex.sum (processStartOfnConstraint, cplex.prod(y[i][f][delta], r[i][j][f]));
    			    	}
    		    		
    		    		cplex.addLe (processStartOfnPlusConstraint, cplex.sum(1,cplex.prod(-1, processStartOfnConstraint)),"Const3_snDeltaprimeDelta ["+i+"]["+j+"]["+deltaPrime+"]["+delta+"]");
    		    	}
    	    	}	
	    			
	    	}	    			
	    }

		/**
		 * C11 :ensures that a service start processing on a VNF instance $f$ if the service $s$ is admitted to the network
		 * sum over f sum over delta y[s][f][delta]r[s][n][f] = a[s] for all n for all s
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
	    			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
			    	{
	    				middleboxTypeConstraint = cplex.sum(middleboxTypeConstraint, cplex.prod(y[i][f][delta], r[i][j][f]));	
	    				
			    	}
	    			
		    	}
	    		cplex.addEq (middleboxTypeConstraint, a[i], "Const11_sn ["+i+"]");
	    		
	    	}	    			
	    }

	    /**
	     * C4 makes sure that a VNF f can start processing one service at a time exception for ingress and egress
	     * sum over s y[s][f][delta] <=1 for all f for all delta
	     */
		for (int f=0; f<F; f++)
	    {
			//allow ingress and egress node to process at the same time
			if (tf[f]==0 ||tf[f] ==1)
			{
				continue;
			}
			//loop over timeslots
	    	for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
		    {
	    		IloNumExpr processSameVNFConstraint = cplex.numExpr();
	    		
	    		for(int s =0; s<S; s++)
	      	    {
	    			processSameVNFConstraint = cplex.sum (processSameVNFConstraint, y[s][f][delta]);
	    			
	      	    }
	    		
	    		cplex.addLe(processSameVNFConstraint ,this.vnfdelta[f][delta], "Const4_DeltaF ["+delta+"]["+f+"]" );
		    }
	    }
	
	    /**
	     * C5 makes sure that a VNF f can start processing one service at a time during all processing period (exception for ingress and egress)
	     * sum over s' y[s'][f][deltaPrime] <=1- y[s][f][delta]r[s][n][f] for all f for all delta<=deltaprime<delta+pns for all s for all n
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
			    	for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
				    			processSameVNFConstraint = cplex.sum (processSameVNFConstraint, y[sp][f][deltaPrime]);
				    			
				      	    }
				    		
				    		cplex.addLe(processSameVNFConstraint ,cplex.diff(1,cplex.prod(y[s][f][delta], r[s][i][f])),"Const5_SNFDelta ["+s+"]["+i+"]["+f+"]["+delta+"]" );
					    }
			    		
				    }
			    }
	    	}
	    }
	   
  	    /**
  		 * C6 :ensures that a service should be able to be processed before its deadline $u_s$ to be admitted in the network
  		 * sum over f sum over delta y[s][f][delta]r[s][|Ns|][f](delta+psn[s][|Ns|]<=us[s]  for all s, 
  		 */
	   //loop over services
	   for(int i =0; i<S; i++)
	    {
	    	IloNumExpr processedBeforeDeadlineConstraint = cplex.numExpr();
	    	
	    	//loop over VNF
	    	for (int f=0; f<F; f++)
		    {
    			 //loop over time slots
    			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
		    	{
    				processedBeforeDeadlineConstraint = cplex.sum(processedBeforeDeadlineConstraint, cplex.prod(cplex.prod(y[i][f][delta],r[i][N[i]-1][f]), delta+psn[i][N[i]-1])) ;	
		    	}
	    	}
	    	
	    	cplex.addLe(processedBeforeDeadlineConstraint, us[i],"Const6_S ["+i+"]");
	    }
	    
	   
	    
	    /**
  		 * C7 :prevents the start of the transmission of traffic of a service $s$ between two consecutive middleboxes $o(e)$ and $d(e)$ if they are mapped to VNFs hosted on the same physical server or if the service is not admitted
  		 * sum over delta theta[s][delta[e] = (1-sum over k h[o(e)][s][k])a[s] for all e for all s
  	
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
				for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
		    	{		    		
					preventTransmissionConstraint = cplex.sum(preventTransmissionConstraint, theta[i][delta][e]);			    	
		    	}
				
				for (int k=0; k<ks; k++)
		    	{
					physicalServerMappingConstraint = cplex.sum(physicalServerMappingConstraint, h[i][oe[e]][k]);
		    	}
				cplex.addEq(preventTransmissionConstraint, cplex.prod(cplex.diff(1, physicalServerMappingConstraint ), a[i]),"Const7_se ["+i+"]["+e+"]" );
		
				
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
				for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
				for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
			   			     
			   			  cplex.addLe(bandwidthConstraint,this.cijdelta[i][j][delta],"Const10_DeltaIJ ["+delta+"]["+i+"]["+j+"]" );
			   		
	   		    		}
	   		    	
	   		    	}
	   		    }
	    	} 
	  
	}
    
    /**
     * this method will update the schedule of the services and set them as admitted or not based on the model result
     * @return admitted services
     * @throws IloException 
     * @throws UnknownObjectException 
     * @param online set to true for online batch scheduling so we do not release resources if not admitted but we set the service to admitted = false
     */
    public ArrayList<Service> updateServices(boolean online) throws UnknownObjectException, IloException
    {
    	ArrayList<Service> admittedServices = new ArrayList<Service>();
    	boolean admitted = false;
		Service s = null;
		Middlebox m;
		int count =0;
		boolean firstLink = false;
		int isTransmitting = 0;
		
		//loop over services 
    	for(int i=0; i<this.a.length;i++)
		{
			s = services.get(i);
			
			admitted = this.cplex.getValue(this.a[i]) == 1 ? true:false;
			s.setIsAdmitted(admitted);
			
			if (!s.getIsAdmitted())
			{
				//if online batch we need to keep the services mapped and routed even if not admitted to prevent null pointer exception when initializing model
				if(!online)
				{
					s.releaseResources();
				}
				
				s.setIsAdmitted(false);
				continue;
			}
			
			//if service is admitted set its schedule
		
			admittedServices.add(s);
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
 	    			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
 			    	{
 	    				
 	    				//force y = 0 for functions to which the service is not mapped (Prevent object is unknown to IloCplex)
 	    				if(r[i][j][f] ==0 || this.cplex.getValue(this.y[i][f][delta] ) ==0)
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
    			if (E[e]!=i)//s.getId() this is to handle online batch where service ids may not start from 0 (if this is changed, we should change in SchedulingModel()
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
	 			for (int delta=this.earliestArrivalTime; delta<DELTA; delta++)
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
 	    	
    		
 	    	//reset
			admitted = false;
			s = null;
			count =0;
		}
    	    	
 	    
    	return admittedServices;
    }
	
    
    /**
     * This method is used whenever we consider that some services are hosted in the network and we need to updat ethe resources to consdider them
     * 
     * This method will update this.vnfdelta and this.cijdelta to consider the used resources. These arrays should be initialized to 1 and cij respectively
     * 
     * @param hostedServices services hosted in the network (mapped, routed, scheduled)
     */
    public void updateResources (ArrayList<Service>hostedServices)
    {
    	Service s;
    	Middlebox m;
    	VLink v ;
    	Link l;
    	
    	for(int i=0; i<hostedServices.size(); i++)
    	{
    		s= hostedServices.get(i);
    		
    		if (!s.getIsAdmitted())
    		{
    			continue;
    		}
    		
    		//update the vnfdelta to set the vnfs ofthe hosted services as used and not available at delta
    		for (int j=0; j<s.getMiddleboxes().size();j++)
    		{
    			m = s.getMiddleboxes().get(j);
    			
    			if (m.getProcessingTime() ==0)
    			{
    				this.vnfdelta [m.getMappedToVNF().getId()][m.getStartedProcessing()] = 0;
    			}
    			else
    			{
    				for(int k=0; k<m.getIsProcessing().length; k++)
    				{
    					this.vnfdelta[m.getMappedToVNF().getId()][m.getIsProcessing()[k]]=0;
    				}
    			}
    		}
    		
    		//update the link usage 
    		for(int j=0; j<s.getVirtualLinks().size(); j++)
    		{
    			v= s.getVirtualLinks().get(j);
    			for (int k=0;k<v.getRoutedThrough().size(); k++)
    			{
    				l= v.getRoutedThrough().get(k);
    				for (int x=0; x<v.getIsTransmitting().length; x++ )
    				{
    					this.cijdelta [l.getSource().getId()][l.getDestination().getId()][v.getIsTransmitting()[x]]-=s.getBandwdith();
    					
    				}
    			}
    		}
    		
    	}
    	
	
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
    public ArrayList<Service> runILPModel(String resultsFile, boolean online) throws IloException
    {
    	 ArrayList<Service> admittedservices = new ArrayList<Service>();
    	try {
			this.cplex.exportModel("SchedulingDeadline.lp");
			if (cplex.solve()) {
				this.objectiveValue = this.cplex.getObjValue();
				System.out.println("solved "+this.cplex.getObjValue());	
				//System.out.println(this.cplex.getStatus());
				
				//print results (values of the decision variables)
				admittedservices  = this.updateServices(online);
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
    	
    	return admittedservices;
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
			st+="\t\t"+Output.printArray(a,"a[s]", "\t\ta",this.cplex);
			st+="\t\t"+Output.printTripleArray(y,"y[s][F][DELTA]", "\t\ty",this.cplex);
				
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

    public int getEarliestArrivalTime ()
    {
    	int earliestArrival = Integer.MAX_VALUE;
    	for (int i=0;i<this.services.size(); i++)
    	{
    		if ((int)this.services.get(i).getArrivalTime()<earliestArrival)
    		{
    			earliestArrival = (int)this.services.get(i).getArrivalTime();
    		}
    	}
    	
    	return earliestArrival;
    }
   
}
