/*******************************************************************************
 * Copyright (c) 2016, 2018 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Project model element for the "Views" node.
 *
 * For now it contains the list of the standard analyses, with their outputs
 * (views) under each. For experiments all analyses from children traces are
 * aggregated and shown under the "Views" node.
 *
 * The plan is to eventually only show the views under this node, since the
 * user cannot really interact with the analyses themselves.
 *
 * @author Alexandre Montplaisir
 * @since 9.2
 */
public class TmfCoreViewsElement extends TmfCoreProjectModelElement {

    /**
     * Element of the resource path
     */
    public static final String PATH_ELEMENT = ".views"; //$NON-NLS-1$

    private static final String ELEMENT_NAME = Messages.TmfViewsElement_Name;

    /**
     * Constructor
     *
     * @param resource
     *            The resource to be associated with this element
     * @param parent
     *            The parent element
     */
    protected TmfCoreViewsElement(IResource resource, TmfCoreCommonProjectElement parent) {
        super(ELEMENT_NAME, resource, parent);
    }

    // ------------------------------------------------------------------------
    // TmfProjectModelElement
    // ------------------------------------------------------------------------

    @Override
    public TmfCoreCommonProjectElement getParent() {
        /* Type enforced at constructor */
        return (TmfCoreCommonProjectElement) super.getParent();
    }

    @Override
    protected synchronized void refreshChildren() {
        /* Refreshes the analysis under this trace */
        Map<String, TmfCoreAnalysisElement> childrenMap = new HashMap<>();
        for (TmfCoreAnalysisElement analysis : getParent().getAvailableAnalysis()) {
            childrenMap.put(analysis.getAnalysisId(), analysis);
        }

        TraceTypeHelper helper = TmfTraceType.getTraceType(getParent().getTraceType());

        Class<@NonNull ? extends ITmfTrace> traceClass = null;

        if (helper != null) {
            traceClass = helper.getTraceClass();
        }

        /* Remove all analysis and return */
        if (traceClass == null) {
            for (TmfCoreAnalysisElement analysis : childrenMap.values()) {
                removeChild(analysis);
            }
            return;
        }

        IPath nodePath = getResource().getFullPath();

        TmfCoreCommonProjectElement parent = getParent();

        if (parent instanceof TmfCoreTraceElement) {
            /* Add all new analysis modules or refresh outputs of existing ones */
            for (IAnalysisModuleHelper module : TmfAnalysisManager.getAnalysisModules(traceClass).values()) {

                /* If the analysis is not a child of the trace, create it */
                TmfCoreAnalysisElement analysis = childrenMap.remove(module.getId());
                if (analysis == null) {
                    IFolder analysisRes = ResourcesPlugin.getWorkspace().getRoot().getFolder(nodePath.append(module.getId()));
                    analysis = new TmfCoreAnalysisElement(module.getName(), analysisRes, this, module);
                    addChild(analysis);
                }
                analysis.refreshChildren();
            }

            /* Remove analysis that are not children of this trace anymore */
            for (TmfCoreAnalysisElement analysis : childrenMap.values()) {
                removeChild(analysis);
            }
        } else if (parent != null) {
            /* In experiment case collect trace analyses in the aggregate analyses element */
            Map<String, TmfCoreAggregateAnalysisElement> analysisMap = new HashMap<>();

            /* Add all new analysis modules or refresh outputs of existing ones */
            for (IAnalysisModuleHelper module : TmfAnalysisManager.getAnalysisModules(traceClass).values()) {

                /* If the analysis is not a child of the trace, create it */
                TmfCoreAnalysisElement analysis = childrenMap.remove(module.getId());
                TmfCoreAggregateAnalysisElement aggregateAnalysisElement = null;
                if (analysis == null) {
                    IFolder analysisRes = ResourcesPlugin.getWorkspace().getRoot().getFolder(nodePath.append(module.getId()));
                    analysis = new TmfCoreAnalysisElement(module.getName(), analysisRes, this, module);
                    aggregateAnalysisElement = new TmfCoreAggregateAnalysisElement(parent, analysis);
                    addChild(aggregateAnalysisElement);
                } else {
                    if (analysis instanceof TmfCoreAggregateAnalysisElement) {
                        aggregateAnalysisElement = (TmfCoreAggregateAnalysisElement) analysis;
                    } else {
                        aggregateAnalysisElement = new TmfCoreAggregateAnalysisElement(parent, analysis);
                    }
                    removeChild(analysis);
                    addChild(aggregateAnalysisElement);
                }
                analysisMap.put(analysis.getAnalysisId(), aggregateAnalysisElement);
            }

            /* Now add all available trace analyses */
            for (TmfCoreAnalysisElement analysis : getParent().getAvailableChildrenAnalyses()) {
                /* If the analysis is not a child of the trace, create it */
                TmfCoreAnalysisElement a = childrenMap.remove(analysis.getAnalysisId());

                TmfCoreAggregateAnalysisElement childAnalysis = null;

                if (a instanceof TmfCoreAggregateAnalysisElement) {
                    childAnalysis = (TmfCoreAggregateAnalysisElement) a;
                } else {
                    childAnalysis = analysisMap.get(analysis.getAnalysisId());
                }

                if (childAnalysis == null) {
                    childAnalysis = new TmfCoreAggregateAnalysisElement(parent, analysis);
                    addChild(childAnalysis);
                } else {
                    childAnalysis.addAnalyses(analysis);
                }
                analysisMap.put(analysis.getAnalysisId(), childAnalysis);
            }

            /* Remove analysis that are not children of this trace anymore */
            for (TmfCoreAnalysisElement analysis : childrenMap.values()) {
                removeChild(analysis);
            }
        }
    }

    /**
     * Remove children analysis from aggregated traces
     *
     * @param analysisElements
     *              list of analysis elements to remove
     *
     * @since 3.0
     */
    public void removeChildrenAnalysis(List<@NonNull TmfCoreAnalysisElement> analysisElements) {
        for (TmfCoreAnalysisElement tmfAnalysisElement : analysisElements) {
            TmfCoreAggregateAnalysisElement aggrElement = getAggregateAnalysisElement(tmfAnalysisElement);
            if (aggrElement != null) {
                aggrElement.removeAnalyses(tmfAnalysisElement);
                if (aggrElement.isEmpty()) {
                    removeChild(aggrElement);
                }
            }
        }
    }

    private TmfCoreAggregateAnalysisElement getAggregateAnalysisElement(TmfCoreAnalysisElement element) {
        return getChildren().stream()
                .filter(TmfCoreAggregateAnalysisElement.class::isInstance)
                .map(elem -> ((TmfCoreAggregateAnalysisElement) elem))
                .filter(elem -> elem.getAnalysisHelper().getId().equals(element.getAnalysisHelper().getId()))
                .findFirst().orElse(null);
    }
}
