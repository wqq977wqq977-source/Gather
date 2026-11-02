package android.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * A filesystem test double for WealthStore's Android boundary, not a framework
 * implementation or an Android durability test. A .new directory injects the
 * same startWrite IOException used by the device-side regression tests.
 */
public final class AtomicFile {
    private final File base;
    private final File pending;

    public AtomicFile(File base) {
        this.base = base;
        this.pending = new File(base.getPath() + ".new");
    }

    public File getBaseFile() { return base; }

    public FileInputStream openRead() throws IOException {
        return new FileInputStream(base);
    }

    public FileOutputStream startWrite() throws IOException {
        File parent = base.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create test directory");
        return new FileOutputStream(pending);
    }

    public void finishWrite(FileOutputStream output) {
        try {
            output.close();
            Files.move(pending.toPath(), base.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot publish test ledger", failure);
        }
    }

    public void failWrite(FileOutputStream output) {
        try { output.close(); } catch (IOException ignored) { }
        if (pending.isFile() && !pending.delete()) throw new IllegalStateException("Cannot discard test write");
    }
}
