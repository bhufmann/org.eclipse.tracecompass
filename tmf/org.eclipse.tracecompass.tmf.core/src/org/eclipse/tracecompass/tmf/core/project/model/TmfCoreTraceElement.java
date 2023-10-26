/*******************************************************************************
 * Copyright (c) 2010, 2021 Ericsson, École Polytechnique de Montréal and others
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
 *   Bernd Hufmann - Added supplementary files handling
 *   Geneviève Bastien - Moved supplementary files handling to parent class,
 *                       added code to copy trace
 *   Patrick Tasse - Close editors to release resources
 *   Jean-Christian Kouame - added trace properties to be shown into
 *                           the properties view
 *   Geneviève Bastien - Moved trace type related methods to parent class
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.project.model;

import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.internal.util.ByteBufferTracker;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.io.ResourceUtil;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Implementation of trace model element representing a trace. It provides
 * methods to instantiate <code>ITmfTrace</code> and <code>ITmfEvent</code> as
 * well as editor ID from the trace type extension definition.
 *
 * @since 9.2
 * @author Francois Chouinard
 */
public class TmfCoreTraceElement extends TmfCoreCommonProjectElement {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    private ITmfTimestamp fStartTime = null;
    private ITmfTimestamp fEndTime = null;
    private boolean fPreDeleted = false;

    // ------------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------------
    /**
     * Constructor. Creates trace model element under the trace folder.
     *
     * @param name
     *            The name of trace
     * @param trace
     *            The trace resource.
     * @param parent
     *            The parent element (trace folder)
     */
    public TmfCoreTraceElement(String name, IResource trace, TmfCoreTraceFolder parent) {
        super(name, trace, parent);
    }

    /**
     * Constructor. Creates trace model element under the experiment folder.
     *
     * @param name
     *            The name of trace
     * @param trace
     *            The trace resource.
     * @param parent
     *            The parent element (experiment folder)
     */
    public TmfCoreTraceElement(String name, IResource trace, TmfCoreExperimentElement parent) {
        super(name, trace, parent);
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    /**
     * @since 2.0
     */
    @Override
    public String getLabelText() {
        if (getParent() instanceof TmfCoreExperimentElement) {
            return getElementPath();
        }
        return getName();
    }

    /**
     * Instantiate a <code>ITmfTrace</code> object based on the trace type and
     * the corresponding extension.
     *
     * @return the <code>ITmfTrace</code> or <code>null</code> for an error
     */
    @Override
    public ITmfTrace instantiateTrace() {
        try {

            // make sure that supplementary folder exists
            refreshSupplementaryFolder();

            String traceTypeId = getTraceType();
            if (traceTypeId != null) {
                return TmfTraceType.instantiateTrace(traceTypeId);
            }
        } catch (CoreException e) {
            Activator.logError("Error instantiating ITmfTrace object for trace " + getName(), e); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Instantiate a <code>ITmfEvent</code> object based on the trace type and
     * the corresponding extension.
     *
     * @return the <code>ITmfEvent</code> or <code>null</code> for an error
     */
    public ITmfEvent instantiateEvent() {
        try {
            String traceTypeId = getTraceType();
            if (traceTypeId != null) {
                return TmfTraceType.instantiateEvent(traceTypeId);
            }
        } catch (CoreException e) {
            Activator.logError("Error instantiating ITmfEvent object for trace " + getName(), e); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Returns the <code>TmfTraceElement</code> located under the
     * <code>TmfTracesFolder</code>.
     *
     * @return <code>this</code> if this element is under the
     *         <code>TmfTracesFolder</code> else the corresponding
     *         <code>TmfTraceElement</code> if this element is under
     *         <code>TmfExperimentElement</code>.
     */
    public TmfCoreTraceElement getElementUnderTraceFolder() {

        // If trace is under an experiment, return original trace from the traces folder
        if (getParent() instanceof TmfCoreExperimentElement) {
            ITmfCoreProjectModelElement parent = getProject().getTracesFolder();
            ITmfCoreProjectModelElement element = null;
            if (parent != null) {
                for (String segment : new Path(getElementPath()).segments()) {
                    element = parent.getChild(segment);
                    if (element == null) {
                        return this;
                    }
                    parent = element;
                }
                if (element instanceof TmfCoreTraceElement) {
                    return (TmfCoreTraceElement) element;
                }
            }
        }
        return this;
    }

    @Override
    public String getTypeName() {
        return Messages.TmfTraceElement_TypeName;
    }

    /**
     * Copy this trace in the trace folder. No other parameters are mentioned so
     * the trace is copied in this element's project trace folder
     *
     * @param newName
     *            The new trace name
     * @return the new Resource object
     */
    public TmfCoreTraceElement copy(String newName) {
        TmfCoreTraceFolder folder = (TmfCoreTraceFolder) getParent();
        IResource res = super.copy(newName, false);
        for (TmfCoreTraceElement trace : folder.getTraces()) {
            if (trace.getResource().equals(res)) {
                return trace;
            }
        }
        return null;
    }

    /**
     * Close opened editors associated with this trace.
     */
    @Override
    public void closeEditors() {
        super.closeEditors();

        // Close experiments that contain the trace if open
        if (getParent() instanceof TmfCoreTraceFolder) {
            TmfCoreExperimentFolder experimentsFolder = getProject().getExperimentsFolder();
            if (experimentsFolder != null) {
                for (TmfCoreExperimentElement experiment : experimentsFolder.getExperiments()) {
                    for (TmfCoreTraceElement trace : experiment.getTraces()) {
                        if (trace.getElementPath().equals(getElementPath())) {
                            experiment.closeEditors();
                            break;
                        }
                    }
                }
            }
        } else if (getParent() instanceof TmfCoreExperimentElement) {
            TmfCoreExperimentElement experiment = (TmfCoreExperimentElement) getParent();
            experiment.closeEditors();
        }

        /*
         * We will be deleting a trace shortly. Invoke GC to release
         * MappedByteBuffer objects, which some trace types, like CTF, use.
         * (see Java bug JDK-4724038)
         */
        if (ByteBufferTracker.getAndReset()) {
            System.gc();
        }
    }

    /**
     * Delete the trace resource, remove it from experiments and delete its
     * supplementary files
     *
     * @param progressMonitor
     *            a progress monitor, or null if progress reporting is not
     *            desired
     *
     * @throws CoreException
     *             thrown when IResource.delete fails
     */
    public void delete(IProgressMonitor progressMonitor) throws CoreException {
        delete(progressMonitor, false);
    }

    /**
     * Delete the trace resource, and optionally remove it from experiments and
     * delete its supplementary files.
     *
     * @param progressMonitor
     *            a progress monitor, or null if progress reporting is not desired
     * @param overwriting
     *            if true, keep the trace in experiments and only delete non-hidden
     *            supplementary files (keeping the properties sub-folder), otherwise
     *            remove the trace from experiments and delete the supplementary
     *            folder completely
     *
     * @throws CoreException
     *             thrown when IResource.delete fails
     * @since 3.1
     */
    public void delete(IProgressMonitor progressMonitor, boolean overwriting) throws CoreException {
        delete(progressMonitor, overwriting, true);
    }

    /**
     * Delete the trace resource, and optionally remove it from experiments and
     * delete its supplementary files. Editors are first closed if requested.
     *
     * @param progressMonitor
     *            a progress monitor, or null if progress reporting is not desired
     * @param overwriting
     *            if true, keep the trace in experiments and only delete non-hidden
     *            supplementary files (keeping the properties sub-folder), otherwise
     *            remove the trace from experiments and delete the supplementary
     *            folder completely
     * @param closeEditors
     *            if true, editors associated with this trace are first closed
     *            before proceeding, otherwise it is the responsibility of the
     *            caller to first close editors before calling the method
     *
     * @throws CoreException
     *             thrown when IResource.delete fails
     * @since 4.0
     */
    public void delete(IProgressMonitor progressMonitor, boolean overwriting, boolean closeEditors) throws CoreException {
        // Close editors in UI Thread
        // TODO
//        if (closeEditors) {
//            Display.getDefault().syncExec(this::closeEditors);
//        }

        IResource resourceToDelete = getResource();
        if (resourceToDelete == null) {
            return;
        }

        SubMonitor subMon = SubMonitor.convert(progressMonitor, 2);

        // Do pre-delete cleanup
        preDelete(subMon.split(1), overwriting);

        // Finally, delete the trace resource
        ResourceUtil.deleteResource(resourceToDelete, subMon.split(1));
    }

    /*
     * Pre-delete cleanup
     *
     * This can also be called by the TmfProjectRegistry when it detects that the
     * trace resource has been deleted.
     */
    void preDelete(IProgressMonitor progressMonitor, boolean overwriting) throws CoreException {
        if (fPreDeleted) {
            return;
        }
        fPreDeleted = true;

        IResource resourceToDelete = getResource();
        if (resourceToDelete == null) {
            return;
        }
        IPath path = resourceToDelete.getLocation();
        if (path != null) {
            if (getParent() instanceof TmfCoreTraceFolder) {
                TmfCoreExperimentFolder experimentFolder = getProject().getExperimentsFolder();

                // Propagate the removal to experiments
                if (experimentFolder != null && !overwriting) {
                    for (TmfCoreExperimentElement experiment : experimentFolder.getExperiments()) {
                        List<TmfCoreTraceElement> toRemove = new LinkedList<>();
                        for (TmfCoreTraceElement trace : experiment.getTraces()) {
                            if (trace.getElementPath().equals(getElementPath())) {
                                toRemove.add(trace);
                            }
                        }
                        for (TmfCoreTraceElement child : toRemove) {
                            experiment.removeTrace(child, false);
                        }
                        if (!toRemove.isEmpty() && experiment.getTraces().isEmpty()) {
                            // If experiment becomes empty, delete it
                            experiment.deleteSupplementaryFolder();
                            experiment.getResource().delete(true, progressMonitor);
                        }
                    }
                }

                // Delete supplementary files
                if (overwriting) {
                    deleteSupplementaryResources();
                } else {
                    deleteSupplementaryFolder();
                }

            } else if (getParent() instanceof TmfCoreExperimentElement) {
                TmfCoreExperimentElement experimentElement = (TmfCoreExperimentElement) getParent();
                experimentElement.deleteSupplementaryResources();
            }
        }

        if (overwriting) {
            /*
             * Remove the trace from the model immediately to prevent preDelete from being
             * called again from TmfProjectRegistry.handleTraceDeleted() as it would remove
             * the overwritten trace from any experiments to which it belongs.
             */
            ((TmfCoreProjectModelElement) getParent()).removeChild(this);
        }
    }

    /**
     * Update the trace's start time
     *
     * @param startTime
     *            updated start time for this trace
     * @since 3.0
     */
    public void setStartTime(ITmfTimestamp startTime) {
        fStartTime = startTime;
    }

    /**
     * Getter for the trace start time
     *
     * @return the start time from the trace if available, or from self when
     *         read in advance from supplementary files or from fast trace read.
     *         Return null if completely unknown.
     * @since 3.0
     */
    public ITmfTimestamp getStartTime() {
        ITmfTrace trace = getTrace();
        if (trace != null) {
            setStartTime(trace.getStartTime());
        }
        return fStartTime;
    }

    /**
     * Update the trace's end time
     *
     * @param end
     *            updated end time for this trace
     * @since 3.0
     */
    public void setEndTime(@NonNull ITmfTimestamp end) {
        if (fEndTime == null || end.compareTo(fEndTime) > 0) {
            fEndTime = end;
        }
    }

    /**
     * Getter for the trace end time
     *
     * @return the end time from the trace if available, or from self when read
     *         in advance from supplementary files or from fast trace read.
     *         Return null if completely unknown.
     * @since 3.0
     */
    public ITmfTimestamp getEndTime() {
        ITmfTrace trace = getTrace();
        if (trace != null) {
            setEndTime(trace.getEndTime());
        }
        return fEndTime;
    }

    @Override
    public void deleteSupplementaryResources(IResource[] resources) {
        /* Invalidate the cached trace bounds */
        fStartTime = null;
        fEndTime = null;

        super.deleteSupplementaryResources(resources);
    }

    /**
     * Deletes all supplementary resources in the supplementary directory. Also
     * delete the supplementary resources of experiments that contain this trace.
     */
    @Override
    public void deleteSupplementaryResources() {
        super.deleteSupplementaryResources();

        // Propagate the deletion to experiments
        TmfCoreExperimentFolder experimentFolder = getProject().getExperimentsFolder();
        if (experimentFolder != null) {
            for (TmfCoreExperimentElement experiment : experimentFolder.getExperiments()) {
                for (TmfCoreTraceElement trace : experiment.getTraces()) {
                    if (trace.getElementPath().equals(getElementPath())) {
                        experiment.deleteSupplementaryResources();
                        break;
                    }
                }
            }
        }
    }
}
