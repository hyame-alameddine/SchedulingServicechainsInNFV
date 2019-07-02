package HelperClasses;

import java.io.IOException;
import java.util.Random;

public class Poisson {
	public double[] arrivals;
	public double[] departures;
	//arrival rate
	public double lambda;
	
	//departure rate
	public double mu;
	
	/**
	 * 
	 * @param lambda arrival rate er unit time
	 * @param mu departure rate
	 * @param nbRequests number of requests
	 * @param fileName where to print the poisson information
	 * @throws IOException 
	 */
	public Poisson(double lambda,double mu, int nbRequests) throws IOException
	{
		this.lambda = lambda;
		this.mu = mu;
		this.arrivals = generatePoissonArrival(nbRequests);
		this.departures = generatePoissonDeparture( nbRequests, arrivals);
			
	}

	public double[] generatePoissonArrival(int nbRequests){

		double [] arrival = new double[nbRequests];
		Random generator = new Random();
		generator.setSeed (3);//added for periodicOnlineCG
		//set the first arrival time to 0
		arrival[0] = 0.0;
		for(int i=1;i<nbRequests;i++){
			double t0 = arrival[i-1];
			double u = generator.nextFloat();
			if(u > 0){				
				double t = t0 - ((1/this.lambda)*Math.log(u));
				arrival[i] = t;
			}
		}
		return arrival;
	}

	public double[] generatePoissonDeparture( int nbRequests,double[] arrival){
		
		Random generator = new Random();
		double[] departure = new double[nbRequests];
		generator.setSeed (59);
		for(int i=0;i<arrival.length;i++){
			double u = generator.nextFloat();
			if(u > 0){
				double d = arrival[i] - ((1/this.mu)*Math.log(u));
				departure[i] = d;
			}
		}
		return departure;
	}
	
	
	/**
	 * This function returns a string of poisson distribution load 
	 * Arrivals and departures
	 * 
	 * @return String
	 */
	public String toString ()
	{
		String poissonInfo= " ";

		poissonInfo +="\n------------------------------------------------------------ Poisson distribution with | load :"+ this.lambda/this.mu+" | Arrival Rate = "+this.lambda+" | Departure Rate = "+this.mu+"----------------------------------------------------------------------\n";
		
		poissonInfo +="public double[] arrivals = {";
		for(int i=0;i<arrivals.length;i++){
			poissonInfo +=arrivals[i]+",";
		}
		poissonInfo+=" }; \n";		
		
		poissonInfo+="public double[] departures = {";
		for(int i=0;i<departures.length;i++){
			poissonInfo+= departures[i]+",";
		}
		poissonInfo+= "};\n";
		poissonInfo +=" -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n\n";
		return poissonInfo;
	}

}