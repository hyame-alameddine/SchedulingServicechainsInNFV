import ilog.concert.IloException;
import ilog.cplex.IloCplex.IncumbentCallback;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import jxl.Sheet;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.read.biff.BiffException;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;
import Model.Configuration;
import Model.ILPModel;
import Model.ILPModelModified;
import NFV.Service;
import Network.*;
import HelperClasses.*;

public class Driver {
		public Network network;
		public int Delta;
		public WritableWorkbook workbook;
		public FileManipulation statusFile;
		public FileManipulation resultsFile;
		public String excelFilePath;//excel results file
		
		public Driver(int delta){
			this.Delta = delta;
		}
		
		public Driver (Network network,  int Delta,String statusFilePath, String excelFilePath,WritableWorkbook w, String resultsFilePath) throws IOException
		{
			this.network = network;
			this.Delta = Delta;
			this.excelFilePath = excelFilePath;
			this.workbook = w;
			this.statusFile = new FileManipulation(statusFilePath);
			this.resultsFile = new FileManipulation(resultsFilePath);
		}
		
		
		/**
		 * This function will create and serialize a network 
		 * @param VNFNb
		 * @param VNFTypesNb
		 * @param pmNb
		 * @param linksNb
		 * @param minLinkCapacity
		 * @param maxLinkCapacity
		 * @param linkWeight
		 * @param filePath path to the file where to set the serialized network (file.ser)
		 * 
		 * @return network
		 */
		public Network  createAndDumpNetwork (int VNFNb, int VNFTypesNb, int pmNb,int linksNb, int minLinkCapacity, int maxLinkCapacity, int linkWeight, String filePath)	
		{
			Network pNetwork = new Network (VNFNb, VNFTypesNb, pmNb, linksNb, minLinkCapacity, maxLinkCapacity,linkWeight,0);
			pNetwork.buildPhysicalNetwork();
			try {
		         FileOutputStream fileOut = new FileOutputStream(filePath);
		         ObjectOutputStream out = new ObjectOutputStream(fileOut);
		         
		         out.writeObject(pNetwork);  		         
		         out.close();
		         fileOut.close();
		        
		      }
		      catch(IOException e) 
		      {
		         e.printStackTrace();
		      }
			return pNetwork;
		}
		
		/**
		 * This function serializes the Configuration and dump them in a file
		 * 
		 * @param filePath file with ser extension on where to dump the serialized Configuration
		 * @param services array list of Configuration to serialize
		 */
		public static void serializeConfigurations (ArrayList<Configuration> configs, String filePath)
		{
		    try {
		         FileOutputStream fileOut = new FileOutputStream(filePath);
		         ObjectOutputStream out = new ObjectOutputStream(fileOut);
		         
		         //start by writing the number of services
		         out.writeObject(configs.size());
		         
		         for (int i=0; i<configs.size(); i++)
		         {
		        	 out.writeObject(configs.get(i));
		         }
		         
		         out.close();
		         fileOut.close();
		        
		      }
		      catch(IOException e) 
		      {
		         e.printStackTrace();
		      }
		}
		
		/**
		 * This function will return an array list of Configuration in a serialized file given that the
		 * first element in the file is the number of Configuration
		 * 
		 * @param filePath path to the serialized file
		 * @return array list of Configuration
		 */
		public static ArrayList<Configuration> deserializeConfigurations (String filePath)
		{
			ArrayList<Configuration> configs = new ArrayList<Configuration>();
			Configuration n = null;
	
			Integer totalConfig = null;
			try
			{
				FileInputStream fileIn = new FileInputStream(filePath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				
				//read the total number of services in the file; needed to prevent an end of file exception
				totalConfig = (Integer)in.readObject();
				
				//read all the services in the file
				for (int i=0; i<totalConfig; i++)
				{
					n = (Configuration) in.readObject();
					configs.add(n);
				}
						
				in.close();
				fileIn.close();
	      }
		  catch(IOException e) 
		  {
			  e.printStackTrace();	        
	      }
		  catch(ClassNotFoundException c) 
		  {
			  System.out.println("Network class not found");
			  c.printStackTrace();	         
	      }
		
			return configs;
				
		}
		
		
		/**
		 * This function serializes the scheduling solution  and dump them in a file
		 * 
		 * @param filePath file with ser extension on where to dump the serialized Configuration
		 * @param services array list of Configuration to serialize
		 */
		public static void serializeSchedulingSolution(ArrayList<SchedulingSolution> configs, String filePath)
		{
		    try {
		         FileOutputStream fileOut = new FileOutputStream(filePath);
		         ObjectOutputStream out = new ObjectOutputStream(fileOut);
		         
		         //start by writing the number of services
		         out.writeObject(configs.size());
		         
		         for (int i=0; i<configs.size(); i++)
		         {
		        	 out.writeObject(configs.get(i));
		         }
		         
		         out.close();
		         fileOut.close();
		        
		      }
		      catch(IOException e) 
		      {
		         e.printStackTrace();
		      }
		}
		
		/**
		 * This function will return an array list of scheduling solution in a serialized file given that the
		 * first element in the file is the number of Configuration
		 * 
		 * @param filePath path to the serialized file
		 * @return array list of Configuration
		 */
		public static ArrayList<SchedulingSolution> deserializeSchedulingSolution (String filePath)
		{
			ArrayList<SchedulingSolution> configs = new ArrayList<SchedulingSolution>();
			SchedulingSolution n = null;
	
			Integer totalConfig = null;
			try
			{
				FileInputStream fileIn = new FileInputStream(filePath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				
				//read the total number of services in the file; needed to prevent an end of file exception
				totalConfig = (Integer)in.readObject();
				
				//read all the services in the file
				for (int i=0; i<totalConfig; i++)
				{
					n = (SchedulingSolution) in.readObject();
					configs.add(n);
				}
						
				in.close();
				fileIn.close();
	      }
		  catch(IOException e) 
		  {
			  e.printStackTrace();	        
	      }
		  catch(ClassNotFoundException c) 
		  {
			  System.out.println("Network class not found");
			  c.printStackTrace();	         
	      }
		
			return configs;				
		}
		
		/**
		 * This function serializes the networks and dump them in a file
		 * 
		 * @param filePath file with ser extension on where to dump the serialized networks
		 * @param services array list of networks to serialize
		 */
		public static void serializeNetworks (ArrayList<Network> networks, String filePath)
		{
		    try {
		         FileOutputStream fileOut = new FileOutputStream(filePath);
		         ObjectOutputStream out = new ObjectOutputStream(fileOut);
		         
		         //start by writing the number of services
		         out.writeObject(networks.size());
		         
		         for (int i=0; i<networks.size(); i++)
		         {
		        	 out.writeObject(networks.get(i));
		         }
		         
		         out.close();
		         fileOut.close();
		        
		      }
		      catch(IOException e) 
		      {
		         e.printStackTrace();
		      }
		}
		
		/**
		 * This function read a network from the provided file path and returns it
		 * 
		 * @param filePath file path containing the serialized network
		 * @return network
		 */
		public static Network  deserializeNetwork(String filePath)
		{
			Network n = null;
			try
			{
				FileInputStream fileIn = new FileInputStream(filePath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
							
				 n = (Network) in.readObject();
				 
				 in.close();
			}
			 catch(IOException e) 
			  {
				  e.printStackTrace();	        
		      }
			  catch(ClassNotFoundException c) 
			  {
				  System.out.println("Network class not found");
				  c.printStackTrace();
		         
		      }
			return n;
		}
		
		
		/**
		 * This function will return an array list of networks in a serialized file given that the
		 * first element in the file is the number of networks
		 * 
		 * @param filePath path to the serialized file
		 * @return array list of networks
		 */
		public static ArrayList<Network> deserializeNetworks (String filePath)
		{
			ArrayList<Network> networks = new ArrayList<Network>();
			Network n = null;
	
			Integer totalNetworks = null;
			try
			{
				FileInputStream fileIn = new FileInputStream(filePath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				
				//read the total number of services in the file; needed to prevent an end of file exception
				totalNetworks = (Integer)in.readObject();
				
				//read all the services in the file
				for (int i=0; i<totalNetworks; i++)
				{
					n = (Network) in.readObject();
					networks.add(n);
				}
						
				in.close();
				fileIn.close();
	      }
		  catch(IOException e) 
		  {
			  e.printStackTrace();	        
	      }
		  catch(ClassNotFoundException c) 
		  {
			  System.out.println("Network class not found");
			  c.printStackTrace();	         
	      }
		
			return networks;
				
		}
		
		/**
		 * This function will create services based on the parameters and serialize them and store them in a
		 * file based on the filepath parameter
		 * @param S nb of services to generate
		 * @param minMiddlebox min number of middleboxes to have for each service
		 * @param maxMiddlebox max number of middleboxes to have for each service
		 * @param minBw min bw to have for each service
		 * @param maxBw max bw to have for each service
		 * @param minProcessing min processing to have for each middlebox service
		 * @param maxProcessing  max processing to have for each middlebox service
		 * @param minTraffic min traffic to have for each middlebox service
		 * @param maxTraffic max traffic to have for each middlebox service
		 * @param VNFTypes vnf types to set for the services middleboxes
		 * @param filePath path to the serialized file where to dump the services (file.ser)
		 */
		public void createAndDumpServices (int S, int minMiddlebox, int maxMiddlebox, int minBw, int maxBw, int minProcessing, int maxProcessing, int minTraffic, int maxTraffic, int [] VNFTypes, String filePath)
		{
			ServicesDriver driver = new ServicesDriver (S, this.Delta, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic,VNFTypes);
			ArrayList<Service> serviceList = driver.convertGeneratedServices(driver.generateServicesForModel());
			this.serializeServices(serviceList,filePath);
			
		}
		
		
		/**
		 * This function serializes the services and dump them in a file
		 * 
		 * @param filePath file with ser extension on where to dump the serialized services
		 * @param services array list of services to serialize
		 */
		public static void serializeServices (ArrayList<Service> services, String filePath)
		{
		    try {
		         FileOutputStream fileOut = new FileOutputStream(filePath);
		         ObjectOutputStream out = new ObjectOutputStream(fileOut);
		         
		         //start by writing the number of services
		         out.writeObject(services.size());
		         
		         for (int i=0; i<services.size(); i++)
		         {
		        	 out.writeObject(services.get(i));
		         }
		         
		         out.close();
		         fileOut.close();
		        
		      }
		      catch(IOException e) 
		      {
		         e.printStackTrace();
		      }
		}
		
		
		/**
		 * This function will return the set of services from the serialized file
		 * that belong to a certain set of a specified size
		 * @param filePath the path to the serialized file
		 * @param setId  the id of the set we want to return (1st set should have the id =0)
		 * @param setSize the size of the set (i.e, the set contains 5 services for example)
		 * 
		 * @return array list of services belonging to the set
		 */
		public static ArrayList<Service> deserializeServices (String filePath, int setId, int setSize)
		{
			ArrayList<Service> services = new ArrayList<Service>();
			ArrayList<Service> servicesOfSet = new ArrayList<Service>();
			Service s = null;
			int startIndex =0;
			Integer totalServices = null;
			try
			{
				FileInputStream fileIn = new FileInputStream(filePath);
				ObjectInputStream in = new ObjectInputStream(fileIn);
				
				//read the total number of services in the file; needed to prevent an end of file exception
				totalServices = (Integer)in.readObject();
				
				//read all the services in the file
				for (int i=0; i<totalServices; i++)
				{
					s = (Service) in.readObject();
					services.add(s);
				}
				
				
				//setNb should start from 0 (1st set is 0), here we decide which services to return based on the setId
				startIndex = setId*setSize;
				
				//get the required services
				for (int i = startIndex; i<startIndex+setSize; i++)
				{
					servicesOfSet.add(services.get(i));
				}
				
				in.close();
				fileIn.close();
	      }
		  catch(IOException e) 
		  {
			  e.printStackTrace();	        
	      }
		  catch(ClassNotFoundException c) 
		  {
			  System.out.println("Service class not found");
			  c.printStackTrace();	         
	      }
	
						
		 return servicesOfSet;
			
		}
		
		/**
		 * This function will run column generation on a list of services passed as parameters
		 * @param setId Id of the set to start getting the services
		 * @param setSize nb of services per set
		 * @param totalSets total nb of sets to test
		 * @param servicesSerializedFile path to the file where to read the services
		 * @param pricingFilePath = "pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
		 * @param masterFilePath = "masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
		 * @param ilpFilePath ="masterResults/ILP.txt";
		 * 
		 * @throws IloException
		 * @throws IOException
		 * @throws WriteException 
		 * @throws BiffException 
		 * @throws RowsExceededException 
		 */
		public void runColumnGeneration (int setId, int setSize, int totalSets, String servicesSerializedFile, String masterFilePath,String ilpFilePath, String pricingFilePath, int sheetNb) throws IloException, IOException, RowsExceededException, BiffException, WriteException
		{
			/**
			 *  Array<int []> int [0]=execution time, int[1] objective value, first array for LP and the second for ILP
			 *  it will return null if column generation was not executed due to having a service which caused a mapping exception
			 */					 
			ArrayList<double[]> testResults = new ArrayList<double[]>();
			ArrayList<Service> services = new ArrayList<Service>();
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss zzz");
			for (int i=0; i<totalSets; i++)
			{
				//deserialize services of the set
				services = deserializeServices(servicesSerializedFile, setId, setSize);
				
				//create CG with the services
				ColumnGeneration CG = new ColumnGeneration(this.network, services, this.Delta);	
				
				this.statusFile.writeInFile("starting  CG test for set "+setId+" of size "+setSize+" at time"+dateFormat.format(new Date())+"\n");
				
				//run column generation
				//update the file of results to include the set ids
				pricingFilePath+=setId+"_";
				masterFilePath+=setId+"_";
				ilpFilePath+=setId+".txt";
				
				//3 is the acceptable gap , this is not used in the code because it was replaced by stopping the pricing at the first feasible solution
				testResults = CG.executeColumnGeneration(false, masterFilePath, ilpFilePath, pricingFilePath, false);
		
				//write test results to the excel sheet
				//this.CGResultsToExcel(testResults,sheetNb, setId);
				
				//add results to the results file
			//	this.resultsFile.writeInFile(setId+"\t");
				this.resultsFile.writeInFile( testResults.get(0)[1]+"\t");//set LP objective Value
				this.resultsFile.writeInFile( testResults.get(1)[1]+"\t"); //set ILP objective Value
				this.resultsFile.writeInFile( testResults.get(0)[0]+"\t");//set LP runtime
				this.resultsFile.writeInFile( testResults.get(1)[0]+"\t"); //set ILP runtime
				this.resultsFile.writeInFile( testResults.get(2)[0]+"\t");//iterations
				this.resultsFile.writeInFile( testResults.get(2)[1]+"\t"); //delta
				setId++;
			}
			
		}
		
		
		
		/**
		 * This function will run column generation on a list of services passed as parameters
		 * @param setId Id of the set to start getting the services
		 * @param setSize nb of services per set
		 * @param totalSets total nb of sets to test
		 * @param servicesSerializedFile path to the file where to read the services
		 * @param pricingFilePath = "pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
		 * @param masterFilePath = "masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
		 * @param ilpFilePath ="masterResults/ILP.txt";
		 * 
		 * @throws IloException
		 * @throws IOException
		 * @throws WriteException 
		 * @throws BiffException 
		 * @throws RowsExceededException 
		 */
		public void runColumnGenerationWithDiversification (int setId, int setSize, int totalSets, String servicesSerializedFile, String masterFilePath,String ilpFilePath, String pricingFilePath, int sheetNb, boolean useIncumbentCallback, boolean useHeuristicDiversification, int startTimeslot,int nbOfConfigPerService) throws IloException, IOException, RowsExceededException, BiffException, WriteException
		{
			/**
			 *  Array<int []> int [0]=execution time, int[1] objective value, first array for LP and the second for ILP
			 *  it will return null if column generation was not executed due to having a service which caused a mapping exception
			 */					 
			ArrayList<double[]> testResults = new ArrayList<double[]>();
			ArrayList<Service> services = new ArrayList<Service>();
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss zzz");
			for (int i=0; i<totalSets; i++)
			{
				//deserialize services of the set
				services = deserializeServices(servicesSerializedFile, setId, setSize);
				
				//create CG with the services
				ColumnGeneration CG = new ColumnGeneration(this.network, services, this.Delta);	
				
				this.statusFile.writeInFile("starting  CG with Diversification test for set "+setId+" of size "+setSize+" at time"+dateFormat.format(new Date())+" with ConfigNb per service= "+nbOfConfigPerService+"\n");
				
				//run column generation
				//update the file of results to include the set ids
				pricingFilePath+=setId+"_";
				masterFilePath+=setId+"_";
				ilpFilePath+=setId+".txt";
				
				//running column generation with incumbent call back for diversification
				testResults = CG.executeColumnGenerationDiversification(useIncumbentCallback,useHeuristicDiversification,startTimeslot,nbOfConfigPerService, masterFilePath, ilpFilePath, pricingFilePath);
		
				//write test results to the excel sheet
				//this.CGWithDiversificationResultsToExcel(testResults,sheetNb, setId);
				
				//add results to the results file
				this.resultsFile.writeInFile( testResults.get(0)[1]+"\t");//set LP objective Value
				this.resultsFile.writeInFile( testResults.get(1)[1]+"\t"); //set ILP objective Value
				this.resultsFile.writeInFile( testResults.get(0)[0]+"\t");//set LP runtime
				this.resultsFile.writeInFile( testResults.get(1)[0]+"\t"); //set ILP runtime
				this.resultsFile.writeInFile( testResults.get(2)[0]+"\t");//iterations
				this.resultsFile.writeInFile( testResults.get(2)[1]+"\t"); //delta
				this.resultsFile.writeInFile( nbOfConfigPerService+"\t"); //nbOfConfigPerService
				setId++;
			}
			
		}
		
		
		/**
		 * This function will run the ilp model on the services passed as parameter and will return an 
		 * array of  the execution time and the value of the objective function 
		 * Report the results (values of the variables) to a file called ILP/ILPModel_set_"+setId+".lp
		 * @param services
		 * @param setId the id of the set to which we are executing the model
		 * @throws IloException
		 * @throws IOException 
		 * @throws WriteException 
		 * @throws BiffException 
		 * @throws RowsExceededException 
		 */
		public void runILPModel(int setId, int setSize, int totalSets, String servicesSerializedFile, String ILPModelFilePath, int sheetNb) throws IloException, RowsExceededException, BiffException, WriteException, IOException
		{
			int delta = this.Delta;
			double startIlp =0;
			double endIlp =0;
			double [] results = new double [2];
			ArrayList<Service> services = new ArrayList<Service>();
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss zzz");
			for (int i=0; i<totalSets; i++)
			{
				//deserialize services of the set
				services = deserializeServices(servicesSerializedFile, setId, setSize);
				
				//run ILp Model
				ILPModelFilePath+=setId+".txt";
				
				//set the delta to the upper bound before running ILp
				delta = this.getDeltaUpperBound(services);

				this.statusFile.writeInFile("Starting ILP test for set "+setId+" of size "+setSize+" at time"+dateFormat.format(new Date())+"\n");
				
				ILPModelModified ilp =  new ILPModelModified(this.network, services, delta);
		    	ilp.buildILPModel();
		    	
		    	startIlp = System.currentTimeMillis();
		    	 ilp.runILPModel(ILPModelFilePath, setId);
		    	endIlp = System.currentTimeMillis();
				
		    	results[0] = endIlp - startIlp;
		    	results[1] = ilp.objectiveValue;
			
		    	
		    	//write test results to the excel sheet
				//this.ILpResultsToExcel(results, sheetNb, setId);
				
				//this.resultsFile.writeInFile( "\nILP Results for "+setId+"\n");
				this.resultsFile.writeInFile( results[1]+"\t");//set ILP objective Value
				this.resultsFile.writeInFile(results[0]+"\t"); //set ILP runtime
				this.resultsFile.writeInFile(this.Delta+"\t");
				setId++;
			}
	    	
	    	
		}
		
		/**
		 * This returns an upper bound on delta which would be the completion 
		 * time of the last service given by the heuristic.
		 * This should be used to set the delta of this class when running the ILp model
		 * Note that the column generation will set its delta based on the completion time of 
		 * the last service
		 * 
		 * @param array of services to which we want to run CG or ILP
		 * @return int an upper bound for Delta
		 * @throws IloException 
		 */
		public int getDeltaUpperBound(ArrayList<Service>services)
		{
			try {
				SchedulingHeuristic sh = new SchedulingHeuristic(this.network, "logs/ServicesHeuristic.txt");
				sh.mapScheduleService(services, this.Delta,0);
			} catch (IOException e) {
				System.out.println("Problem mapping scheduling services in Driver");
				e.printStackTrace();
			}
			return services.get(services.size()-1).getCompletionTime();
		}
	

		
		/**
		 * This function updates the results sheet in the specified workbook with the network and set ids
		 * write and close workbook should be called after calling the function
		 * @param workbook
		 * @param sheetName
		 * @param sheetNb
		 * @param totalSets
		 * @param setsize
		 * @throws RowsExceededException
		 * @throws WriteException
		 * @throws IOException
		 */	
		public void updateExcelSheetWithNetworkCellInfo ( int sheetNb, int totalSets, int setSize) throws RowsExceededException, WriteException, IOException
		{
				/*ExcelFileManipulation excel = new ExcelFileManipulation();	
				workbook = excel.getModifiableWorkbook(this.excelFilePath);*/
				WritableSheet excelSheet = workbook.getSheet(sheetNb);
	            
	           //add network info
	           excelSheet.addCell(new Label(0,0, this.network.toString()));
	           excelSheet.addCell(new Label(2,0, "Nb of services per set ="+setSize));
	           //set the sets ids
	            for (int i=0; i<totalSets; i++)
	            {
	            	excelSheet.addCell(new Number (0, i+3, i));
	            }
	            
	            //set the sets ids
	            for (int i=0; i<totalSets; i++)
	            {
	            	excelSheet.addCell(new Number (6, i+3, i));
	            }
	            
	            //write network in txt file
	            this.resultsFile.writeInFile( this.network.toString()+"\n\n");
	    	/*	workbook.write();
				workbook.close();*/
		}
		
		
		/**
		 * This function creates the results sheet in the specified workbook
		 * @param workbook
		 * @param sheetName
		 * @param sheetNb
		 * @throws RowsExceededException
		 * @throws WriteException
		 * @throws IOException
		 */		
		public static void createExcelResultsSheet ( WritableWorkbook workbook, String sheetName,int sheetNb) throws RowsExceededException, WriteException, IOException
		{			
            //create sheet
            workbook.createSheet(sheetName, sheetNb);
            WritableSheet excelSheet = workbook.getSheet(sheetNb);
            
            //add network info -- done in updateExcelSheeetWithNetworkCellInfo
           // excelSheet.addCell(new Label(0,0, this.network.toString()));            
          //  excelSheet.addCell(new Label(2,0, "Nb of services per set ="+setSize));
            
            //create results table containing the objective values --parameter column, row
            excelSheet.addCell(new Label(0,1, "Objective Value"));
            excelSheet.addCell(new Label (0, 2, "Set_Id"));
            excelSheet.addCell(new Label (1, 2, "ColumnGeneration_LP"));
            excelSheet.addCell(new Label (2, 2, "ColumnGeneration_ILP"));
            excelSheet.addCell(new Label (3, 2, "ColumnGeneration_Diversification_LP"));
            excelSheet.addCell(new Label (4, 2, "ColumnGeneration_Diversification_ILP"));
            excelSheet.addCell(new Label (5, 2, "ILP Model"));
           
            //set the sets ids -- done in updateExcelSheeetWithNetworkCellInfo
          /*  for (int i=0; i<totalSets; i++)
            {
            	excelSheet.addCell(new Number (0, i+3, i));
            }*/
            
            //create results table containing the execution time 
            excelSheet.addCell(new Label(8, 1, "Execution Time"));
            excelSheet.addCell(new Label (9, 2, "Set_Id"));
            excelSheet.addCell(new Label (10, 2, "ColumnGeneration_LP"));
            excelSheet.addCell(new Label (11, 2, "ColumnGeneration_ILP"));
            excelSheet.addCell(new Label (12, 2, "ColumnGeneration_Diversification_LP"));
            excelSheet.addCell(new Label (13, 2, "ColumnGeneration_Diversification_ILP"));
            excelSheet.addCell(new Label (14, 2, "ILP Model"));
            
            //set the sets ids -- done in updateExcelSheeetWithNetworkCellInfo
            /*for (int i=0; i<totalSets; i++)
            {
            	excelSheet.addCell(new Number (6, i+3, i));
            }*/

          /*  workbook.write();
           workbook.close();*/
		}
		
		
		/**
		 * 
		 * This function writes the results of the CG in the excel sheet
		 * 
		 * @param results Array<int []> int [0]=execution time, int[1] objective value, first array for LP and the second for ILP
		 * it will return null if column generation was not executed due to having a service which caused a mapping exception
		 * 
		 * @param sheetNb sheet nb to write into
		 * @param setId
		 * @throws BiffException
		 * @throws IOException
		 * @throws RowsExceededException
		 * @throws WriteException
		 */
		public void CGResultsToExcel (ArrayList<double[]> results,  int sheetNb, int setId) throws BiffException, IOException, RowsExceededException, WriteException
		{
			/*ExcelFileManipulation excel = new ExcelFileManipulation();	
			workbook = excel.getModifiableWorkbook(this.excelFilePath);*/
			
            // Get the first sheet
            WritableSheet sheet = workbook.getSheet(sheetNb);
            sheet.addCell(new Number (1, setId+3, results.get(0)[1]));//set LP objective Value
            sheet.addCell(new Number (2, setId+3, results.get(1)[1]));//set ILP objective Value
            
            sheet.addCell(new Number (10, setId+3, results.get(0)[0]));//set LP runtime
            sheet.addCell(new Number (11, setId+3, results.get(1)[0]));//set ILP runtime
            
           /*workbook.write();
           workbook.close();*/
		}
		
		
		/**
		 * 
		 * This function writes the results of the CG in the excel sheet
		 * 
		 * @param results Array<int []> int [0]=execution time, int[1] objective value, first array for LP and the second for ILP
		 * it will return null if column generation was not executed due to having a service which caused a mapping exception
		 * 
		 * @param sheetNb sheet nb to write into
		 * @param setId
		 * @throws BiffException
		 * @throws IOException
		 * @throws RowsExceededException
		 * @throws WriteException
		 */
		public void CGWithDiversificationResultsToExcel (ArrayList<double[]> results,  int sheetNb, int setId) throws BiffException, IOException, RowsExceededException, WriteException
		{
			/*ExcelFileManipulation excel = new ExcelFileManipulation();	
			workbook = excel.getModifiableWorkbook(this.excelFilePath);*/
			
            // Get the first sheet
            WritableSheet sheet = workbook.getSheet(sheetNb);
            sheet.addCell(new Number (3, setId+3, results.get(0)[1]));//set LP objective Value
            sheet.addCell(new Number (4, setId+3, results.get(1)[1]));//set ILP objective Value
            
            sheet.addCell(new Number (12, setId+3, results.get(0)[0]));//set LP runtime
            sheet.addCell(new Number (13, setId+3, results.get(1)[0]));//set ILP runtime
            
           /*workbook.write();
           workbook.close();*/
		}
		
		/**
		 * 
		 * @param results Array<int []> int [0]=execution time, int[1] objective value, first array for LP and the second for ILP
		 * it will return null if column generation was not executed due to having a service which caused a mapping exception
		 *
		 * @param sheetNb
		 * @param setId
		 * @throws BiffException
		 * @throws IOException
		 * @throws RowsExceededException
		 * @throws WriteException
		 */
		public void ILpResultsToExcel (double[] results, int sheetNb, int setId) throws BiffException, IOException, RowsExceededException, WriteException
		{
			/*ExcelFileManipulation excel = new ExcelFileManipulation();	
			workbook = excel.getModifiableWorkbook(this.excelFilePath);*/
			
			// Get the first sheet
            WritableSheet sheet = workbook.getSheet(sheetNb);
            sheet.addCell(new Number (5, setId+3, results[1]));//set ILP objective Value
            sheet.addCell(new Number (14, setId+3, results[0]));//set ILP runtime
            
            /*workbook.write();
            workbook.close();*/
		}
		
		public static void createInputs() throws IOException, WriteException
		{
			//creating and serializing the network
			int VNFNb = 10;//7;//14;
			int VNFTypesNb =10;// 7;//14;//should be equal to VNF number when used to map a service
			int pmNb = 5;//4;//7;
			int linksNb = 5;//10;
			int minLinkCapacity = 500;
			int maxLinkCapacity = 500;
			int linkWeight=1;
			int testId = 0; //change the network file and sheetName
			String networkFilePath = "testResults/inputs/network_"+testId+".ser";
			
			Driver d = new Driver(600);
			Network physicalNetwork = d.createAndDumpNetwork(VNFNb, VNFTypesNb, pmNb, linksNb, minLinkCapacity, maxLinkCapacity, linkWeight, networkFilePath);
			
			//creating and serializing the services
			int S=25;// total nb of services to generate
			int minMiddlebox = 3;
			int maxMiddlebox = 7;//5;
			int minBw = 200;
			int maxBw = 500;
			int minProcessing = 1;
			int maxProcessing = 5;
			int minTraffic = 500;
			int maxTraffic = 1500;
			int [] VNFTypes =  physicalNetwork.getPhysicalNetworkArray().get(2)[0];
			String servicesFilePath = "testResults/inputs/services.ser";
			
			d.createAndDumpServices(S, minMiddlebox, maxMiddlebox, minBw, maxBw, minProcessing, maxProcessing, minTraffic, maxTraffic, VNFTypes, servicesFilePath);
			
			//dump inputs parameters
			FileManipulation inputs = new FileManipulation("testResults/inputs/inputsParameters.txt");
			String content = "Network inputs \n\t VNFNb ="+VNFNb+"\n\t VNFTypesNb ="+VNFTypesNb+"\n\t pmNb ="+pmNb+
							"\n\t linksNb ="+linksNb+"\n\t minLinkCapacity ="+minLinkCapacity+"\n\t maxLinkCapacity ="+maxLinkCapacity+"\n\t linkWeight ="+linkWeight+"\n\n";
			content+="Services inputs \n\t nbOfServices ="+S+" \n\t minMiddlebox ="+minMiddlebox+"\n\t maxMiddlebox ="+maxMiddlebox+"\n\t minBw ="+minBw
					+"maxBw ="+maxBw+" \n\t minProcessing ="+minProcessing+"\n\t maxProcessing ="+maxProcessing+"\n\t minTraffic ="+minTraffic+" \n\t maxTraffic ="+maxTraffic;
			content+="VNFTypes = "+VNFTypes+"\n";
			inputs.writeInFile(content); 
			
			String sheetName = "Test_"+testId;
			int sheetNb = 0;
			String resultsFilePath = "testResults/results.xls";
			ExcelFileManipulation excelF = new ExcelFileManipulation();
			WritableWorkbook workbook = excelF.createWorkbook(resultsFilePath);	
			Driver.createExcelResultsSheet(workbook,sheetName, sheetNb);		
			
			//make sure to write and close
			workbook.write();
            workbook.close();
		}
		
		public static void resultsRunIlpComparison() throws IOException, RowsExceededException, BiffException, WriteException, IloException
		{
			//network input
			int VNFType = 5;//let it be the the min of the VNFNb so when we increase the Nb we increase the VNFs of the same type
			int pmNb = 8;
			int linkNb = 7;
			int linkWeight = 1;
			int pmNoVNF = 2;	
			int VNFNb = 5;
			int linksCapacity = 500;
			
					
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
			int [] servicesSize = {3,6,9,12};
			int totalSets = 1;
			int sheetNb =0;
			int setId =0;
			//files
			//files for CG without diversification
			String pricingFilePath = "testResults/pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
			String masterFilePath = "testResults/masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
			String ilpFilePath = "testResults/masterResults/ILP_";//"masterResults/ILP_"+setId+".txt";
			
			//file for diversification CG
			String divPricingFilePath = "testResults/pricingResults/dpRes_";//"pricingResults/dpRes_"+this.service.getId()+"_"+iterations+".txt";
			String divMasterFilePath = "testResults/masterResults/dmRes_";// "masterResults/dmRes_"+iterations+".txt";
			String divIlpFilePath = "testResults/masterResults/dILP_";//"masterResults/dILP_"+setId+".txt";
			
			//file for ILP model (no decomposition)
			String ILPModelFilePath = "testResults/ILP/ILPResults_";// = "ILP/ILPResults_"+setId+".txt"	
			
			String testStatusFile ="testResults/testStatus.txt";
			String excelFilePath = "testResults/results.xls";
			String resultstxt = "testResults/resultstxt.txt";
			
			//serialized objects
			String servicesSerializedFile = "testResults/inputs/services.ser";
			String networkSerializedFile = "testResults/inputs/network.ser"; 
			
			//other variables
			Network n;
			
			//create the network
			n=new Network(VNFNb,VNFType,pmNb,linkNb,linksCapacity,linksCapacity,linkWeight,pmNoVNF);
			n.buildPhysicalNetwork();
			
			//serialize the network
			ArrayList<Network> networkList = new ArrayList<Network>();
			networkList.add(n);
			Driver.serializeNetworks(networkList, networkSerializedFile);
			
			//create the services
			ServicesDriver driver = new ServicesDriver (servicesNb,  delta, minVm, maxVM, minBw, maxBw,maxProcessing , minProcessing, minTraffic, maxTraffic,n.getPhysicalNetworkArray().get(2)[0]);
			ArrayList<Service> services = driver.generateServices(arrivalRate, departureRate);
			
			//serialize the services
			Driver.serializeServices(services, servicesSerializedFile);
			
			//create the drive
			ExcelFileManipulation excel = new ExcelFileManipulation();			
			WritableWorkbook workbook = excel.getModifiableWorkbook(excelFilePath);
			Driver drive = new Driver (n, delta, testStatusFile, excelFilePath,workbook, resultstxt);
					
			for(int i=0; i<servicesSize.length; i++)
			{
				drive.resultsFile.writeInFile( servicesSize[i]+"\t");
				drive.runColumnGeneration(setId, servicesSize[i], totalSets, servicesSerializedFile, masterFilePath, ilpFilePath, pricingFilePath, sheetNb);
				drive.runColumnGenerationWithDiversification(setId, servicesSize[i], totalSets, servicesSerializedFile, divMasterFilePath, divIlpFilePath, divPricingFilePath, sheetNb, false, true, 0, diversificationConf);
				drive.runILPModel(setId, servicesSize[i], totalSets, servicesSerializedFile, ILPModelFilePath, sheetNb);
				drive.resultsFile.writeInFile("\n");
			}
			
		}
		public static void main (String[]args) throws IloException, IOException, RowsExceededException, WriteException, BiffException
		{
			Driver.resultsRunIlpComparison();
			//createInputs();
			
			//files for CG without diversification
			/*String pricingFilePath = "testResults/pricingResults/pRes_";//"pricingResults/pRes_"+this.service.getId()+"_"+iterations+".txt";
			String masterFilePath = "testResults/masterResults/mRes_";// "masterResults/mRes_"+iterations+".txt";
			String ilpFilePath = "testResults/masterResults/ILP_";//"masterResults/ILP_"+setId+".txt";
			
			//file for diversification CG
			String divPricingFilePath = "testResults/pricingResults/dpRes_";//"pricingResults/dpRes_"+this.service.getId()+"_"+iterations+".txt";
			String divMasterFilePath = "testResults/masterResults/dmRes_";// "masterResults/dmRes_"+iterations+".txt";
			String divIlpFilePath = "testResults/masterResults/dILP_";//"masterResults/dILP_"+setId+".txt";
			
			//file for ILP model (no decomposition)
			String ILPModelFilePath = "testResults/ILP/ILPResults_";// = "ILP/ILPResults_"+setId+".txt"	
			
			String testStatusFile ="testResults/testStatus.txt";
			String excelFilePath = "testResults/results.xls";
			String resultstxt = "testResults/resultstxt.txt";
			
			 //@TODO To be be modified when changing inputs
			String servicesSerializedFile = "testResults/inputs/services.ser";
			String networkSerializedFile = "testResults/inputs/network_0.ser"; //@TO CHANGE _0 to test id in create input
			int totalSets = 1;// total nb of sets to run
			int setId = 0;// id of the set from which we want to start running the models
			int setSize = 10;//number of services per set
			int sheetNb = 1; //sheet where to write the results		
			int delta = 600;
			
		
			ExcelFileManipulation excel = new ExcelFileManipulation();			
			WritableWorkbook workbook = excel.getModifiableWorkbook(excelFilePath);
			
			//if we are testing a new network and we need to add a new sheet 
			try
			{
				//check if sheet exists
				workbook.getSheet(sheetNb);
			}
			catch(IndexOutOfBoundsException e)
			{
				//create the sheet and add data if it does not exists
				String sheetName = "Test_"+sheetNb;				
			
				Driver.createExcelResultsSheet(workbook,sheetName, sheetNb);	
				
			}
						
			//get the network
			Network n = Driver.deserializeNetwork(networkSerializedFile);	
			
			//create the drive
			Driver drive = new Driver (n, delta, testStatusFile, excelFilePath,workbook, resultstxt);
			
			//update the results sheet with the network info
			drive.updateExcelSheetWithNetworkCellInfo( sheetNb, totalSets, setSize);			
		
			drive.runColumnGeneration(setId, setSize, totalSets, servicesSerializedFile, masterFilePath, ilpFilePath, pricingFilePath, sheetNb);
			//drive.runColumnGenerationWithDiversification(setId, setSize, totalSets, servicesSerializedFile, divMasterFilePath, divIlpFilePath, divPricingFilePath, sheetNb);
			drive.runILPModel(setId, setSize, totalSets, servicesSerializedFile, ILPModelFilePath, sheetNb);
	
			//write and close at the end
			workbook.write();
            workbook.close();*/
	
		}
}
