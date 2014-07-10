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

//	@Override
//	public String toString() {
//		return getClass().getSimpleName()+Joiner.on(", ");
//	}
}
