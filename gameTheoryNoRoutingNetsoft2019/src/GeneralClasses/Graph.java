/**
 * This is a general graph class that can create a connected graph given the number of nodes and links
 */
package GeneralClasses;

import java.util.ArrayList;

import HelperClasses.StdRandom;


public class Graph  {
	protected int id;
	protected Node [] nodeList;
	protected Edge [] edgeList;
	protected int nodeNb;//ks, 
	protected int edgeNb;//L,
	
	// this class creates an undirected graph (if ij =1 =>ji =0). The below attributes is only used for Diskjtra algorithm
	public  boolean isDirected = false;
	
	/**
	 * This will hold the physical network information as arrays
	 * 	1- G array specifying the links between servers in the network
	 *  2- cij array specifying the capacity of links in the network (capacity is randomly generated between minCapacity and maxCapacity) 
	 *  3- tf array specifying types on VNF
	 *  4- xfk array specifying on which server each VNF is hosted
	 */
	public ArrayList<int[][]> physicalNetworkArray;
	
	public Graph(){
		//prevent a null pointer exception when printing
		this.nodeList = new Node[0];
		this.edgeList = new Edge[0];
	}

	/**
	 * 
	 * @param id
	 * @param nodeNb
	 * @param edgeNb
	 */
	public Graph (int id, int nodeNb,int edgeNb){		
		this.id = id;
		this.nodeNb = nodeNb;
		this.edgeNb = edgeNb;
			
		this.nodeList = new Node[this.nodeNb];
		this.edgeList = new Edge[this.edgeNb];
		
		this.physicalNetworkArray = new ArrayList<int[][]>();
	}
	
	public int getId(){
		return this.id;
	}
	
	public int getNodeNb(){
		return this.nodeNb;
	}
	
	public int getEdgeNb(){
		return this.edgeNb;
	}
	
	public Node[] getNodeList(){
		return this.nodeList;
	}
	
	public Edge[] getEdgeList(){
		return this.edgeList;
	}
	
	public int setId(int id){
		return this.id=id;
	}
	
	public void setNodeNb(int nodeNb){
		this.nodeNb = nodeNb;
	}
	
	public void setEdgeNb(int edgeNb){
		this.edgeNb = edgeNb;
	}
	
	public void setNodeList(Node [] nodeList){
		this.nodeList = nodeList;
		this.setNodeNb(this.nodeList.length);
	}
	
	public void setEdgeList(Edge [] edgeList){
		this.edgeList = edgeList;
		this.setEdgeNb(this.edgeList.length);
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
	public Edge getEdge(Node source, Node destination, boolean noOrder)
	{

		Edge l = null;
		
		for (int i=0; i<this.getEdgeList().length; i++)
		{
			l = this.getEdgeList()[i];
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
	
	
	
	public Graph clone ()
	{
		Graph n = new Graph();
		n.nodeNb = this.nodeNb;
		n.edgeNb = this.edgeNb;
		n.nodeList = new Node[this.nodeNb];
		n.edgeList = new Edge[this.edgeNb];
		
		for(int i=0; i<this.nodeList.length; i++)
		{
			n.nodeList[i] = this.nodeList[i].clone();
		}
		
		for(int i=0; i<this.edgeList.length; i++)
		{
			n.edgeList[i] = this.edgeList[i].clone();
		}
		
		
		return n;
	}
	
	public String toString(){
		String s="";
		s = String.format("Graph:\n");
		s = String.format("ID:%4d\n", this.id);
		s = String.format("is directed :%4s\n", this.isDirected?"true":"false");
		s+=String.format("Node Nb:%4d \n", this.getNodeNb());
		s+=String.format("Edge Nb: %4d \n", this.getEdgeNb());
	
		s+=String.format("Nodes \n");
		for(int i=0; i<this.getNodeList().length; i++){
			s+=String.format("\n");
			s+=this.getNodeList()[i].toString();
		}
		s+=String.format("\nEdges \n");
		for(int i=0; i<this.getEdgeList().length; i++){
					
			s+=this.getEdgeList()[i].toString();
		}
		
		return s;
	}
	
	/**
     * Returns a random simple graph containing <tt>Ks</tt> vertices and <tt>L</tt> edges.
     * http://algs4.cs.princeton.edu/41graph/GraphGenerator.java.html  --> simple()
     * 
     * @param ks the number of vertices
     * @param L the number of edges
     * @return a random simple graph on <tt>Ks</tt> vertices, containing a total
     *     of <tt>L</tt> edges
     * @throws IllegalArgumentException if no such simple graph exists
     */
	public static int[][] generateRandomNetwork (int ks, int L)
	{
		StdRandom.setSeed(22);
		//the random connected network. This array will specify if a link exists between 2 nodes
		int[][] G = new int[ks][ks];
		int e = 0;
		int v=0; 
		int w =0;
		int node =0;
	    if (L >  ks*(ks-1)/2) 
        	throw new IllegalArgumentException("Too many edges");
        if (L < 0 || L<ks)       
        	throw new IllegalArgumentException("Too few edges");
        
        while (e < L) {
        	//make sure the graph is connected by first adding one link from each node and then choosing random nodes for the remaining links
        	if (e<ks)
        	{    
        		v = node;        	
        	}
        	else
        	{
            	v = StdRandom.uniform(ks);
        	}
             w = StdRandom.uniform(ks);
        
             //make sure to not add a link between 2-3 and 3-2 or 2-2
            if ((v != w) && G[v][w] == 0 && G[w][v] ==0) 
            {
            	G[v][w] = 1; 
          
            	//update the number of edges
            	e++;
            	//update the node here to make sure that a link is added to it
            	node++;
            }
            
        }
	    
        return G;	   
	}
	
	
	/**
	 * This method will update the graph node weights based on the array passed as parameter
	 * if the array was null, it will set the weights of all nodes to 0
	 * 
	 * @param nodeWeights list of new weights of nodes
	 */
	public void updateNodeWeights(int [] nodeWeights)
	{
		for (int i=0; i<this.nodeList.length; i++)
		{
			if (nodeWeights == null)
			{
				this.nodeList[i].setWeight(0);
			}
			else
			{
				this.nodeList[i].setWeight(nodeWeights[i]);
			}
		}
		
	}
	
	
	/**
	 * This method will update the graph node weights based on the array passed as parameter
	 * if the array was null, it will set the weights of all edges to 0
	 * 
	 * @param edgeWeights list of new weights of edges
	 */
	public void updateEdgeWeights(int [] edgeWeights)
	{
		for (int i=0; i<this.edgeList.length; i++)
		{
			if (edgeWeights == null)
			{
				this.edgeList[i].setWeight(0);
			}
			else
			{
				this.edgeList[i].setWeight(edgeWeights[i]);
			}
		}
		
	}
}
