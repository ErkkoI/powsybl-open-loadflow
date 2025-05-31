/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.ac;

import com.powsybl.iidm.network.*;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.math.matrix.DenseMatrixFactory;
import com.powsybl.openloadflow.OpenLoadFlowParameters;
import com.powsybl.openloadflow.OpenLoadFlowProvider;
import com.powsybl.openloadflow.ac.solver.DecoupledNewtonRaphsonFactory;
import com.powsybl.openloadflow.network.EurostagFactory;
import com.powsybl.openloadflow.network.SlackBusSelectionMode;
import com.powsybl.openloadflow.network.TwoBusNetworkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.powsybl.openloadflow.util.LoadFlowAssert.*;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertActivePowerEquals;
import static com.powsybl.openloadflow.util.LoadFlowAssert.assertReactivePowerEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Erkko Ihalainen {@literal <business at erkkoihalainen.fi>}
 */
class DecoupledNewtonRaphsonTest {

    private LoadFlow.Runner loadFlowRunner;

    private LoadFlowParameters parameters;

    @BeforeEach
    void setUp() {
        parameters = new LoadFlowParameters().setUseReactiveLimits(false)
                .setDistributedSlack(false);
        OpenLoadFlowParameters.create(parameters)
                .setSlackBusSelectionMode(SlackBusSelectionMode.FIRST)
                .setAcSolverType(DecoupledNewtonRaphsonFactory.NAME);
        loadFlowRunner = new LoadFlow.Runner(new OpenLoadFlowProvider(new DenseMatrixFactory()));    }

    @Test
    void decoupledNewtonRaphsonTest() {
        Network network = TwoBusNetworkFactory.create();
        Bus bus1 = network.getBusBreakerView().getBus("b1");
        Bus bus2 = network.getBusBreakerView().getBus("b2");
        Line line1 = network.getLine("l12");
        LoadFlowResult result = loadFlowRunner.run(network, parameters);
        assertTrue(result.isFullyConverged());

        assertVoltageEquals(1, bus1);
        assertAngleEquals(0, bus1);
        assertVoltageEquals(0.855, bus2);
        assertAngleEquals(-13.520904, bus2);
        assertActivePowerEquals(2, line1.getTerminal1());
        assertReactivePowerEquals(1.683, line1.getTerminal1());
        assertActivePowerEquals(-2, line1.getTerminal2());
        assertReactivePowerEquals(-1, line1.getTerminal2());
    }
    @Test
    void decoupledNewtonRaphsonTestEurostag() {
        Network network = EurostagFactory.fix(EurostagTutorialExample1Factory.create());
        Bus genBus = network.getBusBreakerView().getBus("NGEN");
        Bus bus1 = network.getBusBreakerView().getBus("NHV1");
        Bus bus2 = network.getBusBreakerView().getBus("NHV2");
        Bus loadBus = network.getBusBreakerView().getBus("NLOAD");
        Line line1 = network.getLine("NHV1_NHV2_1");
        Line line2 = network.getLine("NHV1_NHV2_2");

        LoadFlowResult result = loadFlowRunner.run(network, parameters);

        assertSame(LoadFlowResult.ComponentResult.Status.CONVERGED, result.getComponentResults().get(0).getStatus());

        assertVoltageEquals(24.5, genBus);
        assertAngleEquals(0, genBus);
        assertVoltageEquals(402.143, bus1);
        assertAngleEquals(-2.325965, bus1);
        assertVoltageEquals(389.953, bus2);
        assertAngleEquals(-5.832329, bus2);
        assertVoltageEquals(147.578, loadBus);
        assertAngleEquals(-11.940451, loadBus);
        assertActivePowerEquals(302.444, line1.getTerminal1());
        assertReactivePowerEquals(98.74, line1.getTerminal1());
        assertActivePowerEquals(-300.434, line1.getTerminal2());
        assertReactivePowerEquals(-137.188, line1.getTerminal2());
        assertActivePowerEquals(302.444, line2.getTerminal1());
        assertReactivePowerEquals(98.74, line2.getTerminal1());
        assertActivePowerEquals(-300.434, line2.getTerminal2());
        assertReactivePowerEquals(-137.188, line2.getTerminal2());
    }
}
