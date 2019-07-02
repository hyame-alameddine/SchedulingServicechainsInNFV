package Model;

import java.io.IOException;
import java.util.ArrayList;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;
import HelperClasses.FileManipulation;
import HelperClasses.Output;
import HelperClasses.ServicesDriver;
import NFV.Service;
import Network.Link;
import Network.Network;
import Network.PhysicalMachine;

public class ILPModelModified {
	
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
					
		//NOT _NEEDED deadline time of service s (in time slots).This is formed as double array to be able to return it 
		int [] us;				
		
		//list of virtual links for all services  (E[1] = 2 mean that edge 1 is for service 2)
		int [] E;
		
		//this holds the middlebox which are origin of edge e (oe [1] = 2 means that edge 1 has middlebox 2 as origin)
		int[]oe;
		
		//this holds the middlebox which are destination of edge e (de [1] = 2 means that edge 1 has middlebox 2 as destination)
		int []de;
		
	
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
		
		//specifies that the traffic of service s is processing at time slot delta  on middlebox n scheduled/mapped to VNF f
		public IloIntVar[][][][] z;
		
		//indicates that middleboxes n, (n+1)  of service s are hosted on the same node k(1) (or not, 0).\\
		public IloIntVar[][][] h;
		
		//specifies that the middlebox n of service s is mapped to a VNF instance hosted on physical server k (1) (or not, 0).
		public IloIntVar[][][]q;
				
		//designates that a middlebox o(e) of service s begins the transmission of traffic to its successor middlebox d(e)  at time slot \delta  on virtual link e  (1) (or not, 0). 
		public IloIntVar[][][]theta;
		
		// indicates that the virtual link e is used for traffic transmission between middleboxes o(e), d(e) of service s  at time slot \delta (1)   (or not, 0).
		public IloIntVar[][][]thetaHat;
		
		// specifies that the virtual link e  of service s  is routed through physical link (ij)  (1) (or not, (0)).
		public IloIntVar[][][]l ;
		
		//Needed for linearization
		public IloIntVar[][][][][]g;
	    
	    /**
	     * This constructor is used when we have a manual input
	     * @param G
	     * @param xfk
	     * @param cij
	     * @param tf
	     * @param N
	     * @param E
	     * @param oe
	     * @param de
	     * @param bs
	     * @param ws
	     * @param us
	     * @param psn
	     * @param msn
	     * @param DELTA
	     * @throws IloException
	     */
	    public ILPModelModified(int[][]G, int [][]xfk, int[][]cij, int[]tf, int[]N, int []E, int []oe, int []de, int []bs, int []ws, int [] us, int[][] psn,int[][]msn, int delta, int maxMiddlebox) throws IloException
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
	 	  	//setting the decision variables			
		
			y = new IloIntVar[S][maxMiddlebox][F][DELTA];
			z = new IloIntVar[S][maxMiddlebox][F][DELTA];
			h = new IloIntVar[S][maxMiddlebox][ks];
			q = new IloIntVar[S][maxMiddlebox][ks];
			theta = new IloIntVar[S][DELTA][E.length];
			thetaHat = new IloIntVar[S][DELTA][E.length];
			l = new IloIntVar[ks][ks][E.length];
			g = new IloIntVar[ks][ks][S][DELTA][E.length];
			
	    }
	    
		public ILPModelModified(Network network, ArrayList<Service> services, int delta) throws IloException
		{ 	
			int totalVirtualLinks=0;
			int count =0; 
			int countinit =0;
			
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
	    
	    	for (int i=0; i<this.services.size(); i++)
			{
	    		s= this.services.get(i);
		    	for (int j=0; j<this.maxMiddlebox; j++)
		    	{
		    		this.psn [i][j] = (j<s.getN())? s.getMiddleboxes().get(j).getProcessingTime(): -1;
		    		this.msn[i][j] = (j<s.getN())?s.getMiddleboxes().get(j).getType():-1;
		    	}
		    	
		    	for (int j=count; j<count+s.getVirtualLinks().size(); j++)
		    	{
		    		this.oe[j] =  s.getVirtualLinks().get(countinit).getSource().getId();
		    		this.de[j] =  s.getVirtualLinks().get(countinit).getDestination().getId();
		    		this.E[j] =  s.getId();
		    		
		    		countinit++;
		    	}
		    	countinit =0;
		    	count+=s.getVirtualLinks().size();
			}
	    	
	    	
	    	
			this.physicalNetwork = network;
		   	ArrayList<int[][]>networkArray = this.physicalNetwork.getPhysicalNetworkArray();
	    	
	    	//setting the parameters
	    	this.G = networkArray.get(0);
	    	this.cij = networkArray.get(1);		
			//make sure to choose from the VNF types hosted in the network when generating services. 
			//tf should not be null, if null generateServices will generate services with VNf types not in the network
	    	this.tf = networkArray.get(2)[0];
	    	this.xfk = networkArray.get(3);
	    	 
			this.DELTA = delta;    	
	     	this.F = this.tf.length;
	    	this.ks = this.G.length;
	   	    	this.toString();
	    	//setting the decision variables			
		
			y = new IloIntVar[S][this.maxMiddlebox][F][DELTA];
			z = new IloIntVar[S][this.maxMiddlebox][F][DELTA];
			h = new IloIntVar[S][this.maxMiddlebox][ks];
			q = new IloIntVar[S][this.maxMiddlebox][ks];
			theta = new IloIntVar[S][DELTA][E.length];
			thetaHat = new IloIntVar[S][DELTA][E.length];
			l = new IloIntVar[ks][ks][E.length];
			g = new IloIntVar[ks][ks][S][DELTA][E.length];
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
		    		 //loop over VNF
		    		for (int f=0; f<F; f++)
			    	{
		    			 //loop over time slots
		    			for (int delta=0; delta<DELTA; delta++)
				    	{
		    				z[i][j][f][delta] = cplex.intVar(0, 1,"z "+i+j+f+delta);	
		    				
		    				//force z = 0 for middleboxes that does not exist for the service (n not in Ns) (Prevent bject is unknown to IloCplex)
		    				if(j>=N[i])
		    				{
		    					cplex.addEq(z[i][j][f][delta], 0);
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
		    		for (int e=0; e<E.length; e++)
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
					//loop over services
					for (int s=0; s<S; s++)
			    	{
						//loop over VNF time slots
						for (int delta=0; delta<DELTA; delta++)
				    	{
				    		//loop over  edges
				    		for (int e=0; e<E.length; e++)
					    	{
				    			g[i][j][s][delta][e] = cplex.intVar(0, 1,"g "+i+j+s+delta+e);
				    			
					    	}
				    	}
			    	}
				
		    	}
		    }
		    
	    }
	    
	    /**
	     * This function will build the pricing model by building the constraints
	     * @throws IloException 
	     */
	    public void buildILPModel() throws IloException
	    {    	
	    	this.initializeDecisionVariables();
	    	 System.out.println("Building");
	    	 
	    	//set the objective 
		   	cplex.addMinimize(rho);
			 
		   	//start with the constraints
		  
			/**
			 * C3 : ensures that a middlebox $n$ of service $s$ should be mapped to exactly one VNF instance $f$ of the same type if the service $s$ is admitted to the network
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
		    		
		    		cplex.addEq(middleboxMappedConstraint, 1, "Const3_sn ["+i+"]["+j+"]");
		    	}	    			
		    }
		    
		    
			/**
			 * C4 :ensures that a middlebox $n$ of service $s$ should be mapped to a VNF instance $f$ of the same type if the service $s$ is admitted to the network
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
		    		
		    		cplex.addEq (middleboxTypeConstraint, msn[i][j], "Const4_sn ["+i+"]["+j+"]");
		    	}	    			
		    }
		    
		    
		    /**
			 * C5 :prevents the transmission of traffic of a service $s$ between two consecutive middleboxes $n$ and ($n+1$), if its processing on $n$ has not been completed
			 * theta [s][delta'][e] = 1-sum over f y[s][o(e)][f][delta]  for all e, for all s, for all delta, delta'; delta'<delta+p[s][o(e)]
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
	    		    			transmissionStartConstraint = cplex.sum(transmissionStartConstraint, y[i][oe[e]][f][delta]);
	    			    	}
	    		    		
	    		    		cplex.addLe( theta[i][deltaPrime][e],cplex.sum(1, cplex.prod(-1,transmissionStartConstraint)), "Const5_seDeltaprimeDelta ["+i+"]["+e+"]["+deltaPrime+"]["+delta+"]");
	    		    	}
	    	    	}
	    			
		    	}
		    	
		    }
		    
		    /**
			 * C6 :ensures that traffic of a service $s$ can not be processed by a middlebox $(n+1)$ if it was not transmitted to it by its precedent middlebox $n$
			 * sum over f y[s][d(e)][f][delta'] <= 1-theta[s][delta][e] for all e, for all s, for all delta, delta'; delta'<delta+ws[s]/bs[s]
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
	    		    			transmissionFinishedConstraint = cplex.sum(transmissionFinishedConstraint, y[i][de[e]][f][deltaPrime]);
	    			    	}
	    		    		
	    		    		cplex.addLe(transmissionFinishedConstraint, cplex.sum(1, cplex.prod(-1,theta[i][delta][e])),"Const6_seDeltaprimeDelta ["+i+"]["+e+"]["+deltaPrime+"]["+delta+"]");
	    		    	}
	    	    	}
	    			
		    	}
		    	
		    }
		    
		    /**
			 * C7 :prevents a middlebox $(n+1)$ to start processing traffic of service $s$ before its precedent middlebox $n$ finishes its execution
			 * sum over f y[s][n+1][f][delta'] <= 1-sum over f y[s][n][f][delta] for all n, for all s, for all delta, delta'; delta'<delta+psn[s][n]
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
	    		    		
	    		    		cplex.addLe (processStartOfnPlusConstraint, cplex.sum(1,cplex.prod(-1, processStartOfnConstraint)),"Const7_snDeltaprimeDelta ["+i+"]["+j+"]["+deltaPrime+"]["+delta+"]");
	    		    	}
	    	    	}	
		    			
		    	}	    			
		    }
		    
		    
		  /**
		   * C8: make sure that z is set to 1 only during the processing period
		   * sum over delta  z[s][i][f][delta]= psn[s][n] sum over delta y[s][i][f][delta] for all s, f, n
		   */
		    for(int s =0; s<S; s++)
			 {
				//loop over middleboxes of the service s
		   		for (int i=0; i<N[s]; i++)
		    	{
					//loop over VNF
		 	   		for (int f=0; f<F; f++)
		 		    {
		 	   			IloNumExpr processSameVNFConstraint = cplex.numExpr();
		 	   			IloNumExpr startedProcessingConstraint = cplex.numExpr();
		 	   			//loop over timeslots
		     	    	for (int delta=0; delta<DELTA; delta++)
		     		    {
		     	    		processSameVNFConstraint = cplex.sum (processSameVNFConstraint, z[s][i][f][delta]);
		     	    		startedProcessingConstraint = cplex.sum (startedProcessingConstraint, y[s][i][f][delta]);
		     		    }
		     	    	
		     	    	cplex.addEq(processSameVNFConstraint, cplex.prod(psn[s][i], startedProcessingConstraint), "Const8_snf["+s+"]["+i+"]["+f+"]");
		 		    }
		    	}
			 }
		    
	    /**
	     * C9: Specify that a service is processing (set z)
	     * 
	     * z[s][i][f][deltaPrime]>=y[s][i][f][delta] for all deltaPrime>= delta and deltaPrime<delta+psn[s][i] for all n for all s for all f
	     */
		  //loop over services s
		 for(int s =0; s<S; s++)
		 {
			//loop over middleboxes of the service s
	   		for (int i=0; i<N[s]; i++)
	    	{
				//loop over VNF
	 	   		for (int f=0; f<F; f++)
	 		    {
	 	   			//loop over timeslots
	     	    	for (int delta=0; delta<DELTA; delta++)
	     		    {
	     	    		//loop over timeslots
	 	     			for (int deltaPrime=delta; deltaPrime<delta+psn[s][i]&&deltaPrime<DELTA; deltaPrime++ )
	 	     	    	{
	 	     				cplex.addGe(z[s][i][f][deltaPrime], y[s][i][f][delta], "Const9_SNDeltaprimeDeltaF ["+s+"]["+i+"]["+deltaPrime+"]["+delta+"]["+f+"]"); 	     				
	 	     	    	}
	     		    }
	 		    }
	    	}
		 }
		 
		 
		 /**
		  * C10: Ensure that a VNf can process the traffic of one service at a time
		  * sum over all s, over all n of the same type of f z[s][n][f][delta] <=1 for all f for all delta
		  */
		 //loop over VNF
		for (int f=0; f<F; f++)
	    {
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
	 	 	   			
	 	 	   			processSameVNFConstraint = cplex.sum(processSameVNFConstraint, z[s][i][f][delta]); 	 	   			
	 	 	    	}
	      	    }
	    		
	    		cplex.addLe(processSameVNFConstraint, 1,"Const10_DeltaF ["+delta+"]["+f+"]" );
		    }
	    }
		 
		  
	  	    /**
	  		 * C11 :sets the completion time of all services to be = to be greater or equal to the completion time of each service
	  		 * rho>=(sum over f sum over delta y[s][|Ns|][f][delta](delta+psn[s][|Ns|])  for all s, 
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
		    	
		    	cplex.addGe(rho,processedBeforeDeadlineConstraint, "Const11_S ["+i+"]");
		    }

		    
		    /**
	  		 * C12 : specifies the physical server $k$ on which a middlebox $n$ of service $s$ is mapped
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
			    			
							cplex.addEq(q[i][j][k], mappingConstraint,"Const12_snk ["+i+"]["+j+"]["+k+"]" );
				    	}
			    	}
			    }
		    
		    /**
	  		 * C13 :specifies if two consecutive middleboxes $n$ and $(n+1)$ of the same service $s$ are mapped to VNFs $f$ and $f^\prime$ hosted on the same physical server
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
			    			cplex.addLe(h[s][j][k], q[s][j][k],"Const12_1_nSk ["+j+"][["+s+"]["+k+"]" );
			    			cplex.addLe(h[s][j][k], q[s][j+1][k],"Const12_2_nSk ["+j+"][["+s+"]["+k+"]" );
			    			cplex.addGe(h[s][j][k], cplex.sum (cplex.sum(q[s][j][k],q[s][j+1][k]), -1),"Const13_3_nSffPrime ["+j+"][["+s+"]["+k+"]" );
				    	
				    	}
			    	}
			    }

		    
		    /**
	  		 * C14 :prevents the start of the transmission of traffic of a service $s$ between two consecutive middleboxes $o(e)$ and $d(e)$ if they are mapped to VNFs hosted on the same physical server or if the service is not admitted
	  		 * sum over delta theta[s][delta[e] = (1-sum over k h[s][o(e)][k])a[s] for all e for all s
	  		 * C13-1: sum over delta theta[s][delta[e]<=1-sum over k h[s][o(e)][k]
	  		 * C13-2: sum over delta theta[s][delta[e]<= a[s] for all e for all s
	  		 * C13-3: sum over delta theta[s][delta[e]>= a[s] -sum over k h[s][o(e)][k] for all e for all s
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
					cplex.addEq(preventTransmissionConstraint, cplex.sum (1, cplex.prod(-1, physicalServerMappingConstraint)),"Const14_1_se ["+i+"]["+e+"]" );

		    	}
		    }
	  	    
		    /**
	  		 * C15 :specifies that the virtual link $e$ is used to transmit traffic of service $s$ during all the required transmission time ($\frac{w_s}{b_s}$)
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
					
					cplex.addEq(transmissionTimeConstraint, cplex.prod (Math.ceil((double)ws[i]/(double)bs[i]),transmissionStartConstraint),"Const15_se ["+i+"]["+e+"]" );
		    	}
		    }
		    
		    
		    /**
	  		 * C16 : ensures the virtual link $e$ is used for transmission of traffic of service $s$ during all the transmission delay
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
						
						cplex.addGe(transmissionTimeConstraint, cplex.prod (Math.ceil((double)ws[i]/(double)bs[i]),theta[i][delta][e]),"Const16_seDelta ["+i+"]["+e+"]["+delta+"]" );
			    	}				
		    	}
		    }
	    
		    
		     /**
	  		 * C17 : ensures that the physical links capacity constraint is not violated
	  		 * sum over s sum over e l[i][j][e]thetaHat[s][delta][e] b[s] <= cij[i][j] for all delat , for all (ij) in L->linearize
	  		 * Const16_1: g[i][j][s][delta][e] <= l[i][j][e] for all delta, (ij), e, s ->C24
	  		 * Const16_2: g[i][j][s][delta][e] <= thetaHat[s][delta][e] for all delta, (ij), e, s ->C25
	  		 * Const16_3: g[i][j][s][delta][e] >= l[i][j][e]+thetaHat[s][delta][e] -1 for all delta, (ij), e, s ->C26
	  		 * Const16_4: sum over s sum over e g[i][j][s][delta][e]bs[s]+ sum over s sum over e g[j][i][s][delta][e]bs[s]<= c[i][j] for all delta, (ij) -> c27
	  		 */
		     
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
		   			
		   			//loop over timeslots
					for (int delta=0; delta<DELTA; delta++)
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
			   		    			cplex.addLe(g[i][j][s][delta][e], l[i][j][e], "Const16_1_ijsDeltaE ["+i+"]["+j+"]["+s+"]["+delta+"]["+e+"]");
			   		    			cplex.addLe(g[i][j][s][delta][e], thetaHat[s][delta][e], "Const16_2_ijsDeltaE ["+i+"]["+j+"]["+s+"]["+delta+"]["+e+"]");
			   		    			cplex.addGe(g[i][j][s][delta][e], cplex.sum(cplex.sum(l[i][j][e],thetaHat[s][delta][e]),-1), "Const17_3_ijsDeltaE ["+i+"]["+j+"]["+s+"]["+delta+"]["+e+"]");
			   		    		}
			   		    	}
			   		    }
			    	}
			    }
		     }
		     
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
		   		    		IloNumExpr bandwidthConstraint2 = cplex.numExpr();//changed
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
			   			   			
			   			   			/**
			   			   			 * Make sure to add capacity constraint for link ij and ji 
			   			   			 */
			   			   			bandwidthConstraint = cplex.sum(bandwidthConstraint, cplex.prod(g[i][j][s][delta][e], bs[s]));
			   			   			bandwidthConstraint2 = cplex.sum(bandwidthConstraint2, cplex.prod(g[j][i][s][delta][e], bs[s]));//changed
			   			   	
			   				    }
			   			     }   			    
			   			     	/**
		   			   			 * Make sure to add capacity constraint for link ij and ji
		   			   			 * keep using cij and no cji because only cij are populated and ij is the same as ji (check graph)
		   			   			 * sum over s sum over e g[i][j][s][delta][e]bs[s]+ sum over s sum over e g[j][i][s][delta][e]bs[s]<= c[i][j]
		   			   			 * this is to consider that ij and ji are one link so if they are assigned at the same time slot make sure the capacity is respected
		   			   			 */
			   			  cplex.addLe(cplex.sum(bandwidthConstraint,bandwidthConstraint2), cij[i][j],"Const17_4_DeltaIJ ["+delta+"]["+i+"]["+j+"]" );
			   		
	   		    		}
	   		    	
	   		    	}
	   		    }
	    	}
		   
		
		    
		    /**
	  		 * C18 : specifies the physical server $k$ on which a middlebox $n$ of service $s$ is mapped
	  		 * IMPORTANT: This constraint may select some links and create subtours that may not be included in the path from source to destination which also may encouter reserving bw dor them
	  		 * even though that can not be actually used. This can be eliminated by adding subtour elemination constraint. However this is not added here because:
	  		 * 1- Those links wont be selected if they are against the objective function
	  		 * 2- adding subtour elimination constraint is costly
	  		 *  sum j:(i,j) in L l[i][j][e] - sum j:(j,i) in L   l[j][i][e] = q[s][oe[e]][i] - q[s][de[e]][i] for all iin ks for all e, for all s 
	  		 *  
	  		 */	     
		    
		    //loop over services s
		    for(int s =0; s<S; s++)
		     {
		    	//loop over edges
		   		for (int e=0; e<E.length; e++)
			    {	   			 
		   			// if the virtual is not for the service do not map it to a physical link
		    		if (E[e]!=s)
		    		{
		    		
		    			continue;
		    		}
		    		
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
		   		    		if (G[i][j]==1 || G[j][i]==1)//
		   		    		{ 
		   		    			physicalLinkConstraint1 = cplex.sum(physicalLinkConstraint1, l[i][j][e]);
		   		    			physicalLinkConstraint2 = cplex.sum(physicalLinkConstraint2, l[j][i][e]);
		   		    			
		   		    			//l[i][j][e]+l[j][i][e]<=1 prevent tours for one link only.
		   		    			//cplex.addLe(cplex.sum(l[i][j][e],l[j][i][e]), 1, "Const17_1_["+s+"]["+e+"]["+i+"]");	   		    		
		   		    		
		   		    		}
		   		    		
		   		    	}	   		    	
		   		    	
		   		    	//sum j:(i,j) in L l[i][j][e][delta] - sum j:(j,i) in L
		   		    	constraint17_1 = cplex.sum(physicalLinkConstraint1, cplex.prod(-1, physicalLinkConstraint2));
		   		    	
		   		    	//q[s][oe[e]][i] - q[s][de[e]][i]
			   		    constraint17_2 = cplex.sum( q[s][oe[e]][i], cplex.prod(-1, q[s][de[e]][i])); 
			   		    
			   		    cplex.addEq(constraint17_1,constraint17_2, "Const18_sei["+s+"]["+e+"]["+i+"]");
		   		    }		    	
		    	}
		     }
		     
		    /**
		     * C19: Make sure that virtual link is not mapped if its source and destination are on the same server
		     * l[i][j][e]<= 1- sum over k h[s][oe][k] for all ij in L, for all e in Es for all s in S
		     */
		    //loop over services s
		    for(int s =0; s<S; s++)
		     {
		    	//loop over edges
		   		for (int e=0; e<E.length; e++)
			    {	   			 
		   			// if the virtual is not for the service do not map it to a physical link
		    		if (E[e]!=s)
		    		{
		    		
		    			continue;
		    		}
		    		
					//loop over servers
		   		    for(int i =0; i<ks; i++)
		   		    {	  
		   		    	 //loop over servers
		   		    	for (int j=0; j<ks; j++)
		   		    	{		    		
		   		    		
		   		    		//make sure the physical link exists -- NEED to check ON G[i][j] and  G[j][i]==1 because we are using l[i][j][e] and l[j][i][e]
		   		    		if (G[i][j]==1 || G[j][i]==1 )//
		   		    		{ 
		   		    			IloNumExpr physicalServerMappingConstraint = cplex.numExpr();
		   		    			for (int k=0; k<ks; k++)
		   				    	{
		   							physicalServerMappingConstraint = cplex.sum(physicalServerMappingConstraint, h[s][oe[e]][k]);
		   				    	}
		   		    			cplex.addLe(l[i][j][e], cplex.diff(1,  physicalServerMappingConstraint), "Const19_ijse["+i+"]["+j+"]["+s+"]["+e+"]");	    		
		   		    		
		   		    		}
		   		    		
		   		    	}	   		    	
		   		    	
		   		    }		    	
		    	}
		     }
		    
	    	
	    }
	    
	    /**
		 * Function for testing on the same inputs
		 * This function provides values for the parameters of the model
		 */
		public static ArrayList<int[][]> manualInput()
		{
			//inputs used to randomly generate the below values
			/*int ks = 4;
			int L = 5;
			int F = 8;
			int T = 8;
			int S = 3;
			int DELTA = 20;//changed to 30
			int minCapacity = 50;
			int maxCapacity = 1000;
			int minMiddlebox = 3;
			int maxMiddlebox = 5;
			int minBw = 10;
			int maxBw = 500;
			int maxProcessing =4;
			int minProcessing = 1;
			int minTraffic = 10;// should be 500 with respect to bw so thetahat won't be 0 (c13)
			int maxTraffic = 40;*///wrong should be 1500 with respect to bw
			
			
			
			 ArrayList<int[][]> parameters = new ArrayList<int[][]>();
			 
			 /**
			  * Example 1 : 3 services:
			  * S0: has VNF 1 and 2 mapped on the same server 2 -> admitted
			  * S1: Has all VNF mapped on server 1 -> no transmission -> admitted
			  * S2: requesting VNF type (6) that do not exist -> not admitted
			  */
			//physical network
		/*	int [][] G = {
			 {0, 1, 1, 0},
			 {0, 0, 1, 0 },
			 {0, 0, 0, 0},
			 {0, 1, 1, 0}, 
			};
			
			//links capacity
			int [][] cij = {
			 { 0, 789, 758, 0 }, 
			 {0, 0, 294, 0}, 
			 {0, 0, 0, 0}, 
			 {0, 940, 948, 0 }	
			};

			//VNFs types
			//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
			int [][] tf = {{2, 3, 4, 4, 8, 3, 1, 5  }};//manual
		
			//VNF MAPPING TO PHYSICAL SERVERS
			int [][] xfk = {
				{0, 0, 0, 1}, 
				{0, 0, 1, 0}, 
				{1, 0, 0, 0},
				{1, 0, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 0, 1, 0}, 
			};
			
			//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
			int [][]N = {{4, 3, 5 }};
			
			//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
			int[][] msn ={
				{4, 5, 3, 2, -1  },
				{3, 1, 8, -1, -1 },//{4,2,5,  -1, -1},
				{2, 1, 6, 3, 5  },
			};

			//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
			int [][] psn = {
				{2, 3, 3, 3, -1},
				{1, 1, 2, -1, -1},
				{1, 2, 3, 2, 2 }
			};
		 
			//BANDWIDTH REQUIRED BY EACH SERVICE--
			int [][]bs = {{136, 84, 361  }};
			
			//TRAFFIC SIZE OF EACH SERVICE
			int [][]ws =  {{408, 420, 361 }};
		
			//deadline OF EACH SERVICE
			int [][]us = {{22, 18, 19 }};
					
			//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
			int [][] E = {{0, 0, 0, 1, 1, 2, 2, 2, 2 }};
		
			//ORIGIN OF EACH VIRTUAL LINK--
			int [][] oe = {{0, 1, 2, 0, 1, 0, 1, 2, 3}};
			
			//DESTINATION OF EACH VIRTUAL LINK--
			int [][] de = {{1, 2, 3, 1, 2, 1, 2, 3, 4 }};
			*/
			
		
			 
			 /**
			  * Example 2 : 3 services:
			  * S0: has VNF 1 and 2 mapped on the same server 2 -> not admitted because of bandwidth capacity constraint
			  * S1: Has all VNF mapped on server 1 -> no transmission -> admitted
			  * S2: requesting VNF type (6) that do not exist -> not admitted
			  */
			 /*int [][] G = {
				 {0, 1, 1, 0},
				 {0, 0, 1, 0 },
				 {0, 0, 0, 0},
				 {0, 1, 1, 0}, 
				};
				
				//links capacity
			int [][] cij = {
				 { 0, 789, 758, 0 }, 
				 {0, 0, 294, 0}, 
				 {0, 0, 0, 0}, 
				 {0, 940, 948, 0 }	
				};
		//VNFs types
			//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
			int [][] tf = {{2, 3, 4, 4, 8, 3, 1, 5  }};//manual
		
			//VNF MAPPING TO PHYSICAL SERVERS
			int [][] xfk = {
				{0, 0, 0, 1}, 
				{0, 0, 1, 0}, 
				{1, 0, 0, 0},
				{1, 0, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 0, 1, 0}, 
			};
			
			//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
			int [][]N = {{4, 3, 5 }};
			
			//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
			int[][] msn ={
				{4, 5, 3, 2, -1  },
				{3, 1, 8, -1, -1 },
				{2, 1, 6, 3, 5  },
			};

			//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
			int [][] psn = {
				{2, 3, 3, 3, -1},
				{1, 1, 2, -1, -1},
				{1, 2, 3, 2, 2 }
			};
		 
			//BANDWIDTH REQUIRED BY EACH SERVICE--
			int [][]bs = {{1000, 84, 361  }};
			
			//TRAFFIC SIZE OF EACH SERVICE
			int [][]ws =  {{2000, 420, 361 }};
		
			//deadline OF EACH SERVICE
			int [][]us = {{22, 18, 19 }};
					
			//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
			int [][] E = {{0, 0, 0, 1, 1, 2, 2, 2, 2 }};
		
			//ORIGIN OF EACH VIRTUAL LINK--
			int [][] oe = {{0, 1, 2, 0, 1, 0, 1, 2, 3}};
			
			//DESTINATION OF EACH VIRTUAL LINK--
			int [][] de = {{1, 2, 3, 1, 2, 1, 2, 3, 4 }};
			*/
			 
			/**
			  * Example 3 : 3 services:
			  * S0, S1 are the same (same chain, same bw, same deadline: 
			  * S2: requesting VNF type (6) that do not exist -> not admitted
			  */
			/* int [][] G = {
				 {0, 1, 1, 0},
				 {0, 0, 1, 0 },
				 {0, 0, 0, 0},
				 {0, 1, 1, 0}, 
				};
				
				//links capacity
			int [][] cij = {
				 { 0, 789, 758, 0 }, 
				 {0, 0, 294, 0}, 
				 {0, 0, 0, 0}, 
				 {0, 940, 948, 0 }	
				};
			//VNFs types
			//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
			int [][] tf = {{2, 3, 4, 4, 8, 3, 1, 5  }};//manual
		
			//VNF MAPPING TO PHYSICAL SERVERS
			int [][] xfk = {
				{0, 0, 0, 1}, 
				{0, 0, 1, 0}, 
				{1, 0, 0, 0},
				{1, 0, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 1, 0, 0}, 
				{0, 0, 1, 0}, 
			};
			
			//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
			int [][]N = {{3, 3, 5 }};
			
			//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
			int[][] msn ={
				{3, 1, 8, -1, -1 },
				{3, 1, 8, -1, -1 },
				{2, 1, 6, 3, 5  },
			};

			//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
			int [][] psn = {
				{1, 1, 2, -1, -1},
				{1, 1, 2, -1, -1},
				{1, 2, 3, 2, 2 }
			};
		 
			//BANDWIDTH REQUIRED BY EACH SERVICE--
			int [][]bs = {{84, 84, 361  }};
			
			//TRAFFIC SIZE OF EACH SERVICE
			int [][]ws =  {{420, 420, 361 }};
		
			//deadline OF EACH SERVICE
			int [][]us = {{18, 18, 19 }};
					
			//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
			int [][] E = {{0, 0, 1, 1, 2, 2, 2, 2 }};
		
			//ORIGIN OF EACH VIRTUAL LINK--
			int [][] oe = {{0, 1, 0, 1, 0, 1, 2, 3}};
			
			//DESTINATION OF EACH VIRTUAL LINK--
			int [][] de = {{1, 2, 1, 2,  1, 2, 3, 4 }};*/
			 
			 /**
			  * Example 4: egress and ingress considered with processing time = 0
			  * All 3 services are admitted
			  * S1 : starts processing (y) and transmission (theta and thetaHat) at the same time delta = 0 which is correct since processing on ingress = 0
			  * Good example to check mapping to VNF at the same time S0 and S1 assign at delta= 18 to egress F6 and all 3 assigned at delta = 0 at ingress
			  */
			 /*int [][] G = {
					 {0, 1, 1, 0},
					 {0, 0, 1, 0 },
					 {0, 0, 0, 0},
					 {0, 1, 1, 0}, 
					};
					
					//links capacity
				int [][] cij = {
					 { 0, 789, 758, 0 }, 
					 {0, 0, 294, 0}, 
					 {0, 0, 0, 0}, 
					 {0, 940, 948, 0 }	
					};
				//VNFs types
				//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
				int [][] tf = {{0, 1, 4, 6, 8, 3, 1, 5  }};//manual
			
				//VNF MAPPING TO PHYSICAL SERVERS
				int [][] xfk = {
					{0, 0, 0, 1}, 
					{0, 0, 1, 0}, 
					{1, 0, 0, 0},
					{1, 0, 0, 0}, 
					{0, 1, 0, 0}, 
					{0, 1, 0, 0}, 
					{0, 1, 0, 0}, 
					{0, 0, 1, 0}, 
				};
				
				//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
				int [][]N = {{3, 3, 5 }};
				
				//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
				int[][] msn ={
					{0, 4, 1, -1, -1 },
					{0, 8, 1, -1, -1 },
					{0, 5, 6, 3, 1  },
				};

				//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
				int [][] psn = {
					{0, 1, 0, -1, -1},
					{0, 1, 0, -1, -1},
					{0, 2, 3, 2, 0 }
				};
			 
				//BANDWIDTH REQUIRED BY EACH SERVICE--
				int [][]bs = {{84, 84, 361  }};
				
				//TRAFFIC SIZE OF EACH SERVICE
				int [][]ws =  {{420, 420, 361 }};
			
				//deadline OF EACH SERVICE
				int [][]us = {{18, 18, 19 }};
						
				//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
				int [][] E = {{0, 0, 1, 1, 2, 2, 2, 2 }};
			
				//ORIGIN OF EACH VIRTUAL LINK--
				int [][] oe = {{0, 1, 0, 1, 0, 1, 2, 3}};
				
				//DESTINATION OF EACH VIRTUAL LINK--
				int [][] de = {{1, 2, 1, 2,  1, 2, 3, 4 }};*/
			 /**
			  * Example 5: egress and ingress considered with processing time = 0 
			  * 
			  * Service 0 is admitted, the rest are rejected because of deadline time
			  * Good example to Test that a virtual link is not routed if the service is not admitted
			  */
			/* int [][] G = {
					 {0, 1, 1, 1},
					 {0, 0, 0, 0 },
					 {0, 1, 0, 0},
					 {0, 1, 0, 0}, 
					};
					
					//links capacity
				int [][] cij = {
					 { 0, 1000, 1000, 1000 }, 
					 {0, 0, 0, 0}, 
					 {0, 1000, 0, 0}, 
					 {0, 1000, 0, 0 }	
					};
				//VNFs types
				//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
				int [][] tf = {{0, 1, 1, 3, 1, 6, 4, 4   }};//manual
			
				//VNF MAPPING TO PHYSICAL SERVERS
				int [][] xfk = {
					{0, 0, 0, 1}, 
					{0, 0, 1, 0}, 
					{0, 0, 1, 0},
					{0, 1, 0, 0}, 
					{0, 1, 0, 0}, 
					{1, 0, 0, 0}, 
					{0, 0, 1, 0}, 
					{0, 0, 1, 0}, 
				};
				
				//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
				int [][]N = {{3,3, 5, 5 }};
				
				//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
				int[][] msn ={
					{0, 4, 1, -1, -1 },
					{0, 4, 1, -1, -1 },
					{0, 4, 3, 4, 1 },
					{0, 4, 4, 6, 1  },
				};

				//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
				int [][] psn = {
					{0, 4, 0, -1, -1},
					{0, 3, 0, -1, -1},
					{0, 4, 1, 1, 0},
					{0, 1, 1, 1, 0 }
				};
			 
				//BANDWIDTH REQUIRED BY EACH SERVICE--
				int [][]bs = {{200, 10, 220, 252   }};
				
				//TRAFFIC SIZE OF EACH SERVICE
				int [][]ws =  {{956, 898, 1301, 1381 }};
			
				//deadline OF EACH SERVICE
				int [][]us = {{13, 25, 10, 14 }};
						
				//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
				int [][] E = {{0, 0, 1, 1, 2, 2, 2, 2 ,3, 3, 3, 3 }};
			
				//ORIGIN OF EACH VIRTUAL LINK--
				int [][] oe = {{0, 1, 0, 1, 0, 1, 2, 3, 0, 1, 2, 3 }};
				
				//DESTINATION OF EACH VIRTUAL LINK--
				int [][] de = {{1, 2, 1, 2, 1, 2, 3, 4, 1, 2, 3, 4 }};*/
			
			 /**
			  * Example 6: Link with capacity less than required by service cause no solution to route a VND
			  * 
			  */
			/* int [][] G = {
					  {0, 1, 0, 0, },

					 {0, 0, 0, 0, },

					 {1, 1, 0, 0, },

					  {0, 1, 1, 0, },
					};
					
					//links capacity
				int [][] cij = {
						{0, 500, 0, 0, },//407 before

						{0, 0, 0, 0, },

						{215, 781, 0, 0, },

						{0, 763, 87, 0, },
					};
				//VNFs types
				//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
				int [][] tf = {{0, 1, 3, 3, 8, 7, 4, 5,   }};//manual
			
				//VNF MAPPING TO PHYSICAL SERVERS
				int [][] xfk = {
						{0, 0, 0, 1, },
						 {0, 1, 0, 0, },
						 {0, 0, 1, 0, },
						{0, 0, 1, 0, },
						 {0, 0, 0, 1, },
						 {1, 0, 0, 0, },
						{0, 1, 0, 0, },
						 {0, 0, 0, 1, }, 
				};
				
				//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
				int [][]N = {{3}};
				
				//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
				int[][] msn ={
						{0, 7, 1, -1, -1, -1, },
				};

				//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
				int [][] psn = {
						{0, 1, 0, -1, -1, -1, },
				};
			 
				//BANDWIDTH REQUIRED BY EACH SERVICE--
				int [][]bs = {{468   }};
				
				//TRAFFIC SIZE OF EACH SERVICE
				int [][]ws =  {{896}};
			
				//deadline OF EACH SERVICE
				int [][]us = {{13}};
						
				//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
				int [][] E = {{0, 0,  }};
			
				//ORIGIN OF EACH VIRTUAL LINK--
				int [][] oe = {{0, 1,  }};
				
				//DESTINATION OF EACH VIRTUAL LINK--
				int [][] de = {{1, 2, }};*/

			 /**
			  * Example 7: Normal example with 3 services
			  * 
			  */
			/* int [][] G = {
					 {0, 1, 0, 0, },

					 {0, 0, 0, 0, },

					 {1, 1, 0, 0, },

					{0, 1, 1, 0, },
					};
					
					//links capacity
				int [][] cij = {
						{0, 500, 0, 0, },//407 before

						{0, 0, 0, 0, },

						{215, 781, 0, 0, },

						{0, 763, 87, 0, },
					};
				//VNFs types
				//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
				int [][] tf = {{0, 1, 3, 3, 8, 7, 4, 5,   }};//manual
			
				//VNF MAPPING TO PHYSICAL SERVERS
				int [][] xfk = {
						{0, 0, 0, 1, },
						 {0, 1, 0, 0, },
						 {0, 0, 1, 0, },
						{0, 0, 1, 0, },
						 {0, 0, 0, 1, },
						 {1, 0, 0, 0, },
						{0, 1, 0, 0, },
						 {0, 0, 0, 1, }, 
				};
				
				//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
				int [][]N = {{5, 3, 5, }};
				
				//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
				int[][] msn ={
						 {0, 5, 8, 4, 1, -1, },

						 {0, 7, 1, -1, -1, -1, },

						 {0, 3, 5, 5, 1, -1, },
				};

				//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
				int [][] psn = {
						 {0, 3, 6, 5, 0, -1, },

						 {0, 1, 0, -1, -1, -1, },

						 {0, 5, 2, 2, 0, -1, },
				};
			 
				//BANDWIDTH REQUIRED BY EACH SERVICE--
				int [][]bs = {{410, 468, 258,    }};
				
				//TRAFFIC SIZE OF EACH SERVICE
				int [][]ws =  {{1190, 896, 1473, }};
			
				//deadline OF EACH SERVICE
				int [][]us = {{14, 12, 70, }};
						
				//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
				int [][] E = {{0, 0, 0, 0, 1, 1, 2, 2, 2, 2,   }};
			
				//ORIGIN OF EACH VIRTUAL LINK--
				int [][] oe = {{0, 1, 2, 3, 0, 1, 0, 1, 2, 3,  }};
				
				//DESTINATION OF EACH VIRTUAL LINK--
				int [][] de = {{1, 2, 3, 4, 1, 2, 1, 2, 3, 4,  }};*/
			 /**
			  * Example 7 : Making sure of the link capacity constraint where one link can be traversed at the same time by 2 services
			  * with no enough capacity
			  */
			 int [][] G = {
					 {0, 1, 0,},
					 {0, 0,1,},
					 {0, 0, 0, },

					};
					
					//links capacity
				int [][] cij = {
						{0, 490, 0, },//407 before
						{0, 0, 490,},
						{0, 0, 0,},
					};
				//VNFs types
				//int [][] tf = {{2, 3, 4, 4, 8, 2, 3, 8  }};
				int [][] tf = {{0, 2, 4, 1, 3   }};//manual
			
				//VNF MAPPING TO PHYSICAL SERVERS
				int [][] xfk = {
						 {0, 0, 1,},
						 {0, 0, 1, },
						 {0, 1,0 },
						{0, 1, 0, },
						 {1, 0, 0 },
					 
				};
				
				//NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE
				int [][]N = {{3, 3,  }};
				
				//SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)
				int[][] msn ={
						 {0, 3, 1, -1, -1, -1, },

						 {4, 0, 1, -1, -1, -1, },

				};

				//PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE
				int [][] psn = {
						 {0, 1, 0, -1, -1, -1, },

						 {0, 1, 0, -1, -1, -1, },
					
				};
			 
				//BANDWIDTH REQUIRED BY EACH SERVICE--
				int [][]bs = {{250, 250,     }};
				
				//TRAFFIC SIZE OF EACH SERVICE
				int [][]ws =  {{250, 250, }};
			
				//deadline OF EACH SERVICE
				int [][]us = {{14, 12,  }};
						
				//VIRTUAL LINKS ASSIGNMENT FOR SERVICES
				int [][] E = {{0, 0, 1, 1,   }};
			
				//ORIGIN OF EACH VIRTUAL LINK--
				int [][] oe = {{0, 1, 0, 1,}};
				
				//DESTINATION OF EACH VIRTUAL LINK--
				int [][] de = {{1, 2, 1, 2,  }};
			parameters.add(G);
			parameters.add(cij);
			parameters.add(tf);
			parameters.add(xfk);
			parameters.add(N);
			parameters.add(msn);
			parameters.add(psn);
			parameters.add(bs);
			parameters.add(ws);
			parameters.add(us);
			parameters.add(E);
			parameters.add(oe);
			parameters.add(de);
			
			return parameters;
		}
		
		
		/**
		 * This function build the services configurations based on the solution returned
		 * by the ILP model
		 * This is basically used to pass the configurations as initial solution to the master model
		 * for testing purposes
		 * All configuration will have configPerServiceId = 0
		 * O, R and V are build based on the constraints written in the pricing 
		 * @return array of configurations
		 */
		public ArrayList<Configuration> buildServiceConfigurations()
		{
			int L = this.physicalNetwork.getLinkList().length;
			Link pl;
			ArrayList<Configuration> configurations = new ArrayList<Configuration> ();
			int[][] r=  new int [this.F][this.DELTA];; 
			int[][] o =  new int [L][this.DELTA];
			int v = 0;
			Configuration c;
			int sourceId;
			int destinationId;
			
			
		    //loop over services
		    for(int s =0; s<S; s++)
		    {
		    	//reset the values
		    	r = new int [this.F][this.DELTA];
				o = new int [L][this.DELTA];
				v=0;
				
				//build r
		    	 //loop over middleboxes
		    	for (int j=0; j<maxMiddlebox; j++)
		    	{
		    		 //loop over VNF
		    		for (int f=0; f<F; f++)
			    	{
		    			 //loop over time slots
		    			for (int delta=0; delta<this.DELTA; delta++)
				    	{
		    				if (msn[s][j] !=tf[f] )
			 	    		{ 
		 	 	   				continue;
			 	    		}
		    				 try {
		    					 
								r[f][delta]+= (int) this.cplex.getValue(z[s][j][f][delta]);
								
							} catch (IloException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
		    				
				    	}
			    	}
		    	}
		    	
		    	//build v
		    	for (int i=0; i<this.F; i++)
				{
					for (int j=0; j<this.DELTA; j++)
					{
						try {
							v+=this.cplex.getValue(this.y[s][this.N[s]-1][i][j])*(j+this.psn[s][this.N[s]-1]);
						} catch (IloException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
		    	
		    	//build o
		    	for (int i=0; i<this.ks; i++)
				{
					for (int j=0; j<this.ks; j++)
					{									
						 //loop over time slots
		    			for (int delta=0; delta<this.DELTA; delta++)
				    	{
							for (int e=0; e<this.E.length; e++ )
							{
								  try {
									  //get the physical link between the 2 nodes
									 pl = this.physicalNetwork.getLink(i, j, true);
									 //if no link exists between the 2 nodes continue;
									 if (pl ==null)
									 {
										 continue;
									 }
									 
									o[pl.getId()][delta] += this.cplex.getValue(this.l[i][j][e])*this.cplex.getValue(this.thetaHat[s][delta][e])*this.bs[s];
								} catch (IloException x) {
									
								}
								  pl = null;
							}
						}
					}
					
				}
				//consider that this is the first config by service ConfigPerServiceId =0
				c = new Configuration( this.services.get(s), v, r, o);
				configurations.add(c);
		    }
			
			
			return configurations;
		}
		
	    /**
	     * This method will run the ILP model 
	     * Export the model to a file called ILPModel_set_"+setId+".lp
	     * Report the results (values of the variables) to a file called ILP/ILPModel_set_"+setId+".lp
	     * 
	     * @param resultsFile path to the file where to dump results
	     * @param setId
	     * @throws IloException 
	     */
	    public void runILPModel(String resultsFile, int setId) throws IloException
	    {
	    	
	    	try {
				this.cplex.exportModel("ILPModel_set_"+setId+".lp");
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
				//st+="\t\t"+Output.printArray(a,"a[s]", "\t\ta",this.cplex);
				st+="\t\t"+Output.printQuadrupleArray(y,"y[s][n][F][DELTA]", "\t\ty",this.cplex);
				st+="\t\t"+Output.printQuadrupleArray(z,"z[s][n][F][DELTA]", "\t\tz",this.cplex);
				st+="\t\t"+Output.printTripleArray(q,"q[s][n][ks]","\t\tq", this.cplex); 
				st+="\t\t"+Output.printTripleArray(h,"h[s][n][ks]","\t\th", this.cplex);
				st+="\t\t"+Output.printTripleArray(theta,"theta[s][DELTA][E]","\t\ttheta", this.cplex);
				st+="\t\t"+Output.printTripleArray(thetaHat,"thetaHat[s][DELTA][E]","\t\tthetaHat", this.cplex);
				st+="\t\t"+Output.printTripleArray(l,"l[ks][ks][E]","\t\tl", this.cplex);
				st+="\t\t"+Output.printCinqArray(g,"g[ks][ks][s][DELTA][E]","\t\tg", this.cplex);
				
						
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
			
		
			st+="\t\t"+this.physicalNetwork.toString()+"\n";
			
			st+="\t\t Services\n";
			for (int i=0; i<this.services.size(); i++)
			{
				st+= "\t\t"+this.services.get(i).toString();
			}
			
			return st;
			
	    }
		public static void main(String[] args) throws IloException 
		{	
			// test with manual input
		   /*	ArrayList<int[][]> parameters = ILPModelModified.manualInput();
	   
	    	ILPModelModified ilp =  new ILPModelModified(parameters.get(0), parameters.get(3),parameters.get(1), parameters.get(2)[0],parameters.get(4)[0], parameters.get(10)[0], parameters.get(11)[0], parameters.get(12)[0], parameters.get(7)[0],parameters.get(8)[0],parameters.get(9)[0],parameters.get(6), parameters.get(5), 100,3);
	    	ilp.buildILPModel();
	    	ilp.runILPModel(1);*/
	    	
			//test with geenrated input
			int ks = 3;//4;//10;
			int L = 2;//5;//14;
			int F =5;//8;
			int T = 5;//8;
			int S = 2;//3;//15;
			int DELTA = 100;
			int minCapacity = 1000;
			int maxCapacity = 1000;
			int minMiddlebox = 5;
			int maxMiddlebox = 10;
			int minBw = 10;
			int maxBw = 500;
			int maxProcessing =6;
			int minProcessing = 1;
			int minTraffic = 500;
			int maxTraffic = 1500;
			double startlp =0;
			double endlp =0;
			
			int vnfTypesNb =7;
			 DELTA =85;// 50;
				//Network  pNetwork = new Network(10,10,8,8,500,500,1,3);
				Network  pNetwork = new Network(5,5,4,5,500,500,1,0);
			// Network  pNetwork = new Network(vnfTypesNb,vnfTypesNb,4,5,500,500,1,0);
	    	//Network pNetwork = new Network (F, T, ks, L, minCapacity, maxCapacity,1);
			// Network  pNetwork = new Network(5,5,3,3,200,200,1);
			 ArrayList<int[][]> physicalNetwork = pNetwork.buildPhysicalNetwork();
			int [] tf = physicalNetwork.get(2)[0];
			//ServicesDriver driver = new ServicesDriver (S, DELTA, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic,tf);
		//	ServicesDriver driver = new ServicesDriver (6,  DELTA, 3, 5, 200, 500, 5 , 1, 500, 1500,tf);
			ServicesDriver driver = new ServicesDriver (6,  DELTA, 3, 5, 300, 500, 5 , 1, 500, 1500,tf);
			//ServicesDriver driver = new ServicesDriver (4,  DELTA, 3, 4, 50, 100, 4 , 1, 100, 200,tf);
			ArrayList<int[][]> rservices = driver.generateServicesForModel();
			ArrayList<Service> services = driver.convertGeneratedServices(rservices);
			ILPModelModified ilp =  new ILPModelModified(pNetwork, services, DELTA);
	    	ilp.buildILPModel();
	    	startlp = System.currentTimeMillis();
	    	ilp.runILPModel("testResults/ILP/ILPResults_1.txt", 1);
	    	endlp = System.currentTimeMillis();
	    	double time = endlp - startlp;
			System.out.print( "EXec Time"+time );
	    	/*ArrayList<Configuration> conf = ilp.buildServiceConfigurations();
	    	
	    	for (int i=0; i<conf.size(); i++)
	    	{
	    		System.out.println(conf.get(i));
	    	}*/
		}
	
}
