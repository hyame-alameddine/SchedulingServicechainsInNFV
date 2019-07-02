/**
 * This class performs the Dijkstra algorithm
 * http://www.vogella.com/tutorials/JavaAlgorithmsDijkstra/article.html
 * 
 * It requires:
 * Graph (Network class) with a list of edges and vertices
 * Edge (link class) with and id a source node (vertex/int) and destination node (vertex/int) and weight .The below code works for source and destination as ids
 * Vertex (PhysicalMachine class) node needs an equal() if edge has a source and destination as objects and may have weight
 * The implementation here considers a directed/undirected graph in addition to modifying the main algorithm to consider node weights
 * 
 * The code got from the website works when the graph is directed only. I modified it to work with directed and undirected graphs
 * 
 * The execute() function need to be called if the graph is a directed graph
 * The executeUndirected() function need to be called if the graph is undirected
 * 
 * IMPORTANT Fixed in execute() :
 * settledNodes = new LinkedHashSet<Node>();
 *          unSettledNodes = new LinkedHashSet<Node>();
 *          distance = new LinkedHashMap<Node, Integer>();
 *          predecessors = new LinkedHashMap<Node, Node>();
 *   It was using hashset and hashaMap that do not preserve the order causing to change the shortest path if there is multiple ones
 *
 */
package HelperClasses;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import GeneralClasses.Edge;
import GeneralClasses.Graph;
import GeneralClasses.Node;
import Network.Link;
import Network.Network;



public class DijkstraAlgorithm {

		private Graph graph; //added by Hyame
        private  List<Node> nodes;
        private  List<Edge> edges;
        private Set<Node> settledNodes;
        private Set<Node> unSettledNodes;
        private Map<Node, Node> predecessors;
        private Map<Node, Integer> distance;
        private boolean considerNodeWeight = false;//added by hyame
        
        public DijkstraAlgorithm(Graph graph, boolean considerNodeWeight) {
                // create a copy of the array so that we can operate on this array
        	this.graph = graph;//added by hyame
            this.nodes = new ArrayList<Node>(Arrays.asList(graph.getNodeList()));
            this.edges = new ArrayList<Edge>(Arrays.asList(graph.getEdgeList()));
            this.considerNodeWeight = considerNodeWeight;
     
    }

    /**
     * This function mainly works when the graph is a directed graph.
     * If you have an edge where the source is 0 and destination is 2
     * and you ask to get the path from 0 to 2, you will get the link
     * however, if you ask for the path from source 2 to 0 you will get null
     * 
     * @check execute(source, destination, is directed) to get shortest 
     * path with and undirected graph
     * @param source
     */
    public void execute(Node source) {
            settledNodes = new LinkedHashSet<Node>();
            unSettledNodes = new LinkedHashSet<Node>();
            distance = new LinkedHashMap<Node, Integer>();
            predecessors = new LinkedHashMap<Node, Node>();
            distance.put(source, 0);
            unSettledNodes.add(source);
            while (unSettledNodes.size() > 0) {
            	Node node = getMinimum(unSettledNodes);
            	
                    settledNodes.add(node);
                    unSettledNodes.remove(node);
                    findMinimalDistances(node);
            }
    }

    private void findMinimalDistances(Node node) {
            List<Node> adjacentNodes = getNeighbors(node);   
           
            for (Node target : adjacentNodes) {          
                    if (getShortestDistance(target) > getShortestDistance(node)//@TODO @hyame OR  add = to prevent changing strategy if 2 path have the same cost
                                    + getDistance(node, target)) {
                    	
                            distance.put(target, getShortestDistance(node)
                                            + getDistance(node, target));
                            predecessors.put(target, node);
                            unSettledNodes.add(target);
                    }
            }

    }

    private int getDistance(Node node, Node target) {
    	int d =0;
            for (Edge edge : edges) {
                    if (edge.getSource().getId() == node.getId() && edge.getDestination().getId() == target.getId()) {
                    	d	=	this.considerNodeWeight? node.getWeight()+edge.getWeight() :edge.getWeight();//modified by Hyame
                        return d;
                    }
                    else if(!this.graph.isDirected && edge.getSource().getId() == target.getId() //else if added by Hyame
                            && edge.getDestination().getId() == node.getId()){
                    		d	=	this.considerNodeWeight? node.getWeight()+edge.getWeight() :edge.getWeight();//modified by Hyame
                        return d;
                    }
            }
            throw new RuntimeException("Should not happen");
    }

    private List<Node> getNeighbors(Node node) {
            List<Node> neighbors = new ArrayList<Node>();
            Node edgeDestination =null;
            Node edgeSource =null;
            
            for (Edge edge : edges) {            
            	edgeDestination = this.nodes.get(edge.getDestination().getId());
            	edgeSource = this.nodes.get(edge.getSource().getId());//added by hyame
                    if (edge.getSource().getId() == node.getId()
                                    && !isSettled(edgeDestination)) {
                            neighbors.add(edgeDestination);
                    }
                    else if (!this.graph.isDirected && edge.getDestination().getId() == node.getId()//else if added by hyame
                            && !isSettled(edgeSource)){
                    	neighbors.add(edgeSource);
                    }
            }
            return neighbors;
    }

    private Node getMinimum(Set<Node> vertexes) {
    	Node minimum = null;
            for (Node vertex : vertexes) {
                    if (minimum == null) {
                            minimum = vertex;
                    } else {
                            if (getShortestDistance(vertex) < getShortestDistance(minimum)) {//@TODO @hyame either add = to prevent changing strategy if 2 path have the same cost
                                    minimum = vertex;
                            }
                    }
            }
            return minimum;
    }

    private boolean isSettled(Node vertex) {
            return settledNodes.contains(vertex);
    }

    private int getShortestDistance(Node destination) {
            Integer d = distance.get(destination);
            if (d == null) {
                    return Integer.MAX_VALUE;
            } else {
                    return d;
            }
    }

    /*
     * This method returns the path from the source to the selected target and
     * NULL if no path exists
     */
    public LinkedList<Node> getPath(Node target) {
            LinkedList<Node> path = new LinkedList<Node>();
            Node step = target;
            // check if a path exists
            if (predecessors.get(step) == null) {
                    return null;
            }
            path.add(step);
            while (predecessors.get(step) != null) {
                    step = predecessors.get(step);
                    path.add(step);
            }
            // Put it into the correct order
            Collections.reverse(path);
            return path;
    }
    
    
	/**
	 * @hyame
	 * This method will return the links on the path between the source and the destination
	 * 
	 * @param target destination node
	 * 
	 * @return ArrayList of links in the path
	 */
	    public ArrayList<Edge> getPathByLinks(Node target)
    {
    	ArrayList<Edge> linksPath = null; 
    	LinkedList<Node> path = this.getPath(target);
    	Edge l = null;
    	if (path == null)
    	{
    		linksPath = null;
    	}
    	else
    	{
    		linksPath = new ArrayList<Edge>();
    		for(int i=0; i<path.size()-1; i++)
    		{
    			l = graph.getEdge(path.get(i),path.get(i+1), false);
    			if (l == null && !this.graph.isDirected){
    				
    				l = graph.getEdge(path.get(i+1),path.get(i), false);
    			}
    			linksPath.add(l);
    		}
    	}
    	
    	return linksPath;
    }
    
    /**
     * @hyame
     * This function is added by me to get the shortest path between a source and a destination
     * This function will return 
     * @param source source of the path
     * @param destination destination of the path
     * If the graph is undirected, the code will return a path from source->destination or destination->source
     * 
     */
	public ArrayList<Edge> execute (Node source, Node destination)
	{
		ArrayList<Edge> path =  null;
		this.execute(source);
		path = this.getPathByLinks(this.nodes.get(destination.getId()));
		
		//if the graph is undirected and path is null, switch source and destination
		if (path == null && !this.graph.isDirected)
		{
			this.execute(destination);
			path = this.getPathByLinks(this.nodes.get(source.getId()));
		}		
		
		return path;
	}
	
	
	/**
	 * This function will return the shortest path between a source and a destination 
	 * by making sure that the links in the path has the required bandwidth 
	 * 
	 * @param source
	 * @param destination
	 * @param bandwidth
	 * @return array list of links in the path
	 */
	/*public ArrayList<Link> getShortestPathWithEnoughBandwidth (PhysicalMachine source, PhysicalMachine destination, int bandwidth)
	{
		ArrayList<Link> path =  null;
		boolean enoughBandwdith = false;
		ArrayList<Link> notEnoughBandwidthLinks ;
		
		while (!enoughBandwdith)
		{
			path = this.execute(source,destination);
			
			if (path == null)
			{
				return null;
			}
		
			//check if all the links has the required bandwidth. If so the code will terminate
			notEnoughBandwidthLinks = this.checkBandwidth(path, bandwidth);
			
			//set the enoughBandwdith to true if all the links has enough bandwidth to terminate the code
			enoughBandwdith = notEnoughBandwidthLinks.size()==0 ? true : false;
			
			/**
			 * remove the links which does not have enough bandwidth from the graph and get a new shortest path
			 * This is better than setting a big weight on the links because in this case the same path will be selected
			 * if no other path exists from source to destination
			 */
			/*if (!enoughBandwdith)
			{
				//create a copy of the network and update the current garph, nodes and edges by removing the links with no bandwidth
				 this.remove(notEnoughBandwidthLinks);
			}
		}
		
		return path;
	}*/
	
	/**
	 * This function will return the shortest path between a source and a destination 
	 * by making sure that the links in the path has the required bandwidth 
	 * 
	 * @param source
	 * @param destination
	 * @param bandwidth
	 * @return array list of links in the path
	 * @throws Exception 
	 */
	public ArrayList<Edge> getShortestPathWithEnoughBandwidth (Node source, Node destination, int bandwidth) throws Exception
	{
		ArrayList<Edge> path =  null;
	
		ArrayList<Edge> notEnoughBandwidthLinks = new ArrayList<Edge> ();
		
		for(int i=0; i<this.graph.getEdgeList().length; i++)
		{
			if (!(this.graph.getEdgeList()[i] instanceof Link))
			{
				throw new Exception("Trying to access bandwidth attribute on an object that does not have it");
			}
			if (((Link)this.graph.getEdgeList()[i]).getBandwidth()<bandwidth)
			{
				notEnoughBandwidthLinks.add(this.graph.getEdgeList()[i]);
			}
		}
		
		 this.remove(notEnoughBandwidthLinks);
		
		path = this.execute(source,destination);
		
		return path;
	}
	
	/**
	 * This method check if all the links in a path path has a bandwidth >= than the bandwidth passed
	 * as parameter. 
	 * 
	 * @param path array of all the links in a path to check
	 * @param bandwidth minimum bandwidth needed on the links in the path
	 * @return array of all the links which bandwidth<bandwidth passed as parameter
	 */
	public  ArrayList<Link> checkBandwidth (ArrayList<Link> path, int bandwidth)
	{
		ArrayList<Link> notEnoughBandwidthLinks = new ArrayList<Link> ();
		
		for(int i=0; i<path.size(); i++)
		{
			if (path.get(i).getBandwidth()<bandwidth)
			{
				notEnoughBandwidthLinks.add(path.get(i));
			}
		}
		
		return notEnoughBandwidthLinks;
	}
	
	/**
	 * This method will create a network which a copy of the current one, 
	 * remove from it all the links in the list passed as parameter
	 * 
	 * @param links links to remove from the network
	 * @return copy of the current network with an update link list
	 */
	public Graph remove (ArrayList<Edge> links)
	{
		boolean exist = false;
		Graph n = this.graph.clone();
		List <Edge> updatedLinkList = new ArrayList<Edge>();

		//if the link in the network is not in the list of links with no bandwidth, add it ti the new list
		for (int j=0; j<n.getEdgeList().length; j++)
		{
			 for (int i=0; i<links.size(); i++)
			 { 
				 //the links in the network exists with not enough bandwidth
				if (n.getEdgeList()[j].equals(links.get(i)))
				{
					exist = true;
				}
			}
			 
			 if (!exist)
			 {
				 updatedLinkList.add(n.getEdgeList()[j]);
			 }
			 
			 exist =false;
		 }
		 
		//convert the list to array and set the new link list of n to be equal to the array		
		 n.setEdgeList(updatedLinkList.toArray(new Edge [updatedLinkList.size()]));
			
		 this.graph = n;
		 this.nodes = new ArrayList<Node>(Arrays.asList(n.getNodeList()));
         this.edges = new ArrayList<Edge>(Arrays.asList(n.getEdgeList()));
    
		return n;
	}
	
	/**
	 * This method will return an array of shortest path between each 2 nodes in the network
	 * @return ArrayList<Path> shortestPath
	 * @throws Exception 
	 */
	public ArrayList<Path> buildShortestPaths () throws Exception
	{
		ArrayList<Path> shortestPath = new ArrayList<Path>();
		ArrayList<Edge> linksPath = null;
		int count =0;
		for (int i=0; i<this.graph.getNodeList().length; i++)
		{
			//do between node o-1 but not 1-0 because it will bet the same
			for (int j=i+1; j<this.graph.getNodeList().length; j++)
			{
				linksPath = this.execute(this.graph.getNodeList()[i], this.graph.getNodeList()[j]);
				
				if (linksPath==null)
				{
					throw new Exception(" DijkstraAlgorith-buildShortestPaths(): No PATH FOUND between node "+this.graph.getNodeList()[i].getId() +" and "+this.graph.getNodeList()[j].getId());
				}
				
				//add the path to the array
				shortestPath.add(new Path(count, this.graph.getNodeList()[i],this.graph.getNodeList()[j], linksPath));
				count++;
				linksPath = null;
			}
		}
		
		return shortestPath;
		
	}
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		   Network  graph = new Network(4,4,4,5,200,200,1,0);
		   graph.buildPhysicalNetwork();		
		   graph.getEdgeList()[1].setWeight(1000);
		   graph.getEdgeList()[4].setWeight(200);
		  System.out.println(graph);
		 //  graph.isDirected = true;
		   /**
		    * TESTING USING the predefined function got from the website
		    */
		   System.out.println("TESTING USING the predefined function got from the website");
           DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph,false);
           dijkstra.execute(graph.getNodeList()[1]);
           LinkedList<Node> path = dijkstra.getPath(graph.getNodeList()[3]);

           if (path == null)
           {
        	   System.out.println("no Path");
        	   
           }
           else
           {   
	           for (Node vertex : path) {
	                   System.out.println(vertex);
	           }
           }
           
           /**
		    * TESTING USING the additional execute function added
		    */
           System.out.println("TESTING USING the additional execute function added");
		
           DijkstraAlgorithm dijkstra2 = new DijkstraAlgorithm(graph,false);
        
           ArrayList<Edge> pathLinks = dijkstra2.execute(graph.getNodeList()[1],graph.getNodeList()[3]);
      
          // ArrayList<Edge> pathLinks = dijkstra2.getShortestPathWithEnoughBandwidth(graph.getNodeList()[2],graph.getNodeList()[0],300);     
           if (pathLinks == null)
           {
        	   System.out.println("no Path");
        	  
           }
           else
           {
	           for (Edge l : pathLinks) {
	                   System.out.println((Link)l);
	           }
           }
           
           System.out.println("TESTING directed graph for SFC");
           int [] nodeWeight = {0,2,3,3,2,6,3,0};
           Node [] nodeList = new Node [8];
           Edge [] edgeList = new Edge [10];
           Graph g = new Graph(0, 8, 10);
           for (int i=0; i<8; i++)
           {
        	   Node n = new Node(i,nodeWeight[i]);
        	   nodeList[i] =n;
           }
          
           g.setNodeList(nodeList);
           
           Edge e0 = new Edge(0,0, nodeList[0], nodeList[1]);
           edgeList[0] = e0;
           Edge e1= new Edge(1, 0,nodeList[0], nodeList[2]);
           edgeList[1] = e1;
           Edge e2 = new Edge(2,1, nodeList[1], nodeList[3]);
           edgeList[2] = e2;
           Edge e3= new Edge(3, 4,nodeList[3], nodeList[5]);
           edgeList[3] = e3;
           Edge e4 = new Edge(4,6, nodeList[1], nodeList[4]);
           edgeList[4] = e4;
           Edge e5= new Edge(5, 4,nodeList[2], nodeList[4]);
           edgeList[5] = e5;
           Edge e6 = new Edge(6,3, nodeList[4], nodeList[6]);
           edgeList[6] = e6;
           Edge e7= new Edge(7, 5,nodeList[2], nodeList[3]);
           edgeList[7] = e7;
           Edge e8 = new Edge(8,0, nodeList[5], nodeList[7]);
           edgeList[8] = e8;
           Edge e9= new Edge(9, 0,nodeList[6], nodeList[7]);
           edgeList[9] = e9;
           
           g.setEdgeList(edgeList);
           g.isDirected = true;
           System.out.println(g);
           DijkstraAlgorithm dijkstra3 = new DijkstraAlgorithm(g, true);

           ArrayList<Edge> paths = dijkstra3.execute(g.getNodeList()[0],g.getNodeList()[7]);
           System.out.println(dijkstra3.distance);
           System.out.println("Total cost from source 0 to destination 7 is "+dijkstra3.distance.get(g.getNodeList()[7]));
           if (paths== null)
           {
        	   System.out.println("no Path");
        	  
           }
           else
           {
	           for (Edge l : paths) {
	                   System.out.println(l);
	           }
           }
           
	}

}
