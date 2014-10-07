package edu.mit.commensalejml.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.findStatic;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.params;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.ejml.ops.CommonOps;

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

	private static final MethodHandle TRANSPOSE = findStatic(CommonOps.class, "transpose", params(2));
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		return MethodHandleUtils.apply(TRANSPOSE, Iterables.concat(sources, ImmutableList.of(sink)));
	}
}
