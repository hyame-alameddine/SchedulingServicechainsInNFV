/**
 * This the master model that would ensure accurate resource management of the network in terms of:
 * 1- Link capacity constraints
 * 2- Each VNF processing one service at a time
 * 3- One column is selected for each service
 * 
 * The master model receives a configuration(mapping, scheduling and routing) for each service at each iteration
 * It will choose the combination of configurations to maximize the admitted services.
 *
 */
package Model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import HelperClasses.FileManipulation;
import HelperClasses.IloNumVarArray;
import HelperClasses.Output;
import ilog.concert.IloColumn;
import ilog.concert.IloColumnArray;
import ilog.concert.IloCopyManager;
import ilog.concert.IloCopyable;
import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.concert.IloCopyManager.Check;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.UnknownObjectException;

public class CGMasterModel {

	public IloCplex cplex;
	
	//set to -1 by default if the model was not solved
	public double lpObjectiveValue;
	public double IlpObjectiveValue;
	//ranges
	public int initialColumnsSet; //c
	public int servicesNb;//s
	public int timeslotsNb;//delta
	public int linksNb;//ij
	public int VNFNb;
	
	//parameters
	public int[][] v ;//sc
	public int[][][][] r ;//fsdeltac
	public int[][][][]o;//lsdeltac
	public int[]cl;
	
	//Decision variable
	public IloRange[] serviceRange;
	public IloRange[][] VNFRange;
	public IloRange[][] linkCapacityRange;
	//public IloRange[][] makespanRange;//when we want to have rho>=v_s^c lambda_s^c for all c for all s
	public IloRange[]makespanRange;//when we want to have rho>=sum over c v_s^c lambda_s^c for all s

	public IloObjective mincompletionTimeObj;
	public IloColumn  objColumn;
	//this will hold the values for lambda_s^c, The value for rho can be got by getting the value of the objective function
	public IloNumVarArray [] cut;
	public IloNumVar rho;//this is to be able to get rho only to be able to convert it to int (check run ILP)
	
	
	//this will hold all the columns that are added to the master 
	public ArrayList <Configuration> columns;
	public ArrayList <PricingSolution> pricingSolutions; //solutions of the pricing corresponds to the columns but does not include any diversifications solutions
	
	//this is used for diversification and will be populated in the incumbent callback. These will be added the columns attributed when the addColumnToMaster is called
	public ArrayList <Configuration> incumbentColumns;
	
	//Dual values	
	public double	[] alpha;
	//dual variable associated with VNF constraint of the master problem
	public double [][]beta;//f delta
	//dual variable associated with the link capacity constraint of the master problem
	public double [][]gamma;//l delta
	//dual variable associated with the makespan constraint of the master problem
	public double [] phi;
	 
	public CGMasterModel( int initialColumnsSet, int servicesNb, int timeslotsNb, int linksNb,int VNFNb,int[][] v, int[][][][] r, int[][][][]o, int[] cl) throws IloException
	{
		this.cplex = new IloCplex();
		this.lpObjectiveValue =-1;//set to -1 by default 
		this.IlpObjectiveValue =-1;//set to -1 by default 
		
		this.initialColumnsSet = initialColumnsSet;
		this.servicesNb = servicesNb;
		this.timeslotsNb = timeslotsNb;
		this.linksNb = linksNb;
		this.VNFNb = VNFNb;
		
		this.v = v;
		this.r = r;
		this.o = o;	
		this.cl=cl;
		
		 cut = new IloNumVarArray[this.servicesNb];
		 for(int i=0;i<this.servicesNb;i++)
		 {
			 cut[i] = new IloNumVarArray();
		 }
		 
		 this.columns = new ArrayList<Configuration>();
		 this.pricingSolutions = new ArrayList<PricingSolution>();
		 this.incumbentColumns = new ArrayList<Configuration>();
	}
	
	
	/**
	 * This function will build the master model
	 * 
	 * @throws IloException
	 */
	public void buildMaster() throws IloException
	{
		//dealing with objective function
		mincompletionTimeObj = cplex.addMinimize();

		//Populate by Column to enable column generation.   
	    /**
	     * Hyame: initializing right hand side of each constraint. 
	     * Each constraint is represented by a range where you can specify if it is less, greater or equal to a certain constant value that you specify by the range
	     */
		this.serviceRange = new IloRange[this.servicesNb];
		this.VNFRange = new IloRange [this.VNFNb][this.timeslotsNb];
		this.linkCapacityRange   = new IloRange [this.linksNb][this.timeslotsNb];
	//	this.makespanRange   = new IloRange [this.servicesNb][this.initialColumnsSet];//rho >= v_s^clambda_s^c for all c,for all s
		this.makespanRange   = new IloRange [this.servicesNb];
		
		//initializing columns
		//represents sum over c lambda_s^c = 1 for all s, here we are setting that it to = 1
		for(int i=0;i<this.servicesNb;i++)
		{
			serviceRange[i] = cplex.addRange(1.0,Double.MAX_VALUE);
  		}
 		
		//sum over c, over s, r_fs^deltac lambda_s^c <= 1 for all f in F, for all delta in Delta, here we are setting that it to <= 1 
		for(int i=0;i<this.VNFNb;i++)
		{
			for (int j=0; j<this.timeslotsNb;j++)
			{
				this.VNFRange[i][j] = cplex.addRange(-Double.MAX_VALUE,1.0);	
			}
  		}
		
		//sum over c over s o_ls^deltac lambda_s^c <=c_l for all delta, forall l in L,  here we are setting that it to <= c_l 
		for(int i=0;i<this.linksNb;i++)
		{
			for (int j=0; j<this.timeslotsNb;j++)
			{
				this.linkCapacityRange[i][j] = cplex.addRange(-Double.MAX_VALUE,this.cl[i]);
			}
  		}
		
		//rho >= v_s^clambda_s^c for all c, for all s => this is implemented as -v_s^c lambda_s^c +rho >=0
	/*	for(int i=0;i<this.servicesNb;i++)
		{
			for (int c=0; c<this.initialColumnsSet; c++)
			{
				makespanRange[i][c] = cplex.addRange(0,Double.MAX_VALUE);//setting constraint to be >=0	
			}
		}*/
		
		//rho >= sum over c v_s^c lambda_s^c  for all s => this is implemented as -sum over c v_s^c lambda_s^c +rho >=0
		for(int i=0;i<this.servicesNb;i++)
		{
			makespanRange[i]= cplex.addRange(0,Double.MAX_VALUE);//setting constraint to be >=0	
			
		}
		

			
		 //specifying that coefficient of rho in objective function is 1
		objColumn = cplex.column(mincompletionTimeObj,1);
		
		 //setting up the constraints				
		 for(int s=0;s<this.servicesNb;s++)
    	 {
			 
			 //creating one column for each service (for the service admission constraint)
			 IloColumn  serviceColumnAdmission = cplex.column(this.serviceRange[s],1);  //setting coefficient of lambda to be =1 
			
			//completion time constraint
			 IloColumn  makespanColumn = cplex.column(this.makespanRange[s],-this.v[s][0]);//rho >= sum over c v_s^clambda_s^c ,for all s
			 
			//this.initialColumnsSet is the number of columns of the first feasible solution got before running the pricing.
			//When running the master again (after pricing), we just add column and solve() it. no need to loop over all the column
			for (int c=0; c<this.initialColumnsSet; c++)
			{	  				 				 
				//VNF management constraint
				 IloColumn  VNFColumn = cplex.column(this.VNFRange[0][0],this.r[0][s][0][c]);		
	    		 for(int f=0;f<this.VNFNb;f++)
		    	 {
	    			 for(int t=1;t<this.timeslotsNb;t++)
			    	 {
	    				 VNFColumn = VNFColumn.and(cplex.column(this.VNFRange[f][t],this.r[f][s][t][c] ));
			    	 }
		    			
		    	 }
	    		 
	    		 //link capacity constraint
	    		 IloColumn  linkColumn = cplex.column(this.linkCapacityRange[0][0],this.o[0][s][0][c]);	    		 	
	    		 for (int l=0;l<this.linksNb; l++ )
		    	 {
	    			 for(int t=1;t<this.timeslotsNb;t++)
	    			 {
	    				 linkColumn = linkColumn.and(cplex.column(this.linkCapacityRange[l][t],o[l][s][t][c]));
	    			 }
		    	 } 
	    		
	    		//completion time constraint
				// IloColumn  makespanColumn = cplex.column(this.makespanRange[s][c],-this.v[s][c]); //needed when having the constraint for all s for all c
	    		 if (c!=0)
	    			 makespanColumn = makespanColumn.and(cplex.column(this.makespanRange[s],-this.v[s][c]));//needed when having the constraint for all s sum over c
	    		  
	    	 	//adding one column to the master	for this specified service
	    		 cut[s].add(cplex.numVar(serviceColumnAdmission.and(VNFColumn).and(linkColumn).and(makespanColumn),0,1,"lambda"+s+c));//creating the lambda variable and adding to the constraints
	    		
	    		 //adding rho to each makespan constraint and adding it to the objective column so we can use this latter to create rho (one variable outside the service, column loop)
	 			//objColumn = objColumn.and(cplex.column(this.makespanRange[s][c], 1.0));	    //needed when having the constraint for all s for all c
	 			    
	    	 } 			
			objColumn = objColumn.and(cplex.column(this.makespanRange[s], 1.0));//needed when having the constraint for all s sum over c
		} 
		 
		 /**
		  * this will create the variable rho and add it to the objective function, and to each makespan column
		  */
		rho = cplex.numVar(objColumn,0 , Integer.MAX_VALUE , "rho"); 
		 
		 //setting the algorithm to use
		 cplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Primal);
	}
	
	
	
	
	/**
	 * This function will build the master model by setting up some resources as used by the configurations
	 * passed as parameter, in addition to setting all the resources as used before the specified start time slot
	 * 
	 * This is mainly used for the online arrival of services
	 * @see OnlineScheduling
	 * @param configurations list of configuration using certain resources that can not be used by current model
	 * @param startTimelsot 
	 * 
	 * @throws IloException
	 */
	public void buildMaster(ArrayList<Configuration>configurations, int startTimelsot) throws IloException
	{
		//dealing with objective function
		mincompletionTimeObj = cplex.addMinimize();

		//Populate by Column to enable column generation.   
	    /**
	     * Hyame: initializing right hand side of each constraint. 
	     * Each constraint is represented by a range where you can specify if it is less, greater or equal to a certain constant value that you specify by the range
	     */
		this.serviceRange = new IloRange[this.servicesNb];
		this.VNFRange = new IloRange [this.VNFNb][this.timeslotsNb];
		this.linkCapacityRange   = new IloRange [this.linksNb][this.timeslotsNb];
		this.makespanRange   = new IloRange [this.servicesNb];
		
		//initializing columns
		//represents sum over c lambda_s^c = 1 for all s, here we are setting that it to = 1
		for(int i=0;i<this.servicesNb;i++)
		{
			serviceRange[i] = cplex.addRange(1.0,Double.MAX_VALUE,"const_serv_range_"+i);
  		}
 		
		//sum over c, over s, r_fs^deltac lambda_s^c <= 1 for all f in F, for all delta in Delta, here we are setting that it to <= 1 
		this.setUpVNFRange(configurations, startTimelsot);
		
		//sum over c over s o_ls^deltac lambda_s^c <=c_l for all delta, forall l in L,  here we are setting that it to <= c_l 
		this.setUpLinkRange(configurations, startTimelsot);
		
		//rho >= sum over c v_s^c lambda_s^c  for all s => this is implemented as -sum over c v_s^c lambda_s^c +rho >=0
		for(int i=0;i<this.servicesNb;i++)
		{
			makespanRange[i]= cplex.addRange(0,Double.MAX_VALUE,"const_makespan_range_"+i);//setting constraint to be >=0				
		}
		
			
		 //specifying that coefficient of rho in objective function is 1
		objColumn = cplex.column(mincompletionTimeObj,1);
		
		 //setting up the constraints				
		 for(int s=0;s<this.servicesNb;s++)
    	 {
			 
			 //creating one column for each service (for the service admission constraint)
			 IloColumn  serviceColumnAdmission = cplex.column(this.serviceRange[s],1);  //setting coefficient of lambda to be =1 
			
			//completion time constraint
			 IloColumn  makespanColumn = cplex.column(this.makespanRange[s],-this.v[s][0]);//rho >= sum over c v_s^clambda_s^c ,for all s
			 
			//this.initialColumnsSet is the number of columns of the first feasible solution got before running the pricing.
			//When running the master again (after pricing), we just add column and solve() it. no need to loop over all the column
			for (int c=0; c<this.initialColumnsSet; c++)
			{	  				 	
				//VNF management constraint
				 IloColumn  VNFColumn = cplex.column(this.VNFRange[0][0],this.r[0][s][0][c]);		
	    		 for(int f=0;f<this.VNFNb;f++)
		    	 {
	    			 for(int t=1;t<this.timeslotsNb;t++)
			    	 {
	    				 VNFColumn = VNFColumn.and(cplex.column(this.VNFRange[f][t],this.r[f][s][t][c] ));
			    	 }
		    			
		    	 }
	    		 
	    		 //link capacity constraint
	    		 IloColumn  linkColumn = cplex.column(this.linkCapacityRange[0][0],this.o[0][s][0][c]);	    		 	
	    		 for (int l=0;l<this.linksNb; l++ )
		    	 {
	    			 for(int t=1;t<this.timeslotsNb;t++)
	    			 {
	    				 linkColumn = linkColumn.and(cplex.column(this.linkCapacityRange[l][t],o[l][s][t][c]));
	    			 }
		    	 } 
	    		
	    		//completion time constraint
				// IloColumn  makespanColumn = cplex.column(this.makespanRange[s][c],-this.v[s][c]); //needed when having the constraint for all s for all c
	    		 if (c!=0)
	    			 makespanColumn = makespanColumn.and(cplex.column(this.makespanRange[s],-this.v[s][c]));//needed when having the constraint for all s sum over c
	    		  
	    	 	//adding one column to the master	for this specified service
	    		cut[s].add(cplex.numVar(serviceColumnAdmission.and(VNFColumn).and(linkColumn).and(makespanColumn),0,1,"lambda"+s+c));//creating the lambda variable and adding to the constraints
	    		
	    	 } 	
			
			objColumn = objColumn.and(cplex.column(this.makespanRange[s], 1.0));//needed when having the constraint for all s sum over c
		} 
		 
		 /**
		  * this will create the variable rho and add it to the objective function, and to each makespan column
		  */
		 rho = cplex.numVar(objColumn,0 , Integer.MAX_VALUE , "rho"); 
		 
		 //setting the algorithm to use
		 cplex.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Primal);
	}
	
	
	
	/**
	 * This method used to set up the VNF range as follows:
	 * set the const of VNf use to be <=0 for all delta <startTimeslot and for all delta >=startTimeslot where the VNF is 
	 * used by one of the configurations
	 * 
	 * @param configurations configuration using the resources
	 * @param startTimelsot timeslot at which the schedule of the services should start
	 * 
	 * @throws IloException
	 */
	public void setUpVNFRange (ArrayList<Configuration>configurations, int startTimelsot) throws IloException
	{
		//sum over c, over s, r_fs^deltac lambda_s^c <= 1 for all f in F, for all delta in Delta, here we are setting that it to <= 1 
		for(int i=0;i<this.VNFNb;i++)
		{
			//prevent the use of the VNF by any service before the startTimeslot
			for (int j=0; j<startTimelsot;j++)
			{ 
				this.VNFRange[i][j] = cplex.addRange(-Double.MAX_VALUE,0,"const_vnf_range_"+i+"_"+j);
			}
			
			for (int j=startTimelsot; j<this.timeslotsNb;j++)
			{
				//check if the VNF is used at the specified timeslot and prevent its use so set the const<=0
				if (!this.isVNFAvailable(i, j, configurations))
				{
					this.VNFRange[i][j] = cplex.addRange(-Double.MAX_VALUE,0,"const_vnf_range_"+i+"_"+j);	
				}
				else
				{	
					//set the const <=1 if the VNf can be used
					this.VNFRange[i][j] = cplex.addRange(-Double.MAX_VALUE,1.0,"const_vnf_range_"+i+"_"+j);	
				}
			}
  		}
	}
	
	
	/**
	 * This function will set up the linkCapacity range by setting it to <=0 for all delta<startTimeslot
	 * and <= the bandwidth available for all delta >=startTimeslot based on its use by the configurations passed as parameter
	 * 
	 * @param configurations configuration that may be using the links
	 * @param startTimelsot timeslot at which the services schedule should start
	 * @throws IloException
	 */
	public void setUpLinkRange (ArrayList<Configuration>configurations, int startTimelsot) throws IloException 
	{
		int a = 0;
		//sum over c over s o_ls^deltac lambda_s^c <=c_l for all delta, forall l in L,  here we are setting that it to <= c_l 
		for(int i=0;i<this.linksNb;i++)
		{
			//prevent the use of the link by any service before the startTimeslot
			for (int j=0; j<startTimelsot;j++)
			{
				this.linkCapacityRange[i][j] = cplex.addRange(-Double.MAX_VALUE,0,"const_link_range_"+i+"_"+j);
			}
			
			for (int j=startTimelsot; j<this.timeslotsNb;j++)
			{
				a= this.getLinkAvailability(i,j,configurations);
				
				this.linkCapacityRange[i][j] = cplex.addRange(-Double.MAX_VALUE,a,"const_link_range_"+i+"_"+j);
				a=0;
			}
			
  		}
	}
	
	/**
	 * This method will check if a VNF is used at a certain timeslot by one of the configuration passed as paramter
	 * 
	 * @param VNF VNf to check if it is used
	 * @param timeslot the timeslot at which we want to check if VNf is used
	 * @param configurations the configurations that may be using the VNf
	 * 
	 * @return true if the VNF is available at the specified timeslot. False otherwise
	 */
	public boolean isVNFAvailable (int VNF,int timeslot, ArrayList<Configuration>configurations)
	{
		Configuration c;
		
		for(int i=0; i<configurations.size(); i++)
		{
			c= configurations.get(i);
			
			/**
			 * The configurations may be of different length and related to different run of the master
			 * each with different DELTA (timeline). So if the timeslot is >than the length of r we 
			 * consider that the VNF is available
			 * @see OnlineScheduling->OnlineCG 
			 */
			if(c.getR()[VNF].length<=timeslot)
			{
				return true;
			}
			
			if (c.getR()[VNF][timeslot] == 1)
			{
				return false;
			}
			
		}
		return true;
	}
	
	
	/**
	 * This method returns the bandwidth available on a link at a specified timeslot by checking 
	 * if it is used by the configurations
	 * 
	 * @param link link to get its available bandwidth
	 * @param timeslot timeslot at which we want to get the bandwidth available on the link
	 * @param configurations configurations that may be using the link
	 * 
	 * @return bandwidth available on the link at the specified timeslot. It will return its capacity (cl) if not used
	 */
	public int getLinkAvailability (int link, int timeslot, ArrayList<Configuration>configurations)
	{
		Configuration c;
		int availableBw = this.cl[link];
		int usedBw =0;
		for(int i=0; i<configurations.size(); i++)
		{
			c= configurations.get(i);
			/**
			 * The configurations may be of different length and related to different run of the master
			 * each with different DELTA (timeline). So if the timeslot is >than the length of o we 
			 * consider that the link is not used by this configuration
			 * @see OnlineScheduling->OnlineCG 
			 */
			if(c.getO()[link].length<=timeslot)
			{
				usedBw =0;
			}
			else
			{
				usedBw = c.getO()[link][timeslot];
			}
			availableBw = availableBw -usedBw;
		
			//reset to 0
			usedBw =0;
			
		}

		return availableBw;
	}
	
	/**
	 * This method takes a column provided by the pricing and adds it to the master 
	 * This will set the IdPerService for the column 
	 * 
	 * @param c column for a service to be added to the pricing
	 * @throws IloException
	 */
	public void addColumnToMaster(Configuration c) throws IloException{
		
		int serviceId = c.getService().getId();
		//this should not be = c.getId(), because the configuration ids are incremental regardless of the service. Conf Id = total number of columns generated-1
		int columnId = cut[serviceId].getSize();
		
		//update the id per service to be able to track the column if selected on lambda
		c.setIdPerService(this.cut[serviceId].getSize() );
		//start by adding the column to the columns array in the master
		this.columns.add(c);
		
		 //creating one column for each service (for the service admission constraint)
		 IloColumn  serviceColumnAdmission = cplex.column(this.serviceRange[serviceId],1);  //setting coefficient of lambda to be =1 
		 
		//VNF management constraint
		 IloColumn  VNFColumn = cplex.column(this.VNFRange[0][0],c.getR()[0][0]);		
		 for(int f=0;f<this.VNFNb;f++)
		 {
			 for(int t=1;t<this.timeslotsNb;t++)
	    	 {
				 VNFColumn = VNFColumn.and(cplex.column(this.VNFRange[f][t],c.getR()[f][t] ));
	    	 }
   			
   	 	}
		 
		 //link capacity constraint
		 IloColumn  linkColumn = cplex.column(this.linkCapacityRange[0][0],c.getO()[0][0]);	    		 	
		 for (int l=0;l<this.linksNb; l++ )
		 {
			 for(int t=1;t<this.timeslotsNb;t++)
			 {
				 linkColumn = linkColumn.and(cplex.column(this.linkCapacityRange[l][t],c.getO()[l][t]));
			 }
		 } 
		
		//completion time constraint	
		// this.updateMakespanRange();
		// IloColumn  makespanColumn = cplex.column(this.makespanRange[serviceId][columnId],-c.getV());	//@check	
		 IloColumn makespanColumn = cplex.column(this.makespanRange[serviceId],-c.getV());
	 	//adding one column to the master for this specified service
		cut[serviceId].add(cplex.numVar(serviceColumnAdmission.and(VNFColumn).and(linkColumn).and(makespanColumn),0,1,"lambda"+serviceId+columnId));//creating the lambda variable and adding to the constraints
		
		 /**
		  * Test if rho is added , if not set OjColumn as attribute and add the below column to it. Or at each call reinitialize the objective and the creation of rho
		  */
		 //adding rho to each makespan constraint and adding it to the objective column so we can use this latter to create rho (one variable outside the service, column loop)
		//this.objColumn = this.objColumn.and(cplex.column(this.makespanRange[serviceId][columnId], 1.0));
		
		cplex.column(this.makespanRange[serviceId], 1.0);
	}

	
	/**
	 * This function will add all the configurations passed as parameter to the master 
	 * These are added if they do not already exists in the master.
	 * addColumnToMaster will also add these columns to the columns array
	 * 
	 * @param diversificationConfigurations list of configurations to add to the master
	 * @throws IloException
	 */
	public void addColumnsToMaster( ArrayList<Configuration> diversificationConfigurations) throws IloException
	{
		Configuration c = null;

		for (int i=0; i<diversificationConfigurations.size(); i++)
		{
			c= diversificationConfigurations.get(i);
		
			if(!this.columnExists(c))
			{
				this.addColumnToMaster(c);
			}
		}
	}
	
	/**
	 * this function will set an initialize the master dual values
	 * @throws UnknownObjectException
	 * @throws IloException
	 */
	public void initializeAndSetDuals() throws UnknownObjectException, IloException
	{
		this.alpha = new double [this.serviceRange.length];
		this.beta = new double [this.VNFRange.length][this.VNFRange[0].length];
		this.gamma = new double [this.linkCapacityRange.length][this.linkCapacityRange[0].length];
		this.phi = new double [this.makespanRange.length];
		
		for (int i=0; i<this.serviceRange.length; i++)
		{
			this.alpha[i] = this.cplex.getDual(this.serviceRange[i]);
		}
		
		for (int i=0; i<this.VNFRange.length; i++)
		{
			for (int j=0; j<this.VNFRange[i].length; j++)
			{
				this.beta[i][j] = this.cplex.getDual(this.VNFRange[i][j]);
			}
		}
		
		for (int i=0; i<this.linkCapacityRange.length; i++)
		{
			for (int j=0; j<this.linkCapacityRange[i].length; j++)
			{
				this.gamma[i][j] = this.cplex.getDual(this.linkCapacityRange[i][j]);
			}
		}
		
		for (int i=0; i<this.makespanRange.length; i++)
		{
			this.phi[i] = this.cplex.getDual(this.makespanRange[i]);
		}
	
	}
	
	/**
	 * This method run the master model and should print or report the result
	 * @param int iteration number of times the master has been run 
	 * @param filePath path to the file where to write the results
	 */
	public void runMasterModel(String filePath, int iteration )
	{
		try {
			//this.cplex.exportModel("masterModel_"+iteration+".lp");//master/masterModel_"+iteration+".lp"
			
			if(this.cplex.solve())
			{			
				//Print the makespan rho
				//System.out.println("Completion time rho= "+this.cplex.getObjValue());
				this.lpObjectiveValue =this.cplex.getObjValue();
				
				//set and initialize the dual values to be used by the pricing
				this.initializeAndSetDuals();
				
				//print initial inputs and results to a file
				this.reportResults(filePath);				
				 
			}
			else
			{
				this.lpObjectiveValue =-1;
			}

			//do not end cplex here especially if we want to run master ILP
			
		} catch (IloException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}					
	}
	
	
	/**
	 * This function returns true if a configuration is already added to the master model (configuration exists in the columns attributes)
	 * @param c configuration to check if it exists
	 * @return true if the column already added to master
	 */
	public boolean columnExists (Configuration c)
	{
		for (int i=0; i<this.columns.size(); i++)
		{
			if(this.columns.get(i).equals(c))
			{
				return true;
			}
		}
		
		return false;
	}
		
	
	/**
	 * This method will change the decision variables of the model to ILP 
	 * Used for the last run of the model
	 * 
	 * @param filePath path to write the results of the ILP
	 * @throws IloException
	 */
	public void runILPMasterModel(String filePath) throws IloException{
			
		//Change the Variables of the master into ILPs and run the model to round up the results!
		   for ( int i = 0; i < this.cut.length; i++ ) {
			   for ( int j = 0; j < this.cut[i].getSize(); j++ ) 
			   {
				// if no column was added for the service at an iteration we are setting cut[i].add(null) so we can keep the iteration of pricing = to id of column
				   if(this.cut[i].getElement(j)!=null)
					   this.cplex.add(this.cplex.conversion(cut[i].getElement(j), IloNumVarType.Int));
			   } 
		   }
		   this.cplex.add(this.cplex.conversion(rho, IloNumVarType.Int));
		  
		   if (this.cplex.solve())
		   {
			 //  this.cplex.exportModel("masterModel_ILP.lp");//"master/masterModel_ILP.lp"
			   this.reportResults(filePath);
			   this.IlpObjectiveValue = this.cplex.getObjValue();
					   
			   System.out.println("Optimal Solution "+this.cplex.getObjValue()); 
			   
			
		   }
		   else
		   {
			   this.IlpObjectiveValue = -1;
		   }
		  // this.cplex.end();
	}
		
	
	/**
	 * This function will return an array of configurations that were selected by the ILP solution 
	 * 
	 * @return configurations of the ILP solution
	 * @throws UnknownObjectException
	 * @throws IloException
	 */
	public ArrayList<Configuration> getILpSolution() throws UnknownObjectException, IloException
	{
		ArrayList<Configuration> ilpConfigurations = new ArrayList<Configuration>();
		
		for (int i =0; i<this.servicesNb; i++)
		{
			for (int j =0; j<this.cut[i].getSize(); j++)
			{
				// if no column was added for the service at an iteration we are setting cut[i].add(null) so we can keep the iteration of pricing = to id of column
				if(this.cut[i].getElement(j)==null)
				{
					continue;
				}
				
				if(this.cplex.getValue(this.cut[i].getElement(j))==1)
				{
					ilpConfigurations.add(this.getConfiguration(i,j));
				}
			}
		}
		
		return ilpConfigurations;
	}
	
	/**
	 * This function will return an array of PricingSolution that were selected by the ILP solution 
	 * 
	 * @return PricingSolution of the ILP solution
	 * @throws UnknownObjectException
	 * @throws IloException
	 * @throws IOException 
	 */
	public ArrayList<PricingSolution> getILpSolutionByPricingSolution() throws UnknownObjectException, IloException, IOException
	{
		ArrayList<PricingSolution> ilpSolutions = new ArrayList<PricingSolution>();
		FileManipulation f = new FileManipulation("testResults/PricingSlution.txt");
		String st="";
		for (int i =0; i<this.servicesNb; i++)
		{
			for (int j =0; j<this.cut[i].getSize(); j++)
			{
				// if no column was added for the service at an iteration we are setting cut[i].add(null) so we can keep the iteration of pricing = to id of column
				if(this.cut[i].getElement(j)==null)
				{
					continue;
				}
				
				if(this.cplex.getValue(this.cut[i].getElement(j))==1)
				{
				
					ilpSolutions.add(this.getPricingSolution(i,j));
					st+=this.getPricingSolution(i,j);
				}
			}
		}
		f.writeInFile(st);
		
		return ilpSolutions;
	}
	
	/**
	 * This function will return a specific configuration for a certain service.
	 * It is mainly used to check the selected configuration the columnId should be the IdPerService
	 * @param serviceId service id of the configuration 
	 * @param configurationIdPerService IdPerService of the configuration to get
	 * 
	 * @return configuration
	 */
	public Configuration getConfiguration (int serviceId, int configurationIdPerService)
	{
		Configuration c = null;
		
		for (int i=0; i<this.columns.size(); i++)
		{
			c =this.columns.get(i);
			if (c.getService().getId() == serviceId && c.getIdPerService()  == configurationIdPerService )
			{
				return c;
			}
		}
		
		return null;
	}
	
	/**
	 * This function will return a specific PricingSolution for a certain service.
	 * It is mainly used to check the selected PricingSolution the PricingSolutionId should be the IdPerService
	 * @param serviceId service id of the PricingSolution 
	 * @param configurationIdPerService IdPerService of the configuration to get
	 * 
	 * @return configuration
	 */
	public PricingSolution getPricingSolution (int serviceId, int configurationIdPerService)
	{
		PricingSolution c = null;
		
		for (int i=0; i<this.pricingSolutions.size(); i++)
		{
			c =this.pricingSolutions.get(i);
			
			if (c.service.getId() == serviceId && c.idPerService == configurationIdPerService )
			{
				return c;
			}
		}
		
		return null;
	}
	
    /**
     * This method will report the master inputs and results to a file called MasterResults
     * It will also print the inputs to the console
     * 
     * @param filePath to write the master results "masterResults/MasterResults_"+iterations+".txt"
     */
    public void reportResults(String filePath){
    	String st ="";
    	
    	//print parameters
    	st+=this.toString();
    	
    	//print results
    	st+="\t RESULTS\n";
		try {
			st+=String.format("\t\t Objective=rho "+this.cplex.getObjValue()+"\n");
			st+=String.format("\t\t lambda[s][c] :\n");
			
			//print lambda (decision variable values)
			for (int i =0; i<this.servicesNb; i++)
				for (int j =0; j<this.cut[i].getSize(); j++)
				{
					// if no column was added for the service at an iteration we are setting cut[i].add(null) so we can keep the iteration of pricing = to id of column
					if(this.cut[i].getElement(j)!=null)
						st+=String.format("\t\t\tlambda["+i+"]["+j+"]= "+this.cplex.getValue(this.cut[i].getElement(j))+"\n");
				}
			//print dual values
			st+= "\t DUAL VALUES:\n";
	    	st+= "\t\t" +Output.printArray(this.alpha, "alpha[s]", "\t\t\t alpha");
	    	st+= "\t\t" +Output.printDoubleArray(this.beta, "beta[f][delta]", "\t\t\t beta");
	    	st+= "\t\t" +Output.printDoubleArray(this.gamma, "gamma[l][delta]", "\t\t\t gamma");
	    	st+= "\t\t" +Output.printArray(this.phi, "phi[s]", "\t\t\t phi");
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
     * The below method is needed to implement the constraint 
     * rho>= v_s^c lambda_s^c for all s for all c
     */
   /* public void updateMakespanRange() throws IloException
    {
    	int newColumnsNb = this.makespanRange[0].length+1;
    	IloRange[][] newMakespanRange = new IloRange [this.servicesNb][newColumnsNb];
    	
    	for (int i=0; i<newMakespanRange.length; i++)
    	{
    		for (int j=0; j<newMakespanRange[i].length; j++)
    		{
    			newMakespanRange[i][j] = (j== newMakespanRange[i].length-1) ? this.cplex.addRange(0,Double.MAX_VALUE):this.makespanRange[i][j];
    		}
    	}
    	
    	this.makespanRange = newMakespanRange;
    }*/
    
    public String toString()
    {
    	String s =String.format("Master Model \n");
    	s+= "\t PARAMETERS - BASIC SOLUTION GENERATED BY HEURISTIC:\n";
    	s+= "\t\t" +Output.printDoubleArray(v, "v[s][c]","\t\t\tv");
    	s+= "\t\t" +Output.printQuadrupleArray(r, "r[f][s][delta][c]","\t\t\tr");
    	s+= "\t\t" +Output.printQuadrupleArray(o, "o[l][s][delta][c]","\t\t\to");
    	s+= "\t\t" +Output.printArray(cl, "cl[l]","\t\t\tcl");
    	
    	s+= "\t COLUMNS PASSED TO MASTER:\n";
    	for (int i=0; i<this.columns.size(); i++)
    	{
    		s+="\t\t"+this.columns.get(i).toString();
    	}
    	s+= "\t INCUMBENT COLUMNS PASSED TO MASTER:\n";
    	for (int i=0; i<this.incumbentColumns.size(); i++)
    	{
    		s+="\t\t"+this.incumbentColumns.get(i).toString();
    	}
    	return s;
    }
    
	/**
	 * Main function to run the master model
	 * @param args
	 * @throws IloException
	 */
	public static void  main (String[]args) throws IloException
	{
		int initialColumnsSet = 2; //c number of columns for each service
		int servicesNb =2;//s
		int timeslotsNb = 4;//delta
		int linksNb = 1;//ij
		int VNFNb = 2;
		
		int[][] v = {{2,3}, {3,2}};//sc
		int[][][][] r = new int [VNFNb][servicesNb][timeslotsNb][initialColumnsSet];//fsdeltac		
		
		//service 0 column 0 using both VNFs
		r[0][0][0][0] = 1;
		r[1][0][2][0] = 1;//timeslot 2 for transmission
		r[0][0][1][1] = 1;
		r[1][0][3][1] = 1;
		
		//service 1 column 0 using both VNFs
		r[0][1][1][0] = 1;
		r[1][1][3][0] = 1;//timeslot 3 for transmission
		r[0][1][0][1] = 1;
		r[1][1][2][1] = 1;
			
		int[][][][]o= new int [linksNb][servicesNb][timeslotsNb][initialColumnsSet];//lsdeltac		
		//service 0 column 0 using both VNFs
		o[0][0][1][0] = 200;
		o[0][0][2][1] = 20;
		//service 1 column 0 using both VNFs
		o[0][1][2][0] = 10;
		o[0][1][1][1] = 10;	
		int[]cl = {200};
		
		
		CGMasterModel masterModel = new CGMasterModel(initialColumnsSet,servicesNb,timeslotsNb, linksNb,VNFNb, v, r, o,cl);		
		masterModel.buildMaster();
		masterModel.cplex.exportModel("masterModel.lp");
	
		/*********
		 * convert variables to int 
		 * build and add column has numVar to be able to get the dual values
		 */
		  /* for ( int i = 0; i < masterModel.cut.length; i++ ) {
			   for (int j =0; j<masterModel.cut[i].getSize(); j++)
				   masterModel.cplex.add(masterModel.cplex.conversion(masterModel.cut[i].getElement(j), IloNumVarType.Int));
	        } 
		   masterModel.cplex.add(masterModel.cplex.conversion(masterModel.rho, IloNumVarType.Int));*/
		if(masterModel.cplex.solve())
		{
			//print objective value
			System.out.println("Master Objective Value = "+masterModel.cplex.getObjValue());
			
			//print lambda (decision variable values)
			for (int i =0; i<servicesNb; i++)
				for (int j =0; j<masterModel.cut[i].getSize(); j++)
					System.out.println("lambda["+i+"]["+j+"]= "+masterModel.cplex.getValue(masterModel.cut[i].getElement(j)));
			
			System.out.println("serviceRange[serviceId]");
			for (int i=0; i<masterModel.serviceRange.length; i++)
			{
				System.out.println("serviceRange["+i+"] = "+masterModel.cplex.getDual(masterModel.serviceRange[i]));
			}
			
			System.out.println("VNFRange[VNFId][timeslot]");
			for (int i=0; i<masterModel.VNFRange.length; i++)
			{
				for (int j=0; j<masterModel.VNFRange[i].length; j++)
				{
					System.out.println("VNFRange["+i+"]["+j+"] = "+masterModel.cplex.getDual(masterModel.VNFRange[i][j]));
				}
			}
			
			System.out.println("linkCapacityRange[linkId][timeslot]");
			for (int i=0; i<masterModel.linkCapacityRange.length; i++)
			{
				for (int j=0; j<masterModel.linkCapacityRange[i].length; j++)
				{
					System.out.println("linkCapacityRange["+i+"]["+j+"] = "+masterModel.cplex.getDual(masterModel.linkCapacityRange[i][j]));
				}
			}
			
			System.out.println("MakespanRange[serviceID] ");
			for (int i=0; i<masterModel.makespanRange.length; i++)
			{
				System.out.println("makespanRange["+i+"] = "+masterModel.cplex.getDual(masterModel.makespanRange[i]));
			}
			
		
		}
		else
		{
			System.out.println("error");
		}	
		
	}
}
