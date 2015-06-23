/*	GetScores
	Calculation of scores, based on several similarity metrics
	
	Gregory Gay (greg@greggay.com)
	Last Updated: 06/10/2014
	 	- Change: No longer requires traces to be same # steps.
	05/12/2014
		- Initial file creation.
		- Calculates Squared Euclidean distance metric.
		- Calculates Manhattan distance metric.

	README:
		- Assumes data has already been filtered to the oracle data set variables.
		- Assumes that variables are the same in the oracle and SUT
		- Also assumes that the first line of both traces contains a variable list.
		- WorkerFunctions has the functionality to produce traces of the correct format.
*/

package steering;

import java.util.ArrayList;
import java.util.HashMap;

public class GetScores {
	private SteerModel steerer;
	
	public GetScores(SteerModel st){
		steerer = st;
	}
	
	
	// Entry method, chooses the appropriate calculation based on current setting in the Steerer.
	public ArrayList<Double> calculate(ArrayList<String> oracle, ArrayList<String> sut) throws Exception{
		ArrayList<Double> scores;
		// Do some basic error checking on the size.
		int olength = oracle.get(0).split(",").length;
		int sutlength = sut.get(0).split(",").length;
		if(olength != sutlength){
			throw new SteeringDataException("Number of variables in the traces do not match: "+olength+","+sutlength);
		}
		/*olength = oracle.size();
		sutlength = sut.size();
		if(olength != sutlength){
			throw new SteeringDataException("Number of test steps in the traces do not match: "+olength+","+sutlength);
		}*/
		
		// Select appropriate method.
		String metric = steerer.getMetric();
		if(metric.toLowerCase().equals("manhattan")){
			scores = calculateManhattan(oracle,sut);
		}else if(metric.toLowerCase().equals("sqeuclid")){
			scores = calculateSqEuclid(oracle,sut);
		}else{
			throw new SteeringException("Unsupported metric: "+metric);
		}
		
		return scores;
	}
	
	// Calculates result of the Manhattan (city block) distance metric.
	public ArrayList<Double> calculateManhattan(ArrayList<String> oracle, ArrayList<String> sut) throws Exception{
		ArrayList<Double> scores = new ArrayList<Double>();
		String[] header=oracle.get(0).split(",");
		
		int maxRecord = Math.max(oracle.size(), sut.size());
		
		for(int record = 1; record< maxRecord; record++){
			String[] oRecord = null;
			String[] sRecord = null;
			
			if(record<oracle.size()){
				oRecord = oracle.get(record).split(",");
			}
			if(record<sut.size()){
				sRecord = sut.get(record).split(",");
			}
			
			double score = 0;
			
			if(oRecord==null || sRecord==null){
				scores.add(10000.0);
			}else{
				for(int var = 0; var<oRecord.length; var++){
					String variable = header[var];
					String type = steerer.getModel().getTypeMap().get(variable);

					if(oRecord[var].toLowerCase()=="nan"){
						oRecord[var]="0";
					}else if(sRecord[var].toLowerCase()=="nan"){
						sRecord[var]="0";
					}

					double oVal = Double.parseDouble(oRecord[var]);
					double sVal = Double.parseDouble(sRecord[var]);

					// Normalize if imported.
					HashMap<String,HashMap<String,Double>> normVals = steerer.getNormalization();	
					if(normVals!=null){
						if(!type.equals("bool")){
							double minV = normVals.get(variable).get("min");
							double maxV = normVals.get(variable).get("max");
							oVal = (oVal-minV)/(maxV-minV);
							sVal = (sVal-minV)/(maxV-minV);
						}
					}

					if(!type.equals("bool")){
						score+=Math.abs(oVal-sVal);
					}else{
						if(oVal!=sVal){
							score+=1;
						}
					}
				}

				scores.add(score);
			}
		}
				
		return scores;
	}
	
	// Calculates result of the Squared Euclidean distance metric
	public ArrayList<Double> calculateSqEuclid(ArrayList<String> oracle, ArrayList<String> sut) throws Exception{
		ArrayList<Double> scores = new ArrayList<Double>();
		String[] header=oracle.get(0).split(",");

		int maxRecord = Math.max(oracle.size(), sut.size());

		for(int record = 1; record< maxRecord; record++){
			String[] oRecord = null;
			String[] sRecord = null;

			if(record<oracle.size()){
				oRecord = oracle.get(record).split(",");
			}
			if(record<sut.size()){
				sRecord = sut.get(record).split(",");
			}

			double score = 0;

			if(oRecord==null || sRecord==null){
				scores.add(10000.0);
			}else{
				for(int var = 0; var< oRecord.length; var++){
					String variable = header[var];
					String type = steerer.getModel().getTypeMap().get(variable);

					if(oRecord[var].toLowerCase()=="nan"){
						oRecord[var]="0";
					}else if(sRecord[var].toLowerCase()=="nan"){
						sRecord[var]="0";
					}

					double oVal = Double.parseDouble(oRecord[var]);
					double sVal = Double.parseDouble(sRecord[var]);

					// Normalize if imported.
					HashMap<String,HashMap<String,Double>> normVals = steerer.getNormalization();	
					if(normVals!=null){
						if(!type.equals("bool")){
							double minV = normVals.get(variable).get("min");
							double maxV = normVals.get(variable).get("max");
							oVal = (oVal-minV)/(maxV-minV);
							sVal = (sVal-minV)/(maxV-minV);
						}
					}

					if(!type.equals("bool")){
						double dist = oVal - sVal;
						score+=(dist*dist);
					}else{
						if(oVal!=sVal){
							score+=1;
						}
					}
				}

				scores.add(score);
			}
		}

		return scores;
	}
	
	public void setSteerer(SteerModel st){
		steerer = st;
	}
	
	public SteerModel getSteerer(){
		return steerer;
	}
}
