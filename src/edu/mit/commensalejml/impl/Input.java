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

import edu.mit.streamjit.util.bytecode.Field;
import java.lang.invoke.MethodHandle;
import java.util.List;
import org.ejml.data.DenseMatrix64F;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/9/2014
 */
public final class Input extends Expr {
	/**
	 * A state holder class field.
	 */
	private final Field field;
	/**
	 * The field's value, if known and final.
	 */
	private final DenseMatrix64F value;
	public Input(Field field, DenseMatrix64F value) {
		super();
		this.field = field;
		this.value = value;
	}

	public Field getField() {
		return field;
	}

	@Override
	public int rows() {
		return value != null ? value.getNumRows() : -1;
	}

	@Override
	public int cols() {
		return value != null ? value.getNumCols() : -1;
	}

	@Override
	public MethodHandle operate(List<MethodHandle> sources, MethodHandle sink) {
		throw new AssertionError();
	}

	@Override
	public String toString() {
		return String.format("Input(%s)@%h", field.getName(), System.identityHashCode(this));
	}
}
