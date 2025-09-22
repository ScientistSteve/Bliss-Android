package net.kdt.pojavlaunch;

import android.util.Log;

import androidx.annotation.Keep;

import java.util.ArrayList;

/** Singleton class made to log on one file
 * The singleton part can be removed but will require more implementation from the end-dev
 */
@Keep
public class Logger {
    private static ArrayList<eventLogListener> logListeners;
    private static boolean nativeLogListenerSet = false;

    /** Print the text to the log file if not censored */
    public static native void appendToLog(String text);


    /** Reset the log file, effectively erasing any previous logs */
    public static native void begin(String logFilePath);

    /** Add a listener for the logfile, ask the native side for a listener if needed */
    public static void addLogListener(eventLogListener logListeners) {
        if (Logger.logListeners == null) Logger.logListeners = new ArrayList<>();
        Logger.logListeners.add(logListeners);
        if (Logger.nativeLogListenerSet) return;

        setLogListener(text -> {
            for (Logger.eventLogListener logListener: Logger.logListeners) {
                logListener.onEventLogged(text);
            }
        });
        Logger.nativeLogListenerSet = true;
    }
    /** Remove a listener for the logfile, unset the native listener if no listeners left */
    public static void removeLogListener(eventLogListener logListener) {
        if (Logger.logListeners == null) return;
        Logger.logListeners.remove(logListener);
        if (Logger.logListeners.isEmpty()){
            // Makes the JNI code be able to skip expensive logger callbacks
            // NOTE: was tested by rapidly smashing the log on/off button, no sync issues found :)
            setLogListener(null);
            Logger.nativeLogListenerSet = false;
        }
    }

    /** Small listener for anything listening to the log
     *  Performs double duty as being the interface for java listeners and the native callback
     */
    @Keep
    public interface eventLogListener {
        void onEventLogged(String text);
    }

    /** Link a log listener to the logger */
    private static native void setLogListener(eventLogListener logListener);
}
