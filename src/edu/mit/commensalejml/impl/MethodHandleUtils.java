package edu.mit.commensalejml.impl;

import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.findStatic;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/21/2014
 */
public final class MethodHandleUtils {
	private MethodHandleUtils() {}

	public static MethodHandle apply(MethodHandle handle, Iterable<MethodHandle> args) {
		for (MethodHandle a : args)
			handle = MethodHandles.collectArguments(handle, 0,
					a.asType(a.type().changeReturnType(handle.type().parameterType(0))));
		return handle;
	}

	public static MethodHandle apply(MethodHandle handle, MethodHandle... args) {
		for (MethodHandle a : args)
			handle = MethodHandles.collectArguments(handle, 0,
					a.asType(a.type().changeReturnType(handle.type().parameterType(0))));
		return handle;
	}

	private static void _semicolon(MethodHandle[] handles) throws Throwable {
		for (MethodHandle h : handles)
			h.invokeExact();
	}
	private static final MethodHandle SEMICOLON = findStatic(MethodHandles.lookup(), "_semicolon");
	public static MethodHandle semicolon(List<MethodHandle> handles) {
		return SEMICOLON.bindTo(handles.stream().toArray(MethodHandle[]::new));
	}
}
