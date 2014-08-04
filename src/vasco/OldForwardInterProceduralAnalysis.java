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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * A generic forward-flow inter-procedural analysis which is fully context-sensitive. 
 * 
 * <p>
 * This class essentially captures a forward data flow problem which can be
 * solved using the context-sensitive inter-procedural analysis framework as
 * described in {@link InterProceduralAnalysis}.
 * </p>
 * 
 * <p>
 * This is the class that client analyses will extend in order to perform
 * forward-flow inter-procedural analysis.
 * </p>
 * 
 * @author Rohan Padhye
 * 
 * @param <M> the type of a method
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 * 
 * @deprecated This is the old API from the initial SOAP '13 submission without call/return flow functions. 
 * It is only here for a temporary period while the {@link vasco.callgraph.PointsToAnalysis PointsToAnalysis} class is migrated to the new API.
 * After that work is done, this class will be permanently removed from VASCO. 
 */
public abstract class OldForwardInterProceduralAnalysis<M,N,A> extends InterProceduralAnalysis<M,N,A> {

	/** Constructs a new forward-flow inter-procedural analysis. */
	public OldForwardInterProceduralAnalysis() {
		// Kick-up to the super with the FORWARD direction.
		super(false);
		analysisStack = new Stack<Context<M,N,A>>();
	}
	
	protected Stack<Context<M,N,A>> analysisStack;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doAnalysis() {

		// Initialise the MAIN context
		for (M entryPoint : programRepresentation().getEntryPoints()) {
			Context<M,N,A> context = new Context<M,N,A>(entryPoint, programRepresentation().getControlFlowGraph(entryPoint), false);
			A boundaryInformation = boundaryValue(entryPoint);
			initContext(context, boundaryInformation);
			
		}

		// Stack-of-work-lists data flow analysis.
		while (!analysisStack.isEmpty()) {
			// Get the context at the top of the stack.
			Context<M,N,A> context = analysisStack.peek();

			// Either analyse the next pending unit or pop out of the method
			if (!context.getWorkList().isEmpty()) {
				// work-list contains items; So the next unit to analyse.
				N unit = context.getWorkList().pollFirst();

				if (unit != null) {
					// Compute the IN data flow value (only for non-entry units).
					List<N> predecessors = context.getControlFlowGraph().getPredsOf(unit);
					if (predecessors.size() != 0) {
						// Merge all the OUT values of the predecessors
						Iterator<N> predIterator = predecessors.iterator();
						// Initialise IN to the OUT value of the first predecessor
						A in = context.getValueAfter(predIterator.next());
						// Then, merge OUT of remaining predecessors with the
						// intermediate IN value
						while (predIterator.hasNext()) {
							A predOut = context.getValueAfter(predIterator.next());
							in = meet(in, predOut);
						}
						// Set the IN value at the context
						context.setValueBefore(unit, in);
					}
					
					// Store the value of OUT before the flow function is processed.
					A prevOut = context.getValueAfter(unit);
					
					// Get the value of IN 
					A in = context.getValueBefore(unit);

					// Now perform the flow function.
					A out = flowFunction(context, unit, in);

					// If the result is null, then no change 
					if (out == null)
						out = prevOut;
					
					// Set the OUT value
					context.setValueAfter(unit, out);
					
					// If the flow function was applied successfully and the OUT changed...
					if (out.equals(prevOut) == false) {
						// Then add successors to the work-list.
						for (N successor : context.getControlFlowGraph().getSuccsOf(unit)) {
							context.getWorkList().add(successor);
						}
						// If the unit is in TAILS, then we have at least one
						// path to the end of the method, so add the NULL unit
						if (context.getControlFlowGraph().getTails().contains(unit)) {
							context.getWorkList().add(null);
						}
					}
				} else {
					// NULL unit, which means the end of the method.
					assert (context.getWorkList().isEmpty());

					// Exit flow value is the merge of the OUTs of the tail nodes.
					A exitFlow = topValue();
					for (N tail : context.getControlFlowGraph().getTails()) {
						A tailOut = context.getValueAfter(tail);
						exitFlow = meet(exitFlow, tailOut);
					}
					// Set the exit flow of the context.
					context.setExitValue(exitFlow);
					
					// Mark this context as analysed at least once.
					context.markAnalysed();

					// Add return nodes to stack (only if there were callers).
					Set<CallSite<M,N,A>> callersSet =  contextTransitions.getCallers(context);
					if (callersSet != null) {
						List<CallSite<M,N,A>> callers = new LinkedList<CallSite<M,N,A>>(callersSet);
						// Sort the callers in ascending order of their ID so that 
						// the largest ID is on top of the stack
						Collections.sort(callers);
						for (CallSite<M,N,A> callSite : callers) {
							// Extract the calling context and unit from the caller site.
							Context<M,N,A> callingContext = callSite.getCallingContext();
							N callingNode = callSite.getCallNode();
							// Add the calling unit to the calling context's work-list.
							callingContext.getWorkList().add(callingNode);
							// Ensure that the calling context is on the analysis stack,
							// and if not, push it on to the stack.
							if (!analysisStack.contains(callingContext)) {
								analysisStack.push(callingContext);
							}
						}
					}
					
					// Free memory on-the-fly if not needed
					if (freeResultsOnTheFly) {
						Set<Context<M,N,A>> reachableContexts = contextTransitions.reachableSet(context, true);
						// If any reachable contexts exist on the stack, then we cannot free memory
						boolean canFree = true;
						for (Context<M,N,A> reachableContext : reachableContexts) {
							if (analysisStack.contains(reachableContext)) {
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
			} else {
				// If work-list is empty, then remove it from the analysis.
				analysisStack.remove(context);
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
	 * Creates a new context and initialises data flow values.
	 * 
	 * <p>
	 * The following steps are performed:
	 * <ol>
	 * <li>Initialise all nodes to default flow value (lattice top).</li>
	 * <li>Initialise the entry nodes (heads) with a copy of the entry value.</li>
	 * <li>Add entry points to work-list.</li>
	 * <li>Push this context on the top of the analysis stack.</li>
	 * </ol>
	 * </p>
	 * 
	 * @param context the context to initialise
	 * @param entryValue the data flow value at the entry of this method
	 */
	protected void initContext(Context<M,N,A> context, A entryValue) {
		// Get the method
		M method = context.getMethod();

		// First initialise all points to default flow value.
		for (N unit : context.getControlFlowGraph()) {
			context.setValueBefore(unit, topValue());
			context.setValueAfter(unit, topValue());
		}

		// Now, initialise entry points with a copy of the given entry flow.
		context.setEntryValue(copy(entryValue));
		for (N unit : context.getControlFlowGraph().getHeads()) {
			context.setValueBefore(unit, copy(entryValue));
			// Add entry points to work-list
			context.getWorkList().add(unit);
		}

		// Add this new context to the given method's mapping.
		if (!contexts.containsKey(method)) {
			contexts.put(method, new LinkedList<Context<M,N,A>>());
		}
		contexts.get(method).add(context);

		// Push this context on the top of the analysis stack.
		analysisStack.add(context);

	}

	/**
	 * Processes a call statement.
	 * 
	 * <p>
	 * Retrieves a value context for the callee if one exists with the given
	 * entry value, or else creates a new one and adds the transition to
	 * the context transition table.
	 * </p>
	 * 
	 * <p>
	 * If the callee context has already been analysed, returns the resulting 
	 * exit value. For newly created contexts the result would be <tt>null</tt>,
	 * as they are obviously not analysed even once.
	 * </p>
	 * 
	 * <p>
	 * Note that this method is not directly called by {@link #doAnalysis() doAnalysis}, but
	 * is instead called by {@link #flowFunction(Context, Object, Object) flowFunction} when a method
	 * call statement is encountered. The reason for this design decision is
	 * that client analyses may want their own setup and tear down sequences
	 * before a call is made (similar to edge flow functions at the call and
	 * return site). Also, analyses may want to choose which method call to
	 * process at an invoke statement in the case of virtual calls (e.g. a
	 * points-to analysis may build the call-graph on-the-fly).
	 * </p>
	 * 
	 * <p>Therefore, it is the responsibility of the client analysis to detect
	 * an invoke expression when implementing {@link #flowFunction(Context, Object, Object) flowFunction},
	 * and suitably invoke {@link #processCall(Context, Object, Object, Object) processCall} with 
	 * the input data flow value which may be different from the IN/OUT value of the node due to
	 * handling of arguments, etc. Similarly, the result of {@link #processCall(Context, Object, Object, Object) processCall}
	 * may be modified by the client to handle return values, etc. before returning from  {@link #flowFunction(Context, Object, Object) flowFunction}.
	 * Ideally,  {@link #flowFunction(Context, Object, Object) flowFunction} should return
	 * <tt>null</tt> if and only if {@link #processCall(Context, Object, Object, Object) processCall} 
	 * returns <tt>null</tt>.
	 * 
	 * @param callerContext the analysis context at the call-site
	 * @param callNode the calling statement
	 * @param method the method being called
	 * @param entryValue the data flow value at the entry of the called method.
	 * @return the data flow value at the exit of the called method,
	 *         if available, or <tt>null</tt> if unavailable.
	 */
	protected A processCall(Context<M,N,A> callerContext, N callNode, M method, A entryValue) {
		CallSite<M,N,A> callSite = new CallSite<M,N,A>(callerContext, callNode);
		
		// Check if the called method has a context associated with this entry flow:
		Context<M,N,A> calleeContext = getContext(method, entryValue);
		// If not, then set 'calleeContext' to a new context with the given entry flow.
		if (calleeContext == null) {
			calleeContext = new Context<M,N,A>(method, programRepresentation().getControlFlowGraph(method), false);
			initContext(calleeContext, entryValue);
			if (verbose) {
				System.out.println("[NEW] X" + callerContext + " -> X" + calleeContext + " " + method + " ");
			}
		}

		// Store the transition from the calling context and site to the called context.
		contextTransitions.addTransition(callSite, calleeContext);

		// Check if 'caleeContext' has been analysed (surely not if it is just newly made):
		if (calleeContext.isAnalysed()) {
			if (verbose) {
				System.out.println("[HIT] X" + callerContext + " -> X" + calleeContext + " " + method + " ");
			}
			// If yes, then return the 'exitFlow' of the 'calleeContext'.
			return calleeContext.getExitValue();
		} else {
			// If not, then return 'null'.
			return null;
		}
	}
	
	protected abstract A flowFunction(Context<M,N,A> context, N unit, A in); 

}
