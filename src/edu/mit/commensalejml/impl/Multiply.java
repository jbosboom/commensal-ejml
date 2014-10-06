package edu.mit.commensalejml.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import static edu.mit.commensalejml.impl.MethodHandleUtils.lookup;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.ejml.ops.CommonOps;

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

	@Override
	public int rows() {
		return transposeLeft ? deps().get(0).cols() : deps().get(0).rows();
	}

	@Override
	public int cols() {
		return transposeRight ? deps().get(1).rows() : deps().get(1).cols();
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

	private static final MethodHandle MULT = lookup(CommonOps.class, "mult", 3),
			MULT_TRANS_L = lookup(CommonOps.class, "multTransA", 3),
			MULT_TRANS_R = lookup(CommonOps.class, "multTransB", 3),
			MULT_TRANS_LR = lookup(CommonOps.class, "multTransAB", 3);
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		MethodHandle handle = isTransposeLeft() ?
				(isTransposeRight() ? MULT_TRANS_LR : MULT_TRANS_L) :
				(isTransposeRight() ? MULT_TRANS_R : MULT);
		return MethodHandleUtils.apply(handle, Iterables.concat(sources, ImmutableList.of(sink)));
	}
}
