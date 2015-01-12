/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app.syncadapter;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.murrayc.galaxyzoo.app.Log;
import com.murrayc.galaxyzoo.app.Utils;
import com.murrayc.galaxyzoo.app.provider.HttpUtils;
import com.murrayc.galaxyzoo.app.provider.ImageType;
import com.murrayc.galaxyzoo.app.provider.Item;
import com.murrayc.galaxyzoo.app.provider.client.ZooniverseClient;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SubjectAdder {
    private final Context mContext;

    /* A map of remote URIs to the last dates that we tried to download them.
     */
    private final Map<String, Date> mImageDownloadsInProgress = new HashMap<>();
    private final RequestQueue mRequestQueue;

    public SubjectAdder(final Context context, final RequestQueue requestQueue) {
        this.mContext = context;
        this.mRequestQueue = requestQueue;
    }

    /**
     * Download any images that have previously failed to download.
     *
     * @return Return true if we know for sure that no further downloading is currently necessary.
     */
    boolean downloadMissingImages() {
        boolean noWorkNeeded = true;

        throwIfNoNetwork();

        //Get all the items that have an image that is not yet fully downloaded:
        final ContentResolver resolver = getContext().getContentResolver();

        final String[] projection = {Item.Columns._ID,
                Item.Columns.LOCATION_STANDARD_DOWNLOADED,
                Item.Columns.LOCATION_STANDARD_URI_REMOTE,
                Item.Columns.LOCATION_STANDARD_URI,
                Item.Columns.LOCATION_THUMBNAIL_DOWNLOADED,
                Item.Columns.LOCATION_THUMBNAIL_URI_REMOTE,
                Item.Columns.LOCATION_THUMBNAIL_URI,
                Item.Columns.LOCATION_INVERTED_DOWNLOADED,
                Item.Columns.LOCATION_INVERTED_URI_REMOTE,
                Item.Columns.LOCATION_INVERTED_URI};
        final Cursor c = resolver.query(Item.ITEMS_URI, projection,
                getWhereClauseForDownloadNotDone(), new String[]{}, null);

        //Find out if the image is currently being downloaded:
        while (c.moveToNext()) {
            final String itemId = c.getString(0);
            if (TextUtils.isEmpty(itemId)) {
                continue;
            }

            final Uri itemUri = Utils.getItemUri(itemId);

            //Restart any downloads that seem to have failed before, or have been interrupted:
            final boolean standardDownloaded = c.getInt(1) == 1;
            if (!standardDownloaded) {
                final String uriStandardRemote = c.getString(2);
                if (!mImageDownloadsInProgress.containsKey(uriStandardRemote)) {
                    final String uriStandard = c.getString(3);
                    downloadMissingImage(itemUri, uriStandardRemote, uriStandard, ImageType.STANDARD);
                    noWorkNeeded = false;
                }
            }

            final boolean thumbnailDownloaded = c.getInt(4) == 1;
            if (!thumbnailDownloaded) {
                final String uriThumbnailRemote = c.getString(5);
                if (!mImageDownloadsInProgress.containsKey(uriThumbnailRemote)) {
                    final String uriThumbnail = c.getString(6);
                    downloadMissingImage(itemUri, uriThumbnailRemote, uriThumbnail, ImageType.THUMBNAIL);
                    noWorkNeeded = false;
                }
            }

            final boolean invertedDownloaded = c.getInt(7) == 1;
            if(!invertedDownloaded) {
                final String uriInvertedRemote = c.getString(8);
                if (!mImageDownloadsInProgress.containsKey(uriInvertedRemote)) {
                    final String uriInverted = c.getString(9);
                    downloadMissingImage(itemUri, uriInvertedRemote, uriInverted, ImageType.INVERTED);
                    noWorkNeeded = false;
                }
            }
        }

        c.close();

        return noWorkNeeded;
    }

    private void downloadMissingImage(final Uri itemUri, final String uriRemote, final String uriContent, final ImageType imageType) {
        Log.info("downloadMissingImage(): imageType=" + imageType + ", uriRemote=" + uriRemote);

        try {
            cacheUriToFile(uriRemote, uriContent, itemUri, imageType, true /* async */);
        } catch (final HttpUtils.NoNetworkException e) {
            //Ignore this - it is normal if wifi-only is set in the settings
            //and if we are then not on a wi-fi connection.
            Log.info("downloadMissingImage(): No network connection.");
        }
    }

    /**
     * @param subjects
     * @param asyncFileDownloads Get the image data asynchronously if this is true.
     */
    public void addSubjects(final List<ZooniverseClient.Subject> subjects, final boolean asyncFileDownloads) {
        if (subjects == null) {
            return;
        }

        for (final ZooniverseClient.Subject subject : subjects) {
            addSubject(subject, asyncFileDownloads);
        }
    }


    private void cacheUrisToFiles(final Uri itemUri, final boolean asyncFileDownloads) {

        final ContentResolver resolver = getContext().getContentResolver();

        //Actually cache the URIs' data in the local files:
        //This will mark the data as fully downloaded by setting the *Downloaded boolean fields,
        //so we do this only after creating the items record.

        final String[] projection = {
                Item.Columns.LOCATION_STANDARD_URI_REMOTE,
                Item.Columns.LOCATION_STANDARD_URI,
                Item.Columns.LOCATION_THUMBNAIL_URI_REMOTE,
                Item.Columns.LOCATION_THUMBNAIL_URI,
                Item.Columns.LOCATION_INVERTED_URI_REMOTE,
                Item.Columns.LOCATION_INVERTED_URI,
        };
        final Cursor c = resolver.query(itemUri, projection,
                null, new String[]{}, null);
        while (c.moveToNext()) {
            final String uriStandardRemote = c.getString(0);
            final String uriStandard = c.getString(1);
            final String uriThumbnailRemote = c.getString(2);
            final String uriThumbnail = c.getString(3);
            final String uriInvertedRemote = c.getString(4);
            final String uriInverted = c.getString(5);

            cacheUriToFileWithNullChecks(uriStandardRemote, uriStandard, itemUri, ImageType.STANDARD, asyncFileDownloads);
            cacheUriToFileWithNullChecks(uriThumbnailRemote, uriThumbnail, itemUri, ImageType.THUMBNAIL, asyncFileDownloads);
            cacheUriToFileWithNullChecks(uriInvertedRemote, uriInverted, itemUri, ImageType.INVERTED, asyncFileDownloads);
        }

        c.close();
    }

    private void cacheUriToFileWithNullChecks(final String uriStandardRemote, final String uriStandard, final Uri itemUri, final ImageType imageType, final boolean asyncFileDownloads) {
        if (TextUtils.isEmpty(uriStandardRemote) || TextUtils.isEmpty(uriStandard)) {
            Log.error("cacheUriToFileWithNullChecks(): Empty uriStandardRemote or uriStandard.");
        } else {
            try {
                cacheUriToFile(uriStandardRemote, uriStandard, itemUri, imageType, asyncFileDownloads);
            } catch (final HttpUtils.NoNetworkException e) {
                //Ignore this - it is normal if wifi-only is set in the settings
                //and if we are then not on a wi-fi connection.
                Log.info("cacheUriToFileWithNullChecks(): No network connection.");
            }
        }
    }

    /**
     * Download bytes from a url and store them in a file, optionally asynchronously in spawned thread.
     *
     * @param asyncFileDownloads Get the image data asynchronously if this is true.
     */
    private void cacheUriToFile(final String uriFileToCache, final String cacheFileUri, final Uri itemUri, final ImageType imageType, final boolean asyncFileDownloads) throws HttpUtils.NoNetworkException {
        if (TextUtils.isEmpty(uriFileToCache)) {
            throw new IllegalArgumentException("uriFileToCache is empty or null.");
        }

        if (TextUtils.isEmpty(cacheFileUri)) {
            throw new IllegalArgumentException("uriFileToCache is empty or null");
        }

        //Don't attempt it if it is already in progress.
        if (mImageDownloadsInProgress.containsKey(uriFileToCache)) {
            //TODO: Check the actual date?
            return;
        }

        //Don't try if there is no suitable network connection:
        HttpUtils.throwIfNoNetwork(getContext());

        final Date now = new Date();
        mImageDownloadsInProgress.put(uriFileToCache, now);

        if (asyncFileDownloads) {
            Log.info("cacheUriToFile(): uriFileToCache=" + uriFileToCache);

            final Request<Boolean> request = new HttpUtils.FileCacheRequest(getContext(), uriFileToCache, cacheFileUri,
                    new Response.Listener<Boolean>() {
                        @Override
                        public void onResponse(final Boolean response) {
                            onImageDownloadDone(response, uriFileToCache, itemUri, imageType);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(final VolleyError error) {
                            Log.error("cacheUriToFile.onErrorResponse()", error);
                            onImageDownloadDone(false, uriFileToCache, itemUri, imageType);
                        }
                    });

            //We won't request the same image again if it succeeded once:
            addRequestToQueue(request);
        } else {
            boolean response = false;
            try {
                response = HttpUtils.cacheUriToFileSync(getContext(), mRequestQueue, uriFileToCache, cacheFileUri);
            } catch (final HttpUtils.FileCacheException e) {
                Log.error("SubjectAdder.CacheUriToFile(): Exception from HttpUtils.cacheUriToFileSync", e);
            }

            onImageDownloadDone(response, uriFileToCache, itemUri, imageType);
        }
    }

    private void addRequestToQueue(final Request<Boolean> request) {
        //We won't request the same image again if it succeeded once,
        //so don't waste memory or storage caching it.
        //(We are downloading it to our own cache, of course.)
        request.setShouldCache(false);

        mRequestQueue.add(request);
    }

    private void onImageDownloadDone(final boolean success, final String uriFileToCache, final Uri itemUri, final ImageType imageType) {
        markImageDownloadAsNotInProgress(uriFileToCache);
        if (success) {
            markImageAsDownloaded(itemUri, imageType, uriFileToCache);
        } else {
            Log.error("onImageDownloadDone(): cacheUriToContentUriFileSync(): failed.");
        }
    }

    private Context getContext() {
        return mContext;
    }

    private void throwIfNoNetwork() {
        HttpUtils.throwIfNoNetwork(getContext());
    }


    private void markImageDownloadAsNotInProgress(final String uriFileToCache) {
        mImageDownloadsInProgress.remove(uriFileToCache);
    }

    private void markImageAsDownloaded(final Uri itemUri, final ImageType imageType, final String uriFileToCache) {

        //Don't try downloading this again later:

        //Actually the caller should already have removed this,
        //regardless of the download's success or failure.
        //but let's be sure:
        markImageDownloadAsNotInProgress(uriFileToCache);

        //Let users of the ContentProvider API know that the image has been fully downloaded
        //so it's safe to use it:
        String fieldName = null;
        switch (imageType) {
            case STANDARD:
                fieldName = Item.Columns.LOCATION_STANDARD_DOWNLOADED;
                break;
            case THUMBNAIL:
                fieldName = Item.Columns.LOCATION_THUMBNAIL_DOWNLOADED;
                break;
            case INVERTED:
                fieldName = Item.Columns.LOCATION_INVERTED_DOWNLOADED;
                break;
            default:
                throw new IllegalArgumentException("markImageAsDownloaded(): Unexpected imageType.");
        }

        final ContentResolver resolver = getContext().getContentResolver();

        final ContentValues values = new ContentValues();
        values.put(fieldName, 1);

        final int affected = resolver.update(itemUri, values,
                null, null);
        if (affected != 1) {
            Log.error("markImageAsDownloaded(): Failed to mark image download as done.");
        } //else {
            //Let the ListView (or other UI) know that there is more to display.
            //TODO? notifyRowChangeBySubjectId(subjectId);
        //}
    }

    /**
     * @param item
     * @param asyncFileDownloads Get the image data asynchronously if this is true.
     */
    void addSubject(final ZooniverseClient.Subject item, final boolean asyncFileDownloads) {
        if (subjectIsInDatabase(item.getSubjectId())) {
            //It is already in the database.
            //TODO: Update the row?
            return;
        }

        final ContentResolver resolver = getContext().getContentResolver();

        final ContentValues values = new ContentValues();
        values.put(Item.Columns.SUBJECT_ID, item.getSubjectId());
        values.put(Item.Columns.ZOONIVERSE_ID, item.getZooniverseId());

        //The ItemsContentProvider will take care of creating local file URIs for the remote URis,
        //and this SyncAdapter will request that the remote image files are downloaded into those local file URIs.
        values.put(Item.Columns.LOCATION_STANDARD_URI_REMOTE, item.getLocationStandard());
        values.put(Item.Columns.LOCATION_THUMBNAIL_URI_REMOTE, item.getLocationThumbnail());
        values.put(Item.Columns.LOCATION_INVERTED_URI_REMOTE, item.getLocationInverted());

        final Uri itemUri = resolver.insert(Item.ITEMS_URI, values);
        if (itemUri == null) {
            throw new IllegalStateException("could not insert " +
                    "content values: " + values);
        }

        cacheUrisToFiles(itemUri, asyncFileDownloads);

        //TODO: notifyRowChangeById(rowId);
    }

    boolean subjectIsInDatabase(final String subjectId) {
        //TODO: Use COUNT_AS_COUNT ?
        final ContentResolver resolver = getContext().getContentResolver();

        final String[] projection = {Item.Columns.SUBJECT_ID};
        final String whereClause = Item.Columns.SUBJECT_ID + " = ?"; //We use ? to avoid SQL Injection.
        final String[] selectionArgs = {subjectId};
        final Cursor c = resolver.query(Item.ITEMS_URI, projection,
                whereClause, selectionArgs, null);
        final boolean result = c.getCount() > 0;
        c.close();
        return result;
    }

    private static String getWhereClauseForDownloadNotDone() {
        return "(" +
                Item.Columns.LOCATION_STANDARD_DOWNLOADED + " != 1" +
                ") OR (" +
                Item.Columns.LOCATION_THUMBNAIL_DOWNLOADED + " != 1" +
                ") OR (" +
                Item.Columns.LOCATION_INVERTED_DOWNLOADED + " != 1" +
                ")";
    }
}
