/*	SteerModel
	Oracle Steering process for generic Lustre models

	Gregory Gay (greg@greggay.com)
	Last Updated: 06/18/2014
		- Storage added for soem data used in result checking.
	06/17/2014
		- Bug fix: score reading no longer freaks out at fractions
		- Optimization: Order of operations reordering disabled in interpreter
	06/10/2014
	 	- Bug fixes to score refinement.
	05/23/2014
		- Steering loop implementation completed.
	05/15/2014
	 	- Steering loop started.
	05/13/2014 
		- Incorporated new LustreModel POJO.
	05/12/2014
		- Stores type map of variables
		- Stores score calculator.
	05/09/2014
		- Initial file creation
		- Reads in config file and stores data

 */

package steering;

import interpreter.*;

import java.util.ArrayList;
import java.io.*;
import java.util.HashMap;

import org.eclipse.core.runtime.NullProgressMonitor;

import jkind.SolverOption;
import jkind.api.JKindApi;
import jkind.api.results.JKindResult;
import jkind.api.results.PropertyResult;
import jkind.lustre.values.Value;
import jkind.results.*;

public class SteerModel{
	private WorkerFunctions worker;
	private GetScores scorer;
	private ArrayList<ArrayList<String>> oracleTrace;
	private ArrayList<ArrayList<String>> sutTrace;
	private ArrayList<ArrayList<String>> steeredTrace=null;
	private LustreModel model;
	private ArrayList<String> oracleData;
	private ArrayList<String> inputData;
	private String variableListFile;
	private ArrayList<Integer> testSuite;
	private ArrayList<String> tolerances;
	private HashMap<String,HashMap<String,Double>> normalization;
	private String metric;
	private String outFile;
	private boolean offset=false;
	// Only used for result checking, which piggybacks on steerer's functionality
	private ArrayList<ArrayList<String>> unmutatedSUT;

	// Constructor, takes in name of the config file.
	// Reads in fields and initializes data variables
	public SteerModel(String configFile, boolean isOffset) throws Exception{
		try{
			worker=new WorkerFunctions(this);
			scorer=new GetScores(this);
			offset=isOffset;
			BufferedReader reader = new BufferedReader(new FileReader(configFile));
			String line ="";
			String oFile="";
			String sFile="";

			while((line=reader.readLine())!=null){
				String[] parts=line.split("=");

				if(parts.length>2){
					throw new SteeringDataException("Invalid number of fields, "+parts.length+": "+line);
				}
				
				if(parts[0].equals("oracle")){
					if(parts.length==2){
						oracleTrace=worker.readTraceFile(parts[1]);
						if(steeredTrace==null){
							steeredTrace=new ArrayList<ArrayList<String>>(oracleTrace);
						}
						oFile=parts[1].substring(parts[1].lastIndexOf("/")+1);
					}else{
						throw new SteeringDataException("No oracle trace imported.");
					}
				}else if(parts[0].equals("sut")){
					if(parts.length==2){
						sutTrace=worker.readTraceFile(parts[1]);
						sFile=parts[1].substring(parts[1].lastIndexOf("/")+1);
					}else{
						throw new SteeringDataException("No SUT trace imported.");
					}
				}else if(parts[0].equals("steered")){
					if(parts.length==2){
						steeredTrace=worker.readTraceFile(parts[1]);
					}else{
						throw new SteeringDataException("No steered trace imported.");
					}
				}else if(parts[0].equals("rut")){
					if(parts.length==2){
						unmutatedSUT=worker.readTraceFile(parts[1]);
					}else{
						throw new SteeringDataException("No RUT trace imported.");
					}
				}else if(parts[0].equals("model")){
					if(parts.length==2){
						model=new LustreModel(parts[1]);
					}else{
						throw new SteeringDataException("No Lustre model imported.");
					}
				}else if(parts[0].equals("ods")){
					if(parts.length==2){
						oracleData=worker.readListFile(parts[1]);
					}else{
						throw new SteeringDataException("No oracle data set imported.");
					}
				}else if(parts[0].equals("ids")){
					if(parts.length==2){
						inputData=worker.readListFile(parts[1]);
					}else{
						throw new SteeringDataException("No input data set imported.");
					}
				}else if(parts[0].equals("varlist")){
					if(parts.length==2){
						variableListFile=parts[1];
					}
				}else if(parts[0].equals("testsuite")){
					if(parts.length==2){
						ArrayList<String> tempSuite=worker.readListFile(parts[1]);
						testSuite=new ArrayList<Integer>();
						
						for(int entry=0; entry<tempSuite.size();entry++){
							testSuite.add(Integer.parseInt(tempSuite.get(entry)));
						}
					}else{
						throw new SteeringDataException("No oracle data set imported.");
					}
				}else if(parts[0].equals("tolerances")){
					if(parts.length==2){
						tolerances=worker.readFile(parts[1]);
					}
				}else if(parts[0].equals("metric")){
					if(parts.length==2){
						metric=parts[1];
					}else{
						throw new SteeringDataException("No similarity metric specified.");
					}
				}else if(parts[0].equals("normalization")){
					if(parts.length==2){
						normalization=worker.readNormFile(parts[1]);
					}
				}else{
					throw new SteeringDataException("Invalid field in configuration file: "+parts[0]);
				}
			}
			reader.close();
			outFile=oFile+"_STEERED_"+sFile+"_trace.csv";
			
		}catch(IOException e){
			e.printStackTrace();
		}	

	}

	public static void main(String[] args) throws Exception {	
		SteerModel sp = new SteerModel(args[0], Boolean.parseBoolean(args[1]));
		sp.steer();
	}
	
	// Steering loop
	public void steer() throws Exception{
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
			
			ArrayList<String> oracleToScore=this.getWorker().extractRecords(this.getOracleTrace(), this.getOracleData(), test, -1);
			ArrayList<String> sutToScore=this.getWorker().extractRecords(this.getSutTrace(), this.getOracleData(), test, -1);
			
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
				ArrayList<String> inputs = this.getWorker().extractRecords(this.getOracleTrace(), this.getInputData(), test, -1);
				int steps=inputs.size();
				
				if(where>0){
					where=where-1;
				}
				
				// For each step from the step before divergence
				for(step=where; step<steps-1;step++){
					// If this is not the first step we have steered, run the interpreter and edit the trace.
					if(step!=where-1 && step >0){
						// Get inputs
						ArrayList<String> inputsThisRound=new ArrayList<String>();
						inputsThisRound.add(inputs.get(0));
						inputsThisRound.add(inputs.get(step+1));
						
						// Inject the model to set the state;
						ArrayList<String> oTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step);
						ArrayList<String> sTrace = this.getWorker().extractRecords(this.getSutTrace(), null, test, step);
						ArrayList<String> iTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step-1);
						
						String injectedModel = inject.inject(oTrace, sTrace, iTrace, 0.0);
						inject.getInjectedModel().printToFile("injected.lus");
						
						// Run the interpreter
						interpreter = new LustreInterpreter("injected.lus",inputsThisRound);
						interpreter.setEqOrder(false);
						ArrayList<String> record = interpreter.interpret();
						// Edit the trace
						this.setSteeredTrace(this.getWorker().editTrace(this.getSteeredTrace(),record.get(1),test,step));
							
						try{
							File file=new File("injected.lus");
							file.delete();
						}catch(Exception e){
							e.printStackTrace();
						}
					}
					
					// Get score for this round
					oracleToScore=this.getWorker().extractRecords(this.getSteeredTrace(), this.getOracleData(), test, -1);
					double score=scorer.calculate(oracleToScore, sutToScore).get(step);
					System.out.println("Step: "+step+", Initial Score: "+score);
					testLog.add("Step: "+step+", Initial Score: "+score);
					
					// If not = 0, we need to steer
					if(score>0){
						boolean done=false;
						
						// First, try a direct match.
						System.out.println("Try to direct match:");
						testLog.add("Try to direct match:");
						
						ArrayList<String> oTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step);
						ArrayList<String> sTrace = this.getWorker().extractRecords(this.getSutTrace(), null, test, step);
						ArrayList<String> iTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step-1);
						
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
								this.setSteeredTrace(this.getWorker().editTrace(this.getSteeredTrace(),newRecord.get(1),test,step));
								
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
								
								oTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step);
								sTrace = this.getWorker().extractRecords(this.getSutTrace(), null, test, step);
								iTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step-1);
								
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
								oTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step);
								sTrace = this.getWorker().extractRecords(this.getSutTrace(), null, test, step);
								iTrace = this.getWorker().extractRecords(this.getSteeredTrace(), null, test, step-1);
								
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
											
											// Run the interpreter
											interpreter = new LustreInterpreter("injected.lus",newI);
											interpreter.setEqOrder(false);
											if(this.getVariableListFile()!=null){
												interpreter.setOracleData(this.getVariableListFile());
											}
											ArrayList<String> newRecord = interpreter.interpret();
											this.setSteeredTrace(this.getWorker().editTrace(this.getSteeredTrace(),newRecord.get(1),test,step));
											
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

						// Do we need to continue to steer?
						// Note - Pacemaker *does* need to continue to steer
						/*
						this.getModel().printToFile("temp.lus");
						interpreter = new LustreInterpreter("temp.lus",inputs);
						interpreter.setEqOrder(false);
						ArrayList<ArrayList<String>> records=new ArrayList<ArrayList<String>>();
						records.add(interpreter.interpret());
						
						try{
							File file=new File("temp.lus");
							file.delete();
						}catch(Exception e){
							e.printStackTrace();
						}
						
						oracleToScore=this.getWorker().extractRecords(records, this.getOracleData(), 0, -1);
						initScores = scorer.calculate(oracleToScore, sutToScore);
						boolean cont=false;
						int scoreStep=-1;
						for(double nScore: initScores){
							scoreStep++;
							
							if(nScore>0.0 && scoreStep > step){
								cont=true;
							}
						}
						
						if(!cont){
							break;
						}*/
						
					}
				}
				
			}
			
			oracleToScore=worker.extractRecords(this.getSteeredTrace(), this.getOracleData(), test, -1);
			sutToScore=worker.extractRecords(this.getSutTrace(), this.getOracleData(), test, -1);
			
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
		this.getWorker().writeTraceToFile(this.getSteeredTrace(),this.getOutfile());
		
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

	// Getter and setter methods
	public void setOracleTrace(ArrayList<ArrayList<String>> ot){
		oracleTrace=ot;
	}

	public ArrayList<ArrayList<String>> getOracleTrace(){
		return oracleTrace;
	}

	public void setSutTrace(ArrayList<ArrayList<String>> st){
		sutTrace=st;
	}

	public ArrayList<ArrayList<String>> getSutTrace(){
		return sutTrace;
	}

	public void setSteeredTrace(ArrayList<ArrayList<String>> st){
		steeredTrace=st;
	}

	public ArrayList<ArrayList<String>> getSteeredTrace(){
		return steeredTrace;
	}

	public void setModel(LustreModel mdl){
		model=mdl;
	}

	public LustreModel getModel(){
		return model;
	}

	public void setOracleData(ArrayList<String> ods){
		oracleData=ods;
	}

	public ArrayList<String> getOracleData(){
		return oracleData;
	}

	public void setInputData(ArrayList<String> ids){
		inputData=ids;
	}

	public ArrayList<String> getInputData(){
		return inputData;
	}

	public void setTestSuite(ArrayList<Integer> suite){
		testSuite=suite;
	}

	public ArrayList<Integer> getTestSuite(){
		return testSuite;
	}

	public void setTolerances(ArrayList<String> tol){
		tolerances=tol;
	}

	public ArrayList<String> getTolerances(){
		return tolerances;
	}

	public void setNormalization(HashMap<String,HashMap<String,Double>> norm){
		normalization=norm;
	}

	public HashMap<String,HashMap<String,Double>> getNormalization(){
		return normalization;
	}

	public void setMetric(String m){
		metric=m;
	}

	public String getMetric(){
		return metric;
	}

	public WorkerFunctions getWorker(){
		return worker;
	}

	public void setWorker(WorkerFunctions wf){
		worker=wf;
	}
	
	public GetScores getScorer(){
		return scorer;
	}
	
	public void setScorer(GetScores sc){
		scorer=sc;
	}
	
	public boolean getOffset(){
		return offset;
	}
	
	public void setOffset(boolean of){
		offset = of;
	}
	
	public String getVariableListFile(){
		return variableListFile;
	}
	
	public void setVariableListFile(String vars){
		variableListFile=vars;
	}
	
	public void setOutfile(String out){
		outFile=out;
	}
	
	public String getOutfile(){
		return outFile;
	}
	
	// Used for result checking, which piggybacks on steerer's capabilities
	
	public void setUnmutatedSUT(ArrayList<ArrayList<String>> st){
		unmutatedSUT=st;
	}

	public ArrayList<ArrayList<String>> getUnmutatedSUT(){
		return unmutatedSUT;
	}
	
}
