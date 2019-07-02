package SchedulingHeuristic;

import ilog.concert.IloException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import GeneralClasses.Edge;
import HelperClasses.DijkstraAlgorithm;
import HelperClasses.FileManipulation;

import HelperClasses.ServicesDriver;
import Model.SchedulingModel;
import NFV.*;
import Network.Link;
import Network.Network;
import Network.PhysicalMachine;
import Network.VNF;


public class SchedulingHeuristic {
	
	Network network;
	String logFileName; // name of the log file where we will register some errors
	FileManipulation logFile;
	
	public SchedulingHeuristic (Network network, String logFileName) throws IOException
	{
		this.network = network;
		this.logFileName =logFileName;
		this.logFile = new FileManipulation(this.logFileName);
	}
	
	/**
	 * 
	 * @param s service to schedule
	 * @param timeslot timeslot at which we want to start its schedule
	 * @param vnfs array of integer for each VNF in the network that the id of the interger array is the timeslot and its content is the service using the vnf at this time slot
	 * @param links array list of integer represented time slots for each link and their value represent the available bandwidth at the specified time slot. 
	 */
	public void mapScheduleService(Service s, int timeslot, ArrayList<ArrayList<Integer>>vnfs,ArrayList<ArrayList<Integer>>links )
	{
		Service updatedService = s;
		try
		{
			updatedService = this.mapMiddleboxes(s);
			
			// if service can be mapped try routing it
			if (updatedService != null)
				updatedService = this.mapVirtualLinks(s);
			
			//if service can be routed try scheduling if the scheduling time slot 
			if (updatedService != null)
				updatedService = this.scheduleService(s,timeslot, vnfs, links);
		
			if (updatedService == null)
			{
				s.releaseResources();
				s.setIsAdmitted(false);
			}
			else
			{
				s.setIsAdmitted(true);
			}
			System.out.println(s);
		}
		catch (Exception e)
		{
			e.printStackTrace();		
		}
		
		
	}
	
	
	/**
	 * This function will map and schedule a list of services passed as parameter
	 * It will return null if any of the services can not be mapped, routed or scheduled
	 * 
	 * @param services services to map, route and schedule
	 *
	 * @return list of configuration for each service -null if any of the services can not be mapped, routed or scheduled
	 */
	public ArrayList<Service> mapScheduleService(ArrayList<Service> services)
	{	
		 ArrayList<Service> admittedServices = new ArrayList<Service>();
		 int highestDeadline = ServicesDriver.getHighestDeadline( services);
		 ArrayList<ArrayList<Integer>>vnfs = this.network.getVnfsTime (highestDeadline);
		 ArrayList<ArrayList<Integer>>links = this.network.getLinksTimeBw(highestDeadline);
		 int timeslot =0;
		for(int i=0; i<services.size(); i++)
		{
			//start the schedule of the next service at its arrival time, at t0 if offline mode
			timeslot = (int) (services.get(i).getArrivalTime());
	
			//schedule the service starting from the completion time of the previous service
			 this.mapScheduleService(services.get(i), timeslot, vnfs, links);
			
			if (services.get(i).getIsAdmitted())
			{
				admittedServices.add(services.get(i));
			}
						
		}
		
			
		return admittedServices;
		
	}
	
	
	/**
	 * This function will map and schedule a service and return a configuration of the 
	 * scheduled service
	 * 
	 * @param s service to map and schedule
	 * @param delta total timeslots
	 * 
	 * @param timeslot the timeslot at which we want to service schedule to start
	 * @return configuration, null if the service cannot be mapped/schedules or routed
	 */
	public void mapScheduleServiceSequentially(Service s, int timeslot){
		
		Service updatedService = s;
		try
		{
			updatedService = this.mapMiddleboxes(s);
			
			// if service can be mapped try routing it
			if (updatedService != null)
				updatedService = this.mapVirtualLinks(s);
			
			//if service can be routed try scheduling if the scheduling time slot 
			if (updatedService != null)
				updatedService = this.scheduleServiceSequentially(s,timeslot);
				
			if (updatedService == null)
			{
				s.releaseResources();
				s.setIsAdmitted(false);
			}
			else
			{
				s.setIsAdmitted(true);
			}
			System.out.println(s);
		}
		catch (Exception e)
		{
			e.printStackTrace();		
		}
		
		
	}
	

	/**
	 * This function will map and schedule a list of services passed as parameter
	 * It will return null if any of the services can not be mapped, routed or scheduled
	 * 
	 * @param services services to map, route and schedule
	 * @param timeslot timeslot at which we need to start the schedule of the services
	 * @return list of configuration for each service -null if any of the services can not be mapped, routed or scheduled
	 */
	public ArrayList<Service> mapScheduleServiceSequentially(ArrayList<Service> services,  int timeslot)
	{	
		 ArrayList<Service> admittedServices = new ArrayList<Service>();
	
		for(int i=0; i<services.size(); i++)
		{
			//schedule the service starting from the completion time of the previous service
			 this.mapScheduleServiceSequentially(services.get(i), timeslot);
			 
			if (services.get(i).getIsAdmitted())
			{
				admittedServices.add(services.get(i));
				//start the schedule of the next service once the previous one was completed
				timeslot = services.get(i).getCompletionTime()+1;
			}					
		}
				System.out.println("Admitted"+admittedServices.size());
		return admittedServices;
		
	}
	



	
	/**
	 * This function will map and schedule a list of services passed as parameter
	 * It will return null if all the services can not be mapped, routed or scheduled or it will return the set of admitted services
	 * 
	 * The Mapping will be roud robin
	 * the routing based on the shortest path having available bw
	 * the scheduling is based on SchedulingModel
	 * 
	 * @param services services to map, route and schedule
	 * @param adjustementValue for the arrival time
	 * @param random mapping if true use random maping, else use round robin
	 * @return list of configuration for each service -null if any of the services can not be mapped, routed or scheduled
	 * @throws Exception 
	 */
	public ArrayList<Service> mapScheduleServiceRRBILP(ArrayList<Service> services,   boolean randomMapping) throws Exception
	{	
		 ArrayList<Service> admittedServices = null;
		 ArrayList<Service> servicesToSchedule = new ArrayList<Service>();
		Service updatedService , s;
		int [][] lastUsedVNFs = new int [this.network.getVNFTypesNb()][1];
		
		for(int i=0; i<services.size(); i++)
		{	
			s= services.get(i);
			updatedService = s;
			
			if(randomMapping)
			{
				//map VNFs randomly
				updatedService = this.mapMiddleboxes(s);
			}
			else
			{
				//map services using round robin
				updatedService = this.mapMiddleboxesRoundRobin(s, lastUsedVNFs);
			}
			
			// if service can be mapped try routing it
			if (updatedService != null)
			{
				updatedService = this.mapVirtualLinks(s);
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
			
		//schedule services that can be routed and mapped	
		admittedServices = this.scheduleServicesILP(servicesToSchedule);

		return admittedServices;
		
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
			vnfs = this.network.getVNFOfSpecifiedType(m.getType());
			
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
	 * This functions maps the service middleboxes to VNfs of the same type in the network chosen by a round robin manner
	 * we start assigning middlebox to available VNf of the same type. If all vnfs are assigned we start assigning 
	 * again starting from the first vnf of the same type in the array
	 * 
	 * It will print an error message if a middlebox could not be mapped and return null in this case
	 * 
	 * @param s service to map the middleboxes 
	 * @param int [][] lastUsedVNFs //array which index represents the vnfs types in the network and value is the index of 
	 * the next vnf to be used of this type in the getVNFOfSpecifiedType() array
	 * 
	 * @return service with mapped middleboxes; 
	 * 	null if some middleboxes could not be mapped because the network did not have VNF of the same type
	 * @throws Exception This will through and exception if some the middleboxes can not be mapped
	 */
	public Service mapMiddleboxesRoundRobin (Service s, int [][] lastUsedVNFs ) throws Exception
	{
		ArrayList<VNF> vnfs = new ArrayList<VNF>();
	
		Middlebox m;
				
		//start by mapping middleboxes
		for (int i=0; i<s.getMiddleboxes().size(); i++)
		{
			m = s.getMiddleboxes().get(i);
			
			//get vnfs of the same type of middlebox of the service
			vnfs = this.network.getVNFOfSpecifiedType(m.getType());
			
			if (vnfs.size()!=0)
			{
				//prevent an out of bound exception and check if all vnfs of that type are used then we need to start again using the first vnf
				if (lastUsedVNFs[m.getType()][0] >=vnfs.size())
				{
					lastUsedVNFs[m.getType()][0] = 0;
				}
				//choose a random VNF for the same type of middlebox to map the service middlebox
				m.setMappedToVNF(vnfs.get(lastUsedVNFs[m.getType()][0]));
				//update the next vnf to use
				lastUsedVNFs[m.getType()][0] +=1;
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
		DijkstraAlgorithm diskjtra = new DijkstraAlgorithm(this.network,false);
		ArrayList<Link> linkpath = new ArrayList<Link>();
		ArrayList<Edge> path = new ArrayList<Edge>();
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
				sourcePM = (PhysicalMachine) this.network.getNodeList()[l.getSource().getMappedToVNF().getPmId()];
				destinationPM = (PhysicalMachine) this.network.getNodeList()[l.getDestination().getMappedToVNF().getPmId()];
				
				//map the virtual link to the shortest path where all the links has the bandwidth required by the service
				path = diskjtra.getShortestPathWithEnoughBandwidth(sourcePM, destinationPM, s.getBandwdith());
				
				if (path!=null)
				{	
					for(int j=0; j<path.size(); j++)
					{
						linkpath.add((Link)path.get(j));
					}
					l.setRoutedThrough(linkpath);
				}
				else
				{
					
					String st = "ERROR: service:"+ s.getId()+"-- Vlink "+l.getId()+" can not be routed on the physical network\n";
					this.logFile.writeInFile(st);
					return null;
					//throw new Exception(st);
				
				}	
				 linkpath = new ArrayList<Link>();
			}
		}
		return s;
		
	}
	
	/**
	 * This function will schedule the traffic processing and transmission of a service in a consecutive way with no waiting time
	 * it will return null if the service can not be scheduled within the deadline
	 * For middlebox with processing = 0 it will set the started processing for them but leave the is processing empty. 
	 * In this case, the completion time = the started processing of the last function if its processing time =0
	 * @param s service to schedule
	 * @param timeslot , timeslot at which we want to start the service schedule
	 * @return scheduled service
	 */
	public Service scheduleServiceSequentially (Service s, int timeslot)
	{
		//service can not be scheduled starting at timslot if timeslot>deadline
		if ( timeslot>=s.getDeadlineTime())
		{
			return null;
		}
		
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
				
				//set is transmitting for the virtual link
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
		
		//make sure the schedule is within the deadline
		if (s.getCompletionTime()>s.getDeadlineTime())
		{
			return null;
		}
		
		return s;
	}
	
	
	/**
	 *
	 * This function will schedule the traffic processing and transmission of a service in a consecutive way with no waiting time
	 * it will return null if the service can not be scheduled within the deadline
	 * For middlebox with processing = 0 it will set the started processing for them but leave the is processing empty. 
	 * In this case, the completion time = the started processing of the last function if its processing time =0
	 * @param s service to schedule
	 * @param timeslot , timeslot at which we want to start the service schedule
	 * @param vnfs array of integer for each VNF in the network that the id of the interger array is the timeslot and its content is the service using the vnf at this time slot
	 * @param links array list of integer represented time slots for each link and their value represent the available bandwidth at the specified time slot. 
	 * @return scheduled service
	 */
	public Service scheduleService (Service s, int timeslot, ArrayList<ArrayList<Integer>> vnfs, ArrayList<ArrayList<Integer>> links)
	{
		//service can not be scheduled starting at timslot if timeslot>deadline
		if ( timeslot>=s.getDeadlineTime())
		{
			return null;
		}
		
		Middlebox m;
		VLink l;
		boolean updateTimeslot = false;
		int mappedVNFId ;
		int utimeslot;
		int bw;
		
		//setting up the schedule with no waiting time
		for (int i=0; i<s.getMiddleboxes().size(); i++)
		{
			//get the middlebox
			m = s.getMiddleboxes().get(i);
			mappedVNFId = m.getMappedToVNF().getId();
			
			//get the timeslot at which we can start processing
			timeslot = findProcessingTimeslot(m, timeslot, vnfs);
			
			//processing will violate the schedule
			if (timeslot == -1)
			{
				return null;
			}
			
			//set the vnf is used by the service at this time slot (set it here to handle middlebox with 0 processing)
			vnfs.get(mappedVNFId).set(timeslot, s.getId());
			//set the middlebox start processing time
			m.setStartedProcessing(timeslot);	
		
			//set is processing for the middlebox, here we are sure that vnf is available for the processing time by the findProcessingTimeslot()
			for (int j=0; j<m.getIsProcessing().length; j++)
			{
				//set the vnf is used by the service at this time slot 
				vnfs.get(mappedVNFId).set(timeslot, s.getId());				
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
				
				//get the timeslot at which we can start processing
				timeslot = this.findTransmissionTimeslot(l, timeslot, links);
				
				//transmission will violate the schedule
				if (timeslot == -1)
				{
					return null;
				}
				
				for(int k=0; k<l.getRoutedThrough().size(); k++)
				{
					utimeslot = timeslot;
					//update the bandwidth of the links through which the vlink is routed
					for (int j=0; j<l.getIsTransmitting().length; j++)
					{				
						bw = links.get(l.getRoutedThrough().get(k).getId()).get(utimeslot)-s.getBandwdith(); 
						links.get(l.getRoutedThrough().get(k).getId()).set(utimeslot, bw);
						utimeslot++;				
					}					
				}
				
				//set the vlink  start transmission time
				l.setStartedTransmition(timeslot);
				
				//set is transmitting for the virtual link
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
		
		//make sure the schedule is within the deadline
		if (s.getCompletionTime()>s.getDeadlineTime())
		{
			return null;
		}
		
		return s;
	}

	
	/**
	 * This method will schedule services based on SchedulingModelILP starting their schedule at their arrival time
	 * it will report the model results to testResults/schedILPResults.txt
	 * 
	 * @param services that are mapped and routed successfully
	 * 
	 * @return list of admitted services that were able to be scheduled
	 * 
	 * @throws IloException
	 */
	public ArrayList<Service> scheduleServicesILP (ArrayList<Service> services) throws IloException
	{
		String resultsFile = "testResults/schedILPResults.txt";
		ArrayList<Service> admittedServices = new ArrayList<Service>();
	
		
		//set the time line to the max deadline
		int delta = ServicesDriver.getHighestDeadline(services)+1;
		
		
		SchedulingModel schedModel = new SchedulingModel(this.network, services, delta);
		schedModel.buildILPModel();
		admittedServices = schedModel.runILPModel(resultsFile,false);

		schedModel.toString();
		
		return admittedServices;
		
	}
	
	
	/**
	 * This method will return the timeslot in which a middlebox can start processing on its mapped VNF
	 * taking into consideration that the vnf is available for all the processing period
	 * @param m middlebox to check when we can start its processing on its mapped vnf
	 * @param timeslot timeslot in which we wish start processing
	 * @param vnfs vnfs array of integer for each VNF in the network that the id of the integer array is the timeslot and its content is the service using the vnf at this time slot
	 * The vnfs arrays should be initialized to -1 for the biggest deadline of all the services
	 * @return the timeslot at which a vnf can start processing, -1 if the deadline fo service can not be met
	 */
	public int findProcessingTimeslot (Middlebox m, int timeslot,  ArrayList<ArrayList<Integer>> vnfs)
	{
		boolean timeslotFound = false;
		int nextTimeslotSearch =-1;
		int mappedVNFId = m.getMappedToVNF().getId();
		int utimeslot = timeslot;
		
		//search for the earliest timeslot we can start processing after or = timeslot passed as parameter
		while( !timeslotFound)
		{
			//if we searched till reaching the size of an array of time slot in a vnfs this means all the timeslots within the biggest deadline are reserved, service can not be admitted
			if (utimeslot >=vnfs.get(0).size())
			{
				timeslotFound = false;
				timeslot=-1;
				
			}
			/**
			 * timeslot> deadline - processing time means that the processing will violate the deadline we return
			 * -1 in this case
			 */
			if ( utimeslot>= m.getService().getDeadlineTime()-m.getProcessingTime()+1)
			{
				timeslotFound = true;
				timeslot = -1;
			}			
			else if (m.getProcessingTime() ==0) //this would be egress and ingress that can process many service at a time
			{
				timeslotFound = true;
			}
			else if (vnfs.get(mappedVNFId).get(utimeslot)== -1)
			{	
				//make sure that all the time slot during the processing delays are available
				for(int j=0; j<m.getProcessingTime(); j++)
				{	
					//if one of the timeslot that should be used for the processing is not available set the next time slot to timeslot+1
					if (vnfs.get(mappedVNFId).get(utimeslot)!=-1)
					{
						nextTimeslotSearch = utimeslot++;
						break;
					}
				
					utimeslot+= 1;
				}
		
				if (nextTimeslotSearch == -1)
				{
					timeslotFound = true;
				}
				else
				{
					timeslot = nextTimeslotSearch;
					utimeslot = timeslot;
					nextTimeslotSearch = -1;
					
				}
			}
			//if the given time slot when the method is called is used, try the next timeslot
			else
			{
				timeslot ++;
				utimeslot = timeslot;
			}
			
			
		}
		
		return timeslot;
	}
	
	
	/**
	 * This method will return the timeslot in which a vlink can start transmitting on its mapped network link
	 * taking into consideration that the bandwidth needed is available for all the transmission period on all the links in which the vlink is routed
	 * @param l link to check when we can start its processing on its mapped vnf
	 * @param timeslot timeslot in which we wish start transmitting
	 * @param links array of integer for each link in the network that the id of the integer array is the timeslot and its content is the available bw at this timeslot
	 * The links arrays should be initialized to the capacity of the link
	 * 
	 *  @return the timeslot at which a vlink can start transmitting, -1 if transmission can not be done within the deadline
	 */
	public int findTransmissionTimeslot (VLink l, int timeslot,  ArrayList<ArrayList<Integer>> links)
	{
		boolean timeslotFound = false;	
		int utimeslot = timeslot;
		int physicalLinkId;
		boolean startNextTimeslot = false;
		
		//search for the earliest timeslot we can start processing after or = timeslot passed as parameter
		while( !timeslotFound)
		{
			
			//check that for each link there is available bandwidth during all the transmission time
			for(int k=0; k<l.getRoutedThrough().size(); k++)
			{
				//if one physical link can not start at timeslot all the other should not
				if (!startNextTimeslot)
				{				
					//reinitialize utimeslot at the timeslot for each link
					utimeslot = timeslot;
				}
				else
				{	//reset startNextTimeslot so we know when timeslot is found
					startNextTimeslot = false;
					
				}
				
				//this means that the deadline can not be met we need to get out of the function
				if (utimeslot>= l.getService().getDeadlineTime()-l.getIsTransmitting().length+1)
				{
					timeslotFound = true;
					timeslot = -1;
					//break out of the while
					return timeslot ;
				
				}				
					
				physicalLinkId = l.getRoutedThrough().get(k).getId();
				for (int j=0; j<l.getIsTransmitting().length; j++)
				{	 
					if (links.get(physicalLinkId).get(utimeslot)>= l.getService().getBandwdith())
					{
						utimeslot++;
					}
					else
					{
						//if one timeslot has no available bandwidth check the next time slot for all links
						startNextTimeslot = true;
						 timeslot++;
						 utimeslot =timeslot;
						//start again for all links
						break;
					}
				}
				//if start time slot was true, this means one link has no bandwidth we need to recheck the next time slot for all the links so we need to get ou
				//of the next for loop but not the while
				if (startNextTimeslot)
				{
					break;
				}
			
			}
			
			//if start time slot was found get out of the loop
			if (!startNextTimeslot)
			{
				timeslotFound=true;
			}
		}
		
		//return utimeslot - l.getIsTransmitting().length; because at the end of the loop utimeslot will hold the processing time as well
		return timeslot;
	}
	

	
	public static void main(String[] args) throws Exception {
		
		double startTime = 0;
		double endTime = 0;
		double execTime =0;
		
		
		int vnfTypesNb= 4;
		 Network  graph = new Network(4,vnfTypesNb,4,5,200,200,1,0);
		 ArrayList<int[][]> physicalNetwork = graph.buildPhysicalNetwork();			 
		 
		
		 System.out.println(graph); 
		 //generate services
		int[]	tf = physicalNetwork.get(2)[0];		
		ServicesDriver driver = new ServicesDriver (3,  50, 3, 4, 50, 100, 4 , 1, 100, 200,tf);
		ArrayList<int[][]>  services = driver.generateServicesForModel(12);
		ArrayList<Service> servicesObjects = driver.convertGeneratedServices(services);
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		//schedule the services
		SchedulingHeuristic sh = new SchedulingHeuristic(graph,"ServicesHeuristic.txt");
		//sh.mapScheduleServiceSequentially(servicesObjects, 0);
		
		//servicesObjects.get(0).setBandwidth(150);
		//servicesObjects.get(0).setArrivalTime(0.002);
		sh.mapScheduleServiceRRBILP(servicesObjects, false);
		
		/*try {
			servicesObjects.get(1).setDeadlineTime(2);
			servicesObjects.get(0).setArrivalTime(0.02);
			servicesObjects.get(0).setDeadlineTime(12);
			
			startTime = System.currentTimeMillis();
			admittedServices = sh.mapScheduleServiceRRBILP(servicesObjects, 0,false);
			endTime = System.currentTimeMillis();
			execTime = endTime - startTime;
			
			System.out.println("Exec "+execTime);
			
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		*/
		
		//testing for one service
		//System.out.println("===Testing mapping algo for one service===");
		
	
		
    	/*Network  pNetwork = new Network(5,5,4,5,500,500,1,0);	
		 ArrayList<int[][]> physicalNetwork = pNetwork.buildPhysicalNetwork();
		int [] tf = physicalNetwork.get(2)[0];
		//ServicesDriver driver = new ServicesDriver (S, DELTA, minMiddlebox, maxMiddlebox, minBw, maxBw, maxProcessing, minProcessing, minTraffic, maxTraffic,tf);

		ServicesDriver driver = new ServicesDriver (15,  40, 3, 5, 300, 500, 5 , 1, 500, 1500,tf);

		ArrayList<int[][]> rservices = driver.generateServicesForModel();
		ArrayList<Service> services = driver.convertGeneratedServices(rservices);
		SchedulingHeuristic sh = new SchedulingHeuristic(pNetwork,"ServicesHeuristic.txt");
		sh.mapScheduleServiceSequentially(services, 0);*/
		
		/*for (int i=0; i<servicesObjects.size(); i++)
		{
			//servicesObjects.get(i).getMiddleboxes().get(servicesObjects.get(i).getMiddleboxes().size()-1).setProcessingTime(2);
			// sh.mapScheduleServiceSequentially(servicesObjects.get(i), 0);
			try {
				sh.mapMiddleboxesRoundRobin(servicesObjects.get(i),lastUsedVNFs);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println (servicesObjects.get(i));
			
			//servicesObjects.get(i).releaseResources();
			//System.out.println ("-Released "+servicesObjects.get(i));
		}*/
	

	}

}
