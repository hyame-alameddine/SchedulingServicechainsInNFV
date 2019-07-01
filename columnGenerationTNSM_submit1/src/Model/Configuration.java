package Model;

import NFV.Service;

public class Configuration {
	private int id;
	
	//this id will be set when adding column to master
	private int idPerService;//this specifies the configuration id based on each service. this is to be used to know if the configuration was selected or not
	private Service service;
	private int v ; //completion time of the service
	private int[][] r ;//fdelta // if VNF f is used at time slot delta
	private int[][] o;//ldelta // if the link l is used at time slot delta
	
	//this will allow setting the configuration ids for all pricing in incremental manner. It will be equal to the total number of configurations passed to the master -1
	public static int configurationId =0;
	
	public Configuration (  Service s, int v, int[][] r,int[][] o ){
		this.id = configurationId;
		configurationId++;
		this.service= s; 
		this.v = v;
		this.o = o;
		this.r=r;				
	}
	
	public int getId(){
		return this.id;
	}
	public int getIdPerService(){
		return this.idPerService;
	}
	public Service getService(){
		return this.service;
	}
	
	public int getV(){
		return this.v;
	}
	
	public int[][] getR(){
		return this.r;
	}
	
	public int[][]getO(){
		return this.o;
	}
	
	public void setIdPerService(int idPerService){
		this.idPerService =idPerService;
	}
	
	public void setV(int v){
		this.v =v;
	}
	
	public void setR(int [][] r){
		this.r =r;
	}

	public void setO(int [][] o){
		this.o =o;		
	}
	
	public void setId(int id){
		this.id =id;
	}
	
	public void setService(Service s){
		this.service = s;
	}
	
	
	public boolean equals (Configuration c)
	{
		//check configuration belongs to the same service
		if (this.service.getId()!=c.service.getId())
		{
			return false;
		}
		
		//check if they have the same completion time
		if (this.v!=c.v)
		{
			return false;
		}
		
		//check if r is the same
		for (int i=0; i<this.r.length; i++)
		{
			for (int j=0; j<this.r[i].length; j++)
			{
				if(this.r[i][j] !=c.r[i][j])
				{
					return false;
				}
			}
		}
		
		//check if o is the same 
		for (int i=0; i<o.length; i++)
		{			
			for (int delta=0; delta<this.o[i].length; delta++)
			{	
				if(this.o[i][delta] !=c.o[i][delta])
				{
					return false;
				}
			}
		}
		
		return true;
	}
	
	
	public String toString(){
		String s = String.format("Column Id: %4d;\n",  this.getId());
		s+= String.format("\t\t\t Column Per Service ID (used to know if column was selected): %4d;\n",  this.getIdPerService());
		s+= String.format("\t\t\t Service ID: %4d;\n",  this.getService().getId());
		s+= String.format("\t\t\t Completion time v: %4d;\n",  this.getV());
		
		s+=String.format("\t\t\t VNF f used at time slot delta r[f][delta]:\n");
		for(int i=0; i<this.getR().length; i++){
			for(int j=0; j<this.getR()[i].length; j++){
				if (this.getR()[i][j] ==1)
					s+=String.format("\t\t\t\t r["+i+"]["+j+"]: %4d;\n" ,this.getR()[i][j]);
			}
		}
		
		s+=String.format("\t\t\t Link l used at time slot delta o[l][delta]:\n");
		for(int i=0; i<this.getO().length; i++){
			for(int j=0; j<this.getO()[i].length; j++){
				if (this.getO()[i][j] !=0)
					s+=String.format("\t\t\t\t o["+i+"]["+j+"]: %4d;\n" ,this.getO()[i][j]);
			}
		}
		
		return s;
	}
}
