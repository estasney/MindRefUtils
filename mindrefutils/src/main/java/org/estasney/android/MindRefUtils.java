package org.estasney.android;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.ContentResolver;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;


public class MindRefUtils {
    private static final String TAG = "mindrefutils";
    private final ListeningExecutorService service;
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    public MindRefUtils() {
        this.service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(NUMBER_OF_CORES));
    }

    private static Path combinePath(String p1, String p2) {
        return FileSystems.getDefault().getPath(p1, p2);
    }

    private static void createDirectoryIfNeeded(@NonNull File f) {
        if (!f.exists()) {
            Log.v(TAG, String.format("creating directory %s", f));
            boolean b = f.mkdir();
        }
    }

    private static Path constructMirroredPath(@NonNull DocumentFile src, Path targetRoot) {
        if (src.isDirectory()) {
            // Check if corresponding target directory exists
            Path targetDirPath = combinePath(targetRoot.toString(), src.getName());
            File targetDir = targetDirPath.toFile();
            createDirectoryIfNeeded(targetDir);
            return targetDirPath;
        } else {
            return combinePath(targetRoot.toString(), src.getName());
        }
    }

    // Get Categories
    public interface GetCategoriesCallback {
        void onComplete(String[] categories);
    }
    private GetCategoriesCallback getCategoriesCallback;
    private boolean haveGetCategoriesCallback = false;
    public void setGetCategoriesCallback(GetCategoriesCallback callback) {
        Log.d(TAG, "setGetCategoriesCallback");
        this.getCategoriesCallback = callback;
        this.haveGetCategoriesCallback = true;
    }
    public static class GetCategoriesRunner {
        public static ArrayList<String> getCategories(@NonNull DocumentFile srcFolder, File targetDir, ContentResolver contentResolver) throws IOException {
            DocumentFile[] childFiles = srcFolder.listFiles();
            ArrayList<String> results = new ArrayList<>();
            for (DocumentFile srcChild : childFiles) {
                if (srcChild.isDirectory()) {
                    // This is a category
                    String categoryName = srcChild.getName();
                    DocumentFile categoryImg = null;
                    Path targetChildDir = combinePath(targetDir.getPath(), categoryName);
                    File targetChildDirFile = targetChildDir.toFile();
                    for (DocumentFile categoryChild : srcChild.listFiles()) {
                        String ccMime = categoryChild.getType();
                        if (ccMime == null) {
                            continue;
                        }
                        if (ccMime.startsWith("image/")) {
                            categoryImg = categoryChild;
                            break;
                        }
                    }
                    // Copy basic folder structure and image if it exists
                    if (!targetChildDirFile.exists()) {
                        if (!targetChildDirFile.mkdir()) {
                            throw new IOException(String.format("Unable to create directory %s", targetChildDirFile.getPath()));
                        }
                    }
                    if (categoryImg != null) {
                        Path targetChildImgFile = constructMirroredPath(categoryImg, targetChildDirFile.toPath());
                        InputStream imgInputStream = contentResolver.openInputStream(categoryImg.getUri());
                        Files.copy(imgInputStream, targetChildImgFile, REPLACE_EXISTING);
                    }
                    results.add(categoryName);
                }
            }
            return results;
        }

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
    public static class CopyTaskRunner {

        public static void mirrorFile(DocumentFile srcFile, Path targetPath, ContentResolver contentResolver) throws IOException {
            File targetFile = targetPath.toFile();
            if (targetFile.exists()) {
                long srcMod = srcFile.lastModified();
                long tgtMod = targetFile.lastModified();
                if (srcMod > tgtMod) {
                    Log.v(TAG, String.format("srcFile : %s is newer than targetFile %s, by %d", srcFile.getName(), targetFile.getName(), srcMod - tgtMod));
                    InputStream inputStream = contentResolver.openInputStream(srcFile.getUri());
                    Files.copy(inputStream, targetPath, REPLACE_EXISTING, COPY_ATTRIBUTES);
                } else {
                    Log.v(TAG, String.format("targetFile : %s is current with srcFile %s", srcFile.getName(), targetFile.getName()));
                }
            } else {
                Log.d(TAG, "srcFile : %s not present");
                InputStream inputStream = contentResolver.openInputStream(srcFile.getUri());
                Files.copy(inputStream, targetPath, COPY_ATTRIBUTES);
            }
        }

        /**
         * @param sourceFolder    DocumentFile constructed from ACTION_OPEN_DOCUMENT_TREE Uri
         * @param targetDir       File path to copy to. This should be one level higher. I.e. Copying
         *                        /documents/notes
         *                        /someapp/files/documents.
         *                        This path should exist
         * @param contentResolver ContentResolver
         * @throws IOException Thrown when the target path is invalid (not a directory)
         */


        public static void mirrorDirectory(@NonNull DocumentFile sourceFolder, File targetDir, ContentResolver contentResolver) throws IOException {
            Log.d(TAG, String.format("mirrorDirectory %s -> %s", sourceFolder.getUri().getPath(), targetDir.getPath()));
            DocumentFile[] childFiles = sourceFolder.listFiles();

            for (DocumentFile srcChild : childFiles) {
                if (srcChild.isDirectory()) {
                    Path targetChildDir = combinePath(targetDir.getPath(), srcChild.getName());
                    Log.v(TAG, String.format("Child is directory %s", targetChildDir.toString()));
                    mirrorDirectory(srcChild, targetChildDir.toFile(), contentResolver);
                } else {
                    Path targetChild = constructMirroredPath(srcChild, targetDir.toPath());
                    // image/png, text/markdown
                    String srcType = srcChild.getType();
                    Log.v(TAG, String.format("%s is %s", srcChild.getUri().getPath(), srcType));
                    mirrorFile(srcChild, targetChild, contentResolver);

                }
            }
        }
    }

    public void getNoteCategories(@NonNull DocumentFile sourceFolder, @NonNull String targetPathS, @NonNull ContentResolver contentResolver) throws IOException {
        Log.d(TAG, "getNoteCategories");
        Path targetPath = FileSystems.getDefault().getPath(targetPathS);
        File targetFile = targetPath.toFile();
        if (!targetFile.exists()) {
            Log.v(TAG, String.format("Creating targetFile: %s", targetFile.getPath()));
            if (!targetFile.mkdir()) {
                throw new IOException(String.format("Unable to create directory %s", targetFile.getPath()));
            }
        }

        ListenableFuture<String[]> task = service.submit(
                () -> {
                    ArrayList<String> result = GetCategoriesRunner.getCategories(sourceFolder, targetFile, contentResolver);
                    Log.d(TAG, "GetCategoriesRunner.getCategories - Complete");
                    return result.toArray(new String[0]);
                }
        );

        Futures.addCallback(
                task,
                new FutureCallback<String[]>() {
                    @Override
                    public void onSuccess(String[] result) {
                        Log.d(TAG, "GetCategoriesRunner.getCategories - OnSuccess");
                        if (haveGetCategoriesCallback) {
                            Log.d(TAG, "GetCategoriesRunner.getCategories - onComplete Callback");
                            getCategoriesCallback.onComplete(result);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "GetCategoriesRunner.getCategories - onFailure");
                        Log.w(TAG, t.toString());
                    }
                }
                , service);
    }





    public void copyTaskOnSuccessCallback(boolean result) {
        Log.v(TAG, "copyTask Success");
        if (this.haveStorageCallback) {
            Log.d(TAG, "Calling storageCallback");
            storageCallback.onCopyStorageResult(true);
        } else {
            Log.i(TAG, "No callback registered for storageCallback!");
        }
    }

    public void copyTaskOnFailure(@NonNull Throwable t) {
        Log.w(TAG, "copyTask Failure", t);

        if (this.haveStorageCallback) {
            this.storageCallback.onCopyStorageResult(false);
        }
    }



    /**
     * @param sourceFolder DocumentFile constructed from ACTION_OPEN_DOCUMENT_TREE Uri
     * @param targetPathS  String path, contents of sourceFolder will be copied to it. Directory will be created if it does not exist.
     * @throws IOException Thrown when the target path is invalid (not a directory)
     */

    public void copyToAppStorage(@NonNull DocumentFile sourceFolder, @NonNull String targetPathS, @NonNull ContentResolver contentResolver) throws IOException {
        Log.v(TAG, "copyToAppStorage Invoked");
        Path targetPath = FileSystems.getDefault().getPath(targetPathS);
        File targetFile = targetPath.toFile();

        // Creating if not exists
        if (!targetFile.exists()) {
            Log.v(TAG, String.format("Creating targetFile: %s", targetFile.getPath()));
            if (!targetFile.mkdir()) {
                throw new IOException(String.format("Unable to create directory %s", targetFile.getPath()));
            }
        }

        Log.d(TAG, String.format("copyToAppStorage %s -> %s", sourceFolder.getUri().getPath(), targetPathS));

        // Schedule a task
        ListenableFuture<Boolean> task = service.submit(
                () -> {
                    CopyTaskRunner.mirrorDirectory(sourceFolder, targetFile, contentResolver);
                    Log.d(TAG, "CopyTaskRunner Complete");
                    return true;
                }
        );

        // Callbacks
        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {

                    public void onSuccess(Boolean result) {
                        Log.d(TAG, "CopyTaskRunner - onSuccess");
                        copyTaskOnSuccessCallback(true);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {

                        copyTaskOnFailure(t);
                    }
                }
                , service);
        Log.v(TAG, "copyToAppStorage - Future Scheduled");
    }


}
