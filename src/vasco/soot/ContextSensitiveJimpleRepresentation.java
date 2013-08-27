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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import soot.MethodContext;
import soot.MethodOrMethodContext;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.callgraph.ContextSensitiveEdge;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import vasco.ProgramRepresentation;

/**
 * A program representation for Soot using the Jimple IR with a context-sensitive
 * call graph. This representation uses control-flow graphs of individual units including exceptional 
 * control flow, and resolves virtual calls using the call graph returned by
 * {@link soot.Scene#getContextSensitiveCallGraph() Scene#getContextSensitiveCallGraph}.
 *   
 * <p><strong>Note</strong>: This class follows the Singleton pattern. The singleton 
 * object is available through {@link #v()}.</p>
 * 
 * @author Rohan Padhye
 *
 */
public class ContextSensitiveJimpleRepresentation implements ProgramRepresentation<MethodOrMethodContext, Unit> {
	
	// Cache for control flow graphs
	private Map<SootMethod, DirectedGraph<Unit>> cfgCache;
	
	// Private constructor, see #v() to retrieve singleton object
	private ContextSensitiveJimpleRepresentation() {
		cfgCache = new HashMap<SootMethod, DirectedGraph<Unit>>();
	}
	
	/**
	 * Returns a singleton list containing the <code>main</code> method.
	 * @see Scene#getMainMethod()
	 */
	@Override
	public List<MethodOrMethodContext> getEntryPoints() {
		return Collections.singletonList(MethodContext.v(Scene.v().getMainMethod(), null));
	}

	/**
	 * Returns an {@link ExceptionalUnitGraph} for a given method.
	 */
	@Override
	public DirectedGraph<Unit> getControlFlowGraph(MethodOrMethodContext momc) {
		if (cfgCache.containsKey(momc.method()) == false) {
			cfgCache.put(momc.method(), new ExceptionalUnitGraph(momc.method().getActiveBody()));
		}
		return cfgCache.get(momc.method());
	}

	/**
	 * Returns <code>true</code> iff the Jimple statement contains an
	 * invoke expression.
	 */
	@Override
	public boolean isCall(Unit node) {
		return ((soot.jimple.Stmt) node).containsInvokeExpr();
	}

	/**
	 * Resolves virtual calls using the Soot's context-sensitive call graph and returns
	 * a list of method-contexts which are the targets of explicit edges.
	 */
	@Override
	public List<MethodOrMethodContext> resolveTargets(MethodOrMethodContext momc, Unit node) {
		List<MethodOrMethodContext> targets = new LinkedList<MethodOrMethodContext>();
		@SuppressWarnings("rawtypes")
		Iterator it = Scene.v().getContextSensitiveCallGraph().edgesOutOf(momc.context(), momc.method(), node);
		while(it.hasNext()) {
			ContextSensitiveEdge edge = (ContextSensitiveEdge) it.next();
			if (edge.kind().isExplicit()) {
				targets.add(MethodContext.v(edge.tgt(), edge.tgtCtxt()));
			}
		}
		return targets;
	}

	// The singleton object
	private static ContextSensitiveJimpleRepresentation singleton = new ContextSensitiveJimpleRepresentation();
	
	/**
	 * Returns a reference to the singleton object of this class.
	 */
	public static ContextSensitiveJimpleRepresentation v() { return singleton; }
	
}
