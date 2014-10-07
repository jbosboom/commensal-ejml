package edu.mit.commensalejml.impl;

import edu.mit.streamjit.util.bytecode.methodhandles.Combinators;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.findStatic;
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

	@Override
	public List<Expr> inplacePlaces() {
		return Collections.unmodifiableList(deps());
	}

	private static final MethodHandle INVERT_ = findStatic(CommonOps.class, "invert", params(2)),
			INVERT = INVERT_.asType(INVERT_.type().changeReturnType(void.class)),
			INVERT_INPLACE_ = findStatic(CommonOps.class, "invert", params(1)),
			INVERT_INPLACE = INVERT_INPLACE_.asType(INVERT_INPLACE_.type().changeReturnType(void.class));
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		return sources.get(0) == sink ?
				Combinators.apply(INVERT_INPLACE, sink) :
				Combinators.apply(INVERT, sources.get(0), sink);
	}
}
