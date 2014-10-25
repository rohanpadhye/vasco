/**
 * Copyright (C) 2013 Rohan Padhye
 * 
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package vasco.callgraph;


import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import soot.PackManager;
import soot.Scene;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.toolkits.callgraph.Edge;
import vasco.CallSite;
import vasco.Context;
import vasco.ContextTransitionTable;

/**
 * A main class for testing call graph construction using a Flow and Context
 * Sensitive Points-to Analysis (FCPA).
 * 
 * <p>Usage: <tt>java vasco.callgraph.CallGraphTest [-cp CLASSPATH] [-out DIR] [-k DEPTH] MAIN_CLASS</tt></p>
 * 
 * @author Rohan Padhye
 */
public class CallGraphTest {
	
	private static String outputDirectory;

	public static void main(String args[]) {
		outputDirectory = ".";
		String classPath = ".";		
		String mainClass = null;
		int callChainDepth = 10;

		/* ------------------- OPTIONS ---------------------- */
		try {
			int i=0;
			while(true){
				if (args[i].equals("-cp")) {
					classPath = args[i+1];
					i += 2;
				} else if (args[i].equals("-out")) {
					outputDirectory = args[i+1];
					i += 2;
				} else if (args[i].equals("-k")) { 
					callChainDepth = Integer.parseInt(args[i+1]);
					i += 2;
				} else {
					mainClass = args[i];
					i++;
					break;
				}
			}
			if (i != args.length || mainClass == null)
				throw new Exception();
		} catch (Exception e) {
			System.out.println("Usage: java vasco.callgraph.CallGraphTest [-cp CLASSPATH] [-out DIR] [-k DEPTH] MAIN_CLASS");
			System.exit(1);
		}

		/* ------------------- SOOT OPTIONS ---------------------- */
		String[] sootArgs = {
				"-cp", classPath, "-pp", 
				"-w", "-app", 
				"-keep-line-number",
				"-keep-bytecode-offset",
				"-p", "cg", "implicit-entry:false",
				"-p", "cg.spark", "enabled",
				"-p", "cg.spark", "simulate-natives",
				"-p", "cg", "safe-forname",
				"-p", "cg", "safe-newinstance",
				"-main-class", mainClass,
				"-f", "none", mainClass 
		};
		

		/* ------------------- ANALYSIS ---------------------- */
		CallGraphTransformer cgt = new CallGraphTransformer();
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.fcpa", cgt));
		soot.Main.main(sootArgs);
		PointsToAnalysis pointsToAnalysis = cgt.getPointsToAnalysis();
		

		
		/* ------------------- LOGGING ---------------------- */
		try {
			printCallSiteStats(pointsToAnalysis);
			printMethodStats(pointsToAnalysis);
			dumpCallChainStats(pointsToAnalysis, callChainDepth);
		} catch (FileNotFoundException e1) {
			System.err.println("Oops! Could not create log file: " + e1.getMessage());
			System.exit(1);
		}
		
	}
	
	public static List<SootMethod> getSparkExplicitEdges(Unit callStmt) {
		Iterator<Edge> edges = Scene.v().getCallGraph().edgesOutOf(callStmt);
		List<SootMethod> targets = new LinkedList<SootMethod>();
		while (edges.hasNext()) {
			Edge edge = edges.next();
			if (edge.isExplicit()) {
				targets.add(edge.tgt());
			}
		}
		return targets;
	}
	
	public static List<SootMethod> getSparkExplicitEdges(SootMethod sootMethod) {
		Iterator<Edge> edges = Scene.v().getCallGraph().edgesOutOf(sootMethod);
		List<SootMethod> targets = new LinkedList<SootMethod>();
		while (edges.hasNext()) {
			Edge edge = edges.next();
			if (edge.isExplicit()) {
				targets.add(edge.tgt());
			}
		}
		return targets;
	}
	
	
	private static Set<SootMethod> dirtyMethods = new HashSet<SootMethod>();
	
	private static void markDirty(Unit defaultSite) {
		List<SootMethod> methods = getSparkExplicitEdges(defaultSite);
		while (methods.isEmpty() == false) {
			SootMethod method = methods.remove(0);
			if (dirtyMethods.contains(method)) {
				continue;
			} else {
				dirtyMethods.add(method);
				methods.addAll(getSparkExplicitEdges(method));
			}
		}
	}
	
	public static void printCallSiteStats(PointsToAnalysis pta) throws FileNotFoundException {
		// Get context-transition table
		ContextTransitionTable<SootMethod,Unit,PointsToGraph> ctt = pta.getContextTransitionTable();
		Map<Context<SootMethod,Unit,PointsToGraph>,Set<CallSite<SootMethod,Unit,PointsToGraph>>> callSitesWithinContexts = ctt.getCallSitesOfContexts();
		Map<CallSite<SootMethod,Unit,PointsToGraph>,Map<SootMethod,Context<SootMethod,Unit,PointsToGraph>>> transitions = ctt.getTransitions();
		Set<CallSite<SootMethod,Unit,PointsToGraph>> defaultCallSites = ctt.getDefaultCallSites();
		
		// Initialise output stream
		PrintWriter csv = new PrintWriter(outputDirectory + "/sites.csv");
		csv.println("FcpaEdges, SparkEdges, Context, CallSite");
		
		// The visited set
		Set<Context<SootMethod,Unit,PointsToGraph>> visited = new HashSet<Context<SootMethod,Unit,PointsToGraph>>();
		
		// Maintain a stack of contexts to process
		Stack<Context<SootMethod,Unit,PointsToGraph>> stack = new Stack<Context<SootMethod,Unit,PointsToGraph>>();
		
		// Initialise it with the main context
		Context<SootMethod,Unit,PointsToGraph> source = pta.getContexts(Scene.v().getMainMethod()).get(0);
		stack.push(source);

		// Now recursively (using stacks) mark reachable contexts
		while (stack.isEmpty() == false) {
			// Get the next item to process
			source = stack.pop();
			// Add successors
			if (callSitesWithinContexts.containsKey(source)) {
				// The above check is there because methods with no calls have no entry
				for (CallSite<SootMethod,Unit,PointsToGraph> callSite : callSitesWithinContexts.get(source)) {
					// My edges are -1 for "default" sites, and whatever the CTT has otherwise
					int myEdges = defaultCallSites.contains(callSite) ? -1 : transitions.get(callSite).size();
					// Get SPARK's edges from the Soot call graph
					int sparkEdges = getSparkExplicitEdges(callSite.getCallNode()).size();
					
					// Log this
					csv.println(myEdges + ", " + sparkEdges + ", " + source + ", " +
							"\"" + callSite.getCallNode() + "\"");

					if (myEdges > 0) {
						for (SootMethod method : transitions.get(callSite).keySet()) {
							Context<SootMethod,Unit,PointsToGraph> target = transitions.get(callSite).get(method);
							// Don't process the same element twice
							if (visited.contains(target) == false) {
								// Mark reachable
								visited.add(target);
								// Add it's successors also later
								stack.push(target);
							}
						}
					} else if (myEdges == -1) {
						// Default call-site, so mark reachable closure as "dirty"
						markDirty(callSite.getCallNode());
					}
				}
			}
			
		}
		// Close the CSV file
		csv.close();		
	}
	
	public static void printMethodStats(PointsToAnalysis pta) throws FileNotFoundException {
		// Initialise output stream
		PrintWriter csv = new PrintWriter(outputDirectory + "/methods.csv");
		csv.println("Method, Contexts, Application?, Dirty?");
		for (SootMethod method : pta.getMethods()) {
			csv.println("\"" + method + "\"" + ", " + pta.getContexts(method).size() +
					", " + (method.getDeclaringClass().isApplicationClass() ? 1 : 0) +
					", " + (dirtyMethods.contains(method) ? 1 : 0));
		}
		
		// Close the CSV file
		csv.close();
	}
	
	public static void dumpCallChainStats(PointsToAnalysis pta, int maxDepth) throws FileNotFoundException {		
		// Initialise output stream
		PrintWriter txt = new PrintWriter(new FileOutputStream(outputDirectory + "/chains.txt"), true);
		Context<SootMethod,Unit,?> mainContext = pta.getContexts(Scene.v().getMainMethod()).get(0);
		SootMethod mainMethod = Scene.v().getMainMethod();
		
		txt.println("FCPA Chains");
		txt.println("------------");
		for(int k=1; k<=maxDepth; k++) {
			txt.println("k=" + k + ": " + countCallChains(pta, mainContext, k));
		}
		txt.println("Spark Chains");
		txt.println("------------");
		for(int k=1; k<=maxDepth; k++) {
			txt.println("k=" + k + ": " + countCallChains(mainMethod, k));
		}
		
		txt.close();	
		
	}
	
	private static long countCallChains(SootMethod method, int k) {
		if (k == 0)
			return 1;
		
		long count = 1;
		Iterator<Edge> edges = Scene.v().getCallGraph().edgesOutOf(method);
		while(edges.hasNext()) {
			Edge edge = edges.next();
			if (edge.isExplicit()) {
				SootMethod target = edge.tgt();
				count = count + countCallChains(target, k-1);
			}
		}
		return count;
	}
	
	private static long countCallChains(PointsToAnalysis pta, Context<SootMethod,Unit,?> context, int k) {		
		if (k == 0)
			return 1;
		
		long count = 1;
		ContextTransitionTable<SootMethod,Unit,?> ctt = pta.getContextTransitionTable();
		if (ctt.getCallSitesOfContexts().containsKey(context)) {
			for (CallSite<SootMethod,Unit,?> callSite : ctt.getCallSitesOfContexts().get(context)) {
				if (ctt.getDefaultCallSites().contains(callSite)) {
					Iterator<Edge> edges = Scene.v().getCallGraph().edgesOutOf(callSite.getCallNode());
					while(edges.hasNext()) {
						SootMethod target = edges.next().tgt();
						count = count + countCallChains(target, k-1);
					}
				} else if (ctt.getTransitions().containsKey(callSite) && ctt.getTransitions().get(callSite) instanceof Map) {
					for (Context<SootMethod,Unit,?> target : ctt.getTransitions().get(callSite).values()) {
						if (target.getMethod().getName().equals("<clinit>")) {
							continue;
						} else {
							count = count + countCallChains(pta, target, k-1);
						}
					}
				}
			}
		}
		
		return count;
		
	}
	
}
