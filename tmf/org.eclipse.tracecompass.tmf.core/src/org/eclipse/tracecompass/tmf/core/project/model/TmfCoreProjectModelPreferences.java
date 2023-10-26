/**********************************************************************
 * Copyright (c) 2017 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 **********************************************************************/
package org.eclipse.tracecompass.tmf.core.project.model;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.NonNull;
import org.osgi.framework.Bundle;

/**
 * Singleton class to access the project model preferences of Trace Compass.
 *
 * This preference allows for customization of project model element label and
 * icon.
 *
 * @author Bernd Hufmann
 * @since 9.2
 */
public final class TmfCoreProjectModelPreferences {

    private static final @NonNull String DEFAULT_LABEL_NAME = "Trace Compass"; //$NON-NLS-1$

    private static String fProjectModelLabel = DEFAULT_LABEL_NAME;

    /**
     * Private constructor
     */
    private TmfCoreProjectModelPreferences() {
    }

    /**
     * Sets the preference of the project model element label
     *
     * @param bundleSymbolicName
     *            the symbolic name of the bundle defining the icon
     * @param label
     *            the label
     */
    public static synchronized void setProjectModelLabel(@NonNull String bundleSymbolicName, @NonNull String label) {
        Bundle bundle = Platform.getBundle(bundleSymbolicName);
        if (bundle == null) {
            return;
        }
        fProjectModelLabel = label;
    }

    /**
     * Get the preference value of the project model element label
     *
     * @return the label of the project model element
     */
    public static synchronized @NonNull String getProjectModelLabel() {
        String label = fProjectModelLabel;
        if (label == null) {
            return DEFAULT_LABEL_NAME;
        }
        return label;
    }
}
