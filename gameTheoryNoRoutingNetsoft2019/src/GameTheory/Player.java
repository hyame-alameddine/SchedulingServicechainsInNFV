package GameTheory;

import java.util.ArrayList;

import GeneralClasses.Edge;
import HelperClasses.Path;
import HelperClasses.PathDriver;
import NFV.Service;
import Network.Network;

public class Player extends Thread{
	private int pId;

	private Game game;//network based on which the graph is build
	private Service service;
	private Strategy strategy;
	private PlayerGraph pGraph;
	public ArrayList<Integer> completionTimeByIteration ;// this arrayList specified the completion time for each valid strategy selected at each iteration
	public ArrayList<Double> avgCompletionTimeByIteration ;
	public ArrayList<Double> payOffByIteration ;
	//this array holds the avg completion time for each 2 consecutive iterations. It is mainly created to stop the game when we reach e-nash using these values. 
	//this is to handle the case when the game does not stop and the completion time values are repeated
	public ArrayList<Double> twoConsecutiveAvgCompletionTime ;

	
	
	public Player( int id, Service service, Game game) throws Exception
	{	
		this.pId = id;

		this.game = game;
		this.service = service;
		
		//build the player graph
		int [] nodeEdge= this.service.calculateNbOfPlayerNodesEdges(this.game.physicalNetwork);			
		pGraph = new PlayerGraph(0, nodeEdge[0],nodeEdge[1], this);
		pGraph.buildGraph();
			
		this.completionTimeByIteration = new ArrayList<Integer>();
		this.avgCompletionTimeByIteration = new ArrayList<Double>();
		this.payOffByIteration = new ArrayList<Double>();
		this.twoConsecutiveAvgCompletionTime = new ArrayList<Double>();
	}
	
	public int getPid()
	{
		return this.pId;
	}
	
	public Service getService(){
		return this.service;
	}
	
	public Game getGame()
	{
		return this.game;
	}
	
	public PlayerGraph getPgraph()
	{
		return this.pGraph;
	}
	
	public Strategy getStrategy()
	{
		return this.strategy;
	}
	
	public void setPid(int pId)
	{
		this.pId = pId;
	}
	
	public void setService (Service service){
		this.service = service;
	}

	public void setGame(Game game)
	{
		this.game = game;
	}
	
	public void setPgraph(PlayerGraph pGraph)
	{
		this.pGraph = pGraph;
	}
	
	public void setStrategy(Strategy strategy)
	{
		this.strategy = strategy;
	}
	
	
	/**
	 * This method will build and set the strategy of the player based on the shortest path in his virtual graph
	 * by considering node and link weights
	 * 
	 * @param type type of the strategy, this will decide if we want to choose the strategy based on the longest path with a weight < service deadline of a shortest path
	 * 
	 * @throws Exception
	 */
	public void buildStrategy(Strategy.StrategyType type) throws Exception
	{
		ArrayList<Edge> edgeStrategyPath = null;
		if (type.equals(Strategy.StrategyType.SHORTEST_PATH))
		{
			//get the shortest path on the pGraph that will represent the strategy path consider node and links weights
			edgeStrategyPath = PathDriver.getShortestPath(this.pGraph, true, this.pGraph.getNodeList()[0], this.pGraph.getNodeList()[this.pGraph.getNodeNb()-1]);
		}
		else if (type.equals(Strategy.StrategyType.LONGEST_PATH))
		{
			edgeStrategyPath = this.getPgraph().getLongestPath(0);//this.service.getDeadlineTime()
		}
		else if (type.equals(Strategy.StrategyType.PLAYERS_AVG_COMPLETIONTIME))
		{
			edgeStrategyPath = this.getPgraph().getPathWithClosestAvgCompletionTime();
		}
		else if (type.equals(Strategy.StrategyType.MIXEDSTRATEGY))
		{
			edgeStrategyPath = this.getPgraph().getMixedStrategyPath(false);
		}
		else if (type.equals(Strategy.StrategyType.MIXEDSTRATEGYILP)) //get mixed strategy using ILp to calculate probability distribution
		{
			edgeStrategyPath = this.getPgraph().getMixedStrategyPath(true);
		}
		ArrayList<PlayerLink> strategyPath = new ArrayList<PlayerLink>();
		
		for(int i=0; i<edgeStrategyPath.size(); i++)
		{
			strategyPath.add((PlayerLink)edgeStrategyPath.get(i));
		}
		
		double avgCompletionTime = this.strategy ==null?0:this.strategy.avgCompletionTime;//keep the avg completion time of the old strategy
		this.strategy = null;
		this.strategy = new Strategy(0, this.getPid(), strategyPath, type);//there is only one strategy for the player
		this.strategy.avgCompletionTime =avgCompletionTime;
		
		double playersAvgCompletionTime = this.getGame().calculatePlayersAvgCompletionTime(this);
		this.strategy.updatePayOff(playersAvgCompletionTime);
	}
	
	public String toString()
	{
		String s="";
		s+=String.format("Player Id:%4d \n", this.getPid());//
		s+="\t"+ this.service.toString()+"\n";
		s+=String.format(" Network Id:%4d \n", this.getGame().physicalNetwork.getId());
		s+=" PLAYER GRAPH:\n"+ this.getPgraph().toString()+"\n";
		
		if (strategy!=null)
		{
			s+="STRATEGY: \n\t"+this.getStrategy().toString()+"\n";
		}
		else
		{
			s+="STRATEGY: null \n";
		}
		s+="Completion time by iteration:"+this.completionTimeByIteration.toString();
		s+="\n Avg Completion time by iteration"+this.avgCompletionTimeByIteration.toString();
		s+="\n PayOff by iteration"+this.payOffByIteration.toString();
		s+="\n twoConsecutiveAvgCompletionTime by iteration"+this.twoConsecutiveAvgCompletionTime.toString();
		return s;
	}
	
	
	
	/**
	 * This method applies the timeline on players who already played and have a strategy
	 * It updates all the players graph (nodes not in strategy) 
	 * update the completion time, avg completion time, payoff,  completionTimeByIteration, avgCompletionTimeByIteration for players who played(has a strategy)
	 * @param updateIterationValues if it is the last player playing, then this is the end of the iteration, we need the players 
	 * to update their completionTimePerIteration/AvgCompletionTimePerIteration/payOffPeriteratin
			
	 * @throws Exception 
	 */
	public void notifyPlayers(boolean updateIterationValues) throws Exception
	{		
		//get list of strategy moves for the players who chose their strategies
		ArrayList<Move> strategiesMoves = this.game.getMoveList(null);
				
		//ApplyTimeline to update the strategies for all the players who already played including this one
		this.game.identifySolveConflicts(strategiesMoves);
		//System.out.println("Playing "+this);
		for(int i=0; i<this.game.players.size(); i++)
		{
			//update the nodes of all players that are not in the strategy and update the players completion time/avgCompletion time/payoff
			this.game.players.get(i).update(updateIterationValues, this.game.players);
			
		//	System.out.println("==Updating "+this.game.players.get(i));
		}	
		
	}
	
	
	/**
	 * A player selects a strategy and notifies the other of its strategy to update their graphs
	 * @throws Exception
	 */
	/**
	 * A player updates his graph based on the other player strategies and chooses a strategy
	 * @param updateIterationValues if true set the avg completion time, completion time, payoff per iteration
	 * @param playersWhoPlayed list of players who played so fat in an iteration so their strategies are considered and updated when identifying conflict
	 * @throws Exception
	 */
	public void play(boolean updateIterationValues, ArrayList<Player>playersWhoPlayed) throws Exception
	{
		//update the nodes of the current player only so he can select his strategy accuratley, no need to update the graph (nodes not in strategy) of the other players for now
		this.update(updateIterationValues, playersWhoPlayed);
	
		//player starts building its strategy
		this.buildStrategy(this.game.type);	
	}
	
	/**
	 * @param updateIterationValues if it is the last player playing, then this is the end of the iteration, we need the players 
	 * to update their completionTimePerIteration/AvgCompletionTimePerIteration/payOffPeriteratin
	 * @throws Exception
	 */
	public void update(boolean updateIterationValues,ArrayList<Player>playersWhoPlayed) throws Exception
	{
		boolean[] visited = new boolean [this.getPgraph().getNodeNb()];
		PlayerNode currentNode = (PlayerNode) this.getPgraph().getNodeList()[0];
		ArrayList<Move> strategiesMoves = this.getGame().getMoveList(playersWhoPlayed); //need to update all the graph of the player including nodes of his selected strategy in the previous iteration
		double playersAvgCompletionTime = this.getGame().calculatePlayersAvgCompletionTime(this);
		
	
		//update cost of the nodes not in strategy
		this.pGraph.updateCost(0, visited, currentNode, strategiesMoves);
				
		//update cost of nodes in strategy first
		if (this.strategy!=null)
		{
			this.getStrategy().updateCompletionTime();
			this.getStrategy().updateAvgCompletionTime();
						
			this.getStrategy().updatePayOff(playersAvgCompletionTime);
			
			
			if (updateIterationValues)
			{
				//save the completion time of each strategy at each iteration
				this.completionTimeByIteration.add(this.getStrategy().getCompletionTime());
				this.avgCompletionTimeByIteration.add(this.getStrategy().avgCompletionTime);
				this.payOffByIteration.add(this.getStrategy().payOff);
			}
			
		}
	
			
	}
	
	/**
	 * This method updates the calculated attributes of the player
	 * 
	 * @param updateIterationValues set to true if at the end of the iteration, we need the players 
	 * to update their completionTimePerIteration/AvgCompletionTimePerIteration/payOffPeriteratin
	 * 
	 */
	public void update(boolean updateIterationValues) 
	{
		double playersAvgCompletionTime =this.getGame().calculatePlayersAvgCompletionTime(this);
		double twoConsecutiveAvg =0;
		int ctSize =0;
		//update cost of nodes in strategy first
		if (this.strategy!=null)
		{
			this.getStrategy().updateCompletionTime();
			this.getStrategy().updateAvgCompletionTime();						
			this.getStrategy().updatePayOff(playersAvgCompletionTime);			
			
			if (updateIterationValues)
			{
				//save the completion time of each strategy at each iteration
				this.completionTimeByIteration.add(this.getStrategy().getCompletionTime());
				this.avgCompletionTimeByIteration.add(this.getStrategy().avgCompletionTime);
				this.payOffByIteration.add(this.getStrategy().payOff);
				
				ctSize = this.completionTimeByIteration.size();
				//we are not in the first iteration
				if (ctSize>1)
				{
					twoConsecutiveAvg = (this.completionTimeByIteration.get(ctSize-1)+this.completionTimeByIteration.get(ctSize-2))/2;
					this.twoConsecutiveAvgCompletionTime.add(twoConsecutiveAvg);
				}
			}
			
		}
	
			
	}
}
