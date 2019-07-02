/**
 * This class is used to get the final results
 */
package mainPackage;

import java.io.IOException;
import java.util.ArrayList;

import HelperClasses.FileManipulation;
import HelperClasses.SerializedObjects;
import SchedulingHeuristic.*;
import Model.ILPModelModified;
import NFV.Service;
import Network.Network;

public class ResultsRun {

	/**
	 * 
	 * This method will report the results to a file "testResults/"+algorithm+"_type_n"+n.getId()+".txt"
	 * It can be used to offline and online results
	 * @param networkFile
	 * @param servicesFiles
	 * @param algorithm : Tabu, RRB, Random or ILP (ILP only for offline), onlineBatchRRB, onlineBatchRandom
	 * @param type if offline or online for the file name
	 * @throws Exception
	 */
	public static void resultsRunAlgo (String networkFile,  String[] servicesFiles, String algorithm, String type) throws Exception
	{
		//some values that we do not need to change
		String logFile = "testResults/log.txt"; //file to dump errors if any
		
		String ILPInputResultFile = "testResults/ILP.txt";//file to dump input an result of the ilp
		String statusFileName = "testResults/statusFile.txt";
		
		//some values for tabu
		int itrNb = 10;
		int canLength=5;
		int tbCount=3;
				
		//some values for online
		int [] batchSize = {15,20};//{2,5,10,15,20};
		int tabu_LR_iterations =4;
		//some variables
		Network n = null;
		ArrayList<Service> services = new ArrayList<Service>();
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		SchedulingHeuristic sh =null;
		OnlineBatchScheduling onBatch = null;
		TabuScheduling tb= null;
		TabuMappingScheduling_LR tb_LR = null;
		double startTime = 0;
		double endTime = 0;
		double execTime =0;
		int admittedServicesNb =0;
		double [] ILPResults = new double[2];//hold the objective value and exectime of the ILp
		FileManipulation statusFile = new FileManipulation(statusFileName);
		
		//get the list of networks
		ArrayList<Network> networkList = SerializedObjects.deserializeNetworks(networkFile);
		
		//if the file contains many networks it will run on all of them
		for (int i=0; i<networkList.size(); i++)
		{
			//get the network
			n= networkList.get(i);
			
			//create the results file for each network and write its header only
			FileManipulation resultsFile = new FileManipulation("testResults/"+algorithm+"_"+type+"_n"+n.getId()+".txt");
			//ResultsRun.reportResults(resultsFile, 0, n, admittedServices,0, 0, 0, true);
		
			
			//create the scheduling heuristic to be able to run the algorithms, log file just in case of errors
			sh = new SchedulingHeuristic(n,logFile );
			//create tabu object
			tb = new TabuScheduling(n, logFile);
			tb_LR = new TabuMappingScheduling_LR(n, logFile);
			for(int k=0; k<batchSize.length;k++)
			{
				for (int j=0; j<servicesFiles.length; j++)
				{
				
					//get the services in each file
					services =  SerializedObjects.deserializeServices(servicesFiles[j]);
				
					//keep track of what is executing
					statusFile.writeInFile("Running for network "+n.getId()+" set "+j+" nb of services "+services.size()+" Algorithm "+algorithm+"\n");
					
					if(algorithm.equals("RRB"))
					{
						startTime = System.currentTimeMillis();
						
						admittedServices = sh.mapScheduleServiceRRBILP(services, false);
						
						endTime = System.currentTimeMillis();
						execTime = endTime - startTime;
						admittedServicesNb = admittedServices==null?0:admittedServices.size();
					}
					else if(algorithm.equals("Random"))
					{
						startTime = System.currentTimeMillis();
						
						admittedServices = sh.mapScheduleServiceRRBILP(services, true);
						
						endTime = System.currentTimeMillis();
						execTime = endTime - startTime;
						admittedServicesNb = admittedServices==null?0:admittedServices.size();
					}
					else if (algorithm.equals("Tabu"))//support online (arrival time!=0 , same for all batch size) or offline (arrival time=0)
					{
						startTime = System.currentTimeMillis();
					
						admittedServices = tb.tabuSearch(services,  itrNb, canLength, tbCount);
						
						endTime = System.currentTimeMillis();
						execTime = endTime - startTime;
						admittedServicesNb = admittedServices==null?0:admittedServices.size();
					}
					else if (algorithm.equals("Tabu_LR") )//support online (arrival time!=0 same for all batch size) or offline (arrival time=0)
					{
						startTime = System.currentTimeMillis();
					
						admittedServices = tb_LR.tabuMappingScheduling(services, tabu_LR_iterations, -1);
								
						
						endTime = System.currentTimeMillis();
						execTime = endTime - startTime;
						admittedServicesNb = admittedServices==null?0:admittedServices.size();
					}
					else if (algorithm.equals("ILP"))
					{
						ILPResults = ILPModelModified.runModelForResults (n, services,ILPInputResultFile);
						admittedServicesNb = (int) ILPResults[0];
						execTime = ILPResults[1];
					}
					else if (algorithm.equals("onlineBatchRRB"))
					{
						//create online batch scheduling
						onBatch = new OnlineBatchScheduling(n, services);
						startTime = System.currentTimeMillis();
						
						admittedServices = onBatch.onlineMapScheduleServiceRRBILP(batchSize[k], false,true);
						
						endTime = System.currentTimeMillis();
						execTime = endTime - startTime;
						admittedServicesNb = admittedServices==null?0:admittedServices.size();
					}
					else if(algorithm.equals("onlineBatchRandom"))
					{
						//create online batch scheduling
						onBatch = new OnlineBatchScheduling(n, services);
						startTime = System.currentTimeMillis();
						
						admittedServices = onBatch.onlineMapScheduleServiceRRBILP(batchSize[k],  true,true);
						
						endTime = System.currentTimeMillis();
						execTime = endTime - startTime;
						admittedServicesNb = admittedServices==null?0:admittedServices.size();
					}
			
				
					ResultsRun.reportResults(resultsFile,j,n, services,batchSize[k], execTime,admittedServicesNb,false, servicesFiles[j]);
					
					//reset variables for the next run
					
					admittedServices = new ArrayList<Service>();
					ILPResults = new double [2];
					 startTime = 0;
					 endTime = 0;
					 execTime =0;
				}
			}
		}
		
	
	}
	
	/**
	 * 
	 * @param resultsFile
	 * @param setId
	 * @param network
	 * @param services
	 * @param executionTime
	 * @param admittedServicesNb
	 * @param headerOnly
	 */
	public static void reportResults(FileManipulation resultsFile, int setId, Network network, ArrayList<Service>services,int batchSize, double executionTime, double admittedServicesNb, boolean headerOnly, String serviceFile)
	{	
		double admissionRate = (admittedServicesNb/services.size())*100;
		if (headerOnly)
		{
			 resultsFile.writeInFile("#NetworkId\t");
			 resultsFile.writeInFile("ServiceFile\t");
			 resultsFile.writeInFile("Set Id\t");
			 resultsFile.writeInFile("VNF Nb\t");
			 resultsFile.writeInFile("Link Capacity\t");
			 resultsFile.writeInFile("Nb of services\t");
			 resultsFile.writeInFile("Batch Size (onlineBatchScheduling)\t");
			 resultsFile.writeInFile("ExecutionTime (ms)\t");
			 resultsFile.writeInFile("Admitted Service NB \t");	
			 resultsFile.writeInFile("Admission Rate (%)\t");	
			 resultsFile.writeInFile("\n");
		}
		else
		{
			 resultsFile.writeInFile("\n");
			 resultsFile.writeInFile(network.getId()+"\t");
			 resultsFile.writeInFile(serviceFile+"\t");
			 resultsFile.writeInFile(setId+"\t");
			 resultsFile.writeInFile(network.getVNFNb()+"\t");
			 resultsFile.writeInFile(network.getMaxLinkCapacity()+"\t");
			 resultsFile.writeInFile(services.size()+"\t");
			 resultsFile.writeInFile(batchSize+"\t");
			 resultsFile.writeInFile(executionTime+"\t");
			 resultsFile.writeInFile(admittedServicesNb+"\t");
			 resultsFile.writeInFile(admissionRate+"\t");
			
		}	 
		
		
	}
	
	public static void main(String[] args) throws Exception
	{
		
		//online run
		String [] servicesFile = new String[5];//5,25

		int nbOfSets =5;//5;
		int [] seed = {43,64, 18,21,24};//{12,15,18,21,24};//{12,33,42,66,50};//{12,15,18,21,24};
		int [] arrivalRates ={50};//{20,40,60,80,100};//50
		int count =0;
		for(int j=0;j<arrivalRates.length;j++)
		{
			for (int i=0; i<nbOfSets; i++)
			{			
				servicesFile[count] = "testResults/onlineBatchInputs/onlineBatchServices_Arr"+arrivalRates[j]+"_set"+i+"_seed"+seed[i]+".ser";
				System.out.println (servicesFile[count]);
				count++;
			}
		}
		String networksFile = "testResults/onlineBatchInputs/OnlineBatchNetwork.ser";
		
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Tabu", "online");
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Tabu_LR", "online");
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "onlineBatchRRB", "online");
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "onlineBatchRandom", "online");
		
		
		//offline run
		/*String networksFile = "testResults/offlineResults/OfflineResults_500GbNetwork/OfflineInputs/offlineNetwork.ser";
		int [] servicesNb ={2};//{5, 10, 15, 20,25, 30};	// 25, 30
		int nbOfSets = 5;
		int [] seed = {12,15,18,21,24};//{12,33,42,66,50};
		String [] servicesFile = new String[5];//30
		int count=0;
		for (int j=0; j<servicesNb.length; j++)
		{
			//offline generation
			for (int i=0; i<nbOfSets; i++)
			{
				servicesFile[count] = "testResults/offlineResults/OfflineResults_500GbNetwork/OfflineInputs/offlineServices_sNb"+servicesNb[j]+"_set"+i+"_seed"+seed[i]+".ser";
				System.out.println (servicesFile[count]);
				count++;
			}
		}
		
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Tabu", "offline");
		/*ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Tabu_LR", "offline");
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "RRB", "offline");
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Random", "offline");
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "ILP", "offline");*/
		
	}
}
