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

import android.graphics.Bitmap;
import org.connectbot.service.terminal.TerminalStateManager.*;

/**
 * Mutable state wrapper for transactions
 */
public class MutableTerminalState {
    private final TerminalStateManager manager;
    
    // Original state
    private final TerminalDimensions oldDimensions;
    private final ScrollState oldScrollState;
    private final SelectionState oldSelectionState;
    private final RenderState oldRenderState;
    
    // Modified state
    private TerminalDimensions newDimensions;
    private ScrollState newScrollState;
    private SelectionState newSelectionState;
    private RenderState newRenderState;
    
    // Change tracking
    private boolean dimensionsModified = false;
    private boolean scrollStateModified = false;
    private boolean selectionStateModified = false;
    private boolean renderStateModified = false;
    
    public MutableTerminalState(TerminalStateManager manager) {
        this.manager = manager;
        
        // Capture current state
        this.oldDimensions = manager.getDimensions();
        this.oldScrollState = manager.getScrollState();
        this.oldSelectionState = manager.getSelectionState();
        this.oldRenderState = manager.getRenderState();
        
        // Initialize new state with current values
        this.newDimensions = oldDimensions;
        this.newScrollState = oldScrollState;
        this.newSelectionState = oldSelectionState;
        this.newRenderState = oldRenderState;
    }
    
    /**
     * Update terminal dimensions
     */
    public void setDimensions(int pixelWidth, int pixelHeight, int columns, int rows,
                             float charWidth, float charHeight, boolean forcedSize) {
        this.newDimensions = new TerminalDimensions(pixelWidth, pixelHeight, 
                                                   columns, rows, charWidth, charHeight, forcedSize);
        this.dimensionsModified = true;
    }
    
    /**
     * Update terminal dimensions with current pixel size
     */
    public void setTerminalSize(int columns, int rows) {
        setDimensions(newDimensions.pixelWidth, newDimensions.pixelHeight,
                     columns, rows, newDimensions.charWidth, newDimensions.charHeight,
                     newDimensions.forcedSize);
    }
    
    /**
     * Update character size
     */
    public void setCharacterSize(float charWidth, float charHeight) {
        setDimensions(newDimensions.pixelWidth, newDimensions.pixelHeight,
                     newDimensions.columns, newDimensions.rows, charWidth, charHeight,
                     newDimensions.forcedSize);
    }
    
    /**
     * Update pixel size
     */
    public void setPixelSize(int pixelWidth, int pixelHeight) {
        setDimensions(pixelWidth, pixelHeight, newDimensions.columns, newDimensions.rows,
                     newDimensions.charWidth, newDimensions.charHeight, newDimensions.forcedSize);
    }
    
    /**
     * Set forced size mode
     */
    public void setForcedSize(boolean forcedSize) {
        setDimensions(newDimensions.pixelWidth, newDimensions.pixelHeight,
                     newDimensions.columns, newDimensions.rows,
                     newDimensions.charWidth, newDimensions.charHeight, forcedSize);
    }
    
    /**
     * Update scroll state
     */
    public void setScrollState(int windowBase, int screenBase, int bufferSize, 
                              int maxBufferSize, int scrollMarker) {
        this.newScrollState = new ScrollState(windowBase, screenBase, bufferSize, 
                                            maxBufferSize, scrollMarker);
        this.scrollStateModified = true;
    }
    
    /**
     * Update window base (scroll position)
     */
    public void setWindowBase(int windowBase) {
        setScrollState(windowBase, newScrollState.screenBase, newScrollState.bufferSize,
                      newScrollState.maxBufferSize, newScrollState.scrollMarker);
    }
    
    /**
     * Update screen base
     */
    public void setScreenBase(int screenBase) {
        setScrollState(newScrollState.windowBase, screenBase, newScrollState.bufferSize,
                      newScrollState.maxBufferSize, newScrollState.scrollMarker);
    }
    
    /**
     * Update buffer size
     */
    public void setBufferSize(int bufferSize) {
        setScrollState(newScrollState.windowBase, newScrollState.screenBase, bufferSize,
                      newScrollState.maxBufferSize, newScrollState.scrollMarker);
    }
    
    /**
     * Clear selection
     */
    public void clearSelection() {
        this.newSelectionState = new SelectionState();
        this.selectionStateModified = true;
    }
    
    /**
     * Set selection
     */
    public void setSelection(int startLine, int startColumn, int endLine, int endColumn,
                           boolean isBufferCoordinates) {
        this.newSelectionState = new SelectionState(startLine, startColumn, endLine, endColumn,
                                                   isBufferCoordinates);
        this.selectionStateModified = true;
    }
    
    /**
     * Update render state
     */
    public void setRenderState(boolean needsFullRedraw, Bitmap bitmap, 
                              int cursorRow, int cursorColumn, boolean showCursor) {
        this.newRenderState = new RenderState(needsFullRedraw, bitmap, 
                                            cursorRow, cursorColumn, showCursor);
        this.renderStateModified = true;
    }
    
    /**
     * Set full redraw flag
     */
    public void setNeedsFullRedraw(boolean needsFullRedraw) {
        setRenderState(needsFullRedraw, newRenderState.bitmap, newRenderState.cursorRow,
                      newRenderState.cursorColumn, newRenderState.showCursor);
    }
    
    /**
     * Update bitmap
     */
    public void setBitmap(Bitmap bitmap) {
        setRenderState(newRenderState.needsFullRedraw, bitmap, newRenderState.cursorRow,
                      newRenderState.cursorColumn, newRenderState.showCursor);
    }
    
    /**
     * Update cursor position
     */
    public void setCursorPosition(int row, int column) {
        setRenderState(newRenderState.needsFullRedraw, newRenderState.bitmap, 
                      row, column, newRenderState.showCursor);
    }
    
    /**
     * Set cursor visibility
     */
    public void setCursorVisible(boolean visible) {
        setRenderState(newRenderState.needsFullRedraw, newRenderState.bitmap,
                      newRenderState.cursorRow, newRenderState.cursorColumn, visible);
    }
    
    /**
     * Validate all state changes
     */
    public boolean validate() {
        return newDimensions.isValid() && 
               newScrollState.isValid() && 
               newSelectionState.isValid() && 
               newRenderState.isValid();
    }
    
    // Getters for old state
    public TerminalDimensions getOldDimensions() { return oldDimensions; }
    public ScrollState getOldScrollState() { return oldScrollState; }
    public SelectionState getOldSelectionState() { return oldSelectionState; }
    public RenderState getOldRenderState() { return oldRenderState; }
    
    // Getters for new state
    public TerminalDimensions getNewDimensions() { return newDimensions; }
    public ScrollState getNewScrollState() { return newScrollState; }
    public SelectionState getNewSelectionState() { return newSelectionState; }
    public RenderState getNewRenderState() { return newRenderState; }
    
    // Change detection
    public boolean dimensionsChanged() { return dimensionsModified; }
    public boolean scrollStateChanged() { return scrollStateModified; }
    public boolean selectionStateChanged() { return selectionStateModified; }
    public boolean renderStateChanged() { return renderStateModified; }
    
    public boolean hasChanges() {
        return dimensionsModified || scrollStateModified || 
               selectionStateModified || renderStateModified;
    }
}