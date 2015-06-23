Model-Based Oracle Steering Framework (Prototype)
-------------------------------------------------
Copyright (c) 2013-15, Gregory Gay (greg@greggay.com). 

This Source Code is subject to the terms of the Mozilla Public License, v. 2.0. 
If a copy of the MPL was not distributed with this file, You can obtain one at 
http://mozilla.org/MPL/2.0/.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
IN THE SOFTWARE.
-------------------------------------------------

INTRODUCTION

The test oracle - a judge of the correctness of the system under test (SUT) - is a major component of the testing process. Specifying test oracles is challenging for some domains, such as real-time embedded systems, where small changes in timing or sensory input may cause large behavioral differences. Models of such systems, often built for analysis and simulation, are appealing for reuse as test oracles. These models, however, typically represent an idealized system, abstracting away certain issues such as non-deterministic timing behavior and sensor noise. Thus, even with the same inputs, the model’s behavior may fail to match an acceptable behavior of the SUT, leading to many false positives reported by the test oracle.

We have implemented an automated steering framework that can adjust the behavior of the model to better match the behavior of the SUT to reduce the rate of false positives. This model steering is limited by a set of constraints (deﬁning the differences in behavior that are acceptable) and is based on a search process attempting to minimize a dissimilarity metric. This framework allows non-deterministic, but bounded, behavioral differences, while preventing future mismatches by guiding the oracle—within limits—to match the execution of the SUT. Results show that steering signiﬁcantly increases SUT-oracle conformance with minimal masking of real faults and, thus, has signiﬁcant potential for reducing false positives and, consequently, testing and debugging costs while improving the quality of the testing proces.

For more background and detailed technical information, see:
[1] Gregory Gay. Automated Steering of Model-Based Test Oracles to Admit Real Program Behaviors. Ph.D. Dissertation, University of Minnesota, May 2015. (included in the docs/ folder)
[2] Gregory Gay, Sanjai Rayadurgam, Mats P.E. Heimdahl. Improving the Accuracy of Oracle Verdicts Through Automated Model Steering. Proceedings of the 29th ACM/IEEE International Conference on Automated Software Engineering (ASE'14). Vasteras, Sweden, September 2014. 
[3] Gregory Gay, Sanjai Rayadurgam, Mats P.E. Heimdahl. Steering Model-Based Oracles to Admit Real Program Behaviors. Proceedings of the 36th ACM/IEEE International Conference on Software Engineering, NIER Track (ICSE'14-NIER). Hyderabad, India, June 2014. 

The typical disclaimer when dealing with academic code applies - this was written as a proof of concept. Repeat - this is a shaky, early-stage prototype intended to demonstrate the feasibility of the idea. This code is being provided as-is, and includes code specifically written for our two case examples. In practice, you would probably want to take our ideas, look at what this code does, and code your own steering framework from scratch. This code has been released to help do that. That said, we would be happy to help you tranfer the idea of oracle model steering into practice, and encourage you to contact us with questions or to start a collaboration.

RUNNING THE CODE

<<introduction>>
In a typical testing scenario that makes use of model-based oracles, a test suite is executed against both the system under test (SUT) and the behavioral model. The values of the input, output, and select internal variables are recorded to a trace ﬁle at certain intervals, such as after each discrete cycle of input and output. Some comparison mechanism examines those trace ﬁles and issues a verdict for each test case (generally a failure if any discrepancies are detected and a pass if a test executes without revealing any differences between the model and SUT). The steering framework will make its own comparison and issue both an initial pass/fail and a final pass/fail. However, this framework does assume that you have already performed the execution and captures a trace file in the correct format.

<<trace file format>>
These trace files take the form of a header defining each variable, then a set of rows where each row defines the value of the variable for each discrete state capture from the system. Each test is separated by a blank line.

variable1,variable2,...,variableN
1,1,...,17
1,2,...,18
(and so forth)

Each test can either repeat the header or leave it off. The "isOffset" input should be set to true if the header is repeated and false if it is not.

<<steering>>

Steering is an additional step added to attempt to override any failing test verdicts. We assume that you have already collected trace files from the SUT and oracle model. The steering framework will then, for each test in your test suite:
1. Compare the model output to the SUT output.
2. If the output does not match, the steering algorithm will instrument the model and attempt to steer the model’s execution within the speciﬁed constraints by searching for an appropriate steering action.
3. Compare the new output of the model to the SUT output and log the ﬁnal dissimilarity score.
4. Issue a ﬁnal verdict for the test.

To steer the oracle model, we instrument the model to match the state it was in during the previous step of execution, formulate the search for a new model state as a boolean satisﬁability problem, and use a powerful search algorithm (jKind) to select a target state to transition the model to. This search is guided by three types of constraints:
1. A set of tolerance constraints limiting the acceptable values for the steering variables - a set of model variables that the steering process is allowed to directly manipulate.
2. A dissimilarity function—a numerical function that compares a candidate model state to the state of the SUT and delivers a numeric score. We seek the candidate solution that minimizes this function.
3. A set of additional policies dictating the limits on steering. There are not implemented currently, but an example would be to require an exact match when steering and not allow steering to "close the gap" between the model and the SUT.

<<running the code>>

This framework has four classes with Main methods, used for various purposes in the proof-of-concept.
- SteerModel
- SteerPacemaker
- InfusionResultChecker
- PacemakerResultChecker

The latter two (the "result checkers") are not related to steering, but rather provide information used in our experiments - including statistics on the accuracy of steering and a application-specific filter baseline (see the included papers for more information). The results checkers have been left in to provide all of our code, but are unlikely to be of much use outside of our own experiment. 

SteerModel and SteerPacemaker are the two that were used to conduct the actual oracle steering. SteerModel is intended to be a generic model steering utility for models written in the Lustre language, and has been used for unpublished work on a Microwave system and the published experiments on the Infusion Pump model. 

SteerPacemaker is a version written to work with the Pacing model, and includes additional functionality specific to working with that model. The primary difference between this and the generic framework is that the other Lustre models used in our work will log state information every second. The Pacing model only logs when state changes occur (on a millisecond level of granularity), and not every ms (this would result in too much information). Once steering begins, we need to generate state update events (that are not explicit environment input) to ensure that all state changes occur at the right time. The additional code in SteerPacemaker automatically produces those state update events.

SteerModel takes in two arguments:
(1) A configuration file that manages the large amount of information used in steering
(2) A boolean variable, isOffSet, stating whether the trace information includes a variable listing with each test (true) or only with the first test (false). 

The configuration file contains the following arguments:
- oracle: Original trace of the model's execution
- sut: Trace of the SUT's execution
- model: Model-based oracle, in the Lustre language
- ods: A list of variables to be used for comparison purposes (often the shared output variables of the model/SUT)
- ids: A list of input variables
- testsuite: Not all tests contained in the trace need to be compared. The testsuite file is a comma-separated list of all tests to be compared and steered (ex: 2,3,4,6 will execute the second, third, fourth, and sixth tests).
- metric: Dissimilarity metric to be used for comparisons between the state of the model and the state of the SUT. Currently, the Manhattan and Squared Euclidean metrics are supported.
- normalization (optional): If the numeric variables should be normalized for comparison, a file containing (on three lines) the variable names, the minimum, and the maximum values should be passed in. For example:
	variable1,variable2,...,variableN
	0,0,...,0
	20,8,...,30
- tolerances: A file containing the constraints on the steering process. These should be a set of boolean Lustre expressions, one per line. For example:
	(real(CONFIG_IN_Patient_Bolus_Duration) >= concrete_oracle_CONFIG_IN_Patient_Bolus_Duration -1.0) and (real(CONFIG_IN_Patient_Bolus_Duration) <= (concrete_oracle_CONFIG_IN_Patient_Bolus_Duration + 2.0))
	(OP_CMD_IN_Infusion_Cancel = concrete_oracle_OP_CMD_IN_Infusion_Cancel)
	(real(CONFIG_IN_Configured) = concrete_oracle_CONFIG_IN_Configured)
	Note that the "concrete_oracle_<name>" variables are inserted when steering as constants containing the original version of the variable's value. There is also a corresponding "concrete_sut_<name>" variable. 

<<dependencies>>

This steering framework depends on the following JAR files, included in the /lib folder:
- jKind (https://github.com/agacek/jkind)
- jKind API (https://github.com/agacek/jkind)
- Lustre Interpreter (in-house, included)

<<demo>>

A demo is included in the /demo directory. To run it, in a bash environment, enter the directory and execute:
./runDemo
