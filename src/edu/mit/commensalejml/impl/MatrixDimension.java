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
