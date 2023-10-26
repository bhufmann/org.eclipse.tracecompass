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
 *   Bernd Hufmann - Added supplementary files handling (in class TmfTraceElement)
 *   Geneviève Bastien - Copied supplementary files handling from TmfTracElement
 *                 Moved to this class code to copy a model element
 *                 Renamed from TmfWithFolderElement to TmfCommonProjectElement
 *   Patrick Tasse - Add support for folder elements
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.osgi.util.NLS;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;
import org.eclipse.tracecompass.tmf.core.io.ResourceUtil;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;

/**
 * Base class for tracing project elements: it implements the common behavior of
 * all project elements: supplementary files, analysis, types, etc.
 *
 * @author Geneviève Bastien
 * @since 9.2
 */
public abstract class TmfCoreCommonProjectElement extends TmfCoreProjectModelElement {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    private static final @NonNull Logger LOGGER = TraceCompassLog.getLogger(TmfCoreCommonProjectElement.class);

    /* Direct child elements */
    private TmfCoreViewsElement fViewsElement = null;
    private TmfCoreOnDemandAnalysesElement fOnDemandAnalysesElement = null;
    private TmfCoreReportsElement fReportsElement = null;

    /** This trace type ID as defined in plugin.xml */
    private String fTraceTypeId = null;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Constructor. Creates model element.
     *
     * @param name
     *            The name of the element
     * @param resource
     *            The resource.
     * @param parent
     *            The parent element
     */
    public TmfCoreCommonProjectElement(String name, IResource resource, TmfCoreProjectModelElement parent) {
        super(name, resource, parent);
        refreshTraceType();
    }

    // ------------------------------------------------------------------------
    // ITmfProjectModelElement
    // ------------------------------------------------------------------------

    /**
     * @since 2.0
     */
    @Override
    protected synchronized void refreshChildren() {
        /* Get the base path to put the resource to */
        IPath tracePath = getResource().getFullPath();
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        if (this.getParent() instanceof TmfCoreExperimentElement) {
            return;
        }

        if (TmfTraceType.getTraceType(getTraceType()) == null) {
            if (fViewsElement != null) {
                removeChild(fViewsElement);
                fViewsElement.dispose();
                fViewsElement = null;
            }
            if (fOnDemandAnalysesElement != null) {
                removeChild(fOnDemandAnalysesElement);
                fOnDemandAnalysesElement.dispose();
                fOnDemandAnalysesElement = null;
            }
            if (fReportsElement != null) {
                removeChild(fReportsElement);
                fReportsElement.dispose();
                fReportsElement = null;
            }
            return;
        }
        if (fViewsElement == null) {
            /* Add the "Views" node */
            IFolder viewsNodeRes = root.getFolder(tracePath.append(TmfCoreViewsElement.PATH_ELEMENT));
            fViewsElement = new TmfCoreViewsElement(viewsNodeRes, this);
            addChild(fViewsElement);
        }
        fViewsElement.refreshChildren();

        if (fOnDemandAnalysesElement == null) {
            /* Add the "On-demand Analyses" node */
            IFolder analysesNodeRes = root.getFolder(tracePath.append(TmfCoreOnDemandAnalysesElement.PATH_ELEMENT));
            fOnDemandAnalysesElement = new TmfCoreOnDemandAnalysesElement(analysesNodeRes, this);
            addChild(fOnDemandAnalysesElement);
        }
        fOnDemandAnalysesElement.refreshChildren();

        if (fReportsElement == null) {
            /* Add the "Reports" node */
            IFolder reportsNodeRes = root.getFolder(tracePath.append(TmfCoreReportsElement.PATH_ELEMENT));
            fReportsElement = new TmfCoreReportsElement(reportsNodeRes, this);
            addChild(fReportsElement);
        }
        fReportsElement.refreshChildren();
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Get the child element "Views". There should always be one.
     *
     * @return The child element
     * @since 2.0
     */
    protected TmfCoreViewsElement getChildElementViews() {
        return fViewsElement;
    }

    /**
     * Get the child element "Reports".
     *
     * @return The Reports child element
     * @since 2.0
     */
    public TmfCoreReportsElement getChildElementReports() {
        return fReportsElement;
    }

    /**
     * Returns the trace type ID.
     *
     * @return trace type ID.
     */
    public String getTraceType() {
        return fTraceTypeId;
    }

    /**
     * Refreshes the trace type field by reading the trace type persistent
     * property of the resource.
     */
    public void refreshTraceType() {
        try {
            fTraceTypeId = TmfTraceType.getTraceTypeId(getResource());
        } catch (CoreException e) {
            Activator.logError(NLS.bind(Messages.TmfCommonProjectElement_ErrorRefreshingProperty, getName()), e);
        }
    }

    /**
     * Instantiate a <code>ITmfTrace</code> object based on the trace type and
     * the corresponding extension.
     *
     * @return the <code>ITmfTrace</code> or <code>null</code> for an error
     */
    public abstract ITmfTrace instantiateTrace();

    /**
     * Return the supplementary folder path for this element. The returned path
     * is relative to the project's supplementary folder.
     *
     * @return The supplementary folder path for this element
     */
    protected String getSupplementaryFolderPath() {
        return getElementPath() + getSuffix();
    }

    /**
     * Return the element path relative to its common element (traces folder,
     * experiments folder or experiment element).
     *
     * @return The element path
     */
    public @NonNull String getElementPath() {
        ITmfCoreProjectModelElement parent = getParent();
        while (!(parent instanceof TmfCoreTracesFolder || parent instanceof TmfCoreExperimentElement || parent instanceof TmfCoreExperimentFolder)) {
            parent = parent.getParent();
        }
        IPath path = getResource().getFullPath().makeRelativeTo(parent.getPath());
        return checkNotNull(path.toString());
    }

    /**
     * Return the element destination path relative to its common element (traces
     * folder, experiments folder or experiment element).
     *
     * @param destinationPath
     *            Full destination path
     *
     * @return The element destination path
     * @since 3.3
     */
    public @NonNull String getDestinationPathRelativeToParent(IPath destinationPath) {
        ITmfCoreProjectModelElement parent = getParent();
        while (!(parent instanceof TmfCoreTracesFolder || parent instanceof TmfCoreExperimentElement || parent instanceof TmfCoreExperimentFolder)) {
            parent = parent.getParent();
        }
        IPath path = destinationPath.makeRelativeTo(parent.getPath());
        return checkNotNull(path.toString());
    }

    /**
     * @return The suffix for the supplementary folder
     */
    protected String getSuffix() {
        return ""; //$NON-NLS-1$
    }

    /**
     * Returns a list of TmfTraceElements contained in project element.
     *
     * @return a list of TmfTraceElements, empty list if none
     */
    public List<TmfCoreTraceElement> getTraces() {
        return new ArrayList<>();
    }

    /**
     * Get the instantiated trace associated with this element.
     *
     * @return The instantiated trace or null if trace is not (yet) available
     */
    public ITmfTrace getTrace() {
        for (ITmfTrace trace : TmfTraceManager.getInstance().getOpenedTraces()) {
            if (getResource().equals(trace.getResource())) {
                return trace;
            }
        }
        return null;
    }

    /**
     * Close open editors associated with this experiment.
     */
    public void closeEditors() {
        // TODO hook in closing of traces
//        IFile file = getBookmarksFile();
//        FileEditorInput input = new FileEditorInput(file);
//        IWorkbench wb = PlatformUI.getWorkbench();
//        for (IWorkbenchWindow wbWindow : wb.getWorkbenchWindows()) {
//            for (IWorkbenchPage wbPage : wbWindow.getPages()) {
//                for (IEditorReference editorReference : wbPage.getEditorReferences()) {
//                    try {
//                        if (editorReference.getEditorInput().equals(input)) {
//                            wbPage.closeEditor(editorReference.getEditor(false), false);
//                        }
//                    } catch (PartInitException e) {
//                        Activator.getDefault().logError(NLS.bind(Messages.TmfCommonProjectElement_ErrorClosingEditor, getName()), e);
//                    }
//                }
//            }
//        }
    }

    /**
     * Get a friendly name for the type of element this common project element
     * is, to be displayed in UI messages.
     *
     * @return A string for the type of project element this object is, for
     *         example "trace" or "experiment"
     */
    public abstract String getTypeName();

    /**
     * Copy this model element
     *
     * @param newName
     *            The name of the new element
     * @param copySuppFiles
     *            Whether to copy supplementary files or not
     * @return the new Resource object
     */
    public IResource copy(final String newName, final boolean copySuppFiles) {
        return copy(newName, copySuppFiles, true);
    }

    /**
     * Copy this model element at the same place as this element
     * (ex./Traces/thisElementPath).
     *
     * @param newName
     *            The name of the new element
     * @param copySuppFiles
     *            Whether to copy supplementary files or not
     * @param copyAsLink
     *            Whether to copy as a link or not
     * @return the new Resource object
     * @since 3.3
     */
    public IResource copy(final String newName, final boolean copySuppFiles, final boolean copyAsLink) {
        final IPath newPath = getParent().getResource().getFullPath().addTrailingSeparator().append(newName);
        return copy(copySuppFiles, copyAsLink, newPath);
    }

    /**
     * Copy this model element to the destinationPath
     *
     * @param copySuppFiles
     *            Whether to copy supplementary files or not
     * @param copyAsLink
     *            Whether to copy as a link or not
     * @param destinationPath
     *            The path where the element will be copied
     * @return the new Resource object
     * @since 3.3
     */
    public IResource copy(final boolean copySuppFiles, final boolean copyAsLink, IPath destinationPath) {
        /* Copy supplementary files first, only if needed */
        if (copySuppFiles) {
            String newElementPath = getDestinationPathRelativeToParent(destinationPath);
            copySupplementaryFolder(newElementPath);
        }
        /* Copy the trace */
        try {
            int flags = IResource.FORCE;
            if (copyAsLink) {
                flags |= IResource.SHALLOW;
            }

            IResource trace = ResourceUtil.copyResource(getResource(), destinationPath, flags, null);

            // TODO
//            /* Delete any bookmarks file found in copied trace folder */
//            if (trace instanceof IFolder) {
//                IFolder folderTrace = (IFolder) trace;
//                for (IResource member : folderTrace.members()) {
//                    String traceTypeId = TmfTraceType.getTraceTypeId(member);
//                    if (ITmfEventsEditorConstants.TRACE_INPUT_TYPE_CONSTANTS.contains(traceTypeId)
//                            || ITmfEventsEditorConstants.EXPERIMENT_INPUT_TYPE_CONSTANTS.contains(traceTypeId)) {
//                        member.delete(true, null);
//                    }
//                }
//            }
            return trace;
        } catch (CoreException e) {
            Activator.logError("Error copying " + getName(), e); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Get the list of analysis elements
     *
     * @return Array of analysis elements
     */
    public List<@NonNull TmfCoreAnalysisElement> getAvailableAnalysis() {
        TmfCoreViewsElement viewsElement = getChildElementViews();
        if (viewsElement != null) {
            return viewsElement.getChildren().stream()
                    .filter(TmfCoreAnalysisElement.class::isInstance)
                    .map(TmfCoreAnalysisElement.class::cast)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * @since 3.0
     * @return list of children analysis elements
     */
    public List<TmfCoreAnalysisElement> getAvailableChildrenAnalyses() {
        return Collections.emptyList();
    }

    // ------------------------------------------------------------------------
    // Supplementary files operations
    // ------------------------------------------------------------------------

    /**
     * Deletes this element specific supplementary folder.
     */
    public void deleteSupplementaryFolder() {
        IFolder supplFolder = getTraceSupplementaryFolder(getSupplementaryFolderPath());
        try {
            deleteFolder(supplFolder);
        } catch (CoreException e) {
            Activator.logError("Error deleting supplementary folder " + supplFolder, e); //$NON-NLS-1$
        }
    }

    private static void deleteFolder(IFolder folder) throws CoreException {
        if (folder.exists()) {
            folder.delete(true, new NullProgressMonitor());
        }
        IContainer parent = folder.getParent();
        // delete empty folders up to the parent project
        if (parent instanceof IFolder && (!parent.exists() || parent.members().length == 0)) {
            deleteFolder((IFolder) parent);
        }
    }

    /**
     * Renames the element specific supplementary folder according to the new
     * element name or path.
     *
     * @param newElementPath
     *            The new element name or path
     */
    public void renameSupplementaryFolder(String newElementPath) {
        IFolder oldSupplFolder = getTraceSupplementaryFolder(getSupplementaryFolderPath());

        // Rename supplementary folder
        try {
            if (oldSupplFolder.exists()) {
                IFolder newSupplFolder = prepareTraceSupplementaryFolder(newElementPath + getSuffix(), false);
                oldSupplFolder.move(newSupplFolder.getFullPath(), true, new NullProgressMonitor());
            }
            deleteFolder(oldSupplFolder);
        } catch (CoreException e) {
            Activator.logError("Error renaming supplementary folder " + oldSupplFolder, e); //$NON-NLS-1$
        }
    }

    /**
     * Copies the element specific supplementary folder to the new element name
     * or path.
     *
     * @param newElementPath
     *            The new element name or path
     */
    public void copySupplementaryFolder(String newElementPath) {
        IFolder oldSupplFolder = getTraceSupplementaryFolder(getSupplementaryFolderPath());

        // copy supplementary folder
        if (oldSupplFolder.exists()) {
            try {
                IFolder newSupplFolder = prepareTraceSupplementaryFolder(newElementPath + getSuffix(), false);
                oldSupplFolder.copy(newSupplFolder.getFullPath(), true, new NullProgressMonitor());
                // Temporary fix for Bug 532677: IResource.copy() does not copy the hidden flag
                hidePropertiesFolder(newSupplFolder);
            } catch (CoreException e) {
                Activator.logError("Error copying supplementary folder " + oldSupplFolder, e); //$NON-NLS-1$
            }
        }
    }

    /**
     * Copies the element specific supplementary folder a new folder.
     *
     * @param destination
     *            The destination folder to copy to.
     */
    public void copySupplementaryFolder(IFolder destination) {
        IFolder oldSupplFolder = getTraceSupplementaryFolder(getSupplementaryFolderPath());

        // copy supplementary folder
        if (oldSupplFolder.exists()) {
            try {
                TraceUtils.createFolder((IFolder) destination.getParent(), new NullProgressMonitor());
                oldSupplFolder.copy(destination.getFullPath(), true, new NullProgressMonitor());
                // Temporary fix for Bug 532677: IResource.copy() does not copy the hidden flag
                hidePropertiesFolder(destination);
            } catch (CoreException e) {
                Activator.logError("Error copying supplementary folder " + oldSupplFolder, e); //$NON-NLS-1$
            }
        }
    }

    private static void hidePropertiesFolder(IFolder supplFolder) throws CoreException {
        IFolder propertiesFolder = supplFolder.getFolder(TmfCommonConstants.TRACE_PROPERTIES_FOLDER);
        if (propertiesFolder.exists()) {
            propertiesFolder.setHidden(true);
        }
    }

    /**
     * Refreshes the element specific supplementary folder information. It creates
     * the folder if not exists. It sets the persistence property of the trace
     * resource
     *
     * @param monitor
     *            the progress monitor
     * @since 4.0
     */
    public void refreshSupplementaryFolder(IProgressMonitor monitor) {
        SubMonitor subMonitor = SubMonitor.convert(monitor, 2);
        IFolder supplFolder = createSupplementaryFolder(subMonitor.split(1));
        try {
            supplFolder.refreshLocal(IResource.DEPTH_INFINITE, subMonitor.split(1));
        } catch (CoreException e) {
            Activator.logError("Error refreshing supplementary folder " + supplFolder, e); //$NON-NLS-1$
        }
    }

    /**
     * Refreshes the element specific supplementary folder information. It
     * creates the folder if not exists. It sets the persistence property of the
     * trace resource
     */
    public void refreshSupplementaryFolder() {
        refreshSupplementaryFolder(new NullProgressMonitor());
    }

    /**
     * Checks if supplementary resource exist or not.
     *
     * @return <code>true</code> if one or more files are under the element
     *         supplementary folder
     */
    public boolean hasSupplementaryResources() {
        IResource[] resources = getSupplementaryResources();
        return (resources.length > 0);
    }

    /**
     * Returns the supplementary resources under the trace supplementary folder.
     *
     * @return array of resources under the trace supplementary folder.
     */
    public IResource[] getSupplementaryResources() {
        IFolder supplFolder = getTraceSupplementaryFolder(getSupplementaryFolderPath());
        if (supplFolder.exists()) {
            try {
                return supplFolder.members();
            } catch (CoreException e) {
                Activator.logError("Error deleting supplementary folder " + supplFolder, e); //$NON-NLS-1$
            }
        }
        return new IResource[0];
    }

    /**
     * Deletes the given resources.
     *
     * @param resources
     *            array of resources to delete.
     */
    public void deleteSupplementaryResources(IResource[] resources) {

        for (int i = 0; i < resources.length; i++) {
            try {
                resources[i].delete(true, new NullProgressMonitor());
                // Needed to audit for privacy concerns
                TraceCompassLogUtils.traceInstant(LOGGER, Level.CONFIG, "deleteSupplementaryResources", resources[i].getFullPath().toOSString() ); //$NON-NLS-1$
            } catch (CoreException e) {
                Activator.logError("Error deleting supplementary resource " + resources[i], e); //$NON-NLS-1$
            }
        }
    }

    /**
     * Deletes all supplementary resources in the supplementary directory
     */
    public void deleteSupplementaryResources() {
        deleteSupplementaryResources(getSupplementaryResources());
    }

    /**
     * Returns the trace specific supplementary folder under the project's
     * supplementary folder. The folder and its parent folders will be created if
     * they don't exist.
     *
     * @param monitor
     *            the progress monitor
     * @return the trace specific supplementary folder
     * @since 4.0
     */
    public IFolder prepareSupplementaryFolder(IProgressMonitor monitor) {
        return prepareTraceSupplementaryFolder(getSupplementaryFolderPath(), true, monitor);
    }

    private IFolder createSupplementaryFolder(IProgressMonitor monitor) {
        IFolder supplFolder = prepareTraceSupplementaryFolder(getSupplementaryFolderPath(), true, monitor);

        try {
            getResource().setPersistentProperty(TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER, supplFolder.getLocation().toOSString());
        } catch (CoreException e) {
            Activator.logError("Error setting persistant property " + TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER, e); //$NON-NLS-1$
        }
        return supplFolder;
    }

}
