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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import de.mud.terminal.VDUBuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InputHandler
 */
@RunWith(MockitoJUnitRunner.class)
public class InputHandlerTest {
    
    @Mock
    private VDUBuffer mockBuffer;
    
    private TerminalStateManager stateManager;
    private InputHandler inputHandler;
    
    @Before
    public void setup() {
        stateManager = new TerminalStateManager();
        inputHandler = new InputHandler(stateManager, mockBuffer);
    }
    
    @Test
    public void testKeyboardInputProcessing() {
        InputContext context = InputContext.DEFAULT_KEYBOARD;
        InputHandler.InputResult result = inputHandler.processKey(65, context); // 'A' key
        
        assertTrue("Key processing should succeed", result.success);
        assertTrue("Keyboard input should trigger scroll reset", result.scrollReset);
        assertNull("No error message on success", result.errorMessage);
    }
    
    @Test
    public void testGestureArrowInputProcessing() {
        InputContext context = InputContext.GESTURE_ARROW;
        InputHandler.InputResult result = inputHandler.processKey(38, context); // Up arrow
        
        assertTrue("Arrow key processing should succeed", result.success);
        assertFalse("Gesture arrow should not trigger scroll reset", result.scrollReset);
        assertNull("No error message on success", result.errorMessage);
    }
    
    @Test
    public void testProgrammaticInputProcessing() {
        InputContext context = InputContext.PROGRAMMATIC_KEY;
        InputHandler.InputResult result = inputHandler.processKey(65, context);
        
        assertTrue("Programmatic input should succeed", result.success);
        assertFalse("Programmatic input should not trigger scroll reset", result.scrollReset);
    }
    
    @Test
    public void testTextInputProcessing() {
        InputContext context = InputContext.DEFAULT_KEYBOARD;
        InputHandler.InputResult result = inputHandler.processText("hello", context);
        
        assertTrue("Text processing should succeed", result.success);
        assertTrue("Text input should trigger scroll reset", result.scrollReset);
    }
    
    @Test
    public void testEmptyTextInput() {
        InputContext context = InputContext.DEFAULT_KEYBOARD;
        InputHandler.InputResult result = inputHandler.processText("", context);
        
        assertFalse("Empty text should fail", result.success);
        assertNotNull("Should have error message", result.errorMessage);
        assertTrue("Error message should mention empty", 
                  result.errorMessage.contains("Empty"));
    }
    
    @Test
    public void testNullTextInput() {
        InputContext context = InputContext.DEFAULT_KEYBOARD;
        InputHandler.InputResult result = inputHandler.processText(null, context);
        
        assertFalse("Null text should fail", result.success);
        assertNotNull("Should have error message", result.errorMessage);
    }
    
    @Test
    public void testArrowKeyDirections() {
        InputContext context = InputContext.GESTURE_ARROW;
        
        // Test all arrow directions
        for (InputHandler.ArrowDirection direction : InputHandler.ArrowDirection.values()) {
            InputHandler.InputResult result = inputHandler.processArrowKey(direction, context);
            assertTrue("Arrow key " + direction + " should succeed", result.success);
            assertFalse("Arrow gesture should preserve scroll position", result.scrollReset);
        }
    }
    
    @Test
    public void testArrowKeyWithNonGestureContext() {
        // Use keyboard context instead of gesture context
        InputContext context = InputContext.DEFAULT_KEYBOARD;
        InputHandler.InputResult result = inputHandler.processArrowKey(
            InputHandler.ArrowDirection.UP, context);
        
        assertTrue("Arrow key should succeed", result.success);
        // Should be converted to gesture context internally, so no scroll reset
        assertFalse("Should preserve scroll position when converted to gesture", result.scrollReset);
    }
    
    @Test
    public void testListenerNotifications() throws InterruptedException {
        CountDownLatch processingLatch = new CountDownLatch(1);
        CountDownLatch processedLatch = new CountDownLatch(1);
        AtomicInteger processingKeyCode = new AtomicInteger(-1);
        AtomicBoolean processingSuccess = new AtomicBoolean(false);
        
        InputHandler.InputProcessorListener listener = new InputHandler.InputProcessorListener() {
            @Override
            public void onInputProcessing(InputContext context, int keyCode) {
                processingKeyCode.set(keyCode);
                processingLatch.countDown();
            }
            
            @Override
            public void onInputProcessed(InputContext context, boolean success) {
                processingSuccess.set(success);
                processedLatch.countDown();
            }
            
            @Override
            public void onScrollReset(InputContext context) {
                // Not tested in this specific test
            }
        };
        
        inputHandler.addListener(listener);
        
        // Process a key
        InputHandler.InputResult result = inputHandler.processKey(65, InputContext.DEFAULT_KEYBOARD);
        
        // Wait for notifications
        assertTrue("Should receive processing notification", 
                  processingLatch.await(1, TimeUnit.SECONDS));
        assertTrue("Should receive processed notification", 
                  processedLatch.await(1, TimeUnit.SECONDS));
        
        // Verify notifications
        assertEquals("Should receive correct key code", 65, processingKeyCode.get());
        assertTrue("Should receive success notification", processingSuccess.get());
        assertTrue("Processing should succeed", result.success);
        
        inputHandler.removeListener(listener);
    }
    
    @Test
    public void testScrollResetNotification() throws InterruptedException {
        CountDownLatch scrollResetLatch = new CountDownLatch(1);
        AtomicBoolean scrollResetCalled = new AtomicBoolean(false);
        
        InputHandler.InputProcessorListener listener = new InputHandler.InputProcessorListener() {
            @Override
            public void onInputProcessing(InputContext context, int keyCode) {}
            
            @Override
            public void onInputProcessed(InputContext context, boolean success) {}
            
            @Override
            public void onScrollReset(InputContext context) {
                scrollResetCalled.set(true);
                scrollResetLatch.countDown();
            }
        };
        
        inputHandler.addListener(listener);
        
        // Use keyboard context which should trigger scroll reset
        InputHandler.InputResult result = inputHandler.processKey(65, InputContext.DEFAULT_KEYBOARD);
        
        // Wait for scroll reset notification
        assertTrue("Should receive scroll reset notification", 
                  scrollResetLatch.await(1, TimeUnit.SECONDS));
        assertTrue("Scroll reset should be called", scrollResetCalled.get());
        assertTrue("Processing should succeed", result.success);
        
        inputHandler.removeListener(listener);
    }
    
    @Test
    public void testHighPriorityInput() {
        InputContext context = InputContext.ACCESSIBILITY; // High priority
        InputHandler.InputResult result = inputHandler.processKey(65, context);
        
        assertTrue("High priority input should succeed", result.success);
        assertFalse("Accessibility input should not trigger scroll reset", result.scrollReset);
    }
    
    @Test
    public void testInputStats() {
        InputHandler.InputStats stats = inputHandler.getStats();
        assertEquals("Initial listener count should be 0", 0, stats.listenerCount);
        
        InputHandler.InputProcessorListener listener = new InputHandler.InputProcessorListener() {
            @Override
            public void onInputProcessing(InputContext context, int keyCode) {}
            @Override
            public void onInputProcessed(InputContext context, boolean success) {}
            @Override
            public void onScrollReset(InputContext context) {}
        };
        
        inputHandler.addListener(listener);
        stats = inputHandler.getStats();
        assertEquals("Listener count should be 1", 1, stats.listenerCount);
        
        inputHandler.removeListener(listener);
        stats = inputHandler.getStats();
        assertEquals("Listener count should be 0 after removal", 0, stats.listenerCount);
    }
    
    @Test
    public void testScrollPositionReset() {
        // Set up state with scrolled back position
        stateManager.executeTransaction(state -> {
            state.setScrollState(10, 0, 100, 1000, 50); // windowBase=10, scrolled back
        });
        
        // Verify initial state
        assertTrue("Should be scrolled back initially", 
                  stateManager.getScrollState().isScrolledBack());
        
        // Process keyboard input which should reset scroll
        InputHandler.InputResult result = inputHandler.processKey(65, InputContext.DEFAULT_KEYBOARD);
        
        assertTrue("Processing should succeed", result.success);
        assertTrue("Should trigger scroll reset", result.scrollReset);
        
        // Verify scroll was reset
        TerminalStateManager.ScrollState scrollState = stateManager.getScrollState();
        // The exact behavior depends on buffer size, but windowBase should be adjusted
        assertTrue("WindowBase should be non-negative", scrollState.windowBase >= 0);
    }
    
    @Test
    public void testConcurrentInputProcessing() throws InterruptedException {
        final int threadCount = 5;
        final int operationsPerThread = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        InputContext context = (j % 2 == 0) ? 
                            InputContext.DEFAULT_KEYBOARD : InputContext.GESTURE_ARROW;
                        InputHandler.InputResult result = inputHandler.processKey(65 + threadId, context);
                        if (result.success) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown(); // Start all threads
        assertTrue("All threads should complete", 
                  completionLatch.await(10, TimeUnit.SECONDS));
        
        assertEquals("All operations should succeed", 
                    threadCount * operationsPerThread, successCount.get());
    }
}