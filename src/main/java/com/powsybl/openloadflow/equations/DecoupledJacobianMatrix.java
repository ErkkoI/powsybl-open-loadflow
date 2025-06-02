/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.openloadflow.equations;

import com.google.common.base.Stopwatch;
import com.powsybl.commons.PowsyblException;
import com.powsybl.math.matrix.*;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.powsybl.openloadflow.util.Markers.PERFORMANCE_MARKER;

/**
 * @author Geoffroy Jamgotchian {@literal <geoffroy.jamgotchian at rte-france.com>}
 */
public class DecoupledJacobianMatrix<V extends Enum<V> & Quantity, E extends Enum<E> & Quantity>
        implements EquationSystemIndexListener<V, E>, StateVectorListener, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecoupledJacobianMatrix.class);

    private final EquationSystem<V, E> equationSystem;

    private final MatrixFactory matrixFactory;

    private final List<Pair<V, E>> filters;

    private HashMap<Pair<V, E>, Matrix> subMatrices;

    private HashMap<Pair<V, E>, LUDecomposition> subDecompositions;

    protected enum Status {
        VALID,
        VALUES_INVALID, // same structure but values have to be updated
        VALUES_AND_ZEROS_INVALID, // same structure but values have to be updated and non-zero values might have changed
        STRUCTURE_INVALID, // structure has changed
    }

    private Status status = Status.STRUCTURE_INVALID;

    public DecoupledJacobianMatrix(EquationSystem<V, E> equationSystem,
                                   MatrixFactory matrixFactory,
                                   List<Pair<V, E>> filters) {
        this.filters = filters;
        this.equationSystem = Objects.requireNonNull(equationSystem);
        this.matrixFactory = Objects.requireNonNull(matrixFactory);
        equationSystem.getIndex().addListener(this);
        equationSystem.getStateVector().addListener(this);
    }

    protected void updateStatus(Status status) {
        if (status.ordinal() > this.status.ordinal()) {
            this.status = status;
        }
    }

    @Override
    public void onEquationChange(Equation<V, E> equation, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onVariableChange(Variable<V> variable, ChangeType changeType) {
        updateStatus(Status.STRUCTURE_INVALID);
    }

    @Override
    public void onEquationTermChange(EquationTerm<V, E> term) {
        updateStatus(Status.VALUES_AND_ZEROS_INVALID);
    }

    @Override
    public void onStateUpdate() {
        updateStatus(Status.VALUES_INVALID);
    }

    private void initSubDer(Pair<V, E> filter) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        Map<E, List<Equation<V, E>>> grouped_eq = equationSystem
                .getIndex()
                .getSortedEquationsToSolve()
                .stream()
                .collect(Collectors.groupingBy(Equation::getType));

        Map<V, List<Variable<V>>> grouped_var = equationSystem
                .getIndex()
                .getSortedVariablesToFind()
                .stream()
                .collect(Collectors.groupingBy(Variable::getType));

        int rowCount = equationSystem.getIndex().getSortedEquationsToSolve().size();
        int columnCount = equationSystem.getIndex().getSortedVariablesToFind().size();
        if (rowCount != columnCount) {
            throw new PowsyblException("Expected to have same number of equations (" + rowCount
                    + ") and variables (" + columnCount + ")");
        }

        int estimatedNonZeroValueCount = rowCount * 3;
        Matrix subMMatrix = matrixFactory.create(rowCount, columnCount, estimatedNonZeroValueCount);

        for (Equation<V, E> eq : equationSystem.getIndex().getSortedEquationsToSolve()) {
            if (eq.getType() != filter.getRight()){
                continue;
            }
            int column = eq.getColumn();
            eq.der((variable, value, matrixElementIndex) -> {
                if (variable.getType() != filter.getLeft()){
                    return matrixElementIndex;
                }
                int row = variable.getRow();
                return subMMatrix.addAndGetIndex(row, column, value);
            });
        }
        this.subMatrices.put(filter, subMMatrix);
        LOGGER.debug(PERFORMANCE_MARKER, "Sub-Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    private void initDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (Pair<V, E> filter: filters){
            initSubDer(filter);
        }
        LOGGER.debug(PERFORMANCE_MARKER, "Decoupled Jacobian matrix built in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    private void clearSubLu(Pair<V, E> filter) {
        LUDecomposition subLu = subDecompositions.remove(filter);
        if (subLu != null) {
            subLu.close();
        }
    }

    private void clearLu() {
        for (Pair<V, E> filter: filters){
            clearSubLu(filter);
        }
    }

    private void initMatrix() {
        initDer();
        clearLu();
    }

    private void updateSubDer(Pair<V, E> filter) {
        Stopwatch stopwatch = Stopwatch.createStarted();

        Matrix subMatrix = subMatrices.get(filter);
        subMatrix.reset();
        for (Equation<V, E> eq : equationSystem.getIndex().getSortedEquationsToSolve()) {
            if (eq.getType() != filter.getRight()){
                continue;
            }
            eq.der((variable, value, matrixElementIndex) -> {
                if (variable.getType() != filter.getLeft()){
                    return matrixElementIndex;
                }
                subMatrix.addAtIndex(matrixElementIndex, value);
                return matrixElementIndex; // don't change element index
            });
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Sub-Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    private void updateDer() {
        Stopwatch stopwatch = Stopwatch.createStarted();

        for (Pair<V, E> filter: filters){
            updateSubDer(filter);
        }

        LOGGER.debug(PERFORMANCE_MARKER, "Decoupled Jacobian matrix values updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    private void updateSubLu(boolean allowIncrementalUpdate, Pair<V, E> filter) {
        LUDecomposition subLu = subDecompositions.get(filter);
        if (subLu != null) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            subLu.update(allowIncrementalUpdate);

            LOGGER.debug(PERFORMANCE_MARKER, "Sub-LU decomposition updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
        }
    }

    private void updateLu(boolean allowIncrementalUpdate) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        for (Pair<V, E> filter: filters){
            updateSubLu(allowIncrementalUpdate, filter);
        }
        LOGGER.debug(PERFORMANCE_MARKER, "LU decomposition updated in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
    }

    private void updateValues(boolean allowIncrementalUpdate) {
        updateDer();
        try {
            updateLu(allowIncrementalUpdate);
        } catch (MatrixException ex) {
            if (allowIncrementalUpdate) {
                // Try another time without incremental
                LOGGER.warn("Exception when updating LU matrix in incremental mode. Retrying without incremental mode");
                updateLu(false);
            } else {
                // Rethrow the exception
                throw ex;
            }
        }
    }

    public void forceUpdate() {
        update();
    }

    private void update() {
        if (status != Status.VALID) {
            switch (status) {
                case STRUCTURE_INVALID:
                    initMatrix();
                    break;

                case VALUES_INVALID:
                    updateValues(true);
                    break;

                case VALUES_AND_ZEROS_INVALID:
                    updateValues(false);
                    break;

                default:
                    break;
            }
            status = Status.VALID;
        }
    }

    public HashMap<Pair<V, E>, Matrix> getMatrices() {
        update();
        return subMatrices;
    }

    private HashMap<Pair<V, E>, LUDecomposition> getLUDecompositions() {
        Stopwatch outer_stopwatch = Stopwatch.createStarted();
        for (Pair<V, E> filter: filters){
            Matrix m = subMatrices.get(filter);
            LUDecomposition lu = subDecompositions.get(filter);
            if (lu == null) {
                Stopwatch stopwatch = Stopwatch.createStarted();

                subDecompositions.put(filter, m.decomposeLU());

                LOGGER.debug(PERFORMANCE_MARKER, "Sub-LU decomposition done in {} us", stopwatch.elapsed(TimeUnit.MICROSECONDS));
            }
        }
        LOGGER.debug(PERFORMANCE_MARKER, "LU decomposition done in {} us", outer_stopwatch.elapsed(TimeUnit.MICROSECONDS));
        return subDecompositions;
    }

    public void solve(Pair<V, E> filter, double[] b) {
        getLUDecompositions().get(filter).solve(b);
    }

    public void solveTransposed(Pair<V, E> filter, double[] b) {
        getLUDecompositions().get(filter).solveTransposed(b);
    }

    public void solve(HashMap<Pair<V, E>, double[]> b) {
        for (Map.Entry<Pair<V, E>, double[]> entry: b.entrySet()){
            solve(entry.getKey(), entry.getValue());
        }
    }

    public void solveTransposed(HashMap<Pair<V, E>, double[]> b) {
        for (Map.Entry<Pair<V, E>, double[]> entry: b.entrySet()){
            solveTransposed(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void close() {
        equationSystem.getIndex().removeListener(this);
        equationSystem.getStateVector().removeListener(this);
        for (Pair<V, E> filter: filters){
            subMatrices.remove(filter);
        }
        clearLu();
    }
}
