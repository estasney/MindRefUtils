package org.estasney.android;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;


public class MindRefUtils {
    private static final String TAG = "mindrefutils";
    private final ListeningExecutorService service;
    private static final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    public MindRefUtils() {
        this.service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(NUMBER_OF_CORES));
    }

    public interface CopyStorageCallback {
        // Called when copy to app storage is done
        void onCopyStorageResult(boolean result);
    }

    private CopyStorageCallback storageCallback;
    private boolean haveStorageCallback = false;

    /**
     * Set a callback that will receive a boolean when copy task is complete
     */
    public void setStorageCallback(CopyStorageCallback callback) {
        storageCallback = callback;
        haveStorageCallback = true;
        Log.v(TAG, "Added Storage Callback");
    }

    public void copyTaskOnSuccessCallback(boolean result) {
        Log.v(TAG, "copyTask Success");
        if (haveStorageCallback) {
            storageCallback.onCopyStorageResult(true);
        }
    }

    public void copyTaskOnFailure(@NonNull Throwable t) {
        Log.w(TAG, "copyTask Failure", t);

        if (haveStorageCallback) {
            storageCallback.onCopyStorageResult(false);
        }
    }

    private static Path combinePath(String p1, String p2) {
        return FileSystems.getDefault().getPath(p1, p2);
    }

    private static void createDirectoryIfNeeded(@NonNull File f) {
        if (!f.exists()) {
            Log.v(TAG, String.format("createDirectory %s", f));
            boolean b = f.mkdir();
        } else {
            Log.v(TAG, String.format("%s already exists", f));
        }
    }

    private static File constructMirrored(@NonNull DocumentFile src, File targetRoot) {
        if (src.isDirectory()) {
            // Check if corresponding target directory exists
            Path targetDirPath = combinePath(targetRoot.getPath(), src.getName());
            File targetDir = targetDirPath.toFile();
            createDirectoryIfNeeded(targetDir);
            return targetDir;
        } else {
            Path targetFilePath = combinePath(targetRoot.getPath(), src.getName());
            return targetFilePath.toFile();
        }
    }




    public static class CopyTaskRunner {

        public static String readTextFromUri(Uri uri, ContentResolver contentResolver) throws IOException {
            StringBuilder stringBuilder = new StringBuilder();
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(Objects.requireNonNull(inputStream)))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line);
                }
            }
            return stringBuilder.toString();
        }
        public static byte[] readBytesFromUri(Uri uri, ContentResolver contentResolver) throws IOException {
            InputStream inputStream = contentResolver.openInputStream(uri);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            int mark = inputStream.read();
            while (mark != -1) {
                outputStream.write(mark);
                mark = inputStream.read();
            }
            inputStream.close();
            return outputStream.toByteArray();
        }


        public static void writeTextToFile(File targetFile, String text) throws IOException {
            FileUtils.writeStringToFile(targetFile, text);
        }

        public static void writeBytesToFile(File targetFile, byte[] data) throws IOException {
            FileUtils.writeByteArrayToFile(targetFile, data);
        }


        public static void mirrorDirectory(@NonNull DocumentFile sourceFolder, File targetDir, ContentResolver contentResolver) throws IOException {
            Log.v(TAG, String.format("mirrorDirectory %s -> %s", sourceFolder.getUri().getPath(), targetDir.getPath()));
            DocumentFile[] childFiles = sourceFolder.listFiles();
            createDirectoryIfNeeded(targetDir);
            for (DocumentFile srcChild : childFiles) {
                if (srcChild.isDirectory()) {
                    Path targetChildDir = combinePath(targetDir.getPath(), srcChild.getName());
                    Log.v(TAG, String.format("Child is directory %s", targetChildDir.toString()));
                    mirrorDirectory(srcChild, targetChildDir.toFile(), contentResolver);
                } else {
                    File targetChild = constructMirrored(srcChild, targetDir);
                    // image/png, text/markdown
                    String srcType = srcChild.getType();
                    Log.v(TAG, String.format("%s is %s", srcChild.getUri().getPath(), srcType));
                    switch (Objects.requireNonNull(srcType)) {
                        case "text/markdown": {
                            String contents = readTextFromUri(srcChild.getUri(), contentResolver);
                            Log.v(TAG, "read text file");
                            writeTextToFile(targetChild, contents);
                        }
                        case "image/png": {
                            byte[] contents = readBytesFromUri(srcChild.getUri(), contentResolver);
                            Log.v(TAG, "read bytes from file");
                            writeBytesToFile(targetChild, contents);
                        }
                        default: {
                            Log.v(TAG, "unhandled srcType");
                        }
                    }

                }
            }
        }
    }

    /**
     * @param sourceFolder DocumentFile constructed from ACTION_OPEN_DOCUMENT_TREE Uri
     * @param targetPathS  String path to copy to. This should be one level higher. I.e. Copying
     *                     /documents/notes
     *                     /someapp/files/documents.
     *                     This path should exists
     * @throws IOException Thrown when the target path is invalid (not a directory)
     */

    public void copyToAppStorage(@NonNull DocumentFile sourceFolder, @NonNull String targetPathS, @NonNull ContentResolver contentResolver) throws IOException {
        Log.v(TAG, "copyToAppStorage Invoked");
        Path targetPath = FileSystems.getDefault().getPath(targetPathS, sourceFolder.getName());
        File targetFile = targetPath.toFile();
        if (!targetFile.exists()) {
            Log.v(TAG, String.format("Creating targetFile: %s", targetFile.getPath()));
            if (!targetFile.mkdir()) {
                throw new IOException(String.format("Unable to create directory %s", targetFile.getPath()));
            }
        }

        Log.v(TAG, String.format("copyToAppStorage %s -> %s", sourceFolder.getUri().getPath(), targetPathS));
        ListenableFuture<Boolean> task = service.submit(
                new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        CopyTaskRunner.mirrorDirectory(sourceFolder, targetFile, contentResolver);
                        return true;
                    }
                }
        );
        Futures.addCallback(
                task,
                new FutureCallback<Boolean>() {

                    public void onSuccess(Boolean result) {
                        copyTaskOnSuccessCallback(true);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        copyTaskOnFailure(t);
                    }
                }
                , service);
        Log.v(TAG, "Awaiting result");
    }
}
