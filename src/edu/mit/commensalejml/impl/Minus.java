package edu.mit.commensalejml.impl;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Minus extends Expr {
	public Minus(Expr left, Expr right) {
		super(left, right);
	}

	@Override
	public int rows() {
		return Math.max(deps().get(0).rows(), deps().get(1).rows());
	}

	@Override
	public int cols() {
		return Math.max(deps().get(0).cols(), deps().get(1).cols());
	}
}
