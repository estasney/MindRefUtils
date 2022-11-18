package org.estasney.android;

import static org.estasney.android.MindRefFileUtils.combinePath;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class CopyTaskRunner {

    private static final String TAG = "mindrefutils";

    public static void mirrorFile(MindRefFileData srcFile, Path targetPath, ContentResolver contentResolver) throws IOException {
        File targetFile = targetPath.toFile();
        if (targetFile.exists()) {
            long srcMod = srcFile.lastModified;
            long tgtMod = targetFile.lastModified();
            if (srcMod > tgtMod) {
                InputStream inputStream = contentResolver.openInputStream(srcFile.uri);
                Files.copy(inputStream, targetPath, REPLACE_EXISTING);
            }
        } else {
            InputStream inputStream = contentResolver.openInputStream(srcFile.uri);
            Files.copy(inputStream, targetPath);
        }
    }

    /**
     * @param sourceFolderUri Uri constructed from ACTION_OPEN_DOCUMENT_TREE - Normalized to Document
     * @param targetDir       File path to copy to. This should be one level higher. I.e. Copying
     *                        /documents/notes
     *                        /someapp/files/documents.
     *                        This path should exist
     * @param contentResolver ContentResolver
     * @throws IOException Thrown when the target path is invalid (not a directory)
     */


    public static void mirrorDirectory(Uri sourceFolderUri, File targetDir, ContentResolver contentResolver) throws IOException {

        MindRefFileData[] fileData = MindRefFileData.getChildrenFromUri(sourceFolderUri, contentResolver);
        for (MindRefFileData srcChild : fileData) {
            if (srcChild.isDirectory) {
                Path targetChildDir = combinePath(targetDir.getPath(), srcChild.displayName);
                mirrorDirectory(srcChild.uri, targetChildDir.toFile(), contentResolver);
            } else {
                Path targetChild = combinePath(targetDir.toString(), srcChild.displayName);
                mirrorFile(srcChild, targetChild, contentResolver);
            }
        }

    }

    public static void writeFileToExternal(Path sourcePath, String name, String mimeType, MindRefFileData externalDir,
                                           ContentResolver contentResolver) throws IOException {
        // We need a URI for a category so we query the root
        Uri targetUri;
        try {
            targetUri = DocumentsContract.createDocument(contentResolver, externalDir.uri, mimeType, name);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Failed to create new document at " + externalDir.uri, e);
            return;
        }
        if (targetUri == null) {
            Log.w(TAG, "Failed to create new document at " + externalDir.uri + " with name: " + name );
        }

        // Read source document
        byte[] srcData = Files.readAllBytes(sourcePath);

        // Now we have URI, open file descriptor and copy
        ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(targetUri, "w");
        FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
        fileOutputStream.write(srcData);
        fileOutputStream.close();
        pfd.close();
    }
}
