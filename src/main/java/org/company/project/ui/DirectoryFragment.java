package org.company.project.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.melnykov.fab.FloatingActionButton;
import com.squareup.otto.Bus;

import org.company.project.App;
import org.company.project.R;
import org.company.project.event.DirectoryItemSelectedEvent;
import org.company.project.event.EditIndividualEvent;
import org.company.project.ui.adapter.DirectoryAdapter;
import org.company.project.ui.loader.DirectoryListLoader;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import butterknife.InjectView;
import butterknife.OnClick;
import butterknife.OnItemClick;

public class DirectoryFragment extends BaseFragment implements LoaderManager.LoaderCallbacks<Cursor>, SearchView.OnQueryTextListener, ActionMode.Callback {
    public static final String TAG = App.createTag(DirectoryFragment.class);

    private static final String ARGS_DUAL_PANE = "DUAL_PANE";

    private static final String SAVE_STATE_LIST_POS = "LIST_POS";
    private static final String SAVE_STATE_LIST_POS_OFFSET = "LIST_POS_OFFSET";
    private static final String SAVE_STATE_LAST_SELECTED_POS = "LIST_LAST_SELECTED_POS";

    @Inject
    Bus bus;

    @InjectView(R.id.list)
    ListView listView;

    @InjectView(R.id.fab_new_item)
    FloatingActionButton newItemButton;

    private boolean dualPane = false;

    private CursorAdapter listAdapter;
    private int lastSelectedPosition = 0;
    private int currentPositionInList = 0;
    private int currentPositionInListOffset = 0;

    public static DirectoryFragment newInstance(boolean dualPane) {
        DirectoryFragment fragment = new DirectoryFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARGS_DUAL_PANE, dualPane);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            dualPane = getArguments().getBoolean(ARGS_DUAL_PANE, false);
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.directory_fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        App.injectFragment(this);
        setHasOptionsMenu(true);

        if (savedInstanceState != null) {
            currentPositionInList = savedInstanceState.getInt(SAVE_STATE_LIST_POS);
            currentPositionInListOffset = savedInstanceState.getInt(SAVE_STATE_LIST_POS_OFFSET);
            lastSelectedPosition = savedInstanceState.getInt(SAVE_STATE_LAST_SELECTED_POS);
        }

        listView.setChoiceMode(dualPane ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);

        // setup the right edge border
        listView.setBackgroundResource(dualPane ? R.drawable.listview_dual_background : 0);

        newItemButton.attachToListView(listView);

        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putInt(SAVE_STATE_LIST_POS, listView.getFirstVisiblePosition());
        View v = listView.getChildAt(0);
        int top = v == null ? 0 : v.getTop();
        outState.putInt(SAVE_STATE_LIST_POS_OFFSET, top);
        outState.putInt(SAVE_STATE_LAST_SELECTED_POS, lastSelectedPosition);
    }

    @Override
    public void onCreateOptionsMenu(@Nonnull Menu menu, @Nonnull MenuInflater inflater) {
        inflater.inflate(R.menu.directory_menu, menu);

        MenuItem searchMenuItem = menu.findItem(R.id.menu_item_search);
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
        searchView.setQueryHint(getString(R.string.menu_search_hint));
        searchView.setOnQueryTextListener(this);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onResume() {
        super.onResume();
        getLoaderManager().restartLoader(0, null, this);
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        Log.i(TAG, "Search Submit: " + s);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        Log.i(TAG, "Search Change: " + s);
        return true;
    }

    @Override
    public boolean onCreateActionMode(@Nonnull ActionMode actionMode, Menu menu) {
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.directory_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        // destroy action mode
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new DirectoryListLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (listAdapter == null) {
            listAdapter = new DirectoryAdapter(getActivity(), data);
            listView.setAdapter(listAdapter);
        } else {
            listAdapter.changeCursor(data);
        }

        if (dualPane) {
            selectPosition(lastSelectedPosition, -1, false);
        }

        listView.setSelectionFromTop(currentPositionInList, currentPositionInListOffset);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        if (listAdapter != null) {
            listAdapter.changeCursor(null);
        }
    }

    @OnItemClick(R.id.list)
    public void onListItemClick(int position, long id) {
        selectPosition(position, id, true);
    }

    private void selectPosition(int position, long id, boolean storeCurrentPos) {
        // Only if we're showing both fragments should the item be "highlighted"
        if (dualPane) {
            listView.setItemChecked(position, true);
            id = getCheckedItemId(id);
        }

        if (storeCurrentPos) {
            currentPositionInList = listView.getFirstVisiblePosition();
            View v = listView.getChildAt(0);
            currentPositionInListOffset = v == null ? 0 : v.getTop();
            lastSelectedPosition = position;
        }

        postListItemSelected(id);
    }

    private long getCheckedItemId(long defaultId) {
        long[] ids = listView.getCheckedItemIds();
        if (ids.length > 0) {
            return ids[0];
        }

        return defaultId;
    }

    private void postListItemSelected(long id) {
        Message msg = new Message();
        msg.what = MSG_SELECTED_ITEM;
        Bundle bundle = new Bundle();
        bundle.putLong("ID", id);
        msg.setData(bundle);
        handler.sendMessage(msg);
    }

    private static final int MSG_SELECTED_ITEM = 1;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_SELECTED_ITEM) {
                Bundle bundle = msg.getData();
                bus.post(new DirectoryItemSelectedEvent(bundle.getLong("ID")));
            }
        }
    };

    @OnClick(R.id.fab_new_item)
    public void onNewItemClick() {
        bus.post(new EditIndividualEvent());
    }
}
