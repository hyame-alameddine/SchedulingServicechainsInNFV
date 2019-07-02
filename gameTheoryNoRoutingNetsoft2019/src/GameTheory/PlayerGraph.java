/**
 * This class represent the player graph build as follows:
 * Virtual source and destination nodes are added with links connecting them to the other nodes. Cost of the source, destination and links connecting them is 0.
 * These are needed to determine the shortest path strategy and overcome the problem of having multiple sources and multiple destinations
 * 
 * For each middlebox n of the service, all VNFs of the same type of the middlebox are identified and a node for each of the VNFs is added to the graph.
 * for each node of middlebox n, we create a node for each VNF to which middlebox n+1 can be mapped. This is because the cost changes based on the previous node
 * Initial cost is determined based on the startScheduleTimeslot which will be the cost of the nodes of the first middlebox. The cost of links are and successors nodes
 * are determined sequentially without considering any waiting time. player links are routed through the shortest path without considering any weights on the nodes and link of the physical network
 */
package GameTheory;

import ilog.concert.IloException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import GameTheory.Strategy.StrategyType;
import GeneralClasses.Edge;
import GeneralClasses.Graph;
import GeneralClasses.Node;
import HelperClasses.Path;
import HelperClasses.PathDriver;
import HelperClasses.ServicesDriver;
import Model.ProbabilityDistributionLP;
import NFV.Middlebox;
import NFV.Service;
import NFV.VLink;
import Network.Link;
import Network.Network;
import Network.VNF;

public class PlayerGraph extends Graph {
	private Player player;
	Random rand;
	
	public PlayerGraph(int id, int nodeNb,int edgeNb, Player player)
	{
		super( id,  nodeNb, edgeNb);
		this.player = player;
		this.isDirected = true;
		this.rand = new Random();
		rand.setSeed(33);
	}
	
	public Player getPlayer()
	{
		return this.player;
	}
	
	public void setPlayer (Player player)
	{
		this.player = player;
	}
	
	
	public boolean equals(PlayerGraph e)
	{
		if (this.id != e.getId())
		{
			return false;
		}
		
		if (this.getPlayer().getId()!=e.getPlayer().getId())
		{
			return false;
		}
		
		return true;
	}
	
	public String toString()
	{
		String s= super.toString();

		return s;
	}
	
	
	/**
	 * This method will build the player graph as follows:
	 * -Add a virtual source  an destination nodes with vnf and middlebox = null;
	 * -Add playerNodes for each VNf the middlebox can be mapped to. Nodes for these VNFs are added to each VNFs node of the previous middlebox
	 * since they will have different cost. Initially costs of all nodes are set to 0
	 * Edges are added between all the created nodes and has the weight =0 
	 * 
	 * After the graph is build, the function will update the cost of nodes, set the routed through to the shortest path and set the weight of the player link
	 * @throws Exception 
	 *
	 * 
	 */
	public void buildGraph() throws Exception
	{
		Middlebox m;
		PlayerNode pNode;
		ArrayList<VNF>  VNFofSpecifiedType = null;
		ArrayList<PlayerNode> nodesOfPreviousMiddlebox= new ArrayList<PlayerNode>();
		ArrayList<PlayerNode> nodesOfCurrentMiddlebox= new ArrayList<PlayerNode>();
		int nodeCount = 0;
		int edgeCount = 0;
		VLink vl = null ;
		
		//create virtual source node
		this.nodeList[nodeCount] = new PlayerNode (nodeCount, 0, null, null, PlayerNode.PNodeType.SOURCE, this);
		nodeCount++;
		
		//add source node as node at previous level so we can have edges between it and the nodes of the first middlebox
		nodesOfPreviousMiddlebox.add((PlayerNode)this.nodeList[0]);
		
		for (int i=0;i<this.player.getService().getMiddleboxes().size(); i++)
		{
			m = this.player.getService().getMiddleboxes().get(i);
			VNFofSpecifiedType = this.player.getGame().physicalNetwork.getVNFOfSpecifiedType(m.getType());
			
			for(int j=0; j<nodesOfPreviousMiddlebox.size(); j++)
			{
				//for each node instance of the previous middlebox in the chain, we need to create a node for each VNF instance in the network of the same type of the current middlebox
				for(int k=0; k<VNFofSpecifiedType.size(); k++)
				{
					//we set the weight of the nodes and the edges to 0 initially
					pNode = new PlayerNode(nodeCount, 0, VNFofSpecifiedType.get(k), m,PlayerNode.PNodeType.VNF, this );
					this.nodeList[nodeCount] = pNode;
					nodesOfCurrentMiddlebox.add(pNode);//add node to the list of nodes of the current middlebox
					nodeCount++;
					
					
					//create edge between nodesOfPreviousMiddlebox and the created node					
					if (i!=0)
					{//if the player link is between the nodes of middleboxes, get the Vlink which the plink should represent
						vl = this.player.getService().getVLink(this.player.getService().getMiddleboxes().get(i-1), m);
					}
					else
					{//if the player link is between the source node and the nodes of the first middlebox, set vlink  to null since they do not represent any vlink
						vl = null;
					}
					this.edgeList[edgeCount] = new PlayerLink(edgeCount, 0, nodesOfPreviousMiddlebox.get(j), pNode, vl, this);				
					edgeCount++;
				}
			}
			
			//update the nodes of the previous middlebox to the  current middlebox
			nodesOfPreviousMiddlebox=null;
			nodesOfPreviousMiddlebox = nodesOfCurrentMiddlebox;	
			
			//reset the nodes of the current middlebox
			nodesOfCurrentMiddlebox = null;
			nodesOfCurrentMiddlebox = new ArrayList<PlayerNode>();
		}
		
		//add virtual destination node and edges to it 	
		this.nodeList[nodeCount] = new PlayerNode (nodeCount, 0, null, null, PlayerNode.PNodeType.DESTINATION, this);

		//add edges between nodes of the last middlebox and the virtual destination node/ Set their Vlink to null
		for (int i=0; i<nodesOfPreviousMiddlebox.size(); i++)
		{
			this.edgeList[edgeCount] = new PlayerLink(edgeCount, 0, nodesOfPreviousMiddlebox.get(i), (PlayerNode) this.nodeList[nodeCount], null, this);
			edgeCount++;
		}
		
		//update the cost of nodes and links and set the routed through for the links after buidling the graph
		boolean [] visited = new boolean [this.getNodeNb()];
		PlayerNode currentNode = (PlayerNode) this.getNodeList()[0];	
		ArrayList<Path> shortestPaths = PathDriver.buildShortestPaths(this.player.getGame().physicalNetwork, false, null, null);		
		this.updateInitialCost(0, visited, currentNode, shortestPaths);//change 0 to the player arrival time if we want to consider online 

	}
	
	
	/**
	 * This method will set the cost of each player node and link in the graph by considering that there is no waiting time for the traffic
	 * and that the schedule starts at a certain timeslot 
	 * 
	 * The cost of each player node/link is = waiting time+processing/transmission time. Initially we consider that waiting =0. The traffic is processed/transmitted as soon as it arrives
	 * The code will also map each player link to a the shortest path in the physical network using Dijkstra (without weights of nodes and links)
	 * The source and destination virtual nodes in addition to the links connecting them should all have a cost =0 since they were only added to 
	 * facilitate the calculation of the shortest path in the player graph to determine the strategy. Setting their cost wait+processing time
	 * will require many destination nodes since each will have a cost based on its precedence cost and we will fall in he problem of not being able to solve shortest path
	 * 
	 * Source and destination node in addition to the links connecting them should always have a cost/weight=0.
	 * 
	 * @param startScheduleTimeSlot time slot at which the processing on the node can start (used for the first middlebox at the first call of the function)
	 * @param visited array with the same size of the number of nodes in the player graph. Set to false by default and used for recursion
	 * @param currentNode should be the source node
	 * @param shortestPath list of shortest path between each 2 servers in the network. This used to prevent calling dijkstra many times
	 * 
	 * @throws Exception 
	 * 
	 */
	public void updateInitialCost (int startScheduleTimeSlot, boolean [] visited, PlayerNode currentNode, ArrayList<Path> shortestPaths ) throws Exception
	{
		ArrayList<Link> shortestPath = new ArrayList<Link>();
		PlayerLink pl = null;
		int startTransmissionTimeslot = 0;
		Path p = null;
		PlayerNode adj = null;
		if (currentNode.getType().equals(PlayerNode.PNodeType.VNF))
		{
			//set the current node as visited, the visited should have the index of each node=to its id
			visited[currentNode.getId()] = true;
		}
		//set the cost of the current node to the processing/execution time since we consider that initially there is no waiting time
		currentNode.setWeight(currentNode.getExecutionTime());
		
		//set the arrival time and startExecution to the startScheduleTimeSlot
		currentNode.setArrivalTime(startScheduleTimeSlot);
		currentNode.setStartExecutionTime(startScheduleTimeSlot);
		currentNode.setOrderValue(currentNode.getArrivalTime());//set it = to arrival Time
		//get direct adjacent nodes- the nodeList has the nodes index =to their ids
		ArrayList<Node> adjacentNodes = PathDriver.getAdjacentNodes(this,nodeList[currentNode.getId()]);
		
		for (int i=0; i<adjacentNodes.size(); i++)
		{
			adj = (PlayerNode) adjacentNodes.get(i);
			if (visited[adj.getId()])
			{	
				continue;
			}
			//update the cost of the playerLink and set their routed through to the shortest path in the physical link
			pl = (PlayerLink)this.getEdge(currentNode, adj, true);
			
			if (pl ==null)
			{
				throw new Exception ("Player graph should be connected. Player link not found between Node "+currentNode.getId()+" and node "+adj.getId());
			} 
			
			// the weight of the links connecting the source node should be the same as the source node weight (no transmission delays)
			//the start processing of the next node after the source node should be also the same 
			if (currentNode.getType().equals(PlayerNode.PNodeType.SOURCE) && pl.getVLink()==null)
			{
				pl.setWeight(0);
			}
			//if the link has no Vlink and the node is not the source node, and the link destination is the destination node, then weight should be set to set to 0 
			else if (pl.getVLink()==null && ((PlayerNode)pl.getDestination()).getType().equals(PlayerNode.PNodeType.DESTINATION))
			{
				
				pl.setWeight(0);
				//the transmission can start once the processing by the vnf (playeNode) is done (if the current node is source node, the transmission starts at startScheduleTimeSlot)
				startTransmissionTimeslot = currentNode.getStartExecutionTime()+currentNode.getExecutionTime();
							
				//set the arrival time and startExecution to the startScheduleTimeSlot
				pl.setArrivalTime(startTransmissionTimeslot);
				pl.setStartExecutionTime(startTransmissionTimeslot);
				pl.setOrderValue(pl.getArrivalTime());//set it = to arrival Time
				startScheduleTimeSlot = 0;
			}
			else
			{
				//the transmission can start once the processing by the vnf (playeNode) is done (if the current node is source node, the transmission starts at startScheduleTimeSlot)
				startTransmissionTimeslot = currentNode.getStartExecutionTime()+currentNode.getExecutionTime();
				
				
				//set the arrival time and startExecution to the startScheduleTimeSlot
				pl.setArrivalTime(startTransmissionTimeslot);
				pl.setStartExecutionTime(startTransmissionTimeslot);
				pl.setOrderValue(pl.getArrivalTime());//set it = to arrival Time
				//check if the player link is between player node representing vnf on the same server or not to account for transmission delays or not
				//we can not use pl.vlink.areHostedOnSameServer() because it will check the middlebox to which vnf they are mapped which will not be populated at this point
				if (pl.areHostedOnSameServer())
				{
					//set the weight to 0 if VNF are on the same server (no transmission
					pl.setWeight(0);
					pl.setExecutionTime(0);//update the execution time to 0
					
					startScheduleTimeSlot = startTransmissionTimeslot;
					
				}
				else
				{
					//set the weight to the execution time as we consider that the transmission starts as soon as the traffic arrives to the link (no wait)
					pl.setWeight(pl.getExecutionTime());
					
					//once the transmission is done, the processing on the next node can start. Here we consider the transmission delay on one link only
					//without accounting for the number of physical links to which the player link is routed through to keep consistency when comparing with the model
					startScheduleTimeSlot = startTransmissionTimeslot+pl.getExecutionTime();//transmission delays
					
					//update the routed through to the shortest path on the physical network between the physical machines hosting the VNF which the playerNodes represent
					p = PathDriver.searchPath(shortestPaths, this.player.getGame().physicalNetwork.getNodeList()[((PlayerNode)pl.getSource()).getVnf().getPmId()], this.player.getGame().physicalNetwork.getNodeList()[((PlayerNode)pl.getDestination()).getVnf().getPmId()]);
					
					//convert from arraylist of edge to link
					for(int k=0;k<p.getPath().size(); k++)
					{
						shortestPath.add((Link)p.getPath().get(k));
					}
					
					pl.setRoutedThrough(shortestPath);
					shortestPath = new ArrayList<Link>();
					p=null;
				}
			
			}
			updateInitialCost(startScheduleTimeSlot,visited,adj, shortestPaths);
		}
	}
	

	
	/**
	 * This method will update the cost of nodes and links in the graph based on policies without considering those in strategy
	 * it will apply the policy by considering strategy moves while disregarding those related to the current player
	 * It will perform a depth first search
	 * 
	 * @param startScheduleTimeSlot time slot at which the processing on the node can start (used for the first middlebox at the first call of the function)
	 * @param visited array with the same size of the number of nodes in the player graph. Set to false by default and used for recursion
	 * @param currentNode should be the source node
	 * @param strategiesMoves moves in the strategies of all the players including the current one. Moves should have updated values after identifying conflict for them
	 * @param linksArraySize size of the physical links timeslot array. (how many time slots per link) usually set to the services highest deadline
	 * @throws Exception 
	 */
	public void updateCost(int startScheduleTimeSlot, boolean [] visited, PlayerNode currentNode, ArrayList<Move> strategiesMoves) throws Exception
	{
		PlayerLink pl = null;
		int startTransmissionTimeslot = 0;
		int startExecution=0;
		ArrayList<PlayerLink> linksConflict = null;
		ArrayList<ArrayList<Integer>>physicalLinks = this.player.getGame().physicalNetwork.getLinksTimeBw(this.getPlayer().getGame().linksArraySize);
		PlayerNode adj = null;
		//System.out.println("Checking node "+currentNode);
		if (currentNode.getType().equals(PlayerNode.PNodeType.VNF))
		{
			//set the current node as visited, the visited should have the index of each node=to its id
			visited[currentNode.getId()] = true;
		}
		
		// if node in strategy is common for path not in strategy update the startScheduleTimeSlot
		//Needed only when no strategy is selected : we do not want to set arrival and start scheduling for destination because it may have many based on on how many paths exist in the graph
		if (currentNode.getType().equals(PlayerNode.PNodeType.DESTINATION)
			|| (this.getPlayer().getStrategy()!=null && this.getPlayer().getStrategy().findMove(currentNode)) )
		{
			startScheduleTimeSlot = currentNode.getStartExecutionTime()+currentNode.getExecutionTime();
		}
		else // update the info for nodes not in strategy
		{
			//reset the weight to exec time
			currentNode.setWeight(currentNode.getExecutionTime());
			
			//set the arrival time and startExecution to the startScheduleTimeSlot before identifying the conflict so if no conflicts are found we still update the node
			currentNode.setArrivalTime(startScheduleTimeSlot);
			currentNode.setStartExecutionTime(startScheduleTimeSlot);
			currentNode.setOrderValue(currentNode.getArrivalTime());//set it = to arrival Time
			
			//get nodes conflicting with the current one without checking for nodes for the current player
			ArrayList<PlayerNode> nodesConflict = currentNode.identifyVNFConflict(strategiesMoves, -1, true);
			
						
			//if there is a conflict apply policy
			if (nodesConflict!=null)
			{	/*System.out.println("UpdateCost: nodes conflict with "+currentNode);			
				for(int k=0; k<nodesConflict.size(); k++)
				{
					System.out.print ("("+nodesConflict.get(k).getId()+"-"+nodesConflict.get(k).getPGraph().getPlayer().getPid()+") ;");
				}*/
				//get the startexec time based on policy
				startExecution = currentNode.getStartExecutionTimeBasedOnPolicy(nodesConflict);
				//System.out.println("startExecution "+startExecution);	
				if (startExecution == -1)
				{
					throw new Exception ("Node not in strategy is not found in conflict nodes ");
				}
				else
				{//System.out.println("Confff "+startExecution);
					//update the start exec time based on policy
					currentNode.setStartExecutionTime(startExecution);
					currentNode.setOrderValue(startExecution);
					currentNode.setWeight(currentNode.getStartExecutionTime()-currentNode.getArrivalTime()+currentNode.getExecutionTime());
					
					// should we update related move?? probably they will be updated by this function automatically
					startExecution = 0;//reset
				}
			}
				
		}	
		
		//get direct adjacent nodes- the nodeList has the nodes index =to their ids
		ArrayList<Node> adjacentNodes = PathDriver.getAdjacentNodes(this, nodeList[currentNode.getId()]);
		
		for (int i=0; i<adjacentNodes.size(); i++)
		{//System.out.println("Adjacent nodes ");
			adj = (PlayerNode)adjacentNodes.get(i);
			if (visited[adj.getId()])
			{	
				continue;
			}
			//update the cost of the playerLink 
			pl = (PlayerLink)this.getEdge(currentNode, adjacentNodes.get(i), true);
			
			if (pl ==null)
			{
				throw new Exception ("Player graph should be connected. Player link not found between Node "+currentNode.getId()+" and node "+adj.getId());
			} 
			
			//if it is a common link with the strategy, we need to skip it		
			//if no strategy is selected for the player yet, we need to update the links
			if (this.getPlayer().getStrategy()==null||(!this.getPlayer().getStrategy().findMove(pl)))
			{
				//the transmission can start once the processing by the vnf (playeNode) is done (if the current node is source node, the transmission starts at startScheduleTimeSlot)
				startTransmissionTimeslot = currentNode.getStartExecutionTime()+currentNode.getExecutionTime();
				
				
				//set the arrival time and startExecution to the startScheduleTimeSlot
				// this will also update links related to destination (those links will have weight and exec time =0)
				pl.setArrivalTime(startTransmissionTimeslot);
				pl.setStartExecutionTime(startTransmissionTimeslot);
				pl.setOrderValue(pl.getArrivalTime());//set it = to arrival Time
				
				
				//check if the player link is between player node representing vnf on the same server or not to account for transmission delays or not
				//we can not use pl.vlink.areHostedOnSameServer() because it will check the middlebox to which vnf they are mapped which will not be populated at this point
				if (((PlayerNode)pl.getSource()).getType().equals(PlayerNode.PNodeType.SOURCE)|| ((PlayerNode)pl.getDestination()).getType().equals(PlayerNode.PNodeType.DESTINATION) || pl.areHostedOnSameServer() )
				{
					//set the weight to 0 if VNF are on the same server (no transmission
					pl.setWeight(0);
					pl.setExecutionTime(0);//update the execution time to 0
					
					startScheduleTimeSlot = startTransmissionTimeslot;					
				}
				/*
				 * //added When only considering VNF scheduling And remove the second  and third else statement for that
				 * */
				else
				{
					//set the weight to the execution time as we consider that the transmission starts as soon as the traffic arrives to the link (no wait)
					pl.setWeight(pl.getStartExecutionTime() - pl.getArrivalTime()+pl.getExecutionTime());
					startScheduleTimeSlot = startTransmissionTimeslot+pl.getExecutionTime();
				}
			/*	else
				{
					
					//get the list of links that are sharing at least one physical link with l without considering those of the current player pl
					linksConflict = pl.identifyLinkConflict(strategiesMoves,-1, true);
					
					if (linksConflict == null)
					{//System.out.println("NO Confff");
						//if no conflict then transmission can start directly. wait =0
						pl.setWeight(pl.getExecutionTime());	
						startScheduleTimeSlot = startTransmissionTimeslot+pl.getExecutionTime();
						updateCost(startScheduleTimeSlot,visited,adj, strategiesMoves, linksArraySize);
						continue;
					}*/
					/*System.out.println("UpdateCost: Conflict Links= with =="+pl);
					for(int k=0; k<linksConflict.size(); k++)
					{
						System.out.print ("("+linksConflict.get(k).getId()+"-"+linksConflict.get(k).getPGraph().getPlayer().getPid()+") ;");
					}*/
					/**
					 * the startTransmissionTimeslot is got without considering that there is reservation on the links 
					 * In fact if their was reservation on the physical links at the same required time slots those virtual links will be in conflict
					 * If they are in conflict then we need to apply the policy while considering that all the physical link capacity is available
					 * 
					 */					
					/*startTransmissionTimeslot = pl.getStartExecutionTimeBasedOnPolicy(linksConflict, physicalLinks);
					
					//reset the physical links because we are altering it in getStartExecutionTimeBasedOnPolicy
					physicalLinks = this.player.getNetwork().getLinksTimeBw(linksArraySize);
					
					System.out.println(startTransmissionTimeslot+"trans link");
					pl.setStartExecutionTime(startTransmissionTimeslot);
					pl.setOrderValue(startTransmissionTimeslot);
					
					//set the weight to the execution time as we consider that the transmission starts as soon as the traffic arrives to the link (no wait)
					pl.setWeight(pl.getStartExecutionTime() - pl.getArrivalTime()+pl.getExecutionTime());
					
					//once the transmission is done, the processing on the next node can start. Here we consider the transmission delay on one link only
					//without accounting for the number of physical links to which the player link is routed through to keep consistency when comparing with the model
					startScheduleTimeSlot = startTransmissionTimeslot+pl.getExecutionTime();//transmission delays					
				}*/			
			}
			/*else
			{//System.out.println("D not in strategy");
				//if the link was in strategy update the startScheduleTimeSlot. This will handles the case where we have a->b->c->{D, E} where only D is not in strategy
				startScheduleTimeSlot = startTransmissionTimeslot+pl.getExecutionTime();//transmission delays
			}*/

			updateCost(startScheduleTimeSlot,visited,adj, strategiesMoves);
		}
	}


	 /**
	  * This method will return the longest path in a graph whose weight is < deadline (if deadline is not =0)
	  * if the deadline =0 it will return the longest path
	  * 
	  * @param deadline integer (usually service deadline) which we need to get the longest path which the weight is <deadline
	  * @return arraylist of edges determining the longest path
	  * @throws Exception
	  */
	public ArrayList<Edge> getLongestPath( int deadline) throws Exception
	{
		boolean[] visited = new boolean [this.getNodeNb()];
		Node currentNode =  this.getNodeList()[0];
		ArrayList<Edge> tillNow = new ArrayList<Edge>();
		ArrayList<ArrayList<Edge>> allPaths = new ArrayList<ArrayList<Edge>>(); 
		
		//this will update the allPaths array with allPaths in the graph
		PathDriver.getAllPaths( this,currentNode, visited, tillNow, allPaths);
		
		
		return this.getLongestPath(allPaths, deadline);
	}
	
	 /**
	  * This method will return the longest path from a list of path whose weight is < deadline (if deadline is not =0)
	  * if the deadline =0 it will return the longest path
	  * @param allPaths list of path to get the longest path from 
	  * @param deadline integer (usually service deadline) which we need to get the longest path which the weight is <deadline
	  * @return arraylist of edges determining the longest path, null if no longest within the deadline exists
	  * @throws Exception
	  */
	public ArrayList<Edge> getLongestPath(ArrayList<ArrayList<Edge>> allPaths, int deadline) throws Exception
	{	  
		ArrayList<Edge> p = null;
		int pathLength =  0;
		
		ArrayList<Edge> longestPath = null;
		int longestLength =0;
		
		for (int i=0; i<allPaths.size(); i++)
		{
			p = allPaths.get(i);
			//the length of the path is the arrival time  to its last link (because no processing on that link)
			pathLength = ((PlayerLink)(p.get(p.size()-1))).getArrivalTime();
			
			if (deadline>0 && (pathLength>deadline || pathLength<longestLength))
			{
				continue;
			}
			
			//need to keep this condition in case the deadline was = 0
			if (longestLength < pathLength)
			{
				longestLength = pathLength;
				longestPath = p;
			}
		}
		return longestPath;				
	}
	
	
	/**
	 * This method is used to get the path which completion time is the closest to the players avg completion time.
	 * It is mainly used for choosing a strategy of type PLAYERS_AVG_COMPLETIONTIME
	 * 
	 * @return the path with lowest difference between its completion time and the playersAvgCompletionTime
	 * @throws Exception
	 */
	public ArrayList<Edge> getPathWithClosestAvgCompletionTime () throws Exception
	{
		boolean[] visited = new boolean [this.getNodeNb()];
		Node currentNode =  this.getNodeList()[0];
		ArrayList<Edge> tillNow = new ArrayList<Edge>();
		ArrayList<ArrayList<Edge>> allPaths = new ArrayList<ArrayList<Edge>>(); 
		
		ArrayList<Edge> pathWithBestPayoff = null;
		double bestPayOff = Double.MAX_VALUE;
		int pathCompletionTime = 0;
		ArrayList<Edge> p=null;
		ArrayList<Edge> strategyPath = null;
		double playersAvgCompletionTime = this.player.getGame().calculatePlayersAvgCompletionTime(this.player);
		
		//this will update the allPaths array with allPaths in the graph
		PathDriver.getAllPaths( this,currentNode, visited, tillNow, allPaths);
	
		for (int i=0; i<allPaths.size(); i++)
		{ 
			p= allPaths.get(i);
			
			if (this.player.getStrategy()!=null && this.isStrategyPath(p))
			{
				strategyPath = p;
			}
			
			//the completion time of the path is the arrival time  to its last link (because no processing on that link)
			pathCompletionTime = ((PlayerLink)(p.get(p.size()-1))).getArrivalTime();
		
			//get the path with the best 
			if (Math.abs(playersAvgCompletionTime-pathCompletionTime)<bestPayOff)
			{
				pathWithBestPayoff = p;
				bestPayOff = Math.abs(playersAvgCompletionTime-pathCompletionTime);
			}
		}
		
		//if there is no improvement in the payOff, return the path selected as strategy in the previous iteration
		// do not return strategyPath just in case the values (arrival, start exec..)were different
		
		if(this.player.getStrategy()!=null && bestPayOff == this.player.getStrategy().payOff)
		{
			//return the same path of the strategy. Do not use strategy.getPath() just in case it was holding values based on an old graph (not updated one)
			return strategyPath;
		}
		return pathWithBestPayoff;
		
	}
	
	
	/**
	 * This method will allow to choose a mixed strategy by calculating the probabilities of all the paths by CT(path)/sumCT of all paths
	 * if ilp is false, the distribution will be calculated as follows.
	 * Since the path with the shortest completion time should have the highest probability, we sort the probabilities in descending order and path in ascending order
	 * of their CT and consider that the highest probability is assigned to the path with lowest CT (one shortcoming is that if we have many path with same CT for exemplae 15, 15, 12)
	 * the one with 12 will have same probability of the one with CT=15
	 * 
	 * we generate a random number btw 0 and 1 and check between which range is it to choose the strategy. 
	 * @param ilp if true it will use probabilityDistibutionILP
	 * @return the chosen strategy
	 * @throws Exception 
	 */
	public  ArrayList<Edge> getMixedStrategyPath(boolean ilp) throws Exception
	{
		boolean[] visited = new boolean [this.getNodeNb()];
		Node currentNode =  this.getNodeList()[0];
		ArrayList<Edge> tillNow = new ArrayList<Edge>();
		ArrayList<ArrayList<Edge>> allPaths = new ArrayList<ArrayList<Edge>>(); 
		Double[] probabilities ;
		double [] probabilityRange;
		double randNb =0;
		//Random rand = new Random();
		//rand.setSeed(12);//don't use it as it will choose the same number always
		//this will update the allPaths array with allPaths in the graph
		PathDriver.getAllPaths( this,currentNode, visited, tillNow, allPaths);
		
		if(!ilp)
		{
			//order path in ascending order of their completion time. so the one with lowest completion time will have the highest probability
			allPaths = this.sortPathBasedOnCompletionTime(allPaths);
			
			//get the probabilities of all the pure strategies
			probabilities = this.getProbabilityDistribution(allPaths);
			//sort them in descending order
			Arrays.sort(probabilities, Collections.reverseOrder());
		}
		else
		{
			//get the probabilities of all the pure strategies using probabilityDistibutionILP
			probabilities = this.getProbabilityDistributionILP(allPaths);
		}
		System.out.println("probabilities: ");
		for (int i=0; i<probabilities.length; i++)
		{
			System.out.print(probabilities[i]+",");
		}
		System.out.println();
		//set up an array of range for probabilities if probabilities are 2/3 and 1/3 the range will be 2/3 and 2/3+1/3
		probabilityRange = new double [probabilities.length];
		probabilityRange[0] = probabilities[0];
		System.out.print("probabilityRange: "+probabilityRange[0]+",");
		for (int i=1; i<probabilityRange.length; i++)
		{
			probabilityRange[i]=probabilities[i]+probabilityRange[i-1];
			System.out.print(probabilityRange[i]+",");
		}
		System.out.println();
		//choose strategy
		randNb = this.rand.nextDouble();//1 excluded
		System.out.println("randNb"+randNb);
		for (int i=0; i<probabilityRange.length; i++)
		{
			if(randNb<probabilityRange[i])
			{
				return allPaths.get(i);
			}
		}
		return allPaths.get(0);// in case of any error return the strategy with lowest completion time
	}
	
	
	/**
	 * This method will calculate the probability distribution of the player based on the paths completion time
	 * if 2 paths with CT1, CT2 the probabilities will be CT1/(CT1+CT2) and CT2/(CT1+CT2)
	 * @param allPaths all paths in the graph (pure strategies)
	 * 
	 * @return array of probabilities
	 */
	public Double [] getProbabilityDistribution (ArrayList<ArrayList<Edge>> allPaths)
	{
		Double[] probabilities  = new Double[allPaths.size()];
		ArrayList<Edge> p = null;
		int pathLength =0;//completion time
		int sum =0; // sum of completion time of all pure startegies (paths)
		for (int i=0; i<allPaths.size(); i++)
		{
			p = allPaths.get(i);
			//the length of the path is the arrival time  to its last link (because no processing on that link)
			pathLength = ((PlayerLink)(p.get(p.size()-1))).getArrivalTime();
			
			probabilities[i] = new Double(pathLength);
			sum+=pathLength;	
			
			pathLength=0;
		}
		

		for(int i=0; i<probabilities.length; i++)
		{
			probabilities[i]/=sum;
			
		}
		return probabilities;
	}
	
	/**
	 * This method will calculate the probability distribution of the player based on the paths completion time
	 * and using the ProbabilityDistributionLP
	 * @param allPaths all paths in the graph (pure strategies)
	 * 
	 * @return array of probabilities
	 * @throws IloException 
	 */
	public Double [] getProbabilityDistributionILP (ArrayList<ArrayList<Edge>> allPaths) throws IloException
	{
		int[] completionTime  = new int[allPaths.size()];
		ArrayList<Edge> p = null;
		Double[] probabilities  = new Double[allPaths.size()];
		for (int i=0; i<allPaths.size(); i++)
		{
			p = allPaths.get(i);
			//the length of the path is the arrival time  to its last link (because no processing on that link)
			completionTime[i] = ((PlayerLink)(p.get(p.size()-1))).getArrivalTime();			
		}
		ProbabilityDistributionLP pdModel = new ProbabilityDistributionLP(completionTime, this.player.getPid());
		pdModel.buildILPModel();
		pdModel.runILPModel("testResults/ProbabilityLPResults");
		
		for (int i=0; i<probabilities.length; i++)
		{
			probabilities[i] = new Double (pdModel.cplex.getValue(pdModel.x[i]));
		}
		
		return probabilities;
	}
	
	
	/**
	 * Sort path (pure strategies) based on ascending order of their completion time
	 * @param allPaths
	 * @return array list of paths sorted in ascending order based on completion time
	 */
	public ArrayList<ArrayList<Edge>>  sortPathBasedOnCompletionTime(ArrayList<ArrayList<Edge>> allPaths)
	{
		ArrayList<Edge>  temp;
		int p1,p2;
		for (int i=0; i<allPaths.size(); i++)
		{ 
			for (int j=i; j<allPaths.size(); j++)
			{ 
				//the length of the path is the arrival time  to its last link (because no processing on that link)
				p1 = ((PlayerLink)( allPaths.get(i).get( allPaths.get(i).size()-1))).getArrivalTime();
				p2 = 	((PlayerLink)( allPaths.get(j).get( allPaths.get(j).size()-1))).getArrivalTime();
				
				if (p2<p1)
				{
					temp= allPaths.get(i);
					allPaths.set(i,allPaths.get(j));
					allPaths.set(j,temp);
				}
			}
		}
		
		
		return allPaths;
			
	}
	/**
	 * This method returns true if the path passed as parameter contains the same edges (regardless on the values)that is the same as the strategy path
	 * @param path to check if it is the same as strategy
	 * @return true if the path passed as parameter is the same as the strategy one
	 */
	public 	boolean isStrategyPath (ArrayList<Edge>path)
	{
		ArrayList<PlayerLink> strategyPath = this.player.getStrategy().getStrategyPath();
		
		if (path.size()!=strategyPath.size())
		{
			return false;
		}
		
		for (int i=0; i<strategyPath.size(); i++)
		{ 
			if (!path.get(i).equals((Edge)strategyPath.get(i)))
			{
				return false;
			}
			
		}
		
		return true;
	}
	
	public static void main (String []args) throws Exception
	{
		Network network = new Network(10,5,4,5,1000,1000,1,0);
		
	   ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();
		int [] tf = physicalNetwork.get(2)[0];

		System.out.println(network);
		
		ServicesDriver driver = new ServicesDriver (3,  100, 3,5, 300, 500, 5 , 1, 500, 1500,tf);
		ArrayList<Service>services = driver.generateServices(50,10,100,12);
		
		Game g = new Game ( network, services,Strategy.StrategyType.SHORTEST_PATH,1.25);
		//System.out.println( services.get(1));
		
		Player p = new Player(0, services.get(0), g);

		System.out.println(p);
		
	
	
		ArrayList<Path> shortestPaths = PathDriver.buildShortestPaths(network, false, null, null);
		for (int i=0; i<shortestPaths.size(); i++)
		{
			System.out.println (shortestPaths.get(i));
		}
		
		
	}
}
