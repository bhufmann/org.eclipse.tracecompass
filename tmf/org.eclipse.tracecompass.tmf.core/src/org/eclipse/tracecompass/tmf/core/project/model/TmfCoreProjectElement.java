/*******************************************************************************
 * Copyright (c) 2011, 2018 Ericsson
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
 *   Patrick Tasse - Refactor resource change listener
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;

/**
 * The implementation of TMF project model element.
 *
 * @since 9.2
 * @author Francois Chouinard
 */
public class TmfCoreProjectElement extends TmfCoreProjectModelElement {

    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------
    static final String TRACECOMPASS_PROJECT_FILE = ".tracecompass"; //$NON-NLS-1$

    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    private @Nullable TmfCoreTraceFolder fTraceFolder = null;
    private @Nullable TmfCoreExperimentFolder fExperimentFolder = null;
    private @Nullable IFolder fSupplFolder = null;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor.
     *
     * Creates the TMF project model element.
     *
     * @param name
     *            The name of the project.
     * @param project
     *            The project reference.
     * @param parent
     *            The parent element
     */
    public TmfCoreProjectElement(String name, IProject project, ITmfCoreProjectModelElement parent) {
        super(name, project, parent);
    }

    // ------------------------------------------------------------------------
    // TmfProjectModelElement
    // ------------------------------------------------------------------------

    @Override
    public IProject getResource() {
        return (IProject) super.getResource();
    }

    @Override
    public void addChild(ITmfCoreProjectModelElement child) {
        super.addChild(child);
        if (child instanceof TmfCoreTraceFolder) {
            fTraceFolder = (TmfCoreTraceFolder) child;
            return;
        }
        if (child instanceof TmfCoreExperimentFolder) {
            fExperimentFolder = (TmfCoreExperimentFolder) child;
            return;
        }
    }

    // ------------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------------

    /**
     * Returns the containing trace folder element.
     * @return the TMF trace folder element.
     */
    @Nullable
    public TmfCoreTraceFolder getTracesFolder() {
        return fTraceFolder;
    }

    /**
     * Returns the containing experiment folder element.
     * @return the TMF experiment folder element.
     */
    @Nullable public TmfCoreExperimentFolder getExperimentsFolder() {
        return fExperimentFolder;
    }

    /**
     * @return returns the supplementary folder
     *
     * @since 3.2
     */
    public IFolder getSupplementaryFolder() {
        return fSupplFolder;
    }

    // ------------------------------------------------------------------------
    // TmfProjectModelElement
    // ------------------------------------------------------------------------

    /**
     * @since 2.0
     */
    @Override
    protected synchronized void refreshChildren() {
        IProject project = getResource();

        // Get the children from the model
        Map<String, ITmfCoreProjectModelElement> childrenMap = new HashMap<>();
        for (ITmfCoreProjectModelElement element : getChildren()) {
            childrenMap.put(element.getResource().getName(), element);
        }

        TmfCoreProjectConfig config = getFolderStructure(project);

        // Add the model folder if the corresponding resource exists and is not
        // accounted for
        IFolder folder = config.getTracesFolder();
        if (folder != null && folder.exists()) {
            String name = folder.getName();
            ITmfCoreProjectModelElement element = childrenMap.get(name);
            if (element instanceof TmfCoreTracesFolder) {
                childrenMap.remove(name);
            } else {
                element = new TmfCoreTracesFolder(TmfCoreTracesFolder.TRACES_RESOURCE_NAME, folder, this);
                addChild(element);
            }
            ((TmfCoreTracesFolder) element).refreshChildren();
        }

        // Add the model folder if the corresponding resource exists and is not
        // accounted for
        folder = config.getExperimentsFolder();
        if (folder != null && folder.exists()) {
            String name = folder.getName();
            ITmfCoreProjectModelElement element = childrenMap.get(name);
            if (element instanceof TmfCoreExperimentFolder) {
                childrenMap.remove(name);
            } else {
                element = new TmfCoreExperimentFolder(TmfCoreExperimentFolder.EXPER_RESOURCE_NAME, folder, this);
                addChild(element);
            }
            ((TmfCoreExperimentFolder) element).refreshChildren();
        }

        fSupplFolder = config.getSupplementaryFolder();

        // Cleanup dangling children from the model
        for (ITmfCoreProjectModelElement danglingChild : childrenMap.values()) {
            removeChild(danglingChild);
        }
    }

    @Override
    public TmfCoreProjectElement getProject() {
        return this;
    }

    @Override
    public String getLabelText() {
        return TmfCoreProjectModelPreferences.getProjectModelLabel();
    }

    static void createFolderStructure(IContainer parent) throws CoreException {
        IFolder folder = parent.getFolder(new Path(TmfCoreTracesFolder.TRACES_RESOURCE_NAME));
        createFolder(folder);

        folder = parent.getFolder(new Path(TmfCoreExperimentFolder.EXPER_RESOURCE_NAME));
        createFolder(folder);

        // create folder for supplementary tracing files
        folder = parent.getFolder(new Path(TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER_NAME));
        createFolder(folder);
    }

    static IFolder createFolderStructure(IProject project, IProject shadowProject) throws CoreException {
        if (shadowProject != null) {
            createFolderStructure(shadowProject);
        }
        IFolder parentFolder = project.getFolder(TRACECOMPASS_PROJECT_FILE);
        createFolder(parentFolder);
        return parentFolder;
    }

    static TmfCoreProjectConfig getFolderStructure(IProject project) {
        TmfCoreProjectConfig.Builder builder = new TmfCoreProjectConfig.Builder();

        IFolder folder = project.getFolder(new Path(TmfCoreTracesFolder.TRACES_RESOURCE_NAME));
        builder.setTracesFolder(folder);

        folder = project.getFolder(new Path(TmfCoreExperimentFolder.EXPER_RESOURCE_NAME));
        builder.setExperimentsFolder(folder);

        // create folder for supplementary tracing files
        folder = project.getFolder(new Path(TmfCommonConstants.TRACE_SUPPLEMENTARY_FOLDER_NAME));
        builder.setSupplementaryFolder(folder);
        return builder.build();
    }

    private static void createFolder(IFolder folder) throws CoreException {
        if (!folder.exists()) {
            folder.create(true, true, null);
        }
    }

    static boolean showProjectRoot(IProject project) {
        IFolder traceCompassFile = project.getFolder(TRACECOMPASS_PROJECT_FILE);
        return traceCompassFile != null && traceCompassFile.exists();
    }
}
