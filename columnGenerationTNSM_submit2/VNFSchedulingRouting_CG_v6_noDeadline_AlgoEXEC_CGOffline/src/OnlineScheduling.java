/**
 * This class is designed to run CG on batches of services that follows a poisson arrival
 * CG is run periodically on services that arrives during the specified period.
 * 
 */
import ilog.concert.IloException;

import java.io.IOException;
import java.util.ArrayList;

import HelperClasses.Batch;
import HelperClasses.FileManipulation;
import HelperClasses.SchedulingSolution;
import HelperClasses.ServicesDriver;
import Model.CGMasterModel;
import Model.CGPricingModel;
import Model.CGPricingModelThread;
import Model.Configuration;
import Model.PricingSolution;
import Model.SchedulingModel;
import NFV.Middlebox;
import NFV.Service;
import NFV.VLink;
import Network.Link;
import Network.Network;



public class OnlineScheduling {
	
	Network network;
	
	//list of services following a poisson arrival sorted by their arrival time. This will hold only services of type arrival. 
	//We do not care about departure because resources will be free on schedule is completed
	ArrayList<Service> services;
	
	//list of selected configuration for each service by the column generation
	ArrayList<Configuration> optimalConfigurations;
	
	//list of batches that were run
	ArrayList<Batch> batches;
	
	//timePeriod is needed to run the CG periodically it is the length of period
	int timePeriod;
	
	int DELTA;//timeline
	
	SchedulingSolution optimalSolution; //used for sceduleServicesILp()
	public OnlineScheduling (ArrayList<Service> services, Network network, int timePeriod, int delta )
	{
		this.network = network;
		this.services = services;
		this.timePeriod = timePeriod;
		this.DELTA = delta;
		this.optimalConfigurations = new ArrayList<Configuration>();
		this.batches = new ArrayList<Batch>();
		this.optimalSolution =null;
	}
	

	/**
	 * This function performs CG periodically on services 
	 * @param adjustmentValue adjustment value of the arrival time
	 * @param diversify set to true if we want to diversify the CG solution
	 * @param diversificationConfigNb nb of configuration to generate by the diversification heuristic
	 * @param threading if true use onlineCGwithThreading , parallel execution of pricing
	 * @throws IloException
	 * @throws IOException
	 */
	public void periodicOnlineCG (int adjustmentValue, boolean diversify, int diversificationConfigNb, boolean threading) throws IloException, IOException
	{
		String pricingFilePath = "testResults/pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
		String masterFilePath = "testResults/masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
		String ilpFilePath ="testResults/masterResults/ILP.txt";
		
		//start with the first time period
		int startTimeslot =0;
		int endTimeslot =  this.timePeriod;
		Batch b;
		int processedServices =0;
		ArrayList<Service> servicesBatch;
		
		while (processedServices<this.services.size())
		{
					
			//get the batch of services to schedule
			servicesBatch = this.getBatch(startTimeslot, endTimeslot, adjustmentValue);
			
			//update the number of the processed services
			processedServices+=servicesBatch.size();
	
			//update services Id before sending them to CG and batch to prevent error in the master model where service Id need to start from 0
			this.updateServicesIds(servicesBatch);
						
			//create a batch
			b = new Batch(servicesBatch, startTimeslot, endTimeslot);
		
			//update the start and end timeslot for the next batch before checking of the size of the batch so we donnot fall into infinite loop
			startTimeslot+=this.timePeriod;
			endTimeslot+=this.timePeriod;
			
			//if no services within this batch continue
			if(servicesBatch.size() == 0)
			{
				continue;
			}
					
			//run column generation
			if(threading)
			{
				b = this.onlineCGWithThread(b, diversify, diversificationConfigNb, masterFilePath, ilpFilePath, pricingFilePath);
			}
			else
			{
				b = this.onlineCG(b, diversify, diversificationConfigNb, masterFilePath, ilpFilePath, pricingFilePath);
			}
			//add batch to the list of batches
			this.batches.add(b);
			
			//add the selected configurations to the array
			this.optimalConfigurations.addAll(b.configurations);
			
			
		}

	}
	
	/**
	 * This function performs tabu or SequentialILP periodically on services 
	 * @param adjustmentValue adjustment value of the arrival time
	 * @param algorithm  set to TABU or SequentialILP
	 * @param tabuIterations how many iterations for tabu
	 * @return totalSchedule length (for all batches)
	 * @throws Exception 
	 */
	public int periodicOnlineAlgo (int adjustmentValue, String algorithm, int tabuIterations) throws Exception
	{
		
		//start with the first time period
		int startTimeslot =0;
		int endTimeslot =  this.timePeriod;
		Batch b;
		int processedServices =0;
		ArrayList<Service> servicesBatch;
		int batchScheduleLength =0;
		int totalScheduleLength =0;
		double start =0; 
		double end =0;
		
		//tabu variables		 
		 int highestDeadline =500; //put it as big as possible because we need to have vnfs initialized to -1 and links to capacity
		 ArrayList<ArrayList<Integer>>vnfs = network.getVnfsTime (highestDeadline);
		 ArrayList<ArrayList<Integer>>links = network.getLinksTimeBw(highestDeadline);
		TabuMappingScheduling sh = new TabuMappingScheduling(this.network,"logs/tabuHeuristic.txt");

			
		while (processedServices<this.services.size())
		{
					
			//get the batch of services to schedule
			servicesBatch = this.getBatch(startTimeslot, endTimeslot, adjustmentValue);
			
			//update the number of the processed services
			processedServices+=servicesBatch.size();
	
						
			//create a batch
			b = new Batch(servicesBatch, startTimeslot, endTimeslot);
		
			//add batch to the list of batches
			this.batches.add(b);
			
			//update the start and end timeslot for the next batch before checking of the size of the batch so we donnot fall into infinite loop
			startTimeslot+=this.timePeriod;
			endTimeslot+=this.timePeriod;
			
			//if no services within this batch continue
			if(servicesBatch.size() == 0)
			{
				continue;
			}
					
			//run tabu
			if(algorithm.equals("TABU"))
			{
				start = System.currentTimeMillis();
				
				batchScheduleLength = sh.tabuMappingScheduling(b.services,tabuIterations, startTimeslot, vnfs, links,adjustmentValue);
				
				end = System.currentTimeMillis();
				b.ilpObjective = batchScheduleLength; //this will be schedule length for each batch independent from the others
				b.ilpExecutionTime = end-start;
				b.CGIterations = tabuIterations;
				
			}
			else if (algorithm.equals("SequentialILP"))
			{
				start = System.currentTimeMillis();
				batchScheduleLength = this.onlineMapScheduleServiceILP(b,null);
				end = System.currentTimeMillis();
				
				b.ilpObjective = batchScheduleLength; //this will be schedule length for each batch independent from the others
				b.ilpExecutionTime = end-start;
			
			}
	
					
		}
		
		if(algorithm.equals("TABU"))
		{
			totalScheduleLength = this.getTotalMakespanForAlgo();
		}
		else if(algorithm.equals("SequentialILP"))
		{
			totalScheduleLength = this.optimalSolution.objectiveValue;
		}
			
		return totalScheduleLength;
	}
	
	
	
	/**
	 * This method will return an array of all the services that were passed to the model in the previous batches in addition to the one in the
	 * batch b passed as parameter
	 * 
	 * @param services services needed only when we have an optimal solution not got from a batch of a previous run but from serialized file.
	 * used mainly for onlineAlgoBtahcExec()
	 * @param b batch that we want to schedule
	 * @return array of services scheduled and to schedule
	 */
	public ArrayList<Service> getScheduledAndToScheduleServices(Batch b, ArrayList<Service> services)
	{
		ArrayList<Service> scheduledAndToScheduleServices = new ArrayList<Service>();
		
		if (services!=null)
		{
			scheduledAndToScheduleServices.addAll(services);
		}
		//get all the batches that were processed in addition to this batch b
		for(int i=0; i<=b.id ; i++)
		{ 
			scheduledAndToScheduleServices.addAll(this.batches.get(i).services);
		}
		
		return scheduledAndToScheduleServices;
	}
	


	/**
	 * This method will schedule services based on SchedulingModelILP starting their schedule at their arrival time
	 * it will report the model results to testResults/schedILPResults.txt
	 * 
	 * @param batch containing the mappedRouted services to schedule set
	* @param optimal solution of all past batches and the veriable will be updated on each run to have solution of old and current batch
	*  @param services services needed only when we have an optimal solution not got from a batch of a previous run but from serialized file.
	 * used mainly for onlineAlgoBtahcExec()
	 * @ return schedule length of services in batch
	 * @throws IloException
	 * @throws IOException 
	 */
	public int scheduleServicesILP (Batch b,ArrayList<Service>services) throws IloException, IOException
	{
		int scheduleLength =0;
		String resultsFile = "testResults/schedILPResults.txt";
			
		//holds the servicestoSchedule of previous batches (in optimalSolution)+servicesToschedule of this batch
		ArrayList<Service> scheduledAndToScheduleServices = this.getScheduledAndToScheduleServices(b, services);
		
		//update services ids of all services sent to the model, mainly needed because E[] uses this id, prevent infeasability constraint
		this.updateServicesIds(scheduledAndToScheduleServices);
		
		int timeslot = b.endTimeslot;//scheduling of batch starts after services are collected between start and end timeslot of batch
		
		//this is used to handle the case where some services are already in the network and we are running for the first time the model(onlineAlgorBatchExec)
		if (services!=null&&this.optimalSolution == null)
		{
			this.optimalSolution = SchedulingModel.buildOptimalSolution(services, this.network.getVNFNb());
		}
		
		//start the scheduling of services in batch after all services of previous batch completed their schedule to get an upper bound on delta
		if (this.optimalSolution!=null && this.optimalSolution.objectiveValue >timeslot)
		{
			timeslot = this.optimalSolution.objectiveValue;
		}
		
		//set the time line to the max completion time of all the services in all the batches. This is to handle services of past batches/ prevent any error
		int delta =this.getHighestCompletionTime(b,timeslot)+1;
	
		//schedule of batch start at its end time slot after the collection of services
		SchedulingModel schedModel = new SchedulingModel(this.network, scheduledAndToScheduleServices, delta, b.endTimeslot, b.services.get(0).getId() );
		schedModel.buildILPModel();
		
		//if optimal solution is null means that this is the first batch no need to set any variables
		if (optimalSolution !=null)
		{
		
			//set the variables of the optimal solution (of previous batches)
			schedModel.setDecisionVariables(optimalSolution);
		}
			
		
		schedModel.runILPModel(resultsFile);		
		//update optimalSolution
		optimalSolution = schedModel.buildOptimalSolution();

		//batch schedule length
		scheduleLength = this.getScheduleLength(b.services);
				
		 
		 return scheduleLength;
		
	}
	
	
	
	/**
	 * This function performs CG periodically on services  
	 * @ NOT TESTED NOT USED
	 * @param adjustmentValue adjustment value of the arrival time
	 * @param diversify set to true if we want to diversify the CG solution
	 * @param diversificationConfigNb nb of configuration to generate by the diversification heuristic
	 * @throws IloException
	 * @throws IOException
	 */
	public void onlineBatchCG (int adjustmentValue, boolean diversify, int diversificationConfigNb, int batchSize) throws IloException, IOException
	{
		String pricingFilePath = "testResults/pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
		String masterFilePath = "testResults/masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
		String ilpFilePath ="testResults/masterResults/ILP.txt";
		
		Batch b;	
		ArrayList<Batch> batchObjects = this.getBatch(batchSize);
		
		for (int i=0; i<batchObjects.size(); i++)
		{
			b = batchObjects.get(i);
			//update services Id before sending them to CG and batch to prevent error in the master model where service Id need to start from 0
			this.updateServicesIds(b.services);
	
			//run column generation
			b = this.onlineCG(batchObjects.get(i), diversify, diversificationConfigNb, masterFilePath, ilpFilePath, pricingFilePath);
			
			//add batch to the list of batches
			this.batches.add(b);
			
			//add the selected configurations to the array
			this.optimalConfigurations.addAll(b.configurations);			
			
		}

	}
	
	
	/**
	 * This function returns an array of batches where each has the 
	 * number of services = batchSize
	 * 
	 * @param batchSize number of services in each batch 
	 * 
	 * @return array of batches
	 */
	public ArrayList<Batch> getBatch (int batchSize)
	{	
		ArrayList<Service> serviceBatch = new ArrayList<Service>();
		ArrayList<Batch> batches = new ArrayList<Batch>();
		Batch b;
		
		int count=0;
		
		for(int i=0; i<this.services.size(); i++)
		{						
			serviceBatch.add(this.services.get(i));
			
			count++;
			
			if(count == batchSize)
			{
				b = new Batch (serviceBatch,0,0);				
				batches.add(b);
				serviceBatch = new ArrayList<Service>();
				count =0;
			}				
			
		}
		
		return batches;
			
	}
		
		
	/**
	 * This function returns an array of services arriving between two timeslots
	 * 
	 * @param startTimeslot 
	 * @param endTimeslot
	 * @param adjustmentValue adjustment value for the arrival time
	 * 
	 * @return array of services
	 */
	public ArrayList<Service> getBatch (int startTimeslot, int endTimeslot, int adjustmentValue)
	{	
		ArrayList<Service> serviceBatch = new ArrayList<Service>();
		Service s;
		int arrivalTime;
		
		for(int i=0; i<this.services.size(); i++)
		{
			s= this.services.get(i);
			
			//adjust the arrival time from 0.1234 to 123 for example
			arrivalTime = (int)(s.getArrivalTime()*adjustmentValue);

			//if the service arrival time is within the batch add it to the batch
			if (arrivalTime>=startTimeslot && arrivalTime<=endTimeslot)
			{
				serviceBatch.add(s);
			}
			
		}
		
		
		return serviceBatch;
		
	}
	
	
	/**
	 * This method executes CG by considering the reserved resources by the optimalConfigurations of a previous batch of services.
	 * It also considers that schedule of the current batch can not start before a the specified startTimeslot of the batch
	 * 
	 * @param batch batch object that has the services to schedule and the start time slot at which their schedule can start
	 * @param diversify if set to true diversification will be applied to CG using the heuristic
	 * @param nbOfConfigPerService nb of columns to generate for each services for diversification purposes
	 * @param masterFilePath path to drop the master results
	 * @param ilpFilePath path to drop the ILP results
	 * @param pricingFilePath path to drop the pricing results
	 * 
	 * @return updated batch object with the ILP optimal solution (configuration) and all the batch info
	 * @throws IloException
	 * @throws IOException
	 */
	public Batch onlineCG(Batch batch, boolean diversify, int nbOfConfigPerService, String masterFilePath, String ilpFilePath, String pricingFilePath) throws IloException, IOException
	{
		//reporting some status
		FileManipulation statusFile = new FileManipulation("testResults/onlineStatusFile.txt");
		statusFile.writeInFile("Running network id "+this.network.getId()+" batch id "+batch.id+"\n");
		 
		 
		Configuration c;
		double reducedCost = 0;
		int pricingIterations=1;//start from iteration 1 to be able to track lambda on the name of the pricing file for the selected lambdas, and start it from 0 for the master
		int masterIterations=0;//start it from 0 for the master
		int initialColumnsSet = 1; //number of columns per service generated by the heuristic as basic solution for the master
		int pricingwithNegativeRC =0;
		
		// Master will take care of not choosing the configuration for schedules starting from 0.Pricing will also be forced to start schedule from satrtTimeslot so we reduce the nb of iterations
		//needed before the pricing starts generating columns starting at timeslot >startTimeslot
		int startTimeslot =batch.endTimeslot;
		int lastBatchCompletionTime = 0;
		int feasiblSchedStart = 0;
		int allowedIterations = 25;//50; //max number of iterations allowed to terminate if CG did not converge
		//runtime variables
		double startlp =0;
		double endlp =0;
		double startIlp =0;
		double endIlp =0;
		
		int[][]v ;
		int[][][][] o;
		int[][][][] r;
		ArrayList <Configuration> configurations = new ArrayList<Configuration>();	
		ArrayList <int[][][][]> modelInput ;
		SchedulingHeuristic sh = new SchedulingHeuristic(this.network,"logs/ServicesHeuristic.txt");
		
		//Since we need a feasible schedule, we should start when we make sure that all resources are available. That is:
		//1- when services of previous batch are completed
		//2- or at the start time of the current batch (if all the services of the previous one are completed before this start time
		if (this.optimalConfigurations.size()!=0)
		{	
			//get the maximum completion time of configurations of previous batches
			lastBatchCompletionTime = this.getTotalMakespan();
		}	
		//if the services are collected between time [0,5] then CG should start scheduling at 5=  batch.endTimeslot
		feasiblSchedStart = startTimeslot > lastBatchCompletionTime ?startTimeslot: lastBatchCompletionTime;
		
		//map and schedule the services
		configurations = sh.mapScheduleService(batch.services, this.DELTA, feasiblSchedStart);
		
			
		//check if any of the services was not scheduled, mapped or routed, then return null and do not execute columns generation
		if (configurations == null)
		{
			return null;
		}
		
		//update delta to its upper bound which is the completion time of the last service
		int delta = batch.services.get(batch.services.size()-1).getCompletionTime(); 	
		
		//map and schedule the services again by passing the new delta. This is only needed to prevent errors when checking is a column exists
		// so we can make sure all the configuration have the same length based on the new delta passed to the master and pricing
		configurations = sh.mapScheduleService(batch.services, delta, feasiblSchedStart);
		
		//prepare the initial configurations for the master
		modelInput = sh.convertConfigurations(configurations, delta,initialColumnsSet);		
		v = modelInput.get(0)[0][0];
		o = modelInput.get(1);//lsdeltac
		r = modelInput.get(2);//fsdeltac
		
		//build the master model by letting it know the reserved resources by the already set optimalConfigurations in addition to the start timeslot at which the services schedule can start
		CGMasterModel masterModel = new CGMasterModel(initialColumnsSet,batch.services.size(),delta, this.network.getLinkList().length,this.network.getVNFNb(), v, r, o,this.network.getLinksCapacities());		
			
		//if the services are collected between time [0,5] then CG should start scheduling at 5=  batch.endTimeslot
		masterModel.buildMaster(this.optimalConfigurations, startTimeslot);
		
		//add configurations to the columns array in the master		
		masterModel.columns.addAll(configurations);
		
		startlp = System.currentTimeMillis();
		while (true)
		{			
			masterModel.runMasterModel(masterFilePath+"_"+masterIterations+".txt",masterIterations );

			for (int i = 0; i<batch.services.size(); i++)
			{				
				//start pricing schedule at batch.endtimeslot to reduce the nb of iterations needed until the pricing starts by generating feasible columns to the master(which schedule starts after batch.endTimeslot)
				CGPricingModel pricingModel = new CGPricingModel(batch.services.get(i), this.network, masterModel, delta,startTimeslot);
				pricingModel.buildPricingModel(null);
					  	 
			  	c = pricingModel.runPricingModel(pricingFilePath+"_"+batch.services.get(i).getId()+"_"+pricingIterations+".txt", pricingIterations);
				
				//get the reduced cost of the pricing 
				reducedCost = pricingModel.cplex.getObjValue();		
							
				//compare to a very small value and not 0 since the reduced cost is double and may have value 0.0000 which will not be considered <=0
				if (reducedCost <=1e-10)
				{
					//if reduced cost<=0, no need to add column for this service
					//count the number of pricing having RC<=0, pricing for all services at the same iteration <=0 stop the CG
					pricingwithNegativeRC++;
					
					//this just to mention that no column was added to the service at this iteration so we can read easily the chosen column (if lambda[0][1] = 1; then pRes_0_1 column was chosen) 
					masterModel.cut[batch.services.get(i).getId()].add(null);
				}
				//make sure that the column is not already added to the master to reduce master execution time
				else if (!masterModel.columnExists(c))
	        	{
					masterModel.addColumnToMaster(c);
				}
				pricingModel.cplex.end();	
			}			
			
			//optimal solution is reached
			if (pricingwithNegativeRC == batch.services.size())
			{
				break;
			}
			
			//@to debug prevent looping indefinitely
			if (pricingIterations>allowedIterations)
			{
				batch.error ="CG Exceeded allowed iterations (#"+allowedIterations+")";
			
				break;
			}
			pricingwithNegativeRC=0;
			pricingIterations++;
			masterIterations++;
		}	
		endlp = System.currentTimeMillis();
						
		if(diversify)
		{
			ArrayList<Configuration> diversificationConfigurations = sh.generateDiversificationConfigurations(batch.services, delta, startTimeslot,nbOfConfigPerService );
			masterModel.addColumnsToMaster(diversificationConfigurations);			
		}		

		
		//run ILP after adding incumbent columns and add results to the array	
		startIlp = System.currentTimeMillis();
		masterModel.runILPMasterModel(ilpFilePath);
		endIlp = System.currentTimeMillis();
		
		//update batch info
		batch.lpExecutionTime = endlp - startlp;
		batch.lpObjective = masterModel.lpObjectiveValue;
		batch.ilpExecutionTime = endIlp - startIlp;
		batch.ilpObjective = masterModel.IlpObjectiveValue;
		batch.CGIterations = pricingIterations;
		batch.delta = delta;		
		batch.configurations =  masterModel.getILpSolution();
		
		masterModel.cplex.end();
		
		//return configurations selected by the ILP solution
		return batch;
	}
	
	
	/**
	 * This method consider thread processing for the pricing. It the same as onlineCG
	 * This method executes CG by considering the reserved resources by the optimalConfigurations of a previous batch of services.
	 * It also considers that schedule of the current batch can not start before a the specified startTimeslot of the batch
	 * 
	 * @param batch batch object that has the services to schedule and the start time slot at which their schedule can start
	 * @param diversify if set to true diversification will be applied to CG using the heuristic
	 * @param nbOfConfigPerService nb of columns to generate for each services for diversification purposes
	 * @param masterFilePath path to drop the master results
	 * @param ilpFilePath path to drop the ILP results
	 * @param pricingFilePath path to drop the pricing results
	 * 
	 * @return updated batch object with the ILP optimal solution (configuration) and all the batch info
	 * @throws IloException
	 * @throws IOException
	 */
	public Batch onlineCGWithThread(Batch batch, boolean diversify, int nbOfConfigPerService, String masterFilePath,String ilpFilePath, String pricingFilePath) throws IloException, IOException
	{
		//reporting some status
		FileManipulation statusFile = new FileManipulation("testResults/onlineStatusFile.txt");
		statusFile.writeInFile("Running network id "+this.network.getId()+" batch id "+batch.id+"\n");
		 
		 
		Configuration c;
		double reducedCost = 0;
		int pricingIterations=1;//start from iteration 1 to be able to track lambda on the name of the pricing file for the selected lambdas, and start it from 0 for the master
		int masterIterations=0;//start it from 0 for the master
		int initialColumnsSet = 1; //number of columns per service generated by the heuristic as basic solution for the master
		int pricingwithNegativeRC =0;
		
		// Master will take care of not choosing the configuration for schedules starting from 0.Pricing will also be forced to start schedule from satrtTimeslot so we reduce the nb of iterations
		//needed before the pricing starts generating columns starting at timeslot >startTimeslot
		int startTimeslot =batch.endTimeslot;
		int lastBatchCompletionTime = 0;
		int feasiblSchedStart = 0;
		int allowedIterations = 25;//50; //max number of iterations allowed to terminate if CG did not converge
		//runtime variables
		double startlp =0;
		double endlp =0;
		double startIlp =0;
		double endIlp =0;
		
		int[][]v ;
		int[][][][] o;
		int[][][][] r;
		
		ArrayList <CGPricingModelThread> pricingThreads = new ArrayList<CGPricingModelThread>() ;
		CGPricingModelThread pricingModelThread;
		ArrayList <Configuration> configurations = new ArrayList<Configuration>();	
		ArrayList <int[][][][]> modelInput ;
		SchedulingHeuristic sh = new SchedulingHeuristic(this.network,"logs/ServicesHeuristic.txt");
		
		//Since we need a feasible schedule, we should start when we make sure that all resources are available. That is:
		//1- when services of previous batch are completed
		//2- or at the start time of the current batch (if all the services of the previous one are completed before this start time
		if (this.optimalConfigurations.size()!=0)
		{	
			//get the maximum completion time of configurations of previous batches
			lastBatchCompletionTime = this.getTotalMakespan();
		}	
		
		//if the services are collected between time [0,5] then CG should start scheduling at 5=  batch.endTimeslot
		feasiblSchedStart = startTimeslot > lastBatchCompletionTime ?startTimeslot: lastBatchCompletionTime;
		
		//map and schedule the services
		configurations = sh.mapScheduleService(batch.services, this.DELTA, feasiblSchedStart);
		
		
		//check if any of the services was not scheduled, mapped or routed, then return null and do not execute columns generation
		if (configurations == null)
		{
			return null;
		}
		
		//update delta to its upper bound which is the completion time of the last service
		int delta = batch.services.get(batch.services.size()-1).getCompletionTime(); 	
	
		//map and schedule the services again by passing the new delta. This is only needed to prevent errors when checking is a column exists
		// so we can make sure all the configuration have the same length based on the new delta passed to the master and pricing
		configurations = sh.mapScheduleService(batch.services, delta, feasiblSchedStart);
		
		//prepare the initial configurations for the master
		modelInput = sh.convertConfigurations(configurations, delta,initialColumnsSet);		
		v = modelInput.get(0)[0][0];
		o = modelInput.get(1);//lsdeltac
		r = modelInput.get(2);//fsdeltac
		
		//build the master model by letting it know the reserved resources by the already set optimalConfigurations in addition to the start timeslot at which the services schedule can start
		CGMasterModel masterModel = new CGMasterModel(initialColumnsSet,batch.services.size(),delta, this.network.getLinkList().length,this.network.getVNFNb(), v, r, o,this.network.getLinksCapacities());		
			
		//if the services are collected between time [0,5] then CG should start scheduling at 5=  batch.endTimeslot
		masterModel.buildMaster(this.optimalConfigurations, startTimeslot);
		
		//add configurations to the columns array in the master		
		masterModel.columns.addAll(configurations);
		
		startlp = System.currentTimeMillis();
		while (true)
		{			
			masterModel.runMasterModel(masterFilePath+"_"+masterIterations+".txt",masterIterations );

			for (int i = 0; i<batch.services.size(); i++)
			{				
				//start pricing schedule at batch.endtimeslot to reduce the nb of iterations needed until the pricing starts by generating feasible columns to the master(which schedule starts after batch.endTimeslot)
				pricingModelThread = new CGPricingModelThread(batch.services.get(i), this.network, masterModel, delta,startTimeslot,pricingFilePath+"_"+batch.services.get(i).getId()+"_"+pricingIterations+".txt");
				pricingModelThread.buildPricingModel(null);
					  	 
			  	pricingModelThread.start();
			  	pricingThreads.add(pricingModelThread);
			}
			
			for (int i = 0; i<pricingThreads.size(); i++)
			{
				try {
					pricingModelThread = pricingThreads.get(i);
					//join all threads so we make sure that all threads are done execution before proceeding
					pricingModelThread.join();
				
					//get the reduced cost of the pricing 
					reducedCost = pricingModelThread.cplex.getObjValue();		
								
					//compare to a very small value and not 0 since the reduced cost is double and may have value 0.0000 which will not be considered <=0
					if (reducedCost <=1e-10)
					{
						//if reduced cost<=0, no need to add column for this service
						//count the number of pricing having RC<=0, pricing for all services at the same iteration <=0 stop the CG
						pricingwithNegativeRC++;
						
						//this just to mention that no column was added to the service at this iteration so we can read easily the chosen column (if lambda[0][1] = 1; then pRes_0_1 column was chosen) 
						masterModel.cut[batch.services.get(i).getId()].add(null);
					}
					//make sure that the column is not already added to the master to reduce master execution time
					else if (!masterModel.columnExists(pricingModelThread.conf))
		        	{
						masterModel.addColumnToMaster(pricingModelThread.conf);
						//update id per service to be able to get selected solutions and add it to master
						pricingModelThread.solution.idPerService = pricingModelThread.conf.getIdPerService();
						masterModel.pricingSolutions.add(pricingModelThread.solution);
					}
					
					pricingModelThread.cplex.end();	
				
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
							
			//empty the threads array for the next iteration or we will end up in an infinite loop
			pricingThreads = new ArrayList<CGPricingModelThread>() ;
			
			//optimal solution is reached
			if (pricingwithNegativeRC == batch.services.size())
			{
				break;
			}
			
			//@to debug prevent looping indefinitely
			if (pricingIterations>allowedIterations)
			{
				batch.error ="CG Exceeded allowed iterations (#"+allowedIterations+")";
			
				break;
			}
			pricingwithNegativeRC=0;
			pricingIterations++;
			masterIterations++;
		}	
		endlp = System.currentTimeMillis();
						
		if(diversify)
		{
			ArrayList<Configuration> diversificationConfigurations = sh.generateDiversificationConfigurations(batch.services, delta, startTimeslot,nbOfConfigPerService );
			masterModel.addColumnsToMaster(diversificationConfigurations);			
		}		

		
		//run ILP after adding incumbent columns and add results to the array	
		startIlp = System.currentTimeMillis();
		masterModel.runILPMasterModel(ilpFilePath);
		endIlp = System.currentTimeMillis();
		
		//update batch info
		batch.lpExecutionTime = endlp - startlp;
		batch.lpObjective = masterModel.lpObjectiveValue;
		batch.ilpExecutionTime = endIlp - startIlp;
		batch.ilpObjective = masterModel.IlpObjectiveValue;
		batch.CGIterations = pricingIterations;
		batch.delta = delta;		
		batch.configurations =  masterModel.getILpSolution();
		
		//update the services solutions based on what selected by the master
		this.updateServices(masterModel.getILpSolutionByPricingSolution(), batch.services);
		masterModel.cplex.end();
	
		//return configurations selected by the ILP solution
		return batch;
	}
	
	/**
	 * This function will map and schedule a list of services in a batch
	 * It will return null if all the services can not be mapped, routed or scheduled or it will return the set of admitted services
	 * 
	 * The Mapping will be random
	 * the routing based on the shortest path having available bw
	 * the scheduling is based on SchedulingModel by setting the variables of the previous batches in optimalScheduling
	 * 
	 * @param services services needed only when we have an optimal solution not got from a batch of a previous run but from serialized file.
	 * used mainly for onlineAlgoBtahcExec(), services should be mapped routed and scheduled
	 *
	 * @return total schedule length of all services in batch
	 * @throws Exception 
	 */
	public int onlineMapScheduleServiceILP(Batch b, ArrayList<Service>services) throws Exception
	{	
		FileManipulation statusFile = new FileManipulation("testResults/onlineStatusFile.txt");
		statusFile.writeInFile("Running network id "+this.network.getId()+" batch id "+b.id+"\n");
		String logFileName ="log.txt";
		
		SchedulingHeuristic sh = new SchedulingHeuristic(this.network,logFileName );
	
		 ArrayList<Service> servicesToSchedule = new ArrayList<Service>();
		Service updatedService , s;
	
		
		for(int i=0; i<b.services.size(); i++)
		{	
			s= b.services.get(i);
			updatedService = s;
			
			//map VNFs randomly
			updatedService = sh.mapMiddleboxes(s);
			
			
			// if service can be mapped try routing it
			if (updatedService != null)
			{
				updatedService = sh.mapVirtualLinks(s);
			}
			
			
			//if service can not be mapped or routed reject it
			if (updatedService == null)
			{
				s.releaseResources();
			
			}
			else
			{	System.out.println(s);
				servicesToSchedule.add(s);
			
			}			
							
		}
		
		b.services = servicesToSchedule;
		
		//schedule services that can be routed and mapped	from the batch services, get batch schedule length
		int scheduleLength = this.scheduleServicesILP(b,services);

		return scheduleLength;
		
	}
	/**
	 * This method updates the services id of the services passed as parameter
	 * to be continious starting from 0. Mainly used for CG 
	 * 
	 * @param servicesList to update 
	 */
	public void updateServicesIds(ArrayList<Service> servicesList)
	{
		for (int i=0; i<servicesList.size(); i++)
		{
			servicesList.get(i).setId(i);
		}
	}
	
	
	public PricingSolution getPricingSolution(ArrayList<PricingSolution>ilpPricingSolutions, Service s)
	{
		
		for(int i=0; i<ilpPricingSolutions.size(); i++)
		{
			if (ilpPricingSolutions.get(i).service.getId() == s.getId())
			{
				return ilpPricingSolutions.get(i);
			}
		}
		
		return null;
		
	}
	
	/**
	 * this method will update the services based on the master model chosen config 
	 * @param ilpPricingSolutions should have one per service
	 */
	public void updateServices(ArrayList<PricingSolution>ilpPricingSolutions, ArrayList<Service> services)
	{
		Service s;
		PricingSolution p ;
		Middlebox m;
		VLink vl;
		int[] isProcessing;
		int[] isTransmitting;
		ArrayList<Link> routedThrough = new ArrayList<Link>();
		int count =0;
		for (int i=0; i<services.size(); i++)
		{
			s= services.get(i);
			s.releaseResources();//here services may be already mapped based on the initial solution given to master, release to prevent any errors
		
			p = this.getPricingSolution(ilpPricingSolutions, s);
			
			if (p == null)
			{
				System.out.println("Error - Pricing solution for service "+s.getId()+"not found in ilpPricingSolutions");
				return;
			}
			//loop over middlebox
			for (int j=0; j<p.z.length; j++)
 	    	{ 	    		
				m = s.getMiddleboxes().get(j);
				isProcessing = new int[m.getProcessingTime()];
				count =0;
				//loop over vnfs
				for (int f=0; f<p.z[j].length; f++)
	 	    	{
					//loop over delta
					for (int delta=0; delta<p.z[j][f].length; delta++)
		 	    	{
						
						if (p.y[j][f][delta] ==1)
						{
							m.setMappedToVNF(this.network.getVNF(f));
							m.setStartedProcessing(delta);
						}
							
						if (p.z[j][f][delta]  ==1)
						{
						
							isProcessing [count] = delta;
							count++;
						}
		 	    	}
					
	 	    	}
				
				m.setIsProcessing(isProcessing);		
				
 	    	}
		
			//loop over vlink
			for (int e=0; e<p.thetaHat[0].length; e++)
			{
				vl = s.getVirtualLinks().get(e);
				isTransmitting = new int[vl.getIsTransmitting().length];
				
				count =0;
	 	    	//loop over delta
				for (int delta=0; delta<p.thetaHat.length; delta++)
				{									
					if (p.theta[delta][e]==1)
					{
						vl.setStartedTransmition(delta);
					}
					
					if (p.thetaHat[delta][e]==1)
					{
						isTransmitting[count] =delta;
						count++;
					}
				}
				vl.setIsTransmitting(isTransmitting);
			}
 	    	
			
			for (int e =0; e<p.l[0][0].length; e++)
			{
				vl = s.getVirtualLinks().get(e);
				routedThrough = new ArrayList<Link>();
				for(int k=0; k<p.l.length; k++)
				{
					for (int j=0; j<p.l[k].length; j++)
					{				
						if (p.l[k][j][e] ==1)
						{
							routedThrough.add(this.network.getLink(k, j, true));
						}
					}
				}				
				vl.setRoutedThrough(routedThrough);
			}
			
    		s.setCompletionTime(p.v);
    		
 	    	//reset
		
			s = null;
			count =0;
		}
	
	}
	
	
	/**
	 * This function return the makespan at which all the services completed their schedule
	 * That is returning the maximum completion time of the optimal configurations
	 * 
	 * @return completion time of all services
	 */
	public int getTotalMakespan()
	{
		int totalMakespan = 0;
		
		for (int i=0; i<this.optimalConfigurations.size(); i++)
		{
			
			if (this.optimalConfigurations.get(i).getV()>totalMakespan)
			{
				totalMakespan=this.optimalConfigurations.get(i).getV();
			}
		}
		
		return totalMakespan;
	}
	
	/**
	 * this method will get the total schedule length based on all batches
	 * @return
	 */
	public int getTotalMakespanForAlgo()
	{
		int totalMakespan = 0;
		
		for (int i=0; i<this.batches.size(); i++)
		{
			
			if (this.batches.get(i).ilpObjective>totalMakespan)
			{
				totalMakespan=(int)this.batches.get(i).ilpObjective;
			}
		}
		
		return totalMakespan;
	}
	
	
	/**
	 * this method is used to get the schedule length of list of scheduled services (used to get batch objective)
	 * @param services
	 * @return schedule length
	 */
	public int getScheduleLength(ArrayList<Service> services)
	{
		
		int totalMakespan = 0;
		
		for (int i=0; i<services.size(); i++)
		{
			
			if (services.get(i).getCompletionTime()>totalMakespan)
			{
				totalMakespan=services.get(i).getCompletionTime();
			}
		}
		
		return totalMakespan;
		
	}
	
	/**
	 * this method will help setting Delta of the schedule ILP
	 * by taking a batch where services are mapped and routed and attempting to schedule them in a sequential order starting from b.endTimeslot
	 * and return the highest completion time of all services
	 * @param b
	 * @param timeslot timeslot at which scheduling should start
	 * @return highest completion time
	 * @throws IOException 
	 */
	public int getHighestCompletionTime (Batch b, int timeslot) throws IOException
	{
		
		Service s = null;
		SchedulingHeuristic sh = new SchedulingHeuristic(this.network, "logs/ServicesHeuristic.txt");

		int highestCompletionTime =0;
		for (int i=0; i<b.services.size(); i++)
		{
			s = b.services.get(i).copy(true,true);			
			sh.scheduleService(s, timeslot);
			
			if (s.getCompletionTime() >highestCompletionTime)
			{
				highestCompletionTime = s.getCompletionTime();
			}
			
			//start the schedule of the next service once the previous one was completed
			timeslot = s.getCompletionTime()+1;
		}
		
		return highestCompletionTime;
	}
	
	/**
	 * This function will report the results of the periodic tabu and sequential scheduling
	 * 
	 * 
	 * @param executionTime
	 * @param adjustementValue
	 * @param totalScheduleLength this is the total length of all batches and services in network, 
	 * @throws IOException
	 */
	public void reportResultsForOnlineAlgo( double executionTime, int adjustementValue, int setId, int totalScheduleLength) throws IOException
	{
		 Batch b;
		 String a="{";
		 String d="{";
		
		 FileManipulation resultsFile = new FileManipulation("testResults/BatchExecTimeRes/onlineResults_set"+setId+".txt");
		 FileManipulation batchResultsFile = new FileManipulation("testResults/BatchExecTimeRes/onlineResultsByBatch"+setId+".txt");
	
		 resultsFile.writeInFile("\n");
		 resultsFile.writeInFile(this.network.getId()+"\t");
		 resultsFile.writeInFile(this.network.getVNFNb()+"\t");
		 resultsFile.writeInFile(this.network.getMaxLinkCapacity()+"\t");
		 resultsFile.writeInFile(totalScheduleLength+"\t");		 
		 resultsFile.writeInFile(executionTime+"\t");
	
		 
		 batchResultsFile.writeInFile(this.network+"\n\n");
		 batchResultsFile.writeInFile("#BtachId\t");
		 batchResultsFile.writeInFile("Number of services\t");	
		 batchResultsFile.writeInFile("Schedule Length (Objective)\t");	
		 batchResultsFile.writeInFile(" ExecutionTime\t");
		 batchResultsFile.writeInFile("Nb Iterations-Tabu Only \t");
		 batchResultsFile.writeInFile("DELTA \t");
		 batchResultsFile.writeInFile("ServicesArrivals\t");
		 batchResultsFile.writeInFile("ServicesDeparture\t");
		 batchResultsFile.writeInFile("\n");
		 for(int i=0; i<this.batches.size(); i++)
		 {
			 b= this.batches.get(i);
			 batchResultsFile.writeInFile(b.id+"\t");
			 batchResultsFile.writeInFile(b.services.size()+"\t");			
			 batchResultsFile.writeInFile(b.ilpObjective+"\t");			
			 batchResultsFile.writeInFile(b.ilpExecutionTime+"\t");
			 batchResultsFile.writeInFile(b.CGIterations+"\t");
			 batchResultsFile.writeInFile(b.delta+"\t");	
			 
			 for(int j=0; j<b.services.size(); j++)
			 {
				 a+=b.services.get(j).getArrivalTime()*adjustementValue+",";
				 d+=b.services.get(j).getDepartureTime()*adjustementValue+",";
			 }
			 a+="}";
			 d+="}";
			 batchResultsFile.writeInFile(a+"\t");
			 batchResultsFile.writeInFile(d+"\t");
			 batchResultsFile.writeInFile("\n");
			 a="";
			 d="";
		 }
		 batchResultsFile.writeInFile("\n\n");
	}
	
	
	/**
	 * This function will report the results of the periodicOnline CG
	 * 
	 * @param diversify
	 * @param diversificationConf
	 * @param executionTime
	 * @param adjustementValue
	 * @throws IOException
	 */
	public void reportResults(boolean diversify, int diversificationConf, double executionTime, int adjustementValue, int setId) throws IOException
	{
		 Batch b;
		 String a="{";
		 String d="{";
		
		 FileManipulation resultsFile = new FileManipulation("testResults/BatchExecTimeRes/onlineResults_set"+setId+".txt");
		 FileManipulation batchResultsFile = new FileManipulation("testResults/BatchExecTimeRes/onlineResultsByBatch"+setId+".txt");
	
		 resultsFile.writeInFile("\n");
		 resultsFile.writeInFile(this.network.getId()+"\t");
		 resultsFile.writeInFile(this.network.getVNFNb()+"\t");
		 resultsFile.writeInFile(this.network.getMaxLinkCapacity()+"\t");
		 resultsFile.writeInFile(this.getTotalMakespan()+"\t");
		 resultsFile.writeInFile(executionTime+"\t");
		 resultsFile.writeInFile(diversify+"\t");
		 resultsFile.writeInFile(diversificationConf+"\t");
		 
		 batchResultsFile.writeInFile(this.network+"\n\n");
		 batchResultsFile.writeInFile("#BtachId\t");
		 batchResultsFile.writeInFile("Number of services\t");
		 batchResultsFile.writeInFile("LP Objective\t");
		 batchResultsFile.writeInFile("ILP Objective\t");
		 batchResultsFile.writeInFile("LP ExecutionTime\t");
		 batchResultsFile.writeInFile("ILP ExecutionTime\t");
		 batchResultsFile.writeInFile("Nb Iterations \t");
		 batchResultsFile.writeInFile("DELTA \t");
		 batchResultsFile.writeInFile("Diversify\t");
		 batchResultsFile.writeInFile("Nb of diversification Configurations\t");
		 batchResultsFile.writeInFile("CG exceeds Nb of iteartions\t");
		 batchResultsFile.writeInFile("ServicesArrivals\t");
		 batchResultsFile.writeInFile("ServicesDeparture\t");
		 batchResultsFile.writeInFile("\n");
		 for(int i=0; i<this.batches.size(); i++)
		 {
			 b= this.batches.get(i);
			 batchResultsFile.writeInFile(b.id+"\t");
			 batchResultsFile.writeInFile(b.services.size()+"\t");
			 batchResultsFile.writeInFile(b.lpObjective+"\t");
			 batchResultsFile.writeInFile(b.ilpObjective+"\t");
			 batchResultsFile.writeInFile(b.lpExecutionTime+"\t");
			 batchResultsFile.writeInFile(b.ilpExecutionTime+"\t");
			 batchResultsFile.writeInFile(b.CGIterations+"\t");
			 batchResultsFile.writeInFile(b.delta+"\t");
			 batchResultsFile.writeInFile(diversify+"\t");
			 batchResultsFile.writeInFile(diversificationConf+"\t");
			 batchResultsFile.writeInFile(b.error+"\t");
			 
			 for(int j=0; j<b.services.size(); j++)
			 {
				 a+=b.services.get(j).getArrivalTime()*adjustementValue+",";
				 d+=b.services.get(j).getDepartureTime()*adjustementValue+",";
			 }
			 a+="}";
			 d+="}";
			 batchResultsFile.writeInFile(a+"\t");
			 batchResultsFile.writeInFile(d+"\t");
			 batchResultsFile.writeInFile("\n");
			 a="";
			 d="";
		 }
		 batchResultsFile.writeInFile("\n\n");
	}
	
	/**
	 * This method is used to get the final online CG results by considering a period length to get the batches
	 * @param diversify
	 * @param threading if true use pricing as thread
	 * 
	 * @throws IOException
	 * @throws IloException
	 */
	public static void resultsRun (boolean diversify, boolean threading) throws IOException, IloException
	{	
		//network input
		int VNFType = 5;//let it be the the min of the VNFNb so when we increase the Nb we increase the VNFs of the same type
		int pmNb = 8;
		int linkNb = 8;
		int linkWeight = 1;
		int pmNoVNF = 2;	
		
		//testing over network of different VNF nb and links capacity
		int [] VNFNb = {4,8,12,16,20};//{8}//{5,10,15,20};
		int [] linksCapacity = {550,650,750, 850};//{500,600,700,800};	
		int VNFVaryLink = 8;
		int linkVaryVNF = 500;
		ArrayList <Network>  networkListVNFs = new ArrayList<Network>() ;
		ArrayList <Network>  networkListLinks = new ArrayList<Network>() ;	
		
		// services input
		int arrivalRate = 50;
		int departureRate = 10;
		int servicesNb = 25;//25;//3;
		int delta = 100;
		int minVm =3;
		int maxVM= 5;
		int minBw = 300;
		int maxBw = 500;
		int minProcessing = 1;
		int maxProcessing =5;
		int minTraffic = 500;
		int maxTraffic = 1500;
		int setNb =5;
		int startSeed=12; //seed+2
				
		//CG variables
		int diversificationConf = 3;
		int timePeriod = 10;//increasing the time period will increase DELTA .. more CG runTime
		int adjustementValue = 100;
				
		//other variables
		Network n=null;
		OnlineScheduling on;
		double endlp=0;
		double startlp=0;
		double executionTime =0;
		
		//creating networks of different VNFNB and LInk NB to test 
		/*for (int i=0; i<VNFNb.length; i++)
		{
			n=new Network(VNFNb[i],VNFType,pmNb,linkNb,linkVaryVNF,linkVaryVNF,linkWeight,pmNoVNF);
			n.buildPhysicalNetwork();
			networkListVNFs.add(n);
			System.out.println(n);
		}
	
		//Driver.serializeNetworks(networkListVNFs,"testResults/onlineNetworksBatchExec.ser");
		 
		 for (int i=0; i<linksCapacity.length; i++)
		{
			n=new Network(VNFVaryLink,VNFType,pmNb,linkNb,linksCapacity[i],linksCapacity[i],linkWeight,pmNoVNF);
			n.buildPhysicalNetwork();
			networkListLinks.add(n);
			System.out.println(n);
		}
		
		//serialize network
		Driver.serializeNetworks(networkListLinks, "testResults/onlineNetworksVaryLinksCapacity.ser");
		Driver.serializeNetworks(networkListVNFs, "testResults/onlineNetworksVaryVNFsNb.ser");*/
	

		/**
		 * Generate services based on the first network VNFTypes.
		 * Since we are using seed, we should not have the problem of VNF not existing in the network
		 */
			networkListLinks = Driver.deserializeNetworks("testResults/onlineNetworksBatchExec.ser");//("testResults/onlineNetworksBatchExec.ser");//hh
		n= networkListLinks.get(0);

		/*ServicesDriver driver = new ServicesDriver (servicesNb,  delta, minVm, maxVM, minBw, maxBw,maxProcessing , minProcessing, minTraffic, maxTraffic,n.getPhysicalNetworkArray().get(2)[0]);
		ArrayList<Service> services = driver.generateServices(arrivalRate, departureRate);
		//System.out.println(services.get(0));
		//Driver.serializeServices(services, "testResults/onlineServicesForBatchExec.ser");
		
		for(int i=0; i<services.size(); i++)
		{
			System.out.println(services.get(i));
		}
		Driver.serializeServices(services, "testResults/onlineServices_set4_seed20.ser");
	
		/*networkListLinks = Driver.deserializeNetworks("testResults/onlineNetworksVaryLinksCapacity.ser");//hh
		
		for (int j=0; j<setNb; j++)
		{
			ArrayList<Service> services =  Driver.deserializeServices("testResults/onlineServices_set"+j+"_seed"+startSeed+".ser", 0, servicesNb);//hh
			
			for (int i=0; i<networkListLinks.size(); i++)//hh
			{
				//n= networkListLinks.get(0);*/
		ArrayList<Service> services =  Driver.deserializeServices("testResults/onlineServicesForBatchExec.ser", 0, servicesNb);//hh
				on = new OnlineScheduling(services, n, timePeriod, delta);
				startlp= System.currentTimeMillis();
				
				on.periodicOnlineCG(adjustementValue, diversify, diversificationConf,threading);		
				Driver.serializeConfigurations(on.optimalConfigurations, "testResults/optimalConfigurationsForBatchExec.ser");
				Driver.serializeServices(on.services, "testResults/onlineCGMappedScheduledServicesForBatchExec.ser");
				
				for(int i=0; i<on.services.size(); i++)
				{
					System.out.println(on.services.get(i));
				}
				endlp = System.currentTimeMillis();
				executionTime = endlp-startlp;
				
				//on.reportResults(diversify, diversificationConf,executionTime, adjustementValue, 0);				
				
				/*	}
		startSeed+=2;
		}*/
	}
	
	
	/**
	 * This method is used to get the final online CG results by considering a period length to get the batches
	 * @param diversify
	 * @param threading if true use pricing as thread
	 * @throws Exception 
	 */
	public static void AlgoResultsRun (int tabuIterations, String algorithm ) throws Exception
	{	
		//network input
		int VNFType = 5;//let it be the the min of the VNFNb so when we increase the Nb we increase the VNFs of the same type
		int pmNb = 8;
		int linkNb = 8;
		int linkWeight = 1;
		int pmNoVNF = 2;	
		
		//testing over network of different VNF nb and links capacity
		int [] VNFNb = {4,8,12,16,20};//{5,10,15,20};
		int [] linksCapacity = {550,650,750, 850};//{500,600,700,800};	
		int VNFVaryLink = 8;
		int linkVaryVNF = 500;
		ArrayList <Network>  networkListVNFs = new ArrayList<Network>() ;
		ArrayList <Network>  networkListLinks = new ArrayList<Network>() ;	
		
		// services input
		int arrivalRate = 50;
		int departureRate = 10;
		int servicesNb = 25;
		int delta = 100;
		int minVm =3;
		int maxVM= 5;
		int minBw = 300;
		int maxBw = 500;
		int minProcessing = 1;
		int maxProcessing =5;
		int minTraffic = 500;
		int maxTraffic = 1500;
		int setNb =5;
		int startSeed=14;//12; //seed+2
				
		//CG variables
		int diversificationConf = 3;
		int timePeriod = 10;//increasing the time period will increase DELTA .. more CG runTime
		int adjustementValue = 100;
				
	
		//other variables
		Network n;
		OnlineScheduling on;
		double endlp=0;
		double startlp=0;
		double executionTime =0;
		int scheduleLength =0;
	
		//networkListLinks = Driver.deserializeNetworks("testResults/onlineNetworksVaryLinksCapacity.ser");//hh
		networkListLinks = Driver.deserializeNetworks("testResults/onlineNetworksVaryVNFsNb.ser");//hh
		
		for (int j=1; j<setNb; j++)
		{
			ArrayList<Service> services =  Driver.deserializeServices("testResults/onlineServices_set"+j+"_seed"+startSeed+".ser", 0, servicesNb);//hh
			
			for (int i=0; i<networkListLinks.size(); i++)//hh
			{
				n= networkListLinks.get(i);
				on = new OnlineScheduling(services, n, timePeriod, delta);
				startlp= System.currentTimeMillis();
				
				if(algorithm.equals("TABU"))
				{
					scheduleLength = on.periodicOnlineAlgo(adjustementValue, "TABU", tabuIterations);
				}
				else if(algorithm.equals("SequentialILP"))
				{
					scheduleLength = on.periodicOnlineAlgo(adjustementValue,"SequentialILP", tabuIterations);
				}
				
			
				endlp = System.currentTimeMillis();
				executionTime = endlp-startlp;
				
				on.reportResultsForOnlineAlgo(executionTime, adjustementValue, j, scheduleLength);
				
				
			}
			startSeed+=2;
		}
	}
	
	/**
	 * This method is used to get the online CG results by considering a defined number of services for each batch
	 *
	 * @param diversify
	 * @throws IOException
	 * @throws IloException
	 */
	public static void batchResultsRun (boolean diversify) throws IOException, IloException
	{	
		//network input
		int VNFType = 5;//let it be the the min of the VNFNb so when we increase the Nb we increase the VNFs of the same type
		int pmNb = 8;
		int linkNb = 8;
		int linkWeight = 1;
		int pmNoVNF = 2;	
		int vnfNb =8;
		int linkCapacity = 500;
		ArrayList <Network>  networkList = new ArrayList<Network>() ;
				
		// services input
		int [] arrivalRate ={3,5,7,9,11};
		int departureRate = 1;
		int servicesNb = 25;
		int delta = 100;
		int minVm =3;
		int maxVM= 5;
		int minBw = 300;
		int maxBw = 500;
		int minProcessing = 1;
		int maxProcessing =5;
		int minTraffic = 500;
		int maxTraffic = 1500;
		
		//CG variables
		int diversificationConf = 3;		
		int adjustementValue = 100;
		int timePeriod = 10; //not needed
		int batchSize = 5;
		//other variables
		Network n;
		OnlineScheduling on;
		double endlp=0;;
		double startlp=0;
		double executionTime =0;
		
		//creating network
		n=new Network(vnfNb,VNFType,pmNb,linkNb,linkCapacity,linkCapacity,linkWeight,pmNoVNF);
		n.buildPhysicalNetwork();
		//serialize network
		Driver.serializeNetworks(networkList, "testResults/onlineNetworks.ser");
		
		/**
		 * Generate services based on the first network VNFTypes.
		 * Since we are using seed, we should not have the problem of VNF not existing in the network
		 */
		ServicesDriver driver = new ServicesDriver (servicesNb,  delta, minVm, maxVM, minBw, maxBw,maxProcessing , minProcessing, minTraffic, maxTraffic,n.getPhysicalNetworkArray().get(2)[0]);
		
	
		networkList = Driver.deserializeNetworks("testResults/onlineNetworks.ser");//hh
	//	ArrayList<Service> services =  Driver.deserializeServices("testResults/onlineServices.ser", 0, servicesNb);//hh
		for (int i=0; i<arrivalRate.length; i++)//hh
		{
			ArrayList<Service> services = driver.generateServices(arrivalRate[i], departureRate);
		
			Driver.serializeServices(services, "testResults/onlineServices"+arrivalRate[i]+".ser");
			on = new OnlineScheduling(services, n, timePeriod, delta);
			startlp= System.currentTimeMillis();
			
			on.onlineBatchCG(adjustementValue, diversify, diversificationConf, batchSize);		
			
			endlp = System.currentTimeMillis();
			executionTime = endlp-startlp;
			
			on.reportResults(diversify, diversificationConf,executionTime, adjustementValue,0);
		}
	}
	
	/**
	 * This method is used to get the execution time for batch of different sizes by considering that the network is already hosting some services
	 * we consider 5 sets for each batch size
	 * @param diversify
	 * @param diversificationConfigNb
	 * @param threading
	 * @throws IloException
	 * @throws IOException
	 */
	public static void onlineBtachExec(boolean diversify, int diversificationConfigNb, boolean threading) throws IloException, IOException
	{

		String pricingFilePath = "testResults/pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
		String masterFilePath = "testResults/masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
		String ilpFilePath ="testResults/masterResults/ILP.txt";
		
		int delta = 118;//prevent an error for the input
		int timePeriod = 0;//not used
		ArrayList<Service> services;
		int servicesNb = 25;
		int startSeed = 12;
		int adjustementValue = 100;
		int set = 5;
		int [] batchSizes = {1,2,3,4,5};
		Batch b;
		Batch c;
		ArrayList<Batch> batches = new ArrayList<Batch>();
		ArrayList<Configuration>optConfig;
		OnlineScheduling on, newOn;
		
		double startlp,endlp, executionTime;
		//deserialize the network
		ArrayList<Network>networkList = Driver.deserializeNetworks("testResults/onlineNetworksBatchExec.ser");
		Network n = networkList.get(0);
		
		//get optimalConfigurations so we consider that the network already have some services deployed
		optConfig = Driver.deserializeConfigurations("testResults/optimalConfigurationsForBatchExec.ser");
		
		for (int i=0; i<batchSizes.length; i++)
		{
			//for each batch size we get a file and get 5 set of batches each of size batchSizes
			services  = Driver.deserializeServices("testResults/onlineServices_set"+i+"_seed"+startSeed+".ser", 0, servicesNb);
			//on is only used to get batch
			on = new OnlineScheduling(services, n, timePeriod, delta);
			
			//get the batches of certain size
			batches = on.getBatch(batchSizes[i]);
			
			//loop over 5 batches only - test on 5 sets
			for (int j=0; j<set; j++)
			{
				//get batch
				b = batches.get(j);
			
				//update services Id before sending them to CG and batch to prevent error in the master model where service Id need to start from 0
				on.updateServicesIds(b.services);
				
				//new on is to run CG so we can only have optConfig serialized present in the network and not all previous batches, 
				newOn = new OnlineScheduling(b.services, n, timePeriod, delta);
		
				//set the optimal configurations to consider that services are already in the network; don't set it to be =optConfig because it will add conf of previous iterations
				newOn.optimalConfigurations.addAll( optConfig);
			
				startlp = System.currentTimeMillis();
				//run column generation
				if(threading)
				{
					c = newOn.onlineCGWithThread(b, diversify, diversificationConfigNb, masterFilePath, ilpFilePath, pricingFilePath);
				}
				else
				{
					c = newOn.onlineCG(b, diversify, diversificationConfigNb, masterFilePath, ilpFilePath, pricingFilePath);
				}
				endlp = System.currentTimeMillis();
				executionTime = endlp-startlp;
				
			
				//add batch to the list of batches
				newOn.batches.add(c);
				
				//add the selected configurations to the array
				newOn.optimalConfigurations.addAll(c.configurations);
			
				newOn.reportResults(diversify, diversificationConfigNb,executionTime, adjustementValue, batchSizes[i]*10+j);
			}
			
			//update seed to get other services file
			startSeed+=2;
		}
		
	}
	
	
	
	/**
	 * This method is used to get the execution time for batch of different sizes by considering that the network is already hosting some services
	 * we consider 5 sets for each batch size. all services are scheduled from time 0, arrival time is not considered
	 * 
	 * The hosted services should be mapped and scheduled based on the same algo which is CG thread no diversification here
	 * @param algorithm  TABU or SequentialILP
	 * @param tabuIterations
	 * @return total schedule length of all services
	 * @throws Exception 
	 */
	public static int onlineAlgoBatchExec(String algorithm, int tabuIterations) throws Exception
	{	
		/*
		 * Getting the optimal solution for services in network based on model
		 * TESTED NOT USED
		 */
		
	/*	int DELTA = 50;
		int timePeriod = 0;//not used
		int serviceNb =3;
		ArrayList<Network>networkList = Driver.deserializeNetworks("testResults/onlineNetworksBatchExec.ser");
		Network n = networkList.get(0);
		ArrayList<Service> initialServices = Driver.deserializeServices("testResults/onlineServicesForBatchExec.ser", 0,serviceNb);
		
		OnlineScheduling on = new OnlineScheduling(initialServices, n, timePeriod, DELTA);
		ArrayList<Batch> b = on.getBatch(serviceNb);
		on.batches.add(b.get(0));
		System.out.println(b);
		int scheduleLength = on.onlineMapScheduleServiceILP(b.get(0),null);
		ArrayList<SchedulingSolution>solutions = new ArrayList<SchedulingSolution>();
		solutions.add(on.optimalSolution);
		System.out.println(on.optimalSolution);
		 Driver.serializeSchedulingSolution(solutions, "testResults/onlineILPSchedulingSolutionForBatchExec.ser");	
		 Driver.serializeServices(b.get(0).services, "onlineILPMappedScheduledServicesForBatchExec.ser");
		 
		return scheduleLength;*/
		
		
		int delta = 100;
		int timePeriod = 0;//not used
		ArrayList<Service> services;
		int servicesNb = 25;
		int startSeed = 12;
		int adjustementValue = 100;
		int set = 5;
		int [] batchSizes = {1,2,3,4,5};
		Batch b;
	
		ArrayList<Batch> batches = new ArrayList<Batch>();
				
		OnlineScheduling on, newon;
		
		double startlp=0,endlp=0, executionTime=0;
		
		//schedule services that we need to consider in the network
		int totalScheduleLength = 0;
		
		//deserialize the network
		ArrayList<Network>networkList = Driver.deserializeNetworks("testResults/onlineNetworksBatchExec.ser");
		Network n = networkList.get(0);
		
		//Tabu initial scheduling of services to consider in network		
		int startTimeslot =0; //consider all services starts at 0 disregard arrival time
		int scheduleLength =0;
		 int highestDeadline =500; //put it as big as possible because we need to have vnfs initialized to -1 and links to capacity
		 ArrayList<ArrayList<Integer>>vnfs = n.getVnfsTime (highestDeadline);
		 ArrayList<ArrayList<Integer>>links = n.getLinksTimeBw(highestDeadline);		 
		TabuMappingScheduling tab = new TabuMappingScheduling(n, "logs/tabuHeuristic.txt");
		//ArrayList<Service> initialServices = Driver.deserializeServices("testResults/onlineServicesForBatchExec.ser", 0,3);
		
		ArrayList<Service> CGMappedScheduledInitialServices = Driver.deserializeServices("testResults/onlineCGMappedScheduledServicesForBatchExec.ser",0,3);
	
		
		for (int i=0; i<batchSizes.length; i++)
		{
			//for each batch size we get a file and get 5 set of batches each of size batchSizes
			services  = Driver.deserializeServices("testResults/onlineServices_set"+i+"_seed"+startSeed+".ser", 0, servicesNb);
			//on is only used to get batch
			on = new OnlineScheduling(services, n, timePeriod, delta);
			
			//get the batches of certain size
			batches = on.getBatch(batchSizes[i]);
			
			//loop over 5 batches only - test on 5 sets
			for (int j=0; j<set; j++)
			{
				//get batch
				b = batches.get(j);
				
				newon = new OnlineScheduling(b.services, n, timePeriod, delta);
				
				//add batch to the list of batches
				newon.batches.add(b);
				
				//run tabu
				if(algorithm.equals("TABU"))
				{
					//start by scheduling services already in network this is done for maintaining vnfs and links to initial services only -needed only if we want to schedule based on tabu-Tested NOT USED
					///totalScheduleLength = tab.tabuMappingScheduling(initialServices, tabuIterations, startTimeslot, vnfs, links, adjustementValue);
					
					vnfs = n.getVnfsTime (highestDeadline);
					links = n.getLinksTimeBw(highestDeadline);	
					 
					//start by updating vnfs and links based on services in network
					tab.initializeVnfsLinks(CGMappedScheduledInitialServices, vnfs, links);
				
					totalScheduleLength = newon.getScheduleLength(CGMappedScheduledInitialServices);
				
					startlp = System.currentTimeMillis();
					
					//use same vnfs and links as they will contain info of services in network
					 scheduleLength = tab.tabuMappingScheduling(b.services,tabuIterations, startTimeslot, vnfs, links,adjustementValue);
					 
					b.ilpObjective = scheduleLength;				
					b.CGIterations = tabuIterations;
					
					if(scheduleLength>totalScheduleLength)
					{
						totalScheduleLength=scheduleLength;
					}
					
					//reset vnfs and links
					vnfs = n.getVnfsTime (highestDeadline);
					links = n.getLinksTimeBw(highestDeadline);
				}
				else if (algorithm.equals("SequentialILP"))
				{
					//newon.optimalSolution = Driver.deserializeSchedulingSolution("testResults/onlineILPSchedulingSolutionForBatchExec.ser").get(0);
										
					startlp = System.currentTimeMillis();
					scheduleLength = newon.onlineMapScheduleServiceILP(b,CGMappedScheduledInitialServices);//this will also update optimal solution to handle existing services in network
										
					b.ilpObjective = scheduleLength; //this will be schedule length for each batch independent from the others
					totalScheduleLength = newon.optimalSolution.objectiveValue;
				}
				endlp = System.currentTimeMillis();
				executionTime = endlp-startlp;
				b.ilpExecutionTime = executionTime;			
					
				newon.reportResultsForOnlineAlgo(executionTime, adjustementValue, batchSizes[i]*10+j,  totalScheduleLength);
			
			}
			
			//update seed to get other services file
			startSeed+=2;
			
		}
		
		return totalScheduleLength;
	}
	
	public static void checkInputs(){
		ArrayList<Network> net = Driver.deserializeNetworks("testResults/onlineNetworks.ser");
		Network n = net.get(0);
		int []tf;
		String s ="";
		System.out.println ("ID \t VNFNb \t LinkBw \t tf \n");
		for (int i=0; i<net.size(); i++)
		{
			n= net.get(i);
			s+=n.getId()+" \t "+n.getVNFNb()+" \t "+n.getMinLinkCapacity()+" \t ";
			tf  = n.getPhysicalNetworkArray().get(2)[0];
			for (int j=0; j<tf.length; j++)
			{
				s+=tf[j]+"; ";
			}
			s+="\n";
		}
		System.out.println (s);
		
		ArrayList<Service> se = Driver.deserializeServices("testResults/onlineServices.ser", 0, 25);
		Service ss;
		OnlineScheduling on = new OnlineScheduling(se, n, 10, 100);
		ArrayList<Batch> b = on.getBatch(3);
		String s1 ="";
		System.out.println ("ID \t  Middleboxes \n");
		for(int k=0; k<b.size(); k++)
		{	for (int i=0; i<b.get(k).services.size(); i++)
			{
				ss=  b.get(k).services.get(i);//se.get(i);
				s1+=ss.getId()+" \t ";
				
				for (int j=0; j<ss.getMiddleboxes().size(); j++)
				{
					s1+=ss.getMiddleboxes().get(j).getType()+"; ";
				}
				s1+="\n";
			}
	
		}
		System.out.println (s1);
		
	}
	public static void main(String[] args) throws Exception {
		//OnlineScheduling.checkInputs();
		//OnlineScheduling.resultsRun(false, true);
		//OnlineScheduling.batchResultsRun(false);
	//	OnlineScheduling.onlineBtachExec(true,3,true);
		//OnlineScheduling.onlineBtachExec(false,0,true);
		
		OnlineScheduling.onlineAlgoBatchExec("TABU",4);
		
		//OnlineScheduling.AlgoResultsRun(4, "SequentialILP");
	//	OnlineScheduling.AlgoResultsRun(4, "SequentialILP");
		
		// TODO Auto-generated method stub
		/*int delta = 200;
		
		//create the network
		//Network  network = new Network(10,5,8,8,700,700,1,3);//problem runs indefinitely
		//Network  network = new Network(10,5,8,8,700,700,1,3);//no prob //500 //600 - VNF 13, 10
		Network  network = new Network(6,5,4,5,500,500,1,0);
		ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();			 
		
		 //get the types of VNFs in the network
		int[] tf = physicalNetwork.get(2)[0];	
		System.out.println(network);
		 //generate services
		ServicesDriver driver = new ServicesDriver (7,  delta, 3, 5, 300, 500, 5 , 1, 500, 1500,tf);
		ArrayList<Service> services = driver.generateServices(50, 10);
		
		for(int i=0; i<services.size(); i++)
		{
			System.out.println(services.get(i).getArrivalTime()*100);
		}
		OnlineScheduling on = new OnlineScheduling(services, network, 10, delta);
		on.periodicOnlineAlgo(100, "TABU",4);
		
		for(int i=0; i<on.batches.size(); i++)
		{
			System.out.println(on.batches.get(i));
		}*/
	}

}
