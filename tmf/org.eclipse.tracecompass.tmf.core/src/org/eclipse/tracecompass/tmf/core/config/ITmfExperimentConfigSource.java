/*******************************************************************************
 * Copyright (c) 2023 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.config;

import java.util.List;

import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfConfigurationException;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

/**
 * Interface to implement for applying a configuration to an experiment instance.
 *
 * @author Bernd Hufmann
 * @since 10.1
 */
public interface ITmfExperimentConfigSource extends ITmfConfigurationSource {
    List<IDataProviderDescriptor> createDataProvider(String configId, ITmfTrace trace) throws TmfConfigurationException;
    void removeDataProvider(IDataProviderDescriptor descriptor, ITmfTrace trace) throws TmfConfigurationException;
}
