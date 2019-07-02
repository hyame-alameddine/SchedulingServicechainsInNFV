package SchedulingHeuristic;

import ilog.concert.IloException;

import java.io.IOException;
import java.util.ArrayList;

import HelperClasses.Batch;
import HelperClasses.FileManipulation;
import HelperClasses.ServicesDriver;
import Model.SchedulingModel;
import NFV.Service;
import Network.Network;

public class OnlineBatchScheduling {
	public Network network;
	public ArrayList<Service> services;
	public ArrayList<Batch> batches;

	
public OnlineBatchScheduling (Network n, ArrayList<Service> services)
{
	this.network = n;
	this.services = services;
	this.batches = new ArrayList<Batch>();
	
}


/**
 * This function returns an array of batches where each has the 
 * number of services = batchSize
 * 
 * @param batchSize number of services in each batch 
 * 
 * @return array of batches
 */
	public ArrayList<Batch> getBatches (int batchSize)
	{	
		ArrayList<Service> serviceBatch = new ArrayList<Service>();
		ArrayList<Batch> batches = new ArrayList<Batch>();
		Batch b;
		
		int count=0;
		int batchId =0;
		for(int i=0; i<this.services.size(); i++)
		{	
			serviceBatch.add(this.services.get(i));
			
			count++;
			
			if(count == batchSize)
			{
				b = new Batch (batchId,serviceBatch);				
				batches.add(b);
				serviceBatch = new ArrayList<Service>();
				count =0;
				batchId++;
			}				
			
		}
		
		//if we got here means that the last batch is not of the specified size but we do want to process it
		if (serviceBatch.size()!=0)
		{
			b = new Batch (batchId,serviceBatch);	
			batches.add(b);
		}
		
		return batches;
			
	}
	
	/**
	 * This function will map and schedule a list of services in a batch
	 * It will return null if all the services can not be mapped, routed or scheduled or it will return the set of admitted services
	 * 
	 * The Mapping will be roud robin
	 * the routing based on the shortest path having available bw
	 * the scheduling is based on SchedulingModel by setting the variables of the previous batches in optimalScheduling
	 * 
	 * @param services services to map, route and schedule
	 * @param adjustementValue for the arrival time
	 * @param random mapping if true use random maping, else use round robin
	 * @return list of configuration for each service -null if any of the services can not be mapped, routed or scheduled
	 * @throws Exception 
	 */
	public ArrayList<Service> onlineMapScheduleServiceRRBILP(int  batchSize,   boolean randomMapping, boolean reportBatchResults) throws Exception
	{	
		FileManipulation f = null;
		String fileName = randomMapping?"testResults/batchesResults/random_": "testResults/batchesResults/RRB_";
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		this.batches = this.getBatches(batchSize);
		
		for(int i=0; i<this.batches.size(); i++)
		{
			
			admittedServices = this.onlineMapScheduleServiceRRBILP(this.batches.get(i),randomMapping);
			if(reportBatchResults)
			{
				f=new FileManipulation(fileName+"size"+batchSize+"_"+i+".txt");
				f.writeInFile(this.batches.get(i).toString());
				
			}
			
		}
		
		return admittedServices;
	}
	
	
	/**
	 * This function will map and schedule a list of services in a batch
	 * It will return null if all the services can not be mapped, routed or scheduled or it will return the set of admitted services
	 * 
	 * The Mapping will be round robin
	 * the routing based on the shortest path having available bw
	 * the scheduling is based on SchedulingModel by setting the variables of the previous batches in optimalScheduling
	 * 
	 * @param services services to map, route and schedule
	 * 
	 * @param random mapping if true use random mapping, else use round robin
	 * @return list of configuration for each service -null if any of the services can not be mapped, routed or scheduled
	 * @throws Exception 
	 */
	public ArrayList<Service> onlineMapScheduleServiceRRBILP(Batch b,boolean randomMapping) throws Exception
	{	
		String logFileName ="log.txt";
		
		SchedulingHeuristic sh = new SchedulingHeuristic(this.network,logFileName );
		 ArrayList<Service> admittedServices = null;
		 ArrayList<Service> servicesToSchedule = new ArrayList<Service>();
		Service updatedService , s;
		int [][] lastUsedVNFs = new int [this.network.getVNFTypesNb()][1];
		
		for(int i=0; i<b.services.size(); i++)
		{	
			s= b.services.get(i);
			updatedService = s;
			
			if(randomMapping)
			{
				//map VNFs randomly
				updatedService = sh.mapMiddleboxes(s);
			}
			else
			{
				//map services using round robin
				updatedService = sh.mapMiddleboxesRoundRobin(s, lastUsedVNFs);
			}
			
			// if service can be mapped try routing it
			if (updatedService != null)
			{
				updatedService = sh.mapVirtualLinks(s);
			}
			
			
			//if service can not be mapped or routed reject it
			if (updatedService == null)
			{
				s.releaseResources();
				s.setIsAdmitted(false);
			}
			else
			{
				servicesToSchedule.add(s);
				System.out.println("Services mapped and routed to send to ILp");
				System.out.println(s);
			}			
							
		}
		
		b.servicesToSchedule = servicesToSchedule;
		//schedule services that can be routed and mapped	from the batch services
		admittedServices = this.scheduleServicesILP(b);

		return admittedServices;
		
	}
	
	
	
	/**
	 * This method will schedule services based on SchedulingModelILP starting their schedule at their arrival time
	 * it will report the model results to testResults/schedILPResults.txt
	 * 
	 * @param batch containing the mappedRouted services to schedule set
	 * 
	 * @return list of admitted services that were able to be scheduled
	 * 
	 * @throws IloException
	 * @throws IOException 
	 */
	public ArrayList<Service> scheduleServicesILP (Batch b) throws IloException, IOException
	{
		double startTime = 0;
		double endTime = 0;
		double execTime =0;
		
		String resultsFile = "testResults/schedILPResults.txt";
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		
		//holds the servicestoSchedule of previous batches (in optimalSolution)
		ArrayList<Service> scheduledServices = this.getScheduledServices(b);
	
		//set the time line to the max deadline of all the services in all the batches. This is to handle services of past batches/ prevent any error
		int delta = ServicesDriver.getHighestDeadline(this.services)+1;
	System.out.println("dddd"+delta);
		
		startTime = System.currentTimeMillis();
		SchedulingModel schedModel = new SchedulingModel(this.network, b.services, delta);
		
		//if some services are scheduled we should take them into consideration
		if (scheduledServices.size()!=0)
		{
			//set the variables of the optimal solution (of previous batches)
			schedModel.updateResources(scheduledServices);
		}
		
		schedModel.buildILPModel();			
		
		admittedServices = schedModel.runILPModel(resultsFile, true);
		endTime = System.currentTimeMillis();
		execTime = endTime - startTime;
		
		//update batch info
		b.ilpExecutionTime = execTime;
		b.batchObjective = admittedServices.size();
		b.overallObjective = b.batchObjective+OnlineBatchScheduling.getAdmittedServices (scheduledServices).size();
		
		//get all admitted services from previous batches in addition to this one
		admittedServices.addAll(OnlineBatchScheduling.getAdmittedServices (scheduledServices));			
		
		return admittedServices;
		
	}
	
	/**
	 * This method will return an array of admitted services based on the one passed as parameter
	 * @param services
	 */
	public static ArrayList<Service> getAdmittedServices(ArrayList<Service> services)
	{
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		
		for (int i=0; i<services.size(); i++)
		{
			if (services.get(i).getIsAdmitted())
			{
				admittedServices.add(services.get(i));
			}
		}
		
		return admittedServices;
	}
	
	/**
	 * This method will return an array of all the services that were passed to the model in the previous batches
	 * 
	 * @param b batch that we want to schedule
	 * @return array of services scheduled 
	 */
	public ArrayList<Service> getScheduledServices(Batch b)
	{
		ArrayList<Service> scheduledAndToScheduleServices = new ArrayList<Service>();
	
		//get all the batches that were processed in addition to this batch b
		for(int i=0; i<b.id ; i++)
		{ 
			scheduledAndToScheduleServices.addAll(this.batches.get(i).servicesToSchedule);
		}
		
		return scheduledAndToScheduleServices;
	}
	
	public static void main(String[]args) throws Exception
	{
		int DELTA=450;
		int adjustmentValue = 100;
		int batchSize = 2;
		ArrayList<Service>admittedServices=null;
		Network  graph = new Network(20,5,8,10,1000,1000,1,0);
		//Network  graph = new Network(5,5,5,6,700,700,1,0);
	   ArrayList<int[][]> physicalNetwork = graph.buildPhysicalNetwork();
		int [] tf = physicalNetwork.get(2)[0];

		// System.out.println(graph);
		
		ServicesDriver driver = new ServicesDriver (100,  DELTA, 3,5, 300, 500, 5 , 1, 500, 1500,tf);
		ArrayList<Service>services = driver.generateServices(50,10,adjustmentValue,12);
		//ArrayList<int[][]> rservices = driver.generateServicesForModel();
	//	ArrayList<Service> services = driver.convertGeneratedServices(rservices);
		SchedulingHeuristic sh = new SchedulingHeuristic(graph,"ServicesHeuristic.txt");
		
			double startTime = System.currentTimeMillis();
			
		//	admittedServices = sh.mapScheduleServiceRRBILP(services,false);
			OnlineBatchScheduling  onb = new OnlineBatchScheduling(graph, services);
		admittedServices = onb.onlineMapScheduleServiceRRBILP(batchSize,  false, true);
			double endTime = System.currentTimeMillis();
			double execTime = endTime - startTime;
			int admittedServicesNb = admittedServices==null?0:admittedServices.size();
		
		
		System.out.println("Admitted "+admittedServicesNb);
		System.out.println("Exec  "+execTime);
	}
}