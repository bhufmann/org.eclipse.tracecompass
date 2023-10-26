/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import org.eclipse.core.resources.IResource;
import org.eclipse.tracecompass.tmf.core.analysis.ondemand.IOnDemandAnalysisReport;

/**
 * Project model element containing a report, which is the result of the
 * execution of an on-demand analysis.
 *
 * @author Alexandre Montplaisir
 * @since 9.2
 */
public class TmfCoreReportElement extends TmfCoreProjectModelElement {

    private final IOnDemandAnalysisReport fReport;

    /**
     * Constructor
     *
     * @param reportName
     *            Name of this report element
     * @param resource
     *            The resource to be associated with this element
     * @param parent
     *            The parent element
     * @param report
     *            The report object represented by this element
     */
    protected TmfCoreReportElement(String reportName, IResource resource,
            TmfCoreReportsElement parent,  IOnDemandAnalysisReport report) {
        super(reportName, resource, parent);
        fReport = report;
    }

    @Override
    public TmfCoreReportsElement getParent() {
        /* Type enforced at constructor */
        return (TmfCoreReportsElement) super.getParent();
    }

    @Override
    protected void refreshChildren() {
        /* No children */
    }

    /**
     * Get the report object of this element.
     *
     * @return The report
     */
    public IOnDemandAnalysisReport getReport() {
        return fReport;
    }
}
