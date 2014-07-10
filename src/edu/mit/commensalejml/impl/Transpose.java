package edu.mit.commensalejml.impl;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Transpose extends Expr {
	public Transpose(Expr matrix) {
		super(matrix);
	}

	@Override
	public int rows() {
		return deps().get(0).cols();
	}

	@Override
	public int cols() {
		return deps().get(0).rows();
	}
}
