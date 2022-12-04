package org.estasney.android;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Objects;

public class MindRefFileData {
    public final Uri uri;
    public final String documentId;
    public final String displayName;
    public final String mimeType;
    public final boolean isDirectory;
    public final long lastModified;
    private static final String TAG = "MindRefFileData";

    public MindRefFileData(Uri parentUri, String documentId, String displayName, String mimeType, long lastModified) {
        this.uri = DocumentsContract.buildDocumentUriUsingTree(parentUri, documentId);
        this.documentId = documentId;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.isDirectory = Objects.equals(this.mimeType, DocumentsContract.Document.MIME_TYPE_DIR);
        this.lastModified = lastModified;
    }

    public static MindRefFileData fromTreeUri(Uri treeUri) {
        String docId = DocumentsContract.getTreeDocumentId(treeUri);
        return new MindRefFileData(treeUri, docId, docId, DocumentsContract.Document.MIME_TYPE_DIR, 0L);
    }

    /**
     * Builds a Uri to query for children
     * @return Uri - Uri that can be used to query
     */
    public Uri getChildrenUri() {
        return DocumentsContract.buildChildDocumentsUriUsingTree(this.uri, DocumentsContract.getDocumentId(this.uri));
    }

    /**
     * Builds a Uri to query for children
     * @return Uri - Uri that can be used to query
     */
    public static Uri getChildrenUriFromUri(Uri parentUri) {
        return DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri));
    }

    /**
     * Use ContentResolver to query for children
     * @param contentResolver - ContentResolver
     * @return - MindRefFileData
     */
    public MindRefFileData[] getChildren(ContentResolver contentResolver) {
        if (!this.isDirectory) {
            throw new IllegalArgumentException(this.displayName + " is not a directory");
        }
        final Uri childrenUri = this.getChildrenUri();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        final ArrayList<MindRefFileData> fileData = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                null, null, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String childId = cursor.getString(0);
                    String childName = cursor.getString(1);
                    String childMime = cursor.getString(2);
                    long childLastMod = cursor.getLong(3);
                    MindRefFileData mf = new MindRefFileData(this.uri, childId, childName, childMime, childLastMod);
                    fileData.add(mf);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildren: " + e);
        }
        return fileData.toArray(new MindRefFileData[0]);
    }

    /**
     * Use ContentResolver to query for child directories
     * @param contentResolver - ContentResolver
     * @return - MindRefFileData
     */
    public MindRefFileData[] getChildDirectories(ContentResolver contentResolver) {
        if (!this.isDirectory) {
            throw new IllegalArgumentException(this.displayName + " is not a directory");
        }
        final Uri childrenUri = this.getChildrenUri();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        final ArrayList<MindRefFileData> fileData = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                DocumentsContract.Document.COLUMN_MIME_TYPE + "=?", new String[]{DocumentsContract.Document.MIME_TYPE_DIR}, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String childMime = cursor.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(childMime)) {
                        continue;
                    }
                    String childId = cursor.getString(0);
                    String childName = cursor.getString(1);
                    long childLastMod = cursor.getLong(3);
                    MindRefFileData mf = new MindRefFileData(this.uri, childId, childName, childMime, childLastMod);
                    fileData.add(mf);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildDirectories: " + e);
        }
        return fileData.toArray(new MindRefFileData[0]);
    }

    /**
     * Use ContentResolver to query for child directory matching name
     * @param contentResolver - ContentResolver
     * @param childName - String
     * @return - MindRefFileData
     */
    public MindRefFileData getChildDirectory(ContentResolver contentResolver, String childName) {
        if (!this.isDirectory) {
            throw new IllegalArgumentException(this.displayName + " is not a directory");
        }
        final Uri childrenUri = this.getChildrenUri();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};

        MindRefFileData matchedFile = null;

        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                DocumentsContract.Document.COLUMN_MIME_TYPE + "=?", new String[]{DocumentsContract.Document.MIME_TYPE_DIR}, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext() && matchedFile == null) {
                    String childMime = cursor.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(childMime)) {
                        continue;
                    }
                    String childName1 = cursor.getString(1);
                    if (!childName1.equals(childName)) {
                        continue;
                    }
                    String childId = cursor.getString(0);
                    long childLastMod = cursor.getLong(3);
                    matchedFile = new MindRefFileData(this.uri, childId, childName, childMime, childLastMod);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildDirectory: " + e);
        }
        return matchedFile;

    }

    /**
     * Use ContentResolver to query for child directory matching name
     * @param contentResolver - ContentResolver
     * @param childName - String
     * @return - MindRefFileData
     */
    public static @Nullable MindRefFileData getChildDirectoryFromUri(Uri parentUri, String childName, ContentResolver contentResolver) {

        final Uri childrenUri = MindRefFileData.getChildrenUriFromUri(parentUri);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};

        MindRefFileData matchedFile = null;

        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                DocumentsContract.Document.COLUMN_MIME_TYPE + "=?", new String[]{DocumentsContract.Document.MIME_TYPE_DIR}, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext() && matchedFile == null) {
                    String childMime = cursor.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(childMime)) {
                        continue;
                    }
                    String childName1 = cursor.getString(1);
                    if (!childName1.equals(childName)) {
                        continue;
                    }
                    String childId = cursor.getString(0);
                    long childLastMod = cursor.getLong(3);
                    matchedFile = new MindRefFileData(parentUri, childId, childName, childMime, childLastMod);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildDirectory: " + e);
        }
        return matchedFile;

    }

    /**
     * Use ContentResolver to query for child directories from a Uri
     * @param contentResolver - ContentResolver
     * @return - MindRefFileData
     */
    public static MindRefFileData[] getChildDirectoriesFromUri(Uri parentUri, ContentResolver contentResolver) {

        final Uri childrenUri = MindRefFileData.getChildrenUriFromUri(parentUri);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        final ArrayList<MindRefFileData> fileData = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                DocumentsContract.Document.COLUMN_MIME_TYPE + "=?", new String[]{DocumentsContract.Document.MIME_TYPE_DIR}, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String childMime = cursor.getString(2);
                    if (!DocumentsContract.Document.MIME_TYPE_DIR.equals(childMime)) {
                        continue;
                    }
                    String childId = cursor.getString(0);
                    String childName = cursor.getString(1);
                    long childLastMod = cursor.getLong(3);
                    MindRefFileData mf = new MindRefFileData(parentUri, childId, childName, childMime, childLastMod);
                    fileData.add(mf);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildDirectoriesFromUri: " + e);
        }
        return fileData.toArray(new MindRefFileData[0]);
    }

    /**
     * Use ContentResolver to query for the first child that matches image mime type
     * @param contentResolver - ContentResolver
     * @return - MindRefFileData, null if not found
     */
    @Nullable
    public MindRefFileData getFirstChildImage(ContentResolver contentResolver) {
        if (!this.isDirectory) {
            throw new IllegalArgumentException(this.displayName + " is not a directory");
        }
        final Uri childrenUri = this.getChildrenUri();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        MindRefFileData matchedFile = null;
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                null, null, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext() && matchedFile == null) {
                    String childId = cursor.getString(0);
                    String childName = cursor.getString(1);
                    String childMime = cursor.getString(2);
                    long childLastMod = cursor.getLong(3);
                    matchedFile = new MindRefFileData(this.uri, childId, childName, childMime, childLastMod);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildren: " + e);
        }
        return matchedFile;
    }



    /**
     * Use ContentResolver to query for the first child that matches image mime type
     * @param contentResolver - ContentResolver
     * @return - MindRefFileData, null if not found
     */
    @Nullable
    public static MindRefFileData getFirstChildImageFromUri(Uri parentUri, ContentResolver contentResolver) {
        final Uri childrenUri = MindRefFileData.getChildrenUriFromUri(parentUri);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        MindRefFileData matchedFile = null;
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                null, null, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext() && matchedFile == null) {
                    String childMime = cursor.getString(2);
                    if (!childMime.startsWith("image")) {
                        continue;
                    }
                    String childId = cursor.getString(0);
                    String childName = cursor.getString(1);
                    long childLastMod = cursor.getLong(3);
                    matchedFile = new MindRefFileData(parentUri, childId, childName, childMime, childLastMod);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildren: " + e);
        }
        return matchedFile;
    }

    /**
     * Use ContentResolver to query for the first child that matches document name and mime type
     * @param contentResolver - ContentResolver
     * @param childName - DocumentName of Child
     * @param childMime - Mime Type of Child
     * @return - MindRefFileData, null if not found
     */

    public MindRefFileData getOrMakeChild(ContentResolver contentResolver, String childName, String childMime) throws FileNotFoundException {
        if (!this.isDirectory) {
            throw new IllegalArgumentException(this.displayName + " is not a directory");
        }
        final Uri childrenUri = this.getChildrenUri();
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        MindRefFileData matchedFile = null;
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                null, null, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext() && matchedFile == null) {
                    // childName may have an extension
                    String childName1 = cursor.getString(1);
                    childName1 = MindRefFileUtils.stripFileExt(childName1);
                    if (!Objects.equals(childName1, childName)) {
                        continue;
                    }
                    String childMime1 = cursor.getString(2);
                    if (!Objects.equals(childMime1, childMime)) {
                        continue;
                    }
                    String childId = cursor.getString(0);
                    long childLastMod = cursor.getLong(3);
                    matchedFile = new MindRefFileData(this.uri, childId, childName1, childMime1, childLastMod);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getOrMakeChild: " + e);
        }
        if (matchedFile != null) {
            Log.d(TAG, "getOrMakeChild: found match");
            return matchedFile;
        }
        Uri childTargetUri = DocumentsContract.createDocument(contentResolver, this.uri, childMime, childName);
        matchedFile = new MindRefFileData(this.uri, DocumentsContract.getDocumentId(childTargetUri), childName, childMime, 0);
        Log.d(TAG, "getOrMakeChild: Match Not Found, Created New Document : " + childTargetUri);
        return matchedFile;
    }


    /**
     * Use ContentResolver to query for children from a Uri, statically
     * @param parentUri - Uri
     * @param contentResolver - ContentResolver
     * @return - MindRefFileData
     */
    public static MindRefFileData[] getChildrenFromUri(Uri parentUri, ContentResolver contentResolver) {

        final Uri childrenUri = getChildrenUriFromUri(parentUri);
        String[] projection = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED};
        final ArrayList<MindRefFileData> fileData = new ArrayList<>();
        try (Cursor cursor = contentResolver.query(childrenUri,
                projection,
                null, null, null
        )) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String childId = cursor.getString(0);
                    String childName = cursor.getString(1);
                    String childMime = cursor.getString(2);
                    long childLastMod = cursor.getLong(3);
                    MindRefFileData mf = new MindRefFileData(parentUri, childId, childName, childMime, childLastMod);
                    fileData.add(mf);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed getChildren: " + e);
        }
        return fileData.toArray(new MindRefFileData[0]);
    }


}
