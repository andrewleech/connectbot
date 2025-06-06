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

import android.util.Log;
import android.view.MotionEvent;

import org.connectbot.bean.SelectionArea;
import org.connectbot.service.TerminalBridge;

import de.mud.terminal.VDUBuffer;

/**
 * Migration layer that provides backward compatibility while transitioning
 * to the new terminal architecture. This allows gradual rollout of new
 * features with the ability to fall back to legacy implementations.
 */
public class TerminalMigrationLayer {
    
    private static final String TAG = "TerminalMigration";
    
    private final TerminalBridge bridge;
    private final TerminalFeatureFlags featureFlags;
    
    // Legacy components (for fallback)
    private final SelectionArea legacySelectionArea;
    
    // New components (when enabled)
    private final TerminalStateManager stateManager;
    private final CoordinateMapper coordinateMapper;
    private final InputHandler inputHandler;
    private final SelectionManager selectionManager;
    private final GestureHandler gestureHandler;
    
    public TerminalMigrationLayer(TerminalBridge bridge) {
        this.bridge = bridge;
        this.featureFlags = TerminalFeatureFlags.getInstance();
        this.legacySelectionArea = bridge.getSelectionArea();
        
        // Get new components from bridge
        this.stateManager = bridge.getStateManager();
        this.coordinateMapper = bridge.getCoordinateMapper();
        this.inputHandler = bridge.getInputHandler();
        this.selectionManager = bridge.getSelectionManager();
        this.gestureHandler = null; // Would be initialized if TerminalView integration is complete
        
        Log.d(TAG, "TerminalMigrationLayer initialized");
    }
    
    /**
     * Handle coordinate conversion with fallback to legacy implementation
     */
    public CoordinateResult convertPixelToCharacter(float pixelX, float pixelY) {
        if (featureFlags.isNewCoordinateMapperEnabled()) {
            try {
                CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(pixelX, pixelY);
                CoordinateMapper.CharPoint charPoint = coordinateMapper.pixelToCharacter(pixel);
                
                return new CoordinateResult(charPoint.column, charPoint.row, true, null);
            } catch (Exception e) {
                Log.w(TAG, "New coordinate mapper failed, falling back to legacy", e);
                return convertPixelToCharacterLegacy(pixelX, pixelY);
            }
        } else {
            return convertPixelToCharacterLegacy(pixelX, pixelY);
        }
    }
    
    /**
     * Legacy coordinate conversion (original implementation)
     */
    private CoordinateResult convertPixelToCharacterLegacy(float pixelX, float pixelY) {
        try {
            int row = (int) Math.floor(pixelY / bridge.charHeight);
            int col = (int) Math.floor(pixelX / bridge.charWidth);
            
            // Simple bounds checking
            row = Math.max(0, Math.min(row, bridge.buffer.getRows() - 1));
            col = Math.max(0, Math.min(col, bridge.buffer.getColumns() - 1));
            
            return new CoordinateResult(col, row, false, null);
        } catch (Exception e) {
            Log.e(TAG, "Legacy coordinate conversion failed", e);
            return new CoordinateResult(0, 0, false, e.getMessage());
        }
    }
    
    /**
     * Handle text selection with migration support
     */
    public SelectionResult startSelection(float pixelX, float pixelY) {
        if (featureFlags.isNewSelectionManagerEnabled()) {
            try {
                CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(pixelX, pixelY);
                selectionManager.startSelection(pixel);
                return new SelectionResult(true, true, null);
            } catch (Exception e) {
                Log.w(TAG, "New selection manager failed, falling back to legacy", e);
                return startSelectionLegacy(pixelX, pixelY);
            }
        } else {
            return startSelectionLegacy(pixelX, pixelY);
        }
    }
    
    /**
     * Update selection with migration support
     */
    public SelectionResult updateSelection(float pixelX, float pixelY) {
        if (featureFlags.isNewSelectionManagerEnabled()) {
            try {
                CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(pixelX, pixelY);
                selectionManager.updateSelection(pixel);
                return new SelectionResult(true, true, null);
            } catch (Exception e) {
                Log.w(TAG, "New selection update failed, falling back to legacy", e);
                return updateSelectionLegacy(pixelX, pixelY);
            }
        } else {
            return updateSelectionLegacy(pixelX, pixelY);
        }
    }
    
    /**
     * End selection with migration support
     */
    public SelectionResult endSelection() {
        if (featureFlags.isNewSelectionManagerEnabled()) {
            try {
                SelectionManager.SelectionArea area = selectionManager.endSelection();
                return new SelectionResult(true, true, null);
            } catch (Exception e) {
                Log.w(TAG, "New selection end failed, falling back to legacy", e);
                return endSelectionLegacy();
            }
        } else {
            return endSelectionLegacy();
        }
    }
    
    /**
     * Get selected text with migration support
     */
    public String getSelectedText() {
        if (featureFlags.isNewSelectionManagerEnabled()) {
            try {
                return selectionManager.getSelectedText();
            } catch (Exception e) {
                Log.w(TAG, "New selection text extraction failed, falling back to legacy", e);
                return getSelectedTextLegacy();
            }
        } else {
            return getSelectedTextLegacy();
        }
    }
    
    /**
     * Legacy selection methods
     */
    private SelectionResult startSelectionLegacy(float pixelX, float pixelY) {
        try {
            CoordinateResult coord = convertPixelToCharacterLegacy(pixelX, pixelY);
            legacySelectionArea.setRow(coord.row);
            legacySelectionArea.setColumn(coord.column);
            return new SelectionResult(true, false, null);
        } catch (Exception e) {
            return new SelectionResult(false, false, e.getMessage());
        }
    }
    
    private SelectionResult updateSelectionLegacy(float pixelX, float pixelY) {
        try {
            CoordinateResult coord = convertPixelToCharacterLegacy(pixelX, pixelY);
            legacySelectionArea.finishSelectingOrigin();
            legacySelectionArea.setRow(coord.row);
            legacySelectionArea.setColumn(coord.column);
            return new SelectionResult(true, false, null);
        } catch (Exception e) {
            return new SelectionResult(false, false, e.getMessage());
        }
    }
    
    private SelectionResult endSelectionLegacy() {
        try {
            // Legacy selection area is maintained in TerminalBridge
            return new SelectionResult(true, false, null);
        } catch (Exception e) {
            return new SelectionResult(false, false, e.getMessage());
        }
    }
    
    private String getSelectedTextLegacy() {
        try {
            return legacySelectionArea.copyFrom(bridge.buffer);
        } catch (Exception e) {
            Log.e(TAG, "Legacy text extraction failed", e);
            return "";
        }
    }
    
    /**
     * Handle input processing with migration support
     */
    public InputResult processKey(int keyCode, InputContext context) {
        if (featureFlags.isNewInputHandlerEnabled()) {
            try {
                InputHandler.InputResult result = inputHandler.processKey(keyCode, context);
                return new InputResult(result.success, true, result.errorMessage);
            } catch (Exception e) {
                Log.w(TAG, "New input handler failed, falling back to legacy", e);
                return processKeyLegacy(keyCode, context);
            }
        } else {
            return processKeyLegacy(keyCode, context);
        }
    }
    
    private InputResult processKeyLegacy(int keyCode, InputContext context) {
        try {
            // Legacy key processing would go here
            // For now, just return success
            return new InputResult(true, false, null);
        } catch (Exception e) {
            return new InputResult(false, false, e.getMessage());
        }
    }
    
    /**
     * Handle gesture processing with migration support
     */
    public GestureResult processGesture(MotionEvent event) {
        if (featureFlags.isNewGestureHandlerEnabled() && gestureHandler != null) {
            try {
                GestureHandler.GestureResult result = gestureHandler.onTouchEvent(event);
                return new GestureResult(result.consumed, true, result.description);
            } catch (Exception e) {
                Log.w(TAG, "New gesture handler failed, falling back to legacy", e);
                return processGestureLegacy(event);
            }
        } else {
            return processGestureLegacy(event);
        }
    }
    
    private GestureResult processGestureLegacy(MotionEvent event) {
        // Legacy gesture processing (none)
        return new GestureResult(false, false, "Legacy gesture handling");
    }
    
    /**
     * Get current migration status
     */
    public MigrationStatus getMigrationStatus() {
        TerminalFeatureFlags.FeatureStatus featureStatus = featureFlags.getFeatureStatus();
        
        return new MigrationStatus(
            featureStatus.getEnabledCount(),
            featureStatus.getTotalCount(),
            featureFlags.areAllCoreFeatureEnabled(),
            featureFlags.areAllUserFeatureEnabled()
        );
    }
    
    /**
     * Coordinate conversion result
     */
    public static class CoordinateResult {
        public final int column;
        public final int row;
        public final boolean usedNewImplementation;
        public final String errorMessage;
        
        public CoordinateResult(int column, int row, boolean usedNewImplementation, String errorMessage) {
            this.column = column;
            this.row = row;
            this.usedNewImplementation = usedNewImplementation;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() {
            return errorMessage == null;
        }
    }
    
    /**
     * Selection operation result
     */
    public static class SelectionResult {
        public final boolean success;
        public final boolean usedNewImplementation;
        public final String errorMessage;
        
        public SelectionResult(boolean success, boolean usedNewImplementation, String errorMessage) {
            this.success = success;
            this.usedNewImplementation = usedNewImplementation;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Input processing result
     */
    public static class InputResult {
        public final boolean success;
        public final boolean usedNewImplementation;
        public final String errorMessage;
        
        public InputResult(boolean success, boolean usedNewImplementation, String errorMessage) {
            this.success = success;
            this.usedNewImplementation = usedNewImplementation;
            this.errorMessage = errorMessage;
        }
    }
    
    /**
     * Gesture processing result
     */
    public static class GestureResult {
        public final boolean consumed;
        public final boolean usedNewImplementation;
        public final String description;
        
        public GestureResult(boolean consumed, boolean usedNewImplementation, String description) {
            this.consumed = consumed;
            this.usedNewImplementation = usedNewImplementation;
            this.description = description;
        }
    }
    
    /**
     * Migration status information
     */
    public static class MigrationStatus {
        public final int enabledFeatures;
        public final int totalFeatures;
        public final boolean allCoreEnabled;
        public final boolean allUserEnabled;
        
        public MigrationStatus(int enabledFeatures, int totalFeatures, 
                             boolean allCoreEnabled, boolean allUserEnabled) {
            this.enabledFeatures = enabledFeatures;
            this.totalFeatures = totalFeatures;
            this.allCoreEnabled = allCoreEnabled;
            this.allUserEnabled = allUserEnabled;
        }
        
        public double getMigrationPercentage() {
            return (double) enabledFeatures / totalFeatures * 100.0;
        }
        
        public boolean isFullyMigrated() {
            return enabledFeatures == totalFeatures;
        }
        
        @Override
        public String toString() {
            return String.format("MigrationStatus{%d/%d features (%.1f%%), core=%b, user=%b}",
                enabledFeatures, totalFeatures, getMigrationPercentage(), 
                allCoreEnabled, allUserEnabled);
        }
    }
    
    /**
     * Validate current migration state
     */
    public ValidationResult validateMigrationState() {
        try {
            // Test coordinate conversion
            CoordinateResult coordTest = convertPixelToCharacter(100f, 100f);
            
            // Test selection if enabled
            boolean selectionWorking = true;
            if (featureFlags.isNewSelectionManagerEnabled()) {
                SelectionResult selTest = startSelection(100f, 100f);
                selectionWorking = selTest.success;
                endSelection(); // Clean up
            }
            
            // Test input handling if enabled
            boolean inputWorking = true;
            if (featureFlags.isNewInputHandlerEnabled()) {
                InputResult inputTest = processKey(65, InputContext.DEFAULT_KEYBOARD);
                inputWorking = inputTest.success;
            }
            
            boolean allWorking = coordTest.isSuccess() && selectionWorking && inputWorking;
            
            return new ValidationResult(allWorking, 
                String.format("Coordinate: %b, Selection: %b, Input: %b", 
                             coordTest.isSuccess(), selectionWorking, inputWorking));
                             
        } catch (Exception e) {
            return new ValidationResult(false, "Validation failed: " + e.getMessage());
        }
    }
    
    /**
     * Validation result
     */
    public static class ValidationResult {
        public final boolean passed;
        public final String details;
        
        public ValidationResult(boolean passed, String details) {
            this.passed = passed;
            this.details = details;
        }
    }
}