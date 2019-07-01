/**
 * This class represent physical links connecting physical machines
 */
package Network;

import java.io.Serializable;

public class Link implements Serializable{
	
	private int id;
	//this will hold the remaining bandwidth 
	private int bandwidth;
	private int capacity;
	
	private int source;
	private int destination;
	private int weight;
	
	public Link(int id, int bandwidth, int capacity, int source, int destination, int weight){
		this.id = id;
		this.bandwidth=bandwidth;
		this.capacity = capacity;
		this.source = source;
		this.destination = destination;
		this.weight = weight;
	}
	
	public Link clone()
	{
		return  new Link (this.id, this.bandwidth, this.capacity, this.source,this.destination, this.weight);
	}
	
	public int getId(){
		return this.id;
	}
	
	public int getBandwidth(){
		return this.bandwidth;
	}
	
	public int getCapacity(){
		return this.capacity;
	}
	
	public int getSource(){
		return this.source;
	}
	
	public int getDestination(){
		return this.destination;
	}
	
	public int getWeight(){
		return this.weight;
	}
	
	public void setId (int id){
		this.id = id;
	}
	
	public void setBandwidth(int bandwidth){
		this.bandwidth = bandwidth;
	}
	
	public void setCapacity(int capacity){
		this.capacity = capacity;
	}
	
	public void setSource(int source){
		this.source = source;
	}
	
	public void setDestination(int destination){
		this.destination = destination;
	}
	
	public void setWeight(int weight){
		 this.weight = weight;
	}
	
	public boolean equals(Link l)
	{
		return this.id == l.getId();
	}
	public String toString(){
		String s="";
		
		s+=String.format("Link id: %4d; Capacity: %4d; Bandwidth: %4d; Source id: %4d; Destination id: %4d Weight: %4d\n", this.getId(),this.getCapacity(), this.getBandwidth(), this.getSource(), this.getDestination(), this.getWeight());
		return s;
		
		
	}
}
