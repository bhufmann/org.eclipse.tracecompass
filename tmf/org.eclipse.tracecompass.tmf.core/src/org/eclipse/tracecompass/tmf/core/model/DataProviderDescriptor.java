/*******************************************************************************
 * Copyright (c) 2019 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.model;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.config.ITmfConfiguration;
import org.eclipse.tracecompass.tmf.core.config.ITmfConfigurationSourceType;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderCapabilities;
import org.eclipse.tracecompass.tmf.core.dataprovider.IDataProviderDescriptor;

/**
 * Data Provider description, used to list the available providers for a trace
 * without triggering the analysis or creating the providers. Supplies
 * information such as the extension point ID, type of provider and help text.
 *
 * @author Loic Prieur-Drevon
 * @author Bernd Hufmann
 * @since 8.2
 */
public class DataProviderDescriptor implements IDataProviderDescriptor {

    private final String fId;
    private final String fName;
    private final String fDescription;
    private final ProviderType fType;
    private @Nullable final IDataProviderCapabilities fCapabilities;
    private @Nullable final ITmfConfigurationSourceType fTimeGraphCreatorType;
    private @Nullable final ITmfConfiguration fTimeGraphCreatorConfiguration;
    /**
     * Constructor
     *
     * @param bulider
     *            the builder object to create the descriptor
     */
    private DataProviderDescriptor(Builder builder) {
        fId = builder.fId;
        fName = builder.fName;
        fDescription = builder.fDescription;
        fType = Objects.requireNonNull(builder.fType);
        fCapabilities = builder.fCapabilities;
        fTimeGraphCreatorType = builder.fTimeGraphCreatorType;
        fTimeGraphCreatorConfiguration = builder.fTimeGraphCreatorConfiguration;
    }

    @Override
    public String getName() {
        return fName;
    }

    @Override
    public String getId() {
        return fId;
    }

    @Override
    public ProviderType getType() {
        return fType;
    }

    @Override
    public String getDescription() {
        return fDescription;
    }

    @Override
    public @Nullable IDataProviderCapabilities getCapabilities() {
        return fCapabilities;
    }

    @Override
    public @Nullable ITmfConfigurationSourceType getTimeGraphCreatorType() {
        return fTimeGraphCreatorType;
    }

    @Override
    public @Nullable ITmfConfiguration getTimeGraphCreatorConfiguration() {
        return fTimeGraphCreatorConfiguration;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return getClass().getSimpleName() + " [fName=" + getName()
                + ", fDescription=" + getDescription() + ", fType=" + getType()
                +  ", fId=" + getId()
                +  "fCapabilities=" + getCapabilities()
                + "]";
    }

    @Override
    public boolean equals(@Nullable Object arg0) {
        if (!(arg0 instanceof DataProviderDescriptor)) {
            return false;
        }
        DataProviderDescriptor other = (DataProviderDescriptor) arg0;
        return Objects.equals(fName, other.fName) && Objects.equals(fId, other.fId)
                && Objects.equals(fType, other.fType) && Objects.equals(fDescription, other.fDescription)
                && Objects.equals(fCapabilities, other.getCapabilities()) &&
                Objects.equals(fTimeGraphCreatorType, other.getTimeGraphCreatorType()) &&
                Objects.equals(fTimeGraphCreatorConfiguration, other.getTimeGraphCreatorConfiguration());
    }

    @Override
    public int hashCode() {
        return Objects.hash(fName, fId, fType, fDescription, fCapabilities, fTimeGraphCreatorType, fTimeGraphCreatorConfiguration);
    }

    /**
     * A builder class to build instances implementing interface {@link IDataProviderDescriptor}
     */
    public static class Builder {
        private String fId = ""; //$NON-NLS-1$
        private String fName = ""; //$NON-NLS-1$
        private String fDescription = ""; //$NON-NLS-1$
        private @Nullable IDataProviderCapabilities fCapabilities = null;
        private @Nullable ProviderType fType = null;
        private @Nullable ITmfConfigurationSourceType fTimeGraphCreatorType;
        private @Nullable ITmfConfiguration fTimeGraphCreatorConfiguration;

        /**
         * Constructor
         */
        public Builder() {
            // Empty constructor
        }

        /**
         * Sets the data provider ID
         *
         * @param id
         *            the ID of the data provider
         * @return the builder instance.
         */
        public Builder setId(String id) {
            fId = id;
            return this;
        }

        /**
         * Sets the name of the data provider
         *
         * @param name
         *            the name to set
         * @return the builder instance.
         */
        public Builder setName(String name) {
            fName = name;
            return this;
        }

        /**
         * Sets the description of the data provider
         *
         * @param description
         *            the description text to set
         * @return the builder instance.
         */
        public Builder setDescription(String description) {
            fDescription = description;
            return this;
        }

        /**
         * Sets the data provider type
         *
         * @param type
         *            the data provider type to set
         * @return the builder instance.
         */
        public Builder setProviderType(ProviderType type) {
            fType = type;
            return this;
        }

        /**
         * Set data provider source type for creating time graphs
         *
         * @param type
         *            the configuration source type
         * @return the builder instance.
         * @since 9.3
         */
        public Builder setTimeGraphCreatorType(ITmfConfigurationSourceType type) {
            fTimeGraphCreatorType = type;
            return this;
        }

        /**
         * Set configuration for creating time graphs
         *
         * @param config
         *            the timegraph config
         * @return the builder instance.
         * @since 9.3
         */
        public Builder setTimeGraphConfiguration(ITmfConfiguration config) {
            fTimeGraphCreatorConfiguration = config;
            return this;
        }

        /**
         * Set data provider capabilities
         *
         * @param capabilities
         *            the capabilities to set
         * @return the builder instance.
         * @since 9.3
         */
        public Builder setCapabilities(IDataProviderCapabilities capabilities) {
            fCapabilities = capabilities;
            return this;
        }

        /**
         * The method to construct an instance of
         * {@link IDataProviderDescriptor}
         *
         * @return a {@link IDataProviderDescriptor} instance
         */
        public IDataProviderDescriptor build() {
            if (fType == null) {
                throw new IllegalStateException("Data provider type not set"); //$NON-NLS-1$
            }
            if (fId.isEmpty()) {
                throw new IllegalStateException("Empty data provider ID"); //$NON-NLS-1$
            }
            return new DataProviderDescriptor(this);
        }
    }
}
