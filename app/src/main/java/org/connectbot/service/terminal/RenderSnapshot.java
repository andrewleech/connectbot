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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Immutable snapshot of terminal rendering state for optimized drawing.
 * This class captures the current state of the terminal buffer, cursor,
 * selection, and visual properties to enable efficient rendering updates.
 */
public class RenderSnapshot {
    
    private final TerminalStateManager.TerminalDimensions dimensions;
    private final TerminalStateManager.ScrollState scrollState;
    private final TerminalStateManager.RenderState renderState;
    private final SelectionManager.SelectionArea selection;
    private final CursorInfo cursor;
    private final List<LineSegment> changedSegments;
    private final long timestamp;
    
    /**
     * Cursor information for rendering
     */
    public static class CursorInfo {
        public final CoordinateMapper.CharPoint position;
        public final boolean visible;
        public final CursorStyle style;
        public final boolean blinking;
        
        public CursorInfo(CoordinateMapper.CharPoint position, boolean visible, 
                         CursorStyle style, boolean blinking) {
            this.position = position;
            this.visible = visible;
            this.style = style;
            this.blinking = blinking;
        }
    }
    
    /**
     * Cursor styles
     */
    public enum CursorStyle {
        BLOCK, UNDERLINE, VERTICAL_BAR
    }
    
    /**
     * A segment of a line that needs rendering
     */
    public static class LineSegment {
        public final int line;
        public final int startColumn;
        public final int endColumn;
        public final SegmentType type;
        
        public enum SegmentType {
            TEXT_CHANGED,
            SELECTION_CHANGED,
            CURSOR_CHANGED,
            ATTRIBUTE_CHANGED
        }
        
        public LineSegment(int line, int startColumn, int endColumn, SegmentType type) {
            this.line = line;
            this.startColumn = startColumn;
            this.endColumn = endColumn;
            this.type = type;
        }
        
        public int getLength() {
            return endColumn - startColumn + 1;
        }
        
        public boolean overlaps(LineSegment other) {
            return line == other.line && 
                   startColumn <= other.endColumn && 
                   endColumn >= other.startColumn;
        }
        
        public LineSegment merge(LineSegment other) {
            if (!overlaps(other)) {
                throw new IllegalArgumentException("Cannot merge non-overlapping segments");
            }
            
            return new LineSegment(
                line,
                Math.min(startColumn, other.startColumn),
                Math.max(endColumn, other.endColumn),
                type
            );
        }
    }
    
    /**
     * Character cell information for rendering
     */
    public static class CellInfo {
        public final char character;
        public final int foregroundColor;
        public final int backgroundColor;
        public final boolean bold;
        public final boolean italic;
        public final boolean underline;
        public final boolean blink;
        public final boolean selected;
        
        public CellInfo(char character, int foregroundColor, int backgroundColor,
                       boolean bold, boolean italic, boolean underline, 
                       boolean blink, boolean selected) {
            this.character = character;
            this.foregroundColor = foregroundColor;
            this.backgroundColor = backgroundColor;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.blink = blink;
            this.selected = selected;
        }
    }
    
    /**
     * Create a render snapshot
     */
    public RenderSnapshot(TerminalStateManager.TerminalDimensions dimensions,
                         TerminalStateManager.ScrollState scrollState,
                         TerminalStateManager.RenderState renderState,
                         SelectionManager.SelectionArea selection,
                         CursorInfo cursor,
                         List<LineSegment> changedSegments) {
        this.dimensions = dimensions;
        this.scrollState = scrollState;
        this.renderState = renderState;
        this.selection = selection;
        this.cursor = cursor;
        this.changedSegments = new ArrayList<>(changedSegments);
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Get terminal dimensions
     */
    public TerminalStateManager.TerminalDimensions getDimensions() {
        return dimensions;
    }
    
    /**
     * Get scroll state
     */
    public TerminalStateManager.ScrollState getScrollState() {
        return scrollState;
    }
    
    /**
     * Get render state
     */
    public TerminalStateManager.RenderState getRenderState() {
        return renderState;
    }
    
    /**
     * Get selection area
     */
    public SelectionManager.SelectionArea getSelection() {
        return selection;
    }
    
    /**
     * Get cursor information
     */
    public CursorInfo getCursor() {
        return cursor;
    }
    
    /**
     * Get segments that need rendering
     */
    public List<LineSegment> getChangedSegments() {
        return new ArrayList<>(changedSegments);
    }
    
    /**
     * Get timestamp when snapshot was created
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Check if a full redraw is needed
     */
    public boolean needsFullRedraw() {
        return renderState.needsFullRedraw;
    }
    
    /**
     * Check if cursor is visible and should be drawn
     */
    public boolean shouldDrawCursor() {
        return cursor.visible && renderState.showCursor;
    }
    
    /**
     * Check if selection should be drawn
     */
    public boolean hasSelection() {
        return selection != null && !selection.isEmpty();
    }
    
    /**
     * Get the number of lines that need updating
     */
    public int getChangedLineCount() {
        return (int) changedSegments.stream()
            .mapToInt(segment -> segment.line)
            .distinct()
            .count();
    }
    
    /**
     * Get the total number of characters that need updating
     */
    public int getChangedCharacterCount() {
        return changedSegments.stream()
            .mapToInt(LineSegment::getLength)
            .sum();
    }
    
    /**
     * Check if a specific line needs rendering
     */
    public boolean isLineChanged(int line) {
        return changedSegments.stream()
            .anyMatch(segment -> segment.line == line);
    }
    
    /**
     * Get segments for a specific line
     */
    public List<LineSegment> getSegmentsForLine(int line) {
        return changedSegments.stream()
            .filter(segment -> segment.line == line)
            .collect(ArrayList::new, (list, segment) -> list.add(segment), List::addAll);
    }
    
    /**
     * Check if cursor is on a specific character position
     */
    public boolean isCursorAt(int column, int row) {
        return cursor.position != null && 
               cursor.position.column == column && 
               cursor.position.row == row;
    }
    
    /**
     * Check if a character position is selected
     */
    public boolean isPositionSelected(int column, int row) {
        if (selection == null) {
            return false;
        }
        
        // Convert character coordinates to buffer coordinates for selection check
        // This would need access to coordinate mapper in a real implementation
        return false; // Placeholder - would need coordinate mapper integration
    }
    
    /**
     * Create a snapshot builder for efficient construction
     */
    public static class Builder {
        private TerminalStateManager.TerminalDimensions dimensions;
        private TerminalStateManager.ScrollState scrollState;
        private TerminalStateManager.RenderState renderState;
        private SelectionManager.SelectionArea selection;
        private CursorInfo cursor;
        private final List<LineSegment> changedSegments = new ArrayList<>();
        
        public Builder setDimensions(TerminalStateManager.TerminalDimensions dimensions) {
            this.dimensions = dimensions;
            return this;
        }
        
        public Builder setScrollState(TerminalStateManager.ScrollState scrollState) {
            this.scrollState = scrollState;
            return this;
        }
        
        public Builder setRenderState(TerminalStateManager.RenderState renderState) {
            this.renderState = renderState;
            return this;
        }
        
        public Builder setSelection(SelectionManager.SelectionArea selection) {
            this.selection = selection;
            return this;
        }
        
        public Builder setCursor(CursorInfo cursor) {
            this.cursor = cursor;
            return this;
        }
        
        public Builder addChangedSegment(LineSegment segment) {
            changedSegments.add(segment);
            return this;
        }
        
        public Builder addChangedLine(int line) {
            // Add the entire line as changed
            if (dimensions != null) {
                changedSegments.add(new LineSegment(
                    line, 0, dimensions.columns - 1, 
                    LineSegment.SegmentType.TEXT_CHANGED));
            }
            return this;
        }
        
        public Builder addChangedRegion(int line, int startCol, int endCol, 
                                      LineSegment.SegmentType type) {
            changedSegments.add(new LineSegment(line, startCol, endCol, type));
            return this;
        }
        
        public Builder mergeOverlappingSegments() {
            // Merge overlapping segments to optimize rendering
            List<LineSegment> merged = new ArrayList<>();
            changedSegments.sort((a, b) -> {
                int lineCmp = Integer.compare(a.line, b.line);
                if (lineCmp != 0) return lineCmp;
                return Integer.compare(a.startColumn, b.startColumn);
            });
            
            for (LineSegment segment : changedSegments) {
                if (merged.isEmpty()) {
                    merged.add(segment);
                } else {
                    LineSegment last = merged.get(merged.size() - 1);
                    if (last.overlaps(segment)) {
                        merged.set(merged.size() - 1, last.merge(segment));
                    } else {
                        merged.add(segment);
                    }
                }
            }
            
            changedSegments.clear();
            changedSegments.addAll(merged);
            return this;
        }
        
        public RenderSnapshot build() {
            mergeOverlappingSegments();
            return new RenderSnapshot(dimensions, scrollState, renderState, 
                                    selection, cursor, changedSegments);
        }
    }
    
    /**
     * Factory for creating render snapshots from current state
     */
    public static class Factory {
        private final TerminalStateManager stateManager;
        private final SelectionManager selectionManager;
        private final CoordinateMapper coordinateMapper;
        private final ReadWriteLock factoryLock = new ReentrantReadWriteLock();
        
        public Factory(TerminalStateManager stateManager, 
                      SelectionManager selectionManager,
                      CoordinateMapper coordinateMapper) {
            this.stateManager = stateManager;
            this.selectionManager = selectionManager;
            this.coordinateMapper = coordinateMapper;
        }
        
        /**
         * Create a snapshot of the current terminal state
         */
        public RenderSnapshot createSnapshot() {
            factoryLock.readLock().lock();
            try {
                Builder builder = new Builder();
                
                // Capture current state
                stateManager.readState(state -> {
                    builder.setDimensions(stateManager.getDimensions())
                           .setScrollState(stateManager.getScrollState())
                           .setRenderState(stateManager.getRenderState());
                    return null;
                });
                
                // Capture selection
                SelectionManager.SelectionArea selection = selectionManager.getCurrentSelectionArea();
                builder.setSelection(selection);
                
                // Capture cursor (would need integration with actual cursor state)
                CursorInfo cursor = createCursorInfo();
                builder.setCursor(cursor);
                
                // Add changed segments based on render state
                if (stateManager.getRenderState().needsFullRedraw) {
                    // Add all lines as changed
                    TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
                    for (int line = 0; line < dims.rows; line++) {
                        builder.addChangedLine(line);
                    }
                } else {
                    // Add only incrementally changed segments
                    // This would be populated by the rendering system
                }
                
                return builder.build();
            } finally {
                factoryLock.readLock().unlock();
            }
        }
        
        /**
         * Create a snapshot for a specific changed region
         */
        public RenderSnapshot createIncrementalSnapshot(int startLine, int endLine) {
            factoryLock.readLock().lock();
            try {
                Builder builder = new Builder();
                
                stateManager.readState(state -> {
                    builder.setDimensions(stateManager.getDimensions())
                           .setScrollState(stateManager.getScrollState())
                           .setRenderState(stateManager.getRenderState());
                    return null;
                });
                
                builder.setSelection(selectionManager.getCurrentSelectionArea())
                       .setCursor(createCursorInfo());
                
                // Add only the specified line range
                for (int line = startLine; line <= endLine; line++) {
                    builder.addChangedLine(line);
                }
                
                return builder.build();
            } finally {
                factoryLock.readLock().unlock();
            }
        }
        
        private CursorInfo createCursorInfo() {
            // This would integrate with actual cursor state from TerminalBridge
            TerminalStateManager.RenderState renderState = stateManager.getRenderState();
            CoordinateMapper.CharPoint position = new CoordinateMapper.CharPoint(
                renderState.cursorColumn, renderState.cursorRow);
            
            return new CursorInfo(position, renderState.showCursor, 
                                CursorStyle.BLOCK, false);
        }
    }
    
    /**
     * Performance statistics for render snapshots
     */
    public RenderStats getStats() {
        return new RenderStats(
            getChangedLineCount(),
            getChangedCharacterCount(),
            changedSegments.size(),
            needsFullRedraw(),
            hasSelection(),
            shouldDrawCursor()
        );
    }
    
    /**
     * Render performance statistics
     */
    public static class RenderStats {
        public final int changedLines;
        public final int changedCharacters;
        public final int segmentCount;
        public final boolean fullRedraw;
        public final boolean hasSelection;
        public final boolean hasCursor;
        
        public RenderStats(int changedLines, int changedCharacters, int segmentCount,
                          boolean fullRedraw, boolean hasSelection, boolean hasCursor) {
            this.changedLines = changedLines;
            this.changedCharacters = changedCharacters;
            this.segmentCount = segmentCount;
            this.fullRedraw = fullRedraw;
            this.hasSelection = hasSelection;
            this.hasCursor = hasCursor;
        }
        
        public double getUpdateRatio() {
            // Assuming 80x24 terminal as baseline
            int totalCharacters = 80 * 24;
            return (double) changedCharacters / totalCharacters;
        }
    }
}