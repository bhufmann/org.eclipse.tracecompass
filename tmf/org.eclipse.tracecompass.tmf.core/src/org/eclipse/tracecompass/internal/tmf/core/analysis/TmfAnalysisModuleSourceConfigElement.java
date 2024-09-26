/*******************************************************************************
 * Copyright (c) 2013, 2014 École Polytechnique de Montréal
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
 *******************************************************************************/

package org.eclipse.tracecompass.internal.tmf.core.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.internal.tmf.core.Activator;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleHelper;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModuleSource;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisModuleHelperConfigElement;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisOutputManager;
import org.eclipse.tracecompass.tmf.core.config.ITmfConfiguration;
import org.eclipse.tracecompass.tmf.core.config.TmfJsonConfiguration;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfConfigurationException;
import org.eclipse.tracecompass.tmf.core.signal.TmfConfigurableAnalysisUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.osgi.framework.Bundle;

/**
 * Utility class for accessing TMF analysis module extensions from the
 * platform's extensions registry.
 *
 * @author Geneviève Bastien
 */
public final class TmfAnalysisModuleSourceConfigElement implements IAnalysisModuleSource {

    /** Extension point ID */
    public static final String TMF_ANALYSIS_TYPE_ID = "org.eclipse.linuxtools.tmf.core.analysis"; //$NON-NLS-1$

    /** Extension point element 'module' */
    public static final String MODULE_ELEM = "module"; //$NON-NLS-1$

    /** Extension point element 'parameter' */
    public static final String PARAMETER_ELEM = "parameter"; //$NON-NLS-1$

    /** Extension point attribute 'ID' */
    public static final String ID_ATTR = "id"; //$NON-NLS-1$

    /** Extension point attribute 'name' */
    public static final String NAME_ATTR = "name"; //$NON-NLS-1$

    /** Extension point attribute 'analysis_module' */
    public static final String ANALYSIS_MODULE_ATTR = "analysis_module"; //$NON-NLS-1$

    /** Extension point attribute 'automatic' */
    public static final String AUTOMATIC_ATTR = "automatic"; //$NON-NLS-1$

    /** Extension point attribute 'applies_experiment' */
    public static final String APPLIES_EXP_ATTR = "applies_experiment"; //$NON-NLS-1$

    /** Extension point attribute 'icon' */
    public static final String ICON_ATTR = "icon"; //$NON-NLS-1$

    /** Extension point attribute 'default_value' */
    public static final String DEFAULT_VALUE_ATTR = "default_value"; //$NON-NLS-1$

    /** Extension point element 'tracetype' */
    public static final String TRACETYPE_ELEM = "tracetype"; //$NON-NLS-1$

    /** Extension point attribute 'class' */
    public static final String CLASS_ATTR = "class"; //$NON-NLS-1$

    /** Extension point attribute 'applies' */
    public static final String APPLIES_ATTR = "applies"; //$NON-NLS-1$

    /** Extension point attribute to outputs (e.g. views) */
    public static final String OUTPUT_ATTR = "output"; //$NON-NLS-1$

    /** Extension point attribute to hide default outputs (e.g. views) */
    public static final String HIDE_OUTPUT_ELEM = "hideOutput"; //$NON-NLS-1$

    /** Extension point attribute for root directory with configuration files */
    public static final String CONFIG_ROOT_ELEM = "config_root"; //$NON-NLS-1$

    /**
     * The mapping of available analysis ID to their corresponding helper
     */
    private final List<IAnalysisModuleHelper> fAnalysisHelpers = new ArrayList<>();
    private final List<IAnalysisModuleHelper> fConfigurableAnalysisHelpers = new ArrayList<>();

    /**
     * Retrieves all configuration elements from the platform extension registry
     * for the analysis module extension.
     *
     * @return an array of analysis module configuration elements
     */
    public static IConfigurationElement[] getTypeElements() {
        IConfigurationElement[] elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor(TMF_ANALYSIS_TYPE_ID);
        List<IConfigurationElement> typeElements = new LinkedList<>();
        for (IConfigurationElement element : elements) {
            if (element.getName().equals(MODULE_ELEM)) {
                typeElements.add(element);
            }
        }
        return typeElements.toArray(new @NonNull IConfigurationElement[typeElements.size()]);
    }

    /**
     * Constructor
     */
    public TmfAnalysisModuleSourceConfigElement() {
        TmfSignalManager.register(this);
        populateAnalysisList();
    }

    @Override
    public Iterable<IAnalysisModuleHelper> getAnalysisModules() {
        synchronized (fConfigurableAnalysisHelpers) {
            List<IAnalysisModuleHelper> result = new ArrayList<>();
            result.addAll(fAnalysisHelpers);
            result.addAll(fConfigurableAnalysisHelpers);
            return result;
        }
    }

    private void populateAnalysisList() {
        if (fAnalysisHelpers.isEmpty()) {
            // Populate the analysis module list
            IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(TMF_ANALYSIS_TYPE_ID);
            for (IConfigurationElement ce : config) {
                String elementName = ce.getName();
                if (elementName.equals(TmfAnalysisModuleSourceConfigElement.HIDE_OUTPUT_ELEM)) {
                    TmfAnalysisOutputManager.getInstance().loadExclusion(ce);
                }
            }
            if (config != null) {
                populateAnalysisList(config, true);
            }
        }
    }

    private void populateAnalysisList(IConfigurationElement[] config, boolean all) {
        List<IAnalysisModuleHelper> configurableAnalysisHelpers = new ArrayList<>();
        for (IConfigurationElement ce : config) {
            String elementName = ce.getName();
            if (elementName.equals(TmfAnalysisModuleSourceConfigElement.MODULE_ELEM)) {
                String rootDir = ce.getAttribute(TmfAnalysisModuleSourceConfigElement.CONFIG_ROOT_ELEM);
                Bundle bundle = TmfAnalysisModuleHelperConfigElement.getBundle(ce);
                if (rootDir != null) {
                    IPath path = Activator.getDefault()
                            .getStateLocation()
                            .removeLastSegments(1)
                            .append(bundle.getSymbolicName())
                            .append(rootDir);
                    File folder = path.toFile();
                    if (folder.exists()) {
                        final File[] files = folder.listFiles();
                        for (File file : files) {
                            try {
                                @SuppressWarnings("null")
                                ITmfConfiguration tmfConfig = TmfJsonConfiguration.fromJsonFile(file);
                                configurableAnalysisHelpers.add(new TmfAnalysisModuleHelperConfigElement(ce, tmfConfig));
                            } catch (TmfConfigurationException e) {
                                Activator.logError("Can't read configuration from file", e); //$NON-NLS-1$
                            }
                        }
                    }
                } else {
                    if (all) {
                        fAnalysisHelpers.add(new TmfAnalysisModuleHelperConfigElement(ce));
                    }
                }
            }
        }
        synchronized (fConfigurableAnalysisHelpers) {
            fConfigurableAnalysisHelpers.clear();
            fConfigurableAnalysisHelpers.addAll(configurableAnalysisHelpers);
        }
    }

    /**
     * Signal handler to refresh configurable analysis references
     * @param signal
     *            the signal to handle
     */
    @TmfSignalHandler
    public void configurableAnalysisUpdated(final TmfConfigurableAnalysisUpdatedSignal signal) {
        IConfigurationElement[] config = Platform.getExtensionRegistry().getConfigurationElementsFor(TMF_ANALYSIS_TYPE_ID);
        if (config != null) {
            populateAnalysisList(config, true);
        }
    }

}
