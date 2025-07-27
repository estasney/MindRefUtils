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
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.concurrent.Executors;


/** @noinspection unused*/
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
     *
     * @param externalStorageRoot - String representing the URI returned from user selecting document storage
     * @param appStorageRoot      - String representing the Filepath to a folder that will mirror externalStorageRoot
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

        void onComplete(int key);

        void onFailure(int key);
    }

    public void setMindRefCallback(MindRefUtilsCallback callback) {
        this.mindRefUtilsCallback = callback;
        this.haveMindRefUtilsCallback = true;
    }

    /**
     * Mirror External Storage to private App storage to allow working with files natively.
     * Newer Files in External Storage - Overwrite Older Files in App Storage
     * Files Present in External Storage - Write to App Storage
     * Files Present in App Storage, but not External Storage - No op
     * This is a slow operation
     *
     * @param key - Arbitrary int, will be passed to callback
     * @throws IOException Thrown when the target path is invalid (not a directory)
     */
    public void copyToAppStorage(int key) throws IOException {
        Log.d(TAG, "copyToAppStorage - Start - Operation Key: " + key);
        ContentResolver contentResolver = this.mContext.getContentResolver();
        Log.d(TAG, "copyToAppStorage - Got ContentResolver");
        File targetFile = this.appStoragePath.toFile();
        Log.d(TAG, "copyToAppStorage - Target File: " + targetFile.getAbsolutePath());

        // Creating the app storage directory if it doesn't exist
        Log.d(TAG, "copyToAppStorage - ensureDirectoryExists: " + targetFile.getAbsolutePath());
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
     *
     * @param sourcePath - Location of the app file
     * @param directory  - Directory to which it belongs
     * @param name       - Name of the file, without suffix
     * @param mimeType   - MimeType of sourcefile
     * @throws IOException - Thrown when the directory cannot be created
     */

    public void copyToExternalStorage(int key, String sourcePath, String directory, String name, String mimeType) throws IOException {
        Log.d(TAG, "copyToExternalStorage - Start " + sourcePath + ", " + directory + ", " + name + ", " + mimeType);
        ContentResolver contentResolver = mContext.getContentResolver();


        // Find matching directory or create it if it doesn't exist
        MindRefFileData directoryData = MindRefFileData.getChildDirectoryFromUri(this.externalStorageUri, directory, contentResolver);
        if (directoryData == null) {
            // Create the directory if it doesn't exist
            Log.d(TAG, "Directory does not exist, creating: " + directory);
            directoryData = createDirectory(directory, contentResolver);
        }

        // Use final variable for lambda
        final MindRefFileData directoryChild = directoryData;

        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    MindRefRunner.writeFileToExternal(MindRefFileUtils.stringToPath(sourcePath), name, mimeType, directoryChild, contentResolver);
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
     * Creates a directory in external storage
     *
     * @param directory       - Name of the directory to create
     * @param contentResolver - ContentResolver
     * @return MindRefFileData object representing the created directory
     * @throws IOException - Thrown when the directory cannot be created
     */
    private MindRefFileData createDirectory(String directory, ContentResolver contentResolver) throws IOException {
        Log.d(TAG, "createDirectory - Start " + directory);
        MindRefFileData sourceFolder = MindRefFileData.fromTreeUri(this.externalStorageUri);
        return sourceFolder.getOrMakeChild(contentResolver, directory, DocumentsContract.Document.MIME_TYPE_DIR);
    }


}
