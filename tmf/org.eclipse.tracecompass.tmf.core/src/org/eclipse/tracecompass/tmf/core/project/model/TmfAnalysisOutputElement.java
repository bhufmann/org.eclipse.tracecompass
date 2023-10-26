/*******************************************************************************
 * Copyright (c) 2013, 2014 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *   Patrick Tasse - Add support for folder elements
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import org.eclipse.core.resources.IResource;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisOutput;

/**
 * Class for project elements of type analysis output
 *
 * @author Geneviève Bastien
 * @since 9.2
 */
public class TmfAnalysisOutputElement extends TmfCoreProjectModelElement {

    private final IAnalysisOutput fOutput;

    /**
     * Constructor
     *
     * @param name
     *            Name of the view
     * @param resource
     *            Resource for the view
     * @param parent
     *            Parent analysis of the view
     * @param output
     *            The output object
     * @since 2.0
     */
    protected TmfAnalysisOutputElement(String name, IResource resource, TmfCoreAnalysisElement parent, IAnalysisOutput output) {
        super(name, resource, parent);
        fOutput = output;
    }

    public void outputAnalysis() {
        ITmfCoreProjectModelElement parent = getParent();
        if (parent instanceof TmfCoreAnalysisElement) {
            ((TmfCoreAnalysisElement) parent).activateParentTrace();
            fOutput.requestOutput();
        }
    }

    @Override
    protected void refreshChildren() {
        /* Nothing to do */
    }

    /**
     * Get the {@link IAnalysisOutput} element.
     *
     * @return Get the {@link IAnalysisOutput} element
     */
    IAnalysisOutput getOutput() {
        return fOutput;
    }
}
