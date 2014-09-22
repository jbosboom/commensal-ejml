package edu.mit.commensalejml.impl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
	private MethodHandleUtils() {}

	public static MethodHandle lookup(Class<?> c, String name, int matrixArguments) {
		List<Method> methods = Arrays.stream(c.getMethods())
				.filter(m -> m.getName().equals(name))
				.filter(m -> m.getParameterCount() == matrixArguments)
				//we don't know precisely which type, but all reference types
				.filter(m -> Arrays.stream(m.getParameterTypes()).allMatch(x -> !x.isPrimitive()))
				.collect(Collectors.toList());
		if (methods.size() != 1)
			throw new RuntimeException(c+" "+name+" "+matrixArguments+": "+methods);
		try {
			return MethodHandles.publicLookup().unreflect(methods.get(0));
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
}
