package edu.mit.commensalejml.impl;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Multiply extends Expr {
	private boolean transposeLeft, transposeRight;
	public Multiply(Expr left, boolean transposeLeft, Expr right, boolean transposeRight) {
		super(left, right);
		this.transposeLeft = transposeLeft;
		this.transposeRight = transposeRight;
	}

	public static Multiply regular(Expr left, Expr right) {
		return new Multiply(left, false, right, false);
	}

	public static Multiply transposeLeft(Expr left, Expr right) {
		return new Multiply(left, true, right, false);
	}

	public static Multiply transposeRight(Expr left, Expr right) {
		return new Multiply(left, false, right, true);
	}

	public static Multiply transposeBoth(Expr left, Expr right) {
		return new Multiply(left, true, right, true);
	}

	@Override
	public int rows() {
		return transposeLeft ? deps().get(0).rows() : deps().get(0).cols();
	}

	@Override
	public int cols() {
		return transposeRight ? deps().get(1).cols() : deps().get(1).rows();
	}

	public boolean isTransposeLeft() {
		return transposeLeft;
	}
	public void setTransposeLeft(boolean transposeLeft) {
		this.transposeLeft = transposeLeft;
	}
	public void toggleTransposeLeft() {
		setTransposeLeft(!isTransposeLeft());
	}
	public boolean isTransposeRight() {
		return transposeRight;
	}
	public void setTransposeRight(boolean transposeRight) {
		this.transposeRight = transposeRight;
	}
	public void toggleTransposeRight() {
		setTransposeRight(!isTransposeRight());
	}
}
