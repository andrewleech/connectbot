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

import de.mud.terminal.VDUBuffer;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe selection manager that handles text selection using buffer coordinates.
 * This provides more robust selection handling that works correctly with scrolling
 * and coordinate transformations.
 */
public class SelectionManager {
    
    private final TerminalStateManager stateManager;
    private final CoordinateMapper coordinateMapper;
    private final VDUBuffer buffer;
    private final ReadWriteLock selectionLock = new ReentrantReadWriteLock();
    
    // Selection state
    private boolean active = false;
    private boolean selectingOrigin = true;
    private CoordinateMapper.BufferPoint startPoint;
    private CoordinateMapper.BufferPoint endPoint;
    private SelectionListener listener;
    
    /**
     * Selection area in normalized coordinates (top-left to bottom-right)
     */
    public static class SelectionArea {
        public final CoordinateMapper.BufferPoint topLeft;
        public final CoordinateMapper.BufferPoint bottomRight;
        public final int lineCount;
        public final int characterCount;
        
        public SelectionArea(CoordinateMapper.BufferPoint topLeft, 
                           CoordinateMapper.BufferPoint bottomRight) {
            this.topLeft = topLeft;
            this.bottomRight = bottomRight;
            this.lineCount = bottomRight.line - topLeft.line + 1;
            
            // Calculate character count
            if (topLeft.line == bottomRight.line) {
                // Single line selection
                this.characterCount = bottomRight.column - topLeft.column + 1;
            } else {
                // Multi-line selection - estimate based on average line length
                this.characterCount = lineCount * 80; // Rough estimate
            }
        }
        
        public boolean isEmpty() {
            return topLeft.equals(bottomRight);
        }
        
        public boolean containsPoint(CoordinateMapper.BufferPoint point) {
            if (point.line < topLeft.line || point.line > bottomRight.line) {
                return false;
            }
            if (point.line == topLeft.line && point.column < topLeft.column) {
                return false;
            }
            if (point.line == bottomRight.line && point.column > bottomRight.column) {
                return false;
            }
            return true;
        }
    }
    
    /**
     * Listener for selection events
     */
    public interface SelectionListener {
        /**
         * Called when selection starts
         */
        void onSelectionStarted(CoordinateMapper.BufferPoint startPoint);
        
        /**
         * Called when selection changes
         */
        void onSelectionChanged(SelectionArea area);
        
        /**
         * Called when selection ends
         */
        void onSelectionEnded(SelectionArea area);
        
        /**
         * Called when selection is cleared
         */
        void onSelectionCleared();
    }
    
    /**
     * Create a selection manager
     */
    public SelectionManager(TerminalStateManager stateManager, 
                          CoordinateMapper coordinateMapper, 
                          VDUBuffer buffer) {
        this.stateManager = stateManager;
        this.coordinateMapper = coordinateMapper;
        this.buffer = buffer;
    }
    
    /**
     * Set the selection listener
     */
    public void setSelectionListener(SelectionListener listener) {
        selectionLock.writeLock().lock();
        try {
            this.listener = listener;
        } finally {
            selectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Start a new selection at the given buffer point
     */
    public void startSelection(CoordinateMapper.BufferPoint point) {
        selectionLock.writeLock().lock();
        try {
            active = true;
            selectingOrigin = true;
            startPoint = point;
            endPoint = point;
            
            if (listener != null) {
                listener.onSelectionStarted(point);
            }
        } finally {
            selectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Start selection at pixel coordinates
     */
    public void startSelection(CoordinateMapper.PixelPoint pixelPoint) {
        CoordinateMapper.BufferPoint bufferPoint = coordinateMapper.pixelToBuffer(pixelPoint);
        startSelection(bufferPoint);
    }
    
    /**
     * Update the selection to include the given point
     */
    public void updateSelection(CoordinateMapper.BufferPoint point) {
        selectionLock.writeLock().lock();
        try {
            if (!active) {
                return;
            }
            
            if (selectingOrigin) {
                // First move - establish the selection direction
                selectingOrigin = false;
            }
            
            endPoint = point;
            
            if (listener != null) {
                SelectionArea area = getCurrentSelectionArea();
                if (area != null) {
                    listener.onSelectionChanged(area);
                }
            }
        } finally {
            selectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Update selection at pixel coordinates
     */
    public void updateSelection(CoordinateMapper.PixelPoint pixelPoint) {
        CoordinateMapper.BufferPoint bufferPoint = coordinateMapper.pixelToBuffer(pixelPoint);
        updateSelection(bufferPoint);
    }
    
    /**
     * End the current selection
     */
    public SelectionArea endSelection() {
        selectionLock.writeLock().lock();
        try {
            if (!active) {
                return null;
            }
            
            SelectionArea area = getCurrentSelectionArea();
            
            if (listener != null && area != null) {
                listener.onSelectionEnded(area);
            }
            
            return area;
        } finally {
            selectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Clear the current selection
     */
    public void clearSelection() {
        selectionLock.writeLock().lock();
        try {
            active = false;
            selectingOrigin = true;
            startPoint = null;
            endPoint = null;
            
            if (listener != null) {
                listener.onSelectionCleared();
            }
        } finally {
            selectionLock.writeLock().unlock();
        }
    }
    
    /**
     * Check if selection is currently active
     */
    public boolean isSelectionActive() {
        selectionLock.readLock().lock();
        try {
            return active;
        } finally {
            selectionLock.readLock().unlock();
        }
    }
    
    /**
     * Check if we're still selecting the origin point
     */
    public boolean isSelectingOrigin() {
        selectionLock.readLock().lock();
        try {
            return selectingOrigin;
        } finally {
            selectionLock.readLock().unlock();
        }
    }
    
    /**
     * Get the current selection area (normalized to top-left, bottom-right)
     */
    public SelectionArea getCurrentSelectionArea() {
        selectionLock.readLock().lock();
        try {
            if (!active || startPoint == null || endPoint == null) {
                return null;
            }
            
            return normalizeSelection(startPoint, endPoint);
        } finally {
            selectionLock.readLock().unlock();
        }
    }
    
    /**
     * Extract text from the current selection
     */
    public String getSelectedText() {
        selectionLock.readLock().lock();
        try {
            SelectionArea area = getCurrentSelectionArea();
            if (area == null || area.isEmpty()) {
                return "";
            }
            
            return extractTextFromArea(area);
        } finally {
            selectionLock.readLock().unlock();
        }
    }
    
    /**
     * Extract text from a specific selection area
     */
    public String extractTextFromArea(SelectionArea area) {
        if (area == null || buffer == null) {
            return "";
        }
        
        // Use synchronized buffer access for thread safety
        SynchronizedBufferAccess bufferAccess = new SynchronizedBufferAccess(buffer);
        
        StringBuilder result = new StringBuilder();
        
        for (int line = area.topLeft.line; line <= area.bottomRight.line; line++) {
            // Determine column range for this line
            int startCol = (line == area.topLeft.line) ? area.topLeft.column : 0;
            int endCol = (line == area.bottomRight.line) ? area.bottomRight.column : 
                        getLineLength(bufferAccess, line);
            
            // Extract text from this line
            String lineText = extractLineText(bufferAccess, line, startCol, endCol);
            result.append(lineText);
            
            // Add newline if not the last line
            if (line < area.bottomRight.line) {
                result.append('\n');
            }
        }
        
        return result.toString();
    }
    
    /**
     * Check if a buffer point is within the current selection
     */
    public boolean isPointSelected(CoordinateMapper.BufferPoint point) {
        selectionLock.readLock().lock();
        try {
            SelectionArea area = getCurrentSelectionArea();
            return area != null && area.containsPoint(point);
        } finally {
            selectionLock.readLock().unlock();
        }
    }
    
    /**
     * Check if a character coordinate is within the current selection
     */
    public boolean isCharacterSelected(CoordinateMapper.CharPoint charPoint) {
        CoordinateMapper.BufferPoint bufferPoint = coordinateMapper.characterToBuffer(charPoint);
        return isPointSelected(bufferPoint);
    }
    
    /**
     * Normalize selection points to ensure top-left to bottom-right ordering
     */
    private SelectionArea normalizeSelection(CoordinateMapper.BufferPoint start, 
                                           CoordinateMapper.BufferPoint end) {
        CoordinateMapper.BufferPoint topLeft, bottomRight;
        
        if (start.line < end.line || 
            (start.line == end.line && start.column <= end.column)) {
            topLeft = start;
            bottomRight = end;
        } else {
            topLeft = end;
            bottomRight = start;
        }
        
        return new SelectionArea(topLeft, bottomRight);
    }
    
    /**
     * Extract text from a single line within the given column range
     */
    private String extractLineText(SynchronizedBufferAccess bufferAccess, 
                                 int line, int startCol, int endCol) {
        StringBuilder lineText = new StringBuilder();
        int lastNonSpace = -1;
        
        // Convert buffer line to character line for VDUBuffer access
        CoordinateMapper.BufferPoint bufferPoint = new CoordinateMapper.BufferPoint(line, 0);
        CoordinateMapper.CharPoint charPoint = coordinateMapper.bufferToCharacter(bufferPoint);
        
        if (charPoint == null) {
            // Line not visible - return empty string
            return "";
        }
        
        int charLine = charPoint.row;
        
        for (int col = startCol; col <= endCol; col++) {
            char c = bufferAccess.getChar(col, charLine);
            
            // Replace control characters with spaces
            if (!Character.isDefined(c) || 
                (Character.isISOControl(c) && c != '\t')) {
                c = ' ';
            }
            
            if (c != ' ') {
                lastNonSpace = lineText.length();
            }
            
            lineText.append(c);
        }
        
        // Trim trailing spaces
        if (lastNonSpace >= 0 && lastNonSpace < lineText.length() - 1) {
            lineText.setLength(lastNonSpace + 1);
        }
        
        return lineText.toString();
    }
    
    /**
     * Get the effective length of a line (excluding trailing spaces)
     */
    private int getLineLength(SynchronizedBufferAccess bufferAccess, int bufferLine) {
        // Convert to character coordinates for buffer access
        CoordinateMapper.BufferPoint bufferPoint = new CoordinateMapper.BufferPoint(bufferLine, 0);
        CoordinateMapper.CharPoint charPoint = coordinateMapper.bufferToCharacter(bufferPoint);
        
        if (charPoint == null) {
            return 0;
        }
        
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        int maxCol = dims.columns - 1;
        
        // Find the last non-space character
        for (int col = maxCol; col >= 0; col--) {
            char c = bufferAccess.getChar(col, charPoint.row);
            if (c != ' ' && c != '\0') {
                return col;
            }
        }
        
        return 0;
    }
    
    /**
     * Get selection statistics
     */
    public SelectionStats getStats() {
        selectionLock.readLock().lock();
        try {
            SelectionArea area = getCurrentSelectionArea();
            return new SelectionStats(
                active,
                selectingOrigin,
                area != null ? area.lineCount : 0,
                area != null ? area.characterCount : 0
            );
        } finally {
            selectionLock.readLock().unlock();
        }
    }
    
    /**
     * Selection statistics
     */
    public static class SelectionStats {
        public final boolean active;
        public final boolean selectingOrigin;
        public final int lineCount;
        public final int characterCount;
        
        public SelectionStats(boolean active, boolean selectingOrigin, 
                            int lineCount, int characterCount) {
            this.active = active;
            this.selectingOrigin = selectingOrigin;
            this.lineCount = lineCount;
            this.characterCount = characterCount;
        }
    }
}