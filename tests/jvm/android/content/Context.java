package android.content;

import java.io.File;

/** JVM test boundary only. Never included in the Android application's source set. */
public abstract class Context {
    public abstract File getFilesDir();
}
