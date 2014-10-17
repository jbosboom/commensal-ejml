/*
 * Copyright (c) 2013-2014 Massachusetts Institute of Technology
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package edu.mit.commensalejml.impl;

import static com.google.common.base.Preconditions.checkArgument;
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
		List<Expr> list = Arrays.asList(deps);
		checkArgument(!list.contains(null), list);
		deps().addAll(list);
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

	@Override
	public String toString() {
		return getClass().getSimpleName()+"@"+Integer.toHexString(hashCode());
	}
}
