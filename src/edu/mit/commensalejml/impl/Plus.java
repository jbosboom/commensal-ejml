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

import edu.mit.streamjit.util.bytecode.methodhandles.Combinators;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.findStatic;
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.noPrimParam;
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
public final class Plus extends Expr {
	public Plus(Expr left, Expr right) {
		super(left, right);
	}

	@Override
	public int rows() {
		return Math.max(deps().get(0).rows(), deps().get(1).rows());
	}

	@Override
	public int cols() {
		return Math.max(deps().get(0).cols(), deps().get(1).cols());
	}

	@Override
	public List<Expr> inplacePlaces() {
		//TODO: this will reassociate if we inplace into the right
		return Collections.unmodifiableList(deps());
	}

	private static final MethodHandle ADD = findStatic(CommonOps.class, "add", params(3).and(noPrimParam())),
			ADD_EQUALS = findStatic(CommonOps.class, "addEquals", params(2));
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		if (sources.get(0) == sink)
			return Combinators.apply(ADD_EQUALS, sources.get(0), sources.get(1));
		else if (sources.get(1) == sink)
			return Combinators.apply(ADD_EQUALS, sources.get(1), sources.get(0));
		return Combinators.apply(ADD, sources.get(0), sources.get(1), sink);
	}
}
