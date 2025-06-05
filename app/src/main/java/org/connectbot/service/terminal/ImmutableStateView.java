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

import org.connectbot.service.terminal.TerminalStateManager.*;

/**
 * Read-only view of terminal state for safe access during read operations
 */
public class ImmutableStateView {
    private final TerminalStateManager manager;
    
    public ImmutableStateView(TerminalStateManager manager) {
        this.manager = manager;
    }
    
    /**
     * Get current dimensions
     */
    public TerminalDimensions getDimensions() {
        return manager.getDimensions();
    }
    
    /**
     * Get current scroll state
     */
    public ScrollState getScrollState() {
        return manager.getScrollState();
    }
    
    /**
     * Get current selection state
     */
    public SelectionState getSelectionState() {
        return manager.getSelectionState();
    }
    
    /**
     * Get current render state
     */
    public RenderState getRenderState() {
        return manager.getRenderState();
    }
    
    // Convenience getters for common operations
    
    public int getColumns() {
        return getDimensions().columns;
    }
    
    public int getRows() {
        return getDimensions().rows;
    }
    
    public float getCharWidth() {
        return getDimensions().charWidth;
    }
    
    public float getCharHeight() {
        return getDimensions().charHeight;
    }
    
    public int getPixelWidth() {
        return getDimensions().pixelWidth;
    }
    
    public int getPixelHeight() {
        return getDimensions().pixelHeight;
    }
    
    public boolean isForcedSize() {
        return getDimensions().forcedSize;
    }
    
    public int getWindowBase() {
        return getScrollState().windowBase;
    }
    
    public int getScreenBase() {
        return getScrollState().screenBase;
    }
    
    public int getBufferSize() {
        return getScrollState().bufferSize;
    }
    
    public int getMaxBufferSize() {
        return getScrollState().maxBufferSize;
    }
    
    public boolean isScrolledBack() {
        return getScrollState().isScrolledBack();
    }
    
    public boolean hasSelection() {
        return getSelectionState().active;
    }
    
    public boolean needsFullRedraw() {
        return getRenderState().needsFullRedraw;
    }
    
    public int getCursorRow() {
        return getRenderState().cursorRow;
    }
    
    public int getCursorColumn() {
        return getRenderState().cursorColumn;
    }
    
    public boolean isCursorVisible() {
        return getRenderState().showCursor;
    }
}