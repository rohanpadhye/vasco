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
package vasco.soot.examples;

import java.util.HashMap;
import java.util.Map;

import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.Constant;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.JimpleLocal;
import vasco.Context;
import vasco.ForwardInterProceduralAnalysis;
import vasco.ProgramRepresentation;
import vasco.soot.DefaultJimpleRepresentation;

/**
 * An inter-procedural copy constant propagation analysis.
 * 
 * <p>This analysis uses a mapping of {@link Local}s to {@link Constant}s as
 * data flow values. The flow functions consider assignments of constants
 * to locals (immediate operands) as well as assignments of locals to locals
 * where the operand has a constant value. This type of analysis is commonly referred
 * to as copy constant propagation.</p>
 * 
 * 
 * @author Rohan Padhye
 *
 */
public class CopyConstantAnalysis extends ForwardInterProceduralAnalysis<SootMethod, Unit, Map<Local, Constant>> {
	
	// An artificial local representing returned value of a procedure (used because a method can have multiple return statements).
	private static final Local RETURN_LOCAL = new JimpleLocal("@return", null);
	
	// Simply constructs a forward flow inter-procedural analysis with the VERBOSE option set.
	public CopyConstantAnalysis() {
		super();
		verbose = true;
	}
	
	// Private utility method to assign the constant value of the RHS (if any) from the input map to  the LHS in the output map.
	private void assign(Local lhs, Value rhs, Map<Local, Constant> input, Map<Local, Constant> output) {
		// First remove casts, if any.
		if (rhs instanceof CastExpr) {
			rhs = ((CastExpr) rhs).getOp();
		}
		// Then check if the RHS operand is a constant or local
		if (rhs instanceof Constant) {
			// If RHS is a constant, it is a direct gen
			output.put(lhs, (Constant) rhs);
		} else if (rhs instanceof Local) {
			// Copy constant-status of RHS to LHS (indirect gen), if exists
			if(input.containsKey(rhs)) {
				output.put(lhs, input.get(rhs));
			}
		} else {
			// RHS is some compound expression, then LHS is non-constant (only kill)
			output.put(lhs, null);
		}			
	}

	@Override
	public Map<Local, Constant> normalFlowFunction(Context<SootMethod, Unit, Map<Local, Constant>> context, Unit unit,
			Map<Local, Constant> inValue) {
		// Initialize result to input
		Map<Local, Constant> outValue = copy(inValue);
		// Only statements assigning locals matter
		if (unit instanceof AssignStmt) {
			// Get operands
			Value lhsOp = ((AssignStmt) unit).getLeftOp();
			Value rhsOp = ((AssignStmt) unit).getRightOp();
			if (lhsOp instanceof Local) {
				assign((Local) lhsOp, rhsOp, inValue, outValue);		
			}
		} else if (unit instanceof ReturnStmt) {
			// Get operand
			Value rhsOp = ((ReturnStmt) unit).getOp();
			assign(RETURN_LOCAL, rhsOp, inValue, outValue);
		}
		// Return the data flow value at the OUT of the statement
		return outValue;
	}

	@Override
	public Map<Local, Constant> callEntryFlowFunction(Context<SootMethod, Unit, Map<Local, Constant>> context, SootMethod calledMethod, Unit unit, Map<Local, Constant> inValue) {
		// Initialise result to empty map
		Map<Local, Constant> entryValue = topValue();
		// Map arguments to parameters
		InvokeExpr ie = ((Stmt) unit).getInvokeExpr();
		for (int i = 0; i < ie.getArgCount(); i++) {
			Value arg = ie.getArg(i);
			Local param = calledMethod.getActiveBody().getParameterLocal(i);
			assign(param, arg, inValue, entryValue);
		}
		// And instance of the this local
		if (ie instanceof InstanceInvokeExpr) {
			Value instance = ((InstanceInvokeExpr) ie).getBase();
			Local thisLocal = calledMethod.getActiveBody().getThisLocal();
			assign(thisLocal, instance, inValue, entryValue);
		}
		// Return the entry value at the called method
		return entryValue;
	}
	
	@Override
	public Map<Local, Constant> callExitFlowFunction(Context<SootMethod, Unit, Map<Local, Constant>> context, SootMethod calledMethod, Unit unit, Map<Local, Constant> exitValue) {
		// Initialise result to an empty value
		Map<Local, Constant> afterCallValue = topValue();
		// Only propagate constants for return values
		if (unit instanceof AssignStmt) {
			Value lhsOp = ((AssignStmt) unit).getLeftOp();
			assign((Local) lhsOp, RETURN_LOCAL, exitValue, afterCallValue);
		}
		// Return the map with the returned value's constant
		return afterCallValue;
	}

	@Override
	public Map<Local, Constant> callLocalFlowFunction(Context<SootMethod, Unit, Map<Local, Constant>> context, Unit unit, Map<Local, Constant> inValue) {
		// Initialise result to the input
		Map<Local, Constant> afterCallValue = copy(inValue);
		// Remove information for return value (as it's value will flow from the call)
		if (unit instanceof AssignStmt) {
			Value lhsOp = ((AssignStmt) unit).getLeftOp();
			afterCallValue.remove(lhsOp);
		}
		// Rest of the map remains the same
		return afterCallValue;
		
	}
	
	@Override
	public Map<Local, Constant> boundaryValue(SootMethod method) {
		return topValue();
	}

	@Override
	public Map<Local, Constant> copy(Map<Local, Constant> src) {
		return new HashMap<Local, Constant>(src);
	}



	@Override
	public Map<Local, Constant> meet(Map<Local, Constant> op1, Map<Local, Constant> op2) {
		Map<Local, Constant> result;
		// First add everything in the first operand
		result = new HashMap<Local, Constant>(op1);
		// Then add everything in the second operand, bottoming out the common keys with different values
		for (Local x : op2.keySet()) {
			if (op1.containsKey(x)) {
				// Check the values in both operands
				Constant c1 = op1.get(x);
				Constant c2 = op2.get(x);
				if (c1 != null && c1.equals(c2) == false) {
					// Set to non-constant
					result.put(x, null);
				}
			} else {
				// Only in second operand, so add as-is
				result.put(x, op2.get(x));
			}
		}
		return result;
	}

	/**
	 * Returns an empty map.
	 */
	@Override
	public Map<Local, Constant> topValue() {
		return new HashMap<Local, Constant>();
	}

	/**
	 * Returns a default jimple representation.
	 * @see DefaultJimpleRepresentation
	 */
	@Override
	public ProgramRepresentation<SootMethod, Unit> programRepresentation() {
		return DefaultJimpleRepresentation.v();
	}

}
