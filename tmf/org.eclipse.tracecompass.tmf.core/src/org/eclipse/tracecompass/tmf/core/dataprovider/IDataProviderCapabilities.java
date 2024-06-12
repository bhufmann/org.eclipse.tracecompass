package org.eclipse.tracecompass.tmf.core.dataprovider;

/**
 * @since 10.1
 */
public interface IDataProviderCapabilities {
    default boolean canCreateXY() { return false; }
    default boolean canCreateTimeGraph() { return false; }
    default boolean canCreateDataTree() { return false; }
    default boolean canCreateVirtualTable() { return false; }
    default boolean canDelete() { return false; }
}
