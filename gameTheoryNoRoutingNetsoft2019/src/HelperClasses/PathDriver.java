/**
 * This class will help building the list of shortest based on certain criteria using dijkstra algorithm
 */
package HelperClasses;

import java.util.ArrayList;

import GameTheory.Game;
import GameTheory.Player;
import GameTheory.PlayerLink;
import GameTheory.PlayerNode;
import GameTheory.Strategy.StrategyType;
import GeneralClasses.Edge;
import GeneralClasses.Graph;
import GeneralClasses.Node;
import NFV.Service;
import Network.Network;

public class PathDriver {
	
	
	/**
	 * This method will take a graph and return an array of shortest path between each 2 nodes.
	 * It will create a copy of the graph and set the nodes and edgeWeights to those passed as parameter
	 * If those are set to null the edge and node weights will be set to 0
	 * 
	 * @param n graph to get the shortest path
	 * @param considerNodeWeight consider node weight when calculating the shortest path
	 * @param nodeWeights array of node weights, null if we want it to be =0;
	 * @param edgeWeights array of edge weights, null if we want it to be =0;
	 * 
	 * @return ArrayList<Path>  shortestPath
	 * @throws Exception 
	 */
	public static ArrayList<Path> buildShortestPaths (Graph n, boolean considerNodeWeight, int [] nodeWeights, int[]edgeWeights) throws Exception
	{
		ArrayList<Path>  shortestPath= null;
		Graph copyGraph=n.clone();
		
		copyGraph.updateNodeWeights(nodeWeights);
		copyGraph.updateEdgeWeights(edgeWeights);
				
		DijkstraAlgorithm dj = new DijkstraAlgorithm(copyGraph, considerNodeWeight);
		shortestPath = dj.buildShortestPaths();
		
		return shortestPath;
		
	}
	
	
	/**
	 * This method returns the shortest path between a source and destination as a list of edges
	 * @param n graph 
	 * @param considerNodeWeight if node weights should be considered when determining the shortest path
	 * @param source source node
	 * @param destination destination node
	 * @return array list of edges representing the shortest path
	 * @throws Exception
	 */
	public static ArrayList<Edge> getShortestPath(Graph n, boolean considerNodeWeight, Node source, Node destination) throws Exception
	{
		DijkstraAlgorithm dj = new DijkstraAlgorithm(n, considerNodeWeight);
	     ArrayList<Edge> shortestPath = dj.execute(source,destination);
		
		return shortestPath;
		
	}

	 
	 /**
	  * 
	  * This method will return a list of all path in a graph
	  * @param n graph (usually a player graph) where there can only be multiple path from the source to the destination node. But their is only one path btw 2 nodes
	  * @param currentNode should be set to the graph source node (first node in the node list array)
	  * @param visited array of boolean with the size of nodes in the graph, set to false by default
	  * @param tillNow empty array list of edges
	  * @param allPaths empty arraylist that will be updated with the all the path in the graph
	  * @throws Exception
	  */
	 public static void getAllPaths(Graph n, Node currentNode, boolean [] visited, ArrayList<Edge> tillNow, ArrayList<ArrayList<Edge>> allPaths) throws Exception
	 {	   
		visited[currentNode.getId()]=true;
		ArrayList<Node> adjacentNodes = PathDriver.getAdjacentNodes(n, n.getNodeList()[currentNode.getId()]);
		Edge l= null;
		
		for (int i=0; i<adjacentNodes.size(); i++)
		{
			if (visited[adjacentNodes.get(i).getId()])
			{	
				continue;
			}
			
			l = PathDriver.getEdge(n, currentNode, adjacentNodes.get(i), true);
			 tillNow.add(l);
			getAllPaths(n,  adjacentNodes.get(i),  visited, tillNow,allPaths); 
		}
		 
		//if we reached the destination node, then we have a path from source to destination to save
		 if(currentNode.equals(n.getNodeList()[n.getNodeList().length-1])){
			 ArrayList<Edge>path = new ArrayList<Edge>();
		     
			 for(Edge tmp: tillNow){
		        path.add(tmp);
		      }
		      allPaths.add(path);	      
		 }
		 
		 visited[currentNode.getId()] = false;
		 
		 if (tillNow.size() !=0)
		 {
			 tillNow.remove(tillNow.size() - 1);
		 }
	 }


			
	/**
	 * This method will get the nodes that are successors of the node passed as parameter
	 * @param graph g
	 * @param node node to which we want to get the successors
	 * 
	 * @return array list of successors nodes
	 */
	public static ArrayList<Node> getAdjacentNodes(Graph g, Node node)
	{
		ArrayList<Node> adjacentNodes = new ArrayList<Node>();
		
		//get edges having the node as source, adjacent nodes will be the destination nodes of the links
		ArrayList<Edge> edgesWithNodeSource = PathDriver.getAdjacentEdges (g, node, "source");
		
		for (int i=0; i<edgesWithNodeSource.size(); i++)
		{
			adjacentNodes.add ( edgesWithNodeSource.get(i).getDestination());
		}
		
		return adjacentNodes;
	}
	
	
	/**
	 * This method will return the player links that have the node as source or destination based on the type
	 * @param graph g
	 * @param node node to which we want to get adjacent edges
	 * @param type can be either "source" or "destination" 
	 * 
	 * @return ArrayList<Edge>edges having the node as source (type="source") or destination (type ="destination")
	 */
	public static ArrayList<Edge> getAdjacentEdges (Graph g, Node node, String type)
	{
		ArrayList<Edge> adjacentEdges = new ArrayList<Edge>();
		
		for (int i=0; i<g.getEdgeList().length; i++)
		{
			if (type.equals ("source") && g.getEdgeList()[i].getSource().equals(node))
			{
				adjacentEdges.add( g.getEdgeList()[i]);
			}
			else if (type.equals ("destination") && g.getEdgeList()[i].getDestination().equals(node))
			{
				adjacentEdges.add( g.getEdgeList()[i]);
			}
		}
		
		return adjacentEdges;
	}
	
	
	/**
	 * This method will return the link between two nodes 
	 * It will return null if no link exists
	 * 
	 * @param source node1 of the link
	 * @param destination node2 of the link
	 * @param noOrder if true mean that we want to return the link between two nodes (source and destination)
	 * regardless which is the source and which is the destination
	 * @return link between the 2 nodes
	 */
	public static Edge getEdge(Graph g,Node source, Node destination, boolean noOrder)
	{

		Edge l = null;
		
		for (int i=0; i<g.getEdgeList().length; i++)
		{
			l = g.getEdgeList()[i];
			if (l.getSource().equals(source) && l.getDestination().equals(destination) )
			{
				return l;
			}
			
			if ( noOrder&& l.getSource().equals(destination) && l.getDestination().equals(source) )
			{
				return l;
			}
		}
		
		return null;
		
	}

	/**
	 * This method will search for a path having source and destination the nodes passed as parameter in a list of path
	 * it will check the path between 2 nodes (regardless if there are source and destination)
	 * 
	 * @param allPaths arraylist of path
	 * @param source node 1 
	 * @param destination node 2
	 * 
	 * @return path object between the 2 nodes, null if no path is found
	 */
	public static Path searchPath (ArrayList<Path> allPaths, Node source, Node destination)
	{
		Path p= null;
		
		for (int i=0; i<allPaths.size(); i++)
		{
			p=allPaths.get(i);
			if (p.getSource().equals(source) && p.getDestination().equals(destination))
			{
				return p;
			}
			else if (p.getSource().equals(destination) && p.getDestination().equals(source))
			{
				return p;
			}
		}
		return p;
	}
	

	
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		Network n = new Network(8,5,4,5,1000,1000,1,0);
		ArrayList<int[][]> physicalNetwork = n.buildPhysicalNetwork();
		physicalNetwork = n.buildPhysicalNetwork();
		int [] tf = physicalNetwork.get(2)[0];
	
		ServicesDriver sdriver = new ServicesDriver (3, 50, 3,5, 300,500, 5 , 1, 500, 1500,tf);//seed 33
		
		ArrayList<Service>services = sdriver.generateServices(50,10,100,12);
		
		Game g= new Game(n, services, StrategyType.LONGEST_PATH, 1.25);
		Player p = new Player(0, services.get(2), g);
		System.out.println(p);
			
		ArrayList<Edge> longestPath = p.getPgraph().getLongestPath(3);//p.getService().getDeadlineTime()
		
	System.out.println("LONGEST"+longestPath);
		
		/*for (int i=0; i<allPaths.size();i++)
		{
			System.out.println(allPaths.get(i));
		}
		boolean[] visited = new boolean [p.getPgraph().getNodeNb()];
		ArrayList<Node> tillNow = new ArrayList<Node>();
		PathDriver.mDFS(p.getPgraph(), p.getPgraph().getNodeList(), 0, visited, tillNow);*/
	}

}
