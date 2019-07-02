/**
 * This class is used to represent the links in the player graph connecting two player nodes 
 * Each player link is routed through several physical link but has a unique weight/cost representing the start transmission 
 * Each player represents a VLINk and has the same transmission delays as the VLink and connects nodes of the same VNF type
 * The routed through of the player link is different from the routed through of the VLink. The routed through of the Vlink holds the final routing solution
 */
package GameTheory;

import java.util.ArrayList;

import GeneralClasses.Edge;
import NFV.VLink;
import Network.Link;
import Network.Network;


public class PlayerLink extends Edge implements Move{
	
	//this will hold the list of links this PlayerLink is routed through
	private ArrayList<Link> routedThrough;
	private VLink vlink;
	
	private PlayerGraph pGraph;
	
	//the below 4 attributes are common for playerLink and playerNde to be able to order and update them
	private int arrivalTime;//arrival of traffic to the link, ready for transmission
	private int startExecutionTime;//start transmission timeslot
	private int executionTime;//transmission time of middlebox
	private int orderValue; //this can be equal to arrival or start execution time. Check Game.orderMoves () explanation
	
	public PlayerLink(int id, int weight, PlayerNode source, PlayerNode destination,  VLink vlink,  PlayerGraph pGraph) {
		super(id, weight, source, destination);
		this.routedThrough = new ArrayList<Link>();
		this.vlink = vlink;
		this.executionTime = this.vlink==null?0: this.vlink.getTransmissionDelays(); // from source and to destination have no vlink
		this.pGraph = pGraph;
	}

	public ArrayList<Link> getRoutedThrough(){
		return this.routedThrough;
	}
	
	public VLink getVLink()
	{
		return this.vlink;
	}
	
	public int getExecutionTime()
	{
		return this.executionTime;
	}
	
	public int getStartExecutionTime()
	{
		return this.startExecutionTime;
	}
	
	public int getArrivalTime()
	{
		return this.arrivalTime;
	}
	
	public int getOrderValue()
	{
		return this.orderValue;
	}
	
	public PlayerGraph getPGraph()
	{
		return this.pGraph;
	}
	public void setPlayerGraph (PlayerGraph pGraph)
	{
		this.pGraph = pGraph;
	}
	
	public void setRoutedThrough(ArrayList<Link> links){
		 this.routedThrough = links;
	}
	
	public void setVLink(VLink vlink)
	{
		this.vlink = vlink;
	}
	
	public void setExecutionTime(int executionTime)
	{
		this.executionTime = executionTime;
	}
	
	public void setStartExecutionTime(int startExecutionTime)
	{
		this.startExecutionTime = startExecutionTime;
	}
	
	public void setArrivalTime(int arrivalTime)
	{
		this.arrivalTime = arrivalTime;
	}
	
	public void setOrderValue(int orderValue)
	{
		this.orderValue = orderValue;
	}
	
	/**
	 * This method will return true if the links has the same id, are for the same pgraph and same player (equals of pgraph)
	 * @param e
	 * @return
	 */
	public boolean equals(PlayerLink e)
	{
		if ( this.id != e.getId() )
		{
			return false;
		}
		
		if (! this.pGraph.equals(e.getPGraph()))
		{
			return false;
		}
		return true;
	}
	
	public String toString(){
		String s="";
		s= String.format("Player Link Id: %4d;",  this.getId());//\n
		s+=String.format("\t Player Id: %4s ", this.getPGraph().getPlayer().getPid());
		s+= String.format("\t Weight:  %4d; ",  this.getWeight());
		s+=String.format("\t Arrival Time: %4s ", this.getArrivalTime());
		s+=String.format("\t Start Execution Time: %4s ", this.getStartExecutionTime());
		s+=String.format("\t Execution Time: %4s ", this.getExecutionTime());
		s+=String.format("\t Order Value: %4s \n", this.getOrderValue());//\n
		s+= this.vlink==null?String.format(" \t VLink Id : %4s;\n", "no-link (edge related to source or destination node)" ):String.format(" \t VLink Id : %4d;\n", this.vlink.getId());
		
		s+= String.format(" \t Source Node: id %4d; ",   this.source.getId());
		s+= ((PlayerNode)this.source).getMiddlebox()==null? String.format(" \t type: %4s;\n", "No related middlebox-source is the virtual source node"): String.format(" \t type: %4d; \t VNF id: %4d\n", ((PlayerNode)this.source).getMiddlebox().getType(), ((PlayerNode)this.source).getVnf().getId());
		
		s+= String.format(" \t Destination Node: id %4d; ",  this.destination.getId());
		s+= ((PlayerNode)this.destination).getMiddlebox()==null? String.format(" \t type: %4s;\n", "No related middlebox-destination is the virtual destination node"): String.format(" \t type: %4d; \t VNF id: %4d\n", ((PlayerNode)this.destination).getMiddlebox().getType(), ((PlayerNode)this.destination).getVnf().getId());
		
		
		
		s+= String.format("\t Routed Through the following physical links");
		for (int i=0; i<this.getRoutedThrough().size(); i++)
		{
			s+=String.format("%4d",this.getRoutedThrough().get(i).getId());
		}
	
		s+="\n";
		return s;
	}
	
	/**
	 * This function will return true of source and destination of the virtual link 
	 * are hosted on the same server
	 * 
	 * @return true if source and destination are mapped to VNfs hosted on the same server
	 */
	public boolean areHostedOnSameServer(){
		return (((PlayerNode)this.source).getVnf().getPmId() == ((PlayerNode)this.destination).getVnf().getPmId());
	}
	
	
	/**
	 * This function identifies the links in conflict with the current one starting at index j(j being the index of the current link)
	 * Links are in conflict if they request at least one of the physical links used by the current on at the same time (transmission time)
	 * Here we do not check if there is enough bandwidth for all the vlinks
	 * 
	 * @param strategyMoves ascending ordered array of nodes and links 
	 * @param j index of current link in strategyMoves (link that we want to check conflict against = this), -1 if the current link is not a strategy move (updating the pgraph for links not in strategy)
	 * @param skipLinksForCurrentPlayer set to true when updating links not in strategy for the graph. (current link not in strategy moves)
	 * @return array of conflict player links (null if no conflicts)
	 */
	public ArrayList<PlayerLink> identifyLinkConflict (ArrayList<Move>strategyMoves, int j, boolean skipLinksForCurrentPlayer)
	{
		ArrayList<PlayerLink> conflictLink = new ArrayList<PlayerLink> ();
		PlayerLink l = null;
		int finishTransmission=0;
		int lFinishTransmission=0;
		
		//add the node to which we are checking the conflict against
		conflictLink.add(this);// this = (PlayerLink)strategyMoves.get(j)
		
		for (int i=j+1; i<strategyMoves.size(); i++)
		{			
			//do not check player node
			if (strategyMoves.get(i) instanceof PlayerNode)
			{
				continue;
			}
			
			l= (PlayerLink)strategyMoves.get(i); 
			
			/**
			 * when updating the graph for nodes that are not in the strategy, we need to apply policy without considering the links in the strategy of the current player (player of current link)
			 */
			if (skipLinksForCurrentPlayer && l.getPGraph().getPlayer().equals(this.getPGraph().getPlayer()))
			{
				continue;
			}
			
			//continue if the execution time = 0 (this means source and destination are on the same server)
			if (l.getExecutionTime() == 0 )
			{
				continue;
			}
			
			//continue if link is not routed through at least one of the physical links of the current one
			if (!this.isUsingSimilarPhysicalLinks(l))
			{
				continue;
			}
			
			//finish transmission 
			lFinishTransmission = l.getStartExecutionTime()+l.getExecutionTime();
			finishTransmission = this.getStartExecutionTime()+this.getExecutionTime();
			
			/**
			 * There is no conflict if the link started and finished transmission before the current link starts or
			 * if it started after the current link finished transmission
			 */
			if ((l.getStartExecutionTime()<this.getStartExecutionTime() && lFinishTransmission <=this.getStartExecutionTime()) || l.getStartExecutionTime()>=finishTransmission)
			{
				continue;
			}
			
			
			conflictLink.add(l);
		}
		
		//if only one node is in the array, then it is the conflict to which were are checking the conflict against, this means there is no conflict , return null
		if (conflictLink.size() == 1)
		{
			conflictLink = null;
		}
		return conflictLink;
	}
	
	
	/**
	 * This method checks if the link passed as parameter is routed through at least one physical link of those used by this
	 * 
	 * @param l player link to check if it is using at least one of the same physical links
	 * @return true if at least one physical link is shared by both virtual links
	 */
	public boolean isUsingSimilarPhysicalLinks(PlayerLink l)
	{
		for (int j=0; j<l.routedThrough.size(); j++)
		{
			for (int i=0; i<this.routedThrough.size(); i++)
			{
				if (l.routedThrough.get(j).equals(this.routedThrough.get(i)))
				{
					return true;
				}
			}
		}
		
		return false;
		
	}
	
	/**
	 * This method will order the conflict nodes based on VNF policy that is:
	 * Ascending ordering based on :
	 * 1- Arrival time
	 * 2- If same arrival time order based on deadline
	 * 3- if same deadline order by link transmission time
	 * 4- if same transmission time order by index in conflict nodes
	 * 
	 * @param conflictNodes list of links requiring at least one of the physical link as the current link at the same time
	 */
	public void orderBasedOnLinkPolicy(ArrayList<PlayerLink> conflictLinks)
	{
		PlayerLink temp = null;
		int deadlinej =0;
		int deadlinej1 =0;
		for (int i = 0; i < conflictLinks.size()-1; i++) 
		{
		      for (int j = i+1; j < conflictLinks.size(); j++) 
		      {
		    	  deadlinej = conflictLinks.get(i).getPGraph().getPlayer().getService().getDeadlineTime();
		    	  deadlinej1 = conflictLinks.get(j).getPGraph().getPlayer().getService().getDeadlineTime();
		    	
		    	  //this means they are ordered no need for any further check. This is needed so we do not fall into any of the following statement in case the order is correct
			   	 if (conflictLinks.get(i).getArrivalTime()< conflictLinks.get(j).getArrivalTime()) 
			     {
		    		 continue;
			     }
			   	 
		    	//order by arrival time
		        if (conflictLinks.get(i).getArrivalTime() > conflictLinks.get(j).getArrivalTime()) 
		        {
		           temp = conflictLinks.get(i);
		           conflictLinks.set(i, conflictLinks.get(j));
		           conflictLinks.set(j, temp);
		        }
		      //check if in the ordered list some has the same arrival time, order them by deadline
		        else if (conflictLinks.get(i).getArrivalTime() == conflictLinks.get(j).getArrivalTime() && deadlinej>deadlinej1)
		        {
		        	temp = conflictLinks.get(i);
		        	conflictLinks.set(i, conflictLinks.get(j));
		        	conflictLinks.set(j, temp);
		        }
		      //check if some have the same deadline, order them by processing time
		        else if (deadlinej == deadlinej1 && conflictLinks.get(i).getExecutionTime() > conflictLinks.get(j).getExecutionTime())
		        {
		          	temp = conflictLinks.get(i);
		          	conflictLinks.set(i, conflictLinks.get(j));
		          	conflictLinks.set(j, temp);
		        }
				//check if some have the same processing time, order by index (i< j) in this case since we are checking j and j+1, do nothing
		        
		      }
		 }	

	}
	
	/**
	 * This method will return the timeslot in which a vlink can start transmitting on its mapped network link
	 * taking into consideration that the bandwidth needed is available for all the transmission period on all the links in which the vlink is routed
	 * 
	 * @param timeslot timeslot in which we wish start transmitting
	 * @param links array of integer for each link in the network that the id of the integer array is the timeslot and its content is the available bw at this timeslot
	 * The links arrays should be initialized to the capacity of the link
	 * 
	 *  @return the timeslot at which a vlink can start transmitting
	 * @throws Exception 
	 */
	public int findTransmissionTimeslot ( int timeslot,  ArrayList<ArrayList<Integer>> links) throws Exception
	{
		boolean timeslotFound = false;	
		int utimeslot = timeslot;
		int physicalLinkId;
		boolean startNextTimeslot = false;
		
		//search for the earliest timeslot we can start processing after or = timeslot passed as parameter
		while( !timeslotFound)
		{
			
			//check that for each link there is available bandwidth during all the transmission time
			for(int k=0; k<this.getRoutedThrough().size(); k++)
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
					
					
				physicalLinkId = this.getRoutedThrough().get(k).getId();
				for (int j=0; j<this.getExecutionTime(); j++)
				{	 
					if (links.get(physicalLinkId).get(utimeslot)>= this.getPGraph().getPlayer().getService().getBandwdith())
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
	 * This method will reserve bandwidth for the current link on the physical links it is routed through starting at the 
	 * startExecutionTime
	 * 
	 * @param links links array list of integer represented time slots for each link and their value represent the available bandwidth at the specified time slot. 
	 * @param startTransmissionTimeslot this is the timeslot at which we need to start doing the reservation of bandwidth (used when updating non strategy links) if set to -1 we use the link startExecution Time
	 */
	public void reserveBandwdith( ArrayList<ArrayList<Integer>> links, int startTransmissionTimeslot)
	{
		int utimeslot =0;
		int bw=0;		
		
		for(int k=0; k<this.getRoutedThrough().size(); k++)
		{
			utimeslot = startTransmissionTimeslot!=-1?startTransmissionTimeslot:this.startExecutionTime;
			//update the bandwidth of the links through which the vlink is routed
			for (int j=0; j<this.getExecutionTime(); j++)
			{				
				bw = links.get(this.getRoutedThrough().get(k).getId()).get(utimeslot)-this.pGraph.getPlayer().getService().getBandwdith(); 				
				links.get(this.getRoutedThrough().get(k).getId()).set(utimeslot, bw);
				utimeslot++;	
				bw=0;
			}					
		}
	
	
	}
	
	
	/**
	 * This method will apply the link policy on the conflicting links by 
	 * updating the start/order value of the conflicting links and all the related successor moves
	 * @param conflictLinks virtual link using at least one of the physical link of the current one at the same time
	 * @param links array of physical links with time slots. The array contains the reserved bandwidth for the links that have their schedule fixed
	 * 
	 * @return the first link to be scheduled based on policy
	 * @throws Exception 
	 */
	public PlayerLink applyLinkPolicy(ArrayList<PlayerLink> conflictLinks,  ArrayList<ArrayList<Integer>> links) throws Exception
	{
		int startTransmissionTimeslot =0;
		
		//this will hold the index of the link that will start transmission first. we will reserve bandwidth for this link only
		int indexOflinkWithEarliestStartTransmission = Integer.MAX_VALUE;
		
		//this will hold the start transmission timeslot for the player in indexOflinkWithEarliestStartTransmission
		int earliestStartTransmission = Integer.MAX_VALUE;
		PlayerLink l=null;
		
		//order links based on link policy
		this.orderBasedOnLinkPolicy(conflictLinks);
		
		//copy the links array and update the copy when checking for the startTransmissionTimeslot
		ArrayList<ArrayList<Integer>> linksTemp = Network.copy (links);
		
		for (int i=0; i<conflictLinks.size(); i++)
		{
			l=conflictLinks.get(i);
			
			/**
			 * the first link in the array can start transmission as soon as ready, this will be either arrival or start execution time which is the orderValue.
			 * It is based on how it is scheduled on the timeline. For instance, it  may have been in conflict before and we have updated it start execution based on policy
			 * and re-ordered it in the timeline. Hence when checking if it is in conflict again we need to consider that it is requesting the link at the new startExecution = ordervalue
			 *///System.out.println ("TO find trans slot"+linksTemp);
			startTransmissionTimeslot = l.findTransmissionTimeslot(l.getOrderValue(), linksTemp);
			//System.out.println ("Applying link policy on link "+ l+" -- "+startTransmissionTimeslot);
			l.setStartExecutionTime(startTransmissionTimeslot);
			l.setOrderValue(startTransmissionTimeslot);
			l.setWeight(l.getStartExecutionTime()-l.getArrivalTime()+l.getExecutionTime());//set weight to wait time (startexec-arrival) +exectime);
			
			//reserve bandwidth on the temp links array so the startTransmissionTimeslot takes into account the links reserved based on link policy 
			l.reserveBandwdith(linksTemp, -1);
		
			if (earliestStartTransmission>startTransmissionTimeslot)
			{
				earliestStartTransmission = startTransmissionTimeslot;
				indexOflinkWithEarliestStartTransmission = i;
			}
			
			//no update needed here to the bandwidth consumed on the successors affected links in relatedMoves, they will be updated when we identify conflict for them
			l.updateRelatedMoves();
		}
		
		//reserve bandwidth for the link with earliest start time. Bandwidth for the others will be reserved as we move through the timeline
		conflictLinks.get(indexOflinkWithEarliestStartTransmission).reserveBandwdith(links, -1);
		//System.out.println("hhh"+linksTemp);
		
		return conflictLinks.get(indexOflinkWithEarliestStartTransmission);
	}
	
	
	/**
	 * Once we update a link based on the link policy, we need to update the arrival time and order value and start exec time of all its successor moves in addition to the weight
	 * This is only done if the current link is in the player strategy
	 * @throws Exception 
	 */
	public void updateRelatedMoves() throws Exception
	{
		//the arrival time of the successor moves is the finish exec time of the current link
		int finishExecTime = this.getStartExecutionTime()+this.getExecutionTime();
		
		//start updating the successor nodes and links of the current link starting from its destination node
		PlayerNode temp = (PlayerNode)this.getDestination();
		
		while (temp.getType()!=PlayerNode.PNodeType.DESTINATION)
		{
			
			temp.setArrivalTime(finishExecTime);
			//@important if the new arrival time (finishExecTime<StartExecutionTime, this means that there was a conflict with this node and it start execution was updated. 
			//we should not change it (decrease it to the new arrival) because we will be creating a conflict  with a previous node who resulted in updating the current one
			if (finishExecTime > temp.getStartExecutionTime())
			{				
				temp.setStartExecutionTime(finishExecTime);
				temp.setOrderValue(finishExecTime);
			}
			temp.setWeight((temp.getStartExecutionTime()-temp.getArrivalTime()+temp.getExecutionTime()));//set weight to wait time (startexec-arrival) +exectime
			
			//update the finish exec time by adding the node exec time. this will be the arrival time to the link destination node
			finishExecTime=temp.getStartExecutionTime()+temp.getExecutionTime();
			
			//search for the link having the temp node as source node. 
			PlayerLink l = this.pGraph.getPlayer().getStrategy().searchLinks(temp);
			
			if (l==null)
			{
				throw new Exception("Link not found for node in conflict");
			}
			
			l.setArrivalTime(finishExecTime);
			
			//@important if the new arrival time is < StartExecutionTime, this means that there was a conflict with this link and it start execution was updated. 
			// we should not change it (decrease it to the new arrival) because we will be creating a conflict (transmission on link with not enough bw) with the previous link who resulted in updating the current one
			if (finishExecTime > l.getStartExecutionTime())
			{
				l.setStartExecutionTime(finishExecTime);
				l.setOrderValue(finishExecTime);
			}
			l.setWeight((l.getStartExecutionTime()-l.getArrivalTime()+l.getExecutionTime()));//set weight to wait time (startexec-arrival) +exectime
			
			//update the finish exec time by adding the link exec time. this will be the arrival time to the link destination node
			finishExecTime=l.getStartExecutionTime()+l.getExecutionTime();
			
			//update values for the link destination node
			temp = ((PlayerNode)l.getDestination());
			
			
		}
	
	}
	
	/**
	 * This method will get the start exec time for the current link not in the player strategy by applying the link policy without
	 * considering link of the same player as conflict links
	 * 
	 * @param conflictLinks links using the same physical links at the same time with the current one not including the links of the current player. 
	 * Conflict links should already have their values updated after applying the policy on the strategy moves
	 * @param physicalLinks array of physical links timeslot initialized to the links capacity
	 * @return -1 if the current node was not in the conflict node (should not happen) return the start exec time based on the vnf policy
	 * @throws Exception 
	 */
	public int getStartExecutionTimeBasedOnPolicy(ArrayList<PlayerLink> conflictLinks, 	ArrayList<ArrayList<Integer>>physicalLinks) throws Exception
	{
		PlayerLink l = null;
		int startTransmissionTimeslot =0;
		
		//order links in conflict based on policy
		this.orderBasedOnLinkPolicy(conflictLinks);

		//System.out.println("Ddd"+conflictLinks.size());
		for (int i=0; i<conflictLinks.size(); i++)
		{
			l=conflictLinks.get(i);
		//System.out.println("==="+l);
			/**
			 * the first link in the array can start transmission as soon as ready, this will be either arrival or start execution time which is the orderValue.
			 * It is based on how it is scheduled on the timeline. For instance, it  may have been in conflict before and we have updated it start execution based on policy
			 * and re-ordered it in the timeline. Hence when checking if it is in conflict again we need to consider that it is requesting the link at the new startExecution = ordervalue
			 */
			startTransmissionTimeslot = l.findTransmissionTimeslot(l.getOrderValue(), physicalLinks);
			
			//to get the accurate startTransmissionTimeslot for subsequent links (index>0) we should reserve the bandwidth of the links using the given startTransmissionTimeslot
			//we should not change the link startExecutionTime here as we may have some links in the strategy that should not be altered
			l.reserveBandwdith(physicalLinks, startTransmissionTimeslot);
			//System.out.println("startTransmissionTimeslot"+startTransmissionTimeslot);
			if (l.equals(this))
			{
				return startTransmissionTimeslot;
			}
		}
		
		return -1;
	}
	
}
