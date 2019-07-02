import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import HelperClasses.DijkstraAlgorithm;
import HelperClasses.FileManipulation;
import HelperClasses.ServicesDriver;
import Model.Configuration;
import NFV.Middlebox;
import NFV.Service;
import NFV.VLink;
import Network.Link;
import Network.Network;
import Network.PhysicalMachine;
import Network.VNF;


public class TabuMappingScheduling {
	
	Network network;
	String logFileName; // name of the log file where we will register some errors
	FileManipulation logFile;
	
	
	public TabuMappingScheduling (Network network, String logFileName) throws IOException
	{
		this.network = network;
		this.logFileName =logFileName;
		this.logFile = new FileManipulation(this.logFileName);
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
	public Service randomMiddleboxesMapping (Service s) throws Exception
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
	 * Shortest Path routing
	 * This function will map the virtual links of the service to the physical links in the network
	 * It make sure to map them to a path where all the links has the bandwidth required by the service
	 * @param s service
	 * @return service with the mapped Vlinks
	 * @throws Exception This will through and exception if the service can not be routed
	 */
	public Service mapVirtualLinks (Service s) throws Exception
	{
		DijkstraAlgorithm diskjtra = new DijkstraAlgorithm(this.network);
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
				sourcePM = this.network.getPmList()[l.getSource().getMappedToVNF().getPmId()];
				destinationPM = this.network.getPmList()[l.getDestination().getMappedToVNF().getPmId()];
				
				//map the virtual link to the shortest path where all the links has the bandwidth required by the service
				path = diskjtra.getShortestPathWithEnoughBandwidth(sourcePM, destinationPM, s.getBandwdith());
				
				if (path!=null)
				{					
					l.setRoutedThrough(path);
				}
				else
				{
					
					String st = "ERROR: service:"+ s.getId()+"-- Vlink "+l.getId()+" can not be routed on the physical network\n";
					this.logFile.writeInFile(st);
					return null;
					//throw new Exception(st);
				
				}				
			}
		}
		return s;
		
	}
	
	/**
	 * This method will take mapped and routed and scheduled services and update the vnfs and links array based on them (used for tabu)
	 * @param services
	 * @param vnfs
	 * @param links
	 */
	public void initializeVnfsLinks(ArrayList<Service>services, ArrayList<ArrayList<Integer>> vnfs, ArrayList<ArrayList<Integer>> links)
	{
		
    	Link pl;
    	Middlebox m;
    	VLink vl;
    	Service s;
    	int availableBw =0;
	   	for (int i=0; i<services.size(); i++)
    	{
    		s=  services.get(i);
    		for (int j=0; j<s.getMiddleboxes().size(); j++)
    		{
    			m = s.getMiddleboxes().get(j);
    			
    			for (int k =0; k<m.getIsProcessing().length; k++)
    			{
    				vnfs.get(m.getMappedToVNF().getId()).set(m.getIsProcessing()[k],s.getId());
    			}
    			    		
    		}
    		
    		
    		for (int j=0; j<s.getVirtualLinks().size(); j++)
    		{
    			vl = s.getVirtualLinks().get(j) ;
    			
    			for (int l=0; l<vl.getRoutedThrough().size(); l++)
    			{
    				pl = vl.getRoutedThrough().get(l);
	    			for (int k =0; k<vl.getIsTransmitting().length; k++)
	    			{
	    				availableBw = links.get(pl.getId()).get(vl.getIsTransmitting()[k]);
	    				links.get(pl.getId()).set(vl.getIsTransmitting()[k], availableBw-s.getBandwdith());
	    			}
    			}
    			
    		}
    	}
	}
	
	/**
	 * First come first served scheduling
	 * This function will schedule the traffic processing and transmission of a service in a consecutive way with no waiting time
	 * 
	 * For middlebox with processing = 0 it will set the started processing for them but leave the is processing empty. 
	 * In this case, the completion time = the started processing of the last function if its processing time =0
	 * @param s service to schedule
	 * @param timeslot , timeslot at which we want to start the service schedule
	 * @param vnfs array of integer for each VNF in the network that the id of the interger array is the timeslot and its content is the service using the vnf at this time slot
	 * @param links array list of integer represented time slots for each link and their value represent the available bandwidth at the specified time slot. 
	 * @param updateVnfsLinks if set to true the VNFs and links array will be updated, if false they won't be updated and this is needed for tabu search so we only update them at the last solution
	 * @return scheduled service
	 * @throws Exception 
	 */
	public Service scheduleServiceFCFS (Service s, int timeslot, ArrayList<ArrayList<Integer>> vnfs, ArrayList<ArrayList<Integer>> links, boolean updateVnfsLinks) throws Exception
	{		
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
			if (updateVnfsLinks)
			{
				vnfs.get(mappedVNFId).set(timeslot, s.getId());
			}
			//set the middlebox start processing time
			m.setStartedProcessing(timeslot);	
		
			//set is processing for the middlebox, here we are sure that vnf is available for the processing time by the findProcessingTimeslot()
			for (int j=0; j<m.getIsProcessing().length; j++)
			{
				//set the vnf is used by the service at this time slot 				
				if (updateVnfsLinks)
				{
					vnfs.get(mappedVNFId).set(timeslot, s.getId());				
				}
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
						
						if (updateVnfsLinks)
						{
							links.get(l.getRoutedThrough().get(k).getId()).set(utimeslot, bw);
						}
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
		
		
		
		return s;
	}

	
	/**
	 * This method will return the timeslot in which a middlebox can start processing on its mapped VNF
	 * taking into consideration that the vnf is available for all the processing period
	 * @param m middlebox to check when we can start its processing on its mapped vnf
	 * @param timeslot timeslot in which we wish start processing
	 * @param vnfs vnfs array of integer for each VNF in the network that the id of the integer array is the timeslot and its content is the service using the vnf at this time slot
	 * The vnfs arrays should be initialized to -1 for the biggest deadline of all the services
	 * @return the timeslot at which a vnf can start processing, -1 if the deadline fo service can not be met
	 * @throws Exception 
	 */
	public int findProcessingTimeslot (Middlebox m, int timeslot,  ArrayList<ArrayList<Integer>> vnfs) throws Exception
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
				//this is needed because we should have vnfs initialized to -1 and links to capacity for each time slot
				throw new Exception(" SHOULD INCREASE SIZE OF vnfs and links array in findProcessingTimeslot()");
				
			}
			 if (m.getProcessingTime() ==0) //this would be egress and ingress that can process many service at a time
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
	 * @param m middlebox to check when we can start its processing on its mapped vnf
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
	
	/**
	 * This method will return null if the service was not mapped or routed or scheduled and the service updated in the other case
	 * @param s service to schedule
	 * @param timeslot timeslot at which we want to start its schedule
	 * @param vnfs array of integer for each VNF in the network that the id of the interger array is the timeslot and its content is the service using the vnf at this time slot
	 * @param links array list of integer represented time slots for each link and their value represent the available bandwidth at the specified time slot. 
	 * @param updateVnfsLinks if set to true the VNFs and links array will be updated, if false they won't be updated and this is needed for tabu search so we only update them at the last solution
	 * @param map if false the service should have been already mapped
	 * @param route if false the service should have been already routed
	 */
	public Service  mapScheduleService(Service s, int timeslot, ArrayList<ArrayList<Integer>>vnfs,ArrayList<ArrayList<Integer>>links, boolean updateVnfsLinks, boolean map, boolean route  )
	{
		Service updatedService = s;
		try
		{
			if (map)
				updatedService = this.randomMiddleboxesMapping(s);			
			
			// if service can be mapped try routing it
			if (updatedService != null && route)
				updatedService = this.mapVirtualLinks(s);
			
			//if service can be routed try scheduling if the scheduling time slot 
			if (updatedService != null)
				updatedService = this.scheduleServiceFCFS(s,timeslot, vnfs, links,updateVnfsLinks);
		
			if (updatedService == null)
			{
				s.releaseResources();
				return null;
			}
			
			//System.out.println(s);
		}
		catch (Exception e)
		{
			e.printStackTrace();		
		}
		
		return s;
	}
	
	
	/**
	 * This method checks if the VNf is in the list
	 * @param f vnf to check if tabu
	 * @param tabuList array list of  <middlebox id, vnf id>
	 * @return true if tabu
	 */
	public boolean isTabu(VNF f, ArrayList<int []> tabuList)
	{
		for(int i=0; i<tabuList.size(); i++)
		{
			if (tabuList.get(i)[1] == f.getId())
			{
				return true;
			}
		}
		
		return false;
	}
	
	
	/**
	 * This method will return the solution /service mapping routing scheduling which the middlebox have the lowest time gap
	 * @param solutionSet
	 * @return best service solution
	 */
	public Service getSolutionWithLowestTimeGap (ArrayList<Service> solutionSet, int middleboxId)
	{
		//initialize to the first service
		Service serviceWithLowestGap = solutionSet.get(0);

		for (int i=1; i<solutionSet.size(); i++)
		{
			if (solutionSet.get(i).getMiddleboxes().get(middleboxId).getTimeGap()<serviceWithLowestGap.getMiddleboxes().get(middleboxId).getTimeGap())
			{
				serviceWithLowestGap = solutionSet.get(i);
			}
		}
		
		return serviceWithLowestGap;
	}
	
	
	/**
	 * This method will return the solution /service mapping routing scheduling which the middlebox mapping results in the lowest flow time
	 * (completion-arrival)
	 * @param solutionSet
	 * @param timeslot for the arrival time of service or timeslot in case of batch
	 * @return best service solution
	 */
	public Service getSolutionWithLowestFlowTime (ArrayList<Service> solutionSet, int timeslot)
	{
		//initialize to the first service

		Service lowestFlowTimeService = solutionSet.get(0);
		
		for (int i=1; i<solutionSet.size(); i++)
		{
			if (solutionSet.get(i).getFlowTime(timeslot)<lowestFlowTimeService.getFlowTime(timeslot))
			{
				lowestFlowTimeService = solutionSet.get(i);
			}
		}
		
		return lowestFlowTimeService;
	}
	
	

	/**
	 * Tabu works as follows:
	 * 1- Get middlebox with the biggest preceding time gap
	 * 2- Get all VNF on which the middlebox can be mapped 
	 * 		a- if it is only 1 vnf that would be the one on which it is already mapped, so get the next middlebox with the biggest preceding time gap
	 * 		b- If all middleboxes have only one vnf on which they can be mapped keep the same initial solution and do not run tabu
	 * 3-if m is mapped to f, for each f' same type as m map the chosen middlebox to f', re-run routing and scheduling
	 * 4- If f' not in tabu ,re-run routing and scheduling
	 * 5- Declare <m,f> as tabu
	 * 6- If mapping to f; resulted in a better solution flow time, update best solution
	 * 7-if all solutions in the neighberhood are tabu, then choose the solution with the least flow time (completion -arraival time (or timeslot in case of batch)
	 * @param s
	 * @param iterations
	 * @param timeslot timeslot at which we want the schedule to start
	 * @param vnfs
	 * @param links
	 * @return
	 */
	public Service tabuMappingScheduling (Service s, int iterations, int timeslot, ArrayList<ArrayList<Integer>>vnfs,ArrayList<ArrayList<Integer>>links)
	{
		boolean updateVnfsLinks = false;
		boolean map =false;
		boolean route =true;
		//no mapping, routing for the service initially
		Service initialServiceSolution = s.copy(false, false);
		
		//start by initial solution obtained by random mapping, shortest path routing and FCFS scheduling, no need to update VNfs and links so we dont affect other services
		initialServiceSolution= mapScheduleService(initialServiceSolution, timeslot, vnfs, links, updateVnfsLinks, true, true);
		
		//stop tabu if no initial solution
		if (initialServiceSolution==null)
		{
			return null;
		}
		
		Middlebox m=null;
		
		int count  =0, k=1;
		Service tempService, sCandidate, bestSolution;
		
		//list of solutions allowed (not tabu
		ArrayList<Service> solutionSet = new ArrayList<Service>();
		//list of possible solutions including tabu
		ArrayList<Service> candidateSet = new ArrayList<Service>();
		
		//tabu list contains (i,j) where is the middlebox that has been moved from j to j', and j is the VNF from which it has been moved
		ArrayList<int []> tabuList = new ArrayList<int[]>();
		int[] tabuTuple = new int[2];	
		
		Middlebox [] midd = initialServiceSolution.getMiddleboxesTimeGapOrdered();

		//get the middlebox that has the biggest flowtime = startProcessing of current function - completion of previous function
		m =initialServiceSolution.getMiddleboxesTimeGapOrdered()[0];
	
		//best solution obtained initialized to initial solution		
		bestSolution = initialServiceSolution;	
		
		//determine Neighborhood = function of the same type of m 
		ArrayList<VNF> neighborhood =  this.network.getVNFOfSpecifiedType(m.getType());
		
		//if there is only one vnf of the same type of m then that would be the one on which it is already mapped, choose the next middlebox with the biggest flow time
		while (neighborhood.size() ==1 )
		{
			m =midd[k];
			neighborhood =  this.network.getVNFOfSpecifiedType(m.getType());
			k++;
		
			//if k == s.getN() this means there is onlye on VNf on which each m can be mapped so we keep the same solution and we do not run tabu
			if(k == s.getN())
			{
				//perform the actual mapping and scheduling based on the best solution by updating the vnfs and links and keep same mapping same routing(same routing to prevent the case where many shortest path are possible)
				s = bestSolution.copy(true,true);
				s= mapScheduleService(s, timeslot, vnfs, links, true, false, false);
				return s;
			}
		}

		//stop when after certain iteration no improvment or no feasible solution in the neighberhood
		//number of iterations will determine how many solutions we will try as the tabu list will change
		//stop whenever we try all solutions in the neighberhood (case where iterations>all solutions in neighberhood)
		while (count != iterations && count<neighborhood.size())
		{
			for (int i=0; i<neighborhood.size(); i++)
			{
				//attempt to update the mapping by replacing the VNf, reroute and reschedule
				tempService = initialServiceSolution.copy(true,false);
				tempService.getMiddleboxes().get(m.getId()).setMappedToVNF(neighborhood.get(i));
				
				//keep the copied mapping, reroute
				tempService = this.mapScheduleService(tempService, timeslot, vnfs, links, updateVnfsLinks, map, route);				
				candidateSet.add(tempService);
			
				if (!this.isTabu (neighborhood.get(i), tabuList))
				{
					solutionSet.add(tempService);
				}
			}
			
			//Aspiration criteria: all neighbehood are tabu, then we allow the move with the lowest flow time
			if (solutionSet.size() ==0)
			{
				//sCandidate = this.getSolutionWithLowestTimeGap(candidateSet, m.getId());//to make sure of
				sCandidate = this.getSolutionWithLowestFlowTime(candidateSet,timeslot);
				
			}
			else
			{
				//sCandidate = this.getSolutionWithLowestTimeGap(solutionSet, m.getId());
				sCandidate = this.getSolutionWithLowestFlowTime(solutionSet,timeslot);
			}
		
			//if (sCandidate.getMiddleboxes().get(m.getId()).getTimeGap() <bestSolution.getMiddleboxes().get(m.getId()).getTimeGap())
			if (sCandidate.getFlowTime(timeslot)<bestSolution.getFlowTime(timeslot))
			{
				tabuTuple[0] = m.getId();
				tabuTuple[1] =  bestSolution.getMiddleboxes().get(m.getId()).getMappedToVNF().getId();
				tabuList.add(tabuTuple);
		
				bestSolution = sCandidate;
			}
			
			solutionSet = new ArrayList<Service>();
			candidateSet =  new ArrayList<Service>();
			count++;
		}
		
		
		//perform the actual mapping and scheduling based on the best solution by updating the vnfs and links and keep same mapping same routing(same routing to prevent the case where many shortest path are possible)
		s = bestSolution.copy(true,true);
		s= mapScheduleService(s, timeslot, vnfs, links, true, false, false);
		
		return s;
	}
	

	/**
	 * 
	 * @param services
	 * @param iterations
	 * @param timeslot if = -1 then consider service arrival time for flow time calculation
	 * @param vnfs
	 * @param links
	 * @param adjustmentValue needed if timeslot = -1
	 * @return max schedule length
	 */
	public int tabuMappingScheduling(ArrayList<Service> services, int iterations, int timeslot, ArrayList<ArrayList<Integer>>vnfs,ArrayList<ArrayList<Integer>>links, int adjustmentValue)
	{
		int scheduleLength=0;
		Service s ;
		int startTime =0;
		for (int i=0; i<services.size(); i++)
		{
			s = services.get(i);
			
			if (timeslot == -1)
			{
				startTime = (int)(s.getArrivalTime()*adjustmentValue);
			}
			else
			{
				startTime = timeslot;
			}
			
			s = this.tabuMappingScheduling(s, iterations, startTime, vnfs, links);
			
			if (s!=null && s.getCompletionTime()>scheduleLength)
			{
				scheduleLength = s.getCompletionTime();
			}
			
			//update the service in services to hold the scheduled service (this is needed since tabuMappingScheduling return a copy of the service new memory allocation)
			services.set(i,s);
		}
		
		
		return scheduleLength;
	}
	
	
	
	public static void main(String[]args) throws IOException
	{
	
		 
		int vnfTypesNb= 7;
		 Network  network = new Network(5,5,4,5,500,500,1,0);
		 ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();			 
		 System.out.println(network);
		 Service c;

		 
		 int highestDeadline =500;
		 ArrayList<ArrayList<Integer>>vnfs = network.getVnfsTime (highestDeadline);
		 ArrayList<ArrayList<Integer>>links = network.getLinksTimeBw(highestDeadline);
		 
		 //generate services
		int[]	tf = physicalNetwork.get(2)[0];		
		ServicesDriver driver = new ServicesDriver (3,  100, 3, 5, 300, 500, 5 , 1, 500, 1500,tf);
		//ArrayList<int[][]>  services = driver.generateServicesForModel();
		//ArrayList<Service> servicesObjects = driver.convertGeneratedServices(services);
		ArrayList<Service> servicesObjects = driver.generateServices(50, 100);
		
		//schedule the services
		TabuMappingScheduling sh = new TabuMappingScheduling(network,"logs/tabuHeuristic.txt");
		
		//testing for one service
		System.out.println("===Testing mapping algo for one service===");
		int sc = sh.tabuMappingScheduling(servicesObjects,4, 0, vnfs, links,100);//try 5
		System.out.println(sc);
		/*for (int i=0; i<servicesObjects.size(); i++)
		{
			//servicesObjects.get(i).getMiddleboxes().get(servicesObjects.get(i).getMiddleboxes().size()-1).setProcessingTime(2);
			c = sh.tabuMappingScheduling(servicesObjects.get(i), 2, 0, vnfs, links,10);
			//System.out.println (servicesObjects.get(i));
			//System.out.println (c);
		}*/
		
		
	/*	ArrayList<Network>networkListLinks = Driver.deserializeNetworks("testResults/onlineNetworksVaryLinksCapacity.ser");//hh
				
			ArrayList<Service> services =  Driver.deserializeServices("testResults/onlineServices_set0_seed12.ser", 0, 25);//hh
			Network n= networkListLinks.get(0);
			 
			 int highestDeadline =500;
			 ArrayList<ArrayList<Integer>>vnfs = n.getVnfsTime (highestDeadline);
			 ArrayList<ArrayList<Integer>>links = n.getLinksTimeBw(highestDeadline);
			TabuMappingScheduling sh = new TabuMappingScheduling(n,"logs/tabuHeuristic.txt");
			int sc = sh.tabuMappingScheduling(services,3, 0, vnfs, links,100);
			System.out.println(sc);
			System.out.println(vnfs);
			System.out.println(links);*/
	}
}