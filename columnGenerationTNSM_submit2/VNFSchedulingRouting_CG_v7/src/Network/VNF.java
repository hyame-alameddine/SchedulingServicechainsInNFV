/**
 * this class represents the VNFs in the network to which services are mapped
 */
package Network;

import java.io.Serializable;

public class VNF implements Serializable{

	private int id;
	private int type;
	private int pmId;
	
	public VNF(int id, int type, int pmId){
		this.id = id;
		this.type = type;
		this.pmId = pmId;
		
		
	}
	
	public VNF clone()
	{
		return new VNF (this.id, this.type, this.pmId);
	}
	public int getId(){
		return this.id;
	}
	
	public int getType(){
		return this.type;
	}
	
	public int getPmId(){
		return this.pmId;
	}
	
	public void setId(int id){
		this.id = id;
	}
	
	public void setType(int type){
		this.type=type;
	}
	
	public void setPm(int pmId){
		this.pmId = pmId;
	}
	
	
	public String toString(){
		String s="";
		s= String.format("Physical server Id: %4d; VNF Id : %4d; Type: %4d; \n",  this.getPmId(),this.getId(), this.getType());
		
		return s;
	}
}
