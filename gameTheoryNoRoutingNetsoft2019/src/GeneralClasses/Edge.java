package GeneralClasses;


public class Edge {
	protected int id;
	protected int weight;
	protected Node source;
	protected Node destination;
	
	public Edge (int id, int weight, Node source, Node destination)
	{
		this.id = id;
		this.weight = weight;
		this.source = source;
		this.destination = destination;
	}
	
	public Edge clone()
	{
		return new Edge(this.id, this.weight, this.source.clone(), this.destination.clone());
	}
	public int getId()
	{
		return this.id;
	}
	
	public int getWeight()
	{
		return this.weight;
	}
	
	
	public Node getSource(){
		return this.source;
	}
	
	public Node getDestination(){
		return this.destination;
	}
	
	public void setId(int id)
	{
		this.id = id;
	}
	
	public void setWeight(int weight)
	{
		this.weight = weight;
	}
	
	public void setSource(Node source){
		this.source = source;
	}
	
	public void setDestination(Node destination){
		this.destination = destination;
	}
	
	
	public boolean equals(Edge e)
	{
		return this.id == e.getId();
	}
	
	public String toString()
	{
		String s="";
		
		s+=String.format("Edge id: %4d; Source id: %4d; Destination id: %4d Weight: %4d\n", this.getId(),this.getSource().getId(), this.getDestination().getId(), this.getWeight());
		return s;
		
		
	}
}
