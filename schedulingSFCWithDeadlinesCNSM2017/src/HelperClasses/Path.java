/**
 * This class will help storing the path between a source and a destination, mainly used to store all shortest path in a network
 */
package HelperClasses;

import java.util.ArrayList;

import GeneralClasses.Node;
import GeneralClasses.Edge;

public class Path {
	private int id;
	private Node source;
	private Node destination;
	private ArrayList<Edge> path;
	
	public Path(int id, Node source, Node destination,  ArrayList<Edge> path )
	{
		this.id = id;
		this.source = source;
		this.destination = destination;
		this.path = path;
	}
	
	public int getId()
	{
		return this.id;
	}
	
	public Node getSource()
	{
		return this.source;
	}
	
	public Node getDestination()
	{
		return this.destination;
	}
	
	public ArrayList<Edge> getPath()
	{
		return this.path;
	}
	
	public void setSource (Node source)
	{
		this.source = source;
	}
	
	public void setDestination (Node destination)
	{
		this.destination = destination;
	}
	
	public void setId (int id)
	{
		this.id = id;
	}
	
	public void setPath(ArrayList<Edge> path)
	{	
		this.path = path;
	}
	
	public String toString()
	{
		String s="Id: "+this.getId();
		s+="	Source Node:"+ this.getSource().getId();
		s+="	Destination Node:"+ this.getDestination().getId();
		s+="	Path:";
		for (int i=0; i<this.getPath().size(); i++)
		{
			s+="\t"+this.getPath().get(i).getId();
		}
		
		return s;
	}
}
