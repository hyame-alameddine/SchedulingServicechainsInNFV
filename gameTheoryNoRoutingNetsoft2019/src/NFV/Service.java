package NFV;

import java.io.Serializable;
import java.util.ArrayList;

import Network.Link;
import Network.Network;

public class Service implements Serializable {
	
	//service id
	private int id;
	
	//nb of middleboxes including ingress and egress
	private int N;
	
	//bandwidth required to be guaranteed between the middleboxes of the service
	private int bandwidth;

	//deadline time of the service
	private int deadlineTime;
	
	//traffic size of this service
	private int trafficSize;
	
	private int completionTime;
	
	//arrival time
	private double arrivalTime;
	
	//departure time
	private double departureTime;
	
	private boolean isAdmitted;
	
	/*
	 * the type allow us to specify if we want to consider the arrival or departure of the request
	 *  (used when sorting requests for poisson distribution)
	 */
	public enum ProcessType {ARRIVAL, DEPARTURE, NA};
	
	private ProcessType type;
	
	//list of middleboxes required in the correct order (service function chaining)
	private ArrayList<Middlebox> middleboxes;
	
	//virtual links connecting middleboxes
	private ArrayList<VLink> virtualLinks;
	
	
	public Service (int id, int N, int bandwidth, int deadlineTime, int trafficSize)
	{
		this.id = id;
		this.N = N;
		this.bandwidth = bandwidth;
		this.deadlineTime = deadlineTime;
		this.trafficSize = trafficSize;
		this.middleboxes = new ArrayList<Middlebox>();
		this.virtualLinks = new ArrayList<VLink>();
		this.isAdmitted = false;
	}
	
	/**
	 * This function will clone a service in addition to its id
	 * All object attributes are referenced and not copied
	 * 
	 * @return cloned service
	 */
	public Service clone()
	{
		Service s = new Service(this.id, this.N, this.bandwidth, this.deadlineTime, this.trafficSize);
		s.arrivalTime = this.arrivalTime;
		s.departureTime = departureTime;
		s.type = this.type;
		
		for (int i=0; i<this.middleboxes.size(); i++)
		{
			s.middleboxes.add(this.middleboxes.get(i));
		}
		
		for (int i=0; i<this.virtualLinks.size(); i++)
		{
			s.virtualLinks.add(this.virtualLinks.get(i));
		}
		return s;
	}
	
	/**
	 * This method will create a clone of the service and copy the mapped to VNf if needed - used for tabu
	 * @param copyMapping
	 * @param copyRouting needed in tabu in case there is many routes as shortest path
	 * @return new copied service
	 */
	public Service copy(boolean copyMapping,boolean copyRouting)
	{
		Middlebox m =null;
		Middlebox b;
		VLink l = null;
		VLink v; 
		Service s = new Service(this.id, this.N, this.bandwidth, this.deadlineTime, this.trafficSize);
		s.arrivalTime = this.arrivalTime;
		s.departureTime = departureTime;
		s.type = this.type;
		
		for (int i=0; i<this.middleboxes.size(); i++)
		{
			b= this.middleboxes.get(i);
			m = new Middlebox(b.getId(), s, b.getType(), b.getProcessingTime());//set the service to the new one
			
			if (copyMapping)
			{
				m.setMappedToVNF(b.getMappedToVNF());
			}
			s.middleboxes.add(m);
		}
		
		for (int i=0; i<this.virtualLinks.size(); i++)
		{
			v = this.virtualLinks.get(i);
			//set source and destination to the new created middleboxes of the copied service
			l= new VLink(v.getId(), v.getContiniousId(), this, s.middleboxes.get(i), s.middleboxes.get(i+1));
			if (copyRouting)
			{
				for (int j=0; j<v.getRoutedThrough().size(); j++)
				{
					l.getRoutedThrough().add(v.getRoutedThrough().get(j));
				}
			}
			s.virtualLinks.add(l);
		}
		return s;
	}
	/**
	 * This function returns the id of the service
	 * @return id
	 */
	public int getId ()
	{
		return this.id;
	}
	
	public int getN(){
		return this.N;
	}
	
	/**
	 * This function returns the bandwidth of the service
	 * @return bandwidth
	 */
	public int getBandwdith()
	{
		return this.bandwidth;
	}
	
	
	/**
	 * This function returns the deadline time of the service
	 * 
	 * @return deadline time
	 */
	public int getDeadlineTime()
	{
		return this.deadlineTime;
	}
	
	
	/**
	 * This function returns the traffic size of the service
	 * @return trafficSize
	 */
	public int getTrafficSize()
	{
		return this.trafficSize;
	}
	
	/**
	 * This function returns the completion time of the service
	 * @return completionTime
	 */
	public int getCompletionTime()
	{
		return this.completionTime;
	}
	
	
	/**
	 * This function returns the arrivalTime  of the service
	 * @return arrivalTime
	 */
	public double getArrivalTime()
	{
		return this.arrivalTime;
	}
	
	/**
	 * This function returns the departureTime  of the service
	 * @return departureTime
	 */
	public double getDepartureTime()
	{
		return this.departureTime;
	}
	
	/**
	 * This function returns the isAdmitted  of the service
	 * @return isAdmitted
	 */
	public boolean getIsAdmitted ()
	{
		return this.isAdmitted;
	}
	
	/**
	 * This function returns the type  of the service
	 * @return type
	 */
	public ProcessType getType()
	{
		return this.type;
	}
	
	/**
	 * This function returns the set of middleboxes
	 * @return middlebox
	 */
	public ArrayList<Middlebox> getMiddleboxes()
	{
		return this.middleboxes;
	}
	
	/**
	 * this function returns the set of virtual links
	 * @return virtual links
	 */
	public ArrayList<VLink> getVirtualLinks()
	{
		return this.virtualLinks;
	}
	
	/**
	 * This function sets the id
	 * @param id
	 */
	public void setId(int id)
	{
		this.id = id;
	}
	
	public void setN(int N){
		this.N = N;
	}
	
	/**
	 * This function sets the bandwidth
	 * @param bandwidth
	 */
	public void setBandwidth(int bandwidth)
	{
		this.bandwidth = bandwidth;
	}
	
	
	/**
	 * This function sets the deadline time
	 * @param deadlineTime
	 */
	public void setDeadlineTime (int deadlineTime)
	{
		this.deadlineTime = deadlineTime;
	}
	
	
	/**
	 * This function sets the traffic size
	 * @param trafficSize
	 */
	public void setTrafficSize(int trafficSize)
	{
		this.trafficSize = trafficSize;
	}
	
	/**
	 * Set the arrivalTime of the service
	 * @param arrivalTime
	 */
	public void setArrivalTime (double arrivalTime)
	{
		this.arrivalTime = arrivalTime;
	}
	
	/**
	 * Set the departureTime of the service
	 * @param departureTime
	 */
	public void setDepartureTime (double departureTime)
	{
		this.departureTime = departureTime;
	}
	
	/**
	 * Set the type of the service
	 * @param type
	 */
	public void setType (ProcessType type)
	{
		this.type = type;
	}
	
	/**
	 * set the isAdmitted attribute
	 * @param admitted
	 */
	public void setIsAdmitted (boolean admitted)
	{
		this.isAdmitted = admitted;
	}
	/**
	 * Set the completion time of the service
	 * @param completionTime
	 */
	public void setCompletionTime (int completionTime)
	{
		this.completionTime = completionTime;
	}
	
	/**
	 * This function sets the middleboxes
	 * @param middleboxes
	 */
	public void setMiddleboxes (ArrayList<Middlebox> middleboxes)
	{
		this.middleboxes = middleboxes;
	}
	
	
	/**
	 * This function sets the virtual links
	 * @param virtualLinks
	 */
	public void setVirtualLinks(ArrayList<VLink> virtualLinks)
	{
		this.virtualLinks = virtualLinks;
	}
	
	/**
	 * This function return a string with the service information
	 * @return string
	 */
	public String toString()
	{
		String s= "";
		
		s= String.format("Service Id: %4d;\n",  this.getId());
		
		s+= String.format("\t isAdmitted: %4s;\n",  this.getIsAdmitted()? "true" : "false" );
		s+= String.format("\t Middlebox Nb: %4d \n",this.getN());
		s+= String.format("\t Bandwidth: %4d \n",this.getBandwdith());
		s+=String.format("\t Deadline: %4d\n",this.getDeadlineTime());
		s+=String.format("\t Traffic size: %4d \n", this.getTrafficSize());
		s+=String.format("\t Completion Time: %4d\n",this.getCompletionTime());
		s+=String.format("\t Arrival Time: %4f\n",this.getArrivalTime());
		s+=String.format("\t Departure Time: %4f\n",this.getDepartureTime());
		s+=String.format("\t Type: "+this.getType()+"\n");
		s+=String.format("\t Middleboxes:\n");
		for(int i = 0; i<this.getMiddleboxes().size(); i++){
			s+="\t\t"+this.getMiddleboxes().get(i);
		}
		
		s+=String.format("\tVirtual Links:\n");
		for(int i = 0; i<this.getVirtualLinks().size(); i++){
			s+="\t\t"+this.getVirtualLinks().get(i);
		}
		
		
		return s;
	}
	
	
    /**
     * This is a helper method that checks if the service requests a VNf of a certain type
     * @param type type of VNf to check
     * 
     * @return true if the service is requesting a VNf of the specified type
     */
    public boolean requestsVNFOfType(int type)
    {
    	for (int i=0; i<this.middleboxes.size(); i++)
    	{
    		if (this.middleboxes.get(i).getType() == type)
    		{
    			return true;
    		}
    	}
    	return false;
    }
    
    /**
     * This method will release the resources assigned to the service and set admitted = false
     * 
     */
    public void releaseResources()
    {
    	for (int i=0; i<this.middleboxes.size(); i++)
    	{
    		this.middleboxes.get(i).setIsProcessing( new int[this.middleboxes.get(i).getProcessingTime()]);
    		this.middleboxes.get(i).setMappedToVNF(null);
    		this.middleboxes.get(i).setStartedProcessing(0);
    	}
    	
    	for (int i=0; i<this.virtualLinks.size(); i++)
    	{
    		this.virtualLinks.get(i).setStartedTransmition(0);
    		this.virtualLinks.get(i).setRoutedThrough(new ArrayList<Link>());
    		
    		//set the size of the isTransmitting array to the number of time slots needed for transmission (size of traffic/bandwidth)
    		this.virtualLinks.get(i).setIsTransmitting ( new int [(int)Math.ceil((double)this.getTrafficSize()/(double)this.getBandwdith())]);
    	}
    	this.setCompletionTime(0);
    	this.isAdmitted = false;
    }
    
    
    /**
     * This method will calculate the number of nodes and edges of the player virtual graph where a source and destination node will be added 
     * in addition to edges from theses sources/destination to VNF nodes
     * @param n network hosting vnfs
     * @return array of nb of nodes/nb of edges
     */
    public int[] calculateNbOfPlayerNodesEdges ( Network n)
    {
    	int nbOfNodes=1;//add a virtual source node
    	Middlebox m=null;
    	int vnfOfSpecifiedType =0;
    	int nodesOfPreviousMidllebox =1;//the virtual source node
    	
    	int nbOfEdges =0;
    	int []nodeEdge = new int[2];
    	for (int i=0; i<this.middleboxes.size(); i++)
    	{
    		m= this.middleboxes.get(i);
    		
    		//get all possible VNFs to which the middlebox can be mapped to
    		vnfOfSpecifiedType = n.getVNFOfSpecifiedType(m.getType()).size();
    		 
    		//update the nb of nodes to hold the number of instances of each VNF of the same type of the middlebox to create.
    		//if we had 2 VNF of middlebox 1 and 3 of middlebox 2, the we will have 2*3 VNFs of middlebox 2 (3 for each VNF of middlebox 1)
    		nbOfNodes+=nodesOfPreviousMidllebox*vnfOfSpecifiedType;
    		nodesOfPreviousMidllebox=nodesOfPreviousMidllebox*vnfOfSpecifiedType;
    		
    		//nb of edges = nb of nodes created -source node-destination node+nb of nodes of the last middlebox(this will be the nb of edges to destination)
    		if (i== this.middleboxes.size()-1)
    		{
    			nbOfEdges= nodesOfPreviousMidllebox;
    		}
    	}
    	
    	nbOfNodes+=1;//add a virtual destination node
    	nbOfEdges+=nbOfNodes-2;//remove source and destination node
    	
    	nodeEdge [0] = nbOfNodes;
    	nodeEdge [1] = nbOfEdges;
    	
    	return nodeEdge;
    	
    }
    
    /**
     * This method will return the virtual link that has the specified source and destination. 
     * If source or destination were set to null it will return the vlink that has the source or destination respectively
     * @param source source middlebox (can be null in that case will check for link with specified destination)
     * @param destination middlebox (can be null in that case will check for link with specified source)
     * 
     * @return vlink, null if not found
     */
    public VLink getVLink(Middlebox source, Middlebox destination)
    {
    	VLink l=null;
    	for(int i=0; i<this.getVirtualLinks().size(); i++)
    	{
    		l= this.getVirtualLinks().get(i);
    		if (source!=null && destination!=null)
    		{
    			if (l.getSource().equals(source) && l.getDestination().equals(destination))
    			{
    				return l;
    			}
    		}
    		else if(source!=null && l.getSource().equals(source))
    		{
    			return l;
    		}
    		else if(destination!=null && l.getSource().equals(destination))
    		{
    			return l;
    		}
    	}
    	
    	return null;
    }
    
    
    
    /**
     * This method will return a descending ordered array of middleboxes based on their time gap
     * time gap = started Processing of f - finished processing of f-1
     * @return middlebox with max flowtime
     */
    public Middlebox[] getMiddleboxesTimeGapOrdered()
    {
    	Middlebox m;
    	
    	Middlebox [] boxes = new Middlebox[this.middleboxes.size()];
    	    	    	
    	//start from 1 to be able to check i-1
    	for(int i=0; i<this.middleboxes.size(); i++)
    	{ 	
    		boxes[i] = this.middleboxes.get(i);
    	}

    	//order middleboxes
    	for (int i=0; i<boxes.length-1; i++)
    	{
    		for (int j=i+1; j<boxes.length; j++)
    		{   		      
                if (boxes[i].getTimeGap()< boxes[j].getTimeGap()) 
                {
                    
                    m = boxes[i];
                    boxes[i] = boxes[j];
                    boxes[j] = m;
                }
    		}         
    	}
    	return boxes;
    }
    
    /**
     * flow time is the difference between the completion time and the arrival time of the service
     * @param arrival can be the service arrival time or timeslot for periodic batch
     * @return flow time
     */
    public int getFlowTime(int arrival)
    {
    	return this.completionTime - arrival;
    }
}
