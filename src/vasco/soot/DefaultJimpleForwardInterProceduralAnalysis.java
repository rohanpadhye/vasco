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
package vasco.soot;

import java.util.Iterator;

import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

import vasco.Context;
import vasco.ForwardInterProceduralAnalysis;

/**
 * A forward-flow inter-procedural analysis for Soot using the default context-insensitive
 * call graph to resolve calls.
 * 
 * <p>This class implements the {@link #flowFunction(Context, Unit, Object) flowFunction}
 * method of the base {@link ForwardInterProceduralAnalysis}, handling nodes with
 * calls (invoke expressions) separately from normal intra-procedural statements.</p>
 * 
 * <p>This removes the burden from the client to resolve method calls and instead 
 * exposes four distinct types of flow functions that the client must override for
 * different situations:</p>
 * 
 * <ol>
 * <li>{@link #normalFlowFunction(Unit, Object) normalFlowFunction}:
 *     A flow function for a non-call statement, the result of which flows to the
 *     intra-procedural successor nodes.</li>
 * <li>{@link #callEntryFlowFunction(Unit, SootMethod, Object) callEntryFlowFunction}: 
 *     A flow function for a method call statement, the input to which is the
 *     data flow value just before the call statement, and the result of which flows
 *     to the entry to the called procedure. This can be used, for example, to handle
 *     parameter passing.</li>
 * <li>{@link #callExitFlowFunction(Unit, SootMethod, Object) callExitFlowFunction}: 
 *     A flow function for a method call statement, the input to which is the 
 *     data flow value at the exit of the called procedure, and the result of which
 *     is the inter-procedural component flowing to the successors of the call 
 *     statement. This can be used, for example, to handle return values.</li>
 * <li>{@link #callLocalFlowFunction(Unit, Object) callLocalFlowFunction}: 
 *     A flow function for a method call statement, the input to which is the
 *     data flow value just before the call statement, and the result of which 
 *     is the intra-procedural component flowing to the successors of the call
 *     statement. This can be used, for example, to propagate values not
 *     involved in the method call.</li>
 * </ol>
 * 
 * <p>Thus, for non-call statements, the flow function 
 * ({@link #normalFlowFunction(Unit, Object) normalFlowFunction})
 * is applied only once during a node visit.</p>
 * 
 * <p>For every call statement, the inter-procedural call edge function 
 * ({@link #callEntryFlowFunction(Unit, SootMethod, Object) callEntryFlowFunction}) and
 * the inter-procedural return edge function 
 * ({@link #callExitFlowFunction(Unit, SootMethod, Object) callExitFlowFunction}) 
 * is called once per target method
 * and the intra-procedural call-to-return edge function
 * ({@link #callLocalFlowFunction(Unit, Object) callLocalFlowFunction}) is called once.
 * The result of the call edge function is used to look-up value-contexts
 * or create new ones. 
 * The results of the return-edge function (for each target method) and
 * the result of the local edge function are merged using the 
 * {@link #meet(Object, Object) meet} operation and propagated to the successor.</p>
 * 
 * <p><strong>Note</strong>: This class uses Soot's default context-insensitive
 * call graph (obtained using <code>Scene.v().getCallGraph()</code>) to resolve method
 * calls. If the underlying call graph engine is imprecise, this can lead to a lot
 * of inefficiency and imprecision for the inter-procedural analysis due to spurious
 * call chains.</p>
 * 
 * @author Rohan Padhye
 *
 * @param <A> the type of a data flow value
 */
public abstract class DefaultJimpleForwardInterProceduralAnalysis<A> 
	extends ForwardInterProceduralAnalysis<SootMethod, Unit, A> {

	/**
	 * Processes the flow function of a Jimple statement, resolving calls
	 * using Soot's default call graph. 
	 * 
	 * <p>For statements that do not contain a method call, 
	 * {@link #normalFlowFunction(Unit, Object)} is invoked.</p>
	 * 
	 * <p>For statements containing method calls, there are two components. An
	 * intra-procedural component is computed using 
	 * {@link #callLocalFlowFunction(Unit, Object)}. The
	 * inter-procedural component is computed using 
	 * {@link #callEntryFlowFunction(Unit, SootMethod, Object)} for the edge from the call-site
	 * to the entry of the called method, and 
	 * {@link #callExitFlowFunction(Unit, SootMethod, Object)} for the edge from the exit of
	 * the called method to the return-site. The final result of the flow function
	 * for the call statement is a meet of the intra-procedural component and 
	 * inter-procedural components for all target methods.</p>
	 * 
	 * <p>Target methods are computed using Soot's default context-insensitive call
	 * graph.</p>
	 * 
	 */
	@Override
	protected A flowFunction(Context<SootMethod, Unit, A> context, Unit unit, A inValue) {
		// Handle separately depending on whether or not this is a call statement
		if (((Stmt) unit).containsInvokeExpr()) {
			// Call statement, so initialise result to intra-procedural edge function
			A outValue = callLocalFlowFunction(unit, inValue);
			// Find all targets using Soot's default call graph
			for(Iterator<Edge> it = Scene.v().getCallGraph().edgesOutOf(unit); it.hasNext();) {
				Edge edge = it.next();
				// We consider only explicit edges for now
				if (edge.isExplicit()) {
					SootMethod targetMethod = edge.tgt();
					// Compute the flow function along the call edge to get the value at the method entry
					A entryValue = callEntryFlowFunction(unit, targetMethod, inValue);
					// Process the call (context transitions, etc) and get the value at the method exit
					A exitValue = processCall(context, unit, targetMethod, entryValue);
					// If the called method has not been analyzed, return null
					if (exitValue == null) {
						return null;
					}
					// Otherwise, compute the flow function along the return edge
					A afterCallValue = callExitFlowFunction(unit, targetMethod, exitValue);	
					// Accumulate the result by merging the data flow value after the call
					outValue = meet(outValue, afterCallValue);
				}
			}
			// Return the accumulated data flow value (local-edge + all calls)
			return outValue;
		} else {
			// For statements without method calls, process a normal flow function
			return normalFlowFunction(unit, inValue);
		}
	}


	/**
	 * Processes the intra-procedural flow function of a statement that does 
	 * not contain a method call.
	 * 
	 * @param unit		the statement whose flow function to process
	 * @param inValue	the data flow value before the statement
	 * @return			the data flow value after the statement 
	 */
	protected abstract A normalFlowFunction(Unit unit, A inValue);
	
	/**
	 * Processes the inter-procedural flow function along the call edge 
	 * of a method call.
	 * 
	 * @param unit 			the statement containing the method call
	 * @param calledMethod 	the target (or one of the targets) of this call site
	 * @param inValue 		the data flow value before the call
	 * @return 				the data flow value at the entry to the called procedure
	 */
	protected abstract A callEntryFlowFunction(Unit unit, SootMethod calledMethod, A inValue);
	
	/**
	 * Processes the inter-procedural flow function along the return edge 
	 * of a method call.
	 * 
	 * @param unit 			the statement containing the method call
	 * @param calledMethod 	the target (or one of the targets) of this call site
	 * @param exitValue		the data flow value at the exit of the called procedure
	 * @return				the data flow value after the call (returned component)
	 */
	protected abstract A callExitFlowFunction(Unit unit, SootMethod calledMethod, A exitValue);
	
	/**
	 * 
	 * Processes the intra-procedural flow function of a statement containing a
	 * method call. 
	 * 
	 * @param unit		the statement containing the method call
	 * @param inValue 	the data flow value before the call 
	 * @return			the data flow value after the call (local component)
	 */
	protected abstract A callLocalFlowFunction(Unit unit, A inValue);
	
	/**
	 * Returns an {@link ExceptionalUnitGraph} for the given method.
	 */
	@Override
	public DirectedGraph<Unit> getControlFlowGraph(SootMethod method) {
		return new ExceptionalUnitGraph(method.getActiveBody());
	}

	/**
	 * Returns the main method of the program using <code>Scene.v().getMainMethod()</code>.
	 */
	@Override
	public SootMethod getMainMethod() {
		return Scene.v().getMainMethod();
	}


}
