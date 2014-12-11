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
package vasco;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A generic backward-flow inter-procedural analysis which is fully
 * context-sensitive.
 * 
 * <p>
 * This class essentially captures a backward data flow problem which can be
 * solved using the context-sensitive inter-procedural analysis framework as
 * described in {@link InterProceduralAnalysis}.
 * </p>
 * 
 * <p>
 * This is the class that client analyses will extend in order to perform
 * backward-flow inter-procedural analysis.
 * </p>
 * 
 * @author Rohan Padhye
 * 
 * @param <M> the type of a method
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 */
public abstract class BackwardInterProceduralAnalysis<M,N,A> extends InterProceduralAnalysis<M,N,A> {

	/** Constructs a new backward-flow inter-procedural analysis. */
	public BackwardInterProceduralAnalysis() {
		// Kick-up to the super with the BACKWARD direction.
		super(true);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doAnalysis() {

		// Initial contexts
		for (M method : programRepresentation().getEntryPoints()) {
			initContext(method, boundaryValue(method));
		}

		// Perform work-list based analysis
		while (!worklist.isEmpty()) {
			// Get the newest context on the work-list
			Context<M,N,A> currentContext = worklist.last();
			
			// If this context has no more nodes to analyze, then take it out of the work-list
			if (currentContext.getWorkList().isEmpty()) {
				worklist.remove(currentContext);
				continue;
			}


			// Remove the next node to process from the context's work-list
			N node = currentContext.getWorkList().pollFirst();

			if (node != null) {
				// Compute the OUT data flow value (only for non-exit units).
				List<N> successors = currentContext.getControlFlowGraph().getSuccsOf(node);
				if (successors.size() != 0) {
					// Initialise to the TOP value
					A out = topValue();					
					// Merge IN values of all successors
					for (N succ : successors) {
						A succIn = currentContext.getValueBefore(succ);
						out = meet(out, succIn);
					}					
					// Set the OUT value at the node to the result
					currentContext.setValueAfter(node, out);
				}
				
				// Store the value of IN before the flow function is processed.
				A prevIn = currentContext.getValueBefore(node);
				
				// Get the value of OUT 
				A out = currentContext.getValueAfter(node);
				

				//System.out.println("OUT(" + node + ") = " + out);
				
				// Now to compute the IN value
				A in;
				
				// Handle flow functions depending on whether this is a call statement or not
				if (programRepresentation().isCall(node)) {
					
					in = topValue();
					
					boolean hit = false;
					
					for (M targetMethod : programRepresentation().resolveTargets(currentContext.getMethod(), node)) {
						A exitValue = callExitFlowFunction(currentContext, targetMethod, node, out);
						
						CallSite<M,N,A> callSite = new CallSite<M,N,A>(currentContext, node);
						
						// Check if the called method has a context associated with this exit flow:
						Context<M,N,A> targetContext = getContext(targetMethod, exitValue);
						// If not, then set 'targetContext' to a new context with the given exit flow.
						if (targetContext == null) {
							targetContext = initContext(targetMethod, exitValue);
							if (verbose) {
								System.out.println("[NEW] X" + currentContext + " -> X" + targetContext + " " + targetMethod + " ");
							}
							//System.out.println("EXIT(X"+targetContext+") = " + exitValue);
						}

						// Store the transition from the calling context and site to the called context.
						contextTransitions.addTransition(callSite, targetContext);

						// Check if the target context has been analysed (surely not if it is just newly made):
						if (targetContext.isAnalysed()) {
							hit = true;
							if (verbose) {
								System.out.println("[HIT] X" + currentContext + " -> X" + targetContext + " " + targetMethod + " ");
							}
							A entryValue = targetContext.getEntryValue();
							//System.out.println("ENTRY(X"+targetContext+") = " + entryValue);
							
							A callValue = callEntryFlowFunction(currentContext, targetMethod, node, entryValue);
							
							in = meet(in, callValue);
							
						} 
					}
					
					// If there was at least one hit, continue propagation
					if (hit) {
						A localValue = callLocalFlowFunction(currentContext, node, out); 
						in = meet(in, localValue);
					}
				} else {
					in = normalFlowFunction(currentContext, node, out);
				}
				
				//System.out.println("IN(" + node + ") = " + in);
				//System.out.println("---------------------------------------");

				// Merge with previous IN to force monotonicity (harmless if flow functions are monotonic)
				in = meet(in, prevIn);
				
				// Set the IN value
				currentContext.setValueBefore(node, in);
				
				// If IN has changed...
				if (in.equals(prevIn) == false) {
					// Then add predecessors to the work-list.
					for (N predecessors : currentContext.getControlFlowGraph().getPredsOf(node)) {
						currentContext.getWorkList().add(predecessors);
					}
				}
				// If the unit is in HEADS, then we have at least one
				// path to the start of the method, so add the NULL unit
				if (currentContext.getControlFlowGraph().getHeads().contains(node)) {
					currentContext.getWorkList().add(null);
				}
			} else {
				// NULL unit, which means the end of the method.
				assert (currentContext.getWorkList().isEmpty());

				// Entry value is the merge of the INs of the head nodes.
				A entryValue = topValue();
				for (N headNode : currentContext.getControlFlowGraph().getHeads()) {
					A headIn = currentContext.getValueBefore(headNode);
					entryValue = meet(entryValue, headIn);
				}
				
				// Set the entry value of the context.
				currentContext.setEntryValue(entryValue);
				
				// Mark this context as analysed at least once.
				currentContext.markAnalysed();

				// Add callers to work-list, if any
				Set<CallSite<M,N,A>> callers =  contextTransitions.getCallers(currentContext);
				if (callers != null) {
					for (CallSite<M,N,A> callSite : callers) {
						// Extract the calling context and node from the caller site.
						Context<M,N,A> callingContext = callSite.getCallingContext();
						N callNode = callSite.getCallNode();
						// Add the calling unit to the calling context's node work-list.
						callingContext.getWorkList().add(callNode);
						// Ensure that the calling context is on the context work-list.
						worklist.add(callingContext);
					}
				}
				
				// Free memory on-the-fly if not needed
				if (freeResultsOnTheFly) {
					Set<Context<M,N,A>> reachableContexts = contextTransitions.reachableSet(currentContext, true);
					// If any reachable contexts exist on the work-list, then we cannot free memory
					boolean canFree = true;
					for (Context<M,N,A> reachableContext : reachableContexts) {
						if (worklist.contains(reachableContext)) {
							canFree = false;
							break;
						}
					}
					// If no reachable contexts on the stack, then free memory associated
					// with this context
					if (canFree) {
						for (Context<M,N,A> reachableContext : reachableContexts) {
							reachableContext.freeMemory();
						}
					}
				}					
			}
			
		}
		
		// Sanity check
		for (List<Context<M,N,A>> contextList : contexts.values()) {
			for (Context<M,N,A> context : contextList) {
				if (context.isAnalysed() == false) {
					System.err.println("*** ATTENTION ***: Only partial analysis of X" + context + 
							" " + context.getMethod());
				}
			}			
		}
	}

	/**
	 * Creates a new value context and initialises data flow values for its nodes.
	 * 
	 * <p>
	 * The following steps are performed:
	 * <ol>
	 * <li>Construct the context.</li>
	 * <li>Initialise IN/OUT for all nodes and add them to the work-list</li>
	 * <li>Initialise the OUT of exit points with a copy of the given exit value.</li>
	 * <li>Add this new context to the given method's mapping.</li>
	 * <li>Add this context to the global work-list.</li>
	 * </ol>
	 * </p>
	 * 
	 * @param method the method whose context to create
	 * @param exitValue the data flow value at the exit of this method
	 */
	protected Context<M,N,A> initContext(M method, A exitValue) {
		// Construct the context
		Context<M,N,A> context = new Context<M,N,A>(method, programRepresentation().getControlFlowGraph(method), true);

		// Initialise IN/OUT for all nodes and add them to the work-list
		for (N unit : context.getControlFlowGraph()) {
			context.setValueBefore(unit, topValue());
			context.setValueAfter(unit, topValue());
			context.getWorkList().add(unit);
		}

		// Now, initialise the OUT of exit points with a copy of the given exit value.
		context.setExitValue(copy(exitValue));
		for (N unit : context.getControlFlowGraph().getTails()) {
			context.setValueAfter(unit, copy(exitValue));
		}
		context.setEntryValue(topValue());

		// Add this new context to the given method's mapping.
		if (!contexts.containsKey(method)) {
			contexts.put(method, new LinkedList<Context<M,N,A>>());
		}
		contexts.get(method).add(context);
		
		// Add this context to the global work-list
		worklist.add(context);
		
		return context;

	}

	/**
	 * Processes the intra-procedural flow function of a statement that does 
	 * not contain a method call.
	 * 
	 * @param context   the value context at the call-site
	 * @param node      the statement whose flow function to process
	 * @param outValue  the data flow value after the statement
	 * @return          the data flow value before the statement 
	 */
	public abstract A normalFlowFunction(Context<M,N,A> context, N node, A outValue);
	
	/**
	 * Processes the inter-procedural flow function for a method call at
	 * the start of the call, to handle parameters.
	 * 
	 * @param context       the value context at the call-site
	 * @param targetMethod  the target (or one of the targets) of this call site
	 * @param node          the statement containing the method call
	 * @param entryValue    the data flow value at the entry to the called procedure
	 * @return              the data flow value before the call (call component) 
	 */
	public abstract A callEntryFlowFunction(Context<M,N,A> context, M targetMethod, N node, A entryValue);
	
	/**
	 * Processes the inter-procedural flow function for a method call at the
	 * end of the call, to handle return values.
	 * 
	 * @param context       the value context at the call-site
	 * @param targetMethod  the target (or one of the targets) of this call site
	 * @param node          the statement containing the method call
	 * @param outValue      the data flow value after the call
	 * @return              the data flow value at the exit of the called procedure
	 */
	public abstract A callExitFlowFunction(Context<M,N,A> context, M targetMethod, N node, A outValue);
	
	/**
	 * 
	 * Processes the intra-procedural flow function for a method call at the
	 * call-site itself, to handle propagation of local values that are not
	 * involved in the call.
	 * 
	 * @param context    the value context at the call-site
	 * @param node       the statement containing the method call
	 * @param outValue   the data flow value after the call 
	 * @return           the data flow value before the call (local component)
	 */
	public abstract A callLocalFlowFunction(Context<M,N,A> context, N node, A outValue);
	


	
}
