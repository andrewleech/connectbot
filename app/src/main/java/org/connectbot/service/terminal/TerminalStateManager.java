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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized state management for terminal operations with thread safety
 * and atomic transactional updates.
 */
public class TerminalStateManager {
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();
    
    // Immutable state objects
    private volatile TerminalDimensions dimensions;
    private volatile ScrollState scrollState;
    private volatile SelectionState selectionState;
    private volatile RenderState renderState;
    
    // State change listeners
    private final List<StateChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * Immutable terminal dimensions state
     */
    public static class TerminalDimensions {
        public final int pixelWidth, pixelHeight;
        public final int columns, rows;
        public final float charWidth, charHeight;
        public final boolean forcedSize;
        
        public TerminalDimensions(int pixelWidth, int pixelHeight, 
                                 int columns, int rows,
                                 float charWidth, float charHeight,
                                 boolean forcedSize) {
            this.pixelWidth = pixelWidth;
            this.pixelHeight = pixelHeight;
            this.columns = columns;
            this.rows = rows;
            this.charWidth = charWidth;
            this.charHeight = charHeight;
            this.forcedSize = forcedSize;
        }
        
        public boolean isValid() {
            return pixelWidth > 0 && pixelHeight > 0 && 
                   columns > 0 && rows > 0 &&
                   charWidth > 0 && charHeight > 0;
        }
    }
    
    /**
     * Immutable scroll state
     */
    public static class ScrollState {
        public final int windowBase;
        public final int screenBase;
        public final int bufferSize;
        public final int maxBufferSize;
        public final int scrollMarker;
        
        public ScrollState(int windowBase, int screenBase, int bufferSize, 
                          int maxBufferSize, int scrollMarker) {
            this.windowBase = windowBase;
            this.screenBase = screenBase;
            this.bufferSize = bufferSize;
            this.maxBufferSize = maxBufferSize;
            this.scrollMarker = scrollMarker;
        }
        
        public boolean isValid() {
            return windowBase >= 0 && screenBase >= 0 && bufferSize >= 0 &&
                   maxBufferSize >= bufferSize && windowBase <= screenBase;
        }
        
        public boolean isScrolledBack() {
            return windowBase < screenBase;
        }
    }
    
    /**
     * Immutable selection state
     */
    public static class SelectionState {
        public final boolean active;
        public final int startLine, startColumn;
        public final int endLine, endColumn;
        public final boolean isBufferCoordinates;
        
        public SelectionState() {
            this.active = false;
            this.startLine = 0;
            this.startColumn = 0;
            this.endLine = 0;
            this.endColumn = 0;
            this.isBufferCoordinates = true;
        }
        
        public SelectionState(int startLine, int startColumn, int endLine, int endColumn, 
                            boolean isBufferCoordinates) {
            this.active = true;
            this.startLine = Math.min(startLine, endLine);
            this.startColumn = startLine <= endLine ? startColumn : endColumn;
            this.endLine = Math.max(startLine, endLine);
            this.endColumn = startLine <= endLine ? endColumn : startColumn;
            this.isBufferCoordinates = isBufferCoordinates;
        }
        
        public boolean isValid() {
            if (!active) return true;
            return startLine >= 0 && startColumn >= 0 && 
                   endLine >= startLine && endColumn >= 0;
        }
    }
    
    /**
     * Immutable render state
     */
    public static class RenderState {
        public final boolean needsFullRedraw;
        public final Bitmap bitmap;
        public final int cursorRow, cursorColumn;
        public final boolean showCursor;
        
        public RenderState(boolean needsFullRedraw, Bitmap bitmap, 
                          int cursorRow, int cursorColumn, boolean showCursor) {
            this.needsFullRedraw = needsFullRedraw;
            this.bitmap = bitmap;
            this.cursorRow = cursorRow;
            this.cursorColumn = cursorColumn;
            this.showCursor = showCursor;
        }
        
        public boolean isValid() {
            return cursorRow >= 0 && cursorColumn >= 0;
        }
    }
    
    /**
     * Exception thrown when state validation fails
     */
    public static class StateException extends RuntimeException {
        public StateException(String message) {
            super(message);
        }
        
        public StateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Transaction interface for atomic state updates
     */
    @FunctionalInterface
    public interface StateTransaction {
        void execute(MutableTerminalState state) throws StateException;
    }
    
    /**
     * Read-only state access interface
     */
    @FunctionalInterface
    public interface StateReader<T> {
        T read(ImmutableStateView state) throws StateException;
    }
    
    /**
     * State change listener interface
     */
    public interface StateChangeListener {
        void onDimensionsChanged(TerminalDimensions oldDims, TerminalDimensions newDims);
        void onScrollStateChanged(ScrollState oldState, ScrollState newState);
        void onSelectionChanged(SelectionState oldState, SelectionState newState);
        void onRenderStateChanged(RenderState oldState, RenderState newState);
    }
    
    /**
     * Constructor with default state
     */
    public TerminalStateManager() {
        this.dimensions = new TerminalDimensions(0, 0, 80, 24, 10f, 20f, false);
        this.scrollState = new ScrollState(0, 0, 24, 1000, 0);
        this.selectionState = new SelectionState();
        this.renderState = new RenderState(true, null, 0, 0, true);
    }
    
    /**
     * Execute atomic state update transaction
     */
    public void executeTransaction(StateTransaction transaction) {
        stateLock.writeLock().lock();
        try {
            MutableTerminalState mutable = new MutableTerminalState(this);
            transaction.execute(mutable);
            
            if (!mutable.validate()) {
                throw new StateException("Invalid state after transaction");
            }
            
            commitState(mutable);
            notifyListeners(mutable);
            
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Read current state with shared lock
     */
    public <T> T readState(StateReader<T> reader) {
        stateLock.readLock().lock();
        try {
            return reader.read(new ImmutableStateView(this));
        } finally {
            stateLock.readLock().unlock();
        }
    }
    
    /**
     * Get current dimensions (thread-safe)
     */
    public TerminalDimensions getDimensions() {
        return dimensions;
    }
    
    /**
     * Get current scroll state (thread-safe)
     */
    public ScrollState getScrollState() {
        return scrollState;
    }
    
    /**
     * Get current selection state (thread-safe)
     */
    public SelectionState getSelectionState() {
        return selectionState;
    }
    
    /**
     * Get current render state (thread-safe)
     */
    public RenderState getRenderState() {
        return renderState;
    }
    
    /**
     * Add state change listener
     */
    public void addStateChangeListener(StateChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove state change listener
     */
    public void removeStateChangeListener(StateChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Internal method to commit state changes
     */
    private void commitState(MutableTerminalState mutable) {
        this.dimensions = mutable.getNewDimensions();
        this.scrollState = mutable.getNewScrollState();
        this.selectionState = mutable.getNewSelectionState();
        this.renderState = mutable.getNewRenderState();
    }
    
    /**
     * Internal method to notify listeners of changes
     */
    private void notifyListeners(MutableTerminalState mutable) {
        if (!listeners.isEmpty()) {
            for (StateChangeListener listener : listeners) {
                try {
                    if (mutable.dimensionsChanged()) {
                        listener.onDimensionsChanged(mutable.getOldDimensions(), 
                                                   mutable.getNewDimensions());
                    }
                    if (mutable.scrollStateChanged()) {
                        listener.onScrollStateChanged(mutable.getOldScrollState(), 
                                                    mutable.getNewScrollState());
                    }
                    if (mutable.selectionStateChanged()) {
                        listener.onSelectionChanged(mutable.getOldSelectionState(), 
                                                  mutable.getNewSelectionState());
                    }
                    if (mutable.renderStateChanged()) {
                        listener.onRenderStateChanged(mutable.getOldRenderState(), 
                                                    mutable.getNewRenderState());
                    }
                } catch (Exception e) {
                    // Log but don't fail transaction due to listener error
                    android.util.Log.e("TerminalStateManager", 
                                     "Error in state change listener", e);
                }
            }
        }
    }
}