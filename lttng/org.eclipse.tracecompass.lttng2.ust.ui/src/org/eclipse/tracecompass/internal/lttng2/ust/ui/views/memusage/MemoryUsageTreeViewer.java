package org.eclipse.tracecompass.internal.lttng2.ust.ui.views.memusage;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.lttng2.ust.core.analysis.memory.MemoryUsageTreeModel;
import org.eclipse.tracecompass.lttng2.ust.core.analysis.memory.UstMemoryUsageDataProvider;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderManager;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.AbstractSelectTreeViewer;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeColumnDataProvider;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.ITmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeColumnData;
import org.eclipse.tracecompass.tmf.ui.viewers.tree.TmfTreeViewerEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.dialogs.TriStateFilteredCheckboxTree;

/**
 * Tree viewer to select which process to display in the UST memory usage
 * chart.
 *
 * @author Loic Prieur-Drevon
 */
public class MemoryUsageTreeViewer extends AbstractSelectTreeViewer {

    private class MemoryLabelProvider extends TreeLabelProvider {

        @Override
        public String getColumnText(Object element, int columnIndex) {
            MemoryUsageEntry obj = (MemoryUsageEntry) element;
            if (columnIndex == 0) {
                return Integer.toString(obj.getModel().getTid());
            } else if (columnIndex == 1) {
                return obj.getName();
            }
            return null;
        }

        @Override
        public Image getColumnImage(Object element, int columnIndex) {
            if (columnIndex == 2 && element instanceof MemoryUsageEntry && isChecked(element)) {
                String name = Integer.toString(((MemoryUsageEntry) element).getModel().getTid());
                return getLegendImage(name);
            }
            return null;
        }
    }

    public MemoryUsageTreeViewer(Composite parent, TriStateFilteredCheckboxTree checkboxTree) {
        super(parent, checkboxTree, 2);
        setLabelProvider(new MemoryLabelProvider());
    }

    @Override
    protected ITmfTreeColumnDataProvider getColumnDataProvider() {
        return new ITmfTreeColumnDataProvider() {

            @Override
            public List<TmfTreeColumnData> getColumnData() {
                /* All columns are sortable */
                List<TmfTreeColumnData> columns = new ArrayList<>();
                TmfTreeColumnData column = new TmfTreeColumnData(Messages.MemoryUsageTree_ColumnProcess);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        MemoryUsageEntry n1 = (MemoryUsageEntry) e1;
                        MemoryUsageEntry n2 = (MemoryUsageEntry) e2;

                        return Integer.compare(n1.getModel().getTid(), n2.getModel().getTid());
                    }
                });
                columns.add(column);
                column = new TmfTreeColumnData(Messages.MemoryUsageTree_ColumnName);
                column.setComparator(new ViewerComparator() {
                    @Override
                    public int compare(Viewer viewer, Object e1, Object e2) {
                        MemoryUsageEntry n1 = (MemoryUsageEntry) e1;
                        MemoryUsageEntry n2 = (MemoryUsageEntry) e2;

                        return n1.getName().compareTo(n2.getName());
                    }
                });
                columns.add(column);
                column = new TmfTreeColumnData(Messages.MemoryUsageTree_Legend);
                columns.add(column);
                return columns;
            }
        };
    }

    @Override
    protected ITmfTreeViewerEntry updateElements(long start, long end, boolean isSelection) {
        ITmfTrace trace = getTrace();
        if (isSelection || (start == end) || trace == null) {
            return null;
        }

        UstMemoryUsageDataProvider provider = DataProviderManager.getInstance().getDataProvider(trace,
                UstMemoryUsageDataProvider.ID, UstMemoryUsageDataProvider.class);
        if (provider == null) {
            return null;
        }
        TmfTreeViewerEntry root = new TmfTreeViewerEntry(""); //$NON-NLS-1$
        List<ITmfTreeViewerEntry> entryList = root.getChildren();

        TmfModelResponse<@NonNull List<@NonNull MemoryUsageTreeModel>> response = provider.fetchTree(new TimeQueryFilter(start, end, 2), null);
        List<@NonNull MemoryUsageTreeModel> model = response.getModel();
        if (model != null) {
            for (MemoryUsageTreeModel k : model) {
                entryList.add(new MemoryUsageEntry(k));
            }
        }
        return root;
    }

    @Override
    protected void initializeDataSource() {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return;
        }
        DataProviderManager.getInstance().getDataProvider(trace,
                UstMemoryUsageDataProvider.ID, UstMemoryUsageDataProvider.class);
    }

}