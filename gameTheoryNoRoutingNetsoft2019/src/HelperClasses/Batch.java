
/**
 * This class is used to hold the information of column generation 
 * By storing the selected configurations by the ILp
 */
package HelperClasses;

import java.util.ArrayList;
import NFV.Service;

public class Batch {
	
	public int id;	
	public double ilpExecutionTime;
	public int overallObjective;//the objective given by scheduling model with the already set variables for previous batches
	public int batchObjective;//the number of admitted services from this batch = overall objective -overallObjective of previous batch
	
	//this holds all the services in the batch
	public ArrayList<Service> services;
	
	//this holds all the services from this.services that were successfully mapped and routed and passed to scheduling
	public ArrayList<Service> servicesToSchedule;
	
	public Batch (int id,ArrayList<Service> services)
	{
		this.id = id;
		this.services = services;
		this.servicesToSchedule = new ArrayList<Service>();
	
	}

	public String toString()
	{
		String s="";
		s= String.format("Batch Id: %4d;\n",  this.id);
		s+=String.format("\t Batch size: %4d\n",this.services.size());
		s+=String.format("\t Services to schedule: %4d\n",this.servicesToSchedule.size());
		s+=String.format("\t Btach-Ilp Execution Time: %4f\n",this.ilpExecutionTime);
		s+=String.format("\t Overall Objective (with previous bathes): "+this.overallObjective+"\n");
		s+=String.format("\t Batch Objective: "+this.batchObjective+"\n");
		s+=String.format("\t Services:\n");
		for(int i = 0; i<this.services.size(); i++){
			s+="\t\t"+this.services.get(i);
		}
		
		s+=String.format("\t servicesToSchedule:\n");
		for(int i = 0; i<this.servicesToSchedule.size(); i++){
			s+="\t\t"+this.servicesToSchedule.get(i);
		}
		return s;
	}
	
	
	
}
