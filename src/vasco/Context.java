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

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.SlowPseudoTopologicalOrderer;
import soot.toolkits.scalar.Pair;

/**
 * A value-based context for a context-sensitive inter-procedural data flow
 * analysis.
 * 
 * <p>
 * A value-based context is identified as a pair of a method and the data
 * flow value at the entry of the method, for forward flows, or the data
 * flow value at the exit of the method, for backward flows. Thus, if
 * two distinct calls are made to a method and each call-site has the same
 * data flow value then it is considered that the target of that call is
 * the same context. This concept allows termination in the presence of 
 * recursion as the number of contexts is limited by the size of the lattice
 * (which must be finite).
 * </p>
 * 
 * <p>
 * Each value context has its own work-list of CFG nodes to analyse, and the
 * results of analysis are stored in a map from nodes to the data flow values
 * before/after the node.
 * </p>
 * 
 * @author Rohan Padhye
 * 
 * @param <M> the type of a method
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 */
public class Context<M,N,A> implements soot.Context, Comparable<Context<M,N,A>> {

	/** A counter for global context identifiers. */
	private static int count = 0;

	/** Debug stuff */
	static java.util.Set<Object> freeContexts = new java.util.HashSet<Object>();
	static int totalNodes = 0;
	static int liveNodes = 0;

	/** Whether or not this context has been fully analysed at least once. */
	private boolean analysed;

	/** The control-flow graph of this method's body. */
	private DirectedGraph<N> controlFlowGraph;

	/** The data flow value associated with the entry to the method. **/
	private A entryValue;

	/** The data flow value associated with the exit of the method. */
	private A exitValue;

	/** A globally unique identifier. */
	private int id;

	
	/** The method for which this calling context context applies. */
	private M method;

	/** The data flow values at the exit of each node. */
	private Map<N,A> outValues;

	/** The data flow values at the entry of each node. */
	private Map<N,A> inValues;

	private Table<N, N, A> vals = HashBasedTable.create();

	/** The work-list of nodes that still need to be analysed. */
	private NavigableSet<N> workList;

	private LinkedList<Pair<N, N>> workListOfEdges;
	/**
	 * Creates a new context for phantom method
	 * 
	 * @param method
	 */
	public Context(M method) {
		count++;
		this.id = count;
		this.method = method;
		this.inValues = new HashMap<N, A>();
		this.outValues = new HashMap<N, A>();
		this.analysed = false;
		this.workList = new TreeSet<N>();
		this.workListOfEdges = new LinkedList<Pair<N, N>>();
	}

	/**
	 * Creates a new context for the given method.
	 * 
	 * @param method
	 *            the method to which this value context belongs
	 * @param cfg
	 *            the control-flow graph for the body of <tt>method</tt>
	 * @param reverse
	 *            <tt>true</tt> if the analysis is in the reverse direction, and
	 *            <tt>false</tt> if the analysis is in the forward direction
	 */
	public Context(M method, DirectedGraph<N> cfg, boolean reverse) {
		// Increment count and set id.
		count++;
		this.id = count;

		// Initialise fields.
		this.method = method;
		this.controlFlowGraph = cfg;
		this.inValues = new HashMap<N,A>();
		this.outValues = new HashMap<N,A>();
		this.analysed = false;
		
		totalNodes = totalNodes + controlFlowGraph.size();
		liveNodes = liveNodes + controlFlowGraph.size();

		// Now to initialise work-list. First, let's create a total order.
		@SuppressWarnings("unchecked")
		List<N> orderedNodes = new SlowPseudoTopologicalOrderer().newList(controlFlowGraph, reverse);
		// Then a mapping from a N to the position in the order.
		final Map<N,Integer> numbers = new HashMap<N,Integer>();
		int num = 1;
		for (N N : orderedNodes) {
			numbers.put(N, num);
			num++;
		}
		// Map the lowest priority to the null N, which is used to aggregate
		// ENTRY/EXIT flows.
		numbers.put(null, Integer.MAX_VALUE);
		// Now, create a sorted set with a comparator created on-the-fly using
		// the total order.
		this.workList = new TreeSet<N>(new Comparator<N>() {
			@Override
			public int compare(N u, N v) {
				return numbers.get(u) - numbers.get(v);
			}
		});
		this.workListOfEdges = new LinkedList<Pair<N, N>>();
	}

	/**
	 * Compares two contexts by their globally unique IDs.
	 * 
	 * This functionality is useful in the framework's internal methods
	 * where ordered processing of newer contexts first helps speed up
	 * certain operations.
	 */
	@Override
	public int compareTo(Context<M,N,A> other) {
		return this.getId() - other.getId();
	}

	/**
	 * Destroys all data flow information associated with the nodes
	 * of this context.
	 */
	public void freeMemory() {
		liveNodes = liveNodes - controlFlowGraph.size();
		inValues = null;
		outValues = null;
		controlFlowGraph = null;
		workList = null;
		freeContexts.add(this);
	}

	/** 
	 * Returns a reference to the control flow graph of this context's method. 
	 * 
	 * @return a reference to the control flow graph of this context's method
	 */
	public DirectedGraph<N> getControlFlowGraph() {
		return controlFlowGraph;
	}
	
	/** Returns the total number of contexts created so far. */
	public static int getCount() {
		return count;
	}

	/**
	 * Returns a reference to the data flow value at the method entry.
	 * 
	 * @return a reference to the data flow value at the method entry
	 */
	public A getEntryValue() {
		return entryValue;
	}

	/**
	 * Returns a reference to the data flow value at the method exit.
	 * 
	 * @return a reference to the data flow value at the method exit
	 */
	public A getExitValue() {
		return exitValue;
	}

	/**
	 * Returns the globally unique identifier of this context.
	 * 
	 * @return the globally unique identifier of this context
	 */
	public int getId() {
		return id;
	}

	/**
	 * Returns a reference to this context's method.
	 * 
	 * @return a reference to this context's method
	 */
	public M getMethod() {
		return method;
	}
	
	public A getEdgeValue(N node, N succ) {
		return this.vals.get(node, succ);
	}

	public void setEdgeValue(N node, N succ, A val) {
		if (this.vals == null) {
			this.vals=HashBasedTable.create();
		}
		this.vals.put(node, succ, val);
	}
	/**
	 * Gets the data flow value at the exit of the given node.
	 * 
	 * @param node a node in the control flow graph
	 * @return the data flow value at the exit of the given node
	 */
	public A getValueAfter(N node) {
		return outValues.get(node);
	}

	/**
	 * Gets the data flow value at the entry of the given node.
	 * 
	 * @param node a node in the control flow graph
	 * @return the data flow value at the entry of the given node
	 */
	public A getValueBefore(N node) {
		return inValues.get(node);
	}

	/**
	 * Returns a reference to this context's work-list.
	 * 
	 * @return a reference to this context's work-list
	 */
	public NavigableSet<N> getWorkList() {
		return workList;
	}

	public LinkedList<Pair<N, N>> getWorkListOfEdges() {
		return this.workListOfEdges;
	}

	/** 
	 * Returns whether or not this context has been analysed at least once. 
	 *
	 * @return <tt>true</tt> if the context has been analysed at least once,
	 * or <tt>false</tt> otherwise
	 */
	public boolean isAnalysed() {
		return analysed;
	}

	/**
	 * Returns whether the information about individual CFG nodes has
	 * been released.
	 * 
	 * @return <tt>true</tt> if the context data has been released
	 */
	boolean isFreed() {
		return inValues == null && outValues == null;
	}

	/** 
	 * Marks this context as analysed.
	 */
	public void markAnalysed() {
		this.analysed = true;
	}

	/** 
	 * Sets the entry flow of this context. 
	 * 
	 * @param entryValue the new data flow value at the method entry
	 */
	public void setEntryValue(A entryValue) {
		this.entryValue = entryValue;
	}

	/** 
	 * Sets the exit flow of this context. 
	 * 
	 * @param exitValue the new data flow value at the method exit
	 */
	public void setExitValue(A exitValue) {
		this.exitValue = exitValue;
	}
	/** 
	 * Sets the data flow value at the exit of the given node.
	 * 
	 * @param node a node in the control flow graph
	 * @param value the new data flow at the node exit
	 */
	public void setValueAfter(N node, A value) {
		outValues.put(node, value);
	}
	/** 
	 * Sets the data flow value at the entry of the given node.
	 * 
	 * @param node a node in the control flow graph
	 * @param value the new data flow at the node entry
	 */
	public void setValueBefore(N node, A value) {
		inValues.put(node, value);
	}
	
	/** {@inheritDoc} */
	@Override
	public String toString() {
		return Integer.toString(id);
	}

	public void unmarkAnalysed() {
		this.analysed = false;
	}
}
