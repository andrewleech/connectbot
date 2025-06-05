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

/**
 * Unified coordinate transformation system with validation and bounds checking.
 * Provides thread-safe conversion between pixel, character, and buffer coordinates.
 */
public class CoordinateMapper {
    private final TerminalStateManager stateManager;
    
    /**
     * Immutable pixel coordinate
     */
    public static class PixelPoint {
        public final float x, y;
        
        public PixelPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            PixelPoint that = (PixelPoint) obj;
            return Float.compare(that.x, x) == 0 && Float.compare(that.y, y) == 0;
        }
        
        @Override
        public int hashCode() {
            return Float.hashCode(x) * 31 + Float.hashCode(y);
        }
        
        @Override
        public String toString() {
            return String.format("PixelPoint(%.1f, %.1f)", x, y);
        }
    }
    
    /**
     * Immutable character coordinate (terminal grid position)
     */
    public static class CharPoint {
        public final int column, row;
        
        public CharPoint(int column, int row) {
            this.column = column;
            this.row = row;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CharPoint charPoint = (CharPoint) obj;
            return column == charPoint.column && row == charPoint.row;
        }
        
        @Override
        public int hashCode() {
            return column * 31 + row;
        }
        
        @Override
        public String toString() {
            return String.format("CharPoint(%d, %d)", column, row);
        }
    }
    
    /**
     * Immutable buffer coordinate (absolute position in scrollback buffer)
     */
    public static class BufferPoint {
        public final int line, column;
        
        public BufferPoint(int line, int column) {
            this.line = line;
            this.column = column;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            BufferPoint that = (BufferPoint) obj;
            return line == that.line && column == that.column;
        }
        
        @Override
        public int hashCode() {
            return line * 31 + column;
        }
        
        @Override
        public String toString() {
            return String.format("BufferPoint(%d, %d)", line, column);
        }
    }
    
    /**
     * Constructor
     */
    public CoordinateMapper(TerminalStateManager stateManager) {
        this.stateManager = stateManager;
    }
    
    /**
     * Convert pixel coordinates to character coordinates
     * Always returns valid coordinates within terminal bounds
     */
    public CharPoint pixelToCharacter(PixelPoint pixel) {
        return stateManager.readState(state -> {
            float charWidth = state.getCharWidth();
            float charHeight = state.getCharHeight();
            
            if (charWidth <= 0 || charHeight <= 0) {
                return new CharPoint(0, 0);
            }
            
            int col = (int)(pixel.x / charWidth);
            int row = (int)(pixel.y / charHeight);
            
            // Bounds checking
            col = Math.max(0, Math.min(col, state.getColumns() - 1));
            row = Math.max(0, Math.min(row, state.getRows() - 1));
            
            return new CharPoint(col, row);
        });
    }
    
    /**
     * Convert character coordinates to buffer coordinates
     * Accounts for scroll position (windowBase)
     */
    public BufferPoint characterToBuffer(CharPoint charPoint) {
        return stateManager.readState(state -> {
            int bufferLine = charPoint.row + state.getWindowBase();
            
            // Ensure within buffer bounds
            bufferLine = Math.max(0, Math.min(bufferLine, state.getBufferSize() - 1));
            
            // Column bounds checking
            int column = Math.max(0, Math.min(charPoint.column, state.getColumns() - 1));
            
            return new BufferPoint(bufferLine, column);
        });
    }
    
    /**
     * Convert buffer coordinates to character coordinates
     * Returns null if buffer position is not visible
     */
    public CharPoint bufferToCharacter(BufferPoint bufferPoint) {
        return stateManager.readState(state -> {
            int relativeLine = bufferPoint.line - state.getWindowBase();
            
            // Check if buffer position is visible
            if (relativeLine < 0 || relativeLine >= state.getRows()) {
                return null; // Not visible
            }
            
            // Column bounds checking
            int column = Math.max(0, Math.min(bufferPoint.column, state.getColumns() - 1));
            
            return new CharPoint(column, relativeLine);
        });
    }
    
    /**
     * Convert character coordinates to pixel coordinates
     */
    public PixelPoint characterToPixel(CharPoint charPoint) {
        return stateManager.readState(state -> {
            float x = charPoint.column * state.getCharWidth();
            float y = charPoint.row * state.getCharHeight();
            
            return new PixelPoint(x, y);
        });
    }
    
    /**
     * Convert buffer coordinates to pixel coordinates
     * Returns null if coordinates not visible
     */
    public PixelPoint bufferToPixel(BufferPoint bufferPoint) {
        CharPoint charPoint = bufferToCharacter(bufferPoint);
        if (charPoint == null) {
            return null; // Not visible
        }
        
        return characterToPixel(charPoint);
    }
    
    /**
     * Convert pixel coordinates directly to buffer coordinates
     */
    public BufferPoint pixelToBuffer(PixelPoint pixel) {
        CharPoint charPoint = pixelToCharacter(pixel);
        return characterToBuffer(charPoint);
    }
    
    /**
     * Check if buffer point is currently visible
     */
    public boolean isVisible(BufferPoint bufferPoint) {
        return stateManager.readState(state -> {
            int relativeLine = bufferPoint.line - state.getWindowBase();
            return relativeLine >= 0 && relativeLine < state.getRows() &&
                   bufferPoint.column >= 0 && bufferPoint.column < state.getColumns();
        });
    }
    
    /**
     * Check if character point is within bounds
     */
    public boolean isValidCharacter(CharPoint charPoint) {
        return stateManager.readState(state -> {
            return charPoint.column >= 0 && charPoint.column < state.getColumns() &&
                   charPoint.row >= 0 && charPoint.row < state.getRows();
        });
    }
    
    /**
     * Get the pixel bounds of a character cell
     */
    public PixelBounds getCharacterBounds(CharPoint charPoint) {
        return stateManager.readState(state -> {
            float left = charPoint.column * state.getCharWidth();
            float top = charPoint.row * state.getCharHeight();
            float right = left + state.getCharWidth();
            float bottom = top + state.getCharHeight();
            
            return new PixelBounds(left, top, right, bottom);
        });
    }
    
    /**
     * Get the pixel bounds of a buffer position (if visible)
     */
    public PixelBounds getBufferBounds(BufferPoint bufferPoint) {
        CharPoint charPoint = bufferToCharacter(bufferPoint);
        if (charPoint == null) {
            return null;
        }
        
        return getCharacterBounds(charPoint);
    }
    
    /**
     * Pixel bounds rectangle
     */
    public static class PixelBounds {
        public final float left, top, right, bottom;
        
        public PixelBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
        
        public float width() {
            return right - left;
        }
        
        public float height() {
            return bottom - top;
        }
        
        public boolean contains(PixelPoint point) {
            return point.x >= left && point.x < right && 
                   point.y >= top && point.y < bottom;
        }
        
        @Override
        public String toString() {
            return String.format("PixelBounds(%.1f, %.1f, %.1f, %.1f)", left, top, right, bottom);
        }
    }
    
    /**
     * Calculate the terminal size that would fit in given pixel dimensions
     */
    public CharPoint calculateTerminalSize(int pixelWidth, int pixelHeight) {
        return stateManager.readState(state -> {
            if (state.getCharWidth() <= 0 || state.getCharHeight() <= 0) {
                return new CharPoint(80, 24); // Default fallback
            }
            
            int columns = (int)(pixelWidth / state.getCharWidth());
            int rows = (int)(pixelHeight / state.getCharHeight());
            
            // Ensure minimum size
            columns = Math.max(1, columns);
            rows = Math.max(1, rows);
            
            return new CharPoint(columns, rows);
        });
    }
    
    /**
     * Calculate character size needed for specific terminal dimensions
     */
    public PixelPoint calculateCharacterSize(int pixelWidth, int pixelHeight, 
                                           int columns, int rows) {
        if (columns <= 0 || rows <= 0) {
            return new PixelPoint(10f, 20f); // Default fallback
        }
        
        float charWidth = (float)pixelWidth / columns;
        float charHeight = (float)pixelHeight / rows;
        
        return new PixelPoint(charWidth, charHeight);
    }
}