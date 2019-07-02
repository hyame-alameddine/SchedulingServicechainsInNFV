/**
 * This class is used to hold the information of column generation 
 * By storing the selected configurations by the ILp
 */
package HelperClasses;

import java.util.ArrayList;

import Model.Configuration;
import Model.PricingSolution;
import NFV.Middlebox;
import NFV.Service;
import NFV.VLink;

public class Batch {
	public static int BtachId =0;
	public int id;
	public int startTimeslot;
	public int endTimeslot;
	public double ilpExecutionTime;
	public double lpExecutionTime;
	public double ilpObjective;
	public double lpObjective;
	
	public int CGIterations;
	public int delta;
	
	//this will hold selected configurations by the ILP
	public ArrayList<Configuration> configurations;

	public ArrayList<Service> services;
	public String error;
	
	public Batch (ArrayList<Service> services,int startTimeslot, int endTimeslot )
	{
		this.id = Batch.BtachId;
		this.services = services;
		this.startTimeslot = startTimeslot;
		this.endTimeslot = endTimeslot;
		
		this.configurations = new ArrayList<Configuration>();
		Batch.BtachId++;
	}

	public String toString()
	{
		String s="";
		s= String.format("Batch Id: %4d;\n",  this.id);
		s+= String.format("\t Start Timeslot: %4d \n",this.startTimeslot);
		s+= String.format("\t End Timeslot: %4d \n",this.endTimeslot);
		s+=String.format("\t Ilp Execution Time: %4f\n",this.ilpExecutionTime);
		s+=String.format("\t Lp Execution Time : %4f \n", this.lpExecutionTime);
		s+=String.format("\t Ilp Objective: %4f\n",this.ilpObjective);
		s+=String.format("\t Lp Objective: %4f\n",this.lpObjective);
		s+=String.format("\t CGIterations: %4d\n",this.CGIterations);
		s+=String.format("\t Delta: "+this.delta+"\n");
		s+=String.format("\t Error: "+this.error+"\n");
		s+=String.format("\t Services:\n");
		for(int i = 0; i<this.services.size(); i++){
			s+="\t\t"+this.services.get(i);
		}
		
		s+=String.format("\tConfigurations:\n");
		for(int i = 0; i<this.configurations.size(); i++){
			s+="\t\t"+this.configurations.get(i);
		}	
		
		return s;
	}
	
	
}
