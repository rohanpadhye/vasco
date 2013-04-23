/**
 * Copyright (C) 2013 Rohan Padhye
 * 
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package vasco;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import soot.toolkits.graph.DirectedGraph;

/**
 * A generic inter-procedural analysis which is fully context-sensitive.
 * 
 * <p>
 * This class is a base for forward and backward inter-procedural analysis
 * classes. This inter-procedural analysis framework is fully context
 * sensitive even in the presence of recursion and uses data flow values
 * reaching a method to distinguish contexts.
 * </p>
 * 
 * @author Rohan Padhye
 * 
 * @param <M> the type of a method
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 * 
 * @see ForwardInterProceduralAnalysis
 * @see BackwardInterProceduralAnalysis
 * @see Context
 * @see ContextTransitionTable
 * @see CallSite
 */
public abstract class InterProceduralAnalysis<M,N,A> {

	/** A stack of contexts to analyse. */
	protected final Stack<Context<M,N,A>> analysisStack;

	/** A mapping from methods to a list of contexts for quick lookups. */
	protected final Map<M,List<Context<M,N,A>>> contexts;

	/**
	 * A record of transitions from calling context and call-site to 
	 * called method and called context.
	 */
	protected final ContextTransitionTable<M,N,A> contextTransitions;

	/**
	 * A reference to the analysis context of the <tt>main</tt> method.
	 */
	protected Context<M,N,A> mainContext;

	/**
	 * <tt>true</tt> if the direction of analysis is backward, or <tt>false</tt>
	 * if it is forward.
	 */
	protected final boolean reverse;

	/**
	 * A flag, if set, directs the analysis to free memory storing
	 * data flow values of individual statements once a context has
	 * been analysed and would not be required to be re-analysed.
	 * 
	 * <p>This setting is only useful for analyses that aggregate secondary
	 * results on the fly (e.g. call graph analysis that can do away with
	 * points-to information once the calls for a particular context have
	 * been resolved). This is not safe for use in analyses whose results
	 * will be directly used later (e.g. liveness analysis).</p>
	 * 
	 * <p>Memory is freed when a context is popped off the stack and no context
	 * reachable from it in the transition table is also on the stack. This 
	 * ensures that the popped context will not be pushed again on the stack
	 * for re-analysis of any statement.</p>
	 * 
	 * <p>Note that the data flow values at the entry/exit of the context are
	 * not freed, and hence it is still used to terminate recursion or as a cache
	 * hit for arbitrary call sites with same values.</p>
	 * 
	 * <p>The default value for this flag is <tt>false</tt>.</p>
	 * 
	 */
	protected boolean freeResultsOnTheFly;
	
	/**
	 * Whether to print information about contexts.
	 */
	protected boolean verbose;
	
	/**
	 * Constructs a new interprocedural analysis.
	 * 
	 * @param reverse <tt>true</tt> if the analysis is in the reverse direction,
	 *            <tt>false</tt> if it is in the forward direction
	 */
	public InterProceduralAnalysis(boolean reverse) {

		// Set direction
		this.reverse = reverse;

		// Initialise map of methods to contexts.
		contexts = new HashMap<M,List<Context<M,N,A>>>();

		// Initialise context transition table
		contextTransitions = new ContextTransitionTable<M,N,A>();

		// Initialise the analysis stack.
		analysisStack = new Stack<Context<M,N,A>>();

	}

	/**
	 * Returns the initial data flow value at the entry (exit) of the program.
	 * 
	 * @return the data flow value at the boundary
	 */
	public abstract A boundaryValue();

	/**
	 * Returns a copy of the given data flow value.
	 * 
	 * @param src the data flow value to copy
	 * @return a new data flow value which is a copy of the argument
	 */
	public abstract A copy(A src);

	/**
	 * Performs the actual data flow analysis.
	 * 
	 * <p>
	 * A stack of contexts is maintained, each with it's own work-list of CFG nodes
	 * to process. For each node removed from the work-list of the context at the top 
	 * of the stack, the meet of values along incoming edges (in the direction of analysis) is computed and
	 * then the flow function is processed by invoking {@link #flowFunction(Context, Object, Object)}.
	 * If the resulting data flow value has changed, then nodes along outgoing edges
	 * (in the direction of analysis) are also added to the work-list. </p>
	 * 
	 * <p>
	 * Analysis starts with the context for the main method with the boundary value
	 * as the first element on the stack.
	 * </p>
	 * 
	 * <p>
	 * When a work-list is empty (i.e. all its node are processed), it's context is popped
	 * off the stack. Memory is freed if {@link #freeResultsOnTheFly} is set and if no
	 * reachable contexts are also on the stack. 
	 * </p>
	 * 
	 * <p>Inter-procedural analysis concludes when the stack is empty.</p>
	 * 
	 * @see #flowFunction(Context, Object, Object)
	 * @see #getMainMethod()
	 * @see #boundaryValue()
	 * 
	 */
	public abstract void doAnalysis();

	/**
	 * Processes a node of the control flow graph.
	 * 
	 * <p>
	 * The flow function processes a data flow value at one end of a given node
	 * for a given context and results in a data flow value at the other
	 * end. 
	 * </p>
	 * 
	 * <p>
	 * Implementations will invoke
	 * {@link #processCall(Context, Object, Object, Object) processCall} to handle
	 * procedure calls when encountered.
	 * </p>
	 * 
	 * <p>If the flow function returns <tt>null</tt>, the data flow value
	 * is not changed (that is, the previous value at that point is retained).
	 * Ideally, the flow  function returns <tt>null</tt> if and only if 
	 * {@link #processCall(Context, Object, Object, Object) processCall} returns <tt>null</tt>.
	 * 
	 * @param context the context which is being analysed
	 * @param node a node in the control flow graph
	 * @param value the data flow value to process
	 * @return the result of the flow function if successful, or <tt>null</tt> if no result is available
	 */
	protected abstract A flowFunction(Context<M,N,A> context, N node, A value);

	/**
	 * Returns the callers of a value context.
	 * 
	 * @param target the value context
	 * @return the call-sites which transition to the analysis context
	 */
	public Set<CallSite<M,N,A>> getCallers(Context<M,N,A> target) {
		return this.contextTransitions.getCallers(target);
	}

	/**
	 * Retrieves a particular value context if it has been constructed.
	 * 
	 * @param method the method whose value context to find
	 * @param value the data flow value at the entry (forward flow) or exit
	 *            (backward flow) of the method
	 * @return the value context, if one is found with the given parameters,
	 *         or <tt>null</tt> otherwise
	 */
	public Context<M,N,A> getContext(M method, A value) {
		// If this method does not have any contexts, then we'll have to return nothing.
		if (!contexts.containsKey(method)) {
			return null;
		}
		// Otherwise, look for a context in this method's list with the given value.
		if (reverse) {
			// Backward flow, so check for EXIT FLOWS
			for (Context<M,N,A> context : contexts.get(method)) {
				if (value.equals(context.getExitValue())) {
					return context;
				}
			}
		} else {
			// Forward flow, so check for ENTRY FLOWS
			for (Context<M,N,A> context : contexts.get(method)) {
				if (value.equals(context.getEntryValue())) {
					return context;
				}
			}
		}
		// If nothing found return null.
		return null;
	}

	/**
	 * Returns a list of value contexts constructed for a given method.
	 * 
	 * @param method the method whose contexts to retrieve
	 * @return an unmodifiable list of value contexts of the given method
	 */
	public List<Context<M,N,A>> getContexts(M method) {
		if (contexts.containsKey(method)) {
			return Collections.unmodifiableList(contexts.get(method));
		} else {
			return Collections.unmodifiableList(new LinkedList<Context<M,N,A>>());
		}
	}
	
	/**
	 * Returns all methods for which at least one context was created.
	 * @return an unmodifiable set of analysed methods
	 */
	public Set<M> getMethods() {
		return Collections.unmodifiableSet(contexts.keySet());
	}

	/**
	 * Returns a control flow graph for the given method.
	 * 
	 * @param method the method whose CFG to construct
	 * @return a control flow graph for the given method
	 */
	public abstract DirectedGraph<N> getControlFlowGraph(M method);

	/**
	 * Returns a reference to the main context.
	 * 
	 * @return the value context for the <tt>main</tt> method
	 */
	public Context<M,N,A> getMainContext() {
		// Ensure the singleton has been created
		if (this.mainContext == null) {
			M mainMethod = getMainMethod();
			this.mainContext = new Context<M,N,A>(mainMethod, getControlFlowGraph(mainMethod), reverse);
		}
		// Return the singleton
		return this.mainContext;
	}

	/**
	 * Returns a reference to the <tt>main</tt> method.
	 * 
	 * @return a reference to the <tt>main</tt> method.
	 */
	public abstract M getMainMethod();

	/**
	 * Returns the target of a call-site.
	 * 
	 * @param callSite the call-site whose targets to retrieve
	 * @return a map of target methods to their respective contexts
	 */
	public Map<M,Context<M,N,A>> getTargets(CallSite<M,N,A> callSite) {
		return this.contextTransitions.getTargets(callSite);
	}

	/**
	 * Returns a reference to the context transition table used by this analysis.
	 * 
	 * @return a reference to the context transition table used by this analysis
	 */
	public ContextTransitionTable<M,N,A> getContextTransitionTable() {
		return contextTransitions;
	}
	
	/**
	 * Returns the meet of two data flow values.
	 * 
	 * @param op1 the first operand
	 * @param op2 the second operand
	 * @return a new data flow which is the result of the meet operation of the
	 *         two operands
	 */
	public abstract A meet(A op1, A op2);

	/**
	 * Processes a call statement.
	 * 
	 * <p>
	 * Retrieves a value context for the callee if one exists with the given
	 * entry (exit) flow, or else creates a new one and adds the transition to
	 * the context transition table.
	 * </p>
	 * 
	 * <p>
	 * If the callee context has already been analysed, returns the resulting 
	 * value (which may be the exit or entry flow depending on the analysis
	 * direction). For newly created contexts the result would be <tt>null</tt>,
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
	 * @param calledMethod the method being called
	 * @param value the data flow value at the entry (exit) of the called method for a
	 *            forward (backward) analysis.
	 * @return the data flow value at the exit (entry) of the called method for
	 *         a forward (backward) analysis, if available, or <tt>null</tt> if
	 *         unavailable.
	 */
	protected abstract A processCall(Context<M,N,A> callerContext, N callNode, M calledMethod, A value);

	/**
	 * Returns the default data flow value (lattice top).
	 * 
	 * @return the default data flow value (lattice top)
	 */
	public abstract A topValue();

}
