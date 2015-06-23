/*	LustreInjection
	Modifies Lustre programs in order to steer with jKind.

	Gregory Gay (greg@greggay.com)
	Last Updated: 05/14/2014 
		- Injection for Manhattan/SqEuclid metrics.
		- Bug fixes.
	05/13/2014
		- Initial file creation
		- State injection

 */

package steering;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class LustreInjection {

	private SteerModel steerer;
	private LustreModel injectedModel;
	private String currentGoal;
	
	public LustreInjection(SteerModel st) {
		steerer = st;
	}
	
	public SteerModel getSteerer(){
		return steerer;
	}	

	public void setSteerer(SteerModel st){
		steerer=st;
	}
	
	public LustreModel getInjectedModel(){
		return injectedModel;
	}	

	public void setInjectedModel(LustreModel model){
		injectedModel = model;
	}
	
	public String getGoal(){
		return currentGoal;
	}
	
	public void setGoal(String goal){
		currentGoal=goal;
	}
	
	// Entry method, chooses the appropriate injection based on current metric in the Steerer.
	public String inject(ArrayList<String> oracleTrace, ArrayList<String> sutTrace, ArrayList<String> initTrace, double threshold) throws Exception{
		injectedModel = new LustreModel(steerer.getModel().getName(), new HashMap<String,String>(steerer.getModel().getTypeMap()), 
				new ArrayList<String>(steerer.getModel().getInputVariables()), new ArrayList<String>(steerer.getModel().getOutputVariables()), 
				new ArrayList<String>(steerer.getModel().getInternalVariables()), new ArrayList<String>(steerer.getModel().getExpressionList()));

		// Pass over traces and check for ill-formated values.
		initTrace=this.valuePass(initTrace);
		oracleTrace=this.valuePass(oracleTrace);
		sutTrace=this.valuePass(sutTrace);
		
		// If not the first test step, set the initial state.
		if(initTrace!=null){
			this.setInitialState(initTrace);
			//injectedModel.printToFile("temp.lus");
		}
		
		// Select appropriate method.
		String metric = steerer.getMetric();
		if(metric.toLowerCase().equals("manhattan")){
			this.injectManhattan(oracleTrace, sutTrace, threshold);
		}else if(metric.toLowerCase().equals("sqeuclid")){
			this.injectSqEuclid(oracleTrace, sutTrace, threshold);
		}else{
			throw new SteeringException("Unsupported metric: "+metric);
		}

		return injectedModel.toString();
	}
	
	// If we just want to update the goal, this method allows a cheap rebuild
	public String updateGoal(double threshold){
		ArrayList<String> exprs = injectedModel.getExpressionList();
		String exp="";
		String newe="";
		
		for(int count=exprs.size()-1;count>=0;count--){
			exp = exprs.get(count);
			
			if(exp.contains("\tprop = ")){
				String prop="(score_steered ";
				if(threshold<0.0){
					prop=prop+"< score_original)";
				}else if(threshold==0.0){
					prop=prop+"= 0.0)";
				}else{
					prop=prop+"< "+String.format("%.12f", threshold)+")";
				}
				newe=exp.replace(currentGoal, prop);
				currentGoal=prop;
				exprs.set(count,newe);
				break;
			}
		}
		injectedModel.setExpressionList(exprs);
		HashMap<String,String> parts = injectedModel.getBuiltParts();
		parts.put("expressionList",parts.get("expressionList").replace(exp,newe));
		injectedModel.setBuiltParts(parts);
		
		return injectedModel.toString();
	}
	
	// Inject equations to calculate the dissimilarity score, 
	// Based on the Manhattan/City Block distance
	public void injectManhattan(ArrayList<String> oracleTrace, ArrayList<String> sutTrace, double threshold) throws Exception{		
		ArrayList<String> iVars=injectedModel.getInternalVariables();
		ArrayList<String> exprs=injectedModel.getExpressionList();
		HashMap<String,String> types=injectedModel.getTypeMap();
		ArrayList<String> header=new ArrayList<String>(Arrays.asList(oracleTrace.get(0).split(",")));
		ArrayList<String> oValues=new ArrayList<String>(Arrays.asList(oracleTrace.get(1).split(",")));
		ArrayList<String> sValues=new ArrayList<String>(Arrays.asList(sutTrace.get(1).split(",")));
		
		String scoreSteered="\tscore_steered = ";
		String scoreOriginal="\tscore_original = ";
		String distanceSteered="\tdistance_steered = ";
		String distanceOriginal="\tdistance_original = ";
		iVars.add("score_steered");
		iVars.add("score_original");
		iVars.add("distance_steered");
		iVars.add("distance_original");
		iVars.add("prop");
		types.put("score_steered","real");
		types.put("score_original","real");
		types.put("distance_steered","real");
		types.put("distance_original","real");
		types.put("prop","bool");
		
		String type="";
		String ovalue="";
		String svalue="";
		double minValue;
		double maxValue;
		
		for(String input: injectedModel.getInputVariables()){
			if(!steerer.getOracleData().contains(input)){
				type=types.get(input);
				ovalue=oValues.get(header.indexOf(input));
				
				if(!type.equals("bool")){
					ovalue=ovalue.replaceAll("[^\\d.]", "");
					iVars.add("concrete_oracle_"+input);
					types.put("concrete_oracle_"+input,"real");
					exprs.add("\tconcrete_oracle_"+input+" = "+Double.toString(Double.parseDouble(ovalue))+";\n");
				}else{
					iVars.add("concrete_oracle_"+input);
					types.put("concrete_oracle_"+input,"bool");
					exprs.add("\tconcrete_oracle_"+input+" = "+ovalue+";\n");
				}
			}
		}
		
		for(String output: steerer.getOracleData()){
			type=types.get(output);
			HashMap<String,HashMap<String,Double>> normConstants=steerer.getNormalization();
			
			String refer="";
			if(normConstants==null){
				if(!type.contains("real")){
					refer="real("+output+")";
				}else{
					refer=output;
				}
			}else{
				refer="norm_"+output;
			}
			
			// (1) Specific to Manhattan
			distanceSteered=distanceSteered+"score_new_"+output+" + ";
			distanceOriginal=distanceOriginal+"score_original_"+output+" + ";
			
			if(!type.equals("bool")){
				iVars.add("concrete_sut_"+output);
				iVars.add("concrete_oracle_"+output);
				types.put("concrete_sut_"+output, "real");
				types.put("concrete_oracle_"+output, "real");
				
				if(normConstants!=null){
					iVars.add("norm_"+output);
					types.put("norm_"+output,"real");
				}
			}else{
				iVars.add("concrete_sut_"+output);
				iVars.add("concrete_oracle_"+output);
				types.put("concrete_sut_"+output, "bool");
				types.put("concrete_oracle_"+output, "bool");
			}
			
			iVars.add("score_new_"+output);
			iVars.add("score_original_"+output);
			types.put("score_new_"+output, "real");
			types.put("score_original_"+output, "real");
			
			ovalue=oValues.get(header.indexOf(output));
			svalue=sValues.get(header.indexOf(output));
						
			if(!type.equals("bool")){
				ovalue=ovalue.replaceAll("[^\\d.]", "");
				svalue=svalue.replaceAll("[^\\d.]", "");
				
				if(normConstants==null){
					exprs.add("\tconcrete_sut_"+output+" = "+Double.toString(Double.parseDouble(svalue))+";\n");
					exprs.add("\tconcrete_oracle_"+output+" = "+Double.toString(Double.parseDouble(ovalue))+";\n");
				}else{
					minValue=normConstants.get(output).get("min");
					maxValue=normConstants.get(output).get("max");
					double covalue=Double.parseDouble(ovalue);
					double csvalue=Double.parseDouble(svalue);
					
					minValue=Math.min(minValue, Math.min(csvalue,covalue));
					maxValue=Math.max(maxValue, Math.max(csvalue,covalue));
					
					exprs.add("\tconcrete_sut_"+output+" = "+(csvalue-minValue)/(maxValue-minValue)+";\n");
					exprs.add("\tconcrete_oracle_"+output+" = "+(covalue-minValue)/(maxValue-minValue)+";\n");
					
					if(!type.contains("real")){
						exprs.add("\tnorm_"+output+" = ((real("+output+") - "+minValue+") / "+(maxValue-minValue)+");\n");
					}else{
						exprs.add("\tnorm_"+output+" = (("+output+" - "+minValue+") / "+(maxValue-minValue)+");\n");
					}
				}
				
				// (2) Specific to Manhattan/SqEuclid
				exprs.add("\tscore_new_"+output+" = if (concrete_sut_"+output+" > "+refer+") then (concrete_sut_"+output+" - "+refer+") else ("+refer+" - concrete_sut_"+output+");\n");
				exprs.add("\tscore_original_"+output+" = if (concrete_sut_"+output+" > concrete_oracle_"+output+") then (concrete_sut_"+output+" - concrete_oracle_"+output+") else (concrete_oracle_"+output+" - concrete_sut_"+output+");\n");
			}else{
				exprs.add("\tconcrete_sut_"+output+" = "+svalue+";\n");
				exprs.add("\tconcrete_oracle_"+output+" = "+ovalue+";\n");
				// (2) Specific to Manhattan/SqEuclid
				exprs.add("\tscore_new_"+output+" = if ((concrete_sut_"+output+" and "+output+") or ((not concrete_sut_"+output+") and (not "+output+"))) then 0.0 else 1.0;\n");
				exprs.add("\tscore_original_"+output+" = if ((concrete_sut_"+output+" and concrete_oracle_"+output+") or ((not concrete_sut_"+output+") and (not concrete_oracle_"+output+"))) then 0.0 else 1.0;\n");
			}
		}
		
		// (3) Specific to Manhattan/SqEuclid
		
		distanceSteered=distanceSteered.substring(0,distanceSteered.length()-3)+";\n";
		distanceOriginal=distanceOriginal.substring(0,distanceOriginal.length()-3)+";\n";
		exprs.add(distanceSteered);
		exprs.add(distanceOriginal);
		exprs.add(scoreSteered+"distance_steered;\n");
		exprs.add(scoreOriginal+"distance_original;\n");
		
		// Formulate property
		
		String prop="(score_steered ";
		if(threshold<0.0){
			prop=prop+"< score_original)";
		}else if(threshold==0.0){
			prop=prop+"= 0.0)";
		}else{
			prop=prop+"< "+threshold+")";
		}
		currentGoal=prop;
		
		String wholeProp="";
		
		ArrayList<String> tolerances= steerer.getTolerances();
		
		if(tolerances!=null){
			for(String tol: tolerances){
				if(wholeProp.equals("")){
					wholeProp="("+tol+")";
				}else{
					wholeProp="("+wholeProp+" and ("+tol+"))";
				}
			}
		}
		
		if(!wholeProp.equals("")){
			wholeProp= "\tprop = not ("+wholeProp+" and "+prop+");\n\t--%PROPERTY prop;\n";
		}else{
			wholeProp= "\tprop = not ("+prop+");\n\t--%PROPERTY prop;\n";
		}
		
		exprs.add(wholeProp);
		
		injectedModel.setTypeMap(types);
		injectedModel.setInternalVariables(iVars);
		injectedModel.setExpressionList(exprs);
	}
	
	// Inject calculations to perform dissimilarity check.
	// Specific to Squared Euclidean distance.
	public void injectSqEuclid(ArrayList<String> oracleTrace, ArrayList<String> sutTrace, double threshold) throws Exception{
		ArrayList<String> iVars=injectedModel.getInternalVariables();
		ArrayList<String> exprs=injectedModel.getExpressionList();
		HashMap<String,String> types=injectedModel.getTypeMap();
		ArrayList<String> header=new ArrayList<String>(Arrays.asList(oracleTrace.get(0).split(",")));
		ArrayList<String> oValues=new ArrayList<String>(Arrays.asList(oracleTrace.get(1).split(",")));
		ArrayList<String> sValues=new ArrayList<String>(Arrays.asList(sutTrace.get(1).split(",")));
		
		String scoreSteered="\tscore_steered = ";
		String scoreOriginal="\tscore_original = ";
		String distanceSteered="\tdistance_steered = ";
		String distanceOriginal="\tdistance_original = ";
		iVars.add("score_steered");
		iVars.add("score_original");
		iVars.add("distance_steered");
		iVars.add("distance_original");
		iVars.add("prop");
		types.put("score_steered","real");
		types.put("score_original","real");
		types.put("distance_steered","real");
		types.put("distance_original","real");
		types.put("prop","bool");
		
		String type="";
		String ovalue="";
		String svalue="";
		double minValue;
		double maxValue;
		
		for(String input: injectedModel.getInputVariables()){
			if(!steerer.getOracleData().contains(input)){
				type=types.get(input);
				ovalue=oValues.get(header.indexOf(input));
				
				if(!type.equals("bool")){
					ovalue=ovalue.replaceAll("[^\\d.]", "");
					
					iVars.add("concrete_oracle_"+input);
					types.put("concrete_oracle_"+input,"real");
					exprs.add("\tconcrete_oracle_"+input+" = "+Double.toString(Double.parseDouble(ovalue))+";\n");
				}else{
					iVars.add("concrete_oracle_"+input);
					types.put("concrete_oracle_"+input,"bool");
					exprs.add("\tconcrete_oracle_"+input+" = "+ovalue+";\n");
				}
			}
		}
		
		for(String output: steerer.getOracleData()){
			type=types.get(output);
			HashMap<String,HashMap<String,Double>> normConstants=steerer.getNormalization();
			
			String refer="";
			if(normConstants==null){
				if(!type.contains("real")){
					refer="real("+output+")";
				}else{
					refer=output;
				}
			}else{
				refer="norm_"+output;
			}
			
			// (1) Specific to SqEuclid
			distanceSteered=distanceSteered+"(score_new_"+output+" * score_new_"+output+") + ";
			distanceOriginal=distanceOriginal+"(score_original_"+output+" * score_original_"+output+") + ";
			
			if(!type.equals("bool")){
				iVars.add("concrete_sut_"+output);
				iVars.add("concrete_oracle_"+output);
				types.put("concrete_sut_"+output, "real");
				types.put("concrete_oracle_"+output, "real");
				
				if(normConstants!=null){
					iVars.add("norm_"+output);
					types.put("norm_"+output,"real");
				}
			}else{
				iVars.add("concrete_sut_"+output);
				iVars.add("concrete_oracle_"+output);
				types.put("concrete_sut_"+output, "bool");
				types.put("concrete_oracle_"+output, "bool");
			}
			
			iVars.add("score_new_"+output);
			iVars.add("score_original_"+output);
			types.put("score_new_"+output, "real");
			types.put("score_original_"+output, "real");
			
			ovalue=oValues.get(header.indexOf(output));
			svalue=sValues.get(header.indexOf(output));
						
			if(!type.equals("bool")){
				ovalue=ovalue.replaceAll("[^\\d.]", "");
				svalue=svalue.replaceAll("[^\\d.]", "");
				
				if(normConstants==null){
					exprs.add("\tconcrete_sut_"+output+" = "+Double.toString(Double.parseDouble(svalue))+";\n");
					exprs.add("\tconcrete_oracle_"+output+" = "+Double.toString(Double.parseDouble(ovalue))+";\n");
				}else{
					minValue=normConstants.get(output).get("min");
					maxValue=normConstants.get(output).get("max");
					double covalue=Double.parseDouble(ovalue);
					double csvalue=Double.parseDouble(svalue);
					
					minValue=Math.min(minValue, Math.min(csvalue,covalue));
					maxValue=Math.max(maxValue, Math.max(csvalue,covalue));
					
					exprs.add("\tconcrete_sut_"+output+" = "+(csvalue-minValue)/(maxValue-minValue)+";\n");
					exprs.add("\tconcrete_oracle_"+output+" = "+(covalue-minValue)/(maxValue-minValue)+";\n");
					
					if(!type.contains("real")){
						exprs.add("\tnorm_"+output+" = ((real("+output+") - "+minValue+") / "+(maxValue-minValue)+");\n");
					}else{
						exprs.add("\tnorm_"+output+" = (("+output+" - "+minValue+") / "+(maxValue-minValue)+");\n");
					}
				}
				
				// (2) Specific to Manhattan/SqEuclid
				exprs.add("\tscore_new_"+output+" = if (concrete_sut_"+output+" > "+refer+") then (concrete_sut_"+output+" - "+refer+") else ("+refer+" - concrete_sut_"+output+");\n");
				exprs.add("\tscore_original_"+output+" = if (concrete_sut_"+output+" > concrete_oracle_"+output+") then (concrete_sut_"+output+" - concrete_oracle_"+output+") else (concrete_oracle_"+output+" - concrete_sut_"+output+");\n");
			}else{
				exprs.add("\tconcrete_sut_"+output+" = "+svalue+";\n");
				exprs.add("\tconcrete_oracle_"+output+" = "+ovalue+";\n");
				// (2) Specific to Manhattan/SqEuclid
				exprs.add("\tscore_new_"+output+" = if ((concrete_sut_"+output+" and "+output+") or ((not concrete_sut_"+output+") and (not "+output+"))) then 0.0 else 1.0;\n");
				exprs.add("\tscore_original_"+output+" = if ((concrete_sut_"+output+" and concrete_oracle_"+output+") or ((not concrete_sut_"+output+") and (not concrete_oracle_"+output+"))) then 0.0 else 1.0;\n");
			}
		}
		
		// (3) Specific to Manhattan/SqEuclid
		
		distanceSteered=distanceSteered.substring(0,distanceSteered.length()-3)+";\n";
		distanceOriginal=distanceOriginal.substring(0,distanceOriginal.length()-3)+";\n";
		exprs.add(distanceSteered);
		exprs.add(distanceOriginal);
		exprs.add(scoreSteered+"distance_steered;\n");
		exprs.add(scoreOriginal+"distance_original;\n");
		
		// Formulate property
		
		String prop="(score_steered ";
		if(threshold<0.0){
			prop=prop+"< score_original)";
		}else if(threshold==0.0){
			prop=prop+"= 0.0)";
		}else{
			prop=prop+"< "+threshold+")";
		}
		currentGoal=prop;
		
		String wholeProp="";
		
		ArrayList<String> tolerances= steerer.getTolerances();
		
		if(tolerances!=null){
			for(String tol: tolerances){
				if(wholeProp.equals("")){
					wholeProp="("+tol+")";
				}else{
					wholeProp="("+wholeProp+" and ("+tol+"))";
				}
			}
		}
		
		if(!wholeProp.equals("")){
			wholeProp= "\tprop = not ("+wholeProp+" and "+prop+");\n\t--%PROPERTY prop;\n";
		}else{
			wholeProp= "\tprop = not ("+prop+");\n\t--%PROPERTY prop;\n";
		}
		
		exprs.add(wholeProp);
		
		injectedModel.setTypeMap(types);
		injectedModel.setInternalVariables(iVars);
		injectedModel.setExpressionList(exprs);
	}
	
	// Takes a pass over values and makes sure there isn't anything wrong.
	public ArrayList<String> valuePass(ArrayList<String> trace){
		ArrayList<String> header=new ArrayList<String>(Arrays.asList(trace.get(0).split(",")));
		ArrayList<String> values=new ArrayList<String>(Arrays.asList(trace.get(1).split(",")));
		String variable="";
		String value="";
		String newValues="";
		
		// Pass over values, make sure they look correct.
		for(int count=0;count<header.size();count++){
			while(!injectedModel.getTypeMap().containsKey(header.get(count))){
				count++;
			}
			if(count>=header.size()){
				break;
			}
			
			variable=header.get(count);
			value=values.get(count);
			
			if(value.toLowerCase().equals("nan")){
				value="0";
			}
			
			if(injectedModel.getTypeMap().get(variable).equals("bool")){
				if(value.equals("1")){
					value="true";
				}else{
					value="false";
				}
			}else if(injectedModel.getTypeMap().get(variable).equals("real")){
				value=value.replaceAll("[^\\d.]", "");
				value=Double.toString(Double.parseDouble(value));
			}
			
			newValues=newValues+value+",";
		}
		
		trace.set(1,newValues.substring(0,newValues.length()-1));
		
		return trace;
	}
	
	// Sets the initial state.
	public void setInitialState(ArrayList<String> initTrace){
		ArrayList<String> header=new ArrayList<String>(Arrays.asList(initTrace.get(0).split(",")));
		ArrayList<String> values=new ArrayList<String>(Arrays.asList(initTrace.get(1).split(",")));
		
		// Modify expressions.
		ArrayList<String> exprs = injectedModel.getExpressionList();
		boolean substitute=false;
		int ocount=0;
		String sub="";
		boolean lastWord=false;
		int howManyO=0;
		int howManyC=0;
		
		for(int expr=0;expr<exprs.size();expr++){
			String line = exprs.get(expr);
			int start=1000;
			int end=0;
			
			if(line.contains("->") || line.contains("pre ") || line.contains("pre(")){
				ocount=0;
				
				// Substitute out everything before the arrow.
				if(line.contains("->")){
					ArrayList<String> words=new ArrayList<String>(Arrays.asList(line.split(" ")));
					
					int word;
					
					for(word=0;word<words.size();word++){
						if(words.get(word).contains("=")){
							start=Math.min(start,word);
						}
						if(words.get(word).contains("->")){
							end=word;
						}
					}
					
					sub="";
					
					for(word=0; word<start+1;word++){
						sub=sub+words.get(word)+" ";
					}
					for(word=start+1; word < end; word++){
						if(words.get(word).contains("(")){
							ocount++;
						}
						if(words.get(word).contains(")")){
							ocount--;
						}
					}
					
					for(word=0;word<ocount;word++){
						sub=sub+"(";
					}
					
					for(word=end+1; word<words.size(); word++){
						sub=sub+words.get(word)+" ";
					}
					
					exprs.set(expr,sub);
				}
				
				ocount=0;
				line = exprs.get(expr);
				
				// Replace variables referenced within a pre(...) expression
				if(line.contains("pre ")||line.contains("pre(")){
					if(!line.contains(";")){
						substitute=true;
					}
					start=0;
					
					ArrayList<String> words=new ArrayList<String>(Arrays.asList(line.split(" ")));
					sub="";
					lastWord=false;
					
					for(int word=0; word<words.size(); word++){
						howManyO=0;
						howManyC=0;
						boolean cast=false;
						
						if(words.get(word).contains("real(")){
							words.set(word, words.get(word).replace("real(",""));
							cast=true;
						}
							
						if(words.get(word).contains(";")){
							lastWord=true;
						}
						char[] letters=words.get(word).toCharArray();

						for(char letter: letters){
							if(letter=='('){
								ocount++;
								howManyO++;
							}else if(letter==')'){
								ocount--;
								howManyC++;
							}
						}
						
						String replaced= words.get(word).replace("(","").replace(")","").replace(";","");
						
						if(start==1 && (ocount>=0 || lastWord) && (header.contains(replaced))){
							int where = header.indexOf(replaced);
							replaced=values.get(where);
							
							if(cast){
								replaced=replaced.replaceAll("[^\\d.]", "");
								replaced=Double.toString(Double.parseDouble(replaced));
								howManyC--;
							}
							
							int count;
							for(count=0;count<howManyO;count++){
								replaced="("+replaced;
							}
							for(count=0;count<howManyC;count++){
								replaced=replaced+")";
							}
							if(lastWord){
								replaced=replaced+";";
							}
							if(word==words.size()-1){
								sub=sub+replaced+"\n";
							}else{
								sub=sub+replaced+" ";
							}
						}else if(words.get(word).equals("pre")){
							ocount=0;
							start=1;
						}else if(words.get(word).equals("pre(")){
							ocount=1;
							start=1;
						}else if(!words.get(word).equals(" ")){
							sub=sub+words.get(word)+" ";
						}
					}
					
					exprs.set(expr,sub);
					
				}				
			}else if(substitute){
				ArrayList<String> words=new ArrayList<String>(Arrays.asList(line.split(" ")));
				sub="";
				lastWord=false;
				
				for(int word=0; word<words.size(); word++){
					howManyO=0;
					howManyC=0;
					boolean cast=false;
					
					if(words.get(word).contains("real(")){
						words.set(word, words.get(word).replace("real(",""));
						cast=true;
					}
						
					if(words.get(word).contains(";")){
						lastWord=true;
						substitute=false;
					}
					char[] letters=words.get(word).toCharArray();

					for(char letter: letters){
						if(letter=='('){
							ocount++;
							howManyO++;
						}else if(letter==')'){
							ocount--;
							howManyC++;
						}
					}
					
					String replaced= words.get(word).replace("(","").replace(")","").replace(";","");
					
					if((ocount>=0 || lastWord) && (header.contains(replaced))){
						int where = header.indexOf(replaced);
						replaced=values.get(where);
						
						if(cast){
							replaced=replaced.replaceAll("[^\\d.]", "");
							replaced=Double.toString(Double.parseDouble(replaced));
							howManyC--;
						}
						
						int count;
						for(count=0;count<howManyO;count++){
							replaced="("+replaced;
						}
						for(count=0;count<howManyC;count++){
							replaced=replaced+")";
						}
						if(lastWord){
							replaced=replaced+";";
						}
						if(word==words.size()-1){
							sub=sub+replaced+"\n";
						}else{
							sub=sub+replaced+" ";
						}
					}else if(words.get(word).equals("pre")){
						ocount=0;
						start=1;
					}else if(words.get(word).equals("pre(")){
						ocount=1;
						start=1;
					}else if(!words.get(word).equals(" ")){
						sub=sub+words.get(word)+" ";
					}
				}
				
				exprs.set(expr,sub);
			}
		}
		
		injectedModel.setExpressionList(exprs);
	}
}
