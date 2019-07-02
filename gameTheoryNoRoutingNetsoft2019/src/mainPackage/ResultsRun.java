/**
 * This class is used to get the final results
 */
package mainPackage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import GameTheory.Game;
import GameTheory.Strategy.StrategyType;
import HelperClasses.FileManipulation;
import HelperClasses.SerializedObjects;
import HelperClasses.ServicesDriver;
import SchedulingHeuristic.*;
import Model.ILPMaxAdmission;
import Model.ILPMinMaxCT;
import Model.ILPMinSumCT;
import NFV.Service;
import Network.Network;

public class ResultsRun {

	/**
	 * 
	 * This method will report the results to a file "testResults/"+algorithm+"_type_n"+n.getId()+".txt"
	 * It can be used to offline and online results
	 * @param networkFile
	 * @param servicesFiles
	 * @param algorithm :game_mixedStrategy Tabu_LR, Tabu, RRB, Random or ILPMaxAdmission (ILP only for offline), ILPMinSumCT,ILPMinMaxCT, onlineBatchRRB, onlineBatchRandom,game_longestPath, game_shortestPath,game_payOffAvgCompletionTime
	 * @param type if offline or online for the file name
	 * @param criteria = completionTime or avgCompletionTime or payOff, based if we are checking on nash for completion or avgCompletionTime
	 * @param nashValue this is for the game. if 0 then we are checking for nash if >0 then we are checking for e-nash
	 * @throws Exception
	 */
	public static void resultsRunAlgo (String networkFile,  String[] servicesFiles, String algorithm, String type, Network n, ArrayList<Service>services, String criteria, double nashValue) throws Exception
	{
		//some values that we do not need to change
		String logFile = "testResults/log.txt"; //file to dump errors if any
		int adjustementValue =10; //100//needed for arrival time, Since offline set it to 0
		String ILPMaxAdmissionInputResultFile = "testResults/ILPMaxAdmission_detailed.txt";//file to dump input an result of the ilp
		String ILPMinSumCTInputResultFile = "testResults/ILPMinSumCT_detailed.txt";
		String ILPMinMaxCTInputResultFile = "testResults/ILPMinMaxCT_detailed.txt";
		String statusFileName = "testResults/statusFile.txt";
		String gameFileNameShortest = "testResults/gameFileShortestPath_detailed.txt";
		String gameFileNameLongest = "testResults/gameFileLongestPath_detailed.txt";
		String gameFileNamePayOff = "testResults/gameFilePayOffAvgCT_detailed.txt";
		String gameFileNameMixed = "testResults/gameFileMixedStrategy_detailed.txt";
		String gameFileNameMixedILP = "testResults/gameFileMixedStrategyILP_detailed.txt";
		//some values for tabu
		int itrNb = 10;
		int canLength=5;
		int tbCount=3;
		
	
		
				
		//some variables for game
		int multiplier = 3;//needed only when considering link scheduling
		
		//some values for online
		int batchSize = 20;//2,5,7,10,15
		
		//some variables
		//Network n = null;
		//ArrayList<Service> services = new ArrayList<Service>();
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		SchedulingHeuristic sh =null;
		OnlineBatchScheduling onBatch = null;
		TabuScheduling tb= null;
		TabuMappingScheduling_LR tb_LR = null;
		int tabu_LR_iterations =4;
		
		Game g = null;
		double enashValue = 0.5; //value for enash to stop when checking twoConsecutiveAvgCompletionTime
		double startTime = 0;
		double endTime = 0;
		double execTime =0;
		int admittedServicesNb =0;
		double [] ILPResults = new double[2];//hold the objective value and exectime of the ILp
		double [] ILPGameResults = new double [3];//hold the objective, exec time and admitted req nb for game ILP
		FileManipulation statusFile = new FileManipulation(statusFileName);
		
		//services inputs
		int[] seed ={12,33,55,67,96};
		int[] nbServices ={25};// {2,5,10,15,20};//50
		
		//create the network
		//n = new Network(7,5,4,5,1000,1000,1,0);
		n = new Network(30,7,25,27,1000,1000,1,0);
		ArrayList<int[][]> physicalNetwork = n.buildPhysicalNetwork();
		System.out.println(n);
		int [] tf = physicalNetwork.get(2)[0];
		
		//create the results file for each network and write its header only
		FileManipulation resultsFile = new FileManipulation("testResults/"+algorithm+"_"+type+"_n"+n.getId()+".txt");
		//ResultsRun.reportResults(resultsFile, 0, n, admittedServices,0, 0, 0, true);
		
		
		//create the scheduling heuristic to be able to run the algorithms, log file just in case of errors
		sh = new SchedulingHeuristic(n,logFile );
		//create tabu object
		tb = new TabuScheduling(n, logFile);
		tb_LR = new TabuMappingScheduling_LR(n, logFile);
		
		for (int j=0; j<nbServices.length; j++)
		{
			for (int k=0; k<seed.length;k++)
			{
				//get the services 

				ServicesDriver sdriver = new ServicesDriver (nbServices[j],  300, 3,5, 300,500, 5 , 1, 500, 1500,tf);
				services = sdriver.convertGeneratedServices(sdriver.generateServicesForModel(seed[k]));
			//	services =shuffle(services);
				//Collections.shuffle(services);
				//keep track of what is executing
				//statusFile.writeInFile("Running for network "+n.getId()+" seed "+seed[k]+" nb of services "+services.size()+" Algorithm "+algorithm+"\n");
				//resultsFile.writeInFile("Seed "+seed[k]+"--set "+j+" nb of services "+nbServices[j]);
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
					
					admittedServices = sh.mapScheduleServiceRRBILP(services,true);
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}
				else if (algorithm.equals("Tabu"))
				{
					startTime = System.currentTimeMillis();
				
					admittedServices = tb.tabuSearch(services,  itrNb, canLength, tbCount);
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}
				else if (algorithm.equals("Tabu_LR") )
				{
					startTime = System.currentTimeMillis();
				
					admittedServices = tb_LR.tabuMappingScheduling(services, tabu_LR_iterations, 0);
							
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}
				else if (algorithm.equals("ILPMaxAdmission"))
				{
					ILPResults = ILPMaxAdmission.runModelForResults (n, services,ILPMaxAdmissionInputResultFile);
					admittedServicesNb = (int) ILPResults[0];
					execTime = ILPResults[1];
				}
				else if (algorithm.equals("ILPMinSumCT"))
				{
					ILPGameResults = ILPMinSumCT.runModelForResults (n, services,ILPMinSumCTInputResultFile);
					admittedServicesNb = (int) ILPGameResults[0];
					execTime = ILPGameResults[1];
				}
				else if (algorithm.equals("ILPMinMaxCT"))
				{
					ILPGameResults = ILPMinMaxCT.runModelForResults (n, services,ILPMinMaxCTInputResultFile);
					admittedServicesNb = (int) ILPGameResults[0];
					execTime = ILPGameResults[1];
				}
				else if (algorithm.equals("onlineBatchRRB"))
				{
					//create online batch scheduling
					onBatch = new OnlineBatchScheduling(n, services);
					startTime = System.currentTimeMillis();
					
					admittedServices = onBatch.onlineMapScheduleServiceRRBILP(batchSize,  false,true);
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}
				else if(algorithm.equals("onlineBatchRandom"))
				{
					//create online batch scheduling
					onBatch = new OnlineBatchScheduling(n, services);
					startTime = System.currentTimeMillis();
					
					admittedServices = onBatch.onlineMapScheduleServiceRRBILP(batchSize,  true,true);
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}
				//strategy selection based on shortest path, pay off = completion time
				else if(algorithm.equals("game_shortestPath"))
				{
					//create online batch scheduling
					g = new Game(n, services, StrategyType.SHORTEST_PATH,multiplier);
					startTime = System.currentTimeMillis();
					admittedServices = g.sequentialPlay(gameFileNameShortest,criteria,nashValue,enashValue);
					
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
					
				}
				//strategy selection based on mixed strategy
				else if(algorithm.equals("game_mixedStrategy"))
				{
					//create online batch scheduling
					g = new Game(n, services, StrategyType.MIXEDSTRATEGY,multiplier);
					startTime = System.currentTimeMillis();
					admittedServices = g.sequentialPlay(gameFileNameMixed,criteria,nashValue,enashValue);
					
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
					
				}
				//strategy selection based on mixed strategy
				else if(algorithm.equals("game_mixedStrategyILP"))
				{
					//create online batch scheduling
					g = new Game(n, services, StrategyType.MIXEDSTRATEGYILP,multiplier);
					startTime = System.currentTimeMillis();
					admittedServices = g.sequentialPlay(gameFileNameMixedILP,criteria,nashValue,enashValue);
					
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
					
				}
				//strategy selection based on longest path, pay off = completion time
				else if(algorithm.equals("game_longestPath"))
				{
					//create online batch scheduling
					g = new Game(n, services, StrategyType.LONGEST_PATH, multiplier);
					startTime = System.currentTimeMillis();
					admittedServices = g.sequentialPlay(gameFileNameLongest,criteria,nashValue,enashValue);
					
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}//strategy selection based on min|avg CT of all players - CT of player playing|= pay off 
				else if(algorithm.equals("game_payOffAvgCompletionTime"))
				{
					//create online batch scheduling
					g = new Game(n, services, StrategyType.PLAYERS_AVG_COMPLETIONTIME, multiplier);
					startTime = System.currentTimeMillis();
					admittedServices = g.sequentialPlay(gameFileNamePayOff,criteria,nashValue,enashValue);
					
					
					endTime = System.currentTimeMillis();
					execTime = endTime - startTime;
					admittedServicesNb = admittedServices==null?0:admittedServices.size();
				}
				ResultsRun.reportResults(resultsFile,k,n, services,batchSize, execTime,admittedServicesNb,false);
				
				//reset variables for the next run
				
				admittedServices = new ArrayList<Service>();
				ILPResults = new double [2];
				ILPGameResults = new double[3];
				 startTime = 0;
				 endTime = 0;
				 execTime =0;
				 admittedServicesNb=0;
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
	public static void reportResults(FileManipulation resultsFile, int setId, Network network, ArrayList<Service>services,int batchSize, double executionTime, double admittedServicesNb, boolean headerOnly)
	{	
		double admissionRate = (admittedServicesNb/services.size())*100;
		if (headerOnly)
		{
			 resultsFile.writeInFile("#NetworkId\t");
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
		//running for offline results
		String networksFile = "testResults/offlineInputs/offlineNetwork.ser";
		String [] servicesFile ={};
	
	/*	Network n = new Network(7,5,4,5,1000,1000,1,0);
	
		ArrayList<int[][]> physicalNetwork = n.buildPhysicalNetwork();
		int [] tf = physicalNetwork.get(2)[0];
	
	int[] seed = {96};//{12,33,55,67,96,44};	
	int[] nbServices = {20};//{2,5,10,15,20,30};		
	for (int i=0; i<seed.length; i++)
	{
		ServicesDriver sdriver = new ServicesDriver (nbServices[i],  300, 3,5, 300,500, 5 , 1, 500, 1500,tf);
	
		ArrayList<Service>services = sdriver.generateServices(50,10,100,seed[i]);//67*/
		Network n=null;
		ArrayList<Service>services = null;
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "game_shortestPath", "offline", n, services,"completionTime",0.0);
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "game_mixedStrategy", "offline", n, services,"completionTime",0.0);
	//	ResultsRun.resultsRunAlgo(networksFile, servicesFile, "game_mixedStrategyILP", "offline", n, services,"completionTime",0.0);
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "game_payOffAvgCompletionTime", "offline", n, services,"payOff",0.0);//"completionTime" "avgCompletionTime"
		/*ResultsRun.resultsRunAlgo(networksFile, servicesFile, "ILPMinMaxCT", "offline", n, services,"",0);
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "ILPMinSumCT", "offline", n, services,"",0);
		ResultsRun.resultsRunAlgo(networksFile, servicesFile, "ILPMaxAdmission", "offline", n, services,"",0);*/
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Tabu_LR", "offline", n, services,"",0);
	//}
	
	

	 
		//ResultsRun.resultsRunAlgo(networksFile, servicesFile, "RRB", "offline", n, services,"",0);
	//	ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Random", "offline", n, services,"",0);
		//change adjustment value in function
	//	ResultsRun.resultsRunAlgo(networksFile, servicesFile, "Tabu", "online");
	
	
		
		
	}

	public static ArrayList<Service> shuffle (ArrayList<Service>services)
	{
		ArrayList<Service> nservices =  new ArrayList<Service>();
		for (int i=services.size()-1; i>=0; i--)
		{
			nservices.add( services.get(i));
		}
		return nservices;
	}
}
