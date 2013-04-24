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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * A record of transitions between contexts at call-sites. 
 * 
 * <p>The context transition table records a bidirectional one-to-many mapping
 * of call-sites to called contexts parameterised by their methods.</p>
 * 
 * <p>If a call-site transition is not traversed in an analysis (e.g. a call
 * to a native method) then it is listed as a "default site" which this table
 * also records.
 * 
 * 
 * @author Rohan Padhye
 * 
 * @param <M> the type of a method
 * @param <N> the type of a node in the CFG
 * @param <A> the type of a data flow value
 */
public class ContextTransitionTable<M,N,A> {

	/** A map from contexts to a set of call-sites that transition to it. */
	protected Map<Context<M,N,A>,Set<CallSite<M,N,A>>> callers;

	/** A map from call-sites to contexts, parameterised by the called method. */
	protected Map<CallSite<M,N,A>,Map<M,Context<M,N,A>>> transitions;
	
	/** A map of contexts to call-sites present within their method bodies. */
	protected Map<Context<M,N,A>,Set<CallSite<M,N,A>>> callSitesOfContexts;
	
	/** A set of call-sites from which transitions are unknown. */
	protected Set<CallSite<M,N,A>> defaultCallSites;
	
	/** Constructs a new context transition table with no initial entries. */
	public ContextTransitionTable() {
		transitions = new HashMap<CallSite<M,N,A>,Map<M,Context<M,N,A>>>();
		callers = new HashMap<Context<M,N,A>,Set<CallSite<M,N,A>>>();
		callSitesOfContexts = new HashMap<Context<M,N,A>,Set<CallSite<M,N,A>>>();
		defaultCallSites = new HashSet<CallSite<M,N,A>>();
	}

	/**
	 * Adds a transition to the table.
	 * 
	 * <p>If the target context is specified as <tt>null</tt>, the source
	 * call-site is considered a "default site" from which transitions are
	 * unknown. This is used to model unpredictable targets, for example,
	 * when encountering calls to native methods.</p>
	 * 
	 * <p>Any previous transitions from the source call site to other
	 * contexts of the same called method are deleted.</p>
	 * 
	 * @param callSite the call-site which is the source of the transition
	 * @param targetContext the value context which is the target of the call-site
	 */
	public void addTransition(CallSite<M,N,A> callSite, Context<M,N,A> targetContext) {
		
		if (targetContext != null) {
			// Get the target method
			M targetMethod = targetContext.getMethod();
			
			// Ensure memory allocated in the reverse direction
			if (!callers.containsKey(targetContext)) {
				callers.put(targetContext, new HashSet<CallSite<M,N,A>>());
			}
	
			// Remove previous entry in the reverse direction
			if (transitions.containsKey(callSite) && transitions.get(callSite).containsKey(targetMethod)) {
				Context<M,N,A> oldTarget = transitions.get(callSite).get(targetMethod);
				callers.get(oldTarget).remove(callSite);
			}
	
			// Ensure memory allocated in the forward direction
			if (!transitions.containsKey(callSite)) {
				transitions.put(callSite, new HashMap<M,Context<M,N,A>>());
			}
			// Make entry in the forward direction
			transitions.get(callSite).put(targetMethod, targetContext);
	
			// Make entry in the reverse direction
			callers.get(targetContext).add(callSite);
		} else {
			// A null target means incomplete information (or "default")
			// Remove previous entries in the reverse direction
			if (transitions.containsKey(callSite) && transitions.get(callSite) != null) {
				for (M method : transitions.get(callSite).keySet()) {
					Context<M,N,A> oldTarget = transitions.get(callSite).get(method);
					callers.get(oldTarget).remove(callSite);
				}
			}
			// Add to default call sites
			defaultCallSites.add(callSite);
		}
		
		// Ensure memory allocated for call-site index
		Context<M,N,A> source = callSite.getCallingContext();
		if (callSitesOfContexts.containsKey(source) == false) {
			callSitesOfContexts.put(source, new HashSet<CallSite<M,N,A>>());
		}
		
		// Add call-site to source context
		callSitesOfContexts.get(source).add(callSite);
	}
	
	/**
	 * Returns an unmodifiable view of the mapping from contexts to their callers.
	 * @return an unmodifiable view of the mapping from contexts to their callers
	 */
	public Map<Context<M,N,A>,Set<CallSite<M,N,A>>> getCallers() {
		return Collections.unmodifiableMap(callers);
	}

	/**
	 * Returns the callers of a value context.
	 * 
	 * @param target the target value context
	 * @return a set of call-sites which transition to the given target context
	 */
	public Set<CallSite<M,N,A>> getCallers(Context<M,N,A> target) {
		return callers.get(target);
	}
	
	/**
	 * Returns an unmodifiable view of a mapping from calling contexts to all their call-sites.
	 * @return an unmodifiable view of a mapping from calling contexts to all their call-sites
	 */
	public Map<Context<M,N,A>,Set<CallSite<M,N,A>>> getCallSitesOfContexts() {
		return Collections.unmodifiableMap(callSitesOfContexts);
	}

	/**
	 * Returns an unmodifiable view of the set of call-sites marked "default".
	 * @return an unmodifiable view of the set of call-sites marked "default"
	 */
	public Set<CallSite<M,N,A>> getDefaultCallSites() {
		return defaultCallSites;
	}
	
	/**
	 * Returns the targets of a call-site.
	 * 
	 * @param callSite the source of the transition
	 * @return a map of target methods to target contexts
	 */
	public Map<M,Context<M,N,A>> getTargets(CallSite<M,N,A> callSite) {
		return transitions.get(callSite);
	}

	/**
	 * Returns an unmodifiable view of context transitions.
	 * 
	 * @return an unmodifiable view of context transitions
	 */
	public Map<CallSite<M,N,A>,Map<M,Context<M,N,A>>> getTransitions() {
		return Collections.unmodifiableMap(this.transitions);
	}

	/**
	 * Computes a reachable set of value contexts from a particular
	 * source by traversing the context transition table.
	 * 
	 * Note that the source context itself is only reachable from 
	 * itself if it there is a recursive call to it (i.e. a context
	 * is not reachable to itself by default).
	 * 
	 * @param source the source context
	 * @return a set of contexts reachable from <tt>source</tt>
	 */
	public Set<Context<M,N,A>> reachableSet(Context<M,N,A> source, boolean ignoreFree) {
		// The result set
		Set<Context<M,N,A>> reachableContexts = new HashSet<Context<M,N,A>>();
		
		// Maintain a stack of contexts to process
		Stack<Context<M,N,A>> stack = new Stack<Context<M,N,A>>();
		// Initialise it with the source
		stack.push(source);

		// Now recursively (using stacks) mark reachable contexts
		while (stack.isEmpty() == false) {
			// Get the next item to process
			source = stack.pop();
			// Add successors
			if (callSitesOfContexts.containsKey(source)) {
				// The above check is there because methods with no calls have no entry
				for (CallSite<M,N,A> callSite : callSitesOfContexts.get(source)) {
					// Don't worry about DEFAULT edges
					if (defaultCallSites.contains(callSite)) {
						continue;
					}
					for (M method : transitions.get(callSite).keySet()) {
						Context<M,N,A> target = transitions.get(callSite).get(method);
						// Don't process the same element twice
						if (reachableContexts.contains(target) == false) {
							// Are we ignoring free contexts?
							if (ignoreFree && target.isFreed()) {
								continue;
							}
							// Mark reachable
							reachableContexts.add(target);
							// Add it's successors also later
							stack.push(target);
						}
					}
				}
			}
			
		}		
		return reachableContexts;
	}
	
	

}
