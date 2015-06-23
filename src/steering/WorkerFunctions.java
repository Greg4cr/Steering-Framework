/*	WorkerFunctions
	Miscellaneous Functionality for Oracle Steering
	
	Gregory Gay (greg@greggay.com)
	Last Updated: 06/11/2014
		- Function to remove records from a trace.
	06/10/2014
	 	- Extract records returns a dummy record if an invalid one requested.
	05/15/2014
	 	- Write trace to file.
	05/12/2014
		- Extract records from a trace
		- Generate type map
		- Edit trace
	05/09/2014
		- Initial file creation
		- Read in trace file
		- Read in variable file
		- Read in normalization file
		- Read in "other" files

*/

package steering;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class WorkerFunctions{

	private SteerModel steerer;

	public WorkerFunctions(SteerModel st){
		steerer=st;
	}

	public SteerModel getSteerer(){
		return steerer;
	}	

	public void setSteerer(SteerModel st){
		steerer=st;
	}
	
	// Reads in trace file and stores it in an array list, indexed by test number
	public ArrayList<ArrayList<String>> readTraceFile(String filename){
		ArrayList<ArrayList<String>> trace = new ArrayList<ArrayList<String>>();
		boolean firstLine=true;
		String header="";
		
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line="";
			ArrayList<String> thisTest= new ArrayList<String>();
			
			while((line=reader.readLine())!=null){
				if(firstLine==true){
					header=line;
					firstLine=false;
				}
				
				if(line.equals("")){
					trace.add(thisTest);
					thisTest=new ArrayList<String>();
					if(steerer.getOffset()==false){
						thisTest.add(header);
					}
				}else{
					thisTest.add(line);
				}
			}
			trace.add(thisTest);
			
			reader.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		
		
		return trace;
	}
	
	// Reads in a variable list (or test suite file) and creates an array list, 
	// Each entry in the list is an entry in the ArrayList
	public ArrayList<String> readListFile(String filename){
		ArrayList<String> list = new ArrayList<String>();
		
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line="";
			
			while((line=reader.readLine())!=null){
				String[] parts=line.split(",");
				
				for(int entry=0;entry<parts.length;entry++){
					list.add(parts[entry]);
				}
			}
			
			reader.close();
		}catch(IOException e){
			e.printStackTrace();
		}
				
		return list;
	}
	
	// Simple utility function to read in a file and just turn its contents into an arraylist.
	public ArrayList<String> readFile(String filename){
		ArrayList<String> contents = new ArrayList<String>();
		
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line="";
			
			while((line=reader.readLine())!=null){
				contents.add(line);
			}
			
			reader.close();
		}catch(IOException e){
			e.printStackTrace();
		}
				
		return contents;
	}
	
	// Simple utility function to read in the normalization constants file.
		public HashMap<String,HashMap<String,Double>> readNormFile(String filename){
			HashMap<String,HashMap<String,Double>> contents = new HashMap<String,HashMap<String,Double>>();
			
			try{
				BufferedReader reader = new BufferedReader(new FileReader(filename));
				String line="";
				int lNum=0;
				String vars="";
				String mins="";
				String maxs="";
				
				while((line=reader.readLine())!=null){
					lNum++;
					
					if(lNum==1){
						vars=line;
					}else if(lNum==2){
						mins=line;
					}else if(lNum==3){
						maxs=line;
					}
				}
				
				reader.close();
				
				String[] varList=vars.split(",");
				String[] minList=mins.split(",");
				String[] maxList=maxs.split(",");
				
				for(int entry=0;entry<varList.length;entry++){
					HashMap<String,Double> v = new HashMap<String,Double>();
					v.put("min", Double.parseDouble(minList[entry]));
					v.put("max",Double.parseDouble(maxList[entry]));
					contents.put(varList[entry],v);
				}
			}catch(IOException e){
				e.printStackTrace();
			}
					
			return contents;
		}
		
		// Extract records from a trace for a chosen test.
		public ArrayList<String> extractRecords(ArrayList<ArrayList<String>> trace, ArrayList<String> variables, int test, int step) throws Exception{
			ArrayList<String> record = new ArrayList<String>();
			String header = trace.get(0).get(0);
			
			// Get indexes of variables to return values for
			// Unless "variables" is null, in which case, return all variables
			ArrayList<Integer> indexes = new ArrayList<Integer>();
			if(variables!=null){
				ArrayList<String> where = new ArrayList<String>(Arrays.asList(header.split(",")));
				for(String s: variables){
					if(where.contains(s)){
						indexes.add(where.indexOf(s));
					}else{
						throw new SteeringDataException("Invalid element requested: "+s);
					}
				}
				
				// Reformat the header
				String[] entries = header.split(",");
				header="";
				for(int index: indexes){
					header=header+entries[index]+",";
				}
				header=header.substring(0,header.length()-1);
			}
			
			if(step==-1){
				// If we want the whole test, just grab and return the test.
				if(variables==null){
					record= new ArrayList<String>(trace.get(test));
				}else{
					// Filter for correct variables.
					ArrayList<String> testRaw = new ArrayList<String>(trace.get(test));
					for(String row: testRaw){
						String[] entries = row.split(",");
						String toAdd = "";
						for(int index: indexes){
							toAdd=toAdd+entries[index]+",";
						}
						record.add(toAdd.substring(0,toAdd.length()-1));
					}
					
				}
			}else if(step>=0){
				record.add(header);
				
				if(variables==null){
					if(step+1<trace.get(test).size()){
						record.add(trace.get(test).get(step+1));
					}else{
						String toAdd="";
						
						for(int index=0;index<trace.get(0).get(0).split(",").length;index++){
							toAdd=toAdd+"0"+",";
						}
						
						record.add(toAdd.substring(0,toAdd.length()-1));
					}
				}else{
					if(step+1<trace.get(test).size()){
						String row=trace.get(test).get(step+1);

						String[] entries = row.split(",");
						String toAdd = "";
						for(int index: indexes){
							toAdd=toAdd+entries[index]+",";
						}
						record.add(toAdd.substring(0,toAdd.length()-1));
					}else{
						String toAdd="";
						
						for(int index:indexes){
							toAdd=toAdd+"0"+",";
						}
						
						record.add(toAdd.substring(0,toAdd.length()-1));
					}
				}
			}
			
			return record;
		}
		
		// Edit a line in a trace.
		public ArrayList<ArrayList<String>> editTrace(ArrayList<ArrayList<String>> trace, String record, int test, int step) throws Exception{
			ArrayList<String> testRecord = new ArrayList<String>(trace.get(test));
			
			if(step<testRecord.size()-1){
				testRecord.set(step+1, record);
			}else if(step==testRecord.size()-1){
				testRecord.add(record);
			}else{
				throw new SteeringDataException("Specified a step not in the test: "+step+", length: "+(testRecord.size()-1));
			}
			
			trace.set(test, testRecord);
			
			return trace;
		}
		
		// Writes a trace to a file.
		public void writeTraceToFile(ArrayList<ArrayList<String>> trace, String filename) throws Exception{
			try{
				boolean firstTest=true;
				
				FileWriter writer = new FileWriter(new File(filename));
				
				for(ArrayList<String> test: trace){
					if(!firstTest){
						writer.write("\n");
					}
					boolean firstStep=true;
					
					for(String step: test){
						if(firstStep && (steerer.getOffset() || firstTest)){
							writer.write(step+"\n");
						}else if(!firstStep){
							writer.write(step+"\n");
						}
						
						firstStep=false;
					}
					
					firstTest=false;
				}
				
				writer.close();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
		
		// Edit a line in a trace.
		public ArrayList<ArrayList<String>> removeRecords(ArrayList<ArrayList<String>> trace, int test, int lastStep) throws Exception{
			ArrayList<String> testRecord = new ArrayList<String>(trace.get(test));

			if(lastStep<testRecord.size()-1){
				while(testRecord.size()>lastStep+1){
					testRecord.remove(lastStep+1);
				}
			}else if(lastStep>=testRecord.size()){
				throw new SteeringDataException("Specified a step not in the test: "+lastStep+", length: "+(testRecord.size()-1));
			}

			trace.set(test, testRecord);

			return trace;
		}
}