package edu.mit.commensalejml.impl;

import edu.mit.streamjit.util.bytecode.Field;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.ejml.data.DenseMatrix64F;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Input extends Expr {
	/**
	 * A state holder class field.
	 */
	private final Field field;
	/**
	 * The field's value, if known and final.
	 */
	private final DenseMatrix64F value;
	public Input(Field field, DenseMatrix64F value) {
		super();
		this.field = field;
		this.value = value;
	}

	public Field getField() {
		return field;
	}

	@Override
	public int rows() {
		return value != null ? value.getNumRows() : -1;
	}

	@Override
	public int cols() {
		return value != null ? value.getNumCols() : -1;
	}

	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		throw new AssertionError();
	}

	@Override
	public String toString() {
		return String.format("Input(%s)@%h", field.getName(), System.identityHashCode(this));
	}
}
