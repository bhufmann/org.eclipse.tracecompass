/*******************************************************************************
 * Copyright (c) 2017, 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.tmf.core.Activator;

/**
 * Class for project elements of type compound analysis modules.
 *
 * This element aggregates analyses with the same type that come from various
 * traces contained in an experiment. This allows to show trace analyses and
 * their views under the experiment's view element.
 *
 * @author Bernd Hufmann
 * @since 9.2
 */
public class TmfCoreAggregateAnalysisElement extends TmfCoreAnalysisElement {

    private final @NonNull Set<TmfCoreAnalysisElement> fContainedAnalyses = new HashSet<>();
    private final @NonNull TmfCoreCommonProjectElement fExperimentParent;

    /**
     * Constructor
     *
     * @param experiment
     *            The element to use for experiment activation.
     *
     * @param module
     *            The analysis module helper.
     *            This helper is used in super and acts as a delegate
     *            helper representing all contained analyses elements.
     */
    protected TmfCoreAggregateAnalysisElement(@NonNull TmfCoreCommonProjectElement experiment, @NonNull TmfCoreAnalysisElement module) {
        super (module.getName(), module.getResource(), module.getParent(), module.getAnalysisHelper());
        fExperimentParent = experiment;
        fContainedAnalyses.add(module);
    }

    @Override
    public void dispose() {
        /* Nothing to do */
    }

    @Override
    protected synchronized void refreshChildren() {
        // refresh all children analysis as well
        for (TmfCoreAnalysisElement analysis : fContainedAnalyses) {
            analysis.refreshChildren();
        }
        super.refreshChildren();
    }

    @Override
    public boolean canExecute() {
        for (TmfCoreAnalysisElement analysis : fContainedAnalyses) {
            if (analysis.canExecute()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add an analysis element that is combined in the compound element.
     *
     * @param element
     *          analysis element to add
     */
    public void addAnalyses(@NonNull TmfCoreAnalysisElement element) {
        fContainedAnalyses.add(element);
    }

    /**
     * Remove an analysis element that is combined in the compound element.
     *
     * @param element
     *            analysis element to remove
     */
    public void removeAnalyses(@NonNull TmfCoreAnalysisElement element) {
        fContainedAnalyses.remove(element);
    }

    /**
     * Checks if aggregated list is empty or not
     *
     * @return <code>true<code> if empty else <code>false</code>
     */
    public boolean isEmpty() {
        return fContainedAnalyses.isEmpty();
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Gets the help message for this analysis
     *
     * @return The help message
     */
    @Override
    public String getHelpMessage() {
        Set<String> messages = new HashSet<>();
        for (TmfCoreAnalysisElement analysis : fContainedAnalyses) {
            messages.add(analysis.getHelpMessage());
        }
        if (!messages.isEmpty()) {
            return String.join(",", messages); //$NON-NLS-1$
        }
        return super.getHelpMessage();
    }

    /**
     * Make sure the trace this analysis is associated to is the currently
     * selected one
     */
    @Override
    public void activateParentTrace() {
        // TODO open experiment
//        IStatus status = TmfOpenTraceHelper.openFromElement(fExperimentParent);
        IStatus status = Status.OK_STATUS;
        if (!status.isOK()) {
            Activator.logError("Error activating parent trace: " + status.getMessage()); //$NON-NLS-1$
        }
    }

    @Override
    public TmfCoreViewsElement getParent() {
        return fExperimentParent.getChildElementViews();
    }
}
