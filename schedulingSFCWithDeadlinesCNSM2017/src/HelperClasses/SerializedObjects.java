package HelperClasses;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import NFV.Service;
import Network.*;


public class SerializedObjects {
	
	
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
		 * @throws Exception 
		 */
		public void createAndDumpServices (int S, int minMiddlebox, int maxMiddlebox, int minBw, int maxBw, int minProcessing, int maxProcessing, int minTraffic, int maxTraffic, int [] VNFTypes, int delta, String filePath, int seed) throws Exception
		{
			ServicesDriver driver = new ServicesDriver (S, delta, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic,VNFTypes);
			ArrayList<Service> serviceList;
			try {
				serviceList = driver.convertGeneratedServices(driver.generateServicesForModel(seed));
				SerializedObjects.serializeServices(serviceList,filePath);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
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
		 * This function will return the set of services from the serialized file
		 * 
		 * @param filePath the path to the serialized file
		 * 
		 * @return array list of services belonging to the set
		 */
		public static ArrayList<Service> deserializeServices (String filePath)
		{
			ArrayList<Service> services = new ArrayList<Service>();
	
			Service s = null;
			
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
	
						
		 return services;
			
		}
	
		
		public static void genrateServices () throws Exception
		{
			int [] servicesNb ={2};//{5, 10, 15, 20, 25, 30};		
			int adjustmentValue = 100;
			
			//needed for online only
			int [] arrivalRates = {20,40,50,60,80,100};
			int departureRate = 10;
			
			// services input
			int delta = 300;//100;
			int minVm =3;
			int maxVM= 5;
			int minBw = 300;
			int maxBw = 500;
			int minProcessing = 1;
			int maxProcessing =5;
			int minTraffic = 500;
			int maxTraffic = 1500;
		
			int nbOfSets = 5;
			int [] seed ={43,64, 18,21,24};//{12,15,18,21,24};//{43,64};//
			ArrayList <Network>  networkList = new ArrayList<Network>() ;
			ArrayList<Service> services ;
		
			/*networkList = SerializedObjects.deserializeNetworks("testResults/offlineResults/OfflineResults_500GbNetwork/OfflineInputs/offlineNetwork.ser");//hh
			Network n= networkList.get(0);
			
			ServicesDriver driver = null;
			
			for (int j=0; j<servicesNb.length; j++)
			{
				//offline generation
				for (int i=0; i<nbOfSets; i++)
				{
					driver = new ServicesDriver (servicesNb[j],  delta, minVm, maxVM, minBw, maxBw,maxProcessing , minProcessing, minTraffic, maxTraffic,n.getPhysicalNetworkArray().get(2)[0]);
					
					ArrayList<int[][]> rservices = driver.generateServicesForModel(seed[i]);
					services = driver.convertGeneratedServices(rservices);
					SerializedObjects.serializeServices(services, "testResults/offlineResults/OfflineResults_500GbNetwork/offlineInputs/offlineServices_sNb"+servicesNb[j]+"_set"+i+"_seed"+seed[i]+".ser");
				}
				
			}*/
		
			int sNb =50;// 25;
			networkList = SerializedObjects.deserializeNetworks("testResults/onlineBatchInputs/OnlineBatchNetwork.ser");//hh
			Network  n= networkList.get(0);
			ServicesDriver driver = null;
			//online generation
			for (int i=0; i<nbOfSets; i++)
			{
				for(int j=0;j<arrivalRates.length;j++)
				{
					driver = new ServicesDriver (sNb,  delta, minVm, maxVM, minBw, maxBw,maxProcessing , minProcessing, minTraffic, maxTraffic,n.getPhysicalNetworkArray().get(2)[0]);
					services = driver.generateServices(arrivalRates[j], departureRate,adjustmentValue,seed[i]);
					SerializedObjects.serializeServices(services, "testResults/onlineBatchInputs/onlineBatchServices_Arr"+arrivalRates[j]+"_set"+i+"_seed"+seed[i]+".ser");
				}
			}
		
			
			
		
		
		}
		
		
		public static void genrateNetworks()
		{
			int []VNFType = {5,5};//let it be the the min of the VNFNb so when we increase the Nb we increase the VNFs of the same type
			int [] pmNb = {4,8};
			int []linkNb = {5, 10};
			int linkWeight = 1;
			int pmNoVNF = 0;	
			
			//testing over network of different VNF nb and links capacity
			int [] VNFNb = {7, 15};//{5,10,15,20};//15
			int [] linksCapacity = {1000,3000};	
			//int VNFVaryLink = 8;
			//int linkVaryVNF = 3000;//2000
			Network n;
			ArrayList <Network>  networkList = new ArrayList<Network>() ;
			String [] fileName = {"testResults/offlineInputs/offlineNetwork.ser", "testResults/onlineBatchInputs/OnlineBatchNetwork.ser"};
			
			//creating networks of different VNFNB 
			for (int i=1; i<VNFNb.length; i++)
			{
				n=new Network(VNFNb[i],VNFType[i],pmNb[i],linkNb[i],linksCapacity[i],linksCapacity[i],linkWeight,pmNoVNF);
				n.buildPhysicalNetwork();
				networkList.add(n);
				
				SerializedObjects.serializeNetworks(networkList, fileName[i]);
				networkList = new ArrayList<Network>() ;
			}			

			//SerializedObjects.serializeNetworks(networkList, "testResults/onlineBatchInputs/OnlineBatchNetwork.ser");
		
			//vary link capacity
			/*for (int i=0; i<linksCapacity.length; i++)
			{
				n=new Network(VNFVaryLink,VNFType,pmNb,linkNb,linksCapacity[i],linksCapacity[i],linkWeight,pmNoVNF);
				n.buildPhysicalNetwork();
				networkList.add(n);
			}
			
			//serialize network
			SerializedObjects.serializeNetworks(networkList, "testResults/onlineNetworkLinks.ser");*/
	
		}
		public static void main (String[]args) throws Exception
		{
			//SerializedObjects.genrateNetworks();
			//SerializedObjects.genrateServices();
			ArrayList<Network> networkList = SerializedObjects.deserializeNetworks("testResults/onlineBatchInputs/OnlineBatchNetwork.ser");
			System.out.println(networkList.get(0));
		}
}
