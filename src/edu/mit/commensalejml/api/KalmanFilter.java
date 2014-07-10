package edu.mit.commensalejml.api;

import org.ejml.data.DenseMatrix64F;

/**
 *
 * @author Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/8/2014
 */
public interface KalmanFilter {

	DenseMatrix64F getCovariance();

	DenseMatrix64F getState();

	void predict();

	void setState(DenseMatrix64F x, DenseMatrix64F P);

	void update(DenseMatrix64F _z, DenseMatrix64F _R);

}
