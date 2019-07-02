/**
 * This is the initial ILP model used for scheduling, mapping and routing of VNFs
 * THis is not based on column Generation but solved by a CG approach
 * 
 * OBJECTIVE TO MINIMIZE MAKESPAN
 * SERVICES HAS NO DEADLINES
 * 
 * THIS MODEL SHOULD BE RUN WITH THE ASSUMPTION THAT ALL SERVICES REQUEST A BANDWIDTH LESS THAN THE CAPACITY OF ALL LINKS 
 * This is because we are forcing the model to admit all services. Having a service requiring a bandwidth greater than a link 
 * may cause the model to give no solution if no route exists for the function assigned to the node that can only be reached by the link with no enough capacity
 * check manualInput() Example 6
 */
package Model;

import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import HelperClasses.FileManipulation;
import HelperClasses.Output;
import HelperClasses.ServicesDriver;
import HelperClasses.StdRandom;
import NFV.Service;
import Network.Network;

public class ILPModel {
	public IloCplex cplex;
	
	public ILPModel() throws IloException
	{
		this.cplex = new IloCplex();
	}
	
	

	/**
	 * Function for testing on the same inputs
	 * This function provides values for the parameters of the model
	 */
	public ArrayList<int[][]> manualInput()
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
	 * This is the inital model for doing routing and scheduling for VNfs
	 * IMPORTANT: when doing w/b to calculate the transmission time, those are automatically rounded down. Ex: w = 1049/b=354 =2.9 => only 2 timeslots are reserved for transmission 
	 *	For rounting constraints, we may have subtours and selection of some links that can not be included in the path. subtour elemination constraint is not added since they
	 * won't be added if they are against the objective function
	 *
	 * @param ks
	 * @param L
	 * @param F
	 * @param T
	 * @param S
	 * @param DELTA
	 * @param minCapacity
	 * @param maxCapacity
	 * @param minMiddlebox
	 * @param maxMiddlebox
	 * @param minBw
	 * @param maxBw
	 * @param maxProcessing
	 * @param minProcessing
	 * @param minTraffic
	 * @param maxTraffic
	 * @param manualInput if true use manual input for test (manualInput()); false generates random data
	 * @throws IloException
	 * @throws IOException
	 */
	public String modelFormulation(int ks, int L, int F, int T, int S,int DELTA, int minCapacity, int maxCapacity, int minMiddlebox, int maxMiddlebox,int  minBw, int maxBw, int maxProcessing, int minProcessing, int minTraffic,int maxTraffic, boolean manualInput) throws IloException, IOException
	{
		//the random connected network. This array will specify if a link exists between 2 nodes
		int[][] G;		
		
		//specifies capacity of physical links interconnecting the servers 
		int [][] cij;		
		
		//set of types on VNFs. This is formed as double array to be able to return it 
		int [] tf ;						
		
		//specifies which VNF is hosted on which server
		int [][] xfk;		
		//this contains the number of middleboxes for each service
		int[]N;
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
		
		/***************************************************************************************************************************
		 * ****************************************INITIALZE PARAMETERS*************************************************************
		 * *************************************************************************************************************************/
		if (manualInput)
		{
			ArrayList<int[][]> input = this.manualInput();
			G = input.get(0);
			cij = input.get(1);
			tf = input.get(2)[0];
			xfk = input.get(3);
			N = input.get(4)[0];
			msn = input.get(5);
			psn = input.get(6);
			bs = input.get(7)[0];
			ws = input.get(8)[0];
			us = input.get(9)[0];	//not used			
			E = input.get(10)[0];
			oe = input.get(11)[0];
			de = input.get(12)[0];			
		}
		else
		{
			Network pNetwork = new Network (F, T, ks, L, minCapacity, maxCapacity,1,0);
			ArrayList<int[][]> physicalNetwork = pNetwork.buildPhysicalNetwork();
		
			G = physicalNetwork.get(0);
			cij = physicalNetwork.get(1);
			
			//make sure to choose from the VNF types hosted in the network when generating services. 
			//tf should not be null, if null generateServices will generate services with VNf typpes not in the network
			tf = physicalNetwork.get(2)[0];
			xfk = physicalNetwork.get(3);
			
			
			ServicesDriver driver = new ServicesDriver (S, DELTA, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic,tf);
			ArrayList<int[][]> services = driver.generateServicesForModel();
		
			msn = services.get(0);
			psn = services.get(1);
			bs = services.get(2)[0];
			us = services.get(3)[0];//not used
			ws = services.get(4)[0];	
			E = services.get(5)[0];
			oe = services.get(6)[0];
			de = services.get(7)[0];
			N = services.get(8)[0];
		}
		/***************************************************************************************************************************
		 * ****************************************INITIALZE DECISION VRIABLES******************************************************
		 * *************************************************************************************************************************/
		//rho specifies the makespan earliest completion time of al the services
		IloIntVar rho =cplex.intVar(0, Integer.MAX_VALUE,"rho");
				
		//a specifies that if a service is admitted or not
		IloIntVar[] a = new IloIntVar[S];
		
		//specifies that the traffic of service s started processing at time slot delta  on middlebox n scheduled/mapped to VNF f
		IloIntVar[][][][] y = new IloIntVar[S][maxMiddlebox][F][DELTA];
		
		//specifies that the traffic of service s is processing at time slot delta  on middlebox n scheduled/mapped to VNF f
		IloIntVar[][][][] z = new IloIntVar[S][maxMiddlebox][F][DELTA];
		
		//indicates that middleboxes n, (n+1)  of service s are hosted on the same node k(1) (or not, 0).\\
		IloIntVar[][][] h = new IloIntVar[S][maxMiddlebox][ks];
		
		//specifies that the middlebox n of service s is mapped to a VNF instance hosted on physical server k (1) (or not, 0).
		IloIntVar[][][]q = new IloIntVar[S][maxMiddlebox][ks];
				
		//designates that a middlebox o(e) of service s begins the transmission of traffic to its successor middlebox d(e)  at time slot \delta  on virtual link e  (1) (or not, 0). 
		IloIntVar[][][]theta = new IloIntVar[S][DELTA][E.length];
		
		// indicates that the virtual link e is used for traffic transmission between middleboxes o(e), d(e) of service s  at time slot \delta (1)   (or not, 0).
		IloIntVar[][][]thetaHat = new IloIntVar[S][DELTA][E.length];
		
		// specifies that the virtual link e  of service s  is routed through physical link (ij)  (1) (or not, (0)).
		IloIntVar[][][]l = new IloIntVar[ks][ks][E.length];
		
		//Needed for linearization
	    IloIntVar[][][][][]g = new IloIntVar[ks][ks][S][DELTA][E.length];
	    
	
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
	    				
	    				//force y = 0 for middleboxes that does not exist for the service (n not in Ns) (Prevent bject is unknown to IloCplex)
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
	    
	    /***************************************************************************************************************************
		 * *************************************************Objective function*****************************************************
		 * *************************************************************************************************************************/
	  
	    	cplex.addMinimize(rho);
		  /***************************************************************************************************************************
		   * *************************************************Constraints*****************************************************
		   * *************************************************************************************************************************/
	    	
	    	 
	    /**
  		 * C2 :force all services to be admitted. This will allow to have value for rho and not setting it to 0
  		 * sum over s a_s = |S| 
  		 */
	    
	    IloNumExpr admitAllConstraint = cplex.numExpr();
	    //loop over services
	    for(int i =0; i<S; i++)
	    {
	    	admitAllConstraint = cplex.sum(admitAllConstraint, a[i]);
	    	
	    }
	    cplex.addEq(admitAllConstraint,S, "Const2");
	    
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
	    		
	    		cplex.addEq(middleboxMappedConstraint, a[i], "Const3_sn ["+i+"]["+j+"]");
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
	    		
	    		cplex.addEq (middleboxTypeConstraint, cplex.prod(a[i],msn[i][j]), "Const4_sn ["+i+"]["+j+"]");
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
				cplex.addLe(preventTransmissionConstraint, cplex.sum (1, cplex.prod(-1, physicalServerMappingConstraint)),"Const14_1_se ["+i+"]["+e+"]" );
				cplex.addLe(preventTransmissionConstraint, a[i],"Const13_2_se ["+i+"]["+e+"]" );
				cplex.addGe(preventTransmissionConstraint, cplex.sum(a[i],cplex.prod(-1, physicalServerMappingConstraint)),"Const14_3_se ["+i+"]["+e+"]" );
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
	    
	    /**
	     * C20: Make sure that virtual link is not mapped if the service is not admitted
	     * l[i][j][e]<= a[s] for all ij in L, for all e in Es for all s in S
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
	   		    		if (G[i][j]==1 || G[j][i]==1)// 
	   		    		{ 
	   		    			cplex.addLe(l[i][j][e],a[s], "Const20_ijse["+i+"]["+j+"]["+s+"]["+e+"]");
	   		    		}
	   		    	}
	   		    }
	    
		    }
	     }
	     cplex.exportModel("SchedulinRouting.lp");
	     String st ="==========INPUTS============\n\n";
	    
	     st+="ks = "+ks+" //nb of servers \n";
	     st+="L = "+L+" //nb of links \n";
	     st+="F = "+F+" //nb of VNFs in the network\n";
	     st+="T = "+T+" //nb of types of middleboxes: 0 is type of ingress and 1 is the egress node type. Those are considered as VNF with processing time =0 \n" ;
	     st+="S = "+S+" //nb of services \n";
	     st+="DELTA = "+DELTA+" //nb of time slots \n";
	     st+="minCapacity = "+minCapacity+" //min link capacity: if minCapacity== -1 set all the links capacity to a fixed value = max capacity\n";
	     st+="maxCapacity = "+maxCapacity+" //max link capacity \n";
	     st+="minMiddlebox = "+minMiddlebox+" //min nb of service middleboxes \n";
	     st+="maxMiddlebox = "+maxMiddlebox+" //max nb of service middleboxes \n";
	     st+="minBw = "+minBw+" //min bw\n";
	     st+="maxBw = "+maxBw+" //max bw\n";
	     st+="minProcessing = "+minProcessing+" //min Processing \n";
	     st+="maxProcessing = "+maxProcessing+" //max Processing \n";	     
	     st+="minTraffic = "+minTraffic+" //min service traffic size\n";
	     st+="maxTraffic = "+maxTraffic+" // max service traffic size\n";

	     st+= this.printInput( G, cij,tf,xfk , msn,psn, bs,ws,us , E ,oe,de, N);
	      
		if (cplex.solve()) {
			System.out.println("solved "+cplex.getObjValue());	
						
			st+="\n\n\n ==============RESULTS================\n\n";
			st+=" objective value = number of admitted Requests = "+cplex.getObjValue()+" \n\n";
			st+=this.printResults( a, y, h, q,z,theta, thetaHat, l,g);
		
			cplex.end();
		}
		return st;
	   
	}
	
	
	/**
	 *  This function will print the parameters
	 * @param G physical network
	 * @param cij link capacity
	 * @param tf type of VNfs
	 * @param xfk assignment of VNF to servers
	 * @param msn type of middleboxes of each service
	 * @param psn processing time of middleboxes of each service
	 * @param bs bandwidth of each service
	 * @param ws  traffic size of each service
	 * @param us deadline of each service
	 * @param E virtual links for each service
	 * @param oe orgin of each virtual link
	 * @param de destination of each virtual link
	 * @param N number of middlebox of each service
	 * 
	 * @ return String
	 */
	public String  printInput(int[][] G,int [][] cij, int [] tf, int [][] xfk ,int[][] msn, int [][] psn,int [] bs,int [] ws,int[] us, int [] E ,int[]oe,int []de, int[]N)
	{
		String st="";
		
		st+= Output.printDoubleMatrice(G,"GRAPH");
		st+= Output.printDoubleMatrice(cij,"PHYASICAL LINK CAPACITY");
		st+= Output.printTable(tf, "VNF TYPES");
		st+= Output.printDoubleMatrice(xfk,"VNF MAPPING TO PHYSICAL SERVERS");
		st+= Output.printTable(N, "NUMBER OF MIDDLEBOXES REQUIRED BY EACH SERVICE");
		st+= Output.printDoubleMatrice(msn,"SERVICE FUNCTION CHAINING (TYPE OF MIDDLEBOX)");
		st+= Output.printDoubleMatrice(psn,"PROCESING TIME OF MIDDLEBOXES OF EACH SERVICE");
		st+= Output.printTable(bs, "BANDWIDTH REQUIRED BY EACH SERVICE");
		st+= Output.printTable(ws, "TRAFFIC SIZE OF EACH SERVICE");
		st+= Output.printTable(us, "DEADLINE TIME OF EACH SERVICE");
		st+= Output.printTable(E, "VIRTUAL LINKS ASSIGNMENT FOR SERVICES");
		st+= Output.printTable(oe, "ORIGIN OF EACH VIRTUAL LINK");
		st+= Output.printTable(de, "DESTINATION OF EACH VIRTUAL LINK");
		
		return st;
		
	}
	
	
	public String printResults (IloIntVar[] a, IloIntVar[][][][] y, IloIntVar[][][] h, IloIntVar[][][] q, IloIntVar[][][][]z, IloIntVar[][][] theta, IloIntVar [][][] thetaHat, IloIntVar [][][]l,  IloIntVar[][][][][] g) throws UnknownObjectException, IloException
	{
			String st ="";
			
			st+="--a: SERVICE ADMITTED--\n";
			System.out.println ("--a: SERVICE ADMITTED--") ;
			for(int i =0; i<a.length; i++)
		    {
		    	System.out.println (cplex.getValue(a[i])+" ");
		    	st+=cplex.getValue(a[i])+" \n";
		    }
			
			st+= "--y: SERVICE STARTED PROCESSING--\n";
			System.out.println ("--y: SERVICE STARTED PROCESSING--") ;
		    //loop over services
		    for(int i =0; i<y.length; i++)
		    {
		    	 //loop over middleboxes
		    	for (int j=0; j<y[i].length; j++)
		    	{
		    		 //loop over VNF
		    		for (int f=0; f<y[i][j].length; f++)
			    	{
		    			 //loop over time slots
		    			for (int delta=0; delta<y[i][j][f].length; delta++)
				    	{
		    				if(cplex.getValue( y[i][j][f][delta])==1)
		    				{
		    					System.out.println ("y["+i+"]["+j+"]["+f+"]["+delta+"]: "+cplex.getValue( y[i][j][f][delta]));			    				
		    					st+= "y["+i+"]["+j+"]["+f+"]["+delta+"]: "+cplex.getValue( y[i][j][f][delta])+" \n";
		    				}
				    	}
			    	}
		    	}	    			
		    }
		    
		    System.out.println ("--h: consecutive middlebox hosted on same server--");
			st+="--h: consecutive middlebox hosted on same server--\n";
		    //loop over services
		    for(int i =0; i<h.length; i++)
		    {
		    	 //loop over middleboxes
		    	for (int j=0; j<h[i].length; j++)
		    	{
		    		for(int k=0; k<h[i][j].length; k++)
		    		{
		    			if (cplex.getValue(h[i][j][k])==1)
		    			{
		    				System.out.println ("h["+i+"]["+j+"]["+k+"] :"+cplex.getValue(h[i][j][k]));			
		    				st+="h["+i+"]["+j+"]["+k+"] :"+cplex.getValue(h[i][j][k])+"\n";
		    			}
		    		}
		    		
		    	}
		    }
		    
		    System.out.println ("--q: SERVICE MIDDLEBOX MAPPED TO SERVER--");
		    st+="--q: SERVICE MIDDLEBOX MAPPED TO SERVER--\n";
		    //loop over services
		    for(int i =0; i<q.length; i++)
		    {
		    	 //loop over middleboxes
		    	for (int j=0; j<q[i].length; j++)
		    	{
		    		//loop over servers
		    		for (int k=0; k<q[i][j].length; k++)
			    	{
		    			if(cplex.getValue(q[i][j][k]) ==1)
		    			{
		    				System.out.println ("q["+i+"]["+j+"]["+k+"] :"+cplex.getValue(q[i][j][k]));
		    				st+="q["+i+"]["+j+"]["+k+"] :"+cplex.getValue(q[i][j][k])+"\n";
		    			}		    			
		    			
			    	}
		    	}
		    }
		    
			
			st+= "--z: SERVICE IS PROCESSING--\n";
			System.out.println ("--z: SERVICE IS PROCESSING--") ;
		    //loop over services
		    for(int i =0; i<z.length; i++)
		    {
		    	 //loop over middleboxes
		    	for (int j=0; j<z[i].length; j++)
		    	{
		    		 //loop over VNF
		    		for (int f=0; f<z[i][j].length; f++)
			    	{
		    			 //loop over time slots
		    			for (int delta=0; delta<z[i][j][f].length; delta++)
				    	{
		    				if(cplex.getValue( z[i][j][f][delta])==1)
		    				{
		    					System.out.println ("z["+i+"]["+j+"]["+f+"]["+delta+"]: "+cplex.getValue( z[i][j][f][delta]));	
		    					st+= "z["+i+"]["+j+"]["+f+"]["+delta+"]: "+cplex.getValue( z[i][j][f][delta])+" \n";
		    				}
		    				
				    	}
			    	}
		    	}	    			
		    }
		    
		   System.out.println ("--theta: SERVICES STARTED TRANSMISSION--");
		    st+="--theta: SERVICES STARTED TRANSMISSION--\n";
		    //loop over services s
		    for(int i =0; i<theta.length; i++)
		    {
		    	//loop over timeslots
				for (int delta=0; delta<theta[i].length; delta++)
		    	{
		    		//loop over edges
		    		for (int e=0; e<theta[i][delta].length; e++)
			    	{
		    			if(cplex.getValue(theta[i][delta][e] ) ==1)
		    			{
		    				System.out.println ("theta["+i+"]["+delta+"]["+e+"] :"+cplex.getValue(theta[i][delta][e] ));
		    				st+="theta["+i+"]["+delta+"]["+e+"] :"+cplex.getValue(theta[i][delta][e] )+"\n";
		    			}
		    			
			    	}
		    	}
		    }
		    
		     System.out.println ("--thetaHat: SERVICES IS TRANSMITTING--");
		    st+="--thetaHat: SERVICES IS TRANSMITTING--\n";
		    //loop over services s
		    for(int i =0; i<thetaHat.length; i++)
		    {
		    	//loop over timeslots
				for (int delta=0; delta<thetaHat[i].length; delta++)
		    	{
		    		//loop over edges
		    		for (int e=0; e<thetaHat[i][delta].length; e++)
			    	{
		    			if(cplex.getValue(thetaHat[i][delta][e])==1)
		    			{
		    				System.out.println ("thetaHat["+i+"]["+delta+"]["+e+"] :"+cplex.getValue(thetaHat[i][delta][e]));
		    				st+="thetaHat["+i+"]["+delta+"]["+e+"] :"+cplex.getValue(thetaHat[i][delta][e])+"\n";
		    			}
		    			
			    	}
		    	}
		    }
		    
		    System.out.println ("--l: LINKS MAPPING--");
		    st+="--l: LINKS MAPPING--\n";
		    //loop over servers i
		    for(int i =0; i<l.length; i++)
		    {
		    	//loop over servers j
				for (int j=0; j<l[i].length; j++)
		    	{
				   		//loop over edges
			    		for (int e=0; e<l[i][j].length; e++)
				    	{
			    			try
			    			{
				    			if(cplex.getValue(l[i][j][e])==1)
				    			{
				    				System.out.println ("l["+i+"]["+j+"]["+e+"] :"+cplex.getValue(l[i][j][e]));
				    				st+="l["+i+"]["+j+"]["+e+"] :"+cplex.getValue(l[i][j][e])+"\n";
				    			}
				    			
				    		}
			    			catch(IloException x) {}
				    	}	    	
		    		}
		    	}

		    
		    System.out.println ("--g: LINEARIZATION--");
		    st+="--g: LINEARIZATION--\n";
		    //loop over middleboxes
		    for(int i =0; i<g.length; i++)
		    {
		    	//loop over servers j
				for (int j=0; j<g[i].length; j++)
		    	{
					//loop over services
					for (int s=0; s<g[i][j].length; s++)
			    	{
						//loop over VNF f
						for (int delta=0; delta<g[i][j][s].length; delta++)
				    	{
				    		//loop over  VNF f'
				    		for (int e=0; e<g[i][j][s][delta].length; e++)
					    	{
				    			try
				    			{
				    				if(cplex.getValue( g[i][j][s][delta][e])==1)
				    				{
				    					System.out.println ("g["+i+"]["+j+"]["+s+"]["+delta+"]["+e+"] :"+cplex.getValue( g[i][j][s][delta][e])); 
				    					st+="g["+i+"]["+j+"]["+s+"]["+delta+"]["+e+"] :"+cplex.getValue( g[i][j][s][delta][e])+"\n";
				    				}
				    				
						    	}
								catch(IloException x) {}
						    }
				    	}
			    	}
				
		    	}
		    }
		    
		    return st;
	}
	
	public static void main(String[] args) throws IloException, IOException {
		// TODO Auto-generated method stub
		String st = "";
		String s = "";
		long startTime;
		long endTime;
		long executionTime;	
		String fileName = "SchedulingRoutingModelResults.txt";	
		
		/*int ks = 24;
		int L = 30;
		int F = 30;
		int T = 20;
		int S = 30;
		int DELTA = 100;
		int minCapacity = -1;
		int maxCapacity = 1000;
		int minMiddlebox = 3;
		int maxMiddlebox = 5;
		int minBw = 10;
		int maxBw = 500;
		int maxProcessing =4;
		int minProcessing = 1;
		int minTraffic = 500;
		int maxTraffic = 1500;*/
	
		/*int ks = 5;//10;
		int L = 7;//14;
		int F =15;
		int T = 10;
		int S = 3;//15;*/
		//manual input
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
		
		System.out.println("Executing");
		FileManipulation fileManipulation = new FileManipulation(fileName);		
		ILPModel model = new ILPModel();
		startTime = System.currentTimeMillis();
		s= model.modelFormulation(ks, L, F, T, S, DELTA, minCapacity, maxCapacity, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic, false);
		endTime = System.currentTimeMillis();
		executionTime = endTime - startTime;	
		st+= "==================ExecutionTime===========================\nExecutionTime =" +executionTime+"\n\n"+s;
		fileManipulation.writeInFile(st);

	}

}
