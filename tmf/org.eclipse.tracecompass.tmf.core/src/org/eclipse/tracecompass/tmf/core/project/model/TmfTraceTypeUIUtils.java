/*******************************************************************************
 * Copyright (c) 2014, 2015 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Alexandre Montplaisir - Initial API and implementation
 *   Patrick Tasse - Add support for folder elements
 *   Bernd Hufmann - Update trace type auto-detection
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.tracecompass.tmf.core.TmfCommonConstants;

/**
 * Utils class for the UI-specific parts of @link {@link TmfTraceType}.
 *
 * @author Alexandre Montplaisir
 * @since 9.2
 */
public final class TmfTraceTypeUIUtils {

    private TmfTraceTypeUIUtils() {
    }

    /**
     * Set the trace type of a {@link TraceTypeHelper}. Should only be
     * used internally by this project.
     *
     * @param resource
     *            the resource to set
     * @param traceType
     *            the {@link TraceTypeHelper} to set the trace type to.
     * @return Status.OK_Status if successful, error is otherwise.
     * @throws CoreException
     *             An exception caused by accessing eclipse project items.
     */
    public static IStatus setTraceType(IResource resource, TraceTypeHelper traceType) throws CoreException {
        return setTraceType(resource, traceType, true);
    }

    /**
     * Set the trace type of a {@link TraceTypeHelper}. Should only be
     * used internally by this project.
     *
     * @param resource
     *            the resource to set
     * @param traceType
     *            the {@link TraceTypeHelper} to set the trace type to.
     * @param refresh
     *            Flag for refreshing the project
     * @return Status.OK_Status if successful, error is otherwise.
     * @throws CoreException
     *             An exception caused by accessing eclipse project items.
     */
    public static IStatus setTraceType(IResource resource, TraceTypeHelper traceType, boolean refresh) throws CoreException {
        String traceTypeId = traceType.getTraceTypeId();

        resource.setPersistentProperty(TmfCommonConstants.TRACETYPE, traceTypeId);

        TmfCoreProjectElement tmfProject = TmfCoreProjectRegistry.getProject(resource.getProject(), true);
        if (tmfProject == null) {
            return Status.CANCEL_STATUS;
        }
        TmfCoreTraceFolder tracesFolder = tmfProject.getTracesFolder();
        TmfCoreExperimentFolder experimentsFolder = tmfProject.getExperimentsFolder();
        if (tracesFolder != null) {
            if (tracesFolder.getPath().isPrefixOf(resource.getFullPath())) {
                String elementPath = resource.getFullPath().makeRelativeTo(tracesFolder.getPath()).toString();
                refreshTraceElement(tracesFolder.getTraces(), elementPath);
            }
        }
        if ((tracesFolder == null) || (experimentsFolder != null)) {
            if (experimentsFolder != null) {
                if (resource.getParent().equals(experimentsFolder.getResource())) {
                    /* The trace type to set is for an experiment */
                    for (TmfCoreExperimentElement experimentElement : experimentsFolder.getExperiments()) {
                        if (resource.equals(experimentElement.getResource())) {
                            experimentElement.refreshTraceType();
                            break;
                        }
                    }
                } else {
                    for (TmfCoreExperimentElement experimentElement : experimentsFolder.getExperiments()) {
                        if (experimentElement.getPath().isPrefixOf(resource.getFullPath())) {
                            String elementPath = resource.getFullPath().makeRelativeTo(experimentElement.getPath()).toString();
                            refreshTraceElement(experimentElement.getTraces(), elementPath);
                            break;
                        }
                    }
                }
            }
        }

        if (refresh) {
            tmfProject.refresh();
        }
        return Status.OK_STATUS;
    }

    private static void refreshTraceElement(List<TmfCoreTraceElement> traceElements, String elementPath) {
        for (TmfCoreTraceElement traceElement : traceElements) {
            if (traceElement.getElementPath().equals(elementPath)) {
                traceElement.refreshTraceType();
                break;
            }
        }
    }
}
