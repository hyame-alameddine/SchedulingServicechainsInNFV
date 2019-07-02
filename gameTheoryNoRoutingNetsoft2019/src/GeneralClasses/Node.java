package GeneralClasses;

public class Node {
	protected int id;
	protected int weight;
	
	public Node (int id, int weight)
	{
		this.id = id;
		this.weight = weight;
	}
	
	public Node clone()
	{
		return new Node(this.id, this.weight);
	}
	
	public int getId()
	{
		return this.id;
	}
	
	public int getWeight()
	{
		return this.weight;
	}
	
	public void setId(int id)
	{
		this.id = id;
	}
	
	public void setWeight(int weight)
	{
		this.weight = weight;
	}
	
	public boolean equals(Node n)
	{
		if (this.getClass().equals(n.getClass()) && this.getId()==n.getId())
		{
			return true;
		}
		
		return false;		
	}
	public String toString(){
		String s="";
		s+=String.format("Node id: %4d \n", this.getId());
		s+=String.format("\t Weight: %4d ", this.getWeight());
		return s;
	}
}
