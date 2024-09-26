/*******************************************************************************
 * Copyright (c) 2024 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.signal;

/**
 * Signal indicating configuration for configurable analysis was add or removed.
 *
 * The trace has been indexed up to the specified range.
 * @since 9.5
 *
 */
public class TmfConfigurableAnalysisUpdatedSignal extends TmfSignal {

    /**
     * Constructor
     *
     * @param source
     *            Object sending this signal
     */
    public TmfConfigurableAnalysisUpdatedSignal(Object source) {
        super(source);
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return "[TmfConfigurableAnalysisUpdatedSignal ()]";
    }

}
