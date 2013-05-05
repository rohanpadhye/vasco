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

	/** Constructs a new forward inter-procedural analysis. */
	public BackwardInterProceduralAnalysis() {
		// Kick-up to the super with the BACKWARD direction.
		super(true);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doAnalysis() {

		// Initialise the MAIN context
		initContext(getMainContext(), boundaryValue());

		// Stack-of-work-lists data flow analysis.
		while (!analysisStack.isEmpty()) {
			// Get the context at the top of the stack.
			Context<M,N,A> context = analysisStack.peek();

			// Either analyse the next pending unit or pop out of the method
			if (!context.getWorkList().isEmpty()) {
				// work-list contains items; So the next unit to analyse.
				N unit = context.getWorkList().pollFirst();

				if (unit != null) {			
					// Compute the OUT data flow value (only for non-entry units).
					List<N> successors = context.getControlFlowGraph().getSuccsOf(unit);
					if (successors.size() != 0) {
						// Merge all the IN values of the successors
						Iterator<N> succIterator = successors.iterator();
						// Initialise OUT to the IN value of the first successor
						A out = context.getValueBefore(succIterator.next());
						// Then, merge IN of remaining successors with the
						// intermediate OUT value
						while (succIterator.hasNext()) {
							A succIn = context.getValueBefore(succIterator.next());
							out = meet(out, succIn);
						}
						// Set the OUT value at the context
						context.setValueAfter(unit, out);
					}

					// Store the value of IN before the flow function is processed.
					A prevIn = context.getValueBefore(unit);
					
					// Get the value of OUT 
					A out = context.getValueAfter(unit);

					// Now perform the flow function.
					A in = flowFunction(context, unit, out);

					// If the result is null, then no change 
					if (in == null)
						in = prevIn;
					
					// Set the IN value
					context.setValueBefore(unit, in);

					// If the flow function was applied successfully and the IN changed...
					if (in.equals(prevIn) == false) {
						// Then add predecessors to the work-list.
						for (N predecessor : context.getControlFlowGraph().getPredsOf(unit)) {
							context.getWorkList().add(predecessor);
						}
						// If the unit is in HEADS, then we have at least one
						// path to the start of the method, so add the NULL unit
						if (context.getControlFlowGraph().getHeads().contains(unit)) {
							context.getWorkList().add(null);
						}
					} 
				} else {
					// NULL unit, which means the end of the method.
					assert (context.getWorkList().isEmpty());

					// Entry flow value is the merge of the INs of the head nodes.
					A entryFlow = topValue();
					for (N head : context.getControlFlowGraph().getHeads()) {
						A headIn = context.getValueBefore(head);
						entryFlow = meet(entryFlow, headIn);
					}
					// Set the entry flow of the context.
					context.setEntryValue(entryFlow);

					// Mark this context as analysed at least once.
					context.markAnalysed();

					// Add return nodes to stack (only if there were callers).
					Set<CallSite<M,N,A>> callersSet = contextTransitions.getCallers(context);
					if (callersSet != null) {
						List<CallSite<M,N,A>> callers = new LinkedList<CallSite<M,N,A>>(callersSet);
						// Sort the callers in ascending order of their ID so that 
						// the largest ID is on top of the stack
						Collections.sort(callers);
						for (CallSite<M,N,A> callSite : callers) {
							// Extract the calling context and unit from the caller site.
							Context<M,N,A> callingContext = callSite.getCallingContext();
							N callingN = callSite.getCallNode();
							// Add the calling unit to the calling context's work-list.
							callingContext.getWorkList().add(callingN);
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
						// If any reachable contexts exit on the stack, then we cannot free memory
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
	 * <li>Initialise the exit nodes (tails) with a copy of the exit value.</li>
	 * <li>Add exit points to work-list.</li>
	 * <li>Push this context on the top of the analysis stack.</li>
	 * </ol>
	 * </p>
	 * 
	 * @param context the context to initialise
	 * @param exitValue the data flow value at the entry of this method
	 */
	protected void initContext(Context<M,N,A> context, A exitValue) {
		// Get the method
		M method = context.getMethod();

		// First initialise all points to default flow value.
		for (N unit : context.getControlFlowGraph()) {
			context.setValueBefore(unit, topValue());
			context.setValueAfter(unit, topValue());
		}

		// Now, initialise exit points with a copy of the given exit flow.
		context.setExitValue(copy(exitValue));
		for (N unit : context.getControlFlowGraph().getTails()) {
			context.setValueAfter(unit, copy(exitValue));
			// Add exit points to work-list
			context.getWorkList().add(unit);
		}

		// Add this new context to the given method's mapping.
		if (!contexts.containsKey(method)) {
			contexts.put(method, new LinkedList<Context<M,N,A>>());
		}
		contexts.get(method).add(context);

		// Push this context on the top of the analysis stack.
		analysisStack.push(context);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected A processCall(Context<M,N,A> callerContext, N callNode, M method, A exitFlow) {
		CallSite<M,N,A> callSite = new CallSite<M,N,A>(callerContext, callNode);

		// Check if the called method has a context associated with this exit flow:
		Context<M,N,A> calleeContext = getContext(method, exitFlow);
		// If not, then set 'calleeContext' to a new context with the given exit flow.
		if (calleeContext == null) {
			calleeContext = new Context<M,N,A>(method, getControlFlowGraph(method), true);
			initContext(calleeContext, exitFlow);
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
			// If yes, then return the 'entryFlow' of the 'calleeContext'.
			return calleeContext.getEntryValue();
		} else {
			// If not, then return 'null'.
			return null;
		}
	}

}
