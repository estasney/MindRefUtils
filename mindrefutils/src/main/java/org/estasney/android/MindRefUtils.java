package org.estasney.android;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Executors;


public class MindRefUtils {
    private static final String TAG = "mindrefutils";
    private final ListeningExecutorService service;
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    private final Context mContext;
    private final Uri externalStorageUri;
    private final Path appStoragePath;
    public final String externalStorageRoot;
    public final String appStorageRoot;

    /**
     * Constructor for MindRefUtils
     * @param externalStorageRoot - String representing the URI returned from user selecting document storage
     * @param appStorageRoot - String representing the Filepath to a folder that will mirror externalStorageRoot
     */
    public MindRefUtils(String externalStorageRoot, String appStorageRoot, Context context) {
        Uri externalStorageRootUri = Uri.parse(externalStorageRoot);
        this.mContext = context;
        this.externalStorageUri = MindRefFileUtils.contentToDocumentUri(externalStorageRootUri, this.mContext);
        this.appStoragePath = FileSystems.getDefault().getPath(appStorageRoot);
        this.service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(NUMBER_OF_CORES));
        this.externalStorageRoot = externalStorageRoot;
        this.appStorageRoot = appStorageRoot;
    }

    /**
     * Get Categories
     */
    public interface GetCategoriesCallback {
        void onComplete(String[] categories);
    }

    private GetCategoriesCallback getCategoriesCallback;
    private boolean haveGetCategoriesCallback = false;

    public void setGetCategoriesCallback(GetCategoriesCallback callback) {
        this.getCategoriesCallback = callback;
        this.haveGetCategoriesCallback = true;
    }

    // Copy Storage
    public interface CopyStorageCallback {
        // Called when copy storage is complete

        void onCopyStorageResult(boolean result);

        // Called each time a directory is finished copying (note discovery)
        void onNoteDiscoveryResult(String category, String imagePath, String[] notes);

    }

    private CopyStorageCallback storageCallback;
    private boolean haveStorageCallback = false;

    public void setStorageCallback(CopyStorageCallback callback) {
        this.storageCallback = callback;
        this.haveStorageCallback = true;
    }

    /**
     * Application specific - this method will only mirror Categories (directories) and a single image
     */
    public void getNoteCategories() throws IOException {
        Log.d(TAG, "getNoteCategories - Start");
        ContentResolver contentResolver = mContext.getContentResolver();
        MindRefFileUtils.ensureDirectoryExists(appStoragePath.toFile());

        ListenableFuture<String[]> task = service.submit(
                () -> {
                    ArrayList<String> result = GetCategoriesRunner.getCategories(externalStorageUri, appStoragePath.toFile(), contentResolver);
                    return result.toArray(new String[0]);
                }
        );

        Futures.addCallback(
                task,
                new FutureCallback<String[]>() {
                    public void onSuccess(String[] result) {
                        Log.d(TAG, "getNoteCategories - Finish");
                        if (haveGetCategoriesCallback) {
                            getCategoriesCallback.onComplete(result);
                        }
                    }

                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "getNotCategories - Failure " + t);
                    }
                }, service);
    }

    /**
     * Mirror External Storage to private App storage to allow working with files natively.
     * This is a slow operation
     * @throws IOException Thrown when the target path is invalid (not a directory)
     */

    public void copyToAppStorage() throws IOException {
        Log.d(TAG, "copyToAppStorage - Start");
        ContentResolver contentResolver = this.mContext.getContentResolver();
        File targetFile = this.appStoragePath.toFile();

        // Creating the app storage directory if it doesn't exist
        MindRefFileUtils.ensureDirectoryExists(targetFile);

        // Schedule a task
        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    CopyTaskRunner.mirrorDirectory(this.externalStorageUri, targetFile, contentResolver);
                    return true;
                }
        );
        // Callbacks
        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {
                    public void onSuccess(Boolean result) {
                        Log.d(TAG, "copyToAppStorage - Finish");
                        if (haveStorageCallback) {
                            storageCallback.onCopyStorageResult(true);
                        }
                    }
                    public void onFailure(@NonNull Throwable t) {
                        if (haveStorageCallback) {
                            storageCallback.onCopyStorageResult(false);
                        }
                    }
                }
                , service);

    }

    public void copyToExternalStorage( String sourcePath, String category, String name, String mimeType) throws IOException {
        Log.d(TAG, "copyToExternalStorage - Start");
        ContentResolver contentResolver = mContext.getContentResolver();


        // Find matching category
        MindRefFileData categoryChild = MindRefFileData.getChildDirectoryFromUri(this.externalStorageUri, category, contentResolver);
        if (categoryChild == null) {
            throw new IOException("category: " + category + " does not exist");
        }

        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    CopyTaskRunner.writeFileToExternal(MindRefFileUtils.stringToPath(sourcePath), name, mimeType, categoryChild, contentResolver);
                    Log.d(TAG, "CopyTaskRunner Complete");
                    return true;
                }
        );

        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        if (haveStorageCallback) {
                            Log.v(TAG, "CopyTaskRunner.writeFileToExternal - onComplete Callback");
                            storageCallback.onCopyStorageResult(result);
                        } else {
                            Log.i(TAG, "CopyTaskRunner.writeFileToExternal - No callback");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(TAG, t.toString());
                    }
                },
                service
        );

    }


}
