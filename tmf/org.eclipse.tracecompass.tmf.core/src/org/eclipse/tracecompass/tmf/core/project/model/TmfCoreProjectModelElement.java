/*******************************************************************************
 * Copyright (c) 2010, 2018 Ericsson
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
 *   Bernd Hufmann - Added supplementary files/folder handling
 *   Patrick Tasse - Refactor resource change listener
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;

import com.google.common.collect.ImmutableList;

/**
 * The implementation of the base TMF project model element. It provides default implementation
 * of the <code>ITmfProjectModelElement</code> interface.
 * <p>
 * @since 9.2
 * @author Francois Chouinard
 */
public abstract class TmfCoreProjectModelElement implements ITmfCoreProjectModelElement {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    private final String fName;

    /** The project model element resource */
    private final IResource fResource;

    /** The project model resource location (URI) */
    private final URI fLocation;

    /** The project model path of a resource */
    private final IPath fPath;

    private final ITmfCoreProjectModelElement fParent;

    /** The list of children elements */
    private final @NonNull List<ITmfCoreProjectModelElement> fChildren;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * Creates a base project model element.
     *
     * @param name
     *            The name of the element.
     * @param resource
     *            The element resource.
     * @param parent
     *            The parent model element.
     */
    protected TmfCoreProjectModelElement(String name, IResource resource, ITmfCoreProjectModelElement parent) {
        fName = name;
        fResource = resource;
        fPath = resource.getFullPath();
        fLocation = new File(resource.getLocationURI()).toURI();
        fParent = parent;
        fChildren = new CopyOnWriteArrayList<>();
    }

    // ------------------------------------------------------------------------
    // ITmfProjectModelElement
    // ------------------------------------------------------------------------

    @Override
    public String getName() {
        return fName;
    }

    @Override
    public IResource getResource() {
        return fResource;
    }

    @Override
    public IPath getPath() {
        return fPath;
    }

    @Override
    public URI getLocation() {
        return fLocation;
    }

    @Override
    public TmfCoreProjectElement getProject() {
        return fParent.getProject();
    }

    @Override
    public ITmfCoreProjectModelElement getParent() {
        return fParent;
    }

    @Override
    public List<ITmfCoreProjectModelElement> getChildren() {
        return ImmutableList.copyOf(fChildren);
    }

    @Override
    public void refresh() {
        // make sure the model is updated in the current thread
        refreshChildren();

//        refreshViewer();
    }

    // ------------------------------------------------------------------------
    // Object
    // ------------------------------------------------------------------------

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fPath == null) ? 0 : fPath.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other.getClass().equals(this.getClass()))) {
            return false;
        }
        TmfCoreProjectModelElement element = (TmfCoreProjectModelElement) other;
        return element.fPath.equals(fPath);
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Refresh the children of this model element, adding new children and
     * removing dangling children as necessary. The remaining children should
     * also refresh their own children sub-tree.
     * <p>
     * The method implementation must be thread-safe.
     *
     * @since 2.0
     */
    protected abstract void refreshChildren();

     /**
     * Add a new child element to this element.
     *
     * @param child
     *            The child to add
     */
    protected void addChild(ITmfCoreProjectModelElement child) {
        fChildren.add(child);
    }

    /**
     * Remove an element from the current child elements.
     * <p>
     * Disposes the removed element. It should no longer be used.
     *
     * @param child
     *            The child to remove
     */
    protected void removeChild(ITmfCoreProjectModelElement child) {
        fChildren.remove(child);
        child.dispose();
    }

    /**
     * Returns the trace specific supplementary folder under the project's
     * supplementary folder. The returned folder and its parent folders may not
     * exist.
     *
     * @param supplFolderPath
     *            folder path relative to the project's supplementary folder
     * @return the trace specific supplementary folder
     */
    public IFolder getTraceSupplementaryFolder(String supplFolderPath) {
        TmfCoreProjectElement project = getProject();
        IFolder supplFolderParent = project.getSupplementaryFolder();
        return supplFolderParent.getFolder(supplFolderPath);
    }

    /**
     * Returns the trace specific supplementary folder under the project's
     * supplementary folder. Its parent folders will be created if they don't exist.
     * If createFolder is true, the returned folder will be created, otherwise it
     * may not exist.
     *
     * @param supplFolderPath
     *            folder path relative to the project's supplementary folder
     * @param createFolder
     *            if true, the returned folder will be created
     * @param progressMonitor
     *            the progress monitor
     * @return the trace specific supplementary folder
     * @since 4.0
     */
    public IFolder prepareTraceSupplementaryFolder(String supplFolderPath, boolean createFolder, IProgressMonitor progressMonitor) {
        SubMonitor subMonitor = SubMonitor.convert(progressMonitor);
        IFolder folder = getTraceSupplementaryFolder(supplFolderPath);
        IFolder propertiesFolder = folder.getFolder(TmfCommonConstants.TRACE_PROPERTIES_FOLDER);
        if ((createFolder && propertiesFolder.exists() && propertiesFolder.isHidden()) ||
                (!createFolder && folder.getParent().exists())) {
            return folder;
        }
        try {
            ICoreRunnable runnable = monitor -> {
                if (createFolder) {
                    TraceUtils.createFolder(propertiesFolder, monitor);
                    propertiesFolder.setHidden(true);
                } else {
                    TraceUtils.createFolder((IFolder) folder.getParent(), monitor);
                }
            };
            ResourcesPlugin.getWorkspace().run(runnable, folder.getProject(), IWorkspace.AVOID_UPDATE, subMonitor);
        } catch (CoreException e) {
            Activator.logError("Error creating supplementary folder " + folder.getFullPath(), e); //$NON-NLS-1$
        }
        return folder;
    }

    /**
     * Returns the trace specific supplementary folder under the project's
     * supplementary folder. Its parent folders will be created if they don't exist.
     * If createFolder is true, the returned folder will be created, otherwise it
     * may not exist.
     *
     * @param supplFolderPath
     *            folder path relative to the project's supplementary folder
     * @param createFolder
     *            if true, the returned folder will be created
     * @return the trace specific supplementary folder
     */
    public IFolder prepareTraceSupplementaryFolder(String supplFolderPath, boolean createFolder) {
        return prepareTraceSupplementaryFolder(supplFolderPath, createFolder, new NullProgressMonitor());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + '(' + getPath() + ')';
    }

}
