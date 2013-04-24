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

/**
 * A context-sensitive location of a method invocation.
 * 
 * <p>
 * A call-site is a uniquely identified by the calling context and the 
 * node containing the call statement.
 * </p>
 * 
 * @author Rohan Padhye
 * 
 * @param <M> the type of a method
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 */
public class CallSite<M,N,A> implements Comparable<CallSite<M,N,A>> {

	/** The context at the caller. */
	private final Context<M,N,A> callingContext;

	/** The node at which the call is made. */
	private final N callNode;

	/** Constructs a new call site with the given parameters. */
	public CallSite(Context<M,N,A> callingContext, N callStmt) {
		this.callingContext = callingContext;
		this.callNode = callStmt;
	}

	/**
	 * Call-sites are ordered by the ordering of their context's IDs.
	 * 
	 * This functionality is useful in the framework's internal methods
	 * where ordered processing of newer contexts first helps speed up
	 * certain operations.
	 */
	@Override
	public int compareTo(CallSite<M,N,A> other) {
		return this.callingContext.getId() - other.callingContext.getId();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CallSite other = (CallSite) obj;
		if (callNode == null) {
			if (other.callNode != null)
				return false;
		} else if (!callNode.equals(other.callNode))
			return false;
		if (callingContext == null) {
			if (other.callingContext != null)
				return false;
		} else if (!callingContext.equals(other.callingContext))
			return false;
		return true;
	}

	/**
	 * Returns the value context at this call-site.
	 * 
	 * @return the value context at this call-site
	 */
	public Context<M,N,A> getCallingContext() {
		return callingContext;
	}

	/**
	 * Returns the calling node.
	 * 
	 * @return the calling node
	 */
	public N getCallNode() {
		return callNode;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((callNode == null) ? 0 : callNode.hashCode());
		result = prime * result + ((callingContext == null) ? 0 : callingContext.hashCode());
		return result;
	}
	
	/**
	 * Returns a string representation of this call-site.
	 * @return a string representation of this call-site
	 */
	@Override
	public String toString() {
		return "X"+callingContext+": "+callNode;
	}
}
