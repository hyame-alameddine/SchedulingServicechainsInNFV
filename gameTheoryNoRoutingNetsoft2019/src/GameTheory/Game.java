package GameTheory;

import java.util.ArrayList;

import GameTheory.Strategy.StrategyType;
import HelperClasses.FileManipulation;
import HelperClasses.ServicesDriver;
import NFV.Service;
import Network.*;

public class Game {

	public Network physicalNetwork;
	public ArrayList<Player> players;
	public Strategy.StrategyType type; //this will decide how the players should play based on shortest or longest path
	
	
	public int linksArraySize;
	
	
	/**
	 * 
	 * @param physicalNetwork
	 * @param services
	 * @param type
	 * 
	 * @param multiplier multiplier to set the linksArraySize= highestDeadline*multiplier. Only used if we are considering link scheduling
	 */
	public Game (Network physicalNetwork,  ArrayList<Service> services, Strategy.StrategyType type,  double multiplier)
	{
		this.physicalNetwork = physicalNetwork;
		this.type = type;
					
		
		this.players = new ArrayList<Player>();
		for (int i=0; i<services.size(); i++)
		{						
			try {
				Player p = new Player( i,services.get(i), this);
				
				this.players.add(p);
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}		
		
		}
		
		this.linksArraySize = (int)(ServicesDriver.getHighestDeadline( this.getServices())*multiplier);
	}
	
	/*public Game (Network physicalNetwork,  ArrayList<Player> players)
	{
		this.physicalNetwork = physicalNetwork;
		
		this.players= players;
	
	}*/
	
	/**
	 * This method will return an array list of moves that is the player nodes and links in the players strategy
	 * 
	 * @param playersWhoPlayed player of which we want to get the strategy moves. if set to null we consider all players in this.players
	 * This is mainly used when we are playing sequentially and when identifying conflict we only want to consider player who played in the current iteration till now
	 * without considering their strategies in previous ietrations
	 * @return arraylist of move
	 */
	public ArrayList<Move> getMoveList (ArrayList<Player>playersWhoPlayed)
	{
		ArrayList<Move> strategyMoves = new ArrayList<Move>();
		Strategy s = null;
		
		if (playersWhoPlayed==null)
		{
			playersWhoPlayed = this.players;
		}
		
		for (int i=0; i<playersWhoPlayed.size(); i++)
		{
			s = playersWhoPlayed.get(i).getStrategy();
			
			//player did not play yet
			if (s==null)
			{
				continue;
			}
			
			for (int j=0; j<s.getNodes().length; j++)
			{
				strategyMoves.add(s.getNodes()[j]);
			}
			
			strategyMoves.addAll(s.getStrategyPath());
		}
		
		return strategyMoves;
		
	}
	
	
	/**
	 * This method will search for a move in an array and return its index
	 * @param moves arraylist of moves
	 * @param m move we are searching for
	 * @return -1 if move was not found, index of move in the array if found
	 */
	public int searchMove (ArrayList<Move> moves, Move m)
	{
			
		for (int i=0; i<moves.size(); i++)
		{
			if (moves.get(i).equals(m))
			{
				return i;
			}
		}
		return -1;
	}
	
	/**
	 * This will order the moves (nodes and link of the player) based on the arrival time or startExecutionTime (processing/transmission)
	 * Initially (iteration0)  they will be ordered by arrival time (here we have startExecution = arrivalTime)
	 * 
	 * After identifying the conflict, the VNfs in conflict will update their startProcessing time and weight. and their dependent (successors) link/VNF will update their
	 * arrival/ startExecution time. So in this case we order those based on startExecution time and the others based on their arrival time.
	 * 
	 * When having a strategy we first order based on arrival and then when identifying conflict we order based of startProcessing starting from the conflict index 
	 * for the affected nodes by the conflict the other will stay ordered based on their arrival.
	 * 
	 * This is why we declare a new attribute for nodes and links reset to the arrival time when first ordering at each iteration
	 * 
	 * @param strategyMoves nodes and links in the players strategy
	 * @param startIndex index at which we need to start the ordering. Set to 0 when ordering after defining the strategy and to the conflict index when updating based on conflict
	 * 
	 *  return ascending ordered list of moves based order index
	 */
	public void orderMoves (ArrayList<Move> strategyMoves, int startIndex)
	{
		Move temp = null;
		for (int i = startIndex; i < strategyMoves.size()-1; i++) 
		{
		      for (int j = i+1; j < strategyMoves.size(); j++) 
		      {
		        if (strategyMoves.get(i).getOrderValue() > strategyMoves.get(j).getOrderValue()) 
		        {
		           temp = strategyMoves.get(i);
		           strategyMoves.set(i, strategyMoves.get(j));
		           strategyMoves.set(j, temp);
		        }
		      }
		 }
		
	}
	
	
	/**
	 * This method will set the orderValue to the arrivalTime
	 * 
	 * @param strategyMoves list of moves (nodes and links) in the strategy of each player
	 */
	public void resetOrderValue(ArrayList<Move> strategyMoves)
	{
		for (int i = 0; i < strategyMoves.size(); i++) 
		{
			strategyMoves.get(i).setOrderValue(strategyMoves.get(i).getArrivalTime());
		}
	}
	
	
	/**
	 * Return the list of services of the current players in the game
	 * @return list of services of the current players
	 */
	public ArrayList<Service> getServices()
	{
		ArrayList<Service> services = new ArrayList<Service>();
		for (int i=0; i<this.players.size(); i++)
		{
			services.add(this.players.get(i).getService());
		}
		return services;
	}
	
	/**
	 * This method takes an array of strategy moves and order them based on their arrival time
	 * identifies VNF and link conflicts. Solves each of them and updates the arrival, startExecTime and order value accordingly in addition to updating their
	 * successors. It will then reorder the strategyMoves starting with the index of the conflict.
	 * 
	 * @param strategyMoves not ordered moves
	 * @throws Exception 
	 */
	public void identifySolveConflicts(ArrayList<Move> strategyMoves) throws Exception
	{
		
		ArrayList<PlayerNode> nodesConflict = null;
		ArrayList<PlayerLink> linksConflict = null;
		Move m = null;
		PlayerNode tempNode;
		PlayerLink tempLink;
		 ArrayList<ArrayList<Integer>>physicalLinks = this.physicalNetwork.getLinksTimeBw(this.linksArraySize);
		 
		//start by ordering the moves
		this.orderMoves(strategyMoves, 0);
		/*System.out.println("==================INITIAL ORDER============");
		for(int j=0; j<strategyMoves.size();j++)
		{
			System.out.println(strategyMoves.get(j));
		}	*/
		for (int i = 0; i < strategyMoves.size(); i++) 
		{
			m = strategyMoves.get(i);
			
			//if no transmission on the link (VNF on same server ) or no processing (source or destination or ingress or egress nodes)
			if (m.getExecutionTime() == 0)
			{
				continue;
			}
			
			if (m instanceof PlayerNode)
			{//System.out.println("==================Node Conflict with  "+m);
				nodesConflict = ((PlayerNode)m).identifyVNFConflictBasedOnExecTime(strategyMoves,i, false);//((PlayerNode)m).identifyVNFConflict(strategyMoves,i, false);
				
				if (nodesConflict == null)
				{
					continue;
				}
				/*System.out.println("Conflict NOdes===");
				for(int k=0; k<nodesConflict.size(); k++)
				{
					System.out.print ("("+nodesConflict.get(k).getId()+"-"+nodesConflict.get(k).getPGraph().getPlayer().getPid()+") ;");
				}*/
				
				//apply VNF policy, update the nodes in conflict start exec time and order value, update successors node to the nodes in conflict by updating their arrival time, startsched and order value
				tempNode = ((PlayerNode) m).applyVNFPolicy(nodesConflict);
			
				//@important before re-ordering moves, we need to switch m with the node that was first scheduled based on the policy to make sure when we re-order we do not skip checking any move
				strategyMoves.set(this.searchMove(strategyMoves,tempNode),(PlayerNode) m);
				strategyMoves.set(i, tempNode);
				
				//order the moves again after doing the update once the conflict is solved (i+1 will be changed since the order in the strategy moves is changed)
				this.orderMoves(strategyMoves, i);
				
				
				/*System.out.println("Order After Applying policy==="+i);
				for(int j=0; j<strategyMoves.size();j++)
				{
					System.out.println(strategyMoves.get(j));
				}*/
					
			}
			/*else if (m instanceof PlayerLink)
			{//System.out.println("==================Link Conflict with  "+m);
				//get the list of links that are sharing at least one physical link with l
				linksConflict = ((PlayerLink)m).identifyLinkConflict(strategyMoves,i, false);
				
				if (linksConflict == null)
				{
					//if no conflict reserve bandwidth for the link
					((PlayerLink)m).reserveBandwdith(physicalLinks, -1);
					//System.out.println("No link conflict"+physicalLinks);
					continue;
				}*/
				/*System.out.println("Conflict Links===");
				for(int k=0; k<linksConflict.size(); k++)
				{
					System.out.print ("("+linksConflict.get(k).getId()+"-"+linksConflict.get(k).getPGraph().getPlayer().getPid()+") ;");
				}*/
				
				/*tempLink =((PlayerLink) m).applyLinkPolicy(linksConflict,physicalLinks);
				//System.out.println("After policy"+physicalLinks);
				
				//@important before re-ordering moves, we need to switch m with the node that was first scheduled based on the policy to make sure when we re-order we do not skip checking any move
				strategyMoves.set(this.searchMove(strategyMoves,tempLink), (PlayerLink) m);
				strategyMoves.set(i, tempLink);
							
				//order the moves again after doing the update once the conflict is solved (i+1 will be changed since the order in the strategy moves is changed)
				this.orderMoves(strategyMoves, i);
				/*System.out.println("Order After Applying policy==="+i);
				for(int j=0; j<strategyMoves.size();j++)
				{

					System.out.println(strategyMoves.get(j));
				}*/
				
			//}
			
		}
	}
	
	
	/**
	 * This method will check if the nash equilibrium is reached by checking if 2 consecutive strategies for each of the playe were equal:
	 * That is the strategy path is the same (same nodes and links regardless of weight, arrival etc. and that the strategies have the same completion time for each player
	 * 
	 * Checking only completion time for equilibrium is not enough as players may change their strategy but keep the same completion time
	 * 
	 * @param previousPlayerStrategies array list of strategies chosen by the players at previous iteration
	 * @param currentPlayerStrategies current players strategies ( = player.strategy)
	 * @param criteria = completionTime or avgCompletionTime, or payOff (min|avgCompleionTimeofPlayers(N-1)-CompletionTime(player N)|)based if we are checking on nash for completion or avgCompletion,twoConsecutiveAvgCompletionTime
	 * @param nashValue if 0 then we are checking for nash, if not then we checking for e-nash
	 * @return true if nash is reached
	 */
	public boolean isNashEquilibriumReached(ArrayList<Strategy>previousPlayerStrategies, ArrayList<Strategy>currentPlayerStrategies, int iteration, String criteria, double nashValue)
	{//System.out.println("size"+previousPlayerStrategies.size()+" iteration "+iteration);
		if (iteration ==1)
		{
			return false;
		}
		
		//checking nash for all players = number of strategiess
		for (int i=0; i<previousPlayerStrategies.size(); i++)
		{
			
			if (criteria.equals("completionTime"))
			{
				if (Math.abs(previousPlayerStrategies.get(i).getCompletionTime() - currentPlayerStrategies.get(i).getCompletionTime())>nashValue)
				{
					return false;
				}
			}
			else if (criteria.equals("avgCompletionTime"))
			{
				if (Math.abs(previousPlayerStrategies.get(i).avgCompletionTime-currentPlayerStrategies.get(i).avgCompletionTime)>nashValue)
				{
					return false;
				}
			}
			
			else if (criteria.equals("payOff"))
			{
				if (Math.abs(previousPlayerStrategies.get(i).payOff-currentPlayerStrategies.get(i).payOff)>nashValue)
				{
					return false;
				}
			}
			//mainly used for e-nash when the game does not stop
			else if (criteria.equals("twoConsecutiveAvgCompletionTime") )
			{
				//iteration<3 because after 3 iterations we can have 2 valuew for twoConsecutiveAvgCompletionTime to compare
				if ( iteration<3)
				{
					return false;
				}
				if (Math.abs(this.players.get(i).twoConsecutiveAvgCompletionTime.get(this.players.get(i).twoConsecutiveAvgCompletionTime.size()-1)-this.players.get(i).twoConsecutiveAvgCompletionTime.get(this.players.get(i).twoConsecutiveAvgCompletionTime.size()-2))>nashValue)
				{
					return false;
				}
			}
			
		}
		 return true;
	}
	
	
	/**
	 * This method is used to calculate the avg completion time of all players excluding player p.
	 * The calculation is based one the sum of Completion time of players / number of players in the game-1( excluding p if p is not null)
	 * This is used to calculate the payoff of player p
	 *  
	 * @param p player player for which we want to calculate the payoff, player to exclude from the avg completion time calculation 
	 */
	public double calculatePlayersAvgCompletionTime(Player p)
	{
	
		double playersAvgCompletionTime =0;
		int totalPlayers = p==null?this.players.size():this.players.size()-1;
	
		for (int i=0; i<this.players.size(); i++)
		{
			if (this.players.get(i).getStrategy() == null || this.players.get(i).equals(p))
			{
				continue;
			}
			
			playersAvgCompletionTime+=this.players.get(i).getStrategy().getCompletionTime();
			
		}
		
		playersAvgCompletionTime = playersAvgCompletionTime/totalPlayers;
		
		return playersAvgCompletionTime;
	}
	
	
	/**
	 * Done
	 * @return
	 */
	public ArrayList<Strategy> getPlayersStrategies ()
	{
		ArrayList<Strategy> strategies = new ArrayList<Strategy>();
		for(int i=0; i<this.players.size(); i++)
		{
			strategies.add(this.players.get(i).getStrategy());
		}
		return strategies;
	}
	
	/**
	 * 
	 * @return
	 * @throws Exception
	 */
	public ArrayList<Strategy> playIteration(int iteration) throws Exception
	{
		Player p = null;
		double startTime = 0;
		double endTime = 0;
		double execTime =0;
		ArrayList<Strategy> strategies = new ArrayList<Strategy>();
		ArrayList<Player> playersWhoPlayed = new ArrayList<Player>();
		ArrayList<Move> strategiesMoves = null;
		for(int i=0; i<this.players.size(); i++)
		{	
			p=this.players.get(i);
		
			//Player updates its graph before playing without setting the completionTime/AvgCompletionTime/payeoff per iteration. 
			//Those are set at the end of the iteration 		
			p.play(false, playersWhoPlayed);		
		
			//add p to the players who played so his strategy is considered by the  next player
			playersWhoPlayed.add(p);					
			
			/**
			 * Update the completion time of the strategies of the players before the next one plays, so he can choose his strategy based on the update avg completion time
			 */
			//get list of strategy moves for the players who chose their strategies during the current iteration (sequential game)
			//we do not want to identify conflict with strategies selected in the previous iteration
			strategiesMoves = this.getMoveList(playersWhoPlayed);
			
			//The player should update his graph based on the updated strategies of the other players who played. 
			// here we consider that the player knows their strategy using it to update its graph. To use it he needs to figure out the updated strategy of each 
			// as we only update all of them before each player plays
			
			this.identifySolveConflicts(strategiesMoves);
		
			//update players completionTime and payOff. No need to update their graphs because it will not be used unless they want to select a strategy
			this.updatePlayersValues(false);
			
			strategiesMoves =null;	
			p=null;
			 
		}
		
		
		//when all players played and their strategies are updated accordingly, we need to update their payoff/completion time;Avg Completion time; pay off per iteration
		this.updatePlayersValues(true);
		
		strategies = this.getPlayersStrategies();
		return strategies;
	}
	
	
	/**
	 * This method updates the values for the player (payoff, completion time, avg completion time etc
	 * @param updatePerIterationValues if set to true it will update the completion time;Avg Completion time; pay off per iteration for the players
	 */
	public void updatePlayersValues(boolean updatePerIterationValues)
	{
		for(int i=0; i<this.players.size(); i++)
		{	
			this.players.get(i).update(updatePerIterationValues);
		}
	}

	/**
	 * this method resets the strategies of the players to null
	 */
	public void resetPlayersStrategies()
	{
		for (int i=0; i<this.players.size(); i++)
		{
			this.players.get(i).setStrategy(null);
		}
	}
	
	/**
	 * This function allows players to play sequentially.
	 *  A player chooses a strategy and communicates it to the others to update their graph. 
	 *  Timeline is applied when each players plays on the player who have chosen a strategy
	 *  
	 * @param resultsfile file name to write up the results
	 * @param criteria criteria = completionTime or avgCompletionTime, or payOff=min difference btw vg completion time of all players and the player playing
	 * @param nashValue value related to the criteria 
	 * @param eNashValue value of nash to be considered when comparing for twoConsecutiveAvgCompletionTime to handle game with repeated values
	 * @return list of admitted services
	 * @throws Exception
	 */
	public ArrayList<Service> sequentialPlay(String resultsfile, String criteria, double nashValue, double eNashValue) throws Exception
	{
		int iteration =0;
		double admissionRate = 0;
		boolean nashEquilibriumReached = false;
		boolean e_nashEquilibriumReached = false;
		ArrayList<Strategy> previousPlayerStrategies = new ArrayList<Strategy>();
		ArrayList<Strategy> currentPlayerStrategies = new ArrayList<Strategy>();
		ArrayList<Double> admissionPerIteration = new ArrayList<Double>();
		ArrayList<Service>admittedServices = new ArrayList<Service>();
		String stoppingCondition ="Nash reached";
		FileManipulation f = new FileManipulation(resultsfile);
		
		while ( true )//nash not reached  
		{		
			if (admissionRate==100 && iteration>=2)
			{
				break;
			}
			if (nashEquilibriumReached)
			{
				//break;
			}
			
			/*if (e_nashEquilibriumReached)
			{
				stoppingCondition="e-nash (Two Consecutive Average Completion Time)";
				break;
			}*/
			if (this.pingPong(criteria))
			{
				stoppingCondition="PING PONG";
				break;				
			}
			
			iteration++;
			
			System.out.println ("iteration:"+iteration);

			//reset players strategies to null for the next iteration
			//this.resetPlayersStrategies();
			
	
			
			if (iteration >300)//500
			{
				iteration--; //to handle error in reportResults since the ct per iteration is of size 500-1
				stoppingCondition="Iterations exceed 25";//500
				break;
			}
			
			
			currentPlayerStrategies = this.playIteration(iteration);
				
			nashEquilibriumReached = this.isNashEquilibriumReached(previousPlayerStrategies, currentPlayerStrategies, iteration,criteria,nashValue);
			e_nashEquilibriumReached= this.isNashEquilibriumReached(previousPlayerStrategies, currentPlayerStrategies, iteration,"twoConsecutiveAvgCompletionTime", eNashValue);
		
			//update previous strategies and current strategies for the next iteration
			previousPlayerStrategies = currentPlayerStrategies;
			currentPlayerStrategies = new ArrayList<Strategy>();
			
			admissionRate = this.getNbAdmittedPlayers(previousPlayerStrategies)*100/this.players.size();
			admissionPerIteration.add(admissionRate);		
		
		}
		
		f.writeInFile(this.reportResults(iteration, stoppingCondition, admissionPerIteration,nashValue,eNashValue));
		
		
		//admission  at last iteration
		//admittedServices = this.getAdmittedServices(previousPlayerStrategies);//previous strategy because at the last iteration the previousPlayerStrategies = currentPlayerStrategies;
		
		//max admission
		double [] maxAdmission = getMaxAdmission(admissionPerIteration);
		admittedServices = this.getAdmittedServices((int)maxAdmission[1]);
		
		return admittedServices;
	}
	
	/**
	 * This method performs the play where all the players play simultaniously by choosing a strategy and putting them on a timeline all together 
	 * @param resultFile file to write up the results
	 * @param criteria criteria = completionTime or avgCompletionTime, or payOff=min difference btw vg completion time of all players and the player playing
	 * @param nashValue value related to the criteria 
	 * @param eNashValue value of nash to be considered when comparing for twoConsecutiveAvgCompletionTime to handle game with repeated values
	 * @return array list of admitted services
	 * @throws Exception
	 */
	public ArrayList<Service> simultaniousPlay(String resultFile, String criteria,double nashValue, double eNashValue) throws Exception
	{
		FileManipulation f = new FileManipulation(resultFile);
		boolean nashEquilibriumReached = false;
		boolean e_nashEquilibriumReached = false;
		int iteration =1;//start from 1 as 0 is the preparation
		boolean [] visited = null;
		PlayerNode currentNode = null;
		
		ArrayList<Strategy> previousPlayerStrategies = new ArrayList<Strategy>();
		ArrayList<Strategy> currentPlayerStrategies = new ArrayList<Strategy>();
		ArrayList<Service> admittedServices = new ArrayList<Service>();
		
		Player p=null;

		ArrayList<Double> admissionPerIteration = new ArrayList<Double>();
		double admissionRate = 0;
		String stoppingCondition ="Nash reached";
		
		
		//start by building a strategy for all players
		for (int i=0; i<this.players.size(); i++)
		{			
			p= this.players.get(i);
			p.buildStrategy(this.type);
		}
		
		while ( true )//nash not reached&& iteration <2
		{	
			if (nashEquilibriumReached)
			{
				break;
			}
			
			if (e_nashEquilibriumReached)
			{
				stoppingCondition="e-nash (Two Consecutive Average Completion Time)";
				break;
			}
			
			if (this.pingPong(criteria))
			{
				stoppingCondition="PING PONG";
				break;				
			}
			
			if (iteration >1000)
			{
				stoppingCondition="Iterations exceed 1000";
				break;
			}
			/*if (admissionRate == 100)
			{
				stoppingCondition="All players are admitted";
				break;
			}*/
			
			
			System.out.println("=============iteration "+(iteration));
			
			
			admissionRate =0;//reset
			ArrayList<Move> strategiesMoves = this.getMoveList(null);
			
			this.identifySolveConflicts(strategiesMoves);
			for (int i=0; i<this.players.size(); i++)
			{			
				p= this.players.get(i);
				//update the completion time of the players strategy after solving the conflict
				p.getStrategy().updateCompletionTime();
				p.getStrategy().updateAvgCompletionTime();
			
				 visited = new boolean [p.getPgraph().getNodeNb()];
				 currentNode = (PlayerNode) p.getPgraph().getNodeList()[0];
				
				//update cost of the moves that are not in the strategy
				p.getPgraph().updateCost(0, visited,currentNode, strategiesMoves);
				 currentPlayerStrategies.add( p.getStrategy());
				
				 //save the completion time of each strategy at each iteration
				p.completionTimeByIteration.add(p.getStrategy().getCompletionTime());
				p.avgCompletionTimeByIteration.add(p.getStrategy().avgCompletionTime);
				
				//set the previous strategy to null and build a new one
				//send the avgCompletonTime of the previous iteration because we creating a new strategy (to be able to calculate the new avg completion time)
				 p.buildStrategy(this.type);
				
										 
				 //reset
				 visited = null;
				 currentNode = null;				
			 }
			
			admissionRate = this.getNbAdmittedPlayers(currentPlayerStrategies)*100/this.players.size();
			admissionPerIteration.add(admissionRate);
			e_nashEquilibriumReached= this.isNashEquilibriumReached(previousPlayerStrategies, currentPlayerStrategies, iteration,"twoConsecutiveAvgCompletionTime", eNashValue);
			nashEquilibriumReached = this.isNashEquilibriumReached(previousPlayerStrategies, currentPlayerStrategies, iteration,criteria,nashValue );
			if (iteration >=1)
			{
				previousPlayerStrategies = currentPlayerStrategies;
				currentPlayerStrategies = new ArrayList<Strategy>();
			
			}
		
			iteration++;		
		}	
		
		admittedServices = this.getAdmittedServices(previousPlayerStrategies);//previous strategy because at the last iteration the previousPlayerStrategies = currentPlayerStrategies;
		f.writeInFile(this.reportResults(--iteration, stoppingCondition, admissionPerIteration, nashValue, eNashValue));
		
		return admittedServices; //--iteration;
		
	}
	
	
	/**
	 * get the number of admitted players 
	 * A player is admitted if its deadline is met
	 * 
	 * @return number of admitted players
	 */
	public int getNbAdmittedPlayers (ArrayList<Strategy>playersStrategies)
	{
		int admitted = 0;
		for (int i=0; i<playersStrategies.size(); i++)
		 {
			if (playersStrategies.get(i).getCompletionTime()<=this.players.get(playersStrategies.get(i).getPlayerId()).getService().getDeadlineTime())
			{
				admitted++;
			}
		 }
		return admitted;
	}
	
	/**
	 * Computes a list of admitted services 
	 * A player is admitted if its deadline is met
	 * 
	 * @return a list of admitted services 
	 */
	public ArrayList<Service> getAdmittedServices (ArrayList<Strategy>playersStrategies)
	{
		 ArrayList<Service> admittedServices = new ArrayList<Service>();
		for (int i=0; i<playersStrategies.size(); i++)
		 {
			if (playersStrategies.get(i).getCompletionTime()<=this.players.get(playersStrategies.get(i).getPlayerId()).getService().getDeadlineTime())
			{
				admittedServices.add(this.players.get(playersStrategies.get(i).getPlayerId()).getService());
			}
		 }
		return admittedServices;
	}
	
	/**
	 * Computes a list of admitted services 
	 * A player is admitted if its deadline is met
	 * @param iteration iteration at which we want to get the admitted services
	 * @return a list of admitted services 
	 */
	public ArrayList<Service> getAdmittedServices (int iteration)
	{
		 ArrayList<Service> admittedServices = new ArrayList<Service>();

		 
		for (int i=0; i<players.size(); i++)
		 {
			
			if (this.players.get(i).completionTimeByIteration.get(iteration)<=this.players.get(i).getService().getDeadlineTime())
			{
				admittedServices.add(this.players.get(i).getService());
			}
		 }
		return admittedServices;
	}
	
	
	/**
	 * This method verifies if we have a ping pong situation
	 * A ping pong situation happens when at the completion times of all player at iteration 1 are the same as in iteration 3 and those at iteration 2 are the same as iteration 4
	 * criteria = completionTime or avgCompletionTime, based if we are checking on nash for completion or avgCompletion
	 * @return true if ping pong or number of ietrations<4 
	 */
	public boolean pingPong(String criteria)
	{
		Player p=null;
		int totalIterations = this.players.get(0).completionTimeByIteration.size();
		//if we have less than 4 iterations, we can not identify any ping pong so we return true
		if (totalIterations<4)
		{
			return false;
		}
		
		for (int i=0; i<this.players.size(); i++)
		{
			p= this.players.get(i);
			
			if (criteria.equals ("completionTime"))
			{
				if (p.completionTimeByIteration.get(p.completionTimeByIteration.size()-1)!=p.completionTimeByIteration.get(p.completionTimeByIteration.size()-3))
				{
					return false;
				}
	
				if (p.completionTimeByIteration.get(p.completionTimeByIteration.size()-2)!=p.completionTimeByIteration.get(p.completionTimeByIteration.size()-4))
				{
					return false;
				}	
			}
			else if (criteria.equals ("avgCompletionTime"))
			{
				if (p.avgCompletionTimeByIteration.get(p.avgCompletionTimeByIteration.size()-1)!=p.avgCompletionTimeByIteration.get(p.avgCompletionTimeByIteration.size()-3))
				{
					return false;
				}
	
				if (p.avgCompletionTimeByIteration.get(p.avgCompletionTimeByIteration.size()-2)!=p.avgCompletionTimeByIteration.get(p.avgCompletionTimeByIteration.size()-4))
				{
					return false;
				}	
			}
			else if (criteria.equals ("payOff"))
			{
				if (p.payOffByIteration.get(p.payOffByIteration.size()-1)!=p.payOffByIteration.get(p.payOffByIteration.size()-3))
				{
					return false;
				}
	
				if (p.payOffByIteration.get(p.payOffByIteration.size()-2)!=p.payOffByIteration.get(p.payOffByIteration.size()-4))
				{
					return false;
				}	
			}
		}
		
		return true;
	}
	
	
	/**
	 * Computes the average admission rate by averaging the admission rate per iteration
	 * @param admissionPerIteration array list of admission rate per ietration
	 * @return double average admission rate
	 */
	public double getAverageAdmissionRate(ArrayList<Double>admissionPerIteration)
	{
		double avgAdmissionRate =0;
		for (int i=0; i<admissionPerIteration.size(); i++)
		{
			avgAdmissionRate+=admissionPerIteration.get(i);
		}
		avgAdmissionRate= avgAdmissionRate/admissionPerIteration.size();
		
		return avgAdmissionRate;
	}
	
	/**
	 * Computes the max admission rate per all iterations
	 * @param admissionPerIteration array list of admission rate per iteration
	 * @return double[] max admission rate, iteration at which we got the max admission
	 */
	public double[] getMaxAdmission(ArrayList<Double>admissionPerIteration)
	{	double iterationWithMaxAdmission =0;
		double maxAdmissionRate =0;
		for (int i=0; i<admissionPerIteration.size(); i++)
		{
			if (admissionPerIteration.get(i)>maxAdmissionRate)
			{
				maxAdmissionRate=admissionPerIteration.get(i);
				iterationWithMaxAdmission = i;
			}
		}
		double [] results = {maxAdmissionRate,iterationWithMaxAdmission };
		return results;
	}
	
	/**
	 * This method will return a string with the results of the game
	 * @param iteration nb of iteration required by the game
	 * @param stoppingCondition condition on which the game stopped (nash, ping pong, admission, iteration>1000)
	 * @param admissionPerIteration admission rate at each iteration
	 * @return string with the results
	 */
	public String reportResults (int iteration, String stoppingCondition, ArrayList<Double>admissionPerIteration, double nashValue, double eNahsValue )
	{
		double [] maxAdmission = getMaxAdmission(admissionPerIteration);
		String s ="";
		String s2 ="";
		String s3 ="";
		String s4="";
		String s5="";
		String s6="";
		s+=" Strategy Type:\t"+this.type+" ";
		s+=" NashValue:\t"+nashValue+" ";
		s+=" e_NashValue for twoConsecutiveAvgCompletionTime :\t"+eNahsValue+" ";
		s+="Total Number of players =\t"+this.players.size()+"\n";
		s+="Total Iterations =\t"+iteration+"\n";
		s+="Stopping Condition =\t"+stoppingCondition+"\n";
		s+="Final Admission Rate- used IF NASH:\t"+admissionPerIteration.get(admissionPerIteration.size()-1)+"\n";
		s+="Max Admission Rate- can be used IF stop at certain iteration:\t"+maxAdmission[0]+"\n";
		s+="iteration at which we got Max Admission Rate - can be used IF stop at certain iteration:\t"+maxAdmission[1]+"\n";
		s+="Average Admission Rate:\t"+this.getAverageAdmissionRate(admissionPerIteration)+"\n";
		s+="Admission rate per iteration:\t"+admissionPerIteration.toString()+"\n";
		s+="Players completion time per iteration\n";
		for (int i=0; i<this.players.size(); i++)
		{
			s+=this.players.get(i).getPid()+"\t"+this.players.get(i).completionTimeByIteration.toString()+"\n";
			s2+=this.players.get(i).getPid()+"\t"+this.players.get(i).avgCompletionTimeByIteration.toString()+"\n";
			s3+=this.players.get(i).getPid()+"\t"+this.players.get(i).payOffByIteration.toString()+"\n";
			s5+= this.players.get(i).getPid()+"\t"+this.players.get(i).twoConsecutiveAvgCompletionTime.toString()+"\n";
			s4+= this.players.get(i).completionTimeByIteration.get((int)maxAdmission[1])+"\t";
			s6+= this.players.get(i).twoConsecutiveAvgCompletionTime.get(iteration-2)+"\t";//ite-2 because we have 1 value less than nb of iterations for twoConsecutiveAvgCompletionTime
		}
		
		s+="Players Average completion time per iteration\n";
		s+=s2+"\n\n";
		s+="Players PayOff per iteration\n";
		s+=s3+"\n\n";
		s+="Players twoConsecutiveAvgCompletionTime per iteration\n";
		s+=s5+"\n\n";
		s+="Players Completion time at iteration with max admission\n";
		s+=s4+"\n\n";
		s+="Players twoConsecutiveAvgCompletionTime at last iteration\n";
		s+=s6+"\n\n";
		return s;
	}
	
	/**
	 * This method is only used for some extra testing purposes to tune the value of alpha for the calculation of the average 
	 * completion time
	 * 
	 * @param completionTime array list of completion time
	 * @param alpha apha for average completion time calculation
	 * 
	 */
	public void tuneAlpha (ArrayList<Integer>completionTime, double alpha )
	{	
		ArrayList<Double>avgCompletionTime = new ArrayList<Double>();
		avgCompletionTime.add((1-alpha)*completionTime.get(0));
		for (int i=1; i<completionTime.size(); i++)
		{
			avgCompletionTime.add( alpha*avgCompletionTime.get(i-1)+(1-alpha)*completionTime.get(i));
		}
		System.out.println (avgCompletionTime.toString());
	}
	
	public static void main (String [] args) throws Exception
	{
		//Network network = new Network(6,4,5,5,1000,1000,1,0);
		Network network = new Network(7,5,5,5,500,500,1,0);
		ArrayList<int[][]> physicalNetwork = network.buildPhysicalNetwork();
		int [] tf = physicalNetwork.get(2)[0];

		System.out.println(network);
		
		//ServicesDriver driver = new ServicesDriver (4, 100, 3,4, 300,500, 5 , 1, 500, 1500,tf);
			//ArrayList<Service>services = driver.generateServices(20.0,10.0, 100, 13);
			
			 
			 ServicesDriver driver = new ServicesDriver (4,  300, 3,5, 300,500, 5 , 1, 500, 1500,tf);
			 ArrayList<Service>services = driver.generateServices(20.0,10.0, 100, 33);
			 //services.remove(0);

				 
			Game g = new Game(network, services,StrategyType.MIXEDSTRATEGY , 3);//StrategyType.PLAYERS_AVG_COMPLETIONTIME; StrategyType.SHORTEST_PATH
			String resultsfile = "testResults/game.txt";
			g.sequentialPlay(resultsfile , "payOff",0,0.5);
			
			for (int i=0; i<g.players.size(); i++)
			{
				System.out.println (g.players.get(i));
			}
		
			
	}
}
