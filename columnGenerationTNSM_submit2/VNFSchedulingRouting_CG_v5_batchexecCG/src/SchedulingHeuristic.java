import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import HelperClasses.DijkstraAlgorithm;
import HelperClasses.FileManipulation;
import HelperClasses.Output;
import HelperClasses.ServicesDriver;
import Model.Configuration;
import NFV.*;
import Network.Link;
import Network.Network;
import Network.PhysicalMachine;
import Network.VNF;


public class SchedulingHeuristic {
	
	Network nertwork;
	String logFileName; // name of the log file where we will register some errors
	FileManipulation logFile;
	
	public SchedulingHeuristic (Network network, String logFileName) throws IOException
	{
		this.nertwork = network;
		this.logFileName =logFileName;
		this.logFile = new FileManipulation(this.logFileName);
	}
	
	
	/**
	 * This function will map and schedule a service and return a configuration of the 
	 * scheduled service
	 * 
	 * @param s service to map and schedule
	 * @param delta total timeslots
	 * 
	 * @param configIdPerService configuration id based on the nb of config for each service
	 * @param timeslot the timeslot at which we want to service schedule to start
	 * @return configuration, null if the service cannot be mapped/schedules or routed
	 */
	public Configuration mapScheduleService(Service s, int delta,int configIdPerService, int timeslot){
		Configuration c = null;
		
		try
		{
			s= this.mapMiddleboxes(s);
			s= this.mapVirtualLinks(s);
			s= this.scheduleService(s,timeslot);
		
			c = this.buildServiceConfiguration(s,delta, configIdPerService);
		}
		catch (Exception e)
		{
			e.printStackTrace();		
		}
		
		return c;
	}

	/**
	 * This function will map and schedule a list of services passed as parameter
	 * It will return null if any of the services can not be mapped, routed or scheduled
	 * 
	 * @param services services to map, route and schedule
	 * @param delta total timeline
	 * @param timeslot timeslot at which we need to start the schedule of the services
	 * @return list of configuration for each service -null if any of the services can not be mapped, routed or scheduled
	 */
	public ArrayList< Configuration> mapScheduleService(ArrayList<Service> services, int delta, int timeslot)
	{	
		ArrayList<Configuration> configurations = new ArrayList<Configuration>();
		Configuration conf;
	
		for(int i=0; i<services.size(); i++)
		{
			//schedule the service starting from the completion time of the previous service
			conf = this.mapScheduleService(services.get(i), delta,0, timeslot);
			
			if (conf == null)
			{
				return null;
			}
			
			//add the configuration to the list of configurations
			configurations.add (conf);
			
			//start the schedule of the next service once the previous one was completed
			timeslot = services.get(i).getCompletionTime()+1;
		}
		
			
		return configurations;
		
	}
	

	/**
	 * This function takes an array of configurations where each one is for a service 
	 * (1 configuration per service) and create o,r, and v, the parameters to be passed to the 
	 * master model for its initial run
	 * IMPORTANT: If a middlebox m has a processing time = 0; the related r[f][delta] =0 as in the model (ingress and egress node)
	 * Same for o for a non routed link
	 * 
	 * @param configurations array of configurations
	 * @param delta total time line
	 * @return array list of int [][][][]  that represents v, o, r in order
	 */
	public ArrayList< int[][][][]> convertConfigurations(ArrayList<Configuration> configurations, int delta, int configurationPerService)
	{	
		ArrayList< int[][][][]> columns = new ArrayList<int [][][][]>();
		Configuration conf;
		int serviceId;
		int c = configurationPerService;
		int F = this.nertwork.getVNFNb();
		int l = this.nertwork.getLinkList().length;
		int s= configurations.size()/configurationPerService; //initially we will generate 1 configuration per service
		int[] confPerService =  new int [s];//counter on which configuration for each service we are at
		
		int[][][][] r = new int [F][s][delta][c] ;//fsdeltac
		int[][][][]o = new int [l][s][delta][c];//lsdeltac
		int[][] v = new int [s][c];//sc
	
		for (int i=0; i<configurations.size(); i++)
		{
			conf = configurations.get(i);
			
			serviceId = conf.getService().getId();
			
			//setting v 
			v [serviceId][confPerService[serviceId]] = conf.getV();
			
			//setting o
			for (int j=0; j<l; j++)
			{
				for (int k=0; k<delta; k++)
				{
					o[j][serviceId][k][confPerService[serviceId]] = conf.getO()[j][k];
					
				}
			}
			
			//setting r
			for (int j=0; j<F; j++)
			{
				for (int k=0; k<delta; k++)
				{
					r[j][serviceId][k][confPerService[serviceId]] = conf.getR()[j][k];
					
				}
			}
			
			confPerService[serviceId]++;
		}
		
		int [][][][] allv = new  int [1][1][s][c];
		allv [0][0] = v;
		columns.add (allv);
		columns.add (o);
		columns.add(r);
		
		return columns;
	}
	

	
	/**
	 * This functions maps the service middleboxes to VNfs of the same type in the network chosen at random
	 * It will print an error message if a middlebox could not be mapped and return null in this case
	 * 
	 * @param s service to map the middleboxes 
	 * 
	 * @return service with mapped middleboxes; 
	 * 	null if some middleboxes could not be mapped because the network did not have VNF of the same type
	 * @throws Exception This will through and exception if some the middleboxes can not be mapped
	 */
	public Service mapMiddleboxes (Service s) throws Exception
	{
		ArrayList<VNF> vnfs = new ArrayList<VNF>();
		Middlebox m;
		Random rand = new Random();
				
		//start by mapping middleboxes
		for (int i=0; i<s.getMiddleboxes().size(); i++)
		{
			//this will make sure to have different type of random mapping for each service. and the same for each run
			rand.setSeed(i);
			m = s.getMiddleboxes().get(i);
			
			//get vnfs of the same type of middlebox of the service
			vnfs = this.nertwork.getVNFOfSpecifiedType(m.getType());
			
			if (vnfs.size()!=0)
			{
				//choose a random VNF for the same type of middlebox to map the service middlebox
				m.setMappedToVNF(vnfs.get(rand.nextInt(vnfs.size())));	
			}
			else
			{
				String st = "ERROR: service:"+ s.getId()+"--- No VNF of the same type:"+m.getType()+" of the middleboxe "+m.getId()+" requestsed by the service \n ";
				this.logFile.writeInFile(st);			
				throw new Exception(st);
			
			}
		}
		
		return s;
		
	}
	
	/**
	 * This function will map the virtual links of the service to the physical links in the network
	 * It make sure to map them to a path where all the links has the bandwidth required by the service
	 * @param s service
	 * @return service with the mapped Vlinks
	 * @throws Exception This will through and exception if the service can not be routed
	 */
	public Service mapVirtualLinks (Service s) throws Exception
	{
		DijkstraAlgorithm diskjtra = new DijkstraAlgorithm(this.nertwork);
		ArrayList<Link> path = new ArrayList<Link>();
		PhysicalMachine sourcePM, destinationPM;
		VLink l;
	
		//mapping the virtual links
		for (int i=0; i<s.getVirtualLinks().size(); i++)
		{
			l = s.getVirtualLinks().get(i);
			
			//if the middleboxes which are source and destination of the links are hosted on the same server no need to route the Vlink
			if (l.areHostedOnSameServer()){
				continue;
			}
			else
			{
				//get the physical servers to which the source and destination of the vlink are mapped
				sourcePM = this.nertwork.getPmList()[l.getSource().getMappedToVNF().getPmId()];
				destinationPM = this.nertwork.getPmList()[l.getDestination().getMappedToVNF().getPmId()];
				
				//map the virtual link to the shortest path where all the links has the bandwidth required by the service
				path = diskjtra.getShortestPathWithEnoughBandwidth(sourcePM, destinationPM, s.getBandwdith());
				
				if (path!=null)
				{					
					l.setRoutedThrough(path);
				}
				else
				{
					System.out.println(s);
					String st = "ERROR: service:"+ s.getId()+"-- Vlink "+l.getId()+" can not be routed on the physical network\n";
					this.logFile.writeInFile(st);
					throw new Exception(st);
				
				}				
			}
		}
		return s;
		
	}
	
	/**
	 * This function will schedule the traffic processing and transmission of a service in a consecutive way with no waiting time
	 * For middlebox with processing = 0 it will set the started processing for them but leave the is processing empty. 
	 * In this case, the completion time = the started processing of the last function if its processing time =0
	 * @param s service to schedule
	 * @param timeslot , timeslot at which we want to start the service schedule
	 * @return scheduled service
	 */
	public Service scheduleService (Service s, int timeslot)
	{
		Middlebox m;
		VLink l;
		boolean updateTimeslot = false;
			
		//setting up the schedule with no waiting time
		for (int i=0; i<s.getMiddleboxes().size(); i++)
		{
			//get the middlebox
			m = s.getMiddleboxes().get(i);
			//set the its start processing time
			m.setStartedProcessing(timeslot);
			
			//set is processing for the middlebox
			for (int j=0; j<m.getIsProcessing().length; j++)
			{
				m.getIsProcessing()[j] = timeslot; 
				timeslot++;
				
				//if the last middlebox has a processing time >0, we need to reduce timeslot by 1 to set the completion time
				if (i==s.getMiddleboxes().size()-1)
				{
					updateTimeslot = true;
				}
				
			}
			
			//the last middlebox is not the source of any virtual links, at this point (i = s.getVirtualLinks().size()-1) the scheduling of all the links is set
			if (i < s.getVirtualLinks().size())
			{
				//get the Vlink which has the middlebox as source
				l = s.getVirtualLinks().get(i);
				
				//this means that source and destination of the link are hosted on the same server and so no transmission is required 
				//this is handled in mapVirtualLinks()
				if (l.getRoutedThrough().size() == 0)
				{
					continue;
				}
				
				l.setStartedTransmition(timeslot);
				
				//set is processing for the middlebox
				for (int j=0; j<l.getIsTransmitting().length; j++)
				{				
					l.getIsTransmitting()[j] = timeslot; 
					timeslot++;				
				}
				
			}
		
		}		
		
		//reduce the timeslot by 1 because at the last iteration it will be increased by 1 but not used
		if (updateTimeslot)
		{
			s.setCompletionTime (--timeslot);
		}
		else
		{
			s.setCompletionTime (timeslot);
		}
		return s;
	}
	
	
	/**
	 * This function will generate configurations for the services used for diversification purposes for column generation
	 * @param services services to generate configuration for
	 * @param delta the timeline in which the services need to be scheduled
	 * @param startTimeslot timelot by which the service schedule should start
	 * @param configurationsPerService number of configuration to generate per service
	 * 
	 * @return configurations for the services
	 */
	public ArrayList<Configuration> generateDiversificationConfigurations (ArrayList<Service>services, int delta,int startTimeslot, int configurationsPerService)
	{
		ArrayList<Configuration> config  = new ArrayList<Configuration>();
		int[] servicesScheduleLowerBound = new int[services.size()];
		int[] servicesServiceTime = new int[services.size()];
		//for the first configuration schedule all services starting at the first startTimeslot
		int timeslot = startTimeslot;
		Configuration c;
		Random rand = new Random();
				
		//loop over the number of configurations for each service
		for (int j=0; j<configurationsPerService;j++)
		{
			//loop over the services
			for (int i=0;i<services.size(); i++ )
			{
				
				if (j==0)
				{
					c = this.mapScheduleService(services.get(i), delta, 0, timeslot);
					config.add(c);
					//set the lower bound of the schedule of each service. This will be the completion time of the service starting at startTimeslot
					servicesScheduleLowerBound[i] = c.getV();
					//set the minimum number of timeslots needed by the service to complete its execution (completion time if its starts at timeslot 0)
					servicesServiceTime[i] = c.getV()- startTimeslot;
				}
				else
				{
					//make sure there is enough time slots for the service to complete its processing if it starts at timeslot
					if ((delta-servicesScheduleLowerBound[i]*j)>=servicesServiceTime[i])
					{
						timeslot = servicesScheduleLowerBound[i]*j;
					}
					else if (delta - (servicesScheduleLowerBound[i]+j)>=servicesServiceTime[i])
					{
						timeslot = servicesScheduleLowerBound[i]+j;
					}
					else
					{
						//this will change the see based on the service but I will still get the same configuration for many runs
						rand.setSeed(i*j+1);//+1 to prevent having a seed = 0 and have an exception
						timeslot = rand.nextInt();
						while ((delta-timeslot <servicesServiceTime[i]) || timeslot<0)
						{
							timeslot = rand.nextInt();
						}
					}
									
					c = this.mapScheduleService(services.get(i), delta, 0, timeslot);
					config.add(c);
				}			
				
			}
		}
		
		return config;
	}
	
	/**
	 * This function will create the configuration needed to pass to the master given a 
	 * service which is mapped, routed and scheduled
	 * 
	 * @param s service
	 * @param delta total number of existing timeslots
	 * @param configIdPerService 
	 * @return
	 * @throws Exception This will through and exception if the service completion time > delta
	 */
	public Configuration buildServiceConfiguration(Service s, int delta, int configIdPerService) throws Exception
	{
		Middlebox m;
		int mappedVNFId;
		VLink vl;
		Link l;		
		int v=0;
		
		//completion time of the service 
		v = s.getCompletionTime();
		
		if (v>delta)
		{
			String st = "ERROR: service:"+ s.getId()+"--Completion time = "+v+"> delta ="+delta+"\n";
			this.logFile.writeInFile(st);
			throw new Exception(st);
	
		}
		
		int[][] r = new int [this.nertwork.getVNFNb()][delta];//fdelta // if VNF f is used at time slot delta
		int[][] o = new int [this.nertwork.getLinkList().length][delta];
		
		for (int i=0; i<s.getMiddleboxes().size(); i++)
		{
			m = s.getMiddleboxes().get(i);
			mappedVNFId = m.getMappedToVNF().getId();
			
			//loop over delta for the VNF to which the middlebox is mapped
			for (int j=0; j<r[mappedVNFId].length; j++)
			{
				//loop over the is processing to get the timeslots at which the service is processing for the specified vnf
				for (int k=0; k<m.getIsProcessing().length; k++)
				{
					if (j == m.getIsProcessing()[k])
					{
						r[mappedVNFId][j]=1;
					}
				}
			}
		}
		
		//loop through all the virtual links
		for (int i=0;i<s.getVirtualLinks().size(); i++)
		{
			vl=s.getVirtualLinks().get(i);
			
			//loop through the links to which each virtual link is mapped
			for (int j=0; j<vl.getRoutedThrough().size(); j++)
			{
				l = vl.getRoutedThrough().get(j);
				
				//loop through all timeslots in o for that physical link
				for(int k=0; k<o[l.getId()].length; k++)
				{
					//check if the virtual link is transmitting on that link at delta
					for (int x=0; x<vl.getIsTransmitting().length; x++)
					{
						if (k==vl.getIsTransmitting()[x])
						{
							//set that the the service is using the link on delta by consuming its required bandwidth
							o[l.getId()][k] =s.getBandwdith();
						}
					}
				}
			}
		}
		
		Configuration c = new Configuration( s, v, r, o);
		c.setIdPerService(configIdPerService);//this is set here mainly because it is used as the first column added to the master, if not it will be reset in Master.addColumnToMaster
		return c;
	}
	
	public static void main(String[] args) throws IOException {
		int vnfTypesNb= 4;
		 Network  graph = new Network(4,vnfTypesNb,4,5,200,200,1,0);
		 ArrayList<int[][]> physicalNetwork = graph.buildPhysicalNetwork();			 
		 System.out.println(graph);
		 Configuration c;
		 int delta =100;
		 
		 //generate services
		int[]	tf = physicalNetwork.get(2)[0];		
		ServicesDriver driver = new ServicesDriver (2,  100, 3, 4, 50, 100, 4 , 1, 100, 200,tf);
		ArrayList<int[][]>  services = driver.generateServicesForModel();
		ArrayList<Service> servicesObjects = driver.convertGeneratedServices(services);
		
		//schedule the services
		SchedulingHeuristic sh = new SchedulingHeuristic(graph,"logs/ServicesHeuristic.txt");
		
		//testing for one service
		System.out.println("===Testing mapping algo for one service===");
		for (int i=0; i<servicesObjects.size(); i++)
		{
			//servicesObjects.get(i).getMiddleboxes().get(servicesObjects.get(i).getMiddleboxes().size()-1).setProcessingTime(2);
			c = sh.mapScheduleService(servicesObjects.get(i), delta,0,0);
			System.out.println (servicesObjects.get(i));
			System.out.println (c);
		}
		
		//testing for multiple services
		System.out.println("===Testing mapping algo for multiple services===");
		ArrayList <Configuration> configurations = new ArrayList<Configuration>();
		ArrayList <int[][][][]> modelInput ;
		
		/*configurations = sh.mapScheduleService(servicesObjects, delta);
		modelInput = sh.convertConfigurations(configurations, delta,1);
		Output.printDoubleArray(modelInput.get(0)[0][0], "sc","v");
		Output.printQuadrupleArray(modelInput.get(1), "lsdeltac","o");
		Output.printQuadrupleArray(modelInput.get(2), "fsdeltac","r");*/
		
		configurations = sh.generateDiversificationConfigurations(servicesObjects, 20, 2, 3);
		for (int i=0; i<configurations.size(); i++)
		{
			System.out.println(configurations.get(i));
		}

	}

}
