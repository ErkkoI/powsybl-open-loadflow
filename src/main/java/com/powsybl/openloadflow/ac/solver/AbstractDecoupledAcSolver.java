/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac.solver;

import com.powsybl.openloadflow.ac.equations.AcEquationType;
import com.powsybl.openloadflow.ac.equations.AcVariableType;
import com.powsybl.openloadflow.equations.EquationSystem;
import com.powsybl.openloadflow.equations.EquationVector;
import com.powsybl.openloadflow.equations.JacobianMatrix;
import com.powsybl.openloadflow.equations.TargetVector;
import com.powsybl.openloadflow.network.LfNetwork;

import java.util.Objects;

/**
 * @author Erkko Ihalainen {@literal <business at erkkoihalainen.fi>}
 */
public abstract class AbstractDecoupledAcSolver implements AcSolver {

    protected final LfNetwork network;

    protected final EquationSystem<AcVariableType, AcEquationType> qEquationSystem;

    protected final EquationSystem<AcVariableType, AcEquationType> pEquationSystem;

    protected final JacobianMatrix<AcVariableType, AcEquationType> h;

    protected final JacobianMatrix<AcVariableType, AcEquationType> n;

    protected final TargetVector<AcVariableType, AcEquationType> qTargetVector;

    protected final TargetVector<AcVariableType, AcEquationType> pTargetVector;

    protected final EquationVector<AcVariableType, AcEquationType> pEquationVector;

    protected final EquationVector<AcVariableType, AcEquationType> qEquationVector;

    protected boolean detailedReport;

    protected AbstractDecoupledAcSolver(LfNetwork network,
                                        EquationSystem<AcVariableType, AcEquationType> pEquationSystem,
                                        EquationSystem<AcVariableType, AcEquationType> qEquationSystem,
                                        JacobianMatrix<AcVariableType, AcEquationType> h,
                                        JacobianMatrix<AcVariableType, AcEquationType> n,
                                        TargetVector<AcVariableType, AcEquationType> pTargetVector,
                                        TargetVector<AcVariableType, AcEquationType> qTargetVector,
                                        EquationVector<AcVariableType, AcEquationType> pEquationVector,
                                        EquationVector<AcVariableType, AcEquationType> qEquationVector,
                                        boolean detailedReport) {
        this.network = Objects.requireNonNull(network);
        this.pEquationSystem = Objects.requireNonNull(pEquationSystem);
        this.qEquationSystem = Objects.requireNonNull(qEquationSystem);
        this.h = Objects.requireNonNull(h);
        this.n = Objects.requireNonNull(n);
        this.pTargetVector = Objects.requireNonNull(pTargetVector);
        this.qTargetVector = Objects.requireNonNull(qTargetVector);
        this.pEquationVector = Objects.requireNonNull(pEquationVector);
        this.qEquationVector = Objects.requireNonNull(qEquationVector);
        this.detailedReport = detailedReport;
    }
}
