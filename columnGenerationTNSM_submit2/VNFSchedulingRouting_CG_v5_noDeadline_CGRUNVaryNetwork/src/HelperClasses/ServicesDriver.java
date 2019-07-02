/**
 * This class will generate random services 
 * 
 */
package HelperClasses;

import Model.ILPModel;
import NFV.*;
import ilog.concert.IloException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class ServicesDriver {
	
	//S number of services to generate
	int serviceNb;//S
	// T number of available types
	int VNFTypesNb;//T
	//DELTA number of time slots
	int totalTimeSlots;//DELTA
	//minMiddlebox min number of middleboxes required for a service SHOULD BE AT LEAST 3 (ingress, egress and 1 vnf)
	int minMiddleboxNb;
	//maxMiddlebox max number of middleboxes required for a service
	int maxMiddleboxNb;
	//minBw min bandwidth required for a service
	int minBw;
	//maxBw max bandwidth required for a service
	int maxBw;
	//maxProcessing max processing time possible for a middlebox of a service
	int maxProcessing;
	// minProcessing min processing time possible for a middlebox of a service
	int minProcessing;
	//minTraffic min traffic possible for a middlebox of a service
	int minTraffic;
	//maxTraffic max traffic possible for a middlebox of a service
	int maxTraffic;
	//VNFTypes an array on integer containing the types of VNF in the network to choose from. If this is set to null services with no available VNF types in the network can be chosen
	int [] VNFTypes;
	
	public ServicesDriver(int S,  int DELTA, int minMiddlebox, int maxMiddlebox, int minBw, int maxBw, int maxProcessing, int minProcessing, int minTraffic, int maxTraffic, int [] VNFTypes){
		this.serviceNb = S;
		this.VNFTypesNb = VNFTypes.length;
		this.totalTimeSlots = DELTA;
		this.minMiddleboxNb = minMiddlebox;
		this.maxMiddleboxNb = maxMiddlebox;
		this.minBw = minBw;
		this.maxBw = maxBw;
		this.maxProcessing = maxProcessing;
		this.minProcessing = minProcessing;
		this.minTraffic = minTraffic;
		this.maxTraffic = maxTraffic;
		this.VNFTypes = VNFTypes;
	}
	
	/**
	 * This function is used in the ILPMOdel.java
	 * This function generates a set of services as following:
	 * It will add an egress and ingress node automatically by considering that 0 = type of ingress and 1 is type of egress and processing time on both = 0 
	 * It will choose the type of service middleboxes as existing in the network. This is done because when we allow random seletion of type that may not exist many services will be rejected
	 * 
	 *
	 * @return arraylist of 
	 *  1- msn type of middleboxes of a service (if msn[1][2]= -1, then the middlebox 2 is not for the service. The service only has 2 middleboxes 0 an 1)
	 *  2- psn processing time of middlebox of a service (if msn[1][2]= -1, then the middlebox 2 is not for the service. The service only has 2 middleboxes 0 an 1)
	 *  	this is randomly generated between minProcessing and maxProcessing
	 *  3- bs bandwidth required by each service (randomly generated between minBw and maxBw)
	 *  4- us deadline of each service randomly generated between total processing time of all the middleboxes of the service and DELTA
	 *  5- ws traffic size randomly between maxTraffic and minTraffic
	 *  6- E edges of each service E[0] =1 means that edge 0 is for service 1. number of edges of each service = number of middleboxes -1
	 *  7- oe origin middlebox of each edge
	 *  8- de destination middlebox of each edge.
	 */
	public ArrayList<int[][]> generateServicesForModel ()
	{
		Random rand = new Random();
		rand.setSeed(12);//12//15//23
		ArrayList<int[][]> services = new ArrayList<int[][]>();
		int n =0;
		
		/**
		 * type of middlebox n of service s. if set to -1, then this middlebox is not for the service.
		 * maxMiddlebox is the maximum number of middleboxes a service can have
		 */
		int[][] msn = new int [this.serviceNb][this.maxMiddleboxNb];
		
		/**
		 * processing time (nb of timeslots) of middlebox n of service s. if set to -1, then this middlebox is not for the service.
		 * maxMiddlebox is the maximum number of middleboxes a service can have
		 */
		int [][] psn = new int [this.serviceNb][this.maxMiddleboxNb];
		
		//this will hold the total processing time of all the middleboxes of a service
		int [] totalps = new int[this.serviceNb];
		
		
		//bandwidth required by service s.This is formed as double array to be able to return it 
		int [][] bs = new int [1][this.serviceNb];
		
		//deadline time of service s (in time slots).This is formed as double array to be able to return it 
		int [][] us = new int  [1][this.serviceNb];
		
		// size of traffic of service s. This is formed as double array to be able to return it 
		int [][] ws = new int  [1][this.serviceNb];	
		
		//this holds the number of middleboxes for each service
		int [][] N = new int[1][this.serviceNb];
		
		/**
		 * this holds the total number of edges for all the services. 
		 * nb of edges for each service = nb of middleboxes - 1
		 */
		int totalEdges = 0;
		int countInit =0;
		int count =0;
		int c=0;
		//loop over services
		for (int i=0; i<this.serviceNb; i++)
		{
			//choose randomly the number of middlboxes for the service to be between minMiddlebox and maxMiddlebox
			n = rand.nextInt((this.maxMiddleboxNb - this.minMiddleboxNb) + 1) + this.minMiddleboxNb;
			
			//set the number of middleboxes of the service
			N[0][i] = n;
			
			//loop over midlleboxes
			for (int j =0; j<this.maxMiddleboxNb; j++)
			{
				if(j==0)
				{
					//add ingress node and set it processing time to 0
					msn [i][j] = 0;
					psn[i][j] = 0;
					continue;
				}
				
				if(j==n-1)
				{
					//add egress node and set it processing time to 0
					msn [i][j] = 1;
					psn[i][j] = 0;
					continue;
				}
				
				//generate other VNFs
				if (j<n-1)
				{
					//set the types of the middlebox of the service to be between 1 and T
					if (this.VNFTypes == null)
					{
						msn [i][j] = rand.nextInt(this.VNFTypesNb);
					}
					else
					{
						//choose one of the VNF types in the network rand.nextInt((max - min) + 1) + min;
						//  prevent selecting egress and ingress since those are added automatically
						c= rand.nextInt(((this.VNFTypesNb-1)-2)+1)+2;//prevent selecting 0 or 1 since we already set those types						
						msn [i][j] = this.VNFTypes[c];
					}
					psn [i][j] = rand.nextInt((this.maxProcessing - this.minProcessing) + 1) + this.minProcessing;
					totalps[i] += psn [i][j];
				}
				else
				{
					//specify that j is not a middlebox for s
					msn [i][j] = -1;
					psn [i][j] = -1;
				}
			}
			
			//choose the bandwidth randomly between maxBw and minBw
			bs[0][i] = rand.nextInt((this.maxBw - this.minBw) + 1) + this.minBw;
			
			//choose the traffic size randomly between maxTraffic and minTraffic
			ws[0][i] = rand.nextInt((this.maxTraffic - this.minTraffic) + 1) + this.minTraffic;
			
			//choose deadline time to be between total processing time of all the middleboxes of the service and DELTA
			us[0][i] = rand.nextInt((this.totalTimeSlots - totalps[i]) + 1) + totalps[i];
			
			//reset n
			n=0;
		}
		
		//calculate total number of edges for all services
		for (int i=0; i<this.serviceNb; i++)
		{
			//nb of edges for each service = nb of middleboxes - 1
			totalEdges = totalEdges+N[0][i]-1;
		}
		
		//list of virtual links for all services  (E[1] = 2 mean that edge 1 is for service 2)
		int [][] E = new int[1][totalEdges];
		
		//this holds the middlebox which are origin of edge e (oe [1] = 2 means that edge 1 has middlebox 2 as origin)
		int[][]oe = new int[1][totalEdges];
		
		//this holds the middlebox which are destination of edge e (de [1] = 2 means that edge 1 has middlebox 2 as destination)
		int [][]de = new int [1][totalEdges];
				
		for (int i=0; i<this.serviceNb; i++)
		{
			for (int j=countInit; j<countInit+N[0][i]-1; j++)
			{
				E[0][j] = i;
				
				//update the number of edges that are set for the services
				count++;
				
			}
			
			//counter on the number of middleboxes for each service
			n=0;
			for (int j=countInit; j<count; j++)
			{
				oe[0][j] = n;
				n++;
			}
			
			//counter on the number of middleboxes for each service; destination of the first edge is the first node
			n=1;
			for (int j=countInit; j<count; j++)
			{
				de[0][j] = n;
				n++;
			}
			
			countInit = count;			
		}
		
		
		services.add(msn);
		services.add(psn);
		services.add(bs);
		services.add(us);
		services.add(ws);
		services.add(E);
		services.add(oe);
		services.add(de);
		services.add(N);
		msn=null;
		psn=null;	
		bs=null;
		us=null;
		ws=null;
		E=null;
		oe=null;
		de=null;
		N=null;
		
		return services;
	}
	
	
	/**
	 * This function will generate random services based on the attribute values by using the generateServicesForModel()
	 * it will return an array of services
	 * 
	 * @param ArrayList<int[][]> randomServices list of random services information generated by the geenrateServicesForModel()
	 * 
	 * @return ArrayList<Service>
	 */
	public ArrayList<Service> convertGeneratedServices (ArrayList<int[][]> randomServices){
		
		ArrayList<Service> services = new ArrayList<Service>();
		Service s;
		Middlebox m;
		VLink vlink;
		ArrayList<Middlebox> middleboxes ;
		ArrayList<VLink> virtualLinks ;
		int vLinkId =0;
			
		/**
		 * type of middlebox n of service s. if set to -1, then this middlebox is not for the service.
		 * maxMiddlebox is the maximum number of middleboxes a service can have
		 */
		int[][] msn = randomServices.get(0);
		
		/**
		 * processing time (nb of timeslots) of middlebox n of service s. if set to -1, then this middlebox is not for the service.
		 * maxMiddlebox is the maximum number of middleboxes a service can have
		 */
		int [][] psn  = randomServices.get(1);
						
		//bandwidth required by service s.This is formed as double array to be able to return it 
		int [][] bs  = randomServices.get(2);
		
		//deadline time of service s (in time slots).This is formed as double array to be able to return it 
		int [][] us = randomServices.get(3);
		
		// size of traffic of service s. This is formed as double array to be able to return it 
		int [][] ws  = randomServices.get(4);
		
		//list of virtual links for all services  (E[1] = 2 mean that edge 1 is for service 2)
		int [][] E  = randomServices.get(5);
		
		//this holds the middlebox which are origin of edge e (oe [1] = 2 means that edge 1 has middlebox 2 as origin)
		int[][]oe  = randomServices.get(6);
		
		//this holds the middlebox which are destination of edge e (de [1] = 2 means that edge 1 has middlebox 2 as destination)
		int [][]de  = randomServices.get(7);
		
		//this holds the number of middleboxes for each service
		int [][] N = randomServices.get(8);
		
		
		for (int i=0; i<this.serviceNb; i++)
		{
			s = new Service(i, N[0][i], bs[0][i], us[0][i], ws[0][i]);
			
			middleboxes = new ArrayList<Middlebox>();
			
			//loop over middleboxes type for each service
			for (int j=0; j<msn[i].length; j++)
			{
				//if set to -1 then the middlebox is not for the service and all middleboxes for that service were added
				if (msn[i][j] == -1)
				{
					break;
				}
				
				m = new Middlebox(j,s, msn[i][j], psn[i][j]);
				
				//add the middlebox to the service
				s.getMiddleboxes().add(m);
			}
			
			
			virtualLinks = new ArrayList<VLink>();
			vLinkId =0;
			for (int j=0; j<E[0].length; j++)
			{
				//if the edge is for the service
				if (E[0][j] == i)
				{
					//create the edge by setting the source and destination middlebox based on their value in oe and de used as index
					vlink = new VLink(vLinkId,j, s, s.getMiddleboxes().get(oe[0][j]), s.getMiddleboxes().get(de[0][j]));
					vLinkId++;
					
					//add the vlink to the service
					s.getVirtualLinks().add(vlink);
				}
			}
			
			services.add(s);
		}
				
		return services;
		
	}
	
	
	/**
	 * This function will set the arrival and departure time for an array of services.
	 * It will set their type to arrival, copy them and set the type of the copy to departure
	 * 
	 * @important: Here the method will not create a copy of the service with type departure
	 * because resources will be release once the schedule time is completed
	 * 
	 * @param poissonDistribution poisson distribution with the arrival and departure time
	 * @param services services for which we would like to set the arrival and departure time
	 * 
	 * @return an array of services where each service is in 2 versions, one arrival and the another departure
	 */
	public ArrayList<Service> getPoissonServices (Poisson poissonDistribution, ArrayList<Service> services)
	{
		Service service;
		ArrayList<Service> arrivalDepartureServices = new ArrayList<Service> ();
		
		for (int i=0; i<services.size(); i++)
		{
			service =services.get(i);
			service.setArrivalTime(poissonDistribution.arrivals[i]);
			service.setDepartureTime(poissonDistribution.departures[i]);
			service.setType(Service.ProcessType.ARRIVAL);
			arrivalDepartureServices.add (service);			
		}
		
		/*for (int j=0; j<services.size(); j++)
		{
			service = services.get(j).clone();
			service.setType(Service.ProcessType.DEPARTURE);
			arrivalDepartureServices.add (service);	
		}*/
		
	
		return arrivalDepartureServices;
	}
	
	
	/**
	 * This function sort the services based on their arrival and departure times
	 * 
	 *  
	 *  if (r1.arrival<r2.arrival && r1.departure >r2.departure && r2.departure>r1.arrival) the returned array will be
	 *   <arrival r1, arrival r2, departure r2, departure r1>
	 *   
	 * @param services array of services to sort, the array should hold a copy of each service one for arrival and the other for departure
	 * @return array of sorted services
	 */
	public ArrayList<Service> sortServices (ArrayList<Service> services)
	{
		Service service;
			
		for (int i=1; i<services.size(); i++)
		{						
			for (int j=i; j>0; j--)
			{
				
				if (services.get(j).getType() == Service.ProcessType.ARRIVAL && services.get(j-1).getType() == Service.ProcessType.ARRIVAL 
						&& services.get(j).getArrivalTime() < services.get(j-1).getArrivalTime() )
				{ 
					service = services.get(j);
					services.remove(j);
					services.add(j,services.get(j-1));
					services.remove(j-1);
					services.add(j-1,service);		
				}			
				else if (services.get(j).getType() == Service.ProcessType.ARRIVAL && services.get(j-1).getType() == Service.ProcessType.DEPARTURE 
						&& services.get(j).getArrivalTime()  < services.get(j-1).getDepartureTime())
				{	
					service = services.get(j);
					services.remove(j);
					services.add(j,services.get(j-1));
					services.remove(j-1);
					services.add(j-1,service);			
					
				}				
				else if ( services.get(j).getType() == Service.ProcessType.DEPARTURE && services.get(j-1).getType() == Service.ProcessType.ARRIVAL 
							&& services.get(j).getDepartureTime() < services.get(j-1).getArrivalTime() )
				{
					service = services.get(j);
					services.remove(j);
					services.add(j,services.get(j-1));
					services.remove(j-1);
					services.add(j-1,service);	
				}				
				else if ( services.get(j).getType() == Service.ProcessType.DEPARTURE && services.get(j-1).getType()== Service.ProcessType.DEPARTURE 
						&& services.get(j).getDepartureTime() < services.get(j-1).getDepartureTime())
				{
					service = services.get(j);
					services.remove(j);
					services.add(j,services.get(j-1));
					services.remove(j-1);
					services.add(j-1,service);	
				}
				
			}				
			
		}
	

		return services;
		
	}
	
	
	/**
	 * This function generates services following a poisson distribution 
	 * @param lambda  arrival rate
	 * @param mu departure rate
	 * @return array of sorted services with arrival and departure time set. Each service has 2 copies in the array one for arrival and the other for departure 
	 * @throws IOException
	 */
	public ArrayList<Service> generateServices (double lambda, double mu) throws IOException
	{
		//generate the service
		ArrayList<int[][]>  services = this.generateServicesForModel();
		ArrayList<Service> servicesObjects = this.convertGeneratedServices(services);
		
		//generate poisson distribution
		Poisson poissonDistribution = new Poisson(lambda, mu, this.serviceNb);
		
		//create a copy from each service, one for arrival and another for departure and set arrival and departure time for each of them 
		servicesObjects = this.getPoissonServices(poissonDistribution,servicesObjects );
		
		//sort the services by arrival, departure time
		servicesObjects = this.sortServices(servicesObjects);
		
		return servicesObjects;
	}
	
	
	/**
	 * some random testing
	 * @param args
	 * @throws IloException
	 * @throws IOException 
	 */
	public static void main(String[]args) throws IloException, IOException{
		
		int ks = 5;//10;
		int L = 7;//14;
		int F =15;
		int T = 10;
		int S = 6;//15;
		int DELTA = 100;
		int minCapacity = 50;
		int maxCapacity = 1000;
		int minMiddlebox = 3;
		int maxMiddlebox = 6;
		int minBw = 10;
		int maxBw = 500;
		int maxProcessing =6;
		int minProcessing = 1;
		int minTraffic = 500;
		int maxTraffic = 1500;
		int[]tf =  {0,1,2,3,4,5,6,7,8,9};
		
		//just needed as parameters
		int[][] G =new int[2][2];
		int [][] cij = new int [3][3];
		int [][]xfk = new int [4][4];
		ServicesDriver driver = new ServicesDriver (S, DELTA, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic,tf);
		/*ArrayList<int[][]> services = driver.generateServicesForModel();
		
		ILPModel model = new ILPModel();

		model.printInput(G, cij, tf, xfk, services.get(0), services.get(1), services.get(2)[0], services.get(4)[0], services.get(3)[0], services.get(5)[0], services.get(6)[0], services.get(7)[0], services.get(8)[0]);
		ArrayList<Service> serviceList = driver.convertGeneratedServices(services);*/
		ArrayList<Service> serviceList = driver.generateServices(20, 10);
		System.out.println(serviceList.size());
		for (int i=0; i<serviceList.size(); i++)
		{
			System.out.println(serviceList.get(i));
		}
	}
}
