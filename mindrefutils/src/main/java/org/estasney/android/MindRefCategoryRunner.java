package org.estasney.android;

import static org.estasney.android.MindRefFileUtils.combinePath;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class MindRefCategoryRunner {
    private static final String TAG = "mindrefutils";
    private static void copyCategoryImage(Uri categoryUri, File targetDir, ContentResolver contentResolver) throws IOException {
        // Query if category has an image - if so copy to target

        MindRefFileData childImage = MindRefFileData.getFirstChildImageFromUri(categoryUri, contentResolver);

        // Build the URI for The Child Image
        if (childImage == null) {
            Log.d(TAG, "Empty query for category image");
            return;
        }

        final Path targetImagePath = combinePath(targetDir.toString(), childImage.displayName);
        final File targetImageFile = targetImagePath.toFile();
        if (!targetImageFile.exists()) {
            InputStream inputStream = contentResolver.openInputStream(childImage.uri);
            Files.copy(inputStream, targetImagePath);
        }
    }

    public static ArrayList<String> getCategories(Uri srcUri, File targetDir, ContentResolver contentResolver) throws IOException {

        final ArrayList<String> categoryNames = new ArrayList<>();
        final MindRefFileData[] categoryFolders = MindRefFileData.getChildDirectoriesFromUri(srcUri, contentResolver);

        // Now we have children uri, we want to query each for an image
        for (MindRefFileData srcChild : categoryFolders) {
            Path tgtPath = combinePath(targetDir.getPath(), srcChild.displayName);
            final File tgtFile = tgtPath.toFile();
            MindRefFileUtils.ensureDirectoryExists(tgtFile);
            copyCategoryImage(srcChild.uri, tgtPath.toFile(), contentResolver);
            categoryNames.add(srcChild.displayName);
            Log.v(TAG, "Found Category: " + srcChild.displayName);
        }
        return categoryNames;
    }

    /**
     * @param sourceFolderUri - This is the Uri of the External Storage Document Tree that the user
     *                        has selected for the app to use.
     * @param category        - The name of the directory to create
     * @param contentResolver - ContentResolver
     * @throws IOException Thrown when the target directory cannot be created
     */

    public static void createCategory(Uri sourceFolderUri, String category, ContentResolver contentResolver) throws IOException {
        MindRefFileData sourceFolder = MindRefFileData.fromTreeUri(sourceFolderUri);
        sourceFolder.getOrMakeChild(contentResolver, category, DocumentsContract.Document.MIME_TYPE_DIR);
    }

}
