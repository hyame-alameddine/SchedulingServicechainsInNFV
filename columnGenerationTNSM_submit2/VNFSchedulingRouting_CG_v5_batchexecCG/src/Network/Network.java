/**
 * This class will generate a random network of connected node and links
 * with randomly mapped VNFs of random type 
 */
package Network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import HelperClasses.Search;
import HelperClasses.StdRandom;

public class Network implements Serializable{
	private int id;
	private static int NETWORK_ID =0;
	private int VNFNb; //F,
	private int VNFTypesNb;//T,
	private int pmNb;//ks, 
	private int linksNb;//L, 
	private int pmNbWithoutVNFs;//this specified the number of servers on which we do not want to host any VNFs. This is included in the pmNb
	
	//min link capacity of a link in the network IMPORTANT: if minCapacity== -1 set all the links capacity to a fixed value = max link capacity in buildPhysicalNetwork()
	private int minLinkCapacity; 
	private int maxLinkCapacity;
	private int linkWeight; // this is used for diskjtra algorithm - set weight to 1 by default
	
	private PhysicalMachine [] pmList;
	private Link [] linkList;
	
	// this class creates an undirected graph (if ij =1 =>ji =0). The below attributes is only used for Diskjtra algorithm
	public final boolean isDirected = false;
	/**
	 * This will hold the physical network information as arrays
	 * 	1- G array specifying the links between servers in the network
	 *  2- cij array specifying the capacity of links in the network (capacity is randomly generated between minCapacity and maxCapacity) 
	 *  3- tf array specifying types on VNF
	 *  4- xfk array specifying on which server each VNF is hosted
	 */
	private ArrayList<int[][]> physicalNetworkArray;
	
	public Network(){
		//prevent a null pointer exception when printing
		this.pmList = new PhysicalMachine[0];
		this.linkList = new Link[0];
	}
	
	/**
	 * 
	 * @param VNFNb nb of VNFs to deploy in the network  should be equal to VNFTypes Nb when mapping a service
	 * @param VNFTypesNb nb of VNf types to consider
	 * @param pmNb nb of servers (node) in the network
	 * @param linksNb nb of links in the network
	 * @param minLinkCapacity min capacity of links
	 * @param maxLinkCapacity max capcity of links
	 */
	public Network (int VNFNb, int VNFTypesNb, int pmNb,int linksNb, int minLinkCapacity, int maxLinkCapacity, int linkWeight, int pmNbWithoutVNFs ){		
		this.id = Network.NETWORK_ID;
		Network.NETWORK_ID++;
		this.VNFNb = VNFNb;
		this.VNFTypesNb = VNFTypesNb;
		this.pmNb = pmNb;
		this.linksNb = linksNb;
		this.minLinkCapacity = minLinkCapacity;
		this.maxLinkCapacity = maxLinkCapacity;		
		this.linkWeight = linkWeight;
		this.pmNbWithoutVNFs = pmNbWithoutVNFs;
		
		this.pmList = new PhysicalMachine[this.pmNb];
		this.linkList = new Link[this.linksNb];
		
		this.physicalNetworkArray = new ArrayList<int[][]>();
	}
	
	public Network clone ()
	{
		Network n = new Network();
		n.VNFNb = this.VNFNb;
		n.VNFTypesNb = this.VNFTypesNb;
		n.pmNb = this.pmNb;
		n.linksNb = this.linksNb;
		n.minLinkCapacity = this.minLinkCapacity;
		n.maxLinkCapacity = this.maxLinkCapacity;		
		n.linkWeight = this.linkWeight;
		n.pmList = new PhysicalMachine[this.pmNb];
		n.linkList = new Link[this.linksNb];
		
		for(int i=0; i<this.pmList.length; i++)
		{
			n.pmList[i] = this.pmList[i].clone();
		}
		
		for(int i=0; i<this.linkList.length; i++)
		{
			n.linkList[i] = this.linkList[i].clone();
		}
		
		
		return n;
	}
	
	public int getId()
	{
		return this.id;
	}
	public int getVNFNb(){
		return this.VNFNb;
	}
	
	public int getVNFTypesNb(){
		return this.VNFTypesNb;
	}
	
	public int getPmNb(){
		return this.pmNb;
	}
	
	public int getLinksNb(){
		return this.linksNb;
	}
	
	public int getMinLinkCapacity(){
		return this.minLinkCapacity;
	}
	
	public int getMaxLinkCapacity(){
		return this.maxLinkCapacity;
	}
	
	public ArrayList<int[][]> getPhysicalNetworkArray()
	{
		return this.physicalNetworkArray;
	}
	
	public PhysicalMachine[] getPmList(){
		return this.pmList;
	}
	
	public Link[] getLinkList(){
		return this.linkList;
	}
	
	public int getLinkWeight()
	{
		return this.linkWeight;
	}
	
	public void setVNFNb(int VNFNb){
		this.VNFNb = VNFNb;
	}
	
	public void setVNFTypesNb(int VNFTypesNb){
		this.VNFTypesNb = VNFTypesNb;
	}
	
	public void setPmNb(int pmNb){
		this.pmNb = pmNb;
	}
	
	public void setLinksNb(int linksNb){
		this.linksNb = linksNb;
	}
	
	public void setMinLinkCapacity(int minLinkCapacity){
		this.minLinkCapacity = minLinkCapacity;
	}
	
	public void setMaxLinkCapacity (int maxLinkCapacity){
		this.maxLinkCapacity = maxLinkCapacity;
	}
	
	public void setPmList(PhysicalMachine [] pmList){
		this.pmList = pmList;
		
	}
	
	public void setLinkList(Link [] linkList){
		this.linkList = linkList;
		this.setLinksNb(linkList.length);
	}
	
	public void setLinkWeight(int weight){
		this.linkWeight = weight;
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
	 * This function will generate the physical network with hosted VNf with at least one ingress and one egress 
	 * it considers  0 = type of ingress and 1 is type of egress
	 * This function is used as input for the models
	 * 
	 * Note: minCapacity min capacity of a link in the network IMPORTANT: if minCapacity== -1 set all the links capacity to a fixed value = max capacity
	 * 
	 * @return and array list of:
	 * 	1- G array specifying the links between servers in the network
	 *  2- cij array specifying the capacity of links in the network (capacity is randomly generated between minCapacity and maxCapacity) 
	 *  3- tf array specifying types on VNF
	 *  4- xfk array specifying on which server each VNF is hosted
	 */
	public ArrayList<int[][]> buildPhysicalNetwork ()
	{
		Random rand = new Random();
		rand.setSeed(33);//42
		
		//the random connected network. This array will specify if a link exists between 2 nodes
		int[][] G = new int[this.pmNb][this.pmNb];
		
		//set of types on VNFs. This is formed as double array to be able to return it 
		int [][] tf = new int [1][this.VNFNb];//hhvnftype
		
		//specifies capacity of physical links interconnecting the servers 
		int [][] cij = new int [this.pmNb][this.pmNb];
				
		//specifies which VNF is hosted on which server
		int [][] xfk = new int[this.VNFNb][this.pmNb];
		
		//list of servers not hosting any VNF 
		int [] pmWithoutVNFs = new int [this.pmNbWithoutVNFs];
		
		int u =0;
		int p =0;
		int linkId =0;
		VNF vnf;
		//generate random connected network/graph
		G = generateRandomNetwork(this.pmNb,this.linksNb);
		
		//create the physical servers of the network
		for (int i=0; i<this.getPmNb(); i++){
			PhysicalMachine pm = new PhysicalMachine(i);
			this.getPmList()[i] = pm;
		}
		
		//decide on the servers which will not be hosting any VNfs
		if (this.pmNbWithoutVNFs!=0)
		{
			for (int i=0;i<this.pmNbWithoutVNFs; i++)
			{
				p = rand.nextInt(this.pmNb);
				//this is needed to make sure that pmNbWithoutVNFs different servers were selected
				while(Search.exists(p, pmWithoutVNFs))
				{
					p = rand.nextInt(this.pmNb);
				}
				//choose any server from the list of servers to not host any VNfs
				pmWithoutVNFs[i] = p;
			}
		}
		
		//set the capacity of links randomly between minCapacity and maxCapacity
		for (int i=0; i<cij.length; i++)
		{
			for (int j =0; j<cij[i].length; j++)
			{
				//check if there exists a link between 2 nodes
				if (G[i][j] == 1 )
				{
					//if minCapacity== -1 set all the links capacity to a fixed value = max capacity
					cij[i][j] =  this.minLinkCapacity == -1? this.maxLinkCapacity: rand.nextInt((this.maxLinkCapacity - this.minLinkCapacity) + 1) + this.minLinkCapacity;
										
					//create a link and add it to the network. bandwidth = capacity initially
					Link l= new Link (linkId,cij[i][j],cij[i][j],this.getPmList()[i].getId(), this.getPmList()[j].getId(),this.linkWeight );
					this.getLinkList()[linkId] = l;
					linkId++;
				}
				else
				{
					cij[i][j] = 0;
				}
			}
		}
	
		//set the types of VNF in the network to be between 2 and T rand.nextInt((max - min) + 1) + min; (min and max inclusive)
		tf[0][0] = 0;// set at least one ingress node
		tf[0][1] = 1;// set at least one egress node
		for (int i =2; i<tf[0].length; i++)
		{			
			tf[0][i] = rand.nextInt(((this.VNFTypesNb-1)-2)+1)+2;//prevent selecting 0 or 1 since we already set those types
		}
		
		
		//populate xfk - loop through VNF and decide on the pm hosting them
		for (int i=0; i<xfk.length; i++)
		{
			//choose a random server to host the VNF i
			u = rand.nextInt(this.pmNb);
			
			//make sure not to host a VNF on a server who should not be hosting any
			while (Search.exists(u, pmWithoutVNFs))
			{
				//choose a random server to host the VNF i
				u = rand.nextInt(this.pmNb);
			}
			
			 xfk[i][u] =1;	
			 
			 //create a VNF of a random type from the list of available types, make sure to have at least one VNF of type 1 and one of type 0(ingress and egress) (this is ensured by i=0 and i=1)
			 //VNF ids are continious independant from server id
			  vnf = new VNF(i, tf[0][i], u);
			
			 
			 //add vnf to the list of VNFs of the server which is hosting it
			 this.getPmList()[u].getHostedVNFs().add(vnf);
		}
		
		
		this.physicalNetworkArray.add(G);
		this.physicalNetworkArray.add(cij);
		this.physicalNetworkArray.add(tf);
		this.physicalNetworkArray.add(xfk);
		
		
		G = null;
		cij =null;
		tf =null;
		xfk = null;
		
		return this.physicalNetworkArray;
	}
	
	
	/**
	 * Gets and Returns an array list of VNFs of specified type
	 * 
	 * @param type type of VNF you are looking for
	 * @return an array list of VNFs of specified type
	 */
	public ArrayList<VNF> getVNFOfSpecifiedType(int type){
		ArrayList<VNF> vnfs = new ArrayList<VNF>();
		VNF v;
		
		for(int i=0; i<this.pmList.length; i++)
		{
			for (int j=0; j<this.pmList[i].getHostedVNFs().size(); j++)
			{
				v = this.pmList[i].getHostedVNFs().get(j);
				if(v.getType() == type)
				{
					vnfs.add(v);
				}
			}
		}
		
		return vnfs;
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
	public Link getLink(int source, int destination, boolean noOrder)
	{

		Link l = null;
		
		for (int i=0; i<this.getLinkList().length; i++)
		{
			l = this.getLinkList()[i];
			if ((l.getSource() == source && l.getDestination() == destination) )
			{
				return l;
			}
			
			if ( noOrder&& (l.getSource() == destination && l.getDestination() == source) )
			{
				return l;
			}
		}
		
		return null;
		
	}
	
	/**
	 * This function returns an array of the capacities of all the links in the network
	 * 
	 * @return array of link capacities
	 */
	public int [] getLinksCapacities()
	{
		int [] capacities = new int[this.linkList.length];
		
		for (int i=0; i<this.linkList.length; i++)
		{
			capacities[i] = this.linkList[i].getCapacity();
		}
		return capacities;
	}
	
	public String toString(){
		String s="";
		s = String.format("Network:\n");
		s = String.format("ID:%4d\n", this.id);
		s+=String.format("Physical Machine Nb:%4d \n", this.getPmNb());
		s+=String.format("Link Nb: %4d \n", this.getLinksNb());
		s+=String.format("VNF type Nb: %4d \n", this.getVNFTypesNb());
		s+=String.format("VNF Nb: %4d \n", this.getVNFNb());
		s+=String.format("Min link Capacity: %4d \n", this.getMinLinkCapacity());
		s+=String.format("Max link Capacity: %4d \n", this.getMaxLinkCapacity());
		s+=String.format("Physical Machines \n");
		for(int i=0; i<this.getPmList().length; i++){
			s+=String.format("\t");
			s+=this.getPmList()[i];
		}
		s+=String.format("Links \n");
		for(int i=0; i<this.getLinkList().length; i++){
			s+=String.format("\t");
			s+=this.getLinkList()[i];
		}
		
		return s;
	}
	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int ks = 8;//10;
		int L = 7;//14;
		int F =15;
		int T = 10;
		int S = 3;//15;
		int DELTA = 100;
		int minCapacity = 50;
		int maxCapacity = 1000;
	
		Network pNetwork = new Network (F, T, ks, L, minCapacity, maxCapacity,1,3);
		ArrayList<int[][]> physicalNetwork = pNetwork.buildPhysicalNetwork();
		System.out.println(pNetwork.toString());
	}

}
