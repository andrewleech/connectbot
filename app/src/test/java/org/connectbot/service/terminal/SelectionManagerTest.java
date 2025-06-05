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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SelectionManager
 */
@RunWith(MockitoJUnitRunner.class)
public class SelectionManagerTest {
    
    @Mock
    private VDUBuffer mockBuffer;
    
    private TerminalStateManager stateManager;
    private CoordinateMapper coordinateMapper;
    private SelectionManager selectionManager;
    
    @Before
    public void setup() {
        stateManager = new TerminalStateManager();
        coordinateMapper = new CoordinateMapper(stateManager);
        selectionManager = new SelectionManager(stateManager, coordinateMapper, mockBuffer);
        
        // Set up default terminal dimensions
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
        });
        
        // Mock buffer to return test characters
        when(mockBuffer.getChar(anyInt(), anyInt())).thenReturn('A');
    }
    
    @Test
    public void testInitialState() {
        assertFalse("Selection should not be active initially", 
                   selectionManager.isSelectionActive());
        assertTrue("Should be selecting origin initially", 
                  selectionManager.isSelectingOrigin());
        assertNull("No selection area initially", 
                  selectionManager.getCurrentSelectionArea());
        assertEquals("Selected text should be empty", "", 
                    selectionManager.getSelectedText());
    }
    
    @Test
    public void testStartSelection() {
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(10, 5);
        
        selectionManager.startSelection(startPoint);
        
        assertTrue("Selection should be active", selectionManager.isSelectionActive());
        assertTrue("Should be selecting origin", selectionManager.isSelectingOrigin());
        
        SelectionManager.SelectionArea area = selectionManager.getCurrentSelectionArea();
        assertNotNull("Selection area should exist", area);
        assertTrue("Single point selection should be empty", area.isEmpty());
    }
    
    @Test
    public void testUpdateSelection() {
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(10, 5);
        CoordinateMapper.BufferPoint endPoint = new CoordinateMapper.BufferPoint(15, 8);
        
        selectionManager.startSelection(startPoint);
        selectionManager.updateSelection(endPoint);
        
        assertTrue("Selection should be active", selectionManager.isSelectionActive());
        assertFalse("Should not be selecting origin after update", 
                   selectionManager.isSelectingOrigin());
        
        SelectionManager.SelectionArea area = selectionManager.getCurrentSelectionArea();
        assertNotNull("Selection area should exist", area);
        assertFalse("Selection should not be empty", area.isEmpty());
        assertEquals("Top-left should be start point", startPoint, area.topLeft);
        assertEquals("Bottom-right should be end point", endPoint, area.bottomRight);
    }
    
    @Test
    public void testSelectionNormalization() {
        // Test selection from bottom-right to top-left
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(15, 8);
        CoordinateMapper.BufferPoint endPoint = new CoordinateMapper.BufferPoint(10, 5);
        
        selectionManager.startSelection(startPoint);
        selectionManager.updateSelection(endPoint);
        
        SelectionManager.SelectionArea area = selectionManager.getCurrentSelectionArea();
        assertNotNull("Selection area should exist", area);
        
        // Should be normalized to top-left, bottom-right
        assertEquals("Top-left should be normalized", endPoint, area.topLeft);
        assertEquals("Bottom-right should be normalized", startPoint, area.bottomRight);
    }
    
    @Test
    public void testPixelCoordinateSelection() {
        CoordinateMapper.PixelPoint pixelStart = new CoordinateMapper.PixelPoint(100f, 125f);
        CoordinateMapper.PixelPoint pixelEnd = new CoordinateMapper.PixelPoint(150f, 175f);
        
        selectionManager.startSelection(pixelStart);
        selectionManager.updateSelection(pixelEnd);
        
        assertTrue("Selection should be active", selectionManager.isSelectionActive());
        
        SelectionManager.SelectionArea area = selectionManager.getCurrentSelectionArea();
        assertNotNull("Selection area should exist", area);
        assertFalse("Selection should not be empty", area.isEmpty());
    }
    
    @Test
    public void testEndSelection() {
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(10, 5);
        CoordinateMapper.BufferPoint endPoint = new CoordinateMapper.BufferPoint(15, 8);
        
        selectionManager.startSelection(startPoint);
        selectionManager.updateSelection(endPoint);
        
        SelectionManager.SelectionArea endedArea = selectionManager.endSelection();
        
        assertNotNull("Ended selection should return area", endedArea);
        assertEquals("Area should match current selection", 
                    selectionManager.getCurrentSelectionArea().topLeft, endedArea.topLeft);
    }
    
    @Test
    public void testClearSelection() {
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(10, 5);
        
        selectionManager.startSelection(startPoint);
        assertTrue("Selection should be active", selectionManager.isSelectionActive());
        
        selectionManager.clearSelection();
        
        assertFalse("Selection should not be active", selectionManager.isSelectionActive());
        assertTrue("Should be selecting origin after clear", 
                  selectionManager.isSelectingOrigin());
        assertNull("Selection area should be null", 
                  selectionManager.getCurrentSelectionArea());
    }
    
    @Test
    public void testPointSelection() {
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(10, 5);
        CoordinateMapper.BufferPoint endPoint = new CoordinateMapper.BufferPoint(15, 8);
        CoordinateMapper.BufferPoint insidePoint = new CoordinateMapper.BufferPoint(12, 6);
        CoordinateMapper.BufferPoint outsidePoint = new CoordinateMapper.BufferPoint(20, 10);
        
        selectionManager.startSelection(startPoint);
        selectionManager.updateSelection(endPoint);
        
        assertTrue("Point inside selection should be selected", 
                  selectionManager.isPointSelected(insidePoint));
        assertFalse("Point outside selection should not be selected", 
                   selectionManager.isPointSelected(outsidePoint));
        
        // Test boundary points
        assertTrue("Start point should be selected", 
                  selectionManager.isPointSelected(startPoint));
        assertTrue("End point should be selected", 
                  selectionManager.isPointSelected(endPoint));
    }
    
    @Test
    public void testCharacterSelection() {
        // Set up scroll offset
        stateManager.executeTransaction(state -> {
            state.setWindowBase(5);
        });
        
        CoordinateMapper.BufferPoint startPoint = new CoordinateMapper.BufferPoint(10, 3);
        CoordinateMapper.BufferPoint endPoint = new CoordinateMapper.BufferPoint(15, 6);
        
        selectionManager.startSelection(startPoint);
        selectionManager.updateSelection(endPoint);
        
        // Test character coordinate within selection
        CoordinateMapper.CharPoint charPoint = new CoordinateMapper.CharPoint(5, 0); // Maps to buffer line 5
        boolean isSelected = selectionManager.isCharacterSelected(charPoint);
        
        // Should be selected since buffer line 5 is between 10 and 15... wait, that's wrong
        // Let me fix the test - character row 0 with windowBase 5 maps to buffer line 5
        // But our selection is lines 10-15, so it should NOT be selected
        assertFalse("Character outside selection range should not be selected", isSelected);
    }
    
    @Test
    public void testSelectionListener() throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch changeLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        CountDownLatch clearLatch = new CountDownLatch(1);
        
        AtomicReference<CoordinateMapper.BufferPoint> startPoint = new AtomicReference<>();
        AtomicReference<SelectionManager.SelectionArea> changedArea = new AtomicReference<>();
        AtomicReference<SelectionManager.SelectionArea> endedArea = new AtomicReference<>();
        
        SelectionManager.SelectionListener listener = new SelectionManager.SelectionListener() {
            @Override
            public void onSelectionStarted(CoordinateMapper.BufferPoint start) {
                startPoint.set(start);
                startLatch.countDown();
            }
            
            @Override
            public void onSelectionChanged(SelectionManager.SelectionArea area) {
                changedArea.set(area);
                changeLatch.countDown();
            }
            
            @Override
            public void onSelectionEnded(SelectionManager.SelectionArea area) {
                endedArea.set(area);
                endLatch.countDown();
            }
            
            @Override
            public void onSelectionCleared() {
                clearLatch.countDown();
            }
        };
        
        selectionManager.setSelectionListener(listener);
        
        // Start selection
        CoordinateMapper.BufferPoint start = new CoordinateMapper.BufferPoint(10, 5);
        selectionManager.startSelection(start);
        
        assertTrue("Should receive start notification", startLatch.await(1, TimeUnit.SECONDS));
        assertEquals("Start point should match", start, startPoint.get());
        
        // Update selection
        CoordinateMapper.BufferPoint end = new CoordinateMapper.BufferPoint(15, 8);
        selectionManager.updateSelection(end);
        
        assertTrue("Should receive change notification", changeLatch.await(1, TimeUnit.SECONDS));
        assertNotNull("Changed area should be set", changedArea.get());
        
        // End selection
        selectionManager.endSelection();
        
        assertTrue("Should receive end notification", endLatch.await(1, TimeUnit.SECONDS));
        assertNotNull("Ended area should be set", endedArea.get());
        
        // Clear selection
        selectionManager.clearSelection();
        
        assertTrue("Should receive clear notification", clearLatch.await(1, TimeUnit.SECONDS));
    }
    
    @Test
    public void testSelectionArea() {
        CoordinateMapper.BufferPoint topLeft = new CoordinateMapper.BufferPoint(10, 5);
        CoordinateMapper.BufferPoint bottomRight = new CoordinateMapper.BufferPoint(15, 8);
        
        SelectionManager.SelectionArea area = new SelectionManager.SelectionArea(topLeft, bottomRight);
        
        assertEquals("Line count should be correct", 4, area.lineCount); // 15-10+1 = 6, wait... 8-5+1 = 4
        assertTrue("Character count should be positive", area.characterCount > 0);
        assertFalse("Area should not be empty", area.isEmpty());
        
        // Test point containment
        CoordinateMapper.BufferPoint insidePoint = new CoordinateMapper.BufferPoint(12, 6);
        assertTrue("Inside point should be contained", area.containsPoint(insidePoint));
        
        CoordinateMapper.BufferPoint outsidePoint = new CoordinateMapper.BufferPoint(20, 10);
        assertFalse("Outside point should not be contained", area.containsPoint(outsidePoint));
        
        // Test boundary points
        assertTrue("Top-left should be contained", area.containsPoint(topLeft));
        assertTrue("Bottom-right should be contained", area.containsPoint(bottomRight));
    }
    
    @Test
    public void testEmptySelection() {
        CoordinateMapper.BufferPoint point = new CoordinateMapper.BufferPoint(10, 5);
        SelectionManager.SelectionArea area = new SelectionManager.SelectionArea(point, point);
        
        assertTrue("Same start/end should be empty", area.isEmpty());
        assertEquals("Line count should be 1", 1, area.lineCount);
    }
    
    @Test
    public void testConcurrentSelection() throws InterruptedException {
        final int threadCount = 5;
        final int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        CoordinateMapper.BufferPoint start = new CoordinateMapper.BufferPoint(
                            threadId * 5, threadId * 2);
                        CoordinateMapper.BufferPoint end = new CoordinateMapper.BufferPoint(
                            threadId * 5 + 3, threadId * 2 + 2);
                        
                        selectionManager.startSelection(start);
                        selectionManager.updateSelection(end);
                        
                        if (selectionManager.isSelectionActive()) {
                            successCount.incrementAndGet();
                        }
                        
                        selectionManager.clearSelection();
                        
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
        assertTrue("Some operations should succeed", successCount.get() > 0);
        
        executor.shutdown();
    }
    
    @Test
    public void testSelectionStats() {
        SelectionManager.SelectionStats initialStats = selectionManager.getStats();
        assertFalse("Initially not active", initialStats.active);
        assertTrue("Initially selecting origin", initialStats.selectingOrigin);
        assertEquals("No lines initially", 0, initialStats.lineCount);
        assertEquals("No characters initially", 0, initialStats.characterCount);
        
        // Start selection
        CoordinateMapper.BufferPoint start = new CoordinateMapper.BufferPoint(10, 5);
        CoordinateMapper.BufferPoint end = new CoordinateMapper.BufferPoint(15, 8);
        
        selectionManager.startSelection(start);
        selectionManager.updateSelection(end);
        
        SelectionManager.SelectionStats activeStats = selectionManager.getStats();
        assertTrue("Should be active", activeStats.active);
        assertFalse("Should not be selecting origin", activeStats.selectingOrigin);
        assertTrue("Should have lines", activeStats.lineCount > 0);
        assertTrue("Should have characters", activeStats.characterCount > 0);
    }
    
    @Test
    public void testUpdateSelectionWhenInactive() {
        CoordinateMapper.BufferPoint point = new CoordinateMapper.BufferPoint(10, 5);
        
        // Try to update selection without starting it
        selectionManager.updateSelection(point);
        
        assertFalse("Selection should remain inactive", selectionManager.isSelectionActive());
        assertNull("No selection area should exist", selectionManager.getCurrentSelectionArea());
    }
    
    @Test
    public void testMultipleStarts() {
        CoordinateMapper.BufferPoint start1 = new CoordinateMapper.BufferPoint(10, 5);
        CoordinateMapper.BufferPoint start2 = new CoordinateMapper.BufferPoint(20, 10);
        
        selectionManager.startSelection(start1);
        assertTrue("First selection should be active", selectionManager.isSelectionActive());
        
        // Start a new selection - should replace the first one
        selectionManager.startSelection(start2);
        assertTrue("Second selection should be active", selectionManager.isSelectionActive());
        assertTrue("Should be selecting origin again", selectionManager.isSelectingOrigin());
    }
}