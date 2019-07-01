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
import HelperClasses.ServicesDriver;
import Model.CGMasterModel;
import Model.CGPricingModel;
import Model.Configuration;
import NFV.Service;
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
	
	public OnlineScheduling (ArrayList<Service> services, Network network, int timePeriod, int delta )
	{
		this.network = network;
		this.services = services;
		this.timePeriod = timePeriod;
		this.DELTA = delta;
		this.optimalConfigurations = new ArrayList<Configuration>();
		this.batches = new ArrayList<Batch>();
	}
	

	/**
	 * This function performs CG periodically on services 
	 * @param adjustmentValue adjustment value of the arrival time
	 * @param diversify set to true if we want to diversify the CG solution
	 * @param diversificationConfigNb nb of configuration to generate by the diversification heuristic
	 * @throws IloException
	 * @throws IOException
	 */
	public void periodicOnlineCG (int adjustmentValue, boolean diversify, int diversificationConfigNb) throws IloException, IOException
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
			b = this.onlineCG(b, diversify, diversificationConfigNb, masterFilePath, ilpFilePath, pricingFilePath);
			
			//add batch to the list of batches
			this.batches.add(b);
			
			//add the selected configurations to the array
			this.optimalConfigurations.addAll(b.configurations);
			
			
		}

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
	 * @NOT USED
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
	public Batch onlineCG(Batch batch, boolean diversify, int nbOfConfigPerService, String masterFilePath,String ilpFilePath, String pricingFilePath) throws IloException, IOException
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
		int allowedIterations = 50; //max number of iterations allowed to terminate if CG did not converge
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
	 * This function will report the results of the periodicOnline CG
	 * 
	 * @param diversify
	 * @param diversificationConf
	 * @param executionTime
	 * @param adjustementValue
	 * @throws IOException
	 */
	public void reportResults(boolean diversify, int diversificationConf, double executionTime, int adjustementValue) throws IOException
	{
		 Batch b;
		 String a="{";
		 String d="{";
		
		 FileManipulation resultsFile = new FileManipulation("testResults/onlineResults.txt");
		 FileManipulation batchResultsFile = new FileManipulation("testResults/onlineResultsByBatch.txt");
		 
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
				 a+=b.services.get(i).getArrivalTime()*adjustementValue+",";
				 d+=b.services.get(i).getDepartureTime()*adjustementValue+",";
			 }
			 a+="}";
			 d+="}";
			 batchResultsFile.writeInFile(a+"\t");
			 batchResultsFile.writeInFile(d+"\t");
			 batchResultsFile.writeInFile("\n");
		 }
		 batchResultsFile.writeInFile("\n\n");
	}
	
	/**
	 * This method is used to get the online cg results by considering a period length to get the batches
	 * @param diversify
	 * @throws IOException
	 * @throws IloException
	 */
	public static void resultsRun (boolean diversify) throws IOException, IloException
	{	
		//network input
		int VNFType = 5;//let it be the the min of the VNFNb so when we increase the Nb we increase the VNFs of the same type
		int pmNb = 8;
		int linkNb = 8;
		int linkWeight = 1;
		int pmNoVNF = 2;	
		
		//testing over network of different VNF nb and links capacity
		int [] VNFNb = {4,12,16};//{5,10,15,20};
		int [] linksCapacity = {550,650,750, 850};//{500,600,700,800};	
		int VNFVaryLink = 8;
		int linkVaryVNF = 500;
		ArrayList <Network>  networkList = new ArrayList<Network>() ;
				
		// services input
		int arrivalRate = 50;
		int departureRate = 10;
		int servicesNb = 25;
		int delta = 500;
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
		int timePeriod = 10;//increasing the time period will increase DELTA .. more CG runTime
		int adjustementValue = 100;
			
		//other variables
		Network n;
		OnlineScheduling on;
		double endlp=0;;
		double startlp=0;
		double executionTime =0;
		
		//creating networks of different VNFNB and LInk NB to test 
		/*for (int i=0; i<VNFNb.length; i++)
		{
			n=new Network(VNFNb[i],VNFType,pmNb,linkNb,linkVaryVNF,linkVaryVNF,linkWeight,pmNoVNF);
			n.buildPhysicalNetwork();
			networkList.add(n);
		}
		
		for (int i=0; i<linksCapacity.length; i++)
		{
			n=new Network(VNFVaryLink,VNFType,pmNb,linkNb,linksCapacity[i],linksCapacity[i],linkWeight,pmNoVNF);
			n.buildPhysicalNetwork();
			networkList.add(n);
		}
		
		//serialize network
		Driver.serializeNetworks(networkList, "testResults/onlineNetworks.ser");*/
		
		/**
		 * Generate services based on the first network VNFTypes.
		 * Since we are using seed, we should not have the problem of VNF not existing in the network
		 */
	/*	n= networkList.get(0);
		ServicesDriver driver = new ServicesDriver (servicesNb,  delta, minVm, maxVM, minBw, maxBw,maxProcessing , minProcessing, minTraffic, maxTraffic,n.getPhysicalNetworkArray().get(2)[0]);
		ArrayList<Service> services = driver.generateServices(arrivalRate, departureRate);
		Driver.serializeServices(services, "testResults/onlineServices.ser");*/
	
		networkList = Driver.deserializeNetworks("testResults/onlineNetworks.ser");//hh
		ArrayList<Service> services =  Driver.deserializeServices("testResults/onlineServices.ser", 0, servicesNb);//hh
		for (int i=0; i<networkList.size(); i++)//hh
		{
			n= networkList.get(i);
			on = new OnlineScheduling(services, n, timePeriod, delta);
			startlp= System.currentTimeMillis();
			
			on.periodicOnlineCG(adjustementValue, diversify, diversificationConf);		
			
			endlp = System.currentTimeMillis();
			executionTime = endlp-startlp;
			
			on.reportResults(diversify, diversificationConf,executionTime, adjustementValue);
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
		int delta = 500;
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
			
			on.reportResults(diversify, diversificationConf,executionTime, adjustementValue);
		}
	}
	
	public static void checkInputs(){
		ArrayList<Network> net = Driver.deserializeNetworks("testResults/onlineNetworks.ser");
		Network n;
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
	
		String s1 ="";
		System.out.println ("ID \t  Middleboxes \n");
		for (int i=0; i<se.size(); i++)
		{
			ss=  se.get(i);
			s1+=ss.getId()+" \t ";
			
			for (int j=0; j<ss.getMiddleboxes().size(); j++)
			{
				s1+=ss.getMiddleboxes().get(j).getType()+"; ";
			}
			s1+="\n";
		}
		System.out.println (s1);
		
	}
	public static void main(String[] args) throws IOException, IloException {
	
		//OnlineScheduling.resultsRun(false);
		OnlineScheduling.batchResultsRun(false);
		// TODO Auto-generated method stub
		/*int delta = 200;
		
		//create the network
		//Network  network = new Network(10,5,8,8,700,700,1,3);//problem runs indefinitely
		Network  network = new Network(10,5,8,8,700,700,1,3);//no prob //500 //600 - VNF 13, 10
		ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();			 
		
		 //get the types of VNFs in the network
		int[] tf = physicalNetwork.get(2)[0];	
		
		 //generate services
		ServicesDriver driver = new ServicesDriver (10,  delta, 3, 5, 200, 500, 5 , 1, 500, 1500,tf);
		ArrayList<Service> services = driver.generateServices(50, 10);
		
		for(int i=0; i<services.size(); i++)
		{
			System.out.println(services.get(i).getArrivalTime()*100);
		}
		OnlineScheduling on = new OnlineScheduling(services, network, 10, delta);
		on.periodicOnlineCG(100, true, 3);
		
		for(int i=0; i<on.batches.size(); i++)
		{
			System.out.println(on.batches.get(i));
		}*/
	}

}
