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

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.TimeUnit;
import de.mud.terminal.VDUBuffer;

/**
 * Thread-safe access wrapper for VDUBuffer operations.
 * Provides controlled access with minimal lock contention using ReadWriteLock.
 */
public class SynchronizedBufferAccess {
    private final ReadWriteLock bufferLock = new ReentrantReadWriteLock();
    private final VDUBuffer buffer;
    
    // Timeout for lock acquisition (to prevent deadlocks)
    private static final long LOCK_TIMEOUT_MS = 5000;
    
    /**
     * Exception for buffer access failures
     */
    public static class BufferAccessException extends RuntimeException {
        public BufferAccessException(String message) {
            super(message);
        }
        
        public BufferAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Functional interface for buffer read operations
     */
    @FunctionalInterface
    public interface BufferReader<T> {
        T read(VDUBuffer buffer) throws Exception;
    }
    
    /**
     * Functional interface for buffer write operations
     */
    @FunctionalInterface
    public interface BufferWriter {
        void write(VDUBuffer buffer) throws Exception;
    }
    
    /**
     * Constructor
     */
    public SynchronizedBufferAccess(VDUBuffer buffer) {
        this.buffer = buffer;
    }
    
    /**
     * Execute read operation with shared lock
     */
    public <T> T readFromBuffer(BufferReader<T> reader) {
        try {
            if (!bufferLock.readLock().tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new BufferAccessException("Failed to acquire read lock within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BufferAccessException("Interrupted while acquiring read lock", e);
        }
        
        try {
            return reader.read(buffer);
        } catch (Exception e) {
            throw new BufferAccessException("Read operation failed", e);
        } finally {
            bufferLock.readLock().unlock();
        }
    }
    
    /**
     * Execute write operation with exclusive lock
     */
    public void writeToBuffer(BufferWriter writer) {
        try {
            if (!bufferLock.writeLock().tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new BufferAccessException("Failed to acquire write lock within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BufferAccessException("Interrupted while acquiring write lock", e);
        }
        
        try {
            writer.write(buffer);
        } catch (Exception e) {
            throw new BufferAccessException("Write operation failed", e);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }
    
    /**
     * Convenience method: Get a single character
     */
    public char getChar(int column, int line) {
        return readFromBuffer(buffer -> buffer.getChar(column, line));
    }
    
    /**
     * Convenience method: Get character attributes
     */
    public long getAttributes(int column, int line) {
        return readFromBuffer(buffer -> buffer.getAttributes(column, line));
    }
    
    /**
     * Convenience method: Put a single character
     */
    public void putChar(int column, int line, char ch, long attributes) {
        writeToBuffer(buffer -> buffer.putChar(column, line, ch, attributes));
    }
    
    /**
     * Convenience method: Put a string
     */
    public void putString(int column, int line, String text, long attributes) {
        writeToBuffer(buffer -> buffer.putString(column, line, text, attributes));
    }
    
    /**
     * Bulk read operation for efficiency
     */
    public String readLines(int startLine, int count) {
        return readFromBuffer(buffer -> {
            StringBuilder result = new StringBuilder();
            int maxLines = Math.min(count, buffer.getRows() - startLine);
            
            for (int i = 0; i < maxLines; i++) {
                int currentLine = startLine + i;
                for (int j = 0; j < buffer.getColumns(); j++) {
                    result.append(buffer.getChar(j, currentLine));
                }
                if (i < maxLines - 1) {
                    result.append('\n');
                }
            }
            return result.toString();
        });
    }
    
    /**
     * Read a rectangular region
     */
    public String readRegion(int startColumn, int startLine, int width, int height) {
        return readFromBuffer(buffer -> {
            StringBuilder result = new StringBuilder();
            int maxWidth = Math.min(width, buffer.getColumns() - startColumn);
            int maxHeight = Math.min(height, buffer.getRows() - startLine);
            
            for (int row = 0; row < maxHeight; row++) {
                int currentLine = startLine + row;
                for (int col = 0; col < maxWidth; col++) {
                    int currentColumn = startColumn + col;
                    result.append(buffer.getChar(currentColumn, currentLine));
                }
                if (row < maxHeight - 1) {
                    result.append('\n');
                }
            }
            return result.toString();
        });
    }
    
    /**
     * Get buffer dimensions safely
     */
    public BufferDimensions getDimensions() {
        return readFromBuffer(buffer -> 
            new BufferDimensions(
                buffer.getColumns(),
                buffer.getRows(),
                buffer.getWindowBase(),
                buffer.getScreenBase(),
                buffer.getBufferSize()
            )
        );
    }
    
    /**
     * Set window base (scroll position)
     */
    public void setWindowBase(int windowBase) {
        writeToBuffer(buffer -> buffer.setWindowBase(windowBase));
    }
    
    /**
     * Insert line at position
     */
    public void insertLine(int line) {
        writeToBuffer(buffer -> buffer.insertLine(line));
    }
    
    /**
     * Delete line at position
     */
    public void deleteLine(int line) {
        writeToBuffer(buffer -> buffer.deleteLine(line));
    }
    
    /**
     * Clear a rectangular area
     */
    public void clearArea(int column, int line, int width, int height) {
        writeToBuffer(buffer -> buffer.deleteArea(column, line, width, height));
    }
    
    /**
     * Set cursor position
     */
    public void setCursorPosition(int column, int line) {
        writeToBuffer(buffer -> buffer.setCursorPosition(column, line));
    }
    
    /**
     * Get cursor position
     */
    public CursorPosition getCursorPosition() {
        return readFromBuffer(buffer -> 
            new CursorPosition(buffer.getCursorColumn(), buffer.getCursorRow())
        );
    }
    
    /**
     * Set screen size
     */
    public void setScreenSize(int width, int height, boolean broadcast) {
        writeToBuffer(buffer -> buffer.setScreenSize(width, height, broadcast));
    }
    
    /**
     * Check if cursor is visible
     */
    public boolean isCursorVisible() {
        return readFromBuffer(buffer -> buffer.isCursorVisible());
    }
    
    /**
     * Set cursor visibility
     */
    public void showCursor(boolean visible) {
        writeToBuffer(buffer -> buffer.showCursor(visible));
    }
    
    /**
     * Immutable buffer dimensions
     */
    public static class BufferDimensions {
        public final int columns;
        public final int rows;
        public final int windowBase;
        public final int screenBase;
        public final int bufferSize;
        
        public BufferDimensions(int columns, int rows, int windowBase, 
                               int screenBase, int bufferSize) {
            this.columns = columns;
            this.rows = rows;
            this.windowBase = windowBase;
            this.screenBase = screenBase;
            this.bufferSize = bufferSize;
        }
        
        public boolean isScrolledBack() {
            return windowBase < screenBase;
        }
        
        @Override
        public String toString() {
            return String.format("BufferDimensions{cols=%d, rows=%d, window=%d, screen=%d, size=%d}",
                               columns, rows, windowBase, screenBase, bufferSize);
        }
    }
    
    /**
     * Immutable cursor position
     */
    public static class CursorPosition {
        public final int column;
        public final int row;
        
        public CursorPosition(int column, int row) {
            this.column = column;
            this.row = row;
        }
        
        @Override
        public String toString() {
            return String.format("CursorPosition(%d, %d)", column, row);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CursorPosition that = (CursorPosition) obj;
            return column == that.column && row == that.row;
        }
        
        @Override
        public int hashCode() {
            return column * 31 + row;
        }
    }
    
    /**
     * Get the underlying buffer (for migration purposes only)
     * WARNING: Direct access bypasses synchronization!
     */
    public VDUBuffer getUnsafeBuffer() {
        return buffer;
    }
    
    /**
     * Execute multiple operations atomically
     */
    public void executeAtomicWrite(BufferWriter... writers) {
        writeToBuffer(buffer -> {
            for (BufferWriter writer : writers) {
                writer.write(buffer);
            }
        });
    }
    
    /**
     * Check if we can acquire read lock without blocking
     */
    public boolean canRead() {
        boolean acquired = bufferLock.readLock().tryLock();
        if (acquired) {
            bufferLock.readLock().unlock();
        }
        return acquired;
    }
    
    /**
     * Check if we can acquire write lock without blocking
     */
    public boolean canWrite() {
        boolean acquired = bufferLock.writeLock().tryLock();
        if (acquired) {
            bufferLock.writeLock().unlock();
        }
        return acquired;
    }
}