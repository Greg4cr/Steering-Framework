/*	LustreModel
	POJO representation of a Lustre model

	Gregory Gay (greg@greggay.com)
	Last Updated: 05/13/2014
		- Initial file creation
		- Construct Lustre model from in-memory data structures
		- Construct Lustre model from file import.

 */

package steering;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class LustreModel {

	// Name of the Lustre node
	private String nodeName;
	// Mapping of name to type
	private HashMap<String,String> typeMap;
	// List of input variables
	private ArrayList<String> inputVariables;
	// List of output variables
	private ArrayList<String> outputVariables;
	// List of internal variables
	private ArrayList<String> internalVariables;
	// List of expressions
	private ArrayList<String> expressionList;
	// Partial build of model-as-string
	private HashMap<String,String> builtParts;
	
	// Construct a Lustre model from memory.
	public LustreModel(String name, HashMap<String,String> types, ArrayList<String> inputs, 
						ArrayList<String> outputs, ArrayList<String> internals, ArrayList<String> exprs){
		nodeName=name;
		typeMap=types;
		inputVariables = inputs;
		outputVariables = outputs;
		internalVariables = internals;
		expressionList = exprs;
		builtParts=new HashMap<String,String>();
	}
	
	// Import a Lustre model from a file.
	public LustreModel(String filename) throws Exception{
		try{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line="";
			typeMap=new HashMap<String,String>();
			inputVariables = new ArrayList<String>();
			outputVariables = new ArrayList<String>();
			internalVariables = new ArrayList<String>();
			expressionList = new ArrayList<String>();
			builtParts=new HashMap<String,String>();
			boolean inMon=false;
			boolean outMon=false;
			boolean intMon=false;
			boolean exprMon=false;
			String variable="";
			String value="";
			
			// Read in Lustre model
			while((line=reader.readLine())!=null){
				
				if(line.contains("node ")){
					String[] parts=line.split(" ");
					if(parts[1].contains("(")){
						nodeName=parts[1].substring(0,parts[1].indexOf("("));
					}else{
						nodeName=parts[1].trim();
					}
					
					inMon=true;
				}else if(line.contains("returns ")||line.contains("returns(")){
					outMon=true;
					inMon=false;
				}else if(line.trim().equals("var")){
					outMon=false;
					intMon=true;
				}else if(line.contains("let ") || line.trim().equals("let")){
					exprMon=true;
					intMon=false;
				}else if(line.contains("tel;")){
					exprMon=false;
				}
				
				if(inMon||outMon||intMon){
					String[] parts=line.split(":");
					
					if(parts.length > 1){
						if(parts[0].contains("(")){
							parts[0]=parts[0].substring(parts[0].indexOf("(")+1);
						}
						
						variable=parts[0].trim().replace(" ","").replace(";","").replace(")","");
						value=parts[1].trim().replace(";","").replace(")","");
						
						typeMap.put(variable, value);
						
						if(inMon){
							inputVariables.add(variable);
						}else if(outMon){
							outputVariables.add(variable);
						}else if(intMon){
							internalVariables.add(variable);
						}
					}
				}else if(exprMon && !(line.contains("let ") || line.trim().equals("let"))){
					expressionList.add(line);
				}
			}
			
			reader.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	// Return Lustre model as a String.
	public String toString(){
		String out="node "+nodeName+"(";
		int count;
		
		if(builtParts.containsKey("inputVariables")){
			out=out+builtParts.get("inputVariables");
		}else{
			String inV="";
			for(count=0; count< inputVariables.size();count++){
				if(count<inputVariables.size()-1){
					inV=inV+inputVariables.get(count)+" : "+typeMap.get(inputVariables.get(count))+";\n\t";
				}else{
					inV=inV+inputVariables.get(count)+" : "+typeMap.get(inputVariables.get(count))+")\n";
				}
			}
			out=out+inV;
			builtParts.put("inputVariables", inV);
		}
		
		out=out+"returns (";
		
		if(builtParts.containsKey("outputVariables")){
			out=out+builtParts.get("outputVariables");
		}else{
			String outV="";
			for(count=0; count< outputVariables.size();count++){
				if(count<outputVariables.size()-1){
					outV=outV+outputVariables.get(count)+" : "+typeMap.get(outputVariables.get(count))+";\n\t";
				}else{
					outV=outV+outputVariables.get(count)+" : "+typeMap.get(outputVariables.get(count))+");\n";
				}
			}
			out=out+outV;
			builtParts.put("outputVariables", outV);
		}
		
		out=out+"var\n";
		
		if(builtParts.containsKey("internalVariables")){
			out=out+builtParts.get("internalVariables");
		}else{
			String iV="";
			for(count=0; count< internalVariables.size();count++){
				iV=iV+"\t"+internalVariables.get(count)+" : "+typeMap.get(internalVariables.get(count))+";\n";
			}
			out=out+iV;
			builtParts.put("internalVariables",iV);
		}
		
		out=out+"let --%MAIN\n";
		

		if(builtParts.containsKey("expressionList")){
			out=out+builtParts.get("expressionList");
		}else{
			String eL="";
			
			for(count=0; count< expressionList.size();count++){
				if(expressionList.get(count).contains("\n")){
					eL=eL+"\t"+expressionList.get(count);
				}else{
					eL=eL+"\t"+expressionList.get(count)+"\n";
				}
			}
			
			out=out+eL;
			builtParts.put("expressionList",eL);
		}
		
		out=out+"tel;\n";
		
		return out;
	}
	
	// Rebuilds String representation of appropriate section.
	public void rebuildString(String part){
		int count;
		
		if(part.equals("inputVariables")){
			String inV="";
			for(count=0; count< inputVariables.size();count++){
				if(count<inputVariables.size()-1){
					inV=inV+inputVariables.get(count)+" : "+typeMap.get(inputVariables.get(count))+";\n\t";
				}else{
					inV=inV+inputVariables.get(count)+" : "+typeMap.get(inputVariables.get(count))+")\n";
				}
			}
			builtParts.put("inputVariables", inV);
		}else if(part.equals("outputVariables")){
			String outV="";
			for(count=0; count< outputVariables.size();count++){
				if(count<outputVariables.size()-1){
					outV=outV+outputVariables.get(count)+" : "+typeMap.get(outputVariables.get(count))+";\n\t";
				}else{
					outV=outV+outputVariables.get(count)+" : "+typeMap.get(outputVariables.get(count))+");\n";
				}
			}
			builtParts.put("outputVariables", outV);
		}else if(part.equals("internalVariables")){
			String iV="";
			for(count=0; count< internalVariables.size();count++){
				iV=iV+"\t"+internalVariables.get(count)+" : "+typeMap.get(internalVariables.get(count))+";\n";
			}
			builtParts.put("internalVariables",iV);
		}else if(part.equals("expressionList")){
			String eL="";
			
			for(count=0; count< expressionList.size();count++){
				if(expressionList.get(count).contains("\n")){
					eL=eL+"\t"+expressionList.get(count);
				}else{
					eL=eL+"\t"+expressionList.get(count)+"\n";
				}
			}
			builtParts.put("expressionList",eL);
		}
	}
	
	// Prints Lustre model to a file
	public void printToFile(String filename){
		try{
			FileWriter writer = new FileWriter(filename);
			
			writer.write(this.toString());
			writer.flush();
			writer.close();
		}catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void setName(String name){
		nodeName=name;
	}
	
	public String getName(){
		return nodeName;
	}
	
	public void setTypeMap(HashMap<String,String> types){
		typeMap=types;
	}
	
	public HashMap<String, String> getTypeMap(){
		return typeMap;
	}
	
	public void setInputVariables(ArrayList<String> inputs){
		inputVariables=inputs;
	}
	
	public ArrayList<String> getInputVariables(){
		return inputVariables;
	}
	
	public void setOutputVariables(ArrayList<String> outputs){
		outputVariables=outputs;
	}
	
	public ArrayList<String> getOutputVariables(){
		return outputVariables;
	}
	
	public void setInternalVariables(ArrayList<String> internal){
		internalVariables=internal;
	}
	
	public ArrayList<String> getInternalVariables(){
		return internalVariables;
	}
	
	public void setExpressionList(ArrayList<String> expr){
		expressionList=expr;
	}
	
	public ArrayList<String> getExpressionList(){
		return expressionList;
	}
	
	public HashMap<String,String> getBuiltParts(){
		return builtParts;
	}
	
	public void setBuiltParts(HashMap<String,String> parts){
		builtParts=parts;
	}

}
