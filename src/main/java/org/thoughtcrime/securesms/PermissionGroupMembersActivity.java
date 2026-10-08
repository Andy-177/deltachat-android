package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import com.b44t.messenger.DcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.contacts.ContactSelectionListItem;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme;
import org.thoughtcrime.securesms.util.ViewUtil;

/** Lets the user toggle which chat contacts are member of a chatroom permission group. */
public class PermissionGroupMembersActivity extends PassphraseRequiredActionBarActivity {

  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String GROUP_ID_EXTRA = "group_id";

  private DcContext dcContext;
  private int chatId;
  private int groupId;
  private int[] memberIds = new int[0];
  private boolean[] memberChecked = new boolean[0];
  private ListView listView;
  private BaseAdapter adapter;
  private Toolbar toolbar;

  @Override
  protected void onPreCreate() {
    dynamicTheme = new DynamicNoActionBarTheme();
    super.onPreCreate();
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    dcContext = DcHelper.getContext(this);
    chatId = getIntent().getIntExtra(CHAT_ID_EXTRA, 0);
    groupId = getIntent().getIntExtra(GROUP_ID_EXTRA, 0);
    setContentView(R.layout.chatroom_members_activity);
    this.toolbar = ViewUtil.findById(this, R.id.toolbar);
    setSupportActionBar(this.toolbar);
    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar != null) {
      supportActionBar.setDisplayHomeAsUpEnabled(true);
      supportActionBar.setTitle(R.string.permission_group_members);
    }

    listView = ViewUtil.findById(this, R.id.member_list);
    adapter = new MembersAdapter();
    listView.setAdapter(adapter);
    listView.setOnItemClickListener((parent, view, position, id) -> toggle(position));
    reload();
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return false;
  }

  private void reload() {
    memberIds = dcContext.getChatContacts(chatId);
    int[] inGroup = dcContext.getPermissionGroupMembers(chatId, groupId);
    memberChecked = new boolean[memberIds.length];
    for (int i = 0; i < memberIds.length; i++) {
      memberChecked[i] = contains(inGroup, memberIds[i]);
    }
    adapter.notifyDataSetChanged();
  }

  private void toggle(int position) {
    if (position < 0 || position >= memberIds.length) {
      return;
    }
    int contactId = memberIds[position];
    int result;
    if (memberChecked[position]) {
      result = dcContext.revokePermissionGroup(chatId, groupId, contactId);
    } else {
      result = dcContext.assignPermissionGroup(chatId, groupId, contactId);
    }
    if (result == 0) {
      Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
      reload();
      return;
    }
    memberChecked[position] = !memberChecked[position];
    adapter.notifyDataSetChanged();
  }

  private class MembersAdapter extends BaseAdapter {

    @Override
    public int getCount() {
      return memberIds.length;
    }

    @Override
    public Object getItem(int position) {
      return memberIds[position];
    }

    @Override
    public long getItemId(int position) {
      return memberIds[position];
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      ContactSelectionListItem view;
      if (convertView instanceof ContactSelectionListItem) {
        view = (ContactSelectionListItem) convertView;
      } else {
        view =
            (ContactSelectionListItem)
                getLayoutInflater().inflate(R.layout.contact_selection_list_item, parent, false);
      }
      view.setContact(
          GlideApp.with(PermissionGroupMembersActivity.this),
          dcContext.getContact(memberIds[position]),
          true);
      view.setChecked(memberChecked[position]);
      return view;
    }
  }

  private static boolean contains(int[] ids, int id) {
    for (int i : ids) {
      if (i == id) {
        return true;
      }
    }
    return false;
  }
}
