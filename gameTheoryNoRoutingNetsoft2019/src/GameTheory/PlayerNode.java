/**
 * This class is used to represent the nodes of the virtual graph for each player/service
 * It represents the node related to a VNF to which the middlebox can be mapped
 * Many nodes can be linked to the same VNF and the same middlebox but differ by the node they are related to.
 * Here the VNF is different from the VNF to which the middlebox isMappedTo. The VNF to which the middlebox is mapped to is the final chosen solution
 */
package GameTheory;

import java.util.ArrayList;

import javax.xml.crypto.dsig.keyinfo.PGPData;

import GeneralClasses.Node;
import HelperClasses.ServicesDriver;
import NFV.Middlebox;
import Network.Network;
import Network.VNF;

public class PlayerNode extends Node implements Move{

	private VNF vnf;
	private Middlebox middlebox;
	/*
	 * The PNodeType will allow to specify if the node is a virtual source/destination node (VNF, middlebox = null) or a vnf instance to which the 
	 * middlebox can be mapped
	 * 
	 */
	public enum PNodeType {SOURCE, DESTINATION, VNF};
	
	private PNodeType type;
	private PlayerGraph pGraph;
	
	//the below 4 attributes are common for playerLink and playerNde to be able to order and update them
	private int arrivalTime;;//arrival of traffic to the node, ready for processing
	private int startExecutionTime;//start processing timeslot
	private int executionTime;//processing time of middlebox
	private int orderValue; //this can be equal to arrival or start execution time. Check Game.orderMoves () explanation
	
	public PlayerNode (int id, int weight, VNF vnf, Middlebox middlebox,PNodeType type, PlayerGraph pGraph)
	{
		super (id, weight);
		this.vnf = vnf;
		this.middlebox = middlebox;
		this.executionTime = this.middlebox==null?0: this.middlebox.getProcessingTime();//source and destination nodes have no middlebox
		this.type = type;
		this.pGraph = pGraph;
	}
	
	
	public void setVnf(VNF f)
	{
		this.vnf = f;
	}
	
	public void setMiddlebox (PNodeType type)
	{
		this.type = type;
	}
	
	public void setType (Middlebox m)
	{
		this.middlebox = m;
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
	
	public void setPlayerGraph (PlayerGraph pGraph)
	{
		this.pGraph = pGraph;
	}
	
	
	public VNF getVnf()
	{
		return this.vnf;
	}
	
	public Middlebox getMiddlebox ()
	{
		return this.middlebox;
	}
	
	public PNodeType getType()
	{
		return this.type;
	}
		
	public PlayerGraph getPGraph()
	{
		return this.pGraph;
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
	public String toString()
	{
		String s ="";
		s+=String.format("Node id: %4d ", this.getId());	//	\n
		s+=String.format("\t Player Id: %4s ", this.getPGraph().getPlayer().getPid());
		s+=String.format("\t Weight: %4d ", this.getWeight());
		s+=String.format("\t Arrival Time: %4s ", this.getArrivalTime());
		s+=String.format("\t Start Execution Time: %4s ", this.getStartExecutionTime());
		s+=String.format("\t Execution Time: %4s ", this.getExecutionTime());
		s+=String.format("\t Order Value: %4s ", this.getOrderValue());
		s+=String.format("\t Type: %4s  \n", this.getType());//  \n
		s+=String.format("\t VNF: %4s", this.getVnf());
		s+=String.format("\t Middlebox: %4s ", this.getMiddlebox());
		
		return s;
	}
	
	
	public boolean equals(PlayerNode p)
	{
		if (this.id !=p.id)
		{
			return false;
		}
		
		if (! this.pGraph.equals(p.getPGraph()))
		{
			return false;
		}	
		
		
		return true;
	}
	
	/**
	 * This function identifies the nodes in conflict with the current one starting at index j(j being the index of the current node)
	 * conflict nodes are those requiring using the same vnf at the same time. 
	 * When working on the strategy, only nodes with same exec time should be identified as conflict to prevent the case where some timeslots are lost not used
	 * if we were considering all nodes of the same vnf. This issue happens with the example:
	 * 	Network network = new Network(6,5,5,5,700,700,1,0);
	 * ServicesDriver driver = new ServicesDriver (4,  100, 3,5, 300,500, 5 , 1, 500, 1500,tf); (Seed 33 for service generation)
	 * 
	 * @param strategyMoves ascending ordered array of nodes and links 
	 * @param j index of current node in strategyMoves (node that we want to check conflict against = this), -1 if the current node is not a strategy move (updating the pgraph for nodes not in strategy)
	 * @param skipNodesForCurrentPlayer set to true when updating nodes not in strategy for the graph. (current node not in strategy moves)
	 * @return array of conflict player nodes (null if no conflicts)
	 */
	public ArrayList<PlayerNode> identifyVNFConflictBasedOnExecTime (ArrayList<Move>strategyMoves, int j, boolean skipNodesForCurrentPlayer)
	{
		ArrayList<PlayerNode> conflictNodes = new ArrayList<PlayerNode> ();
		PlayerNode m = null;
		int finishProcessing=0;
		int mFinishProcessing=0;
		
		//add the node to which we are checking the conflict against
		conflictNodes.add(this);// this = (PlayerNode)strategyMoves.get(j)
		
		for (int i=j+1; i<strategyMoves.size(); i++)
		{			
			//do not check player Link
			if (strategyMoves.get(i) instanceof PlayerLink)
			{
				continue;
			}
			
			m= (PlayerNode)strategyMoves.get(i); 
			
			/**
			 * when updating the graph for nodes that are not in the strategy, we need to apply policy without considering the nodes in the strategy of the current player (player of current node)
			 */
			if (skipNodesForCurrentPlayer && m.getPGraph().getPlayer().equals(this.getPGraph().getPlayer()))
			{
				continue;
			}
			
			//continue if not same VNf or if the execution time = 0 (this means they are ingress or egress or source and destination node, we do not consider there is conflict)
			if (m.getExecutionTime() == 0 || !m.getVnf().equals(this.getVnf())  )
			{
				continue;
			}
			
			//finish processing value is the timeslot at which the vnf can start processing another player
			mFinishProcessing = m.getStartExecutionTime()+m.getExecutionTime();
			finishProcessing = this.getStartExecutionTime()+this.getExecutionTime();
			
			/**
			 * There is no conflict if the node started and finished processing before the current node starts or
			 * if it started after the current node finished processing
			 */
			if ((m.getStartExecutionTime()<this.getStartExecutionTime() && mFinishProcessing <=this.getStartExecutionTime()) || m.getStartExecutionTime()>=finishProcessing)
			{
				continue;
			}
			
			
			conflictNodes.add(m);
		}
		
		//if only one node is in the array, then it is the conflict to which were are checking the conflict against, this means there is no conflict , return null
		if (conflictNodes.size() == 1)
		{
			conflictNodes = null;
		}
		return conflictNodes;
	}
	
	
	/**
	 * This function identifies the nodes in conflict with the current one starting at index j(j being the index of the current node)
	 * It considers all nodes using the same vnf as conflicting. This should be used when updating nodes that are not in the strategy
	 * identifyVNFConflictBasedOnExecTime () should NOT be used instead of this
	 * @param strategyMoves ascending ordered array of nodes and links 
	 * @param j index of current node in strategyMoves (node that we want to check conflict against = this), -1 if the current node is not a strategy move (updating the pgraph for nodes not in strategy)
	 * @param skipNodesForCurrentPlayer set to true when updating nodes not in strategy for the graph. (current node not in strategy moves)
	 * @return array of conflict player nodes (null if no conflicts)
	 */
	public ArrayList<PlayerNode> identifyVNFConflict (ArrayList<Move>strategyMoves, int j, boolean skipNodesForCurrentPlayer)
	{	
		
		//if the node with which we are checking the conflict is a source or destination we return null (no conflict considered with ingress and egress nodes)
		if (this.type.equals(PNodeType.SOURCE)||this.type.equals(PNodeType.DESTINATION))
		{
			return null;
		}
	
		ArrayList<PlayerNode> conflictNodes = new ArrayList<PlayerNode> ();
		PlayerNode m = null;
		
		
		//add the node to which we are checking the conflict against
		conflictNodes.add(this);
		
		for (int i=j+1; i<strategyMoves.size(); i++)
		{			
			//do not check player Link
			if (strategyMoves.get(i) instanceof PlayerLink)
			{
				continue;
			}
			
			m= (PlayerNode)strategyMoves.get(i); 
			
			/**
			 * when updating the graph for nodes that are not in the strategy, we need to apply policy without considering the nodes in the strategy of the current player (player of current node)
			 */
			if (skipNodesForCurrentPlayer && m.getPGraph().getPlayer().equals(this.getPGraph().getPlayer()))
			{
				continue;
			}
		
			//continue if not same VNf or if the execution time = 0 (this means they are ingress or egress or source and destination node, we do not consider there is conflict/prevent null pointer exception when checking VNf)
			if (m.getExecutionTime() == 0 || !m.getVnf().equals(this.getVnf())  )
			{
				continue;
			}
			conflictNodes.add(m);
		}
		
		//if only one node is in the array, then it is the conflict to which were are checking the conflict against, this means there is no conflict , return null
		if (conflictNodes.size() == 1)
		{
			conflictNodes = null;
		}
		return conflictNodes;
	}
	
	
	
	/**
	 * This method will order the conflict nodes based on VNF policy that is:
	 * Ascending ordering based on :
	 * 1- Arrival time
	 * 2- If same arrival time order based on deadline
	 * 3- if same deadline order by vnf processing time
	 * 4- if same processing time order by index in conflict nodes
	 * 
	 * @param conflictNodes list of nodes requiring the same vnf at the same time
	 */
	public void orderBasedOnVNFPolicy(ArrayList<PlayerNode> conflictNodes)
	{
		PlayerNode temp = null;
		int deadlinej =0;
		int deadlinej1 =0;
		for (int i = 0; i < conflictNodes.size()-1; i++) 
		{
		      for (int j = i+1; j < conflictNodes.size(); j++) 
		      {
		    	  deadlinej = conflictNodes.get(i).getPGraph().getPlayer().getService().getDeadlineTime();
		    	  deadlinej1 = conflictNodes.get(j).getPGraph().getPlayer().getService().getDeadlineTime();
		    	
		    	  //this means they are ordered no need for any further check. This is needed so we do not fall into any of the following statement in case the order is correct
		    	 if (conflictNodes.get(i).getArrivalTime() < conflictNodes.get(j).getArrivalTime()) 
		    	 {
		    		  continue;
		    	 }
		    	 
		    	//order by arrival time
		        if (conflictNodes.get(i).getArrivalTime() > conflictNodes.get(j).getArrivalTime()) 
		        {
		           temp = conflictNodes.get(i);
		           conflictNodes.set(i, conflictNodes.get(j));
		           conflictNodes.set(j, temp);
		        }
		      //check if in the ordered list some has the same arrival time, order them by deadline
		        else if (conflictNodes.get(i).getArrivalTime() == conflictNodes.get(j).getArrivalTime() && deadlinej>deadlinej1)
		        {
		        	temp = conflictNodes.get(i);
		           conflictNodes.set(i, conflictNodes.get(j));
		           conflictNodes.set(j, temp);
		        }
		      //check if some have the same deadline, order them by processing time
		        else if (deadlinej == deadlinej1 && conflictNodes.get(i).getExecutionTime() > conflictNodes.get(j).getExecutionTime())
		        {
		          	temp = conflictNodes.get(i);
		           conflictNodes.set(i, conflictNodes.get(j));
		           conflictNodes.set(j, temp);
		        }
				//check if some have the same processing time, order by index (i< j) in this case since we are checking j and j+1, do nothing
		        
		    	/*//if we want to order by deadline first instead of arrival time 
		    	 * if (deadlinej < deadlinej1) 
			    	 {
			    		  continue;
			    	 }
					if (deadlinej >deadlinej1) 
			        {
			           temp = conflictNodes.get(i);
			           conflictNodes.set(i, conflictNodes.get(j));
			           conflictNodes.set(j, temp);
			        }
					 else if (  deadlinej==deadlinej1 && conflictNodes.get(i).getArrivalTime()>conflictNodes.get(j).getArrivalTime() )
			        {
			        	temp = conflictNodes.get(i);
			           conflictNodes.set(i, conflictNodes.get(j));
			           conflictNodes.set(j, temp);
			        }
					else if (conflictNodes.get(i).getArrivalTime()==conflictNodes.get(j).getArrivalTime() && conflictNodes.get(i).getExecutionTime() > conflictNodes.get(j).getExecutionTime())
			        {
			          	temp = conflictNodes.get(i);
			           conflictNodes.set(i, conflictNodes.get(j));
			           conflictNodes.set(j, temp);
			        }*/
		      }
		 }	

	}
	
	/**
	 * IMPORTANT: We assume a service has no 2 VNF with the same type in the chain
	 * This method take the nodes in conflict as parameter, 
	 * order them based on the policy
	 * reset their start processing time based on the policy and their weight
	 * 
	 * Update the arrival time startexec and order value and weight for all the successor nodes
	 * 
	 * 
	 * @param conflictNodes nodes in conflict using the same VNF at the same time
	 * @return the first node to be scheduled based on policy
	 * @throws Exception 
	 */
	public PlayerNode applyVNFPolicy (ArrayList<PlayerNode> conflictNodes) throws Exception
	{	
		PlayerNode p = null;
		//order nodes in conflict based on policy
		this.orderBasedOnVNFPolicy(conflictNodes);
		
		/**
		 * the first node in the array can start processing as soon as ready, this will be either arrival or start execution time which is the orderValue.
		 * It is based on how it is scheduled on the timeline. For instance, it  may have been in conflict before and we have updated it start execution based on policy
		 * and re-ordered it in the timeline. Hence when checking if it is in conflict again we need to consider that it is requesting the VNF at the new startExecution = ordervalue
		 */
		int startSchedule = conflictNodes.get(0).getOrderValue();
		for(int i=0; i<conflictNodes.size(); i++)
		{
			p = conflictNodes.get(i);
			
			//@TODO remove this if condition if identifyVNF conflict checks on execution time
			//if the arrival time is >start schedule, this means that there is no more conflicts (nodes executing at the same time as the current one), no need to do any update
			if (p.getArrivalTime()>startSchedule)
			{
				break;
			}
			//System.out.println("applying conflict "+p);
			p.setStartExecutionTime(startSchedule);
			p.setOrderValue(startSchedule);
			p.setWeight((p.getStartExecutionTime()-p.getArrivalTime()+p.getExecutionTime()));//set weight to wait time (startexec-arrival) +exectime
			
			startSchedule +=p.getExecutionTime();
			
			
			//update arrival time for link and nodes successor to this one (Future change:may have a problem when 2 vnfs are of the same type and mapped to the same vnf here)
			p.updateRelatedMoves();
		}
		
		return conflictNodes.get(0);
	}
	
	
	/**
	 * This method will get the start exec time for the current node not in the player strategy by applying the vnf policy without
	 * considering nodes of the same player as conflict nodes
	 * 
	 * @param conflictNodes nodes conflicting with the current one not including the nodes of the current player. 
	 * Conflict nodes should already have their values updated after applying the policy on the strategy moves
	 * 
	 * @return -1 if the current node was not in the conflict node (should not happen) return the start exec time based on the vnf policy
	 */
	public int getStartExecutionTimeBasedOnPolicy(ArrayList<PlayerNode> conflictNodes)
	{
		PlayerNode p = null;
		//order nodes in conflict based on policy
		this.orderBasedOnVNFPolicy(conflictNodes);
		
		/**
		 * the first node in the array can start processing as soon as ready, this will be the arrival 
		 *
		 */
		int startSchedule = conflictNodes.get(0).getArrivalTime();//conflictNodes.get(0).getOrderValue();
		for(int i=0; i<conflictNodes.size(); i++)
		{
			p = conflictNodes.get(i);
			
			//since the nodes in conflict are all the nodes requesting the same vnf of the current one, we may get to a point where the existing arrivalTime> startSchedule
			//in this case we the start execution time should be the arrival time and not the finish time of the previous node
			if (startSchedule<p.getArrivalTime())		
			{
				startSchedule = p.getArrivalTime();
			}
			//System.out.print ("Ordered:("+p.getId()+"-"+p.getPGraph().getPlayer().getPid()+")-"+startSchedule+" ;");
			if (p.equals(this))
			{
				return startSchedule;
			}
			
			startSchedule +=p.getExecutionTime();
		
		}
		
		return -1;
	}
	
	/**
	 * Once we update a node based on the vnf policy, we need to update the arrival time and order value and start exec time of all its successor moves in addition to the weight
	 * This is only done if the current node is in the player strategy
	 * @throws Exception 
	 */
	public void updateRelatedMoves() throws Exception
	{
		//the arrival time of the successor moves is the finish exec time of the current node
		int finishExecTime = this.getStartExecutionTime()+this.getExecutionTime();
		
		//temporary node to which we want to check the successors
		PlayerNode temp = this;
		
		while (temp.getType()!=PlayerNode.PNodeType.DESTINATION)
		{
			//search for the link having the current node as source node. This will ensure checking links in the strategy
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
			
			//if the link destination is the destination node, we do not want to update it (not even the arrival time since it will be different for different paths reaching to it)
			if (temp.getType()!=PlayerNode.PNodeType.DESTINATION)
			{//update the destination node of the link before moving to the next link
				temp.setArrivalTime(finishExecTime);
				
				//@important if the new arrival time (finishExecTime<StartExecutionTime, this means that there was a conflict with this node and it start execution was updated. 
				//we should not change it (decrease it to the new arrival) because we will be creating a conflict  with a previous node who resulted in updating the current one
				if (finishExecTime > temp.getStartExecutionTime())
				{
					temp.setStartExecutionTime(finishExecTime);
					temp.setOrderValue(finishExecTime);
				}
				temp.setWeight((temp.getStartExecutionTime()-temp.getArrivalTime()+temp.getExecutionTime()));//set weight to wait time (startexec-arrival) +exectime
				
				//update the finish exec time by adding the node exec time for the next iteration. this will be the arrival time to the link 
				finishExecTime = temp.getStartExecutionTime()+temp.getExecutionTime();
			}	
		}
	
	}
}
