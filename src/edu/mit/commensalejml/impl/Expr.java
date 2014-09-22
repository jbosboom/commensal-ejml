package edu.mit.commensalejml.impl;

import com.google.common.collect.ImmutableList;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public abstract class Expr {
	private final List<Expr> dependencies = new ArrayList<>();
	public Expr(Expr... deps) {
		deps().addAll(Arrays.asList(deps));
	}

	public final List<Expr> deps() {
		return dependencies;
	}

	/**
	 * @return the number of rows in this expression's result matrix, or -1 if unknown
	 */
	public abstract int rows();
	/**
	 * @return the number of columns in this expression's result matrix, or -1 if unknown
	 */
	public abstract int cols();

	/**
	 * Returns the dependencies for which this expression can be computed in
	 * place (reusing the dependency's storage).
	 *
	 * The returned list is not modifiable.
	 *
	 * By default, this is an empty list.
	 * @return the dependencies for which this expression can be computed in place
	 */
	public List<Expr> inplacePlaces() {
		return ImmutableList.of();
	}

	public abstract MethodHandle operate(List<MethodHandle> sources, MethodHandle sink);

//	@Override
//	public String toString() {
//		return getClass().getSimpleName()+Joiner.on(", ");
//	}
}
