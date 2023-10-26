/*******************************************************************************
 * Copyright (c) 2010, 2018 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Francois Chouinard - Initial API and implementation
 *   Geneviève Bastien - Copied code to add/remove traces in this class
 *   Patrick Tasse - Close editors to release resources
 *   Geneviève Bastien - Experiment instantiated with trace type
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.InvalidRegistryObjectException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

/**
 * Implementation of TMF Experiment Model Element.
 * <p>
 *
 * @since 9.2
 * @author Francois Chouinard
 */
public class TmfCoreExperimentElement extends TmfCoreCommonProjectElement {

    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------
    private static final String FOLDER_SUFFIX = "_exp"; //$NON-NLS-1$

    // ------------------------------------------------------------------------
    // Static initialization
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------
    /**
     * Constructor
     *
     * @param name
     *            The name of the experiment
     * @param folder
     *            The folder reference
     * @param parent
     *            The experiment folder reference.
     */
    public TmfCoreExperimentElement(String name, IFolder folder, TmfCoreExperimentFolder parent) {
        super(name, folder, parent);
    }

    // ------------------------------------------------------------------------
    // TmfProjectModelElement
    // ------------------------------------------------------------------------

    @Override
    public IFolder getResource() {
        return (IFolder) super.getResource();
    }

    /**
     * @since 2.0
     */
    @Override
    protected synchronized void refreshChildren() {
        /* Update the trace children of this experiment */
        // Get the children from the model
        Map<String, ITmfCoreProjectModelElement> childrenMap = new HashMap<>();
        for (TmfCoreTraceElement trace : getTraces()) {
            childrenMap.put(trace.getElementPath(), trace);
        }

        List<IResource> members = getTraceResources();
        for (IResource resource : members) {
            String name = resource.getName();
            String elementPath = resource.getFullPath().makeRelativeTo(getResource().getFullPath()).toString();
            ITmfCoreProjectModelElement element = childrenMap.get(elementPath);
            if (element instanceof TmfCoreTraceElement) {
                childrenMap.remove(elementPath);
            } else {
                element = new TmfCoreTraceElement(name, resource, this);
                addChild(element);
            }
        }

        // Cleanup dangling children from the model
        for (ITmfCoreProjectModelElement danglingChild : childrenMap.values()) {
            removeChild(danglingChild);
        }

        /* Update the analysis under this experiment */
        super.refreshChildren();

        /*
         * If the experiment is opened, add any analysis that was not added by
         * the parent if it is available with the experiment
         */
        ITmfTrace experiment = getTrace();
        if (experiment == null) {
            return;
        }

        /* super.refreshChildren() above should have set this */
        TmfCoreViewsElement viewsElement = getChildElementViews();
        if (viewsElement == null) {
            return;
        }

        Map<String, TmfCoreAnalysisElement> analysisMap = new HashMap<>();
        for (TmfCoreAnalysisElement analysis : getAvailableAnalysis()) {
            analysisMap.put(analysis.getAnalysisId(), analysis);
            analysis.refreshChildren();
        }
        for (IAnalysisModuleHelper module : TmfAnalysisManager.getAnalysisModules().values()) {
            if (!analysisMap.containsKey(module.getId()) && module.appliesToExperiment() && (experiment.getAnalysisModule(module.getId()) != null)) {
                IFolder newresource = ResourcesPlugin.getWorkspace().getRoot().getFolder(getResource().getFullPath().append(module.getId()));
                TmfCoreAnalysisElement analysis = new TmfCoreAnalysisElement(module.getName(), newresource, viewsElement, module);
                viewsElement.addChild(analysis);
                analysis.refreshChildren();
                analysisMap.put(module.getId(), analysis);
            }
        }
    }

    @Override
    public List<TmfCoreAnalysisElement> getAvailableChildrenAnalyses() {
        return getChildren().stream().filter(TmfCoreTraceElement.class::isInstance)
                .map(TmfCoreTraceElement.class::cast)
                .map(TmfCoreTraceElement::getElementUnderTraceFolder)
                .filter(traceElement -> traceElement.getChildElementViews() != null)
                .flatMap(traceElement -> traceElement.getAvailableAnalysis().stream())
                .collect(Collectors.toList());
    }

    private List<IResource> getTraceResources() {
        IFolder folder = getResource();
        final List<IResource> list = new ArrayList<>();
        try {
            folder.accept(new IResourceProxyVisitor() {
                @Override
                public boolean visit(IResourceProxy resource) throws CoreException {
                    /*
                     * Trace represented by a file, or a link for backward compatibility
                     */
                    if (resource.getType() == IResource.FILE || resource.isLinked()) {
                        list.add(resource.requestResource());
                        /* don't visit linked folders, as they might contain files */
                        return false;
                    }
                    return true;
                }
            }, IResource.NONE);
        } catch (CoreException e) {
        }
        list.sort(Comparator.comparing(resource -> resource.getFullPath().toString()));
        return list;
    }

    /**
     * @since 2.0
     */
    @Override
    public String getLabelText() {
        return getName() + " [" + getTraces().size() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Refreshes the trace type filed by reading the trace type persistent
     * property of the resource reference.
     *
     * If trace type is null after refresh, set it to the generic trace type
     * (for seamless upgrade)
     */
    @Override
    public void refreshTraceType() {
        super.refreshTraceType();
        if (getTraceType() == null) {
            IConfigurationElement ce = TmfTraceType.getTraceAttributes(TmfTraceType.DEFAULT_EXPERIMENT_TYPE);
            if (ce != null) {
                try {
                    IFolder experimentFolder = getResource();
                    experimentFolder.setPersistentProperty(TmfCommonConstants.TRACETYPE, ce.getAttribute(TmfTraceType.ID_ATTR));
                    super.refreshTraceType();
                } catch (InvalidRegistryObjectException | CoreException e) {
                }
            }
        }
    }

    /**
     * Returns a list of TmfTraceElements contained in this experiment.
     *
     * @return a list of TmfTraceElements
     */
    @Override
    public List<TmfCoreTraceElement> getTraces() {
        List<ITmfCoreProjectModelElement> children = getChildren();
        List<TmfCoreTraceElement> traces = new ArrayList<>();
        for (ITmfCoreProjectModelElement child : children) {
            if (child instanceof TmfCoreTraceElement) {
                traces.add((TmfCoreTraceElement) child);
            }
        }
        return traces;
    }

    /**
     * Adds a trace to the experiment
     *
     * @param trace
     *            The trace element to add
     */
    public void addTrace(TmfCoreTraceElement trace) {
        addTrace(trace, true);
    }

    /**
     * Adds a trace to the experiment
     *
     * @param trace
     *            The trace element to add
     * @param refresh
     *            Flag for refreshing the project
     */
    public void addTrace(TmfCoreTraceElement trace, boolean refresh) {
        addTrace(trace.getResource(), trace.getElementPath(), refresh);
    }

    private void addTrace(IResource resource, String elementPath, boolean refresh) {
        /*
         * Create an empty file to represent the experiment trace. The file's element
         * path relative to the experiment resource corresponds to the trace's element
         * path relative to the Traces folder.
         */
        IFile file = getResource().getFile(elementPath);
        try {
            TraceUtils.createFolder((IFolder) file.getParent(), new NullProgressMonitor());
            file.create(new ByteArrayInputStream(new byte[0]), false, new NullProgressMonitor());
            String traceTypeId = TmfTraceType.getTraceTypeId(resource);
            TraceTypeHelper traceType = TmfTraceType.getTraceType(traceTypeId);
            if (traceType != null) {
                TmfTraceTypeUIUtils.setTraceType(file, traceType, refresh);
            }
        } catch (CoreException e) {
            Activator.logError("Error adding experiment trace file " + file, e); //$NON-NLS-1$
        }
    }

    /**
     * Removes a trace from an experiment
     *
     * @param trace
     *            The trace to remove
     * @throws CoreException
     *             exception
     */
    public void removeTrace(TmfCoreTraceElement trace) throws CoreException {
        removeTrace(trace, true);
    }

    /**
     * Removes a trace from an experiment
     *
     * @param trace
     *            The trace to remove
     * @param closeEditors
     *            if true, editors associated with this trace are first closed
     *            before proceeding, otherwise it is the responsibility of the
     *            caller to first close editors before calling the method
     *
     * @throws CoreException
     *             exception
     * @since 4.0
     */
    public void removeTrace(TmfCoreTraceElement trace, boolean closeEditors) throws CoreException {

        // Close editors in UI Thread
        // TODO
//        if (closeEditors) {
//            Display.getDefault().syncExec(this::closeEditors);
//        }

        /* Remove all trace analyses from experiment view */
        TmfCoreViewsElement view = getChildElementViews();
        if (view != null) {
            List<@NonNull TmfCoreAnalysisElement> analysisElements = trace.getElementUnderTraceFolder().getAvailableAnalysis();
            view.removeChildrenAnalysis(analysisElements);
        }

        /* Finally, remove the trace from experiment */
        removeChild(trace);
        deleteTraceResource(trace.getResource());
        deleteSupplementaryResources();
    }

    private void deleteTraceResource(IResource resource) throws CoreException {
        resource.delete(true, null);
        IContainer parent = resource.getParent();
        // delete empty folders up to the parent experiment folder
        if (!parent.equals(getResource()) && parent.exists() && parent.members().length == 0) {
            deleteTraceResource(parent);
        }
    }

    @Override
    public IResource copy(String newName, boolean copySuppFiles, boolean copyAsLink) {
        // Copy the experiment resource to keep all the information attached to this resource
        IResource copiedExpResource = super.copy(newName, copySuppFiles, true);
        if (copyAsLink) {
            return copiedExpResource;
        }

        TmfCoreExperimentFolder experimentsFolder = getProject().getExperimentsFolder();
        TmfCoreTraceFolder tracesFolder = getProject().getTracesFolder();
        if (experimentsFolder == null || copiedExpResource == null || tracesFolder == null) {
            return null;
        }

        experimentsFolder.refreshChildren();
        TmfCoreExperimentElement copiedExperiment = experimentsFolder.getExperiment(copiedExpResource);
        if (copiedExperiment == null) {
            return null;
        }

        // Copy the traces
        List<TmfCoreTraceElement> originalExpTraces = copiedExperiment.getTraces();
        Map<IResource, String> copiedTraceResources = new HashMap<>();
        for (TmfCoreTraceElement originalExpTrace : originalExpTraces) {
            TmfCoreTraceElement traceElement = originalExpTrace.getElementUnderTraceFolder();
            IFolder newFolder = tracesFolder.getResource().getFolder(newName);
            IPath traceElementPath = traceElement.getPath().makeRelativeTo(tracesFolder.getPath());
            IFolder traceDestinationFolder = newFolder.getFolder(traceElementPath.removeLastSegments(1));
            try {
                if (!traceDestinationFolder.exists()) {
                    TraceUtils.createFolder(traceDestinationFolder, null);
                }
            } catch (CoreException e) {
                Activator.logError("Error copying experiment", e); //$NON-NLS-1$
            }
            IPath newTracePath = newFolder.getFullPath().append(traceElementPath);
            String elementPath = new Path(newName).append(traceElementPath).toString();
            IResource copiedTraceResource = traceElement.copy(copySuppFiles, copyAsLink, newTracePath);
            if (copiedTraceResource != null) {
                copiedTraceResources.put(copiedTraceResource, elementPath);
            }
        }

        // Remove original traces from the new experiment
        for (TmfCoreTraceElement originalExpTrace : originalExpTraces) {
            try {
                copiedExperiment.deleteTraceResource(originalExpTrace.getResource());
            } catch (CoreException e) {
                Activator.logError("Error copying experiment", e); //$NON-NLS-1$
            }
        }

        // Add copied traces to the new experiment
        for (Entry<IResource, String> copiedTraceEntry : copiedTraceResources.entrySet()) {
            copiedExperiment.addTrace(copiedTraceEntry.getKey(), copiedTraceEntry.getValue(), false);
        }

        return copiedExpResource;
    }


    /**
     * Instantiate a {@link TmfExperiment} object based on the experiment type
     * and the corresponding extension.
     *
     * @return the {@link TmfExperiment} or <code>null</code> for an error
     */
    @Override
    public TmfExperiment instantiateTrace() {
        try {

            // make sure that supplementary folder exists
            refreshSupplementaryFolder();

            String typeID = getTraceType();
            if (typeID != null) {
                return TmfTraceType.instantiateExperiment(typeID);
            }
        } catch (CoreException e) {
            Activator.logError(NLS.bind(Messages.TmfExperimentElement_ErrorInstantiatingTrace, getName()), e);
        }
        return null;
    }

    @Override
    public String getTypeName() {
        return Messages.TmfExperimentElement_TypeName;
    }

    /**
     * Return the suffix for resource names
     *
     * @return The folder suffix
     */
    @Override
    public String getSuffix() {
        return FOLDER_SUFFIX;
    }

}
