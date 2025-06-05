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
 * Handles input processing with context awareness for different input sources.
 * This class manages key events, gestures, and programmatic input while maintaining
 * proper scroll position and echo behavior based on input context.
 */
public class InputHandler {
    
    private final TerminalStateManager stateManager;
    private final VDUBuffer buffer;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<InputProcessorListener> listeners = new ArrayList<>();
    
    /**
     * Listener interface for input processing events
     */
    public interface InputProcessorListener {
        /**
         * Called when input is being processed
         * @param context The input context
         * @param keyCode The key code being processed
         */
        void onInputProcessing(InputContext context, int keyCode);
        
        /**
         * Called when input processing is complete
         * @param context The input context
         * @param success Whether processing was successful
         */
        void onInputProcessed(InputContext context, boolean success);
        
        /**
         * Called when scroll position should be reset
         * @param context The input context that triggered the reset
         */
        void onScrollReset(InputContext context);
    }
    
    /**
     * Result of input processing
     */
    public static class InputResult {
        public final boolean success;
        public final boolean scrollReset;
        public final String errorMessage;
        
        public InputResult(boolean success, boolean scrollReset, String errorMessage) {
            this.success = success;
            this.scrollReset = scrollReset;
            this.errorMessage = errorMessage;
        }
        
        public static InputResult success(boolean scrollReset) {
            return new InputResult(true, scrollReset, null);
        }
        
        public static InputResult failure(String error) {
            return new InputResult(false, false, error);
        }
    }
    
    /**
     * Create an input handler for the given buffer and state manager
     */
    public InputHandler(TerminalStateManager stateManager, VDUBuffer buffer) {
        this.stateManager = stateManager;
        this.buffer = buffer;
    }
    
    /**
     * Process a key input with the given context
     * @param keyCode The key code to process
     * @param context The input context
     * @return The result of processing
     */
    public InputResult processKey(int keyCode, InputContext context) {
        lock.writeLock().lock();
        try {
            notifyProcessing(context, keyCode);
            
            // Check if we should preserve scroll position
            boolean shouldResetScroll = context.shouldTriggerScrollReset();
            
            // Handle scroll reset before processing input
            if (shouldResetScroll) {
                resetScrollPosition(context);
            }
            
            // Process the actual key input based on context
            boolean success = processKeyInternal(keyCode, context);
            
            notifyProcessed(context, success);
            
            if (success && shouldResetScroll) {
                notifyScrollReset(context);
            }
            
            return InputResult.success(shouldResetScroll);
            
        } catch (Exception e) {
            notifyProcessed(context, false);
            return InputResult.failure("Input processing failed: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Process a string input with the given context
     * @param text The text to process
     * @param context The input context
     * @return The result of processing
     */
    public InputResult processText(String text, InputContext context) {
        if (text == null || text.isEmpty()) {
            return InputResult.failure("Empty text input");
        }
        
        lock.writeLock().lock();
        try {
            boolean shouldResetScroll = context.shouldTriggerScrollReset();
            
            // Handle scroll reset before processing input
            if (shouldResetScroll) {
                resetScrollPosition(context);
            }
            
            // Process each character in the text
            for (char c : text.toCharArray()) {
                boolean success = processKeyInternal((int) c, context);
                if (!success) {
                    return InputResult.failure("Failed to process character: " + c);
                }
            }
            
            if (shouldResetScroll) {
                notifyScrollReset(context);
            }
            
            return InputResult.success(shouldResetScroll);
            
        } catch (Exception e) {
            return InputResult.failure("Text processing failed: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Process arrow key gesture input
     * @param direction The arrow direction (up, down, left, right)
     * @param context The input context
     * @return The result of processing
     */
    public InputResult processArrowKey(ArrowDirection direction, InputContext context) {
        lock.writeLock().lock();
        try {
            int keyCode = getArrowKeyCode(direction);
            
            // Arrow keys from gestures should preserve scroll position
            if (!context.shouldPreserveScrollPosition()) {
                context = InputContext.gestureInput("arrow-" + direction.name().toLowerCase());
            }
            
            return processKey(keyCode, context);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Arrow key directions
     */
    public enum ArrowDirection {
        UP, DOWN, LEFT, RIGHT
    }
    
    /**
     * Add a listener for input processing events
     */
    public void addListener(InputProcessorListener listener) {
        lock.writeLock().lock();
        try {
            listeners.add(listener);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Remove a listener for input processing events
     */
    public void removeListener(InputProcessorListener listener) {
        lock.writeLock().lock();
        try {
            listeners.remove(listener);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Internal key processing implementation
     */
    private boolean processKeyInternal(int keyCode, InputContext context) {
        try {
            // Skip echo if requested by context
            if (context.shouldSkipEcho()) {
                // For programmatic input, we might want to bypass normal echo
                return processKeyWithoutEcho(keyCode, context);
            }
            
            // Normal key processing through buffer
            if (buffer != null) {
                // Use appropriate method based on context priority
                if (context.getPriority() == InputContext.Priority.HIGH) {
                    return processHighPriorityKey(keyCode, context);
                } else {
                    return processNormalKey(keyCode, context);
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Process key without echo (for programmatic input)
     */
    private boolean processKeyWithoutEcho(int keyCode, InputContext context) {
        // This would typically send the key directly to the transport
        // without local echo to the buffer
        return true; // Placeholder implementation
    }
    
    /**
     * Process high priority key input
     */
    private boolean processHighPriorityKey(int keyCode, InputContext context) {
        // High priority input (e.g., accessibility) gets processed immediately
        return processNormalKey(keyCode, context);
    }
    
    /**
     * Process normal key input
     */
    private boolean processNormalKey(int keyCode, InputContext context) {
        // Normal key processing - this would typically involve
        // converting the key code to appropriate terminal sequences
        return true; // Placeholder implementation
    }
    
    /**
     * Reset scroll position to bottom
     */
    private void resetScrollPosition(InputContext context) {
        stateManager.executeTransaction(state -> {
            // Reset window base to show the bottom of the buffer
            int bufferSize = state.getBufferSize();
            int rows = state.getRows();
            int newWindowBase = Math.max(0, bufferSize - rows);
            state.setWindowBase(newWindowBase);
        });
    }
    
    /**
     * Get the key code for an arrow direction
     */
    private int getArrowKeyCode(ArrowDirection direction) {
        switch (direction) {
            case UP: return 38;    // VK_UP
            case DOWN: return 40;  // VK_DOWN
            case LEFT: return 37;  // VK_LEFT
            case RIGHT: return 39; // VK_RIGHT
            default: return 0;
        }
    }
    
    /**
     * Notify listeners that input is being processed
     */
    private void notifyProcessing(InputContext context, int keyCode) {
        for (InputProcessorListener listener : listeners) {
            try {
                listener.onInputProcessing(context, keyCode);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }
    
    /**
     * Notify listeners that input processing is complete
     */
    private void notifyProcessed(InputContext context, boolean success) {
        for (InputProcessorListener listener : listeners) {
            try {
                listener.onInputProcessed(context, success);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }
    
    /**
     * Notify listeners that scroll position was reset
     */
    private void notifyScrollReset(InputContext context) {
        for (InputProcessorListener listener : listeners) {
            try {
                listener.onScrollReset(context);
            } catch (Exception e) {
                // Ignore listener exceptions
            }
        }
    }
    
    /**
     * Check if the input handler is currently processing input
     */
    public boolean isProcessing() {
        return lock.readLock().tryLock();
    }
    
    /**
     * Get current input processing statistics
     */
    public InputStats getStats() {
        lock.readLock().lock();
        try {
            return new InputStats(listeners.size());
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Input processing statistics
     */
    public static class InputStats {
        public final int listenerCount;
        
        public InputStats(int listenerCount) {
            this.listenerCount = listenerCount;
        }
    }
}