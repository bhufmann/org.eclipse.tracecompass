/*******************************************************************************
 * Copyright (c) 2013, 2018 École Polytechnique de Montréal
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisOutput;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisOutputManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.osgi.framework.Bundle;

/**
 * Class for project elements of type analysis modules
 *
 * @author Geneviève Bastien
 * @since 9.2
 */
public class TmfCoreAnalysisElement extends TmfCoreProjectModelElement {

    private final @NonNull IAnalysisModuleHelper fAnalysisHelper;
    private volatile boolean fCanExecute = true;

    /**
     * Constructor
     *
     * @param name
     *            Name of the analysis
     * @param resource
     *            The resource
     * @param parent
     *            Parent of the analysis
     * @param module
     *            The analysis module helper
     * @since 2.0
     */
    protected TmfCoreAnalysisElement(String name, IResource resource,
            TmfCoreViewsElement parent, @NonNull IAnalysisModuleHelper module) {
        super(name, resource, parent);
        fAnalysisHelper = module;
    }

    // ------------------------------------------------------------------------
    // TmfProjectModelElement
    // ------------------------------------------------------------------------

    /**
     * @since 2.0
     */
    @Override
    public TmfCoreViewsElement getParent() {
        /* Type enforced at constructor */
        return (TmfCoreViewsElement) super.getParent();
    }

    /**
     * @since 2.0
     */
    @Override
    protected synchronized void refreshChildren() {
        fCanExecute = true;

        /* Refresh the outputs of this analysis */
        Map<String, TmfAnalysisOutputElement> childrenMap = new HashMap<>();
        for (TmfAnalysisOutputElement output : getAvailableOutputs()) {
            childrenMap.put(output.getName(), output);
        }

        /** Get base path for resource */
        final TmfCoreTraceFolder tracesFolder = getProject().getTracesFolder();
        if (tracesFolder == null) {
            return;
        }
        IPath path = tracesFolder.getPath();
        IResource resource = getResource();
        if (resource instanceof IFolder) {
            path = ((IFolder) resource).getFullPath();
        }

        IAnalysisModule module = getAnalysisModule();
        if(module == null) {
            return;
        }

        TmfAnalysisOutputManager manager = TmfAnalysisOutputManager.getInstance();
        for (IAnalysisOutput output : module.getOutputs()) {
            TmfAnalysisOutputElement outputElement = childrenMap.remove(output.getName());
            if (outputElement == null && !manager.isHidden(getParent().getParent().getTraceType(), fAnalysisHelper.getId(), output.getId())) {
                IFolder newresource = ResourcesPlugin.getWorkspace().getRoot().getFolder(path.append(output.getName()));
                outputElement = new TmfAnalysisOutputElement(output.getName(), newresource, this, output);
                addChild(outputElement);
                outputElement.refreshChildren();
            }
        }

        /* Remove outputs that are not children of this analysis anymore */
        for (TmfAnalysisOutputElement output : childrenMap.values()) {
            removeChild(output);
        }
    }

    private IAnalysisModule getAnalysisModule() {
        /*
         * We can get a list of available outputs once the analysis is
         * instantiated when the trace is opened
         */
        IResource parentTraceResource = getParent().getParent().getResource();

        /*
         * Find any trace instantiated with the same resource as the parent trace
         */
        ITmfTrace trace = null;
        for (ITmfTrace openedTrace : TmfTraceManager.getInstance().getOpenedTraces()) {
            for (ITmfTrace t : TmfTraceManager.getTraceSetWithExperiment(openedTrace)) {
                if (parentTraceResource.equals(t.getResource())) {
                    trace = t;
                    break;
                }
            }
        }
        if (trace == null) {
            deleteOutputs();
            return null;
        }

        IAnalysisModule module = trace.getAnalysisModule(fAnalysisHelper.getId());
        if (module == null) {
            deleteOutputs();
            /*
             * Trace is opened, but the analysis is null, so it does not
             * apply
             */
            fCanExecute = false;
            return null;
        }
        return module;
    }

    // ------------------------------------------------------------------------
    // TmfProjectModelElement
    // ------------------------------------------------------------------------


    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Get the list of analysis output model elements under this analysis
     *
     * @return Array of analysis output elements
     */
    public List<TmfAnalysisOutputElement> getAvailableOutputs() {
        List<ITmfCoreProjectModelElement> children = getChildren();
        List<TmfAnalysisOutputElement> outputs = new ArrayList<>();
        for (ITmfCoreProjectModelElement child : children) {
            if (child instanceof TmfAnalysisOutputElement) {
                outputs.add((TmfAnalysisOutputElement) child);
            }
        }
        return outputs;
    }

    /**
     * Gets the analysis id of this module
     *
     * @return The analysis id
     */
    public String getAnalysisId() {
        return fAnalysisHelper.getId();
    }

    /**
     * Gets the help message for this analysis
     *
     * @return The help message
     */
    public String getHelpMessage() {
        TmfCoreCommonProjectElement parentTrace = getParent().getParent();

        ITmfTrace trace = null;
        if (parentTrace instanceof TmfCoreTraceElement) {
            TmfCoreTraceElement traceElement = (TmfCoreTraceElement) parentTrace;
            trace = traceElement.getTrace();
            if (trace != null) {
                IAnalysisModule module = trace.getAnalysisModule(fAnalysisHelper.getId());
                if (module != null) {
                    return module.getHelpText(trace);
                }
            }
        }

        if (trace != null) {
            return fAnalysisHelper.getHelpText(trace);
        }

        return fAnalysisHelper.getHelpText();
    }

    /**
     * Gets the icon file name for the analysis
     *
     * @return The analysis icon file name
     */
    public String getIconFile() {
        return fAnalysisHelper.getIcon();
    }

    /**
     * Gets the bundle this analysis is from
     *
     * @return The analysis bundle
     */
    public Bundle getBundle() {
        return fAnalysisHelper.getBundle();
    }

    /** Delete all outputs under this analysis element */
    private void deleteOutputs() {
        for (TmfAnalysisOutputElement output : getAvailableOutputs()) {
            removeChild(output);
        }
    }

    /**
     * Checks whether the analysis can be executed or not.
     *
     * @return <code>true</code> if analysis can be executed else
     *         <code>false</code>
     * @since 3.0
     */
    public boolean canExecute() {
        return fCanExecute;
    }

    /**
     * Schedule an analysis
     *
     * @return the status of the analysis scheduling
     * @since 5.2
     */
    public IStatus scheduleAnalysis() {
        IAnalysisModule module = getAnalysisModule();
        if (module == null) {
            return new Status(IStatus.INFO, Activator.PLUGIN_ID, String.format("null analysis for %s", getAnalysisHelper().getName())); //$NON-NLS-1$
        }
        new Thread(() -> {
            module.schedule();
        }).run();
        return Status.OK_STATUS;
    }

    /**
     * Gets the analysis helper for this analysis.
     *
     * @return the analysis module helper
     * @since 3.0
     */
    @NonNull protected IAnalysisModuleHelper getAnalysisHelper() {
        return fAnalysisHelper;
    }

    /**
     * Make sure the trace this analysis is associated to is the currently
     * selected one
     * @since 2.0
     */
    public void activateParentTrace() {
        TmfCoreCommonProjectElement parentTrace = getParent().getParent();

        if (parentTrace instanceof TmfCoreTraceElement) {
            // TODO open trace
//            TmfTraceElement traceElement = (TmfTraceElement) parentTrace;
//            IStatus status = TmfOpenTraceHelper.openFromElement(traceElement);
            IStatus status = Status.OK_STATUS;
            if (!status.isOK()) {
                Activator.logError("Error activating parent trace: " + status.getMessage()); //$NON-NLS-1$
            }
        }
    }

    /**
     * Get the analysis properties of this analysisElement if the corresponding
     * analysis exists for the current trace
     *
     * @return a map with the names and values of the trace properties
     *         respectively as keys and values
     */
    public Map<String, String> getAnalysisProperties() {
        ITmfCoreProjectModelElement parent = getParent();
        if (!(parent instanceof TmfCoreViewsElement)) {
            return Collections.emptyMap();
        }
        parent = parent.getParent();
        if (parent instanceof TmfCoreCommonProjectElement) {
            ITmfTrace trace = ((TmfCoreCommonProjectElement) parent).getTrace();
            if (trace == null) {
                return Collections.emptyMap();
            }
            IAnalysisModule module = trace.getAnalysisModule(fAnalysisHelper.getId());
            if (module instanceof ITmfPropertiesProvider) {
                return ((ITmfPropertiesProvider) module).getProperties();
            }
        }

        return Collections.emptyMap();
    }

    public Map<String, String> getAnalysisHelperProperties() {
        if (fAnalysisHelper instanceof ITmfPropertiesProvider) {
            ITmfPropertiesProvider analysisProperties = (ITmfPropertiesProvider) fAnalysisHelper;
            return analysisProperties.getProperties();
        }
        return Collections.emptyMap();
    }
}
