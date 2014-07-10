package edu.mit.commensalejml.impl;

import edu.mit.streamjit.util.bytecode.Field;
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
	public String toString() {
		return String.format("Input(%s)", field.getName());
	}
}
