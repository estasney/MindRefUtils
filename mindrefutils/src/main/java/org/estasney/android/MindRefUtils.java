package org.estasney.android;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.FileNotFoundException;
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
    private MindRefUtilsCallback mindRefUtilsCallback;
    public boolean haveMindRefUtilsCallback = false;

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
     * Generic Callback
     */
    public interface MindRefUtilsCallback {
        // Create Category
        void onComplete(int key, String category);
        // Get Categories
        void onComplete(int key, String[] category);
        // Copy Storage
        void onComplete(int key);
        void onFailure(int key);

    }
    public void setMindRefCallback(MindRefUtilsCallback callback) {
        this.mindRefUtilsCallback = callback;
        this.haveMindRefUtilsCallback = true;
    }



    /**
     * Application specific - this method will only mirror Categories (directories) and a single image
     */
    public void getNoteCategories(int key) throws IOException {
        Log.d(TAG, "getNoteCategories - Start : " + key);
        ContentResolver contentResolver = mContext.getContentResolver();
        MindRefFileUtils.ensureDirectoryExists(appStoragePath.toFile());

        ListenableFuture<MindRefContainers.TupleTwo<Integer, String[]>> task = service.submit(
                () -> {
                    ArrayList<String> result = MindRefCategoryRunner.getCategories(externalStorageUri, appStoragePath.toFile(), contentResolver);
                    return new MindRefContainers.TupleTwo<>(key, result.toArray(new String[0]));
                }
        );

        Futures.addCallback(
                task,
                new FutureCallback<MindRefContainers.TupleTwo<Integer, String[]>>() {
                    public void onSuccess(MindRefContainers.TupleTwo result) {
                        Log.d(TAG, "getNoteCategories - Finish");
                        if (haveMindRefUtilsCallback) {
                            int key = (int) result.getX();
                            String[] y = (String[]) result.getY();
                            mindRefUtilsCallback.onComplete(key, y);
                        } else {
                            Log.i(TAG, "getNoteCategories - No callback found");
                        }
                    }

                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "getNotCategories - Failure " + t);
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onFailure(key);
                        }
                    }
                }, service);
    }

    /**
     * Given a single URI, this method will directly copy the user provided file URI to the managed storage
     * This method is intended to be used for copying a single image file.
     * @param key - Arbitrary key to be returned in callback
     * @param sourceUri - URI of the file to be copied
     * @param directoryName - Name of the directory to copy the file to
     */

    public void copyToManagedExternal(int key, Uri sourceUri, String directoryName) throws FileNotFoundException {
        Log.d(TAG, "copyToManagedExternal - Start");
        ContentResolver contentResolver = mContext.getContentResolver();
        MindRefFileData srcRoot = MindRefFileData.fromTreeUri(this.externalStorageUri);
        MindRefFileData srcFolder = srcRoot.getOrMakeChild(contentResolver, directoryName, DocumentsContract.Document.MIME_TYPE_DIR);

        // Create a new task to copy the file
        ListenableFuture<Uri> task  = service.submit(
                () -> MindRefRunner.copyExternalFileToExternalDirectory(sourceUri, directoryName, srcFolder, contentResolver)
        );
        Futures.addCallback(
                task,
                new FutureCallback<Uri>() {
                    public void onSuccess(Uri result) {
                        Log.d(TAG, "copyToManagedExternal - Finish");
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onComplete(key);
                        } else {
                            Log.i(TAG, "copyToManagedExternal - No callback found");
                        }
                    }

                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "copyToManagedExternal - Failure " + t);
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onFailure(key);
                        }
                    }
                }, service);

    }

    /**
     * Mirror External Storage to private App storage to allow working with files natively.
     * Newer Files in External Storage - Overwrite Older Files in App Storage
     * Files Present in External Storage - Write to App Storage
     * Files Present in App Storage, but not External Storage - Remove from App Storage
     * This is a slow operation
     * @param key - Arbitrary int, will be passed to callback
     * @throws IOException Thrown when the target path is invalid (not a directory)
     */
    public void copyToAppStorage(int key) throws IOException {
        Log.d(TAG, "copyToAppStorage - Start");
        ContentResolver contentResolver = this.mContext.getContentResolver();
        File targetFile = this.appStoragePath.toFile();

        // Creating the app storage directory if it doesn't exist
        MindRefFileUtils.ensureDirectoryExists(targetFile);

        // Schedule a task
        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    MindRefRunner.mirrorDirectory(this.externalStorageUri, targetFile, contentResolver);
                    return true;
                }
        );
        // Callbacks
        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {
                    public void onSuccess(Boolean flag) {
                        Log.d(TAG, "copyToAppStorage - Finish");
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onComplete(key);
                        }
                    }
                    public void onFailure(@NonNull Throwable t) {
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onFailure(key);
                        }
                    }
                }
                , service);

    }



    /**
     * Given a file from App Storage, Persist it to External Storage using DocumentProvider
     * If the file does not exist in External Storage, it will be created.
     * This function calls CopyTaskRunner in a separate thread
     * @param sourcePath - Location of the app file
     * @param category - Category to which it belongs (directory)
     * @param name - Name of the file, without suffix
     * @param mimeType - MimeType of sourcefile
     * @throws IOException - Thrown when the category does not exist
     */

    public void copyToExternalStorage( int key, String sourcePath, String category, String name, String mimeType) throws IOException {
        Log.d(TAG, "copyToExternalStorage - Start " + sourcePath + ", " + category + ", " + name + ", " + mimeType);
        ContentResolver contentResolver = mContext.getContentResolver();


        // Find matching category
        MindRefFileData categoryChild = MindRefFileData.getChildDirectoryFromUri(this.externalStorageUri, category, contentResolver);
        if (categoryChild == null) {
            throw new IOException("Category: " + category + " does not exist");
        }

        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    MindRefRunner.writeFileToExternal(MindRefFileUtils.stringToPath(sourcePath), name, mimeType, categoryChild, contentResolver);
                    return true;
                }
        );

        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        Log.v(TAG, "copyToExternalStorage - Finish");
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onComplete(key);
                        } else {
                            Log.i(TAG, "copyToExternalStorage - No Callback Registered");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(TAG, t.toString());
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onFailure(key);
                        }
                    }
                },
                service
        );

    }



    /**
     * Given a Category (Directory), Create it in External Storage
     * This function calls MindRefCategoryRunner in a separate thread
     * Result is passed to CategoryActionCallback
     * @param category - Category to which it belongs (directory)
     */

    public void createCategory( int key, String category) {
        Log.d(TAG, "createCategory - start");
        ContentResolver contentResolver = mContext.getContentResolver();

        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    try {
                        MindRefCategoryRunner.createCategory(this.externalStorageUri, category, contentResolver);
                    } catch (IOException e) {
                        Log.w(TAG, "createCategory - failed", e);
                        return false;
                    }
                    return true;
                }
        );

        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {
                        Log.d(TAG, "createCategory - Finish");
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onComplete(key, category);
                        } else {
                            Log.i(TAG, "copyToExternalStorage - No Callback Registered");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(TAG, t.toString());
                        if (haveMindRefUtilsCallback) {
                            mindRefUtilsCallback.onFailure(key);
                        } else {
                            Log.i(TAG, "copyToExternalStorage - No Callback Registered");
                        }
                    }
                },
                service
        );

    }


}
