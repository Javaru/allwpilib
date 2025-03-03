// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package edu.wpi.first.math.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.wpi.first.math.MatBuilder;
import edu.wpi.first.math.Matrix;
import edu.wpi.first.math.Nat;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.numbers.N2;
import org.junit.jupiter.api.Test;

class DiscretizationTest {
  // Check that for a simple second-order system that we can easily analyze
  // analytically,
  @Test
  void testDiscretizeA() {
    final var contA = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0, 1, 0, 0);
    final var x0 = VecBuilder.fill(1, 1);

    final var discA = Discretization.discretizeA(contA, 1.0);
    final var x1Discrete = discA.times(x0);

    // We now have pos = vel = 1 and accel = 0, which should give us:
    final var x1Truth =
        VecBuilder.fill(
            1.0 * x0.get(0, 0) + 1.0 * x0.get(1, 0), 0.0 * x0.get(0, 0) + 1.0 * x0.get(1, 0));

    assertEquals(x1Truth, x1Discrete);
  }

  // Check that for a simple second-order system that we can easily analyze
  // analytically,
  @Test
  void testDiscretizeAB() {
    final var contA = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0, 1, 0, 0);
    final var contB = new MatBuilder<>(Nat.N2(), Nat.N1()).fill(0, 1);

    final var x0 = VecBuilder.fill(1, 1);
    final var u = VecBuilder.fill(1);

    var discABPair = Discretization.discretizeAB(contA, contB, 1.0);
    var discA = discABPair.getFirst();
    var discB = discABPair.getSecond();

    var x1Discrete = discA.times(x0).plus(discB.times(u));

    // We now have pos = vel = accel = 1, which should give us:
    final var x1Truth =
        VecBuilder.fill(
            1.0 * x0.get(0, 0) + 1.0 * x0.get(1, 0) + 0.5 * u.get(0, 0),
            0.0 * x0.get(0, 0) + 1.0 * x0.get(1, 0) + 1.0 * u.get(0, 0));

    assertEquals(x1Truth, x1Discrete);
  }

  //                                               T
  // Test that the discrete approximation of Q_d ≈ ∫ e^(Aτ) Q e^(Aᵀτ) dτ
  //                                               0
  @Test
  void testDiscretizeSlowModelAQ() {
    final var contA = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0, 1, 0, 0);
    final var contQ = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(1, 0, 0, 1);

    final double dt = 1.0;

    //       T
    // Q_d = ∫ e^(Aτ) Q e^(Aᵀτ) dτ
    //       0
    final var discQIntegrated =
        RungeKuttaTimeVarying.rungeKuttaTimeVarying(
            (Double t, Matrix<N2, N2> x) ->
                contA.times(t).exp().times(contQ).times(contA.transpose().times(t).exp()),
            0.0,
            new Matrix<>(Nat.N2(), Nat.N2()),
            dt);

    var discAQPair = Discretization.discretizeAQ(contA, contQ, dt);
    var discQ = discAQPair.getSecond();

    assertTrue(
        discQIntegrated.minus(discQ).normF() < 1e-10,
        "Expected these to be nearly equal:\ndiscQ:\n"
            + discQ
            + "\ndiscQIntegrated:\n"
            + discQIntegrated);
  }

  //                                               T
  // Test that the discrete approximation of Q_d ≈ ∫ e^(Aτ) Q e^(Aᵀτ) dτ
  //                                               0
  @Test
  void testDiscretizeFastModelAQ() {
    final var contA = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0, 1, 0, -1406.29);
    final var contQ = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0.0025, 0, 0, 1);

    final var dt = 0.005;

    //       T
    // Q_d = ∫ e^(Aτ) Q e^(Aᵀτ) dτ
    //       0
    final var discQIntegrated =
        RungeKuttaTimeVarying.rungeKuttaTimeVarying(
            (Double t, Matrix<N2, N2> x) ->
                contA.times(t).exp().times(contQ).times(contA.transpose().times(t).exp()),
            0.0,
            new Matrix<>(Nat.N2(), Nat.N2()),
            dt);

    var discAQPair = Discretization.discretizeAQ(contA, contQ, dt);
    var discQ = discAQPair.getSecond();

    assertTrue(
        discQIntegrated.minus(discQ).normF() < 1e-3,
        "Expected these to be nearly equal:\ndiscQ:\n"
            + discQ
            + "\ndiscQIntegrated:\n"
            + discQIntegrated);
  }

  // Test that the Taylor series discretization produces nearly identical results.
  @Test
  void testDiscretizeSlowModelAQTaylor() {
    final var contA = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0, 1, 0, 0);
    final var contQ = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(1, 0, 0, 1);

    final var dt = 1.0;

    // Continuous Q should be positive semidefinite
    final var esCont = contQ.getStorage().eig();
    for (int i = 0; i < contQ.getNumRows(); ++i) {
      assertTrue(esCont.getEigenvalue(i).real >= 0);
    }

    //       T
    // Q_d = ∫ e^(Aτ) Q e^(Aᵀτ) dτ
    //       0
    final var discQIntegrated =
        RungeKuttaTimeVarying.rungeKuttaTimeVarying(
            (Double t, Matrix<N2, N2> x) ->
                contA.times(t).exp().times(contQ).times(contA.transpose().times(t).exp()),
            0.0,
            new Matrix<>(Nat.N2(), Nat.N2()),
            dt);

    var discA = Discretization.discretizeA(contA, dt);

    var discAQPair = Discretization.discretizeAQ(contA, contQ, dt);
    var discATaylor = discAQPair.getFirst();
    var discQTaylor = discAQPair.getSecond();

    assertTrue(
        discQIntegrated.minus(discQTaylor).normF() < 1e-10,
        "Expected these to be nearly equal:\ndiscQTaylor:\n"
            + discQTaylor
            + "\ndiscQIntegrated:\n"
            + discQIntegrated);
    assertTrue(discA.minus(discATaylor).normF() < 1e-10);

    // Discrete Q should be positive semidefinite
    final var esDisc = discQTaylor.getStorage().eig();
    for (int i = 0; i < discQTaylor.getNumRows(); ++i) {
      assertTrue(esDisc.getEigenvalue(i).real >= 0);
    }
  }

  // Test that the Taylor series discretization produces nearly identical results.
  @Test
  void testDiscretizeFastModelAQTaylor() {
    final var contA = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0, 1, 0, -1500);
    final var contQ = new MatBuilder<>(Nat.N2(), Nat.N2()).fill(0.0025, 0, 0, 1);

    final var dt = 0.005;

    // Continuous Q should be positive semidefinite
    final var esCont = contQ.getStorage().eig();
    for (int i = 0; i < contQ.getNumRows(); ++i) {
      assertTrue(esCont.getEigenvalue(i).real >= 0);
    }

    //       T
    // Q_d = ∫ e^(Aτ) Q e^(Aᵀτ) dτ
    //       0
    final var discQIntegrated =
        RungeKuttaTimeVarying.rungeKuttaTimeVarying(
            (Double t, Matrix<N2, N2> x) ->
                contA.times(t).exp().times(contQ).times(contA.transpose().times(t).exp()),
            0.0,
            new Matrix<>(Nat.N2(), Nat.N2()),
            dt);

    var discA = Discretization.discretizeA(contA, dt);

    var discAQPair = Discretization.discretizeAQ(contA, contQ, dt);
    var discATaylor = discAQPair.getFirst();
    var discQTaylor = discAQPair.getSecond();

    assertTrue(
        discQIntegrated.minus(discQTaylor).normF() < 1e-3,
        "Expected these to be nearly equal:\ndiscQTaylor:\n"
            + discQTaylor
            + "\ndiscQIntegrated:\n"
            + discQIntegrated);
    assertTrue(discA.minus(discATaylor).normF() < 1e-10);

    // Discrete Q should be positive semidefinite
    final var esDisc = discQTaylor.getStorage().eig();
    for (int i = 0; i < discQTaylor.getNumRows(); ++i) {
      assertTrue(esDisc.getEigenvalue(i).real >= 0);
    }
  }

  // Test that DiscretizeR() works
  @Test
  void testDiscretizeR() {
    var contR = Matrix.mat(Nat.N2(), Nat.N2()).fill(2.0, 0.0, 0.0, 1.0);
    var discRTruth = Matrix.mat(Nat.N2(), Nat.N2()).fill(4.0, 0.0, 0.0, 2.0);

    var discR = Discretization.discretizeR(contR, 0.5);

    assertTrue(
        discRTruth.minus(discR).normF() < 1e-10,
        "Expected these to be nearly equal:\ndiscR:\n" + discR + "\ndiscRTruth:\n" + discRTruth);
  }
}
