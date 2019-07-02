/**
 * This interface is needed to be able to represent a player node and player edge as the same object in order to handle their ordering based on arrival time
 */
package GameTheory;

public interface Move {

	public int getExecutionTime();
	public int getStartExecutionTime();
	public int getArrivalTime();
	public int getOrderValue();
	public void setExecutionTime(int executionTime);
	public void setStartExecutionTime(int startExecutionTime);
	public void setArrivalTime(int arrivalTime);
	public void setOrderValue(int orderValue);
}
