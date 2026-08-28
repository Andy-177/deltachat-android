/*
 * Adapter backing the plugin import page. It renders the "Import" row (identical to the main page's
 * "New Group" row) followed by the built-in plugins bundled in assets/plugins. Clicking a row is
 * reported back through {@link SelectionCallback}.
 */
package org.thoughtcrime.securesms.deltax;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.DcContact;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.deltax.module.PluginInfo;
import org.thoughtcrime.securesms.mms.GlideRequests;

public class DeltaXImportListAdapter
    extends RecyclerView.Adapter<DeltaXImportListAdapter.RowHolder> {

  /** Sentinel value representing the "Import" action row at the top of the list. */
  public static final Object IMPORT = new Object();

  public interface SelectionCallback {
    void onEntryClicked(Object entry);
  }

  private final GlideRequests glideRequests;
  private final List<PluginInfo> allPlugins;
  private final SelectionCallback callback;
  private final List<Object> items = new ArrayList<>();

  public DeltaXImportListAdapter(
      GlideRequests glideRequests, List<PluginInfo> allPlugins, SelectionCallback callback) {
    this.glideRequests = glideRequests;
    this.allPlugins = allPlugins;
    this.callback = callback;
  }

  public void applyFilter(String filter) {
    String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
    items.clear();
    items.add(IMPORT);
    for (PluginInfo plugin : allPlugins) {
      if (f.isEmpty()) {
        items.add(plugin);
        continue;
      }
      String name =
          plugin.manifest.name == null ? "" : plugin.manifest.name.toLowerCase(Locale.ROOT);
      String author =
          plugin.manifest.author == null ? "" : plugin.manifest.author.toLowerCase(Locale.ROOT);
      if (name.contains(f) || author.contains(f)) {
        items.add(plugin);
      }
    }
    notifyDataSetChanged();
  }

  @Override
  public int getItemCount() {
    return items.size();
  }

  @Override
  public int getItemViewType(int position) {
    return items.get(position) == IMPORT ? 0 : 1;
  }

  @NonNull
  @Override
  public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    ContactSelectionListItem view =
        (ContactSelectionListItem)
            LayoutInflater.from(parent.getContext())
                .inflate(R.layout.contact_selection_list_item, parent, false);
    return new RowHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull RowHolder holder, int position) {
    Object entry = items.get(position);
    ContactSelectionListItem view = holder.item;
    if (entry == IMPORT) {
      view.set(
          glideRequests,
          DcContact.DC_CONTACT_ID_NEW_GROUP,
          null,
          view.getContext().getString(R.string.deltax_import_plugin),
          null,
          null,
          false,
          true);
    } else {
      PluginInfo plugin = (PluginInfo) entry;
      String subtitle =
          (plugin.manifest.author == null ? "" : plugin.manifest.author)
              + " • "
              + (plugin.manifest.version == null ? "" : plugin.manifest.version);
      view.set(
          glideRequests,
          DcContact.DC_CONTACT_ID_NEW_GROUP,
          null,
          plugin.manifest.name,
          subtitle,
          plugin.manifest.description,
          false,
          true);
      view.setPluginIcon(PluginIcons.getIcon(view.getContext(), plugin));
    }
    holder.item.setOnClickListener(v -> callback.onEntryClicked(entry));
  }

  static class RowHolder extends RecyclerView.ViewHolder {
    final ContactSelectionListItem item;

    RowHolder(ContactSelectionListItem itemView) {
      super(itemView);
      this.item = itemView;
    }
  }
}
