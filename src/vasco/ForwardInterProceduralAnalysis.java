package vasco;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A generic forward-flow inter-procedural analysis which is fully
 * context-sensitive.
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
 */
public abstract class ForwardInterProceduralAnalysis<M,N,A> extends InterProceduralAnalysis<M,N,A> {

	/** Constructs a new forward-flow inter-procedural analysis. */
	public ForwardInterProceduralAnalysis() {
		// Kick-up to the super with the FORWARD direction.
		super(false);

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
							// We want to do IN = IN meet PREDOUT, but to avoid
							// using same object for
							// operand and result, we use a temporary flow
							// object with the TOP value.
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
	 * {@inheritDoc}
	 */
	@Override
	protected A processCall(Context<M,N,A> callerContext, N callNode, M method, A entryFlow) {
		CallSite<M,N,A> callSite = new CallSite<M,N,A>(callerContext, callNode);
		
		// Check if the called method has a context associated with this entry flow:
		Context<M,N,A> calleeContext = getContext(method, entryFlow);
		// If not, then set 'calleeContext' to a new context with the given entry flow.
		if (calleeContext == null) {
			calleeContext = new Context<M,N,A>(method, getControlFlowGraph(method), false);
			initContext(calleeContext, entryFlow);
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

}
