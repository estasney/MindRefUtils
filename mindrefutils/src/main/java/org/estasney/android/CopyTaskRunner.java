package org.estasney.android;

import static org.estasney.android.MindRefFileUtils.combinePath;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.stream.Stream;

public class CopyTaskRunner {

    private static final String TAG = "mindrefutils";

    public static void mirrorFile(MindRefFileData srcFile, File targetFile, ContentResolver contentResolver) throws IOException {
        Path targetPath = targetFile.toPath();
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
        // Gather targetDir Children - if not present in sourceFolder, they are deleted
        ArrayDeque<Path> targetDirPathDeque = new ArrayDeque<>();
        Stream<Path> targetDirFiles = Files.list(targetDir.toPath()).filter(t -> t.toFile().isFile());
        targetDirFiles.forEach(targetDirPathDeque::add);


        for (MindRefFileData srcChild : fileData) {
            if (srcChild.isDirectory) {
                Path targetChildDir = combinePath(targetDir.getPath(), srcChild.displayName);
                mirrorDirectory(srcChild.uri, targetChildDir.toFile(), contentResolver);
            } else {
                Path targetChild = combinePath(targetDir.toString(), srcChild.displayName);
                mirrorFile(srcChild, targetChild.toFile(), contentResolver);
                targetDirPathDeque.remove(targetChild);
            }
        }

        // Remove any children
        while (!targetDirPathDeque.isEmpty()) {
            Path hangingChildPath = targetDirPathDeque.pop();
            Log.d(TAG, "Removing: " + hangingChildPath);
            Files.delete(hangingChildPath);
        }


    }

    /**
     * Given a file from App Storage, Persist it to External Storage using DocumentProvider
     * If the file does not exist in External Storage, it will be created.
     * @param sourcePath - Location of the app file
     * @param name - Name of the file, without suffix
     * @param mimeType - MimeType of sourcefile
    * @param externalDir - Category folder to save in External Storage
     * @throws IOException - Thrown when the category does not exist
     */

    public static void writeFileToExternal(Path sourcePath, String name, String mimeType, MindRefFileData externalDir,
                                           ContentResolver contentResolver) throws IOException {
        // We need a URI for a category so we query the root
        MindRefFileData externalTarget = externalDir.getOrMakeChild(contentResolver, name, mimeType);

        // Read source document
        byte[] srcData = Files.readAllBytes(sourcePath);

        // Now we have URI, open file descriptor and copy
        ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(externalTarget.uri, "w");
        FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
        fileOutputStream.write(srcData);
        fileOutputStream.close();
        pfd.close();
    }
}
