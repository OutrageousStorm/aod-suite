package com.outrageousstorm.aodsuite.shizuku;

interface IShellService {
    /** Write a secure setting directly (runs as shell uid — has WRITE_SECURE_SETTINGS) */
    boolean putSecure(String key, String value);

    /** Write a system setting */
    boolean putSystem(String key, String value);

    /** Write a global setting */
    boolean putGlobal(String key, String value);

    /** Read a secure setting */
    String getSecure(String key);

    /** Read a system setting */
    String getSystem(String key);

    /** Fallback: run a shell command (for things like 'cmd' / 'am broadcast') */
    String exec(String command);
}
