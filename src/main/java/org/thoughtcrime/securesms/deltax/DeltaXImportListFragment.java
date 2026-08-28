/*
 * Copy of org.thoughtcrime.securesms.ContactSelectionListFragment used by the
 * main page "add" button. Kept inside the deltax package so the plugin import
 * page can reuse the exact same UI while only offering a single "Import" row.
 */
package org.thoughtcrime.securesms.deltax;

import static org.thoughtcrime.securesms.util.ShareUtil.isRelayingMessageContent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.thoughtcrime.securesms.ProfileActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcContactsLoader;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.contacts.ContactSelectionListAdapter;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.contacts.NewContactActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;

public class DeltaXImportListFragment extends Fragment
    implements LoaderManager.LoaderCallbacks<DcContactsLoader.Ret>, DcEventCenter.DcEventDelegate {
  private static final String TAG = "DeltaXImportListFragment";

  public static final String MULTI_SELECT = "multi_select";
  public static final String SELECT_UNENCRYPTED_EXTRA = "select_unencrypted_extra";
  public static final String ALLOW_CREATION = "allow_creation";
  public static final String PRESELECTED_CONTACTS = "preselected_contacts";

  private DcContext dcContext;

  private Set<Integer> selectedContacts;
  private Set<Integer> deselectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private String cursorFilter;
  private RecyclerView recyclerView;
  private TextView emptyView;
  private ActionMode actionMode;
  private ActionMode.Callback actionModeCallback;
  private ActivityResultLauncher<Intent> newContactLauncher;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    dcContext = DcHelper.getContext(requireContext());
    newContactLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                int contactId =
                    result.getData().getIntExtra(NewContactActivity.CONTACT_ID_EXTRA, 0);
                if (contactId != 0) {
                  selectedContacts.add(contactId);
                  deselectedContacts.remove(contactId);
                }
                LoaderManager.getInstance(this)
                    .restartLoader(0, null, DeltaXImportListFragment.this);
              }
            });
  }

  @Override
  public void onDestroy() {
    DcHelper.getEventCenter(getActivity()).removeObservers(this);
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    this.getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    emptyView = ViewUtil.findById(view, android.R.id.empty);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(recyclerView, true, false, true, true);

    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
    actionModeCallback =
        new ActionMode.Callback() {
          @Override
          public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.contact_list, menu);
            menu.findItem(R.id.menu_delete_selected).setVisible(!isMulti());
            updateActionModeState(actionMode);
            return true;
          }

          @Override
          public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
            return false;
          }

          @Override
          public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.menu_select_all) {
              handleSelectAll();
              return true;
            } else if (itemId == R.id.menu_view_profile) {
              handleViewProfile();
              return true;
            } else if (itemId == R.id.menu_delete_selected) {
              handleDeleteSelected();
              return true;
            }
            return false;
          }

          @Override
          public void onDestroyActionMode(ActionMode actionMode) {
            DeltaXImportListFragment.this.actionMode = null;
            getContactSelectionListAdapter().resetActionModeSelection();
          }
        };

    DcHelper.getEventCenter(requireActivity())
        .addObserver(DcContext.DC_EVENT_CONTACTS_CHANGED, this);
    initializeCursor();

    return view;
  }

  private void handleSelectAll() {
    getContactSelectionListAdapter().selectAll();
    updateActionModeState(actionMode);
  }

  private void updateActionModeState(ActionMode actionMode) {
    int size = getContactSelectionListAdapter().getActionModeSelection().size();
    if (size == 0) {
      actionMode.finish();
    } else {
      actionMode.getMenu().findItem(R.id.menu_view_profile).setVisible(size == 1);
      actionMode.setTitle(String.valueOf(size));
    }
  }

  private void handleViewProfile() {
    DeltaXImportListAdapter adapter = getContactSelectionListAdapter();
    if (adapter.getActionModeSelection().size() == 1) {
      int contactId = adapter.getActionModeSelection().valueAt(0);

      Intent intent = new Intent(getContext(), ProfileActivity.class);
      intent.putExtra(ProfileActivity.CONTACT_ID_EXTRA, contactId);
      getContext().startActivity(intent);
    }
  }

  private void handleDeleteSelected() {
    AlertDialog dialog =
        new AlertDialog.Builder(getActivity())
            .setMessage(R.string.ask_delete_contacts)
            .setPositiveButton(
                R.string.delete,
                (d, i) -> {
                  DeltaXImportListAdapter adapter = getContactSelectionListAdapter();
                  final SparseIntArray actionModeSelection =
                      adapter.getActionModeSelection().clone();
                  new Thread(
                          () -> {
                            for (int index = 0; index < actionModeSelection.size(); index++) {
                              int contactId = actionModeSelection.valueAt(index);
                              dcContext.deleteContact(contactId);
                            }
                          })
                      .start();
                  adapter.resetActionModeSelection();
                  actionMode.finish();
                })
            .setNegativeButton(R.string.cancel, null)
            .show();
    Util.redPositiveButton(dialog);
  }

  private DeltaXImportListAdapter getContactSelectionListAdapter() {
    return (DeltaXImportListAdapter) recyclerView.getAdapter();
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  public @NonNull List<Integer> getSelectedContacts() {
    List<Integer> selected = new LinkedList<>();
    if (selectedContacts != null) {
      selected.addAll(selectedContacts);
    }

    return selected;
  }

  public @NonNull List<Integer> getDeselectedContacts() {
    List<Integer> deselected = new LinkedList<>();
    if (deselectedContacts != null) {
      deselected.addAll(deselectedContacts);
    }

    return deselected;
  }

  private boolean isMulti() {
    return getActivity().getIntent().getBooleanExtra(MULTI_SELECT, false);
  }

  private boolean isUnencrypted() {
    return getActivity().getIntent().getBooleanExtra(SELECT_UNENCRYPTED_EXTRA, false);
  }

  private void initializeCursor() {
    DeltaXImportListAdapter adapter =
        new DeltaXImportListAdapter(
            getActivity(), GlideApp.with(this), new ListClickListener(), isMulti(), true);
    selectedContacts = adapter.getSelectedContacts();
    deselectedContacts = new HashSet<>();
    ArrayList<Integer> preselectedContacts =
        getActivity().getIntent().getIntegerArrayListExtra(PRESELECTED_CONTACTS);
    if (preselectedContacts != null) {
      selectedContacts.addAll(preselectedContacts);
    }
    recyclerView.setAdapter(adapter);
  }

  public void setQueryFilter(String filter) {
    this.cursorFilter = filter;
    this.getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public Loader<DcContactsLoader.Ret> onCreateLoader(int id, Bundle args) {
    final boolean allowCreation = getActivity().getIntent().getBooleanExtra(ALLOW_CREATION, true);
    final boolean addCreateGroupLinks =
        allowCreation && !isRelayingMessageContent(getActivity()) && !isMulti();
    final boolean addScanQRLink = allowCreation && !isMulti();
    final boolean addCreateContactLink = allowCreation && isUnencrypted();

    final int listflags =
        DcContext.DC_GCL_ADD_SELF | (isUnencrypted() ? DcContext.DC_GCL_ADDRESS : 0);
    return new DcContactsLoader(
        getActivity(),
        listflags,
        " ",
        addCreateGroupLinks,
        addCreateContactLink,
        addScanQRLink,
        false);
  }

  @Override
  public void onLoadFinished(Loader<DcContactsLoader.Ret> loader, DcContactsLoader.Ret data) {
    DeltaXImportListAdapter adapter = (DeltaXImportListAdapter) recyclerView.getAdapter();
    adapter.changeData(new DcContactsLoader.Ret(new int[] {DcContact.DC_CONTACT_ID_NEW_GROUP}));
    if (emptyView != null) {
      emptyView.setVisibility(View.GONE);
    }
  }

  @Override
  public void onLoaderReset(Loader<DcContactsLoader.Ret> loader) {
    ((DeltaXImportListAdapter) recyclerView.getAdapter()).changeData(null);
  }

  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    @Override
    public void onItemClick(ContactSelectionListItem contact, boolean handleActionMode) {
      if (handleActionMode) {
        if (actionMode != null) {
          updateActionModeState(actionMode);
        }
        return;
      }
      int contactId = contact.getSpecialId();
      if (!isMulti() || !selectedContacts.contains(contactId)) {
        if (contactId == DcContact.DC_CONTACT_ID_NEW_CLASSIC_CONTACT) {
          Intent intent = new Intent(getContext(), NewContactActivity.class);
          if (dcContext.mayBeValidAddr(cursorFilter)) {
            intent.putExtra(NewContactActivity.ADDR_EXTRA, cursorFilter);
          }
          if (isMulti()) {
            newContactLauncher.launch(intent);
          } else {
            requireContext().startActivity(intent);
          }
          return;
        }

        selectedContacts.add(contactId);
        deselectedContacts.remove(contactId);
        contact.setChecked(true);
        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactSelected(contactId);
        }
      } else {
        selectedContacts.remove(contactId);
        deselectedContacts.add(contactId);
        contact.setChecked(false);
        if (onContactSelectedListener != null) {
          onContactSelectedListener.onContactDeselected(contactId);
        }
      }
    }

    @Override
    public void onItemLongClick(ContactSelectionListItem view) {
      if (actionMode == null) {
        actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(actionModeCallback);
      } else {
        updateActionModeState(actionMode);
      }
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public interface OnContactSelectedListener {
    void onContactSelected(int contactId);

    void onContactDeselected(int contactId);
  }

  @Override
  public void handleEvent(@NonNull DcEvent event) {
    if (event.getId() == DcContext.DC_EVENT_CONTACTS_CHANGED) {
      getLoaderManager().restartLoader(0, null, DeltaXImportListFragment.this);
    }
  }
}
