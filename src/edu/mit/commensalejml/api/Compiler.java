package edu.mit.commensalejml.api;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/6/2014
 */
public final class Compiler {
	private Compiler() {}

	public MethodHandle compile(Method method) {
		throw new UnsupportedOperationException("TODO");
	}
}
