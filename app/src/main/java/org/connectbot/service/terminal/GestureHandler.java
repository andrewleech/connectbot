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

import android.view.MotionEvent;
import android.view.VelocityTracker;

/**
 * Handles gesture recognition and processing for terminal input.
 * Supports arrow key gestures, selection gestures, and other terminal-specific gestures.
 */
public class GestureHandler {
    
    private final TerminalStateManager stateManager;
    private final CoordinateMapper coordinateMapper;
    private final InputHandler inputHandler;
    
    // Gesture detection parameters
    private static final float MIN_GESTURE_DISTANCE = 50f; // pixels
    private static final float MIN_GESTURE_VELOCITY = 100f; // pixels per second
    private static final long MAX_GESTURE_TIME = 1000; // milliseconds
    
    // Arrow gesture zone (left 2/3 of screen)
    private static final float ARROW_GESTURE_ZONE_RATIO = 0.66f;
    
    // Gesture state
    private GestureState currentGesture = GestureState.NONE;
    private float gestureStartX;
    private float gestureStartY;
    private long gestureStartTime;
    private VelocityTracker velocityTracker;
    
    /**
     * Possible gesture states
     */
    public enum GestureState {
        NONE,
        DETECTING,
        ARROW_UP,
        ARROW_DOWN,
        ARROW_LEFT,
        ARROW_RIGHT,
        SELECTION,
        SCROLL
    }
    
    /**
     * Gesture recognition result
     */
    public static class GestureResult {
        public final GestureState gesture;
        public final boolean consumed;
        public final String description;
        
        public GestureResult(GestureState gesture, boolean consumed, String description) {
            this.gesture = gesture;
            this.consumed = consumed;
            this.description = description;
        }
        
        public static GestureResult none() {
            return new GestureResult(GestureState.NONE, false, "No gesture");
        }
        
        public static GestureResult consumed(GestureState gesture, String description) {
            return new GestureResult(gesture, true, description);
        }
        
        public static GestureResult ignored(GestureState gesture, String description) {
            return new GestureResult(gesture, false, description);
        }
    }
    
    /**
     * Listener for gesture events
     */
    public interface GestureListener {
        /**
         * Called when a gesture is detected
         */
        void onGestureDetected(GestureState gesture, CoordinateMapper.PixelPoint location);
        
        /**
         * Called when gesture processing is complete
         */
        void onGestureComplete(GestureState gesture, boolean success);
        
        /**
         * Called when an arrow key gesture is performed
         */
        void onArrowKeyGesture(InputHandler.ArrowDirection direction, 
                             CoordinateMapper.PixelPoint location);
    }
    
    private GestureListener listener;
    
    /**
     * Create a gesture handler
     */
    public GestureHandler(TerminalStateManager stateManager, 
                         CoordinateMapper coordinateMapper, 
                         InputHandler inputHandler) {
        this.stateManager = stateManager;
        this.coordinateMapper = coordinateMapper;
        this.inputHandler = inputHandler;
    }
    
    /**
     * Set the gesture listener
     */
    public void setGestureListener(GestureListener listener) {
        this.listener = listener;
    }
    
    /**
     * Process a touch event and detect gestures
     */
    public GestureResult onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(event);
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(event);
            case MotionEvent.ACTION_UP:
                return handleTouchUp(event);
            case MotionEvent.ACTION_CANCEL:
                return handleTouchCancel(event);
            default:
                return GestureResult.none();
        }
    }
    
    /**
     * Handle touch down event
     */
    private GestureResult handleTouchDown(MotionEvent event) {
        gestureStartX = event.getX();
        gestureStartY = event.getY();
        gestureStartTime = event.getEventTime();
        currentGesture = GestureState.DETECTING;
        
        // Initialize velocity tracker
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(event);
        
        return GestureResult.none();
    }
    
    /**
     * Handle touch move event
     */
    private GestureResult handleTouchMove(MotionEvent event) {
        if (currentGesture != GestureState.DETECTING) {
            return GestureResult.none();
        }
        
        velocityTracker.addMovement(event);
        
        float deltaX = event.getX() - gestureStartX;
        float deltaY = event.getY() - gestureStartY;
        float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        
        // Check if we've moved far enough to be a gesture
        if (distance < MIN_GESTURE_DISTANCE) {
            return GestureResult.none();
        }
        
        // Check if we're in the arrow gesture zone
        if (isInArrowGestureZone(gestureStartX)) {
            GestureState arrowGesture = detectArrowGesture(deltaX, deltaY);
            if (arrowGesture != GestureState.NONE) {
                currentGesture = arrowGesture;
                return GestureResult.consumed(arrowGesture, "Arrow gesture detected");
            }
        }
        
        return GestureResult.none();
    }
    
    /**
     * Handle touch up event
     */
    private GestureResult handleTouchUp(MotionEvent event) {
        GestureResult result = GestureResult.none();
        
        if (currentGesture != GestureState.NONE && currentGesture != GestureState.DETECTING) {
            velocityTracker.addMovement(event);
            velocityTracker.computeCurrentVelocity(1000); // pixels per second
            
            float velocity = (float) Math.sqrt(
                velocityTracker.getXVelocity() * velocityTracker.getXVelocity() +
                velocityTracker.getYVelocity() * velocityTracker.getYVelocity()
            );
            
            // Process the gesture if it meets velocity requirements
            if (velocity >= MIN_GESTURE_VELOCITY) {
                result = processGesture(currentGesture, event);
            } else {
                result = GestureResult.ignored(currentGesture, "Velocity too low");
            }
        }
        
        // Clean up
        resetGestureState();
        return result;
    }
    
    /**
     * Handle touch cancel event
     */
    private GestureResult handleTouchCancel(MotionEvent event) {
        resetGestureState();
        return GestureResult.none();
    }
    
    /**
     * Check if a point is in the arrow gesture zone (left 2/3 of screen)
     */
    private boolean isInArrowGestureZone(float x) {
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        return x < (dims.pixelWidth * ARROW_GESTURE_ZONE_RATIO);
    }
    
    /**
     * Detect arrow gesture direction from delta movements
     */
    private GestureState detectArrowGesture(float deltaX, float deltaY) {
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(deltaY);
        
        // Determine primary direction
        if (absDeltaX > absDeltaY) {
            // Horizontal gesture
            return deltaX > 0 ? GestureState.ARROW_RIGHT : GestureState.ARROW_LEFT;
        } else {
            // Vertical gesture
            return deltaY < 0 ? GestureState.ARROW_UP : GestureState.ARROW_DOWN;
        }
    }
    
    /**
     * Process a detected gesture
     */
    private GestureResult processGesture(GestureState gesture, MotionEvent event) {
        CoordinateMapper.PixelPoint location = new CoordinateMapper.PixelPoint(event.getX(), event.getY());
        
        if (listener != null) {
            listener.onGestureDetected(gesture, location);
        }
        
        switch (gesture) {
            case ARROW_UP:
                return processArrowGesture(InputHandler.ArrowDirection.UP, location);
            case ARROW_DOWN:
                return processArrowGesture(InputHandler.ArrowDirection.DOWN, location);
            case ARROW_LEFT:
                return processArrowGesture(InputHandler.ArrowDirection.LEFT, location);
            case ARROW_RIGHT:
                return processArrowGesture(InputHandler.ArrowDirection.RIGHT, location);
            default:
                return GestureResult.ignored(gesture, "Unsupported gesture");
        }
    }
    
    /**
     * Process arrow key gesture
     */
    private GestureResult processArrowGesture(InputHandler.ArrowDirection direction, 
                                           CoordinateMapper.PixelPoint location) {
        // Use gesture context to preserve scroll position
        InputContext context = InputContext.gestureInput("arrow-" + direction.name().toLowerCase());
        InputHandler.InputResult result = inputHandler.processArrowKey(direction, context);
        
        if (listener != null) {
            listener.onArrowKeyGesture(direction, location);
            listener.onGestureComplete(getGestureStateForDirection(direction), result.success);
        }
        
        return GestureResult.consumed(
            getGestureStateForDirection(direction), 
            "Arrow " + direction + " gesture processed"
        );
    }
    
    /**
     * Get gesture state for arrow direction
     */
    private GestureState getGestureStateForDirection(InputHandler.ArrowDirection direction) {
        switch (direction) {
            case UP: return GestureState.ARROW_UP;
            case DOWN: return GestureState.ARROW_DOWN;
            case LEFT: return GestureState.ARROW_LEFT;
            case RIGHT: return GestureState.ARROW_RIGHT;
            default: return GestureState.NONE;
        }
    }
    
    /**
     * Reset gesture state
     */
    private void resetGestureState() {
        currentGesture = GestureState.NONE;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }
    
    /**
     * Get current gesture state
     */
    public GestureState getCurrentGesture() {
        return currentGesture;
    }
    
    /**
     * Check if gesture detection is enabled for arrow keys
     */
    public boolean isArrowGestureEnabled() {
        // This would typically check a preference setting
        return true; // Placeholder
    }
    
    /**
     * Enable or disable arrow gesture detection
     */
    public void setArrowGestureEnabled(boolean enabled) {
        // This would typically save to preferences
        // Placeholder implementation
    }
    
    /**
     * Get gesture statistics
     */
    public GestureStats getStats() {
        return new GestureStats(
            isArrowGestureEnabled(),
            ARROW_GESTURE_ZONE_RATIO,
            MIN_GESTURE_DISTANCE,
            MIN_GESTURE_VELOCITY
        );
    }
    
    /**
     * Gesture statistics
     */
    public static class GestureStats {
        public final boolean arrowGestureEnabled;
        public final float arrowGestureZoneRatio;
        public final float minGestureDistance;
        public final float minGestureVelocity;
        
        public GestureStats(boolean arrowGestureEnabled, float arrowGestureZoneRatio,
                          float minGestureDistance, float minGestureVelocity) {
            this.arrowGestureEnabled = arrowGestureEnabled;
            this.arrowGestureZoneRatio = arrowGestureZoneRatio;
            this.minGestureDistance = minGestureDistance;
            this.minGestureVelocity = minGestureVelocity;
        }
    }
}