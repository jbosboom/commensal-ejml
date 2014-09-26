package edu.mit.commensalejml.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/21/2014
 */
public final class MethodHandleUtils {
	private static final Lookup LOOKUP = MethodHandles.lookup();
	private MethodHandleUtils() {}

	public static MethodHandle lookup(Class<?> c, String name, int referenceArguments) {
		List<Method> methods = Arrays.stream(c.getDeclaredMethods())
				.filter(m -> m.getName().equals(name))
				.filter(m -> m.getParameterCount() == referenceArguments)
				//we don't know precisely which type, but all reference types
				.filter(m -> Arrays.stream(m.getParameterTypes()).allMatch(x -> !x.isPrimitive()))
				.collect(Collectors.toList());
		if (methods.size() != 1)
			throw new RuntimeException(c+" "+name+" "+referenceArguments+": "+methods);
		try {
			return LOOKUP.unreflect(methods.get(0));
		} catch (IllegalAccessException ex) {
			throw new RuntimeException(ex);
		}
	}

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
	private static final MethodHandle SEMICOLON = lookup(MethodHandleUtils.class, "_semicolon", 1);
	public static MethodHandle semicolon(List<MethodHandle> handles) {
		return SEMICOLON.bindTo(handles.stream().toArray(MethodHandle[]::new));
	}
}
