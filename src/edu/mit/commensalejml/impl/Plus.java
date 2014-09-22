package edu.mit.commensalejml.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.lang.invoke.MethodHandle;
import java.util.Collections;
import java.util.List;
import org.ejml.ops.CommonOps;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Plus extends Expr {
	public Plus(Expr left, Expr right) {
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
		//TODO: this will reassociate if we inplace into the right
		return Collections.unmodifiableList(deps());
	}

	private static final MethodHandle ADD = MethodHandleUtils.lookup(CommonOps.class, "add", 3);
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		return MethodHandleUtils.apply(ADD, Iterables.concat(sources, ImmutableList.of(sink)));
	}
}
