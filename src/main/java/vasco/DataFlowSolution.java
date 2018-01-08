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

import java.util.Map;

/**
 * A mapping of program points to results of data flow analysis.
 * 
 * <p>This simple mapping does not parametrise solutions with a
 * context object, and is thus context-insensitive. The results of a 
 * context-sensitive inter-procedural analysis can be reduced to this
 * form by merging results for the same program point across all 
 * contexts, giving what is known as the Meet-Over-Valid-Paths solution.</p>
 * 
 * @author Rohan Padhye
 *
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 *
 */
public class DataFlowSolution<N,A> {
	
	/** A map of nodes to data flow values at the entry of the node. */
	private Map<N,A> inValues;
	
	/** A map of nodes to data flow values at the exit of the node. */
	private Map<N,A> outValues;
	
	/** 
	 * Constructs a data flow solution with the given IN and OUT values.
	 * 
	 * @param inValues a map of nodes to data flow values at their entry
	 * @param outValues a map of nodes to data flow values at their exit
	 */
	public DataFlowSolution(Map<N,A> inValues, Map<N,A> outValues) {
		this.inValues = inValues;
		this.outValues = outValues;
	}
	
	/**
	 * Returns the data flow value at the entry of a node.
	 * 
	 * @param node a program point
	 * @return the data flow value at the entry of <tt>node</tt>
	 * 
	 */
	public A getValueBefore(N node) {
		return inValues.get(node);
	}
	
	/**
	 * Returns the data flow value at the exit of a node.
	 * 
	 * @param node a program point
	 * @return the data flow value at the exit of <tt>node</tt>
	 * 
	 */
	public A getValueAfter(N node) {
		return outValues.get(node);
	}
}
