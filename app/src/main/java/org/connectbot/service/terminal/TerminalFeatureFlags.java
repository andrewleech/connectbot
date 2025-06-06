/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.service.terminal;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Feature flag system for controlling rollout of new terminal architecture components.
 * This allows safe, gradual deployment of improvements with the ability to quickly
 * rollback if issues are discovered.
 */
public class TerminalFeatureFlags {
    
    private static final String TAG = "TerminalFeatureFlags";
    
    // Preference keys for feature flags
    public static final String PREF_NEW_STATE_MANAGER = "terminal_new_state_manager";
    public static final String PREF_NEW_COORDINATE_MAPPER = "terminal_new_coordinate_mapper";
    public static final String PREF_NEW_INPUT_HANDLER = "terminal_new_input_handler";
    public static final String PREF_NEW_SELECTION_MANAGER = "terminal_new_selection_manager";
    public static final String PREF_NEW_GESTURE_HANDLER = "terminal_new_gesture_handler";
    public static final String PREF_NEW_RENDER_SNAPSHOT = "terminal_new_render_snapshot";
    public static final String PREF_VDU_BUFFER_THREAD_SAFETY = "terminal_vdu_buffer_thread_safety";
    public static final String PREF_ARROW_KEY_GESTURES = "terminal_arrow_key_gestures";
    public static final String PREF_ENHANCED_TOUCH_HANDLING = "terminal_enhanced_touch_handling";
    public static final String PREF_INCREMENTAL_RENDERING = "terminal_incremental_rendering";
    
    // Default values for feature flags
    private static final Map<String, Boolean> DEFAULT_VALUES = new HashMap<>();
    static {
        // Core infrastructure - start disabled for safety
        DEFAULT_VALUES.put(PREF_NEW_STATE_MANAGER, false);
        DEFAULT_VALUES.put(PREF_NEW_COORDINATE_MAPPER, false);
        DEFAULT_VALUES.put(PREF_NEW_INPUT_HANDLER, false);
        DEFAULT_VALUES.put(PREF_NEW_SELECTION_MANAGER, false);
        DEFAULT_VALUES.put(PREF_NEW_GESTURE_HANDLER, false);
        DEFAULT_VALUES.put(PREF_NEW_RENDER_SNAPSHOT, false);
        
        // Thread safety improvements - can be enabled earlier
        DEFAULT_VALUES.put(PREF_VDU_BUFFER_THREAD_SAFETY, false);
        
        // User-facing features - disabled by default
        DEFAULT_VALUES.put(PREF_ARROW_KEY_GESTURES, false);
        DEFAULT_VALUES.put(PREF_ENHANCED_TOUCH_HANDLING, false);
        DEFAULT_VALUES.put(PREF_INCREMENTAL_RENDERING, false);
    }
    
    private static TerminalFeatureFlags instance;
    private final SharedPreferences prefs;
    private final Map<String, Boolean> overrides = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    private TerminalFeatureFlags(Context context) {
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }
    
    /**
     * Initialize the feature flag system
     */
    public static synchronized void initialize(Context context) {
        if (instance == null) {
            instance = new TerminalFeatureFlags(context);
            Log.d(TAG, "TerminalFeatureFlags initialized");
        }
    }
    
    /**
     * Get the singleton instance
     */
    public static TerminalFeatureFlags getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TerminalFeatureFlags not initialized");
        }
        return instance;
    }
    
    /**
     * Check if a feature is enabled
     */
    public boolean isEnabled(String featureKey) {
        lock.readLock().lock();
        try {
            // Check for temporary overrides first
            if (overrides.containsKey(featureKey)) {
                return overrides.get(featureKey);
            }
            
            // Check SharedPreferences
            Boolean defaultValue = DEFAULT_VALUES.get(featureKey);
            if (defaultValue == null) {
                Log.w(TAG, "Unknown feature flag: " + featureKey);
                return false;
            }
            
            return prefs.getBoolean(featureKey, defaultValue);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Enable or disable a feature
     */
    public void setEnabled(String featureKey, boolean enabled) {
        lock.writeLock().lock();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(featureKey, enabled);
            editor.apply();
            
            Log.d(TAG, "Feature flag " + featureKey + " set to " + enabled);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Temporarily override a feature flag (for testing)
     */
    public void setOverride(String featureKey, boolean enabled) {
        lock.writeLock().lock();
        try {
            overrides.put(featureKey, enabled);
            Log.d(TAG, "Feature flag " + featureKey + " overridden to " + enabled);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear a temporary override
     */
    public void clearOverride(String featureKey) {
        lock.writeLock().lock();
        try {
            overrides.remove(featureKey);
            Log.d(TAG, "Feature flag override cleared for " + featureKey);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Clear all temporary overrides
     */
    public void clearAllOverrides() {
        lock.writeLock().lock();
        try {
            overrides.clear();
            Log.d(TAG, "All feature flag overrides cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    // Convenience methods for specific features
    
    public boolean isNewStateManagerEnabled() {
        return isEnabled(PREF_NEW_STATE_MANAGER);
    }
    
    public boolean isNewCoordinateMapperEnabled() {
        return isEnabled(PREF_NEW_COORDINATE_MAPPER);
    }
    
    public boolean isNewInputHandlerEnabled() {
        return isEnabled(PREF_NEW_INPUT_HANDLER);
    }
    
    public boolean isNewSelectionManagerEnabled() {
        return isEnabled(PREF_NEW_SELECTION_MANAGER);
    }
    
    public boolean isNewGestureHandlerEnabled() {
        return isEnabled(PREF_NEW_GESTURE_HANDLER);
    }
    
    public boolean isNewRenderSnapshotEnabled() {
        return isEnabled(PREF_NEW_RENDER_SNAPSHOT);
    }
    
    public boolean isVduBufferThreadSafetyEnabled() {
        return isEnabled(PREF_VDU_BUFFER_THREAD_SAFETY);
    }
    
    public boolean isArrowKeyGesturesEnabled() {
        return isEnabled(PREF_ARROW_KEY_GESTURES);
    }
    
    public boolean isEnhancedTouchHandlingEnabled() {
        return isEnabled(PREF_ENHANCED_TOUCH_HANDLING);
    }
    
    public boolean isIncrementalRenderingEnabled() {
        return isEnabled(PREF_INCREMENTAL_RENDERING);
    }
    
    /**
     * Enable all new architecture features (for testing)
     */
    public void enableAllNewFeatures() {
        lock.writeLock().lock();
        try {
            setEnabled(PREF_NEW_STATE_MANAGER, true);
            setEnabled(PREF_NEW_COORDINATE_MAPPER, true);
            setEnabled(PREF_NEW_INPUT_HANDLER, true);
            setEnabled(PREF_NEW_SELECTION_MANAGER, true);
            setEnabled(PREF_NEW_GESTURE_HANDLER, true);
            setEnabled(PREF_NEW_RENDER_SNAPSHOT, true);
            setEnabled(PREF_VDU_BUFFER_THREAD_SAFETY, true);
            setEnabled(PREF_ARROW_KEY_GESTURES, true);
            setEnabled(PREF_ENHANCED_TOUCH_HANDLING, true);
            setEnabled(PREF_INCREMENTAL_RENDERING, true);
            
            Log.i(TAG, "All new terminal architecture features enabled");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Disable all new architecture features (rollback)
     */
    public void disableAllNewFeatures() {
        lock.writeLock().lock();
        try {
            for (String key : DEFAULT_VALUES.keySet()) {
                setEnabled(key, false);
            }
            
            Log.i(TAG, "All new terminal architecture features disabled");
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Enable features in phases for safe rollout
     */
    public void enablePhase(int phase) {
        lock.writeLock().lock();
        try {
            switch (phase) {
                case 1:
                    // Phase 1: Core thread safety
                    setEnabled(PREF_VDU_BUFFER_THREAD_SAFETY, true);
                    Log.i(TAG, "Phase 1 features enabled: Thread safety");
                    break;
                    
                case 2:
                    // Phase 2: State management
                    setEnabled(PREF_VDU_BUFFER_THREAD_SAFETY, true);
                    setEnabled(PREF_NEW_STATE_MANAGER, true);
                    setEnabled(PREF_NEW_COORDINATE_MAPPER, true);
                    Log.i(TAG, "Phase 2 features enabled: State management");
                    break;
                    
                case 3:
                    // Phase 3: Input handling
                    setEnabled(PREF_VDU_BUFFER_THREAD_SAFETY, true);
                    setEnabled(PREF_NEW_STATE_MANAGER, true);
                    setEnabled(PREF_NEW_COORDINATE_MAPPER, true);
                    setEnabled(PREF_NEW_INPUT_HANDLER, true);
                    Log.i(TAG, "Phase 3 features enabled: Input handling");
                    break;
                    
                case 4:
                    // Phase 4: Touch and gestures
                    setEnabled(PREF_VDU_BUFFER_THREAD_SAFETY, true);
                    setEnabled(PREF_NEW_STATE_MANAGER, true);
                    setEnabled(PREF_NEW_COORDINATE_MAPPER, true);
                    setEnabled(PREF_NEW_INPUT_HANDLER, true);
                    setEnabled(PREF_ENHANCED_TOUCH_HANDLING, true);
                    setEnabled(PREF_NEW_GESTURE_HANDLER, true);
                    Log.i(TAG, "Phase 4 features enabled: Touch and gestures");
                    break;
                    
                case 5:
                    // Phase 5: Selection and rendering
                    enableAllNewFeatures();
                    Log.i(TAG, "Phase 5 features enabled: All features");
                    break;
                    
                default:
                    Log.w(TAG, "Unknown phase: " + phase);
                    break;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Get current feature status summary
     */
    public FeatureStatus getFeatureStatus() {
        lock.readLock().lock();
        try {
            Map<String, Boolean> currentStatus = new HashMap<>();
            for (String key : DEFAULT_VALUES.keySet()) {
                currentStatus.put(key, isEnabled(key));
            }
            
            return new FeatureStatus(currentStatus);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Feature status summary
     */
    public static class FeatureStatus {
        private final Map<String, Boolean> features;
        
        public FeatureStatus(Map<String, Boolean> features) {
            this.features = new HashMap<>(features);
        }
        
        public boolean isFeatureEnabled(String key) {
            return features.getOrDefault(key, false);
        }
        
        public Map<String, Boolean> getAllFeatures() {
            return new HashMap<>(features);
        }
        
        public int getEnabledCount() {
            return (int) features.values().stream().mapToInt(b -> b ? 1 : 0).sum();
        }
        
        public int getTotalCount() {
            return features.size();
        }
        
        public double getEnabledPercentage() {
            return (double) getEnabledCount() / getTotalCount() * 100.0;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("FeatureStatus{");
            sb.append("enabled=").append(getEnabledCount());
            sb.append("/").append(getTotalCount());
            sb.append(" (").append(String.format("%.1f", getEnabledPercentage())).append("%)");
            sb.append(", features={");
            
            boolean first = true;
            for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey().replace("terminal_", "")).append("=").append(entry.getValue());
                first = false;
            }
            
            sb.append("}}");
            return sb.toString();
        }
    }
    
    /**
     * Check if all core features are enabled
     */
    public boolean areAllCoreFeatureEnabled() {
        return isNewStateManagerEnabled() &&
               isNewCoordinateMapperEnabled() &&
               isNewInputHandlerEnabled() &&
               isVduBufferThreadSafetyEnabled();
    }
    
    /**
     * Check if all user-facing features are enabled
     */
    public boolean areAllUserFeatureEnabled() {
        return isArrowKeyGesturesEnabled() &&
               isEnhancedTouchHandlingEnabled() &&
               isNewGestureHandlerEnabled() &&
               isNewSelectionManagerEnabled();
    }
    
    /**
     * Reset all features to default values
     */
    public void resetToDefaults() {
        lock.writeLock().lock();
        try {
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, Boolean> entry : DEFAULT_VALUES.entrySet()) {
                editor.putBoolean(entry.getKey(), entry.getValue());
            }
            editor.apply();
            overrides.clear();
            
            Log.i(TAG, "All feature flags reset to defaults");
        } finally {
            lock.writeLock().unlock();
        }
    }
}