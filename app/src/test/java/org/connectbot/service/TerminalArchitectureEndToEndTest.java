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

package org.connectbot.service;

import org.connectbot.service.terminal.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import de.mud.terminal.VDUBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration tests for the complete terminal architecture.
 * These tests validate that all components work together correctly under
 * various scenarios including concurrent access, state changes, and error conditions.
 */
@RunWith(MockitoJUnitRunner.class)
public class TerminalArchitectureEndToEndTest {
    
    @Mock
    private VDUBuffer mockBuffer;
    
    private TerminalStateManager stateManager;
    private CoordinateMapper coordinateMapper;
    private InputHandler inputHandler;
    private SelectionManager selectionManager;
    private GestureHandler gestureHandler;
    private RenderSnapshot.Factory snapshotFactory;
    
    @Before
    public void setup() {
        // Initialize the complete architecture
        stateManager = new TerminalStateManager();
        coordinateMapper = new CoordinateMapper(stateManager);
        inputHandler = new InputHandler(stateManager, mockBuffer);
        selectionManager = new SelectionManager(stateManager, coordinateMapper, mockBuffer);
        gestureHandler = new GestureHandler(stateManager, coordinateMapper, inputHandler);
        snapshotFactory = new RenderSnapshot.Factory(stateManager, selectionManager, coordinateMapper);
        
        // Set up default terminal dimensions
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
        });
        
        // Mock buffer to return test characters
        when(mockBuffer.getChar(anyInt(), anyInt())).thenReturn('A');
    }
    
    @Test
    public void testCompleteUserInteractionFlow() {
        // Simulate a complete user interaction: touch, selection, input
        
        // 1. User touches screen to start selection
        CoordinateMapper.PixelPoint startPoint = new CoordinateMapper.PixelPoint(105f, 130f);
        selectionManager.startSelection(startPoint);
        
        assertTrue("Selection should be active", selectionManager.isSelectionActive());
        
        // 2. User drags to extend selection
        CoordinateMapper.PixelPoint endPoint = new CoordinateMapper.PixelPoint(205f, 180f);
        selectionManager.updateSelection(endPoint);
        
        SelectionManager.SelectionArea area = selectionManager.getCurrentSelectionArea();
        assertNotNull("Selection area should exist", area);
        assertFalse("Selection should not be empty", area.isEmpty());
        
        // 3. Extract selected text
        String selectedText = selectionManager.getSelectedText();
        assertNotNull("Selected text should not be null", selectedText);
        
        // 4. User types a key
        InputHandler.InputResult inputResult = inputHandler.processKey(65, InputContext.DEFAULT_KEYBOARD);
        assertTrue("Key processing should succeed", inputResult.success);
        
        // 5. Create render snapshot
        RenderSnapshot snapshot = snapshotFactory.createSnapshot();
        assertNotNull("Snapshot should be created", snapshot);
        assertTrue("Should have changes to render", snapshot.getChangedLineCount() > 0);
        
        // 6. Clear selection
        selectionManager.clearSelection();
        assertFalse("Selection should not be active", selectionManager.isSelectionActive());
    }
    
    @Test
    public void testCoordinateSystemIntegration() {
        // Test complete coordinate system integration across all components
        
        // Set up scroll offset
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        // Test pixel to character conversion
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(155f, 180f);
        CoordinateMapper.CharPoint charPoint = coordinateMapper.pixelToCharacter(pixel);
        CoordinateMapper.BufferPoint bufferPoint = coordinateMapper.pixelToBuffer(pixel);
        
        assertEquals("Character column should be correct", 15, charPoint.column);
        assertEquals("Character row should be correct", 7, charPoint.row);
        assertEquals("Buffer line should include scroll offset", 17, bufferPoint.line);
        
        // Test selection using these coordinates
        selectionManager.startSelection(pixel);
        assertTrue("Selection should be active", selectionManager.isSelectionActive());
        
        // Test that selection uses buffer coordinates
        assertTrue("Point should be selected", selectionManager.isPointSelected(bufferPoint));
        
        // Test coordinate validation
        assertTrue("Character point should be valid", coordinateMapper.isValidCharacter(charPoint));
        assertTrue("Buffer point should be visible", coordinateMapper.isVisible(bufferPoint));
    }
    
    @Test
    public void testStateTransactionIntegrity() {
        // Test that all state changes are atomic and consistent
        
        final int numThreads = 5;
        final int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Perform state transaction
                        stateManager.executeTransaction(state -> {
                            state.setDimensions(800 + threadId, 600 + threadId, 
                                              80 + threadId, 24 + threadId, 
                                              10f + threadId, 25f + threadId, false);
                            state.setWindowBase(threadId * j);
                        });
                        
                        // Verify state consistency
                        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
                        if (dims.columns > 80 && dims.rows > 24) {
                            successCount.incrementAndGet();
                        }
                        
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    // Log error but continue
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
            assertTrue("Some operations should succeed", successCount.get() > 0);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    public void testConcurrentSelectionAndInput() {
        // Test concurrent selection and input operations
        
        final int numOperations = 50;
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger selectionCount = new AtomicInteger(0);
        AtomicInteger inputCount = new AtomicInteger(0);
        AtomicInteger coordinateCount = new AtomicInteger(0);
        
        // Selection thread
        executor.submit(() -> {
            try {
                for (int i = 0; i < numOperations; i++) {
                    CoordinateMapper.PixelPoint start = new CoordinateMapper.PixelPoint(
                        100f + i, 100f + i);
                    CoordinateMapper.PixelPoint end = new CoordinateMapper.PixelPoint(
                        200f + i, 150f + i);
                    
                    selectionManager.startSelection(start);
                    selectionManager.updateSelection(end);
                    
                    if (selectionManager.isSelectionActive()) {
                        selectionCount.incrementAndGet();
                    }
                    
                    selectionManager.clearSelection();
                    Thread.sleep(5);
                }
            } catch (Exception e) {
                // Continue
            } finally {
                latch.countDown();
            }
        });
        
        // Input thread
        executor.submit(() -> {
            try {
                for (int i = 0; i < numOperations; i++) {
                    InputHandler.InputResult result = inputHandler.processKey(
                        65 + (i % 26), InputContext.DEFAULT_KEYBOARD);
                    
                    if (result.success) {
                        inputCount.incrementAndGet();
                    }
                    
                    Thread.sleep(3);
                }
            } catch (Exception e) {
                // Continue
            } finally {
                latch.countDown();
            }
        });
        
        // Coordinate conversion thread
        executor.submit(() -> {
            try {
                for (int i = 0; i < numOperations; i++) {
                    CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(
                        50f + i * 2, 50f + i * 2);
                    CoordinateMapper.CharPoint charPoint = coordinateMapper.pixelToCharacter(pixel);
                    
                    if (charPoint.column >= 0 && charPoint.row >= 0) {
                        coordinateCount.incrementAndGet();
                    }
                    
                    Thread.sleep(2);
                }
            } catch (Exception e) {
                // Continue
            } finally {
                latch.countDown();
            }
        });
        
        try {
            assertTrue("All threads should complete", latch.await(15, TimeUnit.SECONDS));
            assertTrue("Selection operations should succeed", selectionCount.get() > 0);
            assertTrue("Input operations should succeed", inputCount.get() > 0);
            assertTrue("Coordinate operations should succeed", coordinateCount.get() > 0);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        } finally {
            executor.shutdown();
        }
    }
    
    @Test
    public void testRenderingPipeline() {
        // Test complete rendering pipeline with state changes
        
        // Make some changes
        stateManager.executeTransaction(state -> {
            state.setRenderState(true, true, 15, 10);
        });
        
        // Start selection
        selectionManager.startSelection(new CoordinateMapper.PixelPoint(100f, 100f));
        selectionManager.updateSelection(new CoordinateMapper.PixelPoint(200f, 150f));
        
        // Create snapshot
        RenderSnapshot snapshot = snapshotFactory.createSnapshot();
        
        assertNotNull("Snapshot should be created", snapshot);
        assertTrue("Should need full redraw", snapshot.needsFullRedraw());
        assertTrue("Should have selection", snapshot.hasSelection());
        assertTrue("Should draw cursor", snapshot.shouldDrawCursor());
        
        // Test incremental snapshot
        RenderSnapshot incrementalSnapshot = snapshotFactory.createIncrementalSnapshot(5, 10);
        
        assertNotNull("Incremental snapshot should be created", incrementalSnapshot);
        assertEquals("Should have 6 changed lines", 6, incrementalSnapshot.getChangedLineCount());
        
        // Test snapshot stats
        RenderSnapshot.RenderStats stats = snapshot.getStats();
        assertTrue("Should have positive character count", stats.changedCharacters > 0);
        assertTrue("Should have reasonable update ratio", 
                  stats.getUpdateRatio() > 0 && stats.getUpdateRatio() <= 1.0);
    }
    
    @Test
    public void testErrorRecovery() {
        // Test system behavior under error conditions
        
        // Test with invalid coordinates
        CoordinateMapper.PixelPoint invalidPixel = new CoordinateMapper.PixelPoint(-100f, -100f);
        CoordinateMapper.CharPoint result = coordinateMapper.pixelToCharacter(invalidPixel);
        
        // Should handle gracefully
        assertTrue("Invalid coordinates should be handled", result.column >= 0 && result.row >= 0);
        
        // Test selection with invalid coordinates
        selectionManager.startSelection(invalidPixel);
        assertTrue("Selection should still work with invalid coords", 
                  selectionManager.isSelectionActive());
        
        // Test state consistency after errors
        TerminalStateManager.TerminalDimensions dimsBefore = stateManager.getDimensions();
        
        try {
            stateManager.executeTransaction(state -> {
                state.setDimensions(-1, -1, -1, -1, -1f, -1f, false);
                throw new RuntimeException("Simulated error");
            });
        } catch (Exception e) {
            // Expected
        }
        
        TerminalStateManager.TerminalDimensions dimsAfter = stateManager.getDimensions();
        assertEquals("State should be unchanged after failed transaction", 
                    dimsBefore.columns, dimsAfter.columns);
    }
    
    @Test
    public void testMemoryAndPerformance() {
        // Test memory usage and performance characteristics
        
        long startTime = System.currentTimeMillis();
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        // Perform many operations
        final int operationCount = 1000;
        
        for (int i = 0; i < operationCount; i++) {
            // State operations
            stateManager.executeTransaction(state -> {
                state.setWindowBase(i % 100);
            });
            
            // Coordinate operations
            CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(
                i % 400, i % 300);
            coordinateMapper.pixelToCharacter(pixel);
            
            // Selection operations
            if (i % 10 == 0) {
                selectionManager.startSelection(pixel);
                selectionManager.updateSelection(new CoordinateMapper.PixelPoint(
                    pixel.x + 50, pixel.y + 25));
                selectionManager.clearSelection();
            }
            
            // Input operations
            inputHandler.processKey(65 + (i % 26), InputContext.DEFAULT_KEYBOARD);
            
            // Create snapshots occasionally
            if (i % 50 == 0) {
                snapshotFactory.createSnapshot();
            }
        }
        
        long endTime = System.currentTimeMillis();
        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        long duration = endTime - startTime;
        long memoryIncrease = finalMemory - initialMemory;
        
        // Performance assertions
        assertTrue("Operations should complete in reasonable time", duration < 5000); // 5 seconds
        
        // Memory usage should be reasonable (less than 10MB increase)
        assertTrue("Memory usage should be reasonable", memoryIncrease < 10 * 1024 * 1024);
        
        double opsPerSecond = (double) operationCount / (duration / 1000.0);
        assertTrue("Should handle reasonable ops per second", opsPerSecond > 100);
    }
    
    @Test
    public void testScrollingConsistency() {
        // Test coordinate consistency during scrolling operations
        
        // Start with some scroll offset
        stateManager.executeTransaction(state -> {
            state.setWindowBase(20);
        });
        
        // Select some text
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(100f, 100f);
        selectionManager.startSelection(pixel);
        selectionManager.updateSelection(new CoordinateMapper.PixelPoint(200f, 150f));
        
        SelectionManager.SelectionArea initialArea = selectionManager.getCurrentSelectionArea();
        assertNotNull("Initial selection should exist", initialArea);
        
        // Change scroll position
        stateManager.executeTransaction(state -> {
            state.setWindowBase(30);
        });
        
        // Selection should remain consistent in buffer coordinates
        SelectionManager.SelectionArea afterScrollArea = selectionManager.getCurrentSelectionArea();
        assertNotNull("Selection should persist after scroll", afterScrollArea);
        assertEquals("Selection buffer coordinates should be unchanged",
                    initialArea.topLeft.line, afterScrollArea.topLeft.line);
        
        // Test coordinate mapping consistency
        CoordinateMapper.BufferPoint bufferPoint = new CoordinateMapper.BufferPoint(35, 10);
        boolean visibleBefore = coordinateMapper.isVisible(bufferPoint);
        
        // Change window base again
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        boolean visibleAfter = coordinateMapper.isVisible(bufferPoint);
        assertTrue("Buffer point should become visible", visibleAfter);
    }
    
    @Test
    public void testArchitectureIntegrity() {
        // Final comprehensive test of architecture integrity
        
        // Test that all components are properly initialized
        assertNotNull("StateManager should be initialized", stateManager);
        assertNotNull("CoordinateMapper should be initialized", coordinateMapper);
        assertNotNull("InputHandler should be initialized", inputHandler);
        assertNotNull("SelectionManager should be initialized", selectionManager);
        assertNotNull("GestureHandler should be initialized", gestureHandler);
        assertNotNull("SnapshotFactory should be initialized", snapshotFactory);
        
        // Test basic component interactions
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        assertTrue("Dimensions should be reasonable", dims.columns > 0 && dims.rows > 0);
        
        // Test coordinate mapping
        CoordinateMapper.PixelPoint testPixel = new CoordinateMapper.PixelPoint(100f, 100f);
        CoordinateMapper.CharPoint charResult = coordinateMapper.pixelToCharacter(testPixel);
        assertTrue("Coordinate mapping should work", charResult.column >= 0 && charResult.row >= 0);
        
        // Test selection
        selectionManager.startSelection(testPixel);
        assertTrue("Selection should start successfully", selectionManager.isSelectionActive());
        selectionManager.clearSelection();
        
        // Test input
        InputHandler.InputResult inputResult = inputHandler.processKey(65, InputContext.DEFAULT_KEYBOARD);
        assertTrue("Input should be processed successfully", inputResult.success);
        
        // Test snapshot creation
        RenderSnapshot snapshot = snapshotFactory.createSnapshot();
        assertNotNull("Snapshot should be created successfully", snapshot);
        
        // Test stats and monitoring
        InputHandler.InputStats inputStats = inputHandler.getStats();
        assertNotNull("Input stats should be available", inputStats);
        
        SelectionManager.SelectionStats selectionStats = selectionManager.getStats();
        assertNotNull("Selection stats should be available", selectionStats);
        
        GestureHandler.GestureStats gestureStats = gestureHandler.getStats();
        assertNotNull("Gesture stats should be available", gestureStats);
    }
}