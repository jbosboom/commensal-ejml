package edu.mit.commensalejml.impl;

import edu.mit.streamjit.util.bytecode.Field;

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
	public Input(Field field) {
		super();
		this.field = field;
	}

	public Field getField() {
		return field;
	}

	@Override
	public String toString() {
		return String.format("Input(%s)", field.getName());
	}
}
