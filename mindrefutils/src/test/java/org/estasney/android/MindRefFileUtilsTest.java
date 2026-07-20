package org.estasney.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class MindRefFileUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void stripFileExt_removesExtension() {
        assertEquals("note", MindRefFileUtils.stripFileExt("note.md"));
    }

    @Test
    public void stripFileExt_returnsNameWithoutExtensionUnchanged() {
        assertEquals("README", MindRefFileUtils.stripFileExt("README"));
    }

    @Test
    public void combinePath_joinsPartsWithSeparator() {
        Path result = MindRefFileUtils.combinePath("notes", "topics", "gradle.md");
        assertEquals(String.join(File.separator, "notes", "topics", "gradle.md"), result.toString());
    }

    @Test
    public void stringToPath_roundTripsPlainString() {
        assertEquals("notes/topics", MindRefFileUtils.stringToPath("notes/topics").toString());
    }

    @Test
    public void ensureDirectoryExists_createsNestedDirectories() throws IOException {
        File target = new File(tempFolder.getRoot(), String.join(File.separator, "a", "b", "c"));

        MindRefFileUtils.ensureDirectoryExists(target);

        assertTrue(target.isDirectory());
    }

    @Test
    public void ensureDirectoryExists_acceptsExistingDirectory() throws IOException {
        File existing = tempFolder.newFolder("already-there");

        MindRefFileUtils.ensureDirectoryExists(existing);

        assertTrue(existing.isDirectory());
    }

    @Test
    public void ensureDirectoryExists_throwsWhenAFileBlocksThePath() throws IOException {
        File blocker = tempFolder.newFile("blocker");
        File target = new File(blocker, "child");

        assertThrows(IOException.class, () -> MindRefFileUtils.ensureDirectoryExists(target));
    }
}
