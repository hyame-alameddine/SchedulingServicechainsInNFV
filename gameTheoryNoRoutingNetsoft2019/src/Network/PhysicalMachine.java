/**
 * this represents a physical machine that can be connected to multiple nodes (many links)
 */
package Network;
import java.io.Serializable;
import java.util.ArrayList;

import GeneralClasses.Node;
public class PhysicalMachine extends Node implements Serializable{
				
		private ArrayList<VNF> hostedVNFs;
		
		public PhysicalMachine(int id){
			super(id, 0);//set weight to 0 by default
			this.hostedVNFs = new ArrayList<VNF>();
		}
		
		public PhysicalMachine clone()
		{
			PhysicalMachine pm = new PhysicalMachine(this.id);
			
			for (int i=0; i<this.hostedVNFs.size(); i++)
			{
				pm.hostedVNFs.add(this.hostedVNFs.get(i).clone());
			}
			
			return pm;
		}
			
		public ArrayList<VNF> getHostedVNFs(){
			return this.hostedVNFs;
		}
		
		public void setHostedVNFs(ArrayList<VNF> hostedVNFs){
			this.hostedVNFs = hostedVNFs;
		}
		
 
		public String toString(){
			String s="";
			s+=String.format("Physical Machine id: %4d \n", this.getId());
			s+=String.format("\t\t Hosted VNFs \n");
			for (int i=0;i<this.getHostedVNFs().size();i++){
				s+=String.format("\t\t");
				s+=this.getHostedVNFs().get(i);
			}
			
			return s;
		}
}
