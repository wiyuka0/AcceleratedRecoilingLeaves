package com.wiyuka.acceleratedrecoiling.natives;

import java.util.logging.Level;
import java.util.logging.Logger;

public class NativeInterface {

    // 引入 JUL Logger
    private static final Logger LOGGER = Logger.getLogger(NativeInterface.class.getName());

    private static INativeBackend backend;
    private static boolean isInitialized = false;

    public static void initialize() {
        if (isInitialized) return;
        INativeBackend backend1 = getBackend();
//        INativeBackend backend1 = new JavaBackend();
//        backend1.initialize();
        LOGGER.info("Selected backend: " + backend1.getName());
        backend = backend1;
    }

    public static String getBackendName() {
        if(backend == null) return "None";
        return backend.getName();
    }

    private static INativeBackend getBackend() {
        int javaVersion = Runtime.version().feature();
        // JUL 使用 {0} 作为第一个参数的占位符
        LOGGER.log(Level.INFO, "Detected Java Version: {0}", javaVersion);

        // 1. 尝试 FFM
        if (javaVersion >= 21) {
            try {
                LOGGER.info("Attempting to load FFM backend...");
                Class<?> ffmClass = Class.forName("com.wiyuka.acceleratedrecoiling.natives.FFMBackend");
                INativeBackend ffmInstance = (INativeBackend) ffmClass.getDeclaredConstructor().newInstance();

                ffmInstance.initialize();
                return ffmInstance;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "FFM backend failed to load. Reason: {0}", t.getMessage());
            }
        }

        try {
            LOGGER.info("Attempting to load JNI backend...");
            INativeBackend jniInstance = new JNIBackend();

            jniInstance.initialize();
            return jniInstance;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "JNI backend failed to load. Reason: {0}", t.getMessage());
        }

        try {
            LOGGER.info("Falling back to Pure Java backend...");
            INativeBackend javaInstance = new JavaBackend();

            javaInstance.initialize();
            return javaInstance;
        } catch (Throwable t) {
            // JUL 对应的 error 级别是 SEVERE
            LOGGER.log(Level.SEVERE, "CRITICAL: All backends failed to load!", t);
            throw new IllegalStateException("Failed to initialize any AcceleratedRecoiling backend", t);
        }
    }

    public static void applyConfig() {
        if (backend != null) {
            backend.applyConfig();
        }
    }

    public static void destroy() {
        if (backend != null) {
            backend.destroy();
            backend = null;
        }
        isInitialized = false;
    }

    public static PushResult push(double[] locations, double[] aabb, int[] resultSizeOut) {
        if (backend == null) {
            resultSizeOut[0] = 0;
            return null;
        }
        return backend.push(locations, aabb, resultSizeOut);
    }
}