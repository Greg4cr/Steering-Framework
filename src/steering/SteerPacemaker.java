/*	SteerPacemaker
	Oracle Steering process for the Pacemaker model

	Gregory Gay (greg@greggay.com)
	Last Updated: 08/13/2014:
		- Prevent score from dropping below 0
	07/22/2014:
		- Kicks out after 100 steps
	06/17/2014
		- Bug fix: Score parsing doesn't freak out if given a fraction.
	06/11/2014
		- Remove extra trace steps if a steered trace shorter than unsteered.
		- Keep log of all lines printed to console, dump log to file.
	06/10/2014
		- Loop appears to work
		- added optimization (pre-perform order of operations on model)
	 	- Bug fixes to score refinement.
	05/28/2014
	 	- Bug fixes
	05/23/2014
		- Steering loop implemented
	05/12/2014
		- Initial file creation

 */


package steering;

import interpreter.*;
import jkind.SolverOption;
import jkind.api.*;
import jkind.api.results.JKindResult;
import jkind.api.results.PropertyResult;
import jkind.lustre.values.Value;
import jkind.results.Counterexample;
import jkind.results.InvalidProperty;
import jkind.results.Signal;
import jkind.results.UnknownProperty;
import jkind.results.ValidProperty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.core.runtime.NullProgressMonitor;

public class SteerPacemaker extends SteerModel{

	public SteerPacemaker(String configFile, boolean isOffset) throws Exception {
		super(configFile, isOffset);
		this.setWorker(new PacemakerWorker(this));
	}

	public static void main(String[] args) throws Exception {
		SteerPacemaker sp = new SteerPacemaker(args[0], Boolean.parseBoolean(args[1]));
		sp.steer();
	}
	
	// Steering loop
	@Override
	public void steer() throws Exception{
		PacemakerWorker worker = (PacemakerWorker)this.getWorker();
		LustreInjection inject = new LustreInjection(this);
		GetScores scorer = new GetScores(this);
		LustreInterpreter interpreter;
		JKindApi jkind = new JKindApi();
		jkind.setN(1);
		jkind.setSolver(SolverOption.Z3);
		jkind.setBoundedModelChecking();
		ArrayList<String> testLog = new ArrayList<String>();
		
		// For each test in the test suite.
		long start = System.nanoTime();
		long end= System.nanoTime();
		long allStart = System.nanoTime();
		for(int test: this.getTestSuite()){
			start= System.nanoTime();
			
			System.out.println("-----------------\nTest: "+test);
			testLog.add("-----------------\nTest: "+test);
			
			ArrayList<String> oracleToScore=worker.extractRecords(this.getOracleTrace(), this.getOracleData(), test, -1);
			ArrayList<String> sutToScore=worker.extractRecords(this.getSutTrace(), this.getOracleData(), test, -1);
			System.out.println("Initial test size: "+oracleToScore.size()+","+sutToScore.size());
			testLog.add("Initial test size: "+oracleToScore.size()+","+sutToScore.size());
			
			// Do we need to steer for this test?
			ArrayList<Double> initScores = scorer.calculate(oracleToScore, sutToScore);
			int where=-1;
			int step=-1;
			System.out.println("Divergences:");
			testLog.add("Divergences:");
			for(double score: initScores){
				step++;
				
				if(score>0.0){
					if(where==-1){
						where=step;
					}
					
					System.out.println("Step: "+step+", Score: "+score);
					testLog.add("Step: "+step+", Score: "+score);
				}
			}
			
			// If we need to steer
			if(where!=-1){
				System.out.println("Steering:");
				testLog.add("Steering:");
				// Get inputs for the test ready.
				ArrayList<String> inputs = worker.extractRecords(this.getOracleTrace(), this.getInputData(), test, -1);
				
				// For Pacemaker: Filter for concrete senses.
				step=where-1;
				inputs=worker.clearBlanks(inputs,step);
				//for(String in: inputs){
				//	System.out.println(in);
				//}
				
				int steps=inputs.size();
				//System.out.println("Init steps, post cut:"+steps);
				
				// For each step from the step before divergence
				if(where>0){
					where=where-1;
				}
				
				for(step=where; step<steps-1;step++){
					//System.out.println("ds "+step);
					// If this is not the first step we have steered, run the interpreter and edit the trace.
					if(step!=where-1 && step >0){
						// Get inputs
						ArrayList<String> inputsThisRound=new ArrayList<String>();
						inputsThisRound.add(inputs.get(0));
						inputsThisRound.add(inputs.get(step+1));
						//System.out.println("Inputs: "+inputs.get(step+1));
						//System.out.println("SUT: "+worker.extractRecords(this.getSutTrace(), null, test, step).get(1));
						
						// Inject the model to set the state;
						ArrayList<String> oTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step);
						ArrayList<String> sTrace = worker.extractRecords(this.getSutTrace(), null, test, step);
						ArrayList<String> iTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step-1);
						
						String injectedModel = inject.inject(oTrace, sTrace, iTrace, 0.0);
						inject.getInjectedModel().printToFile("injected.lus");
						
						// Run the interpreter
						interpreter = new LustreInterpreter("injected.lus",inputsThisRound);
						interpreter.setEqOrder(false);
						ArrayList<String> record = interpreter.interpret();
						
						// Edit the trace
						this.setSteeredTrace(worker.editTrace(this.getSteeredTrace(),record.get(1),test,step));
							
						try{
							File file=new File("injected.lus");
							file.delete();
						}catch(Exception e){
							e.printStackTrace();
						}
					}
					
					// Get score for this round
					oracleToScore=worker.extractRecords(this.getSteeredTrace(), this.getOracleData(), test, -1);
					double score=scorer.calculate(oracleToScore, sutToScore).get(step);
					System.out.println("Step: "+step+", Initial Score: "+score);
					testLog.add("Step: "+step+", Initial Score: "+score);
					
					// If not = 0, we need to steer
					if(score>0){
						boolean done=false;
						
						// First, try a direct match.
						System.out.println("Try to direct match:");
						testLog.add("Try to direct match:");
						
						ArrayList<String> oTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step);
						ArrayList<String> sTrace = worker.extractRecords(this.getSutTrace(), null, test, step);
						ArrayList<String> iTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step-1);
						
						String injectedModel=inject.inject(oTrace, sTrace, iTrace, 0.0);
						inject.getInjectedModel().printToFile("injected.lus");
						JKindResult result = new JKindResult(injectedModel);
						NullProgressMonitor monitor = new NullProgressMonitor();
						jkind.execute(injectedModel, result, monitor);
						
						HashMap<String,String> newInputs=new HashMap<String,String>();
						ArrayList<String> newI = new ArrayList<String>();
						
						for (PropertyResult pr : result.getPropertyResults()) {
							System.out.println(pr.getName() + " - " + pr.getStatus());
							testLog.add(pr.getName() + " - " + pr.getStatus());
							if(pr.getProperty() instanceof InvalidProperty && pr.getName().equals("prop")){
								done=true;
								Counterexample ce=((InvalidProperty)pr.getProperty()).getCounterexample();

								// If property is invalid, extract inputs from the counterexample
								for(Signal<Value> variable: ce.getSignals()){
									if(this.getInputData().contains(variable.getName())){
										String value= variable.getValue(0).toString();
										if(value.equals("true")){
											value="1";
										}else if(value.equals("false")){
											value="0";
										}else if(value.contains("/")){
											String[] parts = value.split("/");
											value= Double.toString(Double.parseDouble(parts[0])/Double.parseDouble(parts[1]));
										}
										
										newInputs.put(variable.getName(),value);
									}
								}
								
								// Construct an input array for the interpreter
								String vars="";
								String vals="";
								for(String var: this.getInputData()){
									vars=vars+var+",";
									vals=vals+newInputs.get(var)+",";
								}
								newI.add(vars.substring(0,vars.length()-1));
								newI.add(vals.substring(0,vals.length()-1));
								System.out.println(inputs.get(step+1));
								testLog.add(inputs.get(step+1));
								inputs.set(step+1, newI.get(1));
								System.out.println(inputs.get(step+1));
								testLog.add(inputs.get(step+1));
								
								// Run the interpreter
								interpreter = new LustreInterpreter("injected.lus",newI);
								interpreter.setEqOrder(false);
								if(this.getVariableListFile()!=null){
									interpreter.setOracleData(this.getVariableListFile());
								}
								ArrayList<String> newRecord = interpreter.interpret();
								this.setSteeredTrace(worker.editTrace(this.getSteeredTrace(),newRecord.get(1),test,step));
								
								try{
									File file=new File("injected.lus");
									file.delete();
								}catch(Exception e){
									e.printStackTrace();
								}
							}
						}
						
						if(!done){
							// If we can't match, try to cut down the score range.
							double threshold = 1;
							double newScore=score;
							double oldScore=score;
							
							while((threshold >= 0.125) && !done){
								threshold=threshold/2;
								oldScore=newScore;
								newScore=score*threshold;
								System.out.println("Range Cut, Threshold: "+threshold+", New Score: "+newScore);
								testLog.add("Range Cut, Threshold: "+threshold+", New Score: "+newScore);
								
								oTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step);
								sTrace = worker.extractRecords(this.getSutTrace(), null, test, step);
								iTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step-1);
								
								injectedModel=inject.updateGoal(newScore);
								inject.getInjectedModel().printToFile("injected.lus");
								result = new JKindResult(injectedModel);
								monitor = new NullProgressMonitor();
								jkind.execute(injectedModel, result, monitor);
								
								for (PropertyResult pr : result.getPropertyResults()) {
									System.out.println(pr.getName() + " - " + pr.getStatus());
									testLog.add(pr.getName() + " - " + pr.getStatus());
									if((pr.getProperty() instanceof UnknownProperty || pr.getProperty() instanceof ValidProperty) && pr.getName().equals("prop")){
										score=oldScore;
										done=true;
									}else if(pr.getProperty() instanceof InvalidProperty && pr.getName().equals("prop") && (threshold <= 0.125)){
										score=newScore;
									}
								}
							}
							
							newInputs=null;
							newI = null;
							done=false;
							
							// Now, within the remaining range
							while(!done){
								System.out.println("Refinement, Goal: "+score);
								testLog.add("Refinement, Goal: "+score);
								oTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step);
								sTrace = worker.extractRecords(this.getSutTrace(), null, test, step);
								iTrace = worker.extractRecords(this.getSteeredTrace(), null, test, step-1);
								
								injectedModel=inject.updateGoal(score);
								inject.getInjectedModel().printToFile("injected.lus");
								result = new JKindResult(injectedModel);
								monitor = new NullProgressMonitor();
								jkind.execute(injectedModel, result, monitor);
								
								for (PropertyResult pr : result.getPropertyResults()) {
									System.out.println(pr.getName() + " - " + pr.getStatus());
									testLog.add(pr.getName() + " - " + pr.getStatus());
									// If we get an "unknown" or "valid", stop trying to steer.
									if((pr.getProperty() instanceof UnknownProperty || pr.getProperty() instanceof ValidProperty) && pr.getName().equals("prop")){
										done=true;
										
										// If we previously had a counterexample, run that and edit the trace.
										if(newInputs!=null){
											// Construct an input array for the interpreter
											String vars="";
											String vals="";
											for(String var: this.getInputData()){
												vars=vars+var+",";
												vals=vals+newInputs.get(var)+",";
											}
											newI = new ArrayList<String>();
											newI.add(vars.substring(0,vars.length()-1));
											newI.add(vals.substring(0,vals.length()-1));
											
											System.out.println(inputs.get(step+1));
											testLog.add(inputs.get(step+1));
											inputs.set(step+1, newI.get(1));
											System.out.println(inputs.get(step+1));
											testLog.add(inputs.get(step+1));
											
											// Run the 
											interpreter = new LustreInterpreter("injected.lus",newI);
											interpreter.setEqOrder(false);
											if(this.getVariableListFile()!=null){
												interpreter.setOracleData(this.getVariableListFile());
											}
											ArrayList<String> newRecord = interpreter.interpret();
											this.setSteeredTrace(worker.editTrace(this.getSteeredTrace(),newRecord.get(1),test,step));
											
											try{
												File file=new File("injected.lus");
												file.delete();
											}catch(Exception e){
												e.printStackTrace();
											}
										}
									// If we get an "invalid", extract the inputs.
									}else if(pr.getProperty() instanceof InvalidProperty && pr.getName().equals("prop")){
										Counterexample ce=((InvalidProperty)pr.getProperty()).getCounterexample();
										newInputs=new HashMap<String,String>();

										// If property is invalid, extract inputs from the counterexample
										for(Signal<Value> variable: ce.getSignals()){
											if(this.getInputData().contains(variable.getName())){
												String value= variable.getValue(0).toString();
												if(value.equals("true")){
													value="1";
												}else if(value.equals("false")){
													value="0";
												}else if(value.equals("null")){
													value="0";
												}else if(value.contains("/")){
													String[] parts = value.split("/");
													value= Double.toString(Double.parseDouble(parts[0])/Double.parseDouble(parts[1]));
												}
												newInputs.put(variable.getName(),value);
											}else if(variable.getName().equals("score_steered")){
												String value= variable.getValue(0).toString();
												
												if(value.contains("/")){
													String[] parts = value.split("/");
													value= Double.toString((Double.parseDouble(parts[0])/Double.parseDouble(parts[1]))-0.001); // small epsilon to get around rounding issues
												}
												
												score=Double.parseDouble(value);
												
												if(score<0){
													score=0;
												}
											}
										}
									}
								}
							}
						}
					}
					
					// pacemaker: insert step check
					inputs=worker.insertNoEvent(inputs, worker.extractRecords(this.getSteeredTrace(), null, test, -1), "0,0,0,2,120,150,0,50,50,75,0,150,0,50,0,0,2,1000", step);
					steps=inputs.size();
					//System.out.println(steps);
					//if(step+2<inputs.size()){
					//	System.out.println(step+": "+inputs.get(step+1)+"\n"+(step+1)+": "+inputs.get(step+2));
					//}
					
					if(steps>100){
						steps=100;
					}
				}
			}
			
			// Remove any extraneous steps in steered trace (i.e., trace is now shorter)
			if(step<this.getSteeredTrace().get(test).size()-1){
				this.setSteeredTrace(worker.removeRecords(this.getSteeredTrace(),test,step));
			}
			
			oracleToScore=worker.extractRecords(this.getSteeredTrace(), this.getOracleData(), test, -1);
			sutToScore=worker.extractRecords(this.getSutTrace(), this.getOracleData(), test, -1);
			System.out.println("----------------\nFinal test size: "+oracleToScore.size()+","+sutToScore.size());
			testLog.add("----------------\nFinal test size: "+oracleToScore.size()+","+sutToScore.size());
			
			initScores = scorer.calculate(oracleToScore, sutToScore);
			System.out.println("Remaining Divergences:");
			testLog.add("Remaining Divergences:");
			step=-1;
			for(double score: initScores){
				step++;
				
				if(score>0.0){
					System.out.println("Step: "+step+", Score: "+score);
					testLog.add("Step: "+step+", Score: "+score);
				}
			}
			
			end=System.nanoTime();
			System.out.println("Time: "+(end-start));
			testLog.add("Time: "+(end-start));
		}
		
		end=System.nanoTime();
		System.out.println("Time for Suite: "+(end-allStart));
		testLog.add("Time for Suite: "+(end-allStart));
		
		//When done, write steered trace to file.
		worker.writeTraceToFile(this.getSteeredTrace(),this.getOutfile());
		
		// Write test log to file
		try{
			FileWriter writer = new FileWriter(new File("log.txt"));
			for(String entry: testLog){
				writer.write(entry+"\n");
			}
			
			writer.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		
	}
}
