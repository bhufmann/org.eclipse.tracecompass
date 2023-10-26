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
 *   Patrick Tasse - Add support for folder elements
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;

/**
 * Implementation of trace folder model element representing a trace folder in
 * the project.
 * <p>
 * @since 9.2
 * @author Francois Chouinard
 */
public class TmfCoreTraceFolder extends TmfCoreProjectModelElement {

    // ------------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------------

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor. Creates folder model element under the project.
     *
     * @param name
     *            The name of trace folder.
     * @param resource
     *            The folder resource.
     * @param parent
     *            The parent element (project).
     */
    public TmfCoreTraceFolder(String name, IFolder resource, TmfCoreProjectElement parent) {
        super(name, resource, parent);
    }

    /**
     * Constructor. Creates folder model element under another folder.
     *
     * @param name
     *            The name of trace folder.
     * @param resource
     *            The folder resource.
     * @param parent
     *            The parent element (folder).
     */
    public TmfCoreTraceFolder(String name, IFolder resource, TmfCoreTraceFolder parent) {
        super(name, resource, parent);
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
                String name = resource.getName();
                boolean isFolder = resource instanceof IFolder &&
                        (TmfTraceType.getTraceTypeId(resource) == null);
                ITmfCoreProjectModelElement element = childrenMap.get(name);
                if (isFolder && !(element instanceof TmfCoreTraceFolder) && !(element instanceof TmfCoreTraceElement)) {
                    if (TmfTraceType.isDirectoryTrace(resource.getLocationURI().getPath())) {
                        element = new TmfCoreTraceElement(name, resource, this);
                    } else {
                        element = new TmfCoreTraceFolder(name, (IFolder) resource, this);
                    }
                    addChild(element);
                } else if (!isFolder && !(element instanceof TmfCoreTraceElement)) {
                    element = new TmfCoreTraceElement(name, resource, this);
                    addChild(element);
                } else {
                    childrenMap.remove(name);
                }
                if (element != null) {
                    ((TmfCoreProjectModelElement) element).refreshChildren();
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
        int nbTraces = getTraces().size();
        if (nbTraces > 0) {
            return (getName() + " [" + nbTraces + ']'); //$NON-NLS-1$
        }
        return getName();
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * Returns a list of trace elements under the folder element, recursively.
     * @return list of trace model elements
     */
    public List<@NonNull TmfCoreTraceElement> getTraces() {
        List<ITmfCoreProjectModelElement> children = getChildren();
        List<@NonNull TmfCoreTraceElement> traces = new ArrayList<>();
        for (ITmfCoreProjectModelElement child : children) {
            if (child instanceof TmfCoreTraceElement) {
                traces.add((TmfCoreTraceElement) child);
            } else if (child instanceof TmfCoreTraceFolder) {
                traces.addAll(((TmfCoreTraceFolder) child).getTraces());
            }
        }
        return traces;
    }

    /**
     * Gets the traces elements under this folder containing the given resources
     *
     * @param resources
     *            resources to search for
     * @return list of trace elements
     * @since 2.0
     */
    public @NonNull List<TmfCoreTraceElement> getTraceElements(@NonNull List<IResource> resources) {
        return resources.stream()
                .flatMap(resource -> getTraces().stream()
                        .filter(traceElement -> traceElement.getResource().equals(resource)))
                .distinct()
                .collect(Collectors.toList());
    }

}
