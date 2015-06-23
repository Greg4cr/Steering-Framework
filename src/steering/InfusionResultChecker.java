/*	InfusionResultChecker
	Calculates TP/FP/TN/FN matrix for the infusion manager model

	Gregory Gay (greg@greggay.com)
	Last Updated: 07/17/2014
		- Initial file creation
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

public class InfusionResultChecker {
	private SteerModel sp;
	private String resumeFile="";
	
	public InfusionResultChecker(String configFile, boolean isOffset) throws Exception {
		sp = new SteerPacemaker(configFile, isOffset);
		sp.setWorker(new PacemakerWorker(sp));
	}
	
	public InfusionResultChecker(String configFile, boolean isOffset, String resume) throws Exception {
		sp = new SteerPacemaker(configFile, isOffset);
		sp.setWorker(new PacemakerWorker(sp));
		resumeFile=resume;
	}

	public static void main(String[] args) throws Exception{
		InfusionResultChecker result;
		if(args.length>2){
			result = new InfusionResultChecker(args[0], Boolean.parseBoolean(args[1]), args[2]);
		}else{
			result = new InfusionResultChecker(args[0], Boolean.parseBoolean(args[1]));
		}
		result.checkResults();
	}

	public void checkResults() throws Exception{
		ArrayList<String> calculations = new ArrayList<String>();
		WorkerFunctions worker = sp.getWorker();
		GetScores scorer = new GetScores(sp);
		ArrayList<HashMap<String,Integer>> priorResults = this.readResumeFile(this.getResumeFile());
		
		HashMap<String,Integer> results = priorResults.get(0);
		HashMap<String,Integer> defaultResults = priorResults.get(1);
		HashMap<String,Integer> filterResults = priorResults.get(2);
	
		int numSteered=0;
		
		for(int test: sp.getTestSuite()){
			System.out.println("Test: "+test);

			boolean initPass=true;
			boolean steerPass=true;
			boolean filterPass=true;
			int outcome=0;

			ArrayList<String> oracle=worker.extractRecords(sp.getOracleTrace(), sp.getOracleData(), test, -1);
			ArrayList<String> sut=worker.extractRecords(sp.getSutTrace(), sp.getOracleData(), test, -1);
			ArrayList<String> steered=worker.extractRecords(sp.getSteeredTrace(), sp.getOracleData(), test, -1);
			ArrayList<String> rut=worker.extractRecords(sp.getUnmutatedSUT(), sp.getOracleData(), test, -1);
			
			ArrayList<String> oracleAll=worker.extractRecords(sp.getOracleTrace(), null, test, -1);
			ArrayList<String> sutAll=worker.extractRecords(sp.getSutTrace(), null, test, -1);
			ArrayList<String> rutAll=worker.extractRecords(sp.getUnmutatedSUT(), null, test, -1);

			// Do we need to steer for this test?
			ArrayList<Double> initScores = scorer.calculate(oracle, sut);
			ArrayList<Double> steeredScores = scorer.calculate(steered, sut);
			ArrayList<Integer> failingStepsO = new ArrayList<Integer>();
			ArrayList<Integer> failingStepsS = new ArrayList<Integer>();

			int step=-1;
			for(double score: initScores){
				step++;

				if(score>0.0){
					failingStepsO.add(step);
				}
			}
			step=-1;
			for(double score: steeredScores){
				step++;

				if(score>0.0){
					if(step!=steered.size()-1){
						failingStepsS.add(step);
					}
				}
			}

			if(failingStepsO.size()>0){
				initPass=false;
			}

			if(failingStepsS.size()>0){
				steerPass=false;
			}
			
			if(failingStepsO.size()!=failingStepsS.size()){
				numSteered++;
				//System.out.println("steered: "+failingStepsO.size()+","+failingStepsS.size());
			}

			// Does it pass the filter?
			ArrayList<String> oVars = new ArrayList<String>(Arrays.asList(oracleAll.get(0).split(",")));
			ArrayList<String> sVars = new ArrayList<String>(Arrays.asList(sutAll.get(0).split(",")));

			int oFP = oVars.indexOf("CONFIG_IN_Flow_Rate_Patient_Bolus");
			int oFI = oVars.indexOf("CONFIG_IN_Flow_Rate_Intermittent_Bolus");
			int oNI = oVars.indexOf("IM_OUT_New_Infusion");
			int oMI = oVars.indexOf("IM_OUT_Log_Message_ID");
			int oID = oVars.indexOf("IM_OUT_Actual_Infusion_Duration");
			
			int sM = sVars.indexOf("IM_OUT_Current_System_Mode");
			int sFR = sVars.indexOf("IM_OUT_Flow_Rate_Commanded");
			int sNI = sVars.indexOf("IM_OUT_New_Infusion");
			int sMI = sVars.indexOf("IM_OUT_Log_Message_ID");
			int sID = sVars.indexOf("IM_OUT_Actual_Infusion_Duration");
			int sP = sVars.indexOf("CONFIG_IN_Patient_Bolus_Duration");
			int sI = sVars.indexOf("CONFIG_IN_Intermittent_Bolus_Duration");

			int pTally=0;
			int iTally=0;

			for(int record=1;record<sut.size();record++){
				ArrayList<String> oRecord = new ArrayList<String>(Arrays.asList(oracle.get(record).split(",")));
				ArrayList<String> sRecord = new ArrayList<String>(Arrays.asList(sut.get(record).split(",")));
				ArrayList<String> oAllRecord = new ArrayList<String>(Arrays.asList(oracleAll.get(record).split(",")));
				ArrayList<String> sAllRecord = new ArrayList<String>(Arrays.asList(sutAll.get(record).split(",")));
				
				int sMode = Integer.parseInt(Arrays.asList(sutAll.get(record).split(",")).get(sM));
				int pVal = Integer.parseInt(Arrays.asList(sutAll.get(record).split(",")).get(sP));
				int iVal = Integer.parseInt(Arrays.asList(sutAll.get(record).split(",")).get(sI));

				if(sMode==4){
					pTally++;
					iTally=0;
				}else if(sMode==3){
					iTally++;
					pTally=0;
				}else{
					iTally=0;
					pTally=0;
				}
				
				if(!oRecord.equals(sRecord)){
					if(sMode==4){
						if(pTally >=pVal+2){
							filterPass=false;
						}else if(!sAllRecord.get(sFR).equals(oAllRecord.get(oFP))){
							filterPass=false;
						}
					}else if(sMode==3){
						if(iTally >=iVal+2){
							filterPass=false;
						}else if(!sAllRecord.get(sFR).equals(oAllRecord.get(oFI))){
							filterPass=false;
						}
					}
					
					if(!sAllRecord.get(sNI).equals(oAllRecord.get(oNI))){
						filterPass=false;
					}
					if(!sAllRecord.get(sMI).equals(oAllRecord.get(oMI))){
						filterPass=false;
					}
					if(!sAllRecord.get(sID).equals(oAllRecord.get(oID))){
						filterPass=false;
					}
				}
			}
			
			// Did the test pass initially?
			if(initPass){
				calculations.add("Test: "+test+", Outcome: 0");
				if(steerPass){
					int r = results.get("pp");
					results.put("pp",++r);
				}else{
					int r = results.get("pf");
					results.put("pf",++r);
				}
				
				if(filterPass){
					int r = filterResults.get("pp");
					filterResults.put("pp",++r);
				}else{
					int r = filterResults.get("pf");
					filterResults.put("pf",++r);
				}
				
				int r = defaultResults.get("pp");
				defaultResults.put("pp", ++r);
			}else{
				// Is the failure due to a fault?
				for(int record: failingStepsO){
					String sutLine="";
					String rutLine="";

					if(record+1<sut.size()){
						sutLine=sut.get(record+1);
					}
					if(record+1<rut.size()){
						rutLine=rut.get(record+1);
					}
					
					if(!sutLine.equals(rutLine)){
						outcome=3;
						break;
					}
				}	

				// Is the failure within tolerance?
				if(outcome!=3){
					
					sI = Arrays.asList(sutAll.get(0).split(",")).indexOf("CONFIG_IN_Intermittent_Bolus_Duration");
					sP = Arrays.asList(sutAll.get(0).split(",")).indexOf("CONFIG_IN_Patient_Bolus_Duration");
					sM = Arrays.asList(sutAll.get(0).split(",")).indexOf("IM_OUT_Current_System_Mode");

					pTally=0;
					iTally=0;

					for(int record=1; record<sut.size(); record++){
						int sMode = Integer.parseInt(Arrays.asList(sutAll.get(record).split(",")).get(sM));
						int pVal = Integer.parseInt(Arrays.asList(sutAll.get(record).split(",")).get(sP));
						int iVal = Integer.parseInt(Arrays.asList(sutAll.get(record).split(",")).get(sI));

						if(sMode==4){
							pTally++;
							iTally=0;
						}else if(sMode==3){
							iTally++;
							pTally=0;
						}else{
							iTally=0;
							pTally=0;
						}

						if(iTally >= iVal+2){
							outcome=2;
							break;
						}else if(pTally >= pVal+2){
							outcome=2;
							break;
						}else if(pTally >= pVal){
							outcome=1;
						}else if(iTally >= iVal){
							outcome=1;
						}
					}
				}
				
				System.out.println(outcome);
				if(outcome==0){
					outcome=1;
				}
				calculations.add("Test: "+test+", Outcome: "+outcome);
				
				if(outcome==1){
					if(steerPass){
						int r = results.get("fitp");
						results.put("fitp",++r);
					}else{
						int r = results.get("fitf");
						results.put("fitf",++r);
					}
					
					if(filterPass){
						int r = filterResults.get("fitp");
						filterResults.put("fitp",++r);
					}else{
						int r = filterResults.get("fitf");
						filterResults.put("fitf",++r);
					}
					
					int r = defaultResults.get("fitf");
					defaultResults.put("fitf",++r);
				}else if(outcome==2){
					if(steerPass){
						int r = results.get("fntp");
						results.put("fntp",++r);
					}else{
						int r = results.get("fntf");
						results.put("fntf",++r);
					}
					
					if(filterPass){
						int r = filterResults.get("fntp");
						filterResults.put("fntp",++r);
					}else{
						int r = filterResults.get("fntf");
						filterResults.put("fntf",++r);
					}
					
					int r = defaultResults.get("fntf");
					defaultResults.put("fntf",++r);
				}else if(outcome==3){
					if(steerPass){
						int r = results.get("ffp");
						results.put("ffp",++r);
					}else{
						int r = results.get("fff");
						results.put("fff",++r);
					}
					
					if(filterPass){
						int r = filterResults.get("ffp");
						filterResults.put("ffp",++r);
					}else{
						int r = filterResults.get("fff");
						filterResults.put("fff",++r);
					}
				
					int r = defaultResults.get("fff");
					defaultResults.put("fff",++r);
				}
			}
		}
		
		// Display results
		calculations.add("----------\nDefault:");
		calculations.add(defaultResults.get("pp")+","+defaultResults.get("pf"));
		calculations.add(defaultResults.get("fitp")+","+defaultResults.get("fitf"));
		calculations.add(defaultResults.get("fntp")+","+defaultResults.get("fntf"));
		calculations.add(defaultResults.get("ffp")+","+defaultResults.get("fff"));
		
		int tp = defaultResults.get("fntf")+defaultResults.get("fff");
		int fp = defaultResults.get("pf")+defaultResults.get("fitf");
		int fn = defaultResults.get("fntp")+defaultResults.get("ffp");
		double precision=(double)tp/((double)tp + (double)fp);
		double recall=(double)tp/((double)tp+(double)fn);
		double accuracy=2*((precision*recall)/(precision+recall));
		
		calculations.add("Default: "+precision+" | "+recall+" | "+accuracy);
		
		calculations.add("----------\nSteering:");
		calculations.add(results.get("pp")+","+results.get("pf"));
		calculations.add(results.get("fitp")+","+results.get("fitf"));
		calculations.add(results.get("fntp")+","+results.get("fntf"));
		calculations.add(results.get("ffp")+","+results.get("fff"));
		
		tp = results.get("fntf")+results.get("fff");
		fp = results.get("pf")+results.get("fitf");
		fn = results.get("fntp")+results.get("ffp");
		precision=(double)tp/((double)tp + (double)fp);
		recall=(double)tp/((double)tp+(double)fn);
		accuracy=2*((precision*recall)/(precision+recall));
		
		calculations.add("Steering: "+precision+" | "+recall+" | "+accuracy);
		
		calculations.add("----------\nFiltering:");
		calculations.add(filterResults.get("pp")+","+filterResults.get("pf"));
		calculations.add(filterResults.get("fitp")+","+filterResults.get("fitf"));
		calculations.add(filterResults.get("fntp")+","+filterResults.get("fntf"));
		calculations.add(filterResults.get("ffp")+","+filterResults.get("fff"));
		
		tp = filterResults.get("fntf")+filterResults.get("fff");
		fp = filterResults.get("pf")+filterResults.get("fitf");
		fn = filterResults.get("fntp")+filterResults.get("ffp");
		precision=(double)tp/((double)tp + (double)fp);
		recall=(double)tp/((double)tp+(double)fn);
		accuracy=2*((precision*recall)/(precision+recall));
		
		calculations.add("Filtering: "+precision+" | "+recall+" | "+accuracy);
		
		try{
			FileWriter writer = new FileWriter(new File("results.txt"));
			for(String entry: calculations){
				System.out.println(entry);
				writer.write(entry+"\n");
			}
			
			writer.close();
		}catch(IOException e){
			e.printStackTrace();
		}
		
		
		//System.out.println(numSteered);
	}
	
	public SteerModel getSteerer(){
		return sp;
	}	

	public void setSteerer(SteerModel st){
		sp=st;
	}
	
	public String getResumeFile(){
		return resumeFile;
	}	

	public void setResumeFile(String file){
		resumeFile=file;
	}
	
	public ArrayList<HashMap<String,Integer>> readResumeFile(String filename){
		ArrayList<HashMap<String,Integer>> previous = new ArrayList<HashMap<String,Integer>>();
		
		HashMap<String,Integer> results = new HashMap<String,Integer>();
		results.put("pp", 0);
		results.put("pf", 0);
		results.put("fitp", 0);
		results.put("fitf", 0);
		results.put("fntp", 0);
		results.put("fntf", 0);
		results.put("ffp", 0);
		results.put("fff", 0);

		HashMap<String,Integer> filterResults = new HashMap<String,Integer>();
		filterResults.put("pp", 0);
		filterResults.put("pf", 0);
		filterResults.put("fitp", 0);
		filterResults.put("fitf", 0);
		filterResults.put("fntp", 0);
		filterResults.put("fntf", 0);
		filterResults.put("ffp", 0);
		filterResults.put("fff", 0);
		
		HashMap<String,Integer> defaultResults = new HashMap<String,Integer>();
		defaultResults.put("pp", 0);
		defaultResults.put("pf", 0);
		defaultResults.put("fitp", 0);
		defaultResults.put("fitf", 0);
		defaultResults.put("fntp", 0);
		defaultResults.put("fntf", 0);
		defaultResults.put("ffp", 0);
		defaultResults.put("fff", 0);
		
		previous.add(results);
		previous.add(defaultResults);
		previous.add(filterResults);
		
		if(!filename.equals("")){
			try{
				BufferedReader reader = new BufferedReader(new FileReader(filename));
				String line="";
				int lineNum=-1;
				int mode=-1;
				
				while((line=reader.readLine())!=null){
					if(line.contains("Steering")){
						mode=0;
						lineNum=-1;
					}else if(line.contains("Default")){
						mode=1;
						lineNum=-1;
					}else if(line.contains("Filtering")){
						mode=2;
						lineNum=-1;
					}else if(!line.contains("-") && mode>-1){
						lineNum++;
						String[] parts=line.split(",");
						if(lineNum==0){
							int r1 = previous.get(mode).get("pp");
							int r2 = previous.get(mode).get("pf");
							previous.get(mode).put("pp", r1+Integer.parseInt(parts[0]));
							previous.get(mode).put("pf", r2+Integer.parseInt(parts[1]));
						}else if(lineNum==1){
							int r1 = previous.get(mode).get("fitp");
							int r2 = previous.get(mode).get("fitf");
							previous.get(mode).put("fitp", r1+Integer.parseInt(parts[0]));
							previous.get(mode).put("fitf", r2+Integer.parseInt(parts[1]));
						}else if(lineNum==2){
							int r1 = previous.get(mode).get("fntp");
							int r2 = previous.get(mode).get("fntf");
							previous.get(mode).put("fntp", r1+Integer.parseInt(parts[0]));
							previous.get(mode).put("fntf", r2+Integer.parseInt(parts[1]));
						}else if(lineNum==3){
							int r1 = previous.get(mode).get("ffp");
							int r2 = previous.get(mode).get("fff");
							previous.get(mode).put("ffp", r1+Integer.parseInt(parts[0]));
							previous.get(mode).put("fff", r2+Integer.parseInt(parts[1]));
						}
					}
				}
				
				reader.close();
			}catch(IOException e){
				System.out.println("Issue with reading file");
			}
		}
		
		return previous;
	}
	
}
