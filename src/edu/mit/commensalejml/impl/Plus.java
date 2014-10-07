package edu.mit.commensalejml.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.findStatic;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.noPrimParam;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.params;
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

	private static final MethodHandle ADD = findStatic(CommonOps.class, "add", params(3).and(noPrimParam())),
			ADD_EQUALS = findStatic(CommonOps.class, "addEquals", params(2));
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		if (sources.get(0) == sink)
			return MethodHandleUtils.apply(ADD_EQUALS, sources.get(0), sources.get(1));
		else if (sources.get(1) == sink)
			return MethodHandleUtils.apply(ADD_EQUALS, sources.get(1), sources.get(0));
		return MethodHandleUtils.apply(ADD, Iterables.concat(sources, ImmutableList.of(sink)));
	}
}
