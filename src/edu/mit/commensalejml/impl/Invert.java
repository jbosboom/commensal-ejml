package edu.mit.commensalejml.impl;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Invert extends Expr {
	public Invert(Expr matrix) {
		super(matrix);
	}

	@Override
	public int rows() {
		return deps().get(0).rows();
	}

	@Override
	public int cols() {
		return deps().get(0).cols();
	}
}
