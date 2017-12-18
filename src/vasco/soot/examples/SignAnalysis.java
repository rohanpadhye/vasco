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

import static vasco.soot.examples.SignAnalysis.Sign.BOTTOM;
import static vasco.soot.examples.SignAnalysis.Sign.NEGATIVE;
import static vasco.soot.examples.SignAnalysis.Sign.POSITIVE;
import static vasco.soot.examples.SignAnalysis.Sign.ZERO;

import java.util.HashMap;
import java.util.Map;

import soot.IntType;
import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AddExpr;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.MulExpr;
import soot.jimple.NumericConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.UnopExpr;
import soot.jimple.internal.AbstractNegExpr;
import soot.jimple.internal.JimpleLocal;
import vasco.Context;
import vasco.ForwardInterProceduralAnalysis;
import vasco.ProgramRepresentation;
import vasco.soot.DefaultJimpleRepresentation;

/**
 * An inter-procedural simplified sign analysis.
 * 
 * <p>This analysis maps numeric variables to a sign (negative, positive or
 * zero), if it is statically determined to be singular, or else BOTTOM.
 * 
 * <p>Flow functions are non-distributive for statements involving sums or
 * products of two variables. </p>
 * 
 * <p>This is an example implementation only and hence only support analysis
 * of integer-valued local variables, and only handles addition, multiplication
 * and unary negation of such variables or values.</p>
 * 
 * @author Rohan Padhye
 *
 */
public class SignAnalysis extends ForwardInterProceduralAnalysis<SootMethod, Unit, Map<Local, SignAnalysis.Sign>> {
	
	
	// An artificial local representing returned value of a procedure (used because a method can have multiple return statements).
	private static final Local RETURN_LOCAL = new JimpleLocal("@return", IntType.v());
	
	// Simply constructs a forward flow inter-procedural analysis with the VERBOSE option set.
	public SignAnalysis() {
		super();
		verbose = true;
	}
	
	// Returns the sign of a constant or local if it is determinable, or else BOTTOM if the sign cannot be determined. */
	private Sign signOf(Value value, Map<Local, Sign> dfv) {
		if (value instanceof Local) {
			// if the value is a local variable, then look-up the data-flow map
			Local local = (Local) value;
			if (dfv.containsKey(local)) {
				return dfv.get(local);
			} else {
				return Sign.TOP;
			}
		} else if (value instanceof NumericConstant) {
			// If the value is a constant, we can get a definite sign
			NumericConstant num = (NumericConstant) value;
			NumericConstant zero = IntConstant.v(0);
			NumericConstant one = IntConstant.v(1);
			if (num.lessThan(zero).equals(one)) {
				return NEGATIVE;
			} else if (num.greaterThan(zero).equals(one)) {
				return POSITIVE;
			} else {
				return ZERO;
			}
		} else if (value instanceof BinopExpr) {
			BinopExpr expr = (BinopExpr) value;
			Value op1 = expr.getOp1();
			Value op2 = expr.getOp2();
			Sign sign1 = signOf(op1, dfv);
			Sign sign2 = signOf(op2, dfv);
			if (value instanceof AddExpr) {
				// Handle arithmetic plus
				return sign1.plus(sign2);
			} else if (value instanceof MulExpr) {
				// Handle arithmetic multiplication
				return sign1.mult(sign2);
			} else {
				// We do not handle other types of binary expressions
				return BOTTOM;
			}
		} else if (value instanceof UnopExpr) { 
			if (value instanceof AbstractNegExpr) {
				// Handle unary minus
				Value op = ((AbstractNegExpr) value).getOp();
				Sign sign = signOf(op, dfv);
				return sign.negate();
			} else {
				// We do not handle other types of binary expressions
				return BOTTOM;
			}
		} else {
			// We do not handle other types of compound expressions
			return BOTTOM;
		}
	}
	
	// Private utility method to assign the constant value of the RHS (if any) from the input map to  the LHS in the output map.
	private void assign(Local lhs, Value rhs, Map<Local, SignAnalysis.Sign> input, Map<Local, SignAnalysis.Sign> output) {
		// We only care about numeric locals
		if (lhs.getType() instanceof IntType) {			
			// First remove casts, if any.
			if (rhs instanceof CastExpr) {
				rhs = ((CastExpr) rhs).getOp();
			}	
			// Determine the sign of the RHS and add it to the map
			Sign sign = signOf(rhs, input);
			output.put(lhs, sign);
		}
	}

	@Override
	public Map<Local, SignAnalysis.Sign> normalFlowFunction(
			Context<SootMethod, Unit, Map<Local, SignAnalysis.Sign>> context, Unit unit,
			Map<Local, SignAnalysis.Sign> inValue) {
		// Initialize result to input
		Map<Local, SignAnalysis.Sign> outValue = copy(inValue);
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
	public Map<Local, SignAnalysis.Sign> callEntryFlowFunction(
			Context<SootMethod, Unit, Map<Local, SignAnalysis.Sign>> context, SootMethod calledMethod, Unit unit,
			Map<Local, SignAnalysis.Sign> inValue) {
		// Initialise result to empty map
		Map<Local, SignAnalysis.Sign> entryValue = topValue();
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
	public Map<Local, SignAnalysis.Sign> callExitFlowFunction(Context<SootMethod, Unit, Map<Local, SignAnalysis.Sign>> context, SootMethod calledMethod, Unit unit, Map<Local, SignAnalysis.Sign> exitValue) {
		// Initialise result to an empty value
		Map<Local, SignAnalysis.Sign> afterCallValue = topValue();
		// Only propagate signs for return values
		if (unit instanceof AssignStmt) {
			Value lhsOp = ((AssignStmt) unit).getLeftOp();
			assign((Local) lhsOp, RETURN_LOCAL, exitValue, afterCallValue);
		}
		// Return the map with the returned value's sign
		return afterCallValue;
	}

	@Override
	public Map<Local, SignAnalysis.Sign> callLocalFlowFunction(Context<SootMethod, Unit, Map<Local, SignAnalysis.Sign>> context, Unit unit, Map<Local, SignAnalysis.Sign> inValue) {
		// Initialise result to the input
		Map<Local, SignAnalysis.Sign> afterCallValue = copy(inValue);
		// Remove information for return value (as it's value will flow from the call)
		if (unit instanceof AssignStmt) {
			Value lhsOp = ((AssignStmt) unit).getLeftOp();
			afterCallValue.remove(lhsOp);
		}
		// Rest of the map remains the same
		return afterCallValue;
		
	}
	
	@Override
	public Map<Local, SignAnalysis.Sign> boundaryValue(SootMethod method) {
		return topValue();
	}

	@Override
	public Map<Local, SignAnalysis.Sign> copy(Map<Local, SignAnalysis.Sign> src) {
		return new HashMap<Local, SignAnalysis.Sign>(src);
	}



	@Override
	public Map<Local, SignAnalysis.Sign> meet(Map<Local, SignAnalysis.Sign> op1, Map<Local, SignAnalysis.Sign> op2) {
		Map<Local, SignAnalysis.Sign> result;
		// First add everything in the first operand
		result = new HashMap<Local, SignAnalysis.Sign>(op1);
		// Then add everything in the second operand, bottoming out the common keys with different values
		for (Local x : op2.keySet()) {
			if (op1.containsKey(x)) {
				// Check the values in both operands
				Sign sign1 = op1.get(x);
				Sign sign2 = op2.get(x);
				result.put(x, sign1.meet(sign2));
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
	public Map<Local, SignAnalysis.Sign> topValue() {
		return new HashMap<Local, SignAnalysis.Sign>();
	}

	/**
	 * Returns a default jimple representation.
	 * @see DefaultJimpleRepresentation
	 */
	@Override
	public ProgramRepresentation<SootMethod, Unit> programRepresentation() {
		return DefaultJimpleRepresentation.v();
	}
	
	/** A data-flow value representation of a sign. */
	public static interface Sign {

		/** Returns a sign which is the lattice MEET of this sign with the argument. */
		public Sign meet(Sign other);
		
		/** Returns a sign which is the result of adding a number with this sign with a number having the sign of the argument. */
		public Sign plus(Sign other);
		
		/** Returns a sign which is the result of multiplying a number with this sign with a number having the sign of the argument. */
		public Sign mult(Sign other);
		
		/** Returns a sign which is the result of negating a number with this sign. */
		public Sign negate();
		
		/** An unknown sign representing the BOTTOM value of the lattice. */
		public static final Sign BOTTOM = new Sign() {
			
			@Override
			public Sign meet(Sign other) {
				return BOTTOM;
			}
			
			@Override
			public Sign plus(Sign other) {
				return BOTTOM;
			}
			
			@Override
			public Sign mult(Sign other) {
				return BOTTOM;
			}

			@Override
			public Sign negate() {
				return BOTTOM;
			}
			
			@Override
			public String toString() {
				return "_|_";
			}
		};
		
		/** The sign of an undefined variable, representing the TOP value of the lattice. */
		public static final Sign TOP = new Sign() {
		
			@Override
			public Sign meet(Sign other) {
				return other;
			}
			
			@Override
			public Sign plus(Sign other) {
				return other;
			}
			
			@Override
			public Sign mult(Sign other) {
				return other;
			}
			
			@Override
			public Sign negate() {
				return TOP;
			}
			
			@Override
			public String toString() {
				return "T";
			}
		};
		
		/** The sign of the number 0. */
		public static final Sign ZERO = new Sign() {
			
			@Override
			public Sign meet(Sign other) {
				if (other == TOP || other == ZERO) {
					return ZERO;
				} else {
					return BOTTOM;
				}
			}
			
			@Override
			public Sign plus(Sign other) {
				if (other == TOP || other == ZERO) {
					return ZERO;
				} else if (other == POSITIVE) {
					return POSITIVE;
				} else if (other == NEGATIVE) {
					return NEGATIVE;
				} else {
					return BOTTOM;
				}
			}
			
			@Override
			public Sign mult(Sign other) {
				if (other == TOP || other == ZERO || other == POSITIVE || other == NEGATIVE) {
					return ZERO;
				} else {
					return BOTTOM;
				}
			}
			
			@Override
			public Sign negate() {
				return ZERO;
			}
			
			@Override
			public String toString() {
				return "0";
			}
		};
		
		/** The sign of positive numbers. */
		public static final Sign POSITIVE = new Sign() {
			
			@Override
			public Sign meet(Sign other) {
				if (other == TOP || other == POSITIVE) {
					return POSITIVE;
				} else {
					return BOTTOM;
				}
			}
			
			@Override
			public Sign plus(Sign other) {
				if (other == TOP || other == POSITIVE || other == ZERO) {
					return POSITIVE;
				} else if (other == NEGATIVE) {
					return BOTTOM;
				} else {
					return BOTTOM;
				}
			}
			
			@Override
			public Sign mult(Sign other) {
				if (other == TOP || other == POSITIVE) {
					return POSITIVE;
				} else if (other == ZERO) {
					return ZERO;
				} else if (other == NEGATIVE) {
					return NEGATIVE;
				} else {
					return BOTTOM;
				}
			}
			
			@Override
			public Sign negate() {
				return NEGATIVE;
			}
			
			@Override
			public String toString() {
				return "+";
			}
		};
		
		/** The sign of negative numbers. */
		public static final Sign NEGATIVE = new Sign() {
			@Override
			public Sign meet(Sign other) {
				if (other == TOP || other == NEGATIVE) {
					return NEGATIVE;
				} else {
					return BOTTOM;
				}
			}
			@Override
			public Sign plus(Sign other) {
				if (other == TOP || other == NEGATIVE || other == ZERO) {
					return NEGATIVE;
				} else if (other == POSITIVE) {
					return BOTTOM;
				} else {
					return BOTTOM;
				}
			}
			@Override
			public Sign mult(Sign other) {
				if (other == TOP || other == NEGATIVE) {
					return POSITIVE;
				} else if (other == ZERO) {
					return ZERO;
				} else if (other == POSITIVE) {
					return NEGATIVE;
				} else {
					return BOTTOM;
				}
			}
			@Override
			public Sign negate() {
				return POSITIVE;
			}
			@Override
			public String toString() {
				return "-";
			}
		};
	}

}




