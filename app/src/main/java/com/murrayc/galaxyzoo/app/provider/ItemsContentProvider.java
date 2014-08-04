/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo.
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app.provider;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.text.TextUtils;

import com.murrayc.galaxyzoo.app.Log;
import com.murrayc.galaxyzoo.app.provider.rest.FileResponseHandler;
import com.murrayc.galaxyzoo.app.provider.rest.GalaxyZooResponseHandler;
import com.murrayc.galaxyzoo.app.provider.rest.UriRequestTask;

import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemsContentProvider extends ContentProvider {

    public static final String METHOD_REQUEST_ITEMS = "request-items";
    public static final String URI_PART_ITEM = "item";
    public static final String URI_PART_ITEM_ID_NEXT = "next"; //Use in place of the item ID to get the next unclassified item.
    public static final String URI_PART_FILE = "file";
    public static final String URI_PART_CLASSIFICATION = "classification";
    public static final String URI_PART_CLASSIFICATION_ANSWER = "classification-answer";
    public static final String URI_PART_CLASSIFICATION_CHECKBOX = "classification-checkbox";

    /**
     * The MIME type of {@link Item#CONTENT_URI} providing a directory of items.
     */
    private static final String CONTENT_TYPE_ITEMS =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.item";

    /**
     * The MIME type of a {@link Item#CONTENT_URI} sub-directory of a single
     * item.
     */
    private static final String CONTENT_TYPE_ITEM =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.item";

    /**
     * The MIME type of {@link Item#CONTENT_URI} providing a directory of classifications.
     */
    private static final String CONTENT_TYPE_CLASSIFICATIONS =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.classification";

    /**
     * The MIME type of a {@link Item#CONTENT_URI} sub-directory of a single
     * classification.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.classification";

    /**
     * The MIME type of {@link Item#CONTENT_URI} providing a directory of classifications.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_ANSWERS =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.classification-answer";

    /**
     * The MIME type of a {@link Item#CONTENT_URI} sub-directory of a single
     * classification answer.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_ANSWER =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.classification-answer";

    /**
     * The MIME type of {@link Item#CONTENT_URI} providing a directory of classifications.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_CHECKBOXES =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.classification-checkboxes";

    /**
     * The MIME type of a {@link Item#CONTENT_URI} sub-directory of a single
     * classification checkbox.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_CHECKBOX =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.classification-checkbox";

    /** REST uri for querying items.
     * Like, the Galaxy-Zoo website's code, this hard-codes the Group ID for the Sloan survey: */
    private static final String QUERY_URI =
            "https://api.zooniverse.org/projects/galaxy_zoo/groups/50251c3b516bcb6ecb000002/subjects?limit=5";


    //TODO: Use an enum?
    private static final int MATCHER_ID_ITEMS = 1;
    private static final int MATCHER_ID_ITEM = 2;
    private static final int MATCHER_ID_ITEM_NEXT= 3;
    private static final int MATCHER_ID_FILE = 4;
    private static final int MATCHER_ID_CLASSIFICATIONS = 5;
    private static final int MATCHER_ID_CLASSIFICATION = 6;
    private static final int MATCHER_ID_CLASSIFICATION_ANSWERS = 7;
    private static final int MATCHER_ID_CLASSIFICATION_ANSWER = 8;
    private static final int MATCHER_ID_CLASSIFICATION_CHECKBOXES = 9;
    private static final int MATCHER_ID_CLASSIFICATION_CHECKBOX = 10;
    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // A URI for the list of all items:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_ITEM, MATCHER_ID_ITEMS);

        // A URI for a single item:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_ITEM + "/" + URI_PART_ITEM_ID_NEXT, MATCHER_ID_ITEM_NEXT);

        // A URI for a single item:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_ITEM + "/#", MATCHER_ID_ITEM);

        // A URI for a single file:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_FILE + "/#", MATCHER_ID_FILE);

        // A URI for the list of all classifications:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION, MATCHER_ID_CLASSIFICATIONS);

        // A URI for a single classification:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION + "/#", MATCHER_ID_CLASSIFICATION);

        // A URI for the list of all classifications:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_ANSWER, MATCHER_ID_CLASSIFICATION_ANSWERS);

        // A URI for a single classification:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_ANSWER + "/#", MATCHER_ID_CLASSIFICATION_ANSWER);

        // A URI for the list of all classifications:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_CHECKBOX, MATCHER_ID_CLASSIFICATION_CHECKBOXES);

        // A URI for a single classification:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_CHECKBOX + "/#", MATCHER_ID_CLASSIFICATION_CHECKBOX);
    }

    private static final String[] FILE_MIME_TYPES = new String[]{"application/x-glom"};


    /**
     * A map of GlomContentProvider projection column names to underlying Sqlite column names
     * for /item/ URIs, mapping to the items tables.
     */
    private static final Map<String, String> sItemsProjectionMap;
    private static final Map<String, String> sClassificationAnswersProjectionMap;
    private static final Map<String, String> sClassificationCheckboxesProjectionMap;

    static {
        sItemsProjectionMap = new HashMap<>();
        sItemsProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sItemsProjectionMap.put(Item.Columns.DONE, DatabaseHelper.ItemsDbColumns.DONE);
        sItemsProjectionMap.put(Item.Columns.SKIPPED, DatabaseHelper.ItemsDbColumns.SKIPPED);
        sItemsProjectionMap.put(Item.Columns.SUBJECT_ID, DatabaseHelper.ItemsDbColumns.SUBJECT_ID);
        sItemsProjectionMap.put(Item.Columns.ZOONIVERSE_ID, DatabaseHelper.ItemsDbColumns.ZOONIVERSE_ID);
        sItemsProjectionMap.put(Item.Columns.LOCATION_STANDARD_URI, DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_URI);
        sItemsProjectionMap.put(Item.Columns.LOCATION_THUMBNAIL_URI, DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_URI);
        sItemsProjectionMap.put(Item.Columns.LOCATION_INVERTED_URI, DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_URI);


        sClassificationAnswersProjectionMap = new HashMap<>();
        sClassificationAnswersProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.ITEM_ID, DatabaseHelper.ClassificationAnswersDbColumns.ITEM_ID);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.SEQUENCE, DatabaseHelper.ClassificationAnswersDbColumns.SEQUENCE);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.QUESTION_ID, DatabaseHelper.ClassificationAnswersDbColumns.QUESTION_ID);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.ANSWER_ID, DatabaseHelper.ClassificationAnswersDbColumns.ANSWER_ID);

        sClassificationCheckboxesProjectionMap = new HashMap<>();
        sClassificationCheckboxesProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.ITEM_ID, DatabaseHelper.ClassificationCheckboxesDbColumns.ITEM_ID);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.SEQUENCE, DatabaseHelper.ClassificationCheckboxesDbColumns.SEQUENCE);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.QUESTION_ID, DatabaseHelper.ClassificationCheckboxesDbColumns.QUESTION_ID);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.CHECKBOX_ID, DatabaseHelper.ClassificationCheckboxesDbColumns.CHECKBOX_ID);

    }

    private DatabaseHelper mOpenDbHelper;

    public ItemsContentProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        int affected;

        switch (match) {
            //TODO: Do not support this because it would delete everything in one go?
            case MATCHER_ID_ITEMS: {
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_ITEMS,
                        (!TextUtils.isEmpty(selection) ?
                                " AND (" + selection + ')' : ""),
                        selectionArgs
                );
                //TODO: Delete all associated files too.
                break;
            }
            case MATCHER_ID_ITEM: {
                final UriParts uriParts = parseContentUri(uri);
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_ITEMS,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                //TODO: Delete the associated files too.
                break;
            }

            //TODO: Do not support this because it would delete everything in one go?
            case MATCHER_ID_CLASSIFICATION_ANSWERS: {
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                                (!TextUtils.isEmpty(selection) ?
                                        " AND (" + selection + ')' : ""),
                        selectionArgs
                );
                //TODO: Delete all associated files too.
                break;
            }
            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                final UriParts uriParts = parseContentUri(uri);
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                //TODO: Delete the associated files too.
                break;
            }

            //TODO: Do not support this because it would delete everything in one go?
            case MATCHER_ID_CLASSIFICATION_CHECKBOXES: {
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        (!TextUtils.isEmpty(selection) ?
                                " AND (" + selection + ')' : ""),
                        selectionArgs
                );
                //TODO: Delete all associated files too.
                break;
            }
            case MATCHER_ID_CLASSIFICATION_CHECKBOX: {
                final UriParts uriParts = parseContentUri(uri);
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                //TODO: Delete the associated files too.
                break;
            }

            //TODO?: case MATCHER_ID_FILE:
            default:
                throw new IllegalArgumentException("unknown item: " +
                        uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return affected;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_ITEMS:
                return CONTENT_TYPE_ITEMS;
            case MATCHER_ID_ITEM:
            case MATCHER_ID_ITEM_NEXT:
                return CONTENT_TYPE_ITEM;
            case MATCHER_ID_CLASSIFICATION_ANSWERS:
                return CONTENT_TYPE_CLASSIFICATION_ANSWERS;
            case MATCHER_ID_CLASSIFICATION_ANSWER:
                return CONTENT_TYPE_CLASSIFICATION_ANSWER;
            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
                return CONTENT_TYPE_CLASSIFICATION_CHECKBOXES;
            case MATCHER_ID_CLASSIFICATION_CHECKBOX:
                return CONTENT_TYPE_CLASSIFICATION_CHECKBOX;
            default:
                throw new IllegalArgumentException("Unknown item type: " +
                        uri);
        }
    }

    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {

        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_FILE:
                if (mimeTypeFilter != null) {
                    // We use ClipDescription just so we can use its filterMimeTypes()
                    // though we are not intested in ClipData here.
                    // TODO: Find a more suitable utility function?
                    final ClipDescription clip = new ClipDescription(null, FILE_MIME_TYPES);
                    return clip.filterMimeTypes(mimeTypeFilter);
                } else {
                    return FILE_MIME_TYPES;
                }
            default:
                throw new IllegalArgumentException("Unknown type: " +
                        uri);
        }
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return super.openFileHelper(uri, mode);
    }

    //TODO: Is this actually used by anything?
    @Override
    public Uri insert(final Uri uri, final ContentValues values) {

        // Note: We map the values' columns names to the internal database columns names.
        // Strangely, I can't find any example code, or open source code, that bothers to do this,
        // though examples for query() generally do.
        // Maybe they don't do it because it's so awkward. murrayc.
        // But if we don't do this then we are leaking the internal database structure out as our API.

        Uri uriInserted = null;

        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_ITEMS:
            case MATCHER_ID_ITEM: {
                // Get (our) local content URIs for the local caches of any (or any future) remote URIs for the images:
                // Notice that we allow the client to provide a remote URI for each but we then change
                // it to our local URI of our local cache of that remote file.
                // Even if no URI is provided by the client, we still create the local URI and put
                // it in the table row for later use.
                final ContentValues valuesComplete = new ContentValues(values);

                Uri fileUri = createFileUri(values.getAsString(Item.Columns.LOCATION_STANDARD_URI));
                if (fileUri != null) {
                    valuesComplete.put(Item.Columns.LOCATION_STANDARD_URI, fileUri.toString());
                }

                fileUri = createFileUri(values.getAsString(Item.Columns.LOCATION_THUMBNAIL_URI));
                if (fileUri != null) {
                    valuesComplete.put(Item.Columns.LOCATION_THUMBNAIL_URI, fileUri.toString());
                }

                fileUri = createFileUri(values.getAsString(Item.Columns.LOCATION_INVERTED_URI));
                if (fileUri != null) {
                    valuesComplete.put(Item.Columns.LOCATION_INVERTED_URI, fileUri.toString());
                }

                uriInserted = insertMappedValues(DatabaseHelper.TABLE_NAME_ITEMS, valuesComplete,
                        sItemsProjectionMap, Item.ITEMS_URI);

                break;
            }
            case MATCHER_ID_CLASSIFICATION_ANSWERS:
            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                uriInserted = insertMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        values, sClassificationAnswersProjectionMap,
                        ClassificationAnswer.CLASSIFICATION_ANSWERS_URI);
                break;
            }
            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
            case MATCHER_ID_CLASSIFICATION_CHECKBOX: {
                uriInserted = insertMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        values, sClassificationCheckboxesProjectionMap,
                        ClassificationCheckbox.CLASSIFICATION_CHECKBOXES_URI);
                break;
            }
            default:
                //This could be because of an invalid -1 ID in the # position.
                throw new IllegalArgumentException("unsupported uri: " + uri);
        }

        return uriInserted;
    }

    private int updateMappedValues(final String tableName, final ContentValues values, final Map<String, String> projectionMap, String selection,
                                   String[] selectionArgs) {
        final ContentValues valuesToUse = getMappedContentValues(values, projectionMap);

        // insert the initialValues into a new database row
        final SQLiteDatabase db = getDb();

        return db.update(tableName, valuesToUse,
                selection, selectionArgs);
    }


    private Uri insertMappedValues(final String tableName, final ContentValues values, final Map<String, String> projectionMap, final Uri uriPrefix) {
        final ContentValues valuesToUse = getMappedContentValues(values, projectionMap);

        // insert the initialValues into a new database row
        final SQLiteDatabase db = getDb();

        try {
            final long rowId = db.insertOrThrow(tableName,
                    Item.Columns._ID, valuesToUse);
            if (rowId >= 0) {
                final Uri itemUri =
                        ContentUris.withAppendedId(
                                uriPrefix, rowId);
                getContext().getContentResolver().notifyChange(itemUri, null);
                return itemUri; //The URI of the newly-added Item.
            } else {
                throw new IllegalStateException("could not insert " +
                        "content values: " + values);
            }
        } catch (android.database.SQLException e) {
            Log.error("insert failed", e);
        }

        return null;
    }

    private static ContentValues getMappedContentValues(final ContentValues values, final Map<String, String> projectionMap) {
        final ContentValues result = new ContentValues();

        for (final String keyExternal : values.keySet()) {
            final String keyInternal = projectionMap.get(keyExternal);
            if (!TextUtils.isEmpty(keyInternal)) {
                final Object value = values.get(keyExternal);
                putValueinContentValues(result, keyInternal, value);
            }
        }

        return result;
    }

    /** There is no ContentValues.put(key, object),
     * only put(key, String), put(key, Boolean), etc.
     *  so we use this tedious implementation instead,
     *  so our code can be more generic.
     * @param values
     * @param key
     * @param value
     */
    private static void putValueinContentValues(final ContentValues values, final String key, final Object value) {
        if (value instanceof String) {
            values.put(key, (String) value);
        } else if (value instanceof Boolean) {
            values.put(key, (Boolean) value);
        } else if (value instanceof Integer) {
            values.put(key, (Integer) value);
        } else if (value instanceof Long) {
            values.put(key, (Long) value);
        } else if (value instanceof Double) {
            values.put(key, (Double) value);
        }
    }

    /**
     * @param uriOfFileToCache This may be null if the new file should be empty.
     */
    private Uri createFileUri(final String uriOfFileToCache) {
        final SQLiteDatabase db = getDb();
        final long fileId = db.insertOrThrow(DatabaseHelper.TABLE_NAME_FILES,
                DatabaseHelper.FilesDbColumns.FILE_DATA, null);

        //Build a value for the _data column, using the autogenerated file _id:
        String realFileUri = "";
        try {
            final Context context = getContext();
            if (context != null) {
                final File realFile = new File(context.getExternalFilesDir(null),
                        Long.toString(fileId)); //TODO: Is toString() affected by the locale?

                //Actually create an empty file there -
                //otherwise when we try to write to it via openOutputStream()
                //we will get a FileNotFoundException.
                realFile.createNewFile();

                realFileUri = realFile.getAbsolutePath();
            }
        } catch (UnsupportedOperationException e) {
            //This happens while running under ProviderTestCase2.
            //so we just catch it and provide a useful value,
            //so at least the other functionality can be tested.
            //TODO: Find a way to let it succeed.
            realFileUri = "testuri";
            Log.error("Unsupported operation", e);
        } catch (IOException e) {
            Log.error("IOException", e);
            return null;
        }

        //Put the value for the _data column in the files table:
        //This will be used implicitly by openOutputStream() and openInputStream():
        ContentValues valuesUpdate = new ContentValues();
        valuesUpdate.put(DatabaseHelper.FilesDbColumns.FILE_DATA, realFileUri);
        db.update(DatabaseHelper.TABLE_NAME_FILES, valuesUpdate,
                BaseColumns._ID + " = ?", new String[]{Double.toString(fileId)});

        //Build the content: URI for the file to put in the Item's table:
        Uri fileUri = null;
        if (fileId >= 0) {
            fileUri = ContentUris.withAppendedId(Item.FILE_URI, fileId);
            //TODO? getContext().getContentResolver().notifyChange(fileId, null);
        }

        //Actually cache the URI's data in the local file:
        if (uriOfFileToCache != null) {
            cacheUriToFile(uriOfFileToCache, realFileUri);
        }


        return fileUri;
    }


    /**
     * Spawns a thread to download bytes from a url and store them in a file.
     */
    private void cacheUriToFile(final String uriFileToCache, final String cacheFileUri) {
        // use id as a unique request tag
        final HttpGet get = new HttpGet(uriFileToCache);
        final UriRequestTask requestTask = new UriRequestTask(
                get, new FileResponseHandler(cacheFileUri),
                getContext());
        final Thread t = new Thread(requestTask);
        t.start();
    }

    @Override
    public boolean onCreate() {
        mOpenDbHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_REQUEST_ITEMS.equals(method)) {
            return null;
        }

        /** Check with the remote REST API asynchronously,
         * informing the calling client later via notification.
         */
        //TODO: Only do this if there are no unclassified items:
        asyncQueryRequest(QUERY_URI);

        return null;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        //TODO: Avoid a direct implicit mapping between the Cursor column names in "selection" and the
        //underlying SQL database names.

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = DatabaseHelper.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        int match = sUriMatcher.match(uri);

        Cursor c;
        switch (match) {
            case MATCHER_ID_ITEMS: {
                // query the database for all items:
                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
                builder.setProjectionMap(sItemsProjectionMap);
                c = builder.query(getDb(), projection,
                        selection, selectionArgs,
                        null, null, orderBy);

                c.setNotificationUri(getContext().getContentResolver(),
                        Item.CONTENT_URI);

                //The client must call(TODO) sometime to actually fill the database with items,
                //and the client will then be notified via the cursor that there are new items.

                break;
            }
            case MATCHER_ID_ITEM: {
                // query the database for a specific item:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection

                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
                builder.setProjectionMap(sItemsProjectionMap);
                builder.appendWhere(BaseColumns._ID + " = ?"); //We use ? to avoid SQL Injection.
                c = builder.query(getDb(), projection,
                        selection, prependToArray(selectionArgs, uriParts.itemId),
                        null, null, orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        Item.CONTENT_URI); //TODO: More precise?
                break;
            }

            case MATCHER_ID_ITEM_NEXT: {
                c = queryItemNext(uri, projection, selection, selectionArgs, orderBy);
                if(c.getCount() < 1) {
                    //Get some more from the REST server and then try again.
                    final Thread thread = asyncQueryRequest(QUERY_URI);
                    if (thread != null) {
                        try {
                            thread.join(); //Wait until it finishes.
                        } catch (InterruptedException e) {
                            Log.error("thread.join() failed.", e);
                        }
                    }

                    c = queryItemNext(uri, projection, selection, selectionArgs, orderBy);
                }

                c.setNotificationUri(getContext().getContentResolver(),
                        Item.CONTENT_URI); //TODO: More precise?
                break;
            }

            case MATCHER_ID_FILE:
                // query the database for a specific file:
                // The caller will then use the _data value (the normal filesystem URI of a file).
                final long fileId = ContentUris.parseId(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                c = getDb().query(DatabaseHelper.TABLE_NAME_FILES, projection,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, fileId), null, null, orderBy
                );

                c.setNotificationUri(getContext().getContentResolver(),
                        Item.FILE_URI); //TODO: More precise?
                break;

            case MATCHER_ID_CLASSIFICATION_ANSWERS: {
                // query the database for all items:
                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS);
                builder.setProjectionMap(sClassificationAnswersProjectionMap);
                c = builder.query(getDb(), projection,
                        selection, selectionArgs,
                        null, null, orderBy);

                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationAnswer.CONTENT_URI);

                break;
            }
            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                // query the database for a specific item:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection

                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS);
                builder.setProjectionMap(sClassificationAnswersProjectionMap);
                builder.appendWhere(BaseColumns._ID + " = ?"); //We use ? to avoid SQL Injection.
                c = builder.query(getDb(), projection,
                        selection, prependToArray(selectionArgs, uriParts.itemId),
                        null, null, orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationAnswer.CONTENT_URI); //TODO: More precise?
                break;
            }

            case MATCHER_ID_CLASSIFICATION_CHECKBOXES: {
                // query the database for all items:
                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES);
                builder.setProjectionMap(sClassificationCheckboxesProjectionMap);
                c = builder.query(getDb(), projection,
                        selection, selectionArgs,
                        null, null, orderBy);

                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationCheckbox.CONTENT_URI);

                break;
            }
            case MATCHER_ID_CLASSIFICATION_CHECKBOX: {
                // query the database for a specific item:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection

                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES);
                builder.setProjectionMap(sClassificationCheckboxesProjectionMap);
                builder.appendWhere(BaseColumns._ID + " = ?"); //We use ? to avoid SQL Injection.
                c = builder.query(getDb(), projection,
                        selection, prependToArray(selectionArgs, uriParts.itemId),
                        null, null, orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationCheckbox.CONTENT_URI); //TODO: More precise?
                break;
            }

            default:
                //This could be because of an invalid -1 ID in the # position.
                throw new IllegalArgumentException("unsupported uri: " + uri);
        }

        //TODO: Can we avoid passing a Sqlite cursor up as a ContentResolver cursor?
        return c;
    }

    private Cursor queryItemNext(Uri uri, String[] projection, String selection, String[] selectionArgs, String orderBy) {
        Cursor c;// query the database for a single  item that is not yet done or skipped:
        final String whereClause =
                "(" + DatabaseHelper.ItemsDbColumns.DONE + " != 1) AND " +
                "(" + DatabaseHelper.ItemsDbColumns.SKIPPED + " != 1)";

        //Prepend our ID=? argument to the selection arguments.
        //This lets us use the ? syntax to avoid SQL injection

        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
        builder.setProjectionMap(sItemsProjectionMap);
        builder.appendWhere(whereClause);
        c = builder.query(getDb(), projection,
                selection, selectionArgs,
                null, null, orderBy, "1");
        return c;
    }

    private String[] prependToArray(final String[] selectionArgs, long value) {
        return prependToArray(selectionArgs, Double.toString(value));
    }

    private String[] prependToArray(final String[] array, final String value) {
        int arrayLength = 0;
        if (array != null) {
            arrayLength = array.length;
        }

        final String[] result = new String[arrayLength + 1];
        result[0] = value;

        if (arrayLength > 0) {
            System.arraycopy(array, 0, result, 1, result.length);
        }

        return result;
    }

    private UriParts parseContentUri(final Uri uri) {
        final UriParts result = new UriParts();
        //ContentUris.parseId(uri) gets the first ID, not the last.
        //final long userId = ContentUris.parseId(uri);
        final List<String> uriParts = uri.getPathSegments();
        final int size = uriParts.size();

        if (size < 2) {
            Log.error("The URI did not have the expected number of parts.");
        }

        //Note: The UriMatcher will not even match the URI if this id (#) is -1
        //so we will never reach this code then:
        result.itemId = uriParts.get(1);

        return result;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        int affected;

        // Note: We map the values' columns names to the internal database columns names.
        // Strangely, I can't find any example code, or open source code, that bothers to do this,
        // though examples for query() generally do.
        // Maybe they don't do it because it's so awkward. murrayc.
        // But if we don't do this then we are leaking the internal database structure out as our API.

        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_ITEMS:
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_ITEMS, values, sItemsProjectionMap,
                        selection, selectionArgs);
                break;

            case MATCHER_ID_ITEM: {
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_ITEMS, values, sItemsProjectionMap,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId));
                break;
            }

            case MATCHER_ID_CLASSIFICATION_ANSWERS:
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        values, sClassificationAnswersProjectionMap,
                        selection, selectionArgs);
                break;

            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        values, sClassificationAnswersProjectionMap,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                break;
            }

            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        values, sClassificationCheckboxesProjectionMap,
                        selection, selectionArgs);
                break;

            case MATCHER_ID_CLASSIFICATION_CHECKBOX: {
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        values, sClassificationCheckboxesProjectionMap,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return affected;
    }

    private String prependIdToSelection(final String selection) {
        return BaseColumns._ID + " = ?"
                + (!TextUtils.isEmpty(selection) ?
                " AND (" + selection + ')' : "");
    }

    private SQLiteDatabase getDb() {
        return mOpenDbHelper.getWritableDatabase();
    }

    //TODO: Reimplement this, in GalaxyZooResponseHandler, as an insert(uri) call,
    //to avoid repetition, or would that be too inefficient?
    public long addSubject(final Subject item) {
        if(subjectIsInDatabase(item.mId)) {
            //It is already in the database.
            //TODO: Update the row?
            return 0;
        }

        final SQLiteDatabase db = getDb();

        final ContentValues values = new ContentValues();
        values.put(DatabaseHelper.ItemsDbColumns.SUBJECT_ID, item.mId);
        values.put(DatabaseHelper.ItemsDbColumns.ZOONIVERSE_ID, item.mZooniverseId);

        //Get (our) local content URIs of cache files instead of the remote URIs for the images:
        Uri fileUri = createFileUri(item.mLocationStandard);
        if (fileUri != null) {
            values.put(DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_URI, fileUri.toString());
        }

        fileUri = createFileUri(item.mLocationThumbnail);
        if (fileUri != null) {
            values.put(DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_URI, fileUri.toString());
        }

        fileUri = createFileUri(item.mLocationInverted);
        if (fileUri != null) {
            values.put(DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_URI, fileUri.toString());
        }

        final long rowId = db.insert(DatabaseHelper.TABLE_NAME_ITEMS,
                Item.Columns._ID, values);
        if (rowId >= 0) {
            final Uri insertUri =
                    ContentUris.withAppendedId(
                            Item.ITEMS_URI, rowId);
            getContext().getContentResolver().notifyChange(insertUri, null);
            return rowId;
        } else {
            throw new IllegalStateException("could not insert " +
                    "content values: " + values);
        }
    }

    private boolean subjectIsInDatabase(final String subjectId) {
        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
        builder.setProjectionMap(sItemsProjectionMap);
        builder.appendWhere(Item.Columns.SUBJECT_ID + " = ?"); //We use ? to avoid SQL Injection.
        final String[] projection = {Item.Columns.SUBJECT_ID};
        final String[] selectionArgs = {subjectId};
        final Cursor c = builder.query(getDb(), projection,
                null, selectionArgs,
                null, null, null);
        return c.getCount() > 0;
    }

    /**
     * There are 2 tables: items and files.
     * The items table has a uri field that specifies a record in the files tables.
     * The files table has a (standard for openInput/OutputStream()) _data field that
     * contains the URI of the file for the item.
     * <p/>
     * The location and creation of the SQLite database is left entirely up to the SQLiteOpenHelper
     * class. We just store its name in the Document.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        private static final int DATABASE_VERSION = 11;
        private static final String DATABASE_NAME = "items.db";

        private static final String TABLE_NAME_ITEMS = "items";
        private static class ItemsDbColumns implements BaseColumns {
            //Specific to our app:
            protected static final String DONE = "done"; //1 or 0. Whether the user has classified it already.
            protected static final String SKIPPED = "skipped"; //1 or 0. Whether the user has skipped it already.

            //From the REST API:
            protected static final String SUBJECT_ID = "subjectId";
            protected static final String ZOONIVERSE_ID = "zooniverseId";
            protected static final String LOCATION_STANDARD_URI = "locationStandardUri"; //The content URI for a file in the files table.
            protected static final String LOCATION_THUMBNAIL_URI = "locationThumbnailUri"; //The content URI for a file in the files table.
            protected static final String LOCATION_INVERTED_URI = "locationInvertedUri"; //The content URI for a file in the files table.
        }

        private static final String TABLE_NAME_FILES = "files";
        private static class FilesDbColumns implements BaseColumns {
            private static final String FILE_DATA = "_data"; //The real URI
        }

        //Each item row has many classification_answers rows.
        private static final String TABLE_NAME_CLASSIFICATION_ANSWERS = "classification_answers";
        private static class ClassificationAnswersDbColumns implements BaseColumns  {
            private static final String ITEM_ID = "itemId";
            private static final String SEQUENCE = "sequence";
            private static final String QUESTION_ID = "questionId";
            private static final String ANSWER_ID = "answerId";
        }

        //Each item row has some classification_checkboxes rows.
        private static final String TABLE_NAME_CLASSIFICATION_CHECKBOXES = "classification_checkboxes";
        private static class ClassificationCheckboxesDbColumns implements BaseColumns  {

            private static final String ITEM_ID = "classificationId";
            private static final String SEQUENCE = "sequence";
            private static final String QUESTION_ID = "questionId";
            private static final String CHECKBOX_ID = "checkboxId";
        }

        private static final String DEFAULT_SORT_ORDER = Item.Columns._ID + " DESC";

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            createTable(sqLiteDatabase);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase,
                              int oldv, int newv) {
            //TODO: Don't just lose the data?
            if (oldv != newv) {
                dropTable(sqLiteDatabase, TABLE_NAME_ITEMS);
                dropTable(sqLiteDatabase, TABLE_NAME_FILES);
                dropTable(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_ANSWERS);
                dropTable(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_CHECKBOXES);

                createTable(sqLiteDatabase);
            }
        }

        private void dropTable(final SQLiteDatabase sqLiteDatabase, final String tableName) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " +
                    tableName + ";");
        }

        private void createTable(SQLiteDatabase sqLiteDatabase) {
            String qs = "CREATE TABLE " + TABLE_NAME_ITEMS + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ItemsDbColumns.DONE + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.SKIPPED + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.SUBJECT_ID + " TEXT, " +
                    ItemsDbColumns.ZOONIVERSE_ID + " TEXT, " +
                    ItemsDbColumns.LOCATION_STANDARD_URI + " TEXT, " +
                    ItemsDbColumns.LOCATION_THUMBNAIL_URI + " TEXT, " +
                    ItemsDbColumns.LOCATION_INVERTED_URI + " TEXT)";
            sqLiteDatabase.execSQL(qs);

            qs = "CREATE TABLE " + TABLE_NAME_FILES + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    FilesDbColumns.FILE_DATA + " TEXT);";
            sqLiteDatabase.execSQL(qs);


            qs = "CREATE TABLE " + TABLE_NAME_CLASSIFICATION_ANSWERS + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ClassificationAnswersDbColumns.SEQUENCE + " INTEGER DEFAULT 0, " +
                    ClassificationAnswersDbColumns.ITEM_ID + " INTEGER, " + /* Foreign key. See TABLE_NAME_CLASSIFICATIONS . _ID. */
                    ClassificationAnswersDbColumns.QUESTION_ID + " TEXT, " +
                    ClassificationAnswersDbColumns.ANSWER_ID + " TEXT)";
            sqLiteDatabase.execSQL(qs);

            qs = "CREATE TABLE " + TABLE_NAME_CLASSIFICATION_CHECKBOXES + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ClassificationCheckboxesDbColumns.SEQUENCE + " INTEGER DEFAULT 0, " +
                    ClassificationCheckboxesDbColumns.ITEM_ID + " INTEGER, " + /* Foreign key. See TABLE_NAME_CLASSIFICATIONS . _ID. */
                    ClassificationCheckboxesDbColumns.QUESTION_ID + " TEXT, " +
                    ClassificationCheckboxesDbColumns.CHECKBOX_ID + " TEXT)";
            sqLiteDatabase.execSQL(qs);
        }
    }

    public static class Subject {
        public String mId;
        public String mZooniverseId;
        public String mLocationStandard;
        public String mLocationThumbnail;
        public String mLocationInverted;
    }

    private class UriParts {
        public String itemId;
    }

    /*
    private File getFile(long id) {
        return new File(getContext().getExternalFilesDir(null), Long
                .toString(id)
                + ".glom");
    }
    */


    /**
     * Creates a new worker thread to carry out a RESTful network invocation.
     *
     * @param queryUri the complete URI that should be accessed by this request.
     */
    private Thread asyncQueryRequest(final String queryUri) {
        //synchronized (mRequestsInProgress) {
            UriRequestTask requestTask = null; //getRequestTask();
            if (requestTask == null) {
                requestTask = newQueryTask(queryUri);
                final Thread t = new Thread(requestTask);
                // allows other requests to run in parallel.
                t.start();
                return t;
            }
        //}

        return null;
    }

    UriRequestTask newQueryTask(final String url) {
        UriRequestTask requestTask;

        final HttpGet get = new HttpGet(url);
        ResponseHandler handler = new GalaxyZooResponseHandler(this);
        requestTask = new UriRequestTask("" /* TODO */, this, get,
                handler, getContext());

        //mRequestsInProgress.put(requestTag, requestTask);
        return requestTask;
    }
}
