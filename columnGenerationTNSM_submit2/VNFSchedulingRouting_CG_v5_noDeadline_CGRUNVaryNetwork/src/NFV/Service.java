package NFV;

import java.io.Serializable;
import java.util.ArrayList;

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
}
