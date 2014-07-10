package edu.mit.commensalejml.impl;

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

//	@Override
//	public String toString() {
//		return getClass().getSimpleName()+Joiner.on(", ");
//	}
}
