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

    /**
     * Combine parts of a path
     * @param head - String
     * @param pieces - One or more additional parts to join
     * @return String Path
     */
    public static Path combinePath(String head, String... pieces) {
        return FileSystems.getDefault().getPath(head, pieces);
    }

    public static Path stringToPath(String path) {
        return FileSystems.getDefault().getPath(path);
    }

    /**
     * Returns the name (without extension) of a file
     * @param fileName string filename
     * @return String, fileName without extension
     */
    public static String stripFileExt(String fileName) {
        if (!fileName.contains(".")) {
            return fileName;
        }
        return fileName.split("\\.")[0];
    }

}
