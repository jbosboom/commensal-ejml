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
import static edu.mit.streamjit.util.bytecode.methodhandles.LookupUtils.params;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.ejml.ops.CommonOps;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Multiply extends Expr {
	private boolean transposeLeft, transposeRight;
	public Multiply(Expr left, boolean transposeLeft, Expr right, boolean transposeRight) {
		super(left, right);
		this.transposeLeft = transposeLeft;
		this.transposeRight = transposeRight;
	}

	public static Multiply regular(Expr left, Expr right) {
		return new Multiply(left, false, right, false);
	}

	@Override
	public int rows() {
		return transposeLeft ? deps().get(0).cols() : deps().get(0).rows();
	}

	@Override
	public int cols() {
		return transposeRight ? deps().get(1).rows() : deps().get(1).cols();
	}

	public boolean isTransposeLeft() {
		return transposeLeft;
	}
	public void setTransposeLeft(boolean transposeLeft) {
		this.transposeLeft = transposeLeft;
	}
	public void toggleTransposeLeft() {
		setTransposeLeft(!isTransposeLeft());
	}
	public boolean isTransposeRight() {
		return transposeRight;
	}
	public void setTransposeRight(boolean transposeRight) {
		this.transposeRight = transposeRight;
	}
	public void toggleTransposeRight() {
		setTransposeRight(!isTransposeRight());
	}

	private static final MethodHandle MULT = findStatic(CommonOps.class, "mult", params(3)),
			MULT_TRANS_L = findStatic(CommonOps.class, "multTransA", params(3)),
			MULT_TRANS_R = findStatic(CommonOps.class, "multTransB", params(3)),
			MULT_TRANS_LR = findStatic(CommonOps.class, "multTransAB", params(3));
	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		MethodHandle handle = isTransposeLeft() ?
				(isTransposeRight() ? MULT_TRANS_LR : MULT_TRANS_L) :
				(isTransposeRight() ? MULT_TRANS_R : MULT);
		return Combinators.apply(handle, sources.get(0), sources.get(1), sink);
	}
}
