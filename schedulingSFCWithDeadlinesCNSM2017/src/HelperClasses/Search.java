package HelperClasses;

public class Search {


	/**
	 * This function search for an int in an array
	 * @param x
	 * @param list
	 * @return
	 */
	public static boolean exists (int x, int[] list)
	{
		for (int i=0; i<list.length; i++)
		{
			if (list[i] == x)
			{
				return true;
			}
		}
		
		return false;
	}
}
