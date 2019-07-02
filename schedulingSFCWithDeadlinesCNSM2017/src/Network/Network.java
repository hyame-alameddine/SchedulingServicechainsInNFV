/**
 * This class will generate a random network of connected node and links
 * with randomly mapped VNFs of random type 
 */
package Network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Random;

import GeneralClasses.Graph;
import HelperClasses.Search;


public class Network extends Graph implements Serializable{
	private int id;
	private static int NETWORK_ID =0;
	private int VNFNb; //F,
	private int VNFTypesNb;//T,
	private int nodeNbWithoutVNFs;//this specified the number of servers on which we do not want to host any VNFs. This is included in the nodeNb
	
	//min link capacity of a link in the network IMPORTANT: if minCapacity== -1 set all the links capacity to a fixed value = max link capacity in buildPhysicalNetwork()
	private int minLinkCapacity; 
	private int maxLinkCapacity;
	private int linkWeight; // this is used for diskjtra algorithm - set weight to 1 by default

	/**
	 * This will hold the physical network information as arrays
	 * 	1- G array specifying the links between servers in the network
	 *  2- cij array specifying the capacity of links in the network (capacity is randomly generated between minCapacity and maxCapacity) 
	 *  3- tf array specifying types on VNF
	 *  4- xfk array specifying on which server each VNF is hosted
	 */
	public ArrayList<int[][]> physicalNetworkArray;
	
	public Network(){
		//prevent a null pointer exception when printing
		this.nodeList = new PhysicalMachine[0];
		this.edgeList = new Link[0];
		// this class creates an undirected graph (if ij =1 =>ji =0). The below attributes is only used for Diskjtra algorithm
		this.isDirected = false;
	}
	
	/**
	 * 
	 * @param VNFNb nb of VNFs to deploy in the network  should be equal to VNFTypes Nb when mapping a service
	 * @param VNFTypesNb nb of VNf types to consider
	 * @param nodeNb nb of servers (node) in the network
	 * @param edgeNb nb of links in the network
	 * @param minLinkCapacity min capacity of links
	 * @param maxLinkCapacity max capcity of links
	 */
	public Network (int VNFNb, int VNFTypesNb, int nodeNb,int edgeNb, int minLinkCapacity, int maxLinkCapacity, int linkWeight, int nodeNbWithoutVNFs ){		
		this.id = Network.NETWORK_ID;
		Network.NETWORK_ID++;
		this.VNFNb = VNFNb;
		this.VNFTypesNb = VNFTypesNb;
		this.nodeNb = nodeNb;
		this.edgeNb = edgeNb;
		this.minLinkCapacity = minLinkCapacity;
		this.maxLinkCapacity = maxLinkCapacity;		
		this.linkWeight = linkWeight;
		this.nodeNbWithoutVNFs = nodeNbWithoutVNFs;
		
		this.nodeList = new PhysicalMachine[this.nodeNb];
		this.edgeList = new Link[this.edgeNb];
		
		this.physicalNetworkArray = new ArrayList<int[][]>();
		// this class creates an undirected graph (if ij =1 =>ji =0). The below attributes is only used for Diskjtra algorithm
		this.isDirected = false;
	}
	
	public Network clone ()
	{
		Network n = new Network();
		n.VNFNb = this.VNFNb;
		n.VNFTypesNb = this.VNFTypesNb;
		n.nodeNb = this.nodeNb;
		n.edgeNb = this.edgeNb;
		n.minLinkCapacity = this.minLinkCapacity;
		n.maxLinkCapacity = this.maxLinkCapacity;		
		n.linkWeight = this.linkWeight;
		n.nodeList = new PhysicalMachine[this.nodeNb];
		n.edgeList = new Link[this.edgeNb];
		n.isDirected = this.isDirected;
		for(int i=0; i<this.nodeList.length; i++)
		{
			n.nodeList[i] = ((PhysicalMachine)this.nodeList[i]).clone();
		}
		
		for(int i=0; i<this.edgeList.length; i++)
		{
			n.edgeList[i] = ((Link)this.edgeList[i]).clone();
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
	
	public void setMinLinkCapacity(int minLinkCapacity){
		this.minLinkCapacity = minLinkCapacity;
	}
	
	public void setMaxLinkCapacity (int maxLinkCapacity){
		this.maxLinkCapacity = maxLinkCapacity;
	}
	
	
	public void setLinkWeight(int weight){
		this.linkWeight = weight;
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
		int[][] G = new int[this.nodeNb][this.nodeNb];
		
		//set of types on VNFs. This is formed as double array to be able to return it 
		int [][] tf = new int [1][this.VNFTypesNb];//hhvnftype
		
		//specifies capacity of physical links interconnecting the servers 
		int [][] cij = new int [this.nodeNb][this.nodeNb];
				
		//specifies which VNF is hosted on which server
		int [][] xfk = new int[this.VNFNb][this.nodeNb];
		
		//list of servers not hosting any VNF 
		int [] pmWithoutVNFs = new int [this.nodeNbWithoutVNFs];
		
		int u =0;
		int p =0;
		int linkId =0;

		int r =0;
		VNF vnf;
		//generate random connected network/graph
		G = generateRandomNetwork(this.nodeNb,this.edgeNb);
		
		//create the physical servers of the network
		for (int i=0; i<this.getNodeNb(); i++){
			PhysicalMachine pm = new PhysicalMachine(i);
			this.getNodeList()[i] = pm;
		}
		
		//decide on the servers which will not be hosting any VNfs
		if (this.nodeNbWithoutVNFs!=0)
		{
			for (int i=0;i<this.nodeNbWithoutVNFs; i++)
			{
				p = rand.nextInt(this.nodeNb);
				//this is needed to make sure that nodeNbWithoutVNFs different servers were selected
				while(Search.exists(p, pmWithoutVNFs))
				{
					p = rand.nextInt(this.nodeNb);
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
					Link l= new Link (linkId,cij[i][j],cij[i][j],(PhysicalMachine)this.getNodeList()[i], (PhysicalMachine)this.getNodeList()[j],this.linkWeight );
					this.getEdgeList()[linkId] = l;
					linkId++;
				}
				else
				{
					cij[i][j] = 0;
				}
			}
		}
	
		//set the types of VNF in the network 
		tf[0][0] = 0;// set at least one ingress node
		tf[0][1] = 1;// set at least one egress node
	
		for (int i =2; i<tf[0].length; i++)
		{	
			//ensure to have different types			
			tf[0][i] = i;
	
		}
		
		//populate xfk - loop through VNF and decide on the pm hosting them
		for (int i=0; i<xfk.length; i++)
		{
			//choose a random server to host the VNF i
			u = rand.nextInt(this.nodeNb);
			
			//make sure not to host a VNF on a server who should not be hosting any
			while (Search.exists(u, pmWithoutVNFs))
			{
				//choose a random server to host the VNF i
				u = rand.nextInt(this.nodeNb);
			}
			
			 xfk[i][u] =1;	
			 
			 //create a VNF of a random type from the list of available types, make sure to have at least one VNF of type 1 and one of type 0(ingress and egress) (this is ensured by i=0 and i=1)
			 //VNF ids are continious independant from server id
			 //if the number of vnfs>types choose one vnf of each type and the rest randomly
			  r= (i<tf[0].length)? tf[0][i] :tf[0][rand.nextInt(tf[0].length-1)]; 			
			 vnf = new VNF(i, r, u);
			 r=0;
			 
			 //add vnf to the list of VNFs of the server which is hosting it
			 ((PhysicalMachine)this.getNodeList()[u]).getHostedVNFs().add(vnf);
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
		
		for(int i=0; i<this.nodeList.length; i++)
		{
			for (int j=0; j<((PhysicalMachine)this.nodeList[i]).getHostedVNFs().size(); j++)
			{
				v = ((PhysicalMachine)this.nodeList[i]).getHostedVNFs().get(j);
				if(v.getType() == type)
				{
					vnfs.add(v);
				}
			}
		}
		
		return vnfs;
	}
	

	
	/**
	 * This function returns an array of the capacities of all the links in the network
	 * 
	 * @return array of link capacities
	 */
	public int [] getLinksCapacities()
	{
		int [] capacities = new int[this.edgeList.length];
		
		for (int i=0; i<this.edgeList.length; i++)
		{
			capacities[i] = ((Link)this.edgeList[i]).getCapacity();
		}
		return capacities;
	}
	
	public String toString(){
		String s="";
		s = String.format("Network:\n");
		s = String.format("ID:%4d\n", this.id);
		s+=String.format("Physical Machine Nb:%4d \n", this.getNodeNb());
		s+=String.format("Link Nb: %4d \n", this.getEdgeNb());
		s+=String.format("VNF type Nb: %4d \n", this.getVNFTypesNb());
		s+=String.format("VNF Nb: %4d \n", this.getVNFNb());
		s+=String.format("Min link Capacity: %4d \n", this.getMinLinkCapacity());
		s+=String.format("Max link Capacity: %4d \n", this.getMaxLinkCapacity());
		s+=String.format("Physical Machines \n");
		for(int i=0; i<this.getNodeList().length; i++){
			s+=String.format("\t");
			s+=this.getNodeList()[i];
		}
		s+=String.format("Links \n");
		for(int i=0; i<this.getEdgeList().length; i++){
			s+=String.format("\t");
			s+=this.getEdgeList()[i];
		}
		
		return s;
	}
	
	
	/**
	 * this method will create and array list for each link in the network. And for each it will
	 * create an array list for all the timeslot (size) and initialize them to the capacity of the link
	 * @param size size of the array for each link should represent the timeslot
	 * @return array of integer with capacities of each links
	 */
	public ArrayList<ArrayList<Integer>> getLinksTimeBw(int size)
	{
		ArrayList<ArrayList<Integer>> links = new ArrayList<ArrayList<Integer>>();
		for (int i=0; i<this.edgeList.length; i++)
		{
			links.add(new ArrayList<Integer>()) ;
			for (int j=0; j<size ;j++)
			{
				links.get(i).add(((Link)this.edgeList[i]).getCapacity());
			}
		}
		
		return links;
	}
	
	
	/**
	 * this method will create and array list for each vnf in the network. And for each it will
	 * create an array list for all the timeslot (size) and initialize them to -1 to specify that they are not used at the timeslot
	 * @param size size of the array for each vnf should represent the timeslot
	 * @return array of integer with -1 for each vnf
	 */
	public ArrayList<ArrayList<Integer>> getVnfsTime(int size)
	{
		ArrayList<ArrayList<Integer>> vnfs = new ArrayList<ArrayList<Integer>>();
		for (int i=0; i<this.getVNFNb(); i++)
		{
			vnfs.add(new ArrayList<Integer>()) ;
			for (int j=0; j<size ;j++)
			{
				vnfs.get(i).add(-1);
			}
		}
		
		return vnfs;
	}
	
	
	/**
	 * This method will copy the array passed as paramter
	 * 
	 * @param links array list for each link in the network. And for each it will contains an array list
	 *  for all the timeslots (size) with bw avaiable on each at each timeslot
	 * @return copy of the array passed as parameter
	 */
	public static ArrayList<ArrayList<Integer>> copy (ArrayList<ArrayList<Integer>> links)
	{
		ArrayList<ArrayList<Integer>> copy = new ArrayList<ArrayList<Integer>>();
		for (int i=0; i<links.size(); i++)
		{
			copy.add(new ArrayList<Integer>()) ;
			for (int j=0; j<links.get(0).size() ;j++)
			{
				copy.get(i).add(links.get(i).get(j));
			}
		}
		
		return copy;
	}
	
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		int ks = 8;//10;
		int L = 14;//14;
		int F =15;
		int T = 10;
		int S = 3;//15;
		int DELTA = 100;
		int minCapacity = 50;
		int maxCapacity = 1000;
	
		Network pNetwork = new Network (F, T, ks, L, minCapacity, maxCapacity,1,3);
		ArrayList<int[][]> physicalNetwork = pNetwork.buildPhysicalNetwork();
		System.out.println(pNetwork.toString());
		
		 pNetwork = new Network (20, T, ks, L, minCapacity, maxCapacity,1,3);
		 physicalNetwork = pNetwork.buildPhysicalNetwork();
		System.out.println(pNetwork.toString());
	}

}
