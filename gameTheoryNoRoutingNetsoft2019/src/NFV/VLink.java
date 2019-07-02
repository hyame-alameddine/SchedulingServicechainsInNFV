/**
 * This class represents the Virtual links od each service function chaining
 */
package NFV;

import java.io.Serializable;
import java.util.ArrayList;

import Network.Link;

public class VLink implements Serializable{
	private int id;
	//this is the id based on all the links for all the services (as in the model (E))
	private int continiousId;
	private Service service;
	
	//source and destination of the link based on the service function chaining
	private Middlebox source;
	private Middlebox destination;

	//this will hold the list of links this Vlink is routed through
	private ArrayList<Link> routedThrough;
	
	private int startedTransmission;// time slot at which the vlink started transmitting on all the links to which it is mapped - set to 0 if no transmission on this link
	private int[] isTransmitting;//all timeslots at which the vlink is transmitting on all the links to which it is mapped - set to 0 if no transmission on this link
	
	public VLink (int id, int continiousId, Service service, Middlebox source, Middlebox destination){
		this.id = id;
		this.continiousId = continiousId;
		this.service = service;
		this.source = source;
		this.destination = destination;
		this.routedThrough = new ArrayList<Link>();
		
		//set the size of the isTransmitting array to the number of time slots needed for transmission (size of traffic/bandwidth)
		this.isTransmitting = new int [(int)Math.ceil((double)this.service.getTrafficSize()/(double)this.service.getBandwdith())];
	}
	
	
	/**
	 * This function will clone a Vlink
	 * All object attributes to the Vlink are referenced and not copied
	 */
	public VLink clone()
	{
		VLink v = new VLink (this.id,this.continiousId, this.service,this.source, this.destination);
		v.startedTransmission = this.startedTransmission ;
		
		for(int i=0; i<this.routedThrough.size(); i++)
		{
			v.routedThrough.add(this.routedThrough.get(i));
		}
		
		for (int i=0; i<this.isTransmitting.length; i++)
		{
			v.isTransmitting[i] = this.isTransmitting[i];
		}
		
		return v;
	}
	
	public int getId(){
		return id;
	}
	
	public int getContiniousId(){
		return this.continiousId;
	}
	
	public Service getService(){
		return this.service;
	}
	
	public Middlebox getSource(){
		return this.source;
	}
	
	public Middlebox getDestination(){
		return this.destination;
	}
	
	public ArrayList<Link> getRoutedThrough(){
		return this.routedThrough;
	}
	
	public int getStartedTransmitting(){
		return this.startedTransmission;
	}
	
	public int [] getIsTransmitting (){
		return this.isTransmitting;
	}
	public void setId(int id){
		this.id = id;
	}
	
	public void setContiniousId(int continiousId){
		this.continiousId = continiousId;
	}
	
	public void setService(Service service){
		this.service = service;
	}
	
	public void setSource(Middlebox source){
		this.source = source;
	}
	
	public void setDestination (Middlebox destination){
		this.destination = destination;
	}
	
	public void setRoutedThrough(ArrayList<Link> links){
		 this.routedThrough = links;
	}
	
	public void setStartedTransmition(int startedTransmition){
		this.startedTransmission = startedTransmition;		
	}
	
	public void setIsTransmitting (int [] isTransmitting){
		this.isTransmitting = isTransmitting;		
	}
	
	
	/**
	 * This function will return true of source and destination of the virtual link 
	 * are hosted on the same server
	 * 
	 * @return true if source and destination are mapped to VNfs hosted on the same server
	 */
	public boolean areHostedOnSameServer(){
		return (this.source.getMappedToVNF().getPmId() == this.destination.getMappedToVNF().getPmId());
	}
	
	
	/**
	 * This method will return the transmission time needed on this virtual link
	 * by depending on the length of isTransmitting array calculated as traffic/bandwidth
	 * @return transmission delay
	 */
	public int getTransmissionDelays()
	{
		return this.isTransmitting.length;
	}
	
	
	public String toString(){
		String s="";
		s= String.format("Service Id: %4d; VLink Id : %4d; Continious Id: %4d;  Source Middlebox: id %4d; type %4d; Destination Middlebox: id %4d; type %4d \n",  this.service.getId(),this.getId(), this.getContiniousId(), this.source.getId(),  this.source.getType(), this.destination.getId(), this.destination.getType());
		
		s+= String.format("\t\t Routed Through the following physical links");
		for (int i=0; i<this.getRoutedThrough().size(); i++)
		{
			s+=String.format("%4d",this.getRoutedThrough().get(i).getId());
		}
		
		s+= String.format("\n\t\t\t Started transmitting at time slot %4d", this.getStartedTransmitting() );
		s+= String.format("\n\t\t\t Is transmiting at time slots :");
		for (int i=0; i<this.getIsTransmitting().length; i++)
		{
			s+= String.format("%4d", this.getIsTransmitting()[i] );
		}
		s+="\n";
		return s;
	}
}
