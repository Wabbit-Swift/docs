package com.sismics.docs.activity;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.androidquery.util.AQUtility;
import com.sismics.docs.R;
import com.sismics.docs.adapter.TagListAdapter;
import com.sismics.docs.event.SearchEvent;
import com.sismics.docs.listener.JsonHttpResponseHandler;
import com.sismics.docs.model.application.ApplicationContext;
import com.sismics.docs.provider.RecentSuggestionsProvider;
import com.sismics.docs.resource.TagResource;
import com.sismics.docs.resource.UserResource;
import com.sismics.docs.util.PreferenceUtil;

import org.apache.http.Header;
import org.json.JSONObject;

import de.greenrobot.event.EventBus;

/**
 * Main activity.
 * 
 * @author bgamard
 */
public class MainActivity extends ActionBarActivity {
    
    private ActionBarDrawerToggle drawerToggle;
    private MenuItem searchItem;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(final Bundle args) {
        super.onCreate(args);

        // Check if logged in
        if (!ApplicationContext.getInstance().isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Setup the activity
        setContentView(R.layout.main_activity);

        // Enable ActionBar app icon to behave as action to toggle nav drawer
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the sliding drawer and the action bar app icon
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.string.drawer_open, R.string.drawer_close);
        drawerLayout.setDrawerListener(drawerToggle);

        // Fill the drawer user info
        JSONObject userInfo = ApplicationContext.getInstance().getUserInfo();
        TextView usernameTextView = (TextView) findViewById(R.id.usernameTextView);
        usernameTextView.setText(userInfo.optString("username"));
        TextView emailTextView = (TextView) findViewById(R.id.emailTextView);
        emailTextView.setText(userInfo.optString("email"));

        // Get tag list to fill the drawer
        final ListView tagListView = (ListView) findViewById(R.id.tagListView);
        final View tagProgressView = findViewById(R.id.tagProgressView);
        final TextView tagEmptyView = (TextView) findViewById(R.id.tagEmptyView);
        tagListView.setEmptyView(tagProgressView);
        JSONObject cacheTags = PreferenceUtil.getCachedJson(this, PreferenceUtil.PREF_CACHED_TAGS_JSON);
        if (cacheTags != null) {
            tagListView.setAdapter(new TagListAdapter(cacheTags.optJSONArray("stats")));
        }
        TagResource.stats(this, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                PreferenceUtil.setCachedJson(MainActivity.this, PreferenceUtil.PREF_CACHED_TAGS_JSON, response);
                tagListView.setAdapter(new TagListAdapter(response.optJSONArray("stats")));
                tagProgressView.setVisibility(View.GONE);
                tagListView.setEmptyView(tagEmptyView);
            }

            @Override
            public void onAllFailure(int statusCode, Header[] headers, byte[] responseBytes, Throwable throwable) {
                tagEmptyView.setText(R.string.error_loading_tags);
                tagProgressView.setVisibility(View.GONE);
                tagListView.setEmptyView(tagEmptyView);
            }
        });

        // Click on a tag
        tagListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TagListAdapter adapter = (TagListAdapter) tagListView.getAdapter();
                if (adapter == null) return;
                JSONObject tag = adapter.getItem(position);
                if (tag == null) return;
                searchQuery("tag:" + tag.optString("name"));
            }
        });

        // Click on All documents
        View allDocumentsLayout = findViewById(R.id.allDocumentsLayout);
        allDocumentsLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchQuery(null);
            }
        });

        // Click on Shared documents
        View sharedDocumentsLayout = findViewById(R.id.sharedDocumentsLayout);
        sharedDocumentsLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchQuery("shared:yes");
            }
        });

        handleIntent(getIntent());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                UserResource.logout(getApplicationContext(), new JsonHttpResponseHandler() {
                    @Override
                    public void onFinish() {
                        // Force logout in all cases, so the user is not stuck in case of network error
                        ApplicationContext.getInstance().setUserInfo(getApplicationContext(), null);
                        startActivity(new Intent(MainActivity.this, LoginActivity.class));
                        finish();
                    }
                });
                return true;

            case R.id.settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;

            case android.R.id.home:
                // The action bar home/up action should open or close the drawer.
                // ActionBarDrawerToggle will take care of this.
                if (drawerToggle.onOptionsItemSelected(item)) {
                    return true;
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggle
        drawerToggle.onConfigurationChanged(newConfig);
    }
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity, menu);

        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                EventBus.getDefault().post(new SearchEvent(null));
                return false;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    /**
     * Handle the incoming intent.
     *
     * @param intent Intent
     */
    private void handleIntent(Intent intent) {
        // Intent is consumed
        setIntent(null);

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Perform a search query
            String query = intent.getStringExtra(SearchManager.QUERY);

            // Collapse the SearchView
            if (searchItem != null) {
                searchItem.collapseActionView();
            }

            // Save the query
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, RecentSuggestionsProvider.AUTHORITY, RecentSuggestionsProvider.MODE);
            suggestions.saveRecentQuery(query, null);

            EventBus.getDefault().post(new SearchEvent(query));
        }
    }

    /**
     * Perform a search query.
     *
     * @param query Query
     */
    private void searchQuery(String query) {
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQuery(query, true);
        searchView.setIconified(query == null);
        searchView.clearFocus();
        drawerLayout.closeDrawers();
    }

    @Override
    protected void onDestroy() {
        if(isTaskRoot()) {
            int cacheSizeMb = PreferenceUtil.getIntegerPreference(this, PreferenceUtil.PREF_CACHE_SIZE, 10);
            AQUtility.cleanCacheAsync(this, cacheSizeMb * 1000000, cacheSizeMb * 1000000);
        }
        super.onDestroy();
    }
}