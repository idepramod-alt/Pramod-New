package com.google.firebase.database.logging;

import com.google.firebase.database.logging.Logger;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/* loaded from: classes.dex */
public class DefaultLogger implements Logger {
    private final Set<String> enabledComponents;
    private final Logger.Level minLevel;

    public DefaultLogger(Logger.Level level, List<String> enabledComponents) {
        if (enabledComponents != null) {
            this.enabledComponents = new HashSet(enabledComponents);
        } else {
            this.enabledComponents = null;
        }
        this.minLevel = level;
    }

    @Override // com.google.firebase.database.logging.Logger
    public Logger.Level getLogLevel() {
        return this.minLevel;
    }

    @Override // com.google.firebase.database.logging.Logger
    public void onLogMessage(Logger.Level level, String tag, String message, long msTimestamp) {
        if (shouldLog(level, tag)) {
            String toLog = buildLogMessage(level, tag, message, msTimestamp);
            switch (level) {
                case ERROR:
                    error(tag, toLog);
                    return;
                case WARN:
                    warn(tag, toLog);
                    return;
                case INFO:
                    info(tag, toLog);
                    return;
                case DEBUG:
                    debug(tag, toLog);
                    return;
                default:
                    throw new RuntimeException("Should not reach here!");
            }
        }
    }

    protected String buildLogMessage(Logger.Level level, String tag, String message, long msTimestamp) {
        Date now = new Date(msTimestamp);
        return now.toString() + " [" + level + "] " + tag + ": " + message;
    }

    protected void error(String tag, String toLog) {
        System.err.println(toLog);
    }

    protected void warn(String tag, String toLog) {
        System.out.println(toLog);
    }

    protected void info(String tag, String toLog) {
        System.out.println(toLog);
    }

    protected void debug(String tag, String toLog) {
        System.out.println(toLog);
    }

    protected boolean shouldLog(Logger.Level level, String tag) {
        return level.ordinal() >= this.minLevel.ordinal() && (this.enabledComponents == null || level.ordinal() > Logger.Level.DEBUG.ordinal() || this.enabledComponents.contains(tag));
    }
}
