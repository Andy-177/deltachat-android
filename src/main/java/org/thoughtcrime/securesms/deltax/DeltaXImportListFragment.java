/*
 * Fragment for the plugin import page. Lists the "Import" row plus the built-in plugins bundled in
 * assets/plugins, and supports filtering them by name through the search toolbar.
 */
package org.thoughtcrime.securesms.deltax;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.deltax.module.PluginInfo;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.ViewUtil;

public class DeltaXImportListFragment extends Fragment {

  public interface Listener {
    void onImportClicked();

    void onPluginClicked(PluginInfo plugin);
  }

  private Listener listener;
  private DeltaXImportListAdapter adapter;
  private List<PluginInfo> builtinPlugins = new ArrayList<>();
  private String currentFilter = "";

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    DeltaX deltaX = DeltaX.getInstance(requireContext());
    if (!deltaX.isInitialised()) {
      deltaX.init();
    }
    builtinPlugins = deltaX.getBuiltinPlugins();
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater, ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    RecyclerView recyclerView = ViewUtil.findById(view, R.id.recycler_view);

    ViewUtil.applyWindowInsets(recyclerView, true, false, true, true);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    adapter =
        new DeltaXImportListAdapter(
            GlideApp.with(this),
            builtinPlugins,
            entry -> {
              if (listener == null) return;
              if (entry == DeltaXImportListAdapter.IMPORT) {
                listener.onImportClicked();
              } else {
                listener.onPluginClicked((PluginInfo) entry);
              }
            });
    recyclerView.setAdapter(adapter);
    applyFilter(currentFilter);

    return view;
  }

  public void setQueryFilter(String filter) {
    this.currentFilter = filter == null ? "" : filter;
    if (adapter != null) {
      adapter.applyFilter(currentFilter);
    }
  }

  private void applyFilter(String filter) {
    if (adapter != null) {
      adapter.applyFilter(filter);
    }
  }
}
