/*
 * Copy of org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter,
 * kept inside the deltax package so the import page can show a single
 * "Import" row that looks identical to the main page's "New Group" row.
 */
package org.thoughtcrime.securesms.deltax;

import android.widget.TextView;
import androidx.annotation.NonNull;
import com.b44t.messenger.DcContact;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.mms.GlideRequests;

public class DeltaXImportListAdapter extends ContactSelectionListAdapter {

  public DeltaXImportListAdapter(
      @NonNull android.content.Context context,
      @NonNull GlideRequests glideRequests,
      ItemClickListener clickListener,
      boolean multiSelect,
      boolean longPressSelect) {
    super(context, glideRequests, clickListener, multiSelect, longPressSelect);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int i) {
    super.onBindViewHolder(holder, i);
    if (holder.itemView instanceof ContactSelectionListItem) {
      ContactSelectionListItem item = (ContactSelectionListItem) holder.itemView;
      if (item.getSpecialId() == DcContact.DC_CONTACT_ID_NEW_GROUP) {
        TextView nameView = holder.itemView.findViewById(R.id.name);
        if (nameView != null) {
          nameView.setText(R.string.deltax_import_plugin);
        }
      }
    }
  }
}
