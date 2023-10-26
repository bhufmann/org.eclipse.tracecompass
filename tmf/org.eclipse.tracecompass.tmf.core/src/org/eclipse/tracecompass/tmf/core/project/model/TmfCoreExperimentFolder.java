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
 *   Patrick Tasse - Add support for folder elements
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Implementation of model element representing the unique "Experiments" folder
 * in the project.
 * <p>
 *
 * @author Francois Chouinard
 * @since 9.2
 */
public class TmfCoreExperimentFolder extends TmfCoreProjectModelElement {

    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------

    static final String EXPER_RESOURCE_NAME = "Experiments"; //$NON-NLS-1$

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------

    /**
     * Constructor.
     * Creates a TmfExperimentFolder model element.
     * @param name The name of the folder
     * @param folder The folder reference
     * @param parent The parent (project element)
     */
    public TmfCoreExperimentFolder(String name, IFolder folder, TmfCoreProjectElement parent) {
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
        IFolder folder = getResource();

        // Get the children from the model
        Map<String, ITmfCoreProjectModelElement> childrenMap = new HashMap<>();
        for (ITmfCoreProjectModelElement element : getChildren()) {
            childrenMap.put(element.getResource().getName(), element);
        }

        try {
            IResource[] members = folder.members();
            for (IResource resource : members) {
                if (resource instanceof IFolder) {
                    IFolder expFolder = (IFolder) resource;
                    String name = resource.getName();
                    ITmfCoreProjectModelElement element = childrenMap.get(name);
                    if (element instanceof TmfCoreExperimentElement) {
                        childrenMap.remove(name);
                    } else {
                        element = new TmfCoreExperimentElement(name, expFolder, this);
                        addChild(element);
                    }
                    ((TmfCoreExperimentElement) element).refreshChildren();
                }
            }
        } catch (CoreException e) {
        }

        // Cleanup dangling children from the model
        for (ITmfCoreProjectModelElement danglingChild : childrenMap.values()) {
            removeChild(danglingChild);
        }
    }

    /**
     * @since 2.0
     */
    @Override
    public String getLabelText() {
        return getName() + " [" + getChildren().size() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Returns a list of experiment model elements under the experiments folder.
     * @return list of experiment model elements
     */
    public List<TmfCoreExperimentElement> getExperiments() {
        List<ITmfCoreProjectModelElement> children = getChildren();
        List<TmfCoreExperimentElement> traces = new ArrayList<>();
        for (ITmfCoreProjectModelElement child : children) {
            if (child instanceof TmfCoreExperimentElement) {
                traces.add((TmfCoreExperimentElement) child);
            }
        }
        return traces;
    }

    /**
     * Finds the experiment element for a given resource
     *
     * @param resource
     *            the resource to search for
     * @return the experiment element if found else null
     * @since 2.0
     */
    public @Nullable TmfCoreExperimentElement getExperiment(@NonNull IResource resource) {
        String name = resource.getName();
        if (name != null) {
            return getExperiment(name);
        }
        return null;
    }

    /**
     * Finds the experiment element for a given name
     *
     * @param name
     *            the name of experiment to search for
     * @return the experiment element if found else null
     * @since 2.0
     */
    public @Nullable TmfCoreExperimentElement getExperiment(@NonNull String name) {
        return getExperiments().stream()
                .filter(Objects::nonNull)
                .filter(experiment -> experiment.getName().equals(name))
                .findFirst()
                .orElse(null);
    }


    /**
     * Add an experiment element for the specified experiment resource. If an
     * experiment already exists for the specified resource, it is returned.
     *
     * @param resource
     *            the experiment resource
     * @return the experiment element, or null if this experiment folder is not the
     *         parent of the specified resource
     * @since 3.3
     */
    public synchronized TmfCoreExperimentElement addExperiment(@NonNull IFolder resource) {
        if (!resource.getParent().equals(getResource())) {
            return null;
        }
        /* If an experiment element already exists for this resource, return it */
        TmfCoreExperimentElement experiment = getExperiment(resource);
        if (experiment != null) {
            return experiment;
        }
        /* Create a new experiment element and add it as a child, then return it */
        String name = resource.getName();
        experiment = new TmfCoreExperimentElement(name, resource, this);
        addChild(experiment);
        return experiment;
    }
}
