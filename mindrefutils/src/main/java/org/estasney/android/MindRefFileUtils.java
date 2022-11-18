package org.estasney.android;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class MindRefFileUtils {

    /**
    When using ACTION_OPEN_... the Uri is a 'content' form. We are working with DocumentProvider
    and so need to convert it.
     */
    public static Uri contentToDocumentUri(Uri contentUri, Context context) {
        String contentDocumentId = DocumentsContract.getTreeDocumentId(contentUri);
        if (DocumentsContract.isDocumentUri(context, contentUri)) {
            contentDocumentId = DocumentsContract.getDocumentId(contentUri);
        }
        return DocumentsContract.buildDocumentUriUsingTree(contentUri, contentDocumentId);
    }

    /**
     * Checks if a directory exists. If not, creates the directory
     * @param dir File to check
     */
    public static void ensureDirectoryExists(File dir) throws IOException {
        if (dir.exists()) {
            return;
        }
        boolean success = dir.mkdirs();
        if (!success) {
            throw new IOException("Failed to create directory: "+dir);
        }
    }

    public static Path combinePath(String p1, String p2) {
        return FileSystems.getDefault().getPath(p1, p2);
    }

    public static Path stringToPath(String path) {
        return FileSystems.getDefault().getPath(path);
    }

}
