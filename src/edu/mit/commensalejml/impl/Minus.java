package edu.mit.commensalejml.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.ejml.ops.CommonOps;

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

	@Override
	public List<Expr> inplacePlaces() {
		return ImmutableList.of(deps().get(0));
	}

	private static final MethodHandle SUB = MethodHandleUtils.lookup(CommonOps.class, "sub", 3);
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		return MethodHandleUtils.apply(SUB, Iterables.concat(sources, ImmutableList.of(sink)));
	}
}
