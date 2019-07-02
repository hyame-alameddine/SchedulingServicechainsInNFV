/**
 * This class performs the Dijkstra algorithm
 * http://www.vogella.com/tutorials/JavaAlgorithmsDijkstra/article.html
 * 
 * It requires:
 * Graph (Network class) with a list of edges and vertices
 * Edge (link class) with and id a source node (vertex/int) and destination node (vertex/int) and weight .The below code works for source and destination as ids
 * Vertex (PhysicalMachine class) node needs an equal() if edge has a source and destination as objects
 * 
 * The code got from the website works when the graph is directed only. I modified it to work with directed and undirected graphs
 * 
 * The execute() function need to be called if the graph is a directed graph
 * The executeUndirected() function need to be called if the graph is undirected
 * 
 */
package HelperClasses;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import Network.Link;
import Network.Network;
import Network.PhysicalMachine;


public class DijkstraAlgorithm {

		private Network graph; //added by Hyame
        private  List<PhysicalMachine> nodes;
        private  List<Link> edges;
        private Set<PhysicalMachine> settledNodes;
        private Set<PhysicalMachine> unSettledNodes;
        private Map<PhysicalMachine, PhysicalMachine> predecessors;
        private Map<PhysicalMachine, Integer> distance;
    
        
        public DijkstraAlgorithm(Network graph) {
                // create a copy of the array so that we can operate on this array
        	this.graph = graph;//added by hyame
            this.nodes = new ArrayList<PhysicalMachine>(Arrays.asList(graph.getPmList()));
            this.edges = new ArrayList<Link>(Arrays.asList(graph.getLinkList()));
     
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
    public void execute(PhysicalMachine source) {
            settledNodes = new HashSet<PhysicalMachine>();
            unSettledNodes = new HashSet<PhysicalMachine>();
            distance = new HashMap<PhysicalMachine, Integer>();
            predecessors = new HashMap<PhysicalMachine, PhysicalMachine>();
            distance.put(source, 0);
            unSettledNodes.add(source);
            while (unSettledNodes.size() > 0) {
            	PhysicalMachine node = getMinimum(unSettledNodes);
                    settledNodes.add(node);
                    unSettledNodes.remove(node);
                    findMinimalDistances(node);
            }
    }

    private void findMinimalDistances(PhysicalMachine node) {
            List<PhysicalMachine> adjacentNodes = getNeighbors(node);         
            for (PhysicalMachine target : adjacentNodes) {            
                    if (getShortestDistance(target) > getShortestDistance(node)
                                    + getDistance(node, target)) {
                            distance.put(target, getShortestDistance(node)
                                            + getDistance(node, target));
                            predecessors.put(target, node);
                            unSettledNodes.add(target);
                    }
            }

    }

    private int getDistance(PhysicalMachine node, PhysicalMachine target) {
            for (Link edge : edges) {
                    if (edge.getSource() == node.getId()
                                    && edge.getDestination() == target.getId()) {
                            return edge.getWeight();
                    }
                    else if(!this.graph.isDirected && edge.getSource() == target.getId() //else if added by Hyame
                            && edge.getDestination() == node.getId()){
                    	 return edge.getWeight();
                    }
            }
            throw new RuntimeException("Should not happen");
    }

    private List<PhysicalMachine> getNeighbors(PhysicalMachine node) {
            List<PhysicalMachine> neighbors = new ArrayList<PhysicalMachine>();
            PhysicalMachine edgeDestination =null;
            PhysicalMachine edgeSource =null;
            for (Link edge : edges) {            
            	edgeDestination = this.nodes.get(edge.getDestination());
            	edgeSource = this.nodes.get(edge.getSource());//added by hyame
                    if (edge.getSource() == node.getId()
                                    && !isSettled(edgeDestination)) {
                            neighbors.add(edgeDestination);
                    }
                    else if (!this.graph.isDirected && edge.getDestination() == node.getId()//else if added by hyame
                            && !isSettled(edgeSource)){
                    	neighbors.add(edgeSource);
                    }
            }
            return neighbors;
    }

    private PhysicalMachine getMinimum(Set<PhysicalMachine> vertexes) {
    	PhysicalMachine minimum = null;
            for (PhysicalMachine vertex : vertexes) {
                    if (minimum == null) {
                            minimum = vertex;
                    } else {
                            if (getShortestDistance(vertex) < getShortestDistance(minimum)) {
                                    minimum = vertex;
                            }
                    }
            }
            return minimum;
    }

    private boolean isSettled(PhysicalMachine vertex) {
            return settledNodes.contains(vertex);
    }

    private int getShortestDistance(PhysicalMachine destination) {
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
    public LinkedList<PhysicalMachine> getPath(PhysicalMachine target) {
            LinkedList<PhysicalMachine> path = new LinkedList<PhysicalMachine>();
            PhysicalMachine step = target;
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
	    public ArrayList<Link> getPathByLinks(PhysicalMachine target)
    {
    	ArrayList<Link> linksPath = null; 
    	LinkedList<PhysicalMachine> path = this.getPath(target);
    	 Link l = null;
    	if (path == null)
    	{
    		linksPath = null;
    	}
    	else
    	{
    		linksPath = new ArrayList<Link>();
    		for(int i=0; i<path.size()-1; i++)
    		{
    			l = graph.getLink(path.get(i).getId(),path.get(i+1).getId(), false);
    			if (l == null && !this.graph.isDirected){
    				
    				l = graph.getLink(path.get(i+1).getId(),path.get(i).getId(), false);
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
	public ArrayList<Link> execute (PhysicalMachine source, PhysicalMachine destination)
	{
		ArrayList<Link> path =  null;
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
	 */
	public ArrayList<Link> getShortestPathWithEnoughBandwidth (PhysicalMachine source, PhysicalMachine destination, int bandwidth)
	{
		ArrayList<Link> path =  null;
	
		ArrayList<Link> notEnoughBandwidthLinks = new ArrayList<Link> ();
		
		for(int i=0; i<this.graph.getLinkList().length; i++)
		{
			if (this.graph.getLinkList()[i].getBandwidth()<bandwidth)
			{
				notEnoughBandwidthLinks.add(this.graph.getLinkList()[i]);
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
	public Network remove (ArrayList<Link> links)
	{
		boolean exist = false;
		Network n = this.graph.clone();
		List <Link> updatedLinkList = new ArrayList<Link>();

		//if the link in the network is not in the list of links with no bandwidth, add it ti the new list
		for (int j=0; j<n.getLinkList().length; j++)
		{
			 for (int i=0; i<links.size(); i++)
			 { 
				 //the links in the network exists with not enough bandwidth
				if (n.getLinkList()[j].equals(links.get(i)))
				{
					exist = true;
				}
			}
			 
			 if (!exist)
			 {
				 updatedLinkList.add(n.getLinkList()[j]);
			 }
			 
			 exist =false;
		 }
		 
		//convert the list to array and set the new link list of n to be equal to the array		
		 n.setLinkList(updatedLinkList.toArray(new Link [updatedLinkList.size()]));
			
		 this.graph = n;
		 this.nodes = new ArrayList<PhysicalMachine>(Arrays.asList(n.getPmList()));
         this.edges = new ArrayList<Link>(Arrays.asList(n.getLinkList()));
    
		return n;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		   Network  graph = new Network(4,4,4,5,200,200,1,0);
		   graph.buildPhysicalNetwork();		
		  // graph.getLinkList()[1].setWeight(1000);
		   //graph.getLinkList()[2].setWeight(200);
		   System.out.println(graph);
		   /**
		    * TESTING USING the predefined function got from the website
		    */
		   System.out.println("TESTING USING the predefined function got from the website");
           DijkstraAlgorithm dijkstra = new DijkstraAlgorithm(graph);
           dijkstra.execute(graph.getPmList()[2]);
           LinkedList<PhysicalMachine> path = dijkstra.getPath(graph.getPmList()[0]);

           if (path == null)
           {
        	   System.out.println("no Path");
        	   
           }
           else
           {   
	           for (PhysicalMachine vertex : path) {
	                   System.out.println(vertex);
	           }
           }
           
           /**
		    * TESTING USING the additional execute function added
		    */
           System.out.println("TESTING USING the additional execute function added");
		
           DijkstraAlgorithm dijkstra2 = new DijkstraAlgorithm(graph);
        
          // ArrayList<Link> pathLinks = dijkstra2.execute(graph.getPmList()[2],graph.getPmList()[0]);
      
           ArrayList<Link> pathLinks = dijkstra2.getShortestPathWithEnoughBandwidth(graph.getPmList()[2],graph.getPmList()[0],300);     
           if (pathLinks == null)
           {
        	   System.out.println("no Path");
        	  
           }
           else
           {
	           for (Link l : pathLinks) {
	                   System.out.println(l);
	           }
           }
           
          
	}

}
