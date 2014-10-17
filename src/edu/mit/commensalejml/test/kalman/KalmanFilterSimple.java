/*
 * Copyright (c) 2009-2014, Peter Abeles. All Rights Reserved.
 *
 * This file is part of Efficient Java Matrix Library (EJML).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.mit.commensalejml.test.kalman;

import org.ejml.data.DenseMatrix64F;
import org.ejml.simple.SimpleMatrix;

/**
 *
 * @author Peter Abeles, Jeffrey Bosboom <jbosboom@csail.mit.edu>
 * @since 7/8/2014
 */
public class KalmanFilterSimple implements KalmanFilter {
	// kinematics description (constant)
	private final SimpleMatrix F, Q, H;
	// system state estimate (mutated by predict and update)
	private SimpleMatrix x, P;
	public KalmanFilterSimple(DenseMatrix64F F, DenseMatrix64F Q,
			DenseMatrix64F H, DenseMatrix64F x, DenseMatrix64F P) {
		this.F = new SimpleMatrix(F);
        this.Q = new SimpleMatrix(Q);
        this.H = new SimpleMatrix(H);
		this.x = new SimpleMatrix(x);
		this.P = new SimpleMatrix(P);
	}

	@Override
	public void predict() {
		// x = F x
		x = F.mult(x);

		// P = F P F' + Q
		P = F.mult(P).mult(F.transpose()).plus(Q);
	}

	@Override
	public void update(DenseMatrix64F _z, DenseMatrix64F _R) {
		// a fast way to make the matrices usable by SimpleMatrix
		SimpleMatrix z = SimpleMatrix.wrap(_z);
		SimpleMatrix R = SimpleMatrix.wrap(_R);

		// y = z - H x
		SimpleMatrix y = z.minus(H.mult(x));

		// S = H P H' + R
		SimpleMatrix S = H.mult(P).mult(H.transpose()).plus(R);

		// K = PH'S^(-1)
		SimpleMatrix K = P.mult(H.transpose().mult(S.invert()));

		// x = x + Ky
		x = x.plus(K.mult(y));

		// P = (I-kH)P = P - KHP
		P = P.minus(K.mult(H).mult(P));
	}

	@Override
    public void setState(DenseMatrix64F x, DenseMatrix64F P) {
		//don't mutate the args
        this.x = new SimpleMatrix(x);
        this.P = new SimpleMatrix(P);
    }

	@Override
	public DenseMatrix64F getState() {
		return x.getMatrix();
	}

	@Override
	public DenseMatrix64F getCovariance() {
		return P.getMatrix();
	}
}
