/**
 * This class represent physical links connecting physical machines
 */
package Network;

import java.io.Serializable;

import GeneralClasses.Edge;

public class Link extends Edge implements Serializable{
	
	//this will hold the remaining bandwidth 
	private int bandwidth;
	private int capacity;
	
	public Link(int id, int bandwidth, int capacity, PhysicalMachine source, PhysicalMachine destination, int weight){
		super (id, weight,source,destination);
		this.bandwidth=bandwidth;
		this.capacity = capacity;		
	}
	
	public Link clone()
	{
		return  new Link (this.id, this.bandwidth, this.capacity, (PhysicalMachine)this.source,(PhysicalMachine)this.destination, this.weight);
	}
	
	public int getBandwidth(){
		return this.bandwidth;
	}
	
	public int getCapacity(){
		return this.capacity;
	}

	
	public void setBandwidth(int bandwidth){
		this.bandwidth = bandwidth;
	}
	
	public void setCapacity(int capacity){
		this.capacity = capacity;
	}

	public String toString(){
		String s="";
		
		s+=String.format("Link id: %4d; Capacity: %4d; Bandwidth: %4d; Source id: %4d; Destination id: %4d Weight: %4d\n", this.getId(),this.getCapacity(), this.getBandwidth(), this.getSource().getId(), this.getDestination().getId(), this.getWeight());
		return s;
		
		
	}
}
