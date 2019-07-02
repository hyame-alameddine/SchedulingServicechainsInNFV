/**
 * This class represent the middleboxes requested by the service
 */
package NFV;

import java.io.Serializable;

import Network.VNF;

public class Middlebox implements Serializable{
	
	// id of the middlebox, this will specify its order in the chain
	private int id;
	private Service service;
	private int type;
	private VNF mappedToVNF;
	private int startedProcessing;// time slot at which the middlebox started processing at the mappedVNf set even if the processing time =0
	private int[] isProcessing;//all timeslots at which the middlebox is processing/ empty if processng time =0
	
	//processing time of service traffic on the VNF this middlebox is mapped to (considered fixed and independent of the VNf it is mapped to)
	private int processingTime;

	public Middlebox(int id, Service service, int type, int processingTime){
		this.id = id;
		this.service = service;
		this.type = type;
		this.processingTime = processingTime;
		this.isProcessing = new int[this.processingTime];
				
	}
	
	/**
	 * This will clone the current object and return a copy of it with the same id
	 * All object attributes are referenced and copied
	 * @ return cloned middlebox
	 */
	public Middlebox clone()
	{
		Middlebox m = new Middlebox(this.id, this.service, this.type, this.processingTime);
		m.mappedToVNF = this.mappedToVNF; //no need to copy
		m.service = this.service;//no need to copy This can be changed later on
		for (int i=0; i<this.isProcessing.length; i++)
		{
			m.isProcessing[i] = this.isProcessing[i];
		}
		
		return m;
	}
	public int getId(){
		return this.id;
	}
	
	public Service getService(){
		return this.service;
	}
	
	public int getType(){
		return this.type;
	}
	
	public int getProcessingTime(){
		return this.processingTime;
	}
	
	public VNF getMappedToVNF(){
		return this.mappedToVNF;
	}
	
	public int getStartedProcessing(){
		return this.startedProcessing;
	}
	
	public int[] getIsProcessing(){
		return this.isProcessing;
	}
	
	public void setId (int id){
		this.id = id;
	}
	
	public void setService (Service service){
		this.service = service;
	}
	
	public void setType (int type){
		this.type = type;
	}
	
	public void setProcessingTime(int processingTime){
		this.processingTime=processingTime;
		
		//update the is processing-handle the case where processing time =0 and then it is updated to be 2, the size of the processing time should be 2
		this.setIsProcessing(new int[this.processingTime]);
	}
	
	public void setMappedToVNF (VNF mappedToVNF){
		this.mappedToVNF = mappedToVNF;
	}
	
	public void setStartedProcessing (int startedProcessing){
		this.startedProcessing = startedProcessing;
	}
	
	public void setIsProcessing(int [] isProcessing){
		this.isProcessing = isProcessing;
	}
	
	/**
	 * this will return the difference between the started processing of this middlebix and the finished processing of the previous one
	 * @return
	 */
	public int getTimeGap()
	{
		Middlebox previousMiddlebox = this.service.getMiddleboxes().get(this.id-1);
		
		return this.getStartedProcessing()-previousMiddlebox.getIsProcessing()[previousMiddlebox.processingTime-1];
	}
	
	public String toString(){
		String s= "";
		s = String.format("Service Id : %4d; MiddleBox Id: %4d; Type: %4d; Processing time: %4d;", this.service.getId(), this.getId(), this.getType(), this.getProcessingTime());
		
		if(this.mappedToVNF!=null)
		{
			s+= String.format("Mapped to VNF id: %4d;", this.mappedToVNF.getId());
		}
		
		s+= String.format("\n\t\t\t Started Processing at time slot %4d", this.getStartedProcessing() );
		s+= String.format("\n\t\t\t Is Processing at time slots :");
		for (int i=0; i<this.getIsProcessing().length; i++)
		{
			s+= String.format("%4d", this.getIsProcessing()[i] );
		}
		
		s+= String.format("\n");
		return s;
	}
}
