/**
 * this represents a physical machine that can be connected to multiple nodes (many links)
 */
package Network;
import java.io.Serializable;
import java.util.ArrayList;
public class PhysicalMachine implements Serializable{

		private int id;
		private ArrayList<VNF> hostedVNFs;
		
		public PhysicalMachine(int id){
			this.id = id;
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
		public int getId(){
			return this.id;
		}
		
		public ArrayList<VNF> getHostedVNFs(){
			return this.hostedVNFs;
		}
		
		public void setId(int id){
			this.id = id;
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
