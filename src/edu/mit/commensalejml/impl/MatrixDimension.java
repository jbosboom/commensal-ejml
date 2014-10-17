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

import org.ejml.data.Matrix64F;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 9/29/2014
 */
public final class MatrixDimension {
	private final int rows, cols;

	public MatrixDimension(int rows, int cols) {
		this.rows = rows;
		this.cols = cols;
	}

	public MatrixDimension(Matrix64F m) {
		this(m.getNumRows(), m.getNumCols());
	}

	public int rows() {
		return rows;
	}

	public int cols() {
		return cols;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final MatrixDimension other = (MatrixDimension)obj;
		if (this.rows != other.rows)
			return false;
		if (this.cols != other.cols)
			return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 71 * hash + this.rows;
		hash = 71 * hash + this.cols;
		return hash;
	}

	@Override
	public String toString() {
		return String.format("%dx%d", rows(), cols());
	}
}
