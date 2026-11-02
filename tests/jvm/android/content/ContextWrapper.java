package android.content;

import java.io.File;

/** Minimal filesystem context used by the isolated ledger tests. */
public class ContextWrapper extends Context {
    private final Context base;

    public ContextWrapper(Context base) { this.base = base; }

    @Override public File getFilesDir() { return base.getFilesDir(); }
}
