package GameTheory;

import java.util.ArrayList;

import GameTheory.PlayerNode.PNodeType;

public class Strategy {

	private int id;//continious for each player and depends on the number of iterations it was changed??
	private int playerId;
	private ArrayList<PlayerLink> strategyPath; //shortest path determined from the player graph
	private PlayerNode[] nodes;
	private int completionTime;
	
	//used for avgCompletionTime (better results when smaller value
	private double  alpha = 0.9;
//0.1	
	//can be used as payoff calculated based avgCompletionTime = alpha*AvgCompletionTime(n-1)+(1-alpha)CompletionTime(n) (n being nb of iteration)	
	public double avgCompletionTime;
	
	//this payOff is used as min|playersAvgCompletionTime(N-1)- Completion time(N)| where N is the players that played till now. Used with strategy type PLAYERS_AVG_COMPLETIONTIME
	public double payOff;
	
	
	/*
	 * this specifies if we are selecting the strategy based on the shortest or longest path
	 * or PLAYERS_AVG_COMPLETIONTIME which is the path which has the smallest |playersAvgCompletionTime-path Completion time|
	 * the playersAvgCompletionTime = sum of all players completion time (0 if player did not yet play)except the one playing/ number of players except the one playing
	 */
	public enum StrategyType {SHORTEST_PATH,LONGEST_PATH, PLAYERS_AVG_COMPLETIONTIME, MIXEDSTRATEGY, MIXEDSTRATEGYILP};
	private StrategyType type;
	
	public Strategy(int id, int playerId, ArrayList<PlayerLink> strategyPath, StrategyType type)
	{
		this.id = id;
		this.playerId = playerId;
		this.strategyPath = strategyPath;
		this.type = type;
		
		//set the nodes based on the strategy path
		this.updateNodes();
		
		//set the completion time to the last link (related to destination) arrival time as this will be the finish processing of the last middlebox in the chain
		this.completionTime = this.strategyPath.get(this.strategyPath.size()-1).getArrivalTime();
		this.avgCompletionTime =0;
		
		this.payOff=0;
	}
	
	
	public int getId()
	{
		return this.id;
	}
	
	public int getPlayerId()
	{
		return this.playerId;
	}
	
	public ArrayList<PlayerLink> getStrategyPath()
	{
		return this.strategyPath;
	}
	
	public PlayerNode[] getNodes()
	{
		return this.nodes;
	}
	
	public int getCompletionTime()
	{
		return this.completionTime;
	}
	
	public void setId(int id)
	{
		this.id= id;
	}
	
	public void setPlayerId(int playerId)
	{
		this.playerId = playerId;
	}
	
	public void setStrategyPath(ArrayList<PlayerLink> strategyPath)
	{
		this.strategyPath = strategyPath;
	}
	
	public void setNodes(PlayerNode[] nodes)
	{
		this.nodes = nodes;
	}
	
	public void setCompletionTime (int completionTime)
	{
		this.completionTime = completionTime;
	}
	
	public StrategyType getType()
	{
		return this.type;
	}
	
	public void setStrategyType(StrategyType type)
	{
		this.type = type;
	}
	
	public void updateCompletionTime ()
	{
		//set the completion time to the last link (related to destination) arrival time as this will be the finish processing of the last middlebox in the chain
		this.completionTime = this.strategyPath.get(this.strategyPath.size()-1).getArrivalTime();
	}
	
	/**
	 * avgCompletionTime = alpha*AvgCompletionTime(n-1)+(1-alpha)CompletionTime(n) (n being nb of iteration)
	 */
	public void updateAvgCompletionTime()
	{
		this.avgCompletionTime = this.alpha*this.avgCompletionTime+(1-this.alpha)*this.completionTime;
	}
	
	
	/**
	 * This method will update the payoff provided by the strategy as the absolute value of the difference between playerAvgCompletionTime and the strategy completion time
	 * 
	 * @param playerAvgCompletionTime  this is the avg completion time of players who played 
	 */
	public void updatePayOff (double playerAvgCompletionTime)
	{//System.out.println("playerAvgCompletionTime"+playerAvgCompletionTime);
		this.payOff = Math.abs(playerAvgCompletionTime-this.completionTime);
		
	}
	
	
	public String toString()
	{
		String s="";
		s+=String.format("Strategy Id:%4d ", this.getId());
		s+=String.format("\t Player Id:%4d ", this.getPlayerId());
		s+=String.format("\t Type: %4s ", this.getType());
		s+=String.format("\t Completion Time: %4d ", this.getCompletionTime());
		s+=String.format("\t Average Completion Time: %4f ", this.avgCompletionTime);
		s+=String.format("\t PayOff: %4f ", this.payOff);
		s+=String.format("\t Nodes :\n");
		for(int i=0; i<this.getNodes().length; i++){
			if(this.getNodes()[i].getType() == PNodeType.SOURCE || this.getNodes()[i].getType() == PNodeType.DESTINATION)
			{
				s+=String.format("\n\t ID:%4d \t VNF ID:%4s",+this.getNodes()[i].getId(), "no VNF - Source or destination node");
				continue;
			}
			s+=String.format("\n\t ID:%4d \t VNF ID:%4d",+this.getNodes()[i].getId(), this.getNodes()[i].getVnf().getId());
		}
		s+=String.format("\n\t STRATEGY PATH \n");
		for(int i=0; i<this.getStrategyPath().size(); i++){
			s+="\t"+this.getStrategyPath().get(i).toString()+"\n";
		}
		return s;
	}
	
	/**
	 * This method will check if 2 strategies are equal by checking :
	 * if they belong to the same player
	 * They have the same completion time
	 * they have the same strategy path
	 * they have the same nodes
	 * 
	 * It doesn't check if the info of the link (routed through) and  nodes are the same (arrival, startexec, order, weight, etc)
	 * @param s
	 * @return
	 */
	public boolean equals(Strategy s)
	{
		if (s.playerId!= this.playerId)
		{
			return false;
		}
		if (this.avgCompletionTime!=s.avgCompletionTime)
		{
			return false;
		}
		
		if (this.completionTime!=s.completionTime)
		{
			return false;
		}
		/*if (this.completionTime-s.completionTime>1e-10)
		{
			return false;
		}*/
		
		//check if the same links are used
		for (int i=0; i<this.strategyPath.size(); i++)
		{
			if (!this.strategyPath.get(i).equals(s.getStrategyPath().get(i)))
			{
				return false;
			}
		}
		
		//check if the same nodes/vnf are used (this is not necessary if the links are checked since the links source and destination will be the nodes)
		for (int i=0; i<this.nodes.length; i++)
		{
			if (!this.nodes[i].equals(s.getNodes()[i]))
			{
				return false;
			}
		}
		
		
		return true;
	}
	
	/**
	 * This method will set the nodes based on the strategy path and include the source and destination nodes
	 */
	public void updateNodes()
	{
		this.nodes = new PlayerNode[this.strategyPath.size()+1]; //number of nodes = number of links+1
		for(int i=0; i<this.strategyPath.size(); i++)
		{			
			
			this.nodes[i] = (PlayerNode)this.strategyPath.get(i).getSource();
			
			//add destination node from the last link- nb nodes = nb of edges+1
			if(i==this.strategyPath.size()-1)
			{
				this.nodes[i+1] =  (PlayerNode)this.strategyPath.get(i).getDestination();
				continue;
			}			
			
		
		}
	}
	
	/**
	 * This function allows to search for a player link having as node the specified source passed as parameter
	 * 
	 * @param source source node of the link to search for
	 * 
	 * @return found link in the strategy
	 */
	public PlayerLink searchLinks (PlayerNode source)
	{
		PlayerLink l= null;
		for (int i=0; i<this.getStrategyPath().size(); i++)
		{
			l = this.getStrategyPath().get(i);
			if (l.getSource().equals(source))
			{
				return l;
			}
		}
		
		return null;
	}
	
	
	/**
	 * This method checks if the node in parameter is in the player strategy
	 * @param n player node 
	 * 
	 * @return true if node found in strategy
	 */
	public boolean findMove (PlayerNode n)
	{
		for (int i=0; i<this.nodes.length; i++)
		{
			if (n.equals(this.nodes[i]))
			{
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method checks if the link in parameter is in the player strategy
	 * @param l player link
	 * @return true if link found in strategy
	 */
	public boolean findMove(PlayerLink l)
	{
		for (int i=0; i<this.strategyPath.size(); i++)
		{
			if (l.equals(this.strategyPath.get(i)))
			{
				return true;
			}
		}
		return false;
	}
	
}
