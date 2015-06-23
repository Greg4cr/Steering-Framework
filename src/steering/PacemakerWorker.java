/*	PacemakerFunctions
	Miscellaneous Functionality for Oracle Steering, 
	specific to the Pacemaker model.
	
	Gregory Gay (greg@greggay.com)
	Last Updated: 06/18/2014
	 	- Added method to clear all non-concrete steps (for result check)
	06/12/2014
		- Fixed an overlap bug with the step insertion.
	05/28/2014
	 	- Bug fixes
	05/13/2014
		- insertion of "no event" steps.
	05/12/2014
		- Initial file creation
		- Clear blanks function

*/

package steering;

import java.util.ArrayList;
import java.util.Arrays;

public class PacemakerWorker extends WorkerFunctions{

	public PacemakerWorker(SteerModel st) {
		super(st);
	}
	
	// Quick little function to format a String array to a concatenated string
	public String concatStringArray(String[] array){
		String concat = "";
		
		for(String word: array){
			concat=concat+word+",";
		}
		
		concat=concat.substring(0,concat.length()-1);
		return concat;
	}
	
	// Checks for whether or not to insert a "no event" step and 
	// inserts if appropriate.
	public ArrayList<String> insertNoEvent(ArrayList<String> inputs, ArrayList<String> trace, String insertStep, int step) throws Exception{
		double nextConcrete=-1.0;
		int nI = Arrays.asList(inputs.get(0).split(",")).indexOf("IN_EVENT_TIME");
		String[] toAdd = insertStep.split(",");
		String[] traceStep;
		String[] nextStep = null;
				
		int nA = Arrays.asList(trace.get(0).split(",")).indexOf("OUT_NEXT_A");
		int nV = Arrays.asList(trace.get(0).split(",")).indexOf("OUT_NEXT_V");
		int cI = Arrays.asList(trace.get(0).split(",")).indexOf("IN_EVENT_TIME");
		int iA = Arrays.asList(trace.get(0).split(",")).indexOf("IN_A_EVENT");
		int iV = Arrays.asList(trace.get(0).split(",")).indexOf("IN_V_EVENT");

		if(step+1<trace.size()){
			traceStep = trace.get(step+1).split(",");
		}else{
			throw new SteeringDataException("This step does not exist in the trace: "+step+", max: "+(trace.size()-1));
		}
		if(step+2<inputs.size()){
			nextStep = inputs.get(step+2).split(",");
			
			if((nextStep[iA].equals("0")&&nextStep[iV].equals("0"))&&(step+3<inputs.size())){
				String[] nextStep2 = inputs.get(step+3).split(",");
				if(nextStep2[iA].equals("1")||nextStep2[iV].equals("1")){
					//System.out.println("d1");
					nextConcrete = Double.parseDouble(nextStep2[nI]);
				}else{
					//System.out.println("d2");
					nextConcrete = Double.parseDouble(nextStep[nI]);
				}	
			}else{
				//System.out.println("d3");
				nextConcrete = Double.parseDouble(nextStep[nI]);
			}
		}
		
		//System.out.println("next concrete:"+nextConcrete);
		
		double nextA=Double.parseDouble(traceStep[nA]);
		double nextV=Double.parseDouble(traceStep[nV]);
		double currentConcrete=Double.parseDouble(traceStep[cI]);
		
		// If there is no next concrete input
		if(nextConcrete==-1.0){
			// If next V event is before next A event, and before the time limit
			if((nextV < nextA) && (nextV < 3000.0)){
				if(currentConcrete != (nextV-1)){
					toAdd[nI] = Integer.toString((int)nextV-1);
					inputs.add(step+2,this.concatStringArray(toAdd));
					toAdd[nI] = Integer.toString((int)nextV);
					inputs.add(step+3,this.concatStringArray(toAdd));
				}else{
					toAdd[nI] = Integer.toString((int)nextV);
					inputs.add(step+2,Arrays.toString(toAdd));
				}
			// If next A event is before the time limit
			}else if(nextA < 3000.0){
				if(currentConcrete != (nextA-1)){
					toAdd[nI] = Integer.toString((int)nextA-1);
					inputs.add(step+2,this.concatStringArray(toAdd));
					toAdd[nI] = Integer.toString((int)nextA);
					inputs.add(step+3,this.concatStringArray(toAdd));
				}else{
					toAdd[nI] = Integer.toString((int)nextA);
					inputs.add(step+2,this.concatStringArray(toAdd));
				}
			}
		// If the next V event is before the next concrete event
		}else if(nextV < nextConcrete){
			// and before the next A event
			if(nextV < nextA){
				if(currentConcrete != (nextV-1)){
					toAdd[nI] = Integer.toString((int)nextV-1);
					inputs.add(step+2,this.concatStringArray(toAdd));
					toAdd[nI] = Integer.toString((int)nextV);
					inputs.add(step+3,this.concatStringArray(toAdd));
				}else{
					toAdd[nI] = Integer.toString((int)nextV);
					inputs.add(step+2,this.concatStringArray(toAdd));
				}
			// Next A is before the next V (and before concrete)
			}else{
				if(currentConcrete != (nextA-1)){
					toAdd[nI] = Integer.toString((int)nextA-1);
					inputs.add(step+2,this.concatStringArray(toAdd));
					toAdd[nI] = Integer.toString((int)nextA);
					inputs.add(step+3,this.concatStringArray(toAdd));
				}else{
					toAdd[nI] = Integer.toString((int)nextA);
					inputs.add(step+2,this.concatStringArray(toAdd));
				}
			}
		// Next A is before next concrete event
		}else if(nextA < nextConcrete){
			if(currentConcrete != (nextA-1)){
				toAdd[nI] = Integer.toString((int)nextA-1);
				inputs.add(step+2,this.concatStringArray(toAdd));
				toAdd[nI] = Integer.toString((int)nextA);
				inputs.add(step+3,this.concatStringArray(toAdd));
			}else{
				toAdd[nI] = Integer.toString((int)nextA);
				inputs.add(step+2,this.concatStringArray(toAdd));
			}
		}
		
		return inputs;
	}

	// Clears pacing/update steps and leaves only concrete+update pairings. 
	// Assumes existence of input variables
	public ArrayList<String> clearBlanks(ArrayList<String> test, int start){
		ArrayList<String> concreteSteps = new ArrayList<String>(test);
			
		String[] previous=test.get(start+1).split(",");
		String[] thisStep=test.get(start+1).split(",");
		String[] next=null;
		if(start<test.size()-2){
			next =test.get(start+2).split(",");
		}

		int aEvent = Arrays.asList(test.get(0).split(",")).indexOf("IN_A_EVENT");
		int vEvent = Arrays.asList(test.get(0).split(",")).indexOf("IN_V_EVENT");
		int time = Arrays.asList(test.get(0).split(",")).indexOf("IN_EVENT_TIME");
		int deletedRows=0;
		
		for(int count=start+2;count<test.size();count++){
			previous=thisStep;
			thisStep=next;
			if(count<test.size()-1){
				next=test.get(count+1).split(",");
			}
			
			if(count>1){
				// If there was no sense this step
				if(thisStep[aEvent].equals("0") && thisStep[vEvent].equals("0")){
					// If this isn't the last step
					if(count<test.size()-1){
						// If there was no sense last step or next step and this step immediately follows the previous.
						if((previous[aEvent].equals("0") && previous[vEvent].equals("0")) && (Integer.parseInt(previous[time])==Integer.parseInt(thisStep[time])-1) && (next[aEvent].equals("0") && next[vEvent].equals("0"))){
							concreteSteps.remove(count-deletedRows);
							deletedRows++;
						// If there was no sense this or next, and next immediately follows this step. 
						}else if((next[aEvent].equals("0") && next[vEvent].equals("0")) && (Integer.parseInt(thisStep[time])==Integer.parseInt(next[time])-1)){
							concreteSteps.remove(count-deletedRows);
							deletedRows++;
						}
					// If this is the last step, and there was no sense last step, and this step immediately follows the previous.
					}else if((previous[aEvent].equals("0") && previous[vEvent].equals("0")) && (Integer.parseInt(previous[time])==Integer.parseInt(thisStep[time])-1)){
						concreteSteps.remove(count-deletedRows);
						deletedRows++;
					}
				}
			}
			
		}
		
		return concreteSteps;
	}
	
	// Clears pacing/update steps and leaves only concrete+update pairings. 
	// Assumes existence of input variables
	public ArrayList<String> clearNonConcrete(ArrayList<String> test){
		ArrayList<String> concreteSteps = new ArrayList<String>(test);

		int aEvent = Arrays.asList(test.get(0).split(",")).indexOf("IN_A_EVENT");
		int vEvent = Arrays.asList(test.get(0).split(",")).indexOf("IN_V_EVENT");
		int deletedRows=0;

		for(int count=1;count<test.size();count++){
			String[] thisStep=test.get(count).split(",");

			// If there was no sense this step
			if(thisStep[aEvent].equals("0") && thisStep[vEvent].equals("0")){
					concreteSteps.remove(count-deletedRows);
					deletedRows++;
			}
		}

		return concreteSteps;
	}
}
