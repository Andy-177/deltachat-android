package org.thoughtcrime.securesms;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.b44t.messenger.DcContext;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.ViewUtil;

/** Lets the user toggle which chat contacts are member of a chatroom permission group. */
public class PermissionGroupMembersActivity extends PassphraseRequiredActionBarActivity {

  public static final String CHAT_ID_EXTRA = "chat_id";
  public static final String GROUP_ID_EXTRA = "group_id";

  private DcContext dcContext;
  private int chatId;
  private int groupId;
  private int[] memberIds = new int[0];
  private ListView listView;

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    dcContext = DcHelper.getContext(this);
    chatId = getIntent().getIntExtra(CHAT_ID_EXTRA, 0);
    groupId = getIntent().getIntExtra(GROUP_ID_EXTRA, 0);
    setContentView(R.layout.chatroom_members_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.permission_group_members);

    listView = ViewUtil.findById(this, R.id.member_list);
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
    String[] names = new String[memberIds.length];
    for (int i = 0; i < memberIds.length; i++) {
      names[i] = dcContext.getContact(memberIds[i]).getDisplayName();
    }
    listView.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, names));
    for (int i = 0; i < memberIds.length; i++) {
      listView.setItemChecked(i, contains(inGroup, memberIds[i]));
    }
  }

  private void toggle(int position) {
    if (position < 0 || position >= memberIds.length) {
      return;
    }
    int contactId = memberIds[position];
    int result;
    if (listView.isItemChecked(position)) {
      result = dcContext.assignPermissionGroup(chatId, groupId, contactId);
    } else {
      result = dcContext.revokePermissionGroup(chatId, groupId, contactId);
    }
    if (result == 0) {
      Toast.makeText(this, R.string.error, Toast.LENGTH_LONG).show();
      reload();
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
