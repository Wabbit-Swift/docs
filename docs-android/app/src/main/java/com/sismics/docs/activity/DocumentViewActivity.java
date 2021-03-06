package com.sismics.docs.activity;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.sismics.docs.R;
import com.sismics.docs.adapter.FilePagerAdapter;
import com.sismics.docs.event.DocumentDeleteEvent;
import com.sismics.docs.event.DocumentEditEvent;
import com.sismics.docs.event.DocumentFullscreenEvent;
import com.sismics.docs.event.FileAddEvent;
import com.sismics.docs.event.FileDeleteEvent;
import com.sismics.docs.fragment.DocShareFragment;
import com.sismics.docs.listener.JsonHttpResponseHandler;
import com.sismics.docs.model.application.ApplicationContext;
import com.sismics.docs.resource.DocumentResource;
import com.sismics.docs.resource.FileResource;
import com.sismics.docs.service.FileUploadService;
import com.sismics.docs.util.PreferenceUtil;
import com.sismics.docs.util.TagUtil;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * Document activity.
 * 
 * @author bgamard
 */
public class DocumentViewActivity extends ActionBarActivity {
    /**
     * Request code of adding file.
     */
    public static final int REQUEST_CODE_ADD_FILE = 1;

    /**
     * Request code of editing document.
     */
    public static final int REQUEST_CODE_EDIT_DOCUMENT = 2;

    /**
     * File view pager.
     */
    ViewPager fileViewPager;

    /**
     * File pager adapter.
     */
    FilePagerAdapter filePagerAdapter;

    /**
     * Document displayed.
     */
    JSONObject document;

    @Override
    protected void onCreate(final Bundle args) {
        super.onCreate(args);

        // Check if logged in
        if (!ApplicationContext.getInstance().isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Handle activity context
        if (getIntent() == null) {
            finish();
            return;
        }

        // Parse input document
        String documentJson = getIntent().getStringExtra("document");
        if (documentJson == null) {
            finish();
            return;
        }

        try {
            document = new JSONObject(documentJson);
        } catch (JSONException e) {
            finish();
            return;
        }

        // Setup the activity
        setContentView(R.layout.document_view_activity);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // Fill the view
        refreshDocument(document);

        EventBus.getDefault().register(this);
    }

    /**
     * Refresh the displayed document.
     *
     * @param document Document in JSON format
     */
    private void refreshDocument(JSONObject document) {
        this.document = document;

        String title = document.optString("title");
        String date = DateFormat.getDateFormat(this).format(new Date(document.optLong("create_date")));
        String description = document.optString("description");
        boolean shared = document.optBoolean("shared");
        String language = document.optString("language");
        JSONArray tags = document.optJSONArray("tags");

        // Setup the title
        setTitle(title);
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        TextView titleTextView = (TextView) toolbar.getChildAt(1);
        if (titleTextView != null) {
            titleTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            titleTextView.setMarqueeRepeatLimit(-1);
            titleTextView.setFocusable(true);
            titleTextView.setFocusableInTouchMode(true);
        }

        // Fill the layout
        TextView createdDateTextView = (TextView) findViewById(R.id.createdDateTextView);
        createdDateTextView.setText(date);

        TextView descriptionTextView = (TextView) findViewById(R.id.descriptionTextView);
        if (description == null || description.isEmpty()) {
            descriptionTextView.setVisibility(View.GONE);
        } else {
            descriptionTextView.setVisibility(View.VISIBLE);
            descriptionTextView.setText(description);
        }

        TextView tagTextView = (TextView) findViewById(R.id.tagTextView);
        if (tags.length() == 0) {
            tagTextView.setVisibility(View.GONE);
        } else {
            tagTextView.setVisibility(View.VISIBLE);
            tagTextView.setText(TagUtil.buildSpannable(tags));
        }

        ImageView languageImageView = (ImageView) findViewById(R.id.languageImageView);
        languageImageView.setImageResource(getResources().getIdentifier(language, "drawable", getPackageName()));

        ImageView sharedImageView = (ImageView) findViewById(R.id.sharedImageView);
        sharedImageView.setVisibility(shared ? View.VISIBLE : View.GONE);

        // Grab the attached files
        updateFiles();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.document_view_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.download_file:
                downloadCurrentFile();
                return true;

            case R.id.download_document:
                downloadZip();
                return true;

            case R.id.share:
                DialogFragment dialog = DocShareFragment.newInstance(document.optString("id"));
                dialog.show(getSupportFragmentManager(), "DocShareFragment");
                return true;

            case R.id.upload_file:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                        .setType("*/*")
                        .putExtra("android.intent.extra.ALLOW_MULTIPLE", true)
                        .addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, getText(R.string.upload_from)), REQUEST_CODE_ADD_FILE);
                return true;

            case R.id.edit:
                intent = new Intent(this, DocumentEditActivity.class);
                intent.putExtra("document", document.toString());
                startActivityForResult(intent, REQUEST_CODE_EDIT_DOCUMENT);
                return true;

            case R.id.delete_file:
                deleteCurrentFile();
                return true;

            case R.id.delete_document:
                deleteDocument();
                return true;

            case android.R.id.home:
                finish();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Download the current displayed file.
     */
    private void downloadCurrentFile() {
        if (fileViewPager == null || filePagerAdapter == null) return;

        JSONObject file = filePagerAdapter.getObjectAt(fileViewPager.getCurrentItem());
        if (file == null) return;

        // Build the destination filename
        String mimeType = file.optString("mimetype");
        int position = fileViewPager.getCurrentItem();
        if (mimeType == null || !mimeType.contains("/")) return;
        String ext = mimeType.split("/")[1];
        String fileName = getTitle() + "-" + position + "." + ext;

        // Download the file
        String fileUrl = PreferenceUtil.getServerUrl(this) + "/api/file/" + file.optString("id") + "/data";
        downloadFile(fileUrl, fileName, getTitle().toString(), getString(R.string.downloading_file, position + 1));
    }

    private void deleteCurrentFile() {
        if (fileViewPager == null || filePagerAdapter == null) return;

        final JSONObject file = filePagerAdapter.getObjectAt(fileViewPager.getCurrentItem());
        if (file == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.delete_file_title)
                .setMessage(R.string.delete_file_message)
                .setCancelable(true)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Dismiss the confirmation dialog
                        dialog.dismiss();

                        // Show a progress dialog while deleting
                        final ProgressDialog progressDialog = ProgressDialog.show(DocumentViewActivity.this,
                                getString(R.string.please_wait),
                                getString(R.string.file_deleting_message), true, true,
                                new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        FileResource.cancel(DocumentViewActivity.this);
                                    }
                                });

                        // Actual delete server call
                        final String fileId = file.optString("id");
                        FileResource.delete(DocumentViewActivity.this, fileId, new JsonHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                                EventBus.getDefault().post(new FileDeleteEvent(fileId));
                            }

                            @Override
                            public void onAllFailure(int statusCode, Header[] headers, byte[] responseBytes, Throwable throwable) {
                                Toast.makeText(DocumentViewActivity.this, R.string.file_delete_failure, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onFinish() {
                                progressDialog.dismiss();
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create().show();
    }

    /**
     * Download the document (all files zipped).
     */
    private void downloadZip() {
        if (document == null) return;
        String url = PreferenceUtil.getServerUrl(this) + "/api/file/zip?id=" + document.optString("id");
        String fileName = getTitle() + ".zip";
        downloadFile(url, fileName, getTitle().toString(), getString(R.string.downloading_document));
    }

    /**
     * Download a file using Android download manager.
     *
     * @param url URL to download
     * @param fileName Destination file name
     * @param title Notification title
     * @param description Notification description
     */
    private void downloadFile(String url, String fileName, String title, String description) {
        String authToken = PreferenceUtil.getAuthToken(this);
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.addRequestHeader("Cookie", "auth_token=" + authToken);
        request.setTitle(title);
        request.setDescription(description);
        downloadManager.enqueue(request);
    }

    /**
     * Delete the current document.
     */
    private void deleteDocument() {
        if (document == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.delete_document_title)
                .setMessage(R.string.delete_document_message)
                .setCancelable(true)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Dismiss the confirmation dialog
                        dialog.dismiss();

                        // Show a progress dialog while deleting
                        final ProgressDialog progressDialog = ProgressDialog.show(DocumentViewActivity.this,
                                getString(R.string.please_wait),
                                getString(R.string.document_deleting_message), true, true,
                                new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {
                                        DocumentResource.cancel(DocumentViewActivity.this);
                                    }
                                });

                        // Actual delete server call
                        final String documentId = document.optString("id");
                        DocumentResource.delete(DocumentViewActivity.this, documentId, new JsonHttpResponseHandler() {
                            @Override
                            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                                EventBus.getDefault().post(new DocumentDeleteEvent(documentId));
                            }

                            @Override
                            public void onAllFailure(int statusCode, Header[] headers, byte[] responseBytes, Throwable throwable) {
                                Toast.makeText(DocumentViewActivity.this, R.string.document_delete_failure, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onFinish() {
                                progressDialog.dismiss();
                            }
                        });
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create().show();

    }

    /**
     * A document fullscreen event has been fired.
     *
     * @param event Document fullscreen event
     */
    public void onEventMainThread(DocumentFullscreenEvent event) {
        findViewById(R.id.detailLayout).setVisibility(event.isFullscreen() ? View.GONE : View.VISIBLE);
    }

    /**
     * A document edit event has been fired.
     *
     * @param event Document edit event
     */
    public void onEventMainThread(DocumentEditEvent event) {
        if (document == null) return;
        if (event.getDocument().optString("id").equals(document.optString("id"))) {
            // The current document has been modified, refresh it
            refreshDocument(event.getDocument());
        }
    }

    /**
     * A document delete event has been fired.
     *
     * @param event Document delete event
     */
    public void onEventMainThread(DocumentDeleteEvent event) {
        if (document == null) return;
        if (event.getDocumentId().equals(document.optString("id"))) {
            // The current document has been deleted, close this activity
            finish();
        }
    }

    /**
     * A file delete event has been fired.
     *
     * @param event File delete event
     */
    public void onEventMainThread(FileDeleteEvent event) {
        if (filePagerAdapter == null) return;
        filePagerAdapter.remove(event.getFileId());
        final TextView filesEmptyView = (TextView) findViewById(R.id.filesEmptyView);
        if (filePagerAdapter.getCount() == 0) filesEmptyView.setVisibility(View.VISIBLE);
    }

    /**
     * A file add event has been fired.
     *
     * @param event File add event
     */
    public void onEventMainThread(FileAddEvent event) {
        if (document == null) return;
        if (document.optString("id").equals(event.getDocumentId())) {
            updateFiles();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (document == null) return;

        if (requestCode == REQUEST_CODE_ADD_FILE && resultCode == RESULT_OK) {
            List<Uri> uriList = new ArrayList<>();
            // Single file upload
            if (data.getData() != null) {
                uriList.add(data.getData());
            }

            // Handle multiple file upload
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); ++i) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        if (uri != null) {
                            uriList.add(uri);
                        }
                    }
                }
            }

            // Upload all files
            for (Uri uri : uriList) {
                Intent intent = new Intent(this, FileUploadService.class)
                        .putExtra(FileUploadService.PARAM_URI, uri)
                        .putExtra(FileUploadService.PARAM_DOCUMENT_ID, document.optString("id"));
                startService(intent);
            }
        }
    }

    /**
     * Refresh files list.
     */
    private void updateFiles() {
        if (document == null) return;

        final View progressBar = findViewById(R.id.progressBar);
        final TextView filesEmptyView = (TextView) findViewById(R.id.filesEmptyView);
        fileViewPager = (ViewPager) findViewById(R.id.fileViewPager);
        fileViewPager.setOffscreenPageLimit(1);
        fileViewPager.setAdapter(null);
        progressBar.setVisibility(View.VISIBLE);
        filesEmptyView.setVisibility(View.GONE);

        FileResource.list(this, document.optString("id"), new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                JSONArray files = response.optJSONArray("files");
                filePagerAdapter = new FilePagerAdapter(DocumentViewActivity.this, files);
                fileViewPager.setAdapter(filePagerAdapter);

                progressBar.setVisibility(View.GONE);
                if (files.length() == 0) filesEmptyView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAllFailure(int statusCode, Header[] headers, byte[] responseBytes, Throwable throwable) {
                filesEmptyView.setText(R.string.error_loading_files);
                progressBar.setVisibility(View.GONE);
                filesEmptyView.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}