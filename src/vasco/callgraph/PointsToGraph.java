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
package vasco.callgraph;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.ArrayType;
import soot.Local;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.Type;
import soot.jimple.AnyNewExpr;
import soot.jimple.ClassConstant;
import soot.jimple.Constant;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.NewMultiArrayExpr;
import soot.jimple.NullConstant;
import soot.jimple.StringConstant;
import soot.jimple.internal.JNewExpr;
import soot.jimple.internal.JimpleLocal;

/**
 * A data flow value representation for points-to analysis using allocation sites.
 * 
 * <p>The points-to graph contains two types of edges: (1) from root variables
 * ({@link soot.Local Local}) to objects represented by allocation
 * sites ({@link soot.jimple.AnyNewExpr AnyNewExpr}),
 * and (2) from objects to objects along fields ({@link soot.SootField SootField}).</p>
 * 
 * <p>Special artificial locals are used for string constants, class constants,
 * return values, etc. and artificial sites are used for summary nodes. For arrays
 * an artificial field is used to represent element access.</p>
 * 
 * @author Rohan Padhye
 */
public class PointsToGraph {
	
	public static final SootField ARRAY_FIELD = new SootField("[]", Scene.v().getObjectType());
	public static final Local GLOBAL_LOCAL = new JimpleLocal("@global", Scene.v().getObjectType());
	public static final Local RETURN_LOCAL = new JimpleLocal("@return", Scene.v().getObjectType());
	public static final Local STICKY_LOCAL = new JimpleLocal("@params", Scene.v().getObjectType());
	public static final Constant STRING_CONST = StringConstant.v("GLOBAL STRING CONSTANT");
	public static final Constant CLASS_CONST = ClassConstant.v("java/lang/Object");

	public static final NewExpr STRING_SITE = new JNewExpr(Scene.v().getRefType("java.lang.String"));
	public static final NewExpr CLASS_SITE = new JNewExpr(Scene.v().getRefType("java.lang.Class"));
	public static final NewExpr SUMMARY_NODE = new JNewExpr(null);
	public static final NewExpr GLOBAL_SITE = new JNewExpr(Scene.v().getObjectType());
	

	protected Map<Local,Set<AnyNewExpr>> roots;
	protected Map<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>> heap;
	
	
	/**
	 * Constructs a new empty points-to graph.
	 */
	public PointsToGraph() {
		roots = new HashMap<Local,Set<AnyNewExpr>>();
		heap = new HashMap<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>>();
	}

	/** 
	 * Constructs a copy of the given points-to graph.
	 * 
	 * @param other the points-to graph to copy
	 */
	public PointsToGraph(PointsToGraph other) {
		// As the data inside the top-level maps are immutable, just a shallow
		// copy will suffice
		this.roots = new HashMap<Local,Set<AnyNewExpr>>(other.roots);
		this.heap = new HashMap<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>>(other.heap);
	}

	/**
	 * Adds an edge between two sites with a given field.
	 */
	@SuppressWarnings("unused")
	private void addEdge(AnyNewExpr n1, SootField field, AnyNewExpr n2) {
		// No edge from null
		assert_tmp (n1 != null && n2 != null && field != null);

		// Ensure nodes exist in the heap
		ensureNode(n1);
		ensureNode(n2);

		// Add the field edge to a copy of the current edges.
		Map<SootField,Set<AnyNewExpr>> oldEdges = heap.get(n1);
		Map<SootField,Set<AnyNewExpr>> newEdges = new HashMap<SootField,Set<AnyNewExpr>>(oldEdges);
		Set<AnyNewExpr> oldTargets = oldEdges.containsKey(field) ? oldEdges.get(field) : new HashSet<AnyNewExpr>();
		Set<AnyNewExpr> newTargets = new HashSet<AnyNewExpr>(oldTargets);
		boolean change = newTargets.add(n2);
		if (change) {
			newEdges.put(field, Collections.unmodifiableSet(newTargets));
			heap.put(n1, Collections.unmodifiableMap(newEdges));
		}
	}

	/**
	 * Adds an edge between a variable and a node.
	 */
	@SuppressWarnings("unused")
	private void addEdge(Local var, AnyNewExpr node) {
		assert_tmp(var != null && node != null);
		
		// Ensure entry exists for field edges.
		ensureNode(node);
		
		// Add the root variable edge.
		Set<AnyNewExpr> oldPointees = roots.containsKey(var) ? roots.get(var) : new HashSet<AnyNewExpr>();
		Set<AnyNewExpr> newPointees = new HashSet<AnyNewExpr>(oldPointees);
		boolean change = newPointees.add(node);
		if (change) {
			roots.put(var, Collections.unmodifiableSet(newPointees)); 
		}
	}
	
	/**
	 * Returns <tt>true</tt> only if there is an edge
	 * from the given root variable to the given heap node.
	 */
	public boolean containsEdge(Local var, AnyNewExpr node) {
		return roots.get(var).contains(node);
	}

	/**
	 * Assigns a root variable to a root variable.
	 */
	public void assign(Local lhs, Local rhs) {		
		// Find whatever the RHS was pointing to.
		Set<AnyNewExpr> rhsTargets = roots.get(rhs);
		
		// We will fill this up with correctly typed targets
		Set<AnyNewExpr> lhsTargets = new HashSet<AnyNewExpr>();
		Type lhsType = lhs.getType();
		if (lhsType instanceof ArrayType) {
			lhsType = ((ArrayType) lhsType).baseType;
		}
		if (rhsTargets != null) {
			// Handle references and arrays separately
			if (lhsType instanceof RefType) {
				SootClass toClass = ((RefType) lhsType).getSootClass();
				for (AnyNewExpr rhsTarget : rhsTargets) {
					// Handle only instance objects
					if (rhsTarget instanceof AnyNewExpr) {
						// Do not type-check for summary nodes and when the LHS is java.lang.Object
						if (rhsTarget == SUMMARY_NODE || lhsType.equals(Scene.v().getObjectType())) {
							// Add by default
							lhsTargets.add(rhsTarget);
							continue;
						}
						Type rhsTargetType = rhsTarget.getType();
						if (rhsTargetType instanceof ArrayType) {
							rhsTargetType = ((ArrayType) rhsTargetType).baseType;
						}
						// If the type (or base type) is reftype, then type-check
						if (rhsTargetType instanceof RefType) {
							SootClass fromClass = ((RefType) rhsTargetType).getSootClass();
							if (PointsToGraph.canCast(fromClass, toClass)) {
								// Yes, add this target
								lhsTargets.add(rhsTarget);
							}
						} else {
							// For non-ref base types (e.g. char[]), just add
							lhsTargets.add(rhsTarget);
						}
					}
				}
			} else if (lhsType instanceof ArrayType) {
				// We are not so fickle about arrays
				lhsTargets.addAll(rhsTargets);
			}
		}

		// Add the targets to the LHS edges.
		roots.put(lhs, Collections.unmodifiableSet(lhsTargets)); 
		
		// Ensure reachability
		gc();
	}

	/**
	 * Assigns a constant to a root variable.
	 */
	public void assignConstant(Local lhs, Constant rhs) {
		// Get the allocation site of this constant
		NewExpr newExpr = constantNewExpr(rhs);
		// If it was a null constant, assign null, otherwise assign alloc site
		if (newExpr == null) {
			assign(lhs, null);
		} else {
			assignNew(lhs, newExpr);
		}
	}
	
	/**
	 * Assigns the sticky local to a parameter.
	 */
	public void assignSticky(Local sticky, Local parameter) {
		Set<AnyNewExpr> rhsTargets = roots.get(parameter);
		Set<AnyNewExpr> lhsTargets = new HashSet<AnyNewExpr>(roots.get(sticky));
		boolean change = lhsTargets.addAll(rhsTargets);
		if (change) {
			roots.put(sticky, Collections.unmodifiableSet(lhsTargets));
		}
	}

	/**
	 * Assigns a root variable to a new object at a given allocation site.
	 */
	public void assignNew(Local lhs, AnyNewExpr allocSite) {
		
		// If creating a new string or class, re-use the constant site
		if (allocSite != SUMMARY_NODE) {
			if (allocSite.getType().equals(STRING_CONST.getType())) {
				allocSite = STRING_SITE;
			} else if (allocSite.getType().equals(CLASS_CONST.getType())) {
				allocSite = CLASS_SITE;
			}
		}
		
		// We do not handle multi-dimensional arrays in this version
		if (allocSite instanceof NewMultiArrayExpr) {
			allocSite = SUMMARY_NODE;
		}

		// Create this node in the heap, if it doesn't already exist
		newNode(allocSite, false);
		
		// Assign LHS to the new node
		Set<AnyNewExpr> target = new HashSet<AnyNewExpr>();
		target.add(allocSite);
		roots.put(lhs, Collections.unmodifiableSet(target));
	
		// Ensure reachability.
		gc();
	}
	
	/**
	 * Assigns a set of targets to a root variable.
	 */
	void assignGlobals(Local lhs, Set<AnyNewExpr> targets) {
		// Ensure all nodes exist in the heap
		for (AnyNewExpr allocSite : targets) {
			newNode(allocSite, true);
		}
		
		// Assign LHS to all these nodes
		roots.put(lhs, Collections.unmodifiableSet(targets));
		
		// Ensure reachability.
		gc();
	}
	
	/**
	 * Assigns a root variable to the summary node.
	 */
	public void assignSummary(Local lhs) {
		assignNew(lhs, SUMMARY_NODE);
	}

	/**
	 * Determines whether an object of one class can be cast to another class.
	 * 
	 * @param fromClass the source type
	 * @param toClass the target type
	 * @return <tt>true</tt> if and only if <tt>fromClass</tt> is a sub-type of (or implements) <tt>toClass</tt>
	 */
	public static boolean canCast(SootClass fromClass, SootClass toClass) {
		// Handle classes and interfaces differently
		if (toClass.isInterface()) {
			// For interfaces, the fromClass (or one of its super-classes) must
			// implement fromClass
			if (fromClass.implementsInterface(toClass.toString())) {
			    return true;
			} 

			if (fromClass.getInterfaceCount() > 0) {
			    // Check sub interfaces
			    for (SootClass subInterface: fromClass.getInterfaces()) {
				if (canCast(subInterface, toClass)) {
				    return true;
				}
			    }
			}

			if (fromClass.hasSuperclass()) {
			    return canCast(fromClass.getSuperclass(), toClass);
			} else {
			    return false;
			}
		} else {
			// For classes, the fromClass (or one of its super-classes) and
			// toClass have to be same.
			if (fromClass.equals(toClass)) {
				return true;
			} else if (fromClass.hasSuperclass()) {
				return canCast(fromClass.getSuperclass(), toClass);
			} else {
				return false;
			}
		}
	}

	/**
	 * Creates a new node for a constant.
	 */
	private NewExpr constantNewExpr(Constant constant) {
		if (constant instanceof StringConstant) {
			return STRING_SITE;
		} else if (constant instanceof ClassConstant) {
			return CLASS_SITE;
		} else if (constant instanceof NullConstant) {
			return null;
		} else {
			throw new RuntimeException(constant.toString());
		}
	}
	/**
	 * Ensures the given node is in the heap.
	 */
	private void ensureNode(AnyNewExpr node) {
		// WARNING: No fields are added if this is used!
		if (node != null && !heap.containsKey(node))
			heap.put(node, Collections.unmodifiableMap(new HashMap<SootField,Set<AnyNewExpr>>()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof PointsToGraph))
			return false;
		PointsToGraph other = (PointsToGraph) obj;
		if (heap == null) {
			if (other.heap != null)
				return false;
		} else if (!heap.equals(other.heap))
			return false;
		if (roots == null) {
			if (other.roots != null)
				return false;
		} else if (!roots.equals(other.roots))
			return false;
		return true;
	}

	/**
	 * Removes all unreachable nodes from the edge sets.
	 */
	public void gc() {
		// Maintain a work-list of (reachable) nodes to process.
		LinkedList<AnyNewExpr> worklist = new LinkedList<AnyNewExpr>();

		// Add all the nodes pointed-to by root variables to the work-list.
		for (Set<AnyNewExpr> nodes : roots.values()) {
			worklist.addAll(nodes);
		}

		// Start with an empty field map (save the old one!)
		Map<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>> oldHeap = this.heap;
		Map<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>> newHeap = new HashMap<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>>();

		// Process work-list.
		while (!worklist.isEmpty()) {
			// Get the next element
			AnyNewExpr node = worklist.remove();
			// Ignore null pointees from the work-list
			if (node == null)
				throw new NullPointerException();
			// If this is already there in the new map, then ignore (duplicate
			// processing)
			if (newHeap.containsKey(node))
				continue;

			// Otherwise, just get the original stuff back
			// No need to do deep copy as (1) it is our own data and (2) target
			// nodes of edges will be reachable
			// (3) Also oldHeap.get(node) should return an unmodifiable map
			newHeap.put(node, oldHeap.get(node)); 

			// Add targets of this node to the work-list.
			for (Set<AnyNewExpr> targets : newHeap.get(node).values()) {
				worklist.addAll(targets);
			}

		}

		// Set this heap to the new minimal heap
		this.heap = newHeap;

	}

	/**
	 * Loads a field of an object into a root variable.
	 */
	public void getField(Local lhs, Local rhs, SootField field) {
		// Find whatever the RHS->F was pointing to.
		Set<AnyNewExpr> rhsPointees = roots.get(rhs);		
		Set<AnyNewExpr> rhsFieldPointees = new HashSet<AnyNewExpr>();
		for (AnyNewExpr src : rhsPointees) {
			if (src == null) {
				throw new NullPointerException();
			} else if (src == SUMMARY_NODE) { 
				rhsFieldPointees.add(SUMMARY_NODE);
			}else if (heap.get(src).containsKey(field)) {
				Set<AnyNewExpr> targets = heap.get(src).get(field);
				rhsFieldPointees.addAll(targets);
			}
		}

		// Add the indirect pointees to the LHS edges
		roots.put(lhs, Collections.unmodifiableSet(rhsFieldPointees)); 
		
		// Ensure reachability.
		gc();
	}
	
	/**
	 * Returns the points-to set of a root variable.
	 */
	public Set<AnyNewExpr> getTargets(Local local) {
		return roots.get(local);
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((heap == null) ? 0 : heap.hashCode());
		result = prime * result + ((roots == null) ? 0 : roots.hashCode());
		return result;
	}

	/**
	 * Removes all out-edges of the given variable
	 */
	public void kill(Local v) {
		// Kill the edges
		roots.remove(v);

		// Ensure reachability.
		gc();
	}
	
	/**
	 * Creates a new node (if it doesn't already exist) in the heap.
	 * 
	 */
	private void newNode(AnyNewExpr allocSite, boolean summarizeFields) {
		// Do not re-create a new node unless we are summarizing its fields
		if (heap.containsKey(allocSite) && !summarizeFields) {
			return;
		}
		
		
		// OK, we have to create it. Lets find it's fields.
		List<SootField> fields = new LinkedList<SootField>();
		
		// If we are going to summarise fields later, ensure summary node exists
		if (summarizeFields) {
			newNode(SUMMARY_NODE, false);
		}
		
		// First decide properly which type of new expression we have
		if (allocSite instanceof NewExpr) {
			// Enumerate fields only for non-summary nodes
			if (allocSite != SUMMARY_NODE) {
				// Find all reference-like fields from the soot class of the new expression
				SootClass sootClass = ((RefType)((NewExpr) allocSite).getType()).getSootClass(); 
				while(true) {
					for (SootField field : sootClass.getFields()) {
						if (field.isStatic() == false && field.getType() instanceof RefLikeType) {
							fields.add(field);
						}
					}
					// Get fields for all classes up to java.lang.Object
					if (sootClass.hasSuperclass()) {
						sootClass = sootClass.getSuperclass();
					} else {
						break;
					}
				}
			}
		} else if (allocSite instanceof NewArrayExpr) {
			// Has only one field: the element[]
			fields.add(ARRAY_FIELD);
		} else if (allocSite instanceof NewMultiArrayExpr) {
			// A multi-dimensional array creation
			// We do not handle multi-arrays right now
			allocSite = SUMMARY_NODE;
		}
		
		// Create edges
		Map<SootField,Set<AnyNewExpr>> edges = new HashMap<SootField,Set<AnyNewExpr>>();
		for (SootField field : fields) {
			HashSet<AnyNewExpr> targets = new HashSet<AnyNewExpr>();
			if (summarizeFields) {
				targets.add(SUMMARY_NODE);
			}
			edges.put(field, Collections.unmodifiableSet(targets));
		}
		heap.put(allocSite, Collections.unmodifiableMap(edges));
		
	}
	
	/**
	 * Stores values pointed-to by one root variable into a field of objects pointed-to by another root variable.
	 */
	public void setField(Local lhs, SootField field, Local rhs) {
		// You can't set field of a non-existent variable.
		assert_tmp (roots.containsKey(lhs));
	
		// Since we are doing weak updates, nothing to do if setting field to null
		if (rhs == null)
			return;
		
		// Find the objects whose field is being modified.
		Set<AnyNewExpr> lhsPointees = roots.get(lhs);
		// Find the objects to which the fields will now point to.
		Set<AnyNewExpr> rhsPointees = roots.get(rhs);
		
		// LHS variable should exist
		if (lhsPointees == null)
			throw new NullPointerException();
		
		// RHS variable should exist
		if (rhsPointees == null)
			throw new NullPointerException();
		
		// If the RHS variable exists, but points to nothing, then bye-bye
		if(rhsPointees.size() == 0)
			return;
		
		boolean summarizeRhsTargets = false;
		
		// For each object that the LHS points to, add to it's field the RHS pointee
		for (AnyNewExpr node : lhsPointees) {
			if (node == null)
				throw new NullPointerException();
			if (node == SUMMARY_NODE) {
				// Don't add any edge to SUMMARY, but note it down so
				// that we can summarize fields of RHS pointees
				summarizeRhsTargets = true;
				continue;
			}
			// Add the new edges (copy-and-modify as edges are immutable)
			Map<SootField,Set<AnyNewExpr>> oldEdges = heap.get(node);
			Map<SootField,Set<AnyNewExpr>> newEdges = new HashMap<SootField,Set<AnyNewExpr>>(oldEdges);
			Set<AnyNewExpr> oldTargets = oldEdges.get(field);
			if (oldTargets == null) {
				if (node == GLOBAL_SITE) {
					// If the node is global, then field must be static
					assert_tmp(field.isStatic());
					// In that case, we allow this condition
					oldTargets = new HashSet<AnyNewExpr>();
				} else {
					// Otherwise not acceptable as we are doing type-checking
					System.err.println(this);
					throw new RuntimeException("Field not found: " + field + " in " + node);
				}
			}
			Set<AnyNewExpr> newTargets = new HashSet<AnyNewExpr>(oldTargets);
			boolean change = newTargets.addAll(rhsPointees);
			if (change) {
				newEdges.put(field, Collections.unmodifiableSet(newTargets));
				heap.put(node, Collections.unmodifiableMap(newEdges));
			}
		}
		
		// If the LHS local pointed to SUMMARY, then we must summarize all
		// fields of the RHS pointees as we do not know if any such edge
		// could be created (since we are not maintaining edges out of SUMMARY)
		if (summarizeRhsTargets) {
			summarizeTargetFields(rhs);
		}
	}
	
	/**
	 * Stores a constant into a field of objects pointed-to by a root variable.
	 */
	public void setFieldConstant(Local lhs, SootField field, Constant rhs) {
		// Find out the alloc site of the constant
		NewExpr newExpr = constantNewExpr(rhs);
		// If null, do nothing, as we handle only weak updates,
		// otherwise, add the edge
		if (newExpr != null) {
			setFieldNew(lhs, field, newExpr);
		}
	}

	/**
	 * Stores a new object into a field of objects pointed-to by a root variable.
	 */
	public void setFieldNew(Local lhs, SootField field, AnyNewExpr allocSite) {		
		// You can't set field of a non-existent variable.
		assert_tmp (roots.containsKey(lhs));
		
		// Create this node in the heap, if it doesn't already exist
		newNode(allocSite, false);

		// Find the objects whose field is being modified.
		Set<AnyNewExpr> lhsPointees = roots.get(lhs);
		// Find the objects to which the fields will now point to.
		Set<AnyNewExpr> rhsPointees = new HashSet<AnyNewExpr>();
		rhsPointees.add(allocSite);
		
		// LHS variable should exist
		if (lhsPointees == null)
			throw new NullPointerException();
		

	
		// For each object that the LHS points to, add to it's field the RHS pointee
		for (AnyNewExpr node : lhsPointees) {
			if (node == null)
				throw new NullPointerException();
			if (node == SUMMARY_NODE) // Don't add any edge to SUMMARY
				continue; 
			// Add the new edges (copy-and-modify as edges are immutable)
			Map<SootField,Set<AnyNewExpr>> oldEdges = heap.get(node);
			Map<SootField,Set<AnyNewExpr>> newEdges = new HashMap<SootField,Set<AnyNewExpr>>(oldEdges);
			Set<AnyNewExpr> oldTargets = oldEdges.get(field);
			// Check to see if this node had edges with the given field
			if (oldTargets == null) {
				if (node == GLOBAL_SITE) {
					// If the node is global, then field must be static
					assert_tmp(field.isStatic());
					// In that case, we allow this condition
					oldTargets = new HashSet<AnyNewExpr>();
				} else {
					// Otherwise not acceptable as we are doing type-checking
					throw new RuntimeException("Field not found: " + field + " in " + node);
				}
			}
			Set<AnyNewExpr> newTargets = new HashSet<AnyNewExpr>(oldTargets);
			boolean change = newTargets.addAll(rhsPointees);
			if (change) {
				newEdges.put(field, Collections.unmodifiableSet(newTargets));
				heap.put(node, Collections.unmodifiableMap(newEdges));
			}
		}
		
	}
	
	/**
	 * Stores the summary node into a field of objects pointed-to by a root variable.
	 */
	public void setFieldSummary(Local lhs, SootField field) {
		setFieldNew(lhs, field, SUMMARY_NODE);
	}

	/**
	 * Removes nodes contained in the argument. This is used at 
	 * call-edges.
	 */
	public void subtractHeap(PointsToGraph other) {
		for (AnyNewExpr heapNode : other.heap.keySet()) {
			this.heap.remove(heapNode);
		}
	}
	
	public void summarizeTargetFields(Local lhs) {
		Set<AnyNewExpr> targets = roots.get(lhs);
		// Summarize nodes
		for (AnyNewExpr allocSite : targets) {
			newNode(allocSite, true);
		}
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();

		for (Local var : roots.keySet()) {
			sb.append(var).append(" -> ");
			for (AnyNewExpr node : roots.get(var)) {
				sb.append(node2String(node)).append(" ");
			}
			sb.append("\n");
		}

		for (AnyNewExpr source : heap.keySet()) {
			for (SootField field : heap.get(source).keySet()) {
				sb.append(node2String(source)).append(".").append(field.isStatic() ? field.toString() : field.getName()).append(" -> ");
				for (AnyNewExpr target : heap.get(source).get(field)) {
					sb.append(node2String(target)).append(" ");
				}
				sb.append("\n");
			}
		}
		
		return sb.toString();
	}
	
	private String node2String(AnyNewExpr node) {
		if (node == SUMMARY_NODE) {
			return "SUMMARY";
		} else if (node == STRING_SITE) {
			return "STRING";
		} else if (node == CLASS_SITE) {
			return "CLASS";
		} else {
			return "[" + node.toString() + " (" + node.hashCode() + ")]";
		}
	}
	
	/**
	 * Sets this graph to the union of the given arguments.
	 */
	public void union(PointsToGraph p, PointsToGraph q) {
		// Ensure that we are not the operands (otherwise the clear will cause
		// an issue)
		assert_tmp (this != p && this != q);

		// Clear the current data.
		this.roots = new HashMap<Local,Set<AnyNewExpr>>();
		this.heap = new HashMap<AnyNewExpr,Map<SootField,Set<AnyNewExpr>>>();

		// Union root variable edges.
		Set<Local> vars1 = p.roots.keySet();
		Set<Local> vars2 = q.roots.keySet();
		Set<Local> allVars = new HashSet<Local>();
		allVars.addAll(vars1);
		allVars.addAll(vars2);

		for (Local v : allVars) {
			// Collect all pointees
			Set<AnyNewExpr> pointees = new HashSet<AnyNewExpr>();
			if (vars1.contains(v)) {
				pointees.addAll(p.roots.get(v));
			}
			if (vars2.contains(v)) {
				pointees.addAll(q.roots.get(v));
			}

			// Add an immutable version of these pointees
			this.roots.put(v, Collections.unmodifiableSet(pointees));
		}

		// Time to union all heap nodes
		Set<AnyNewExpr> nodes1 = p.heap.keySet();
		Set<AnyNewExpr> nodes2 = q.heap.keySet();
		Set<AnyNewExpr> allNodes = new HashSet<AnyNewExpr>();
		allNodes.addAll(nodes1);
		allNodes.addAll(nodes2);

		for (AnyNewExpr node : allNodes) {
			Map<SootField,Set<AnyNewExpr>> edges = new HashMap<SootField,Set<AnyNewExpr>>();

			// First, find all possible fields
			Set<SootField> fields = new HashSet<SootField>();
			if (p.heap.containsKey(node))
				fields.addAll(p.heap.get(node).keySet());
			if (q.heap.containsKey(node))
				fields.addAll(q.heap.get(node).keySet());

			// Now, initialise each field with a mutable set of pointees
			for (SootField field : fields) {
				edges.put(field, new HashSet<AnyNewExpr>());
			}

			// Add field edges from first operand
			if (nodes1.contains(node)) {
				for (SootField field : fields) {
					edges.get(field).addAll(p.heap.get(node).get(field));
				}
			}

			// Add field edges from second operand
			if (nodes2.contains(node)) {
				for (SootField field : fields) {
					edges.get(field).addAll(q.heap.get(node).get(field));
				}
			}

			// Immutalize the field edges
			for (SootField field : fields) {
				edges.put(field, Collections.unmodifiableSet(edges.get(field)));
			}

			// Add an immutable version of these edges
			this.heap.put(node, Collections.unmodifiableMap(edges));
		}
	}
	
	private void assert_tmp(boolean b) { 
		if (b == false)
			throw new AssertionError();
	}

	public void killWithoutGC(Local local) {
		roots.remove(local);
	}
}
