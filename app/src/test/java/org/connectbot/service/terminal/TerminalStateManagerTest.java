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
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Unit tests for TerminalStateManager
 */
@RunWith(MockitoJUnitRunner.class)
public class TerminalStateManagerTest {
    private TerminalStateManager stateManager;
    
    @Before
    public void setup() {
        stateManager = new TerminalStateManager();
    }
    
    @Test
    public void testInitialState() {
        // Check default dimensions
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        assertEquals(80, dims.columns);
        assertEquals(24, dims.rows);
        assertEquals(10f, dims.charWidth, 0.01f);
        assertEquals(20f, dims.charHeight, 0.01f);
        assertFalse(dims.forcedSize);
        
        // Check default scroll state
        TerminalStateManager.ScrollState scroll = stateManager.getScrollState();
        assertEquals(0, scroll.windowBase);
        assertEquals(0, scroll.screenBase);
        assertEquals(24, scroll.bufferSize);
        assertEquals(1000, scroll.maxBufferSize);
        assertFalse(scroll.isScrolledBack());
        
        // Check default selection state
        TerminalStateManager.SelectionState selection = stateManager.getSelectionState();
        assertFalse(selection.active);
        
        // Check default render state
        TerminalStateManager.RenderState render = stateManager.getRenderState();
        assertTrue(render.needsFullRedraw);
        assertTrue(render.showCursor);
        assertEquals(0, render.cursorRow);
        assertEquals(0, render.cursorColumn);
    }
    
    @Test
    public void testTransactionSuccess() {
        // Execute a simple transaction
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 100, 30, 8f, 20f, true);
            state.setWindowBase(5);
        });
        
        // Verify changes applied
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        assertEquals(100, dims.columns);
        assertEquals(30, dims.rows);
        assertEquals(8f, dims.charWidth, 0.01f);
        assertEquals(20f, dims.charHeight, 0.01f);
        assertTrue(dims.forcedSize);
        
        TerminalStateManager.ScrollState scroll = stateManager.getScrollState();
        assertEquals(5, scroll.windowBase);
    }
    
    @Test(expected = TerminalStateManager.StateException.class)
    public void testTransactionValidationFailure() {
        // Attempt invalid state change
        stateManager.executeTransaction(state -> {
            state.setDimensions(-1, -1, -1, -1, -1f, -1f, false);
        });
    }
    
    @Test
    public void testReadStateOperation() {
        // Set up some state
        stateManager.executeTransaction(state -> {
            state.setTerminalSize(120, 40);
            state.setWindowBase(15);
        });
        
        // Read state safely
        Integer columns = stateManager.readState(state -> state.getColumns());
        Integer windowBase = stateManager.readState(state -> state.getWindowBase());
        
        assertEquals(120, columns.intValue());
        assertEquals(15, windowBase.intValue());
    }
    
    @Test
    public void testStateChangeListener() {
        AtomicBoolean dimensionsChanged = new AtomicBoolean(false);
        AtomicBoolean scrollChanged = new AtomicBoolean(false);
        
        TerminalStateManager.StateChangeListener listener = new TerminalStateManager.StateChangeListener() {
            @Override
            public void onDimensionsChanged(TerminalStateManager.TerminalDimensions oldDims, 
                                          TerminalStateManager.TerminalDimensions newDims) {
                dimensionsChanged.set(true);
            }
            
            @Override
            public void onScrollStateChanged(TerminalStateManager.ScrollState oldState, 
                                           TerminalStateManager.ScrollState newState) {
                scrollChanged.set(true);
            }
            
            @Override
            public void onSelectionChanged(TerminalStateManager.SelectionState oldState, 
                                         TerminalStateManager.SelectionState newState) {}
            
            @Override
            public void onRenderStateChanged(TerminalStateManager.RenderState oldState, 
                                           TerminalStateManager.RenderState newState) {}
        };
        
        stateManager.addStateChangeListener(listener);
        
        // Execute transaction that changes both dimensions and scroll
        stateManager.executeTransaction(state -> {
            state.setTerminalSize(100, 50);
            state.setWindowBase(10);
        });
        
        assertTrue(dimensionsChanged.get());
        assertTrue(scrollChanged.get());
        
        stateManager.removeStateChangeListener(listener);
    }
    
    @Test
    public void testConcurrentTransactions() throws InterruptedException {
        final int threadCount = 10;
        final int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<Exception> error = new AtomicReference<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        stateManager.executeTransaction(state -> {
                            // Simulate some work
                            state.setTerminalSize(80 + threadId, 24 + threadId);
                            Thread.sleep(1);
                            state.setWindowBase(j % 50);
                        });
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    error.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("Concurrent transactions should complete within timeout", 
                  latch.await(30, TimeUnit.SECONDS));
        
        assertNull("No exceptions should occur during concurrent access", error.get());
        assertEquals("All operations should succeed", 
                    threadCount * operationsPerThread, successCount.get());
        
        // Verify final state is consistent
        TerminalStateManager.TerminalDimensions finalDims = stateManager.getDimensions();
        assertTrue("Final columns should be reasonable", 
                  finalDims.columns >= 80 && finalDims.columns < 90);
        assertTrue("Final rows should be reasonable", 
                  finalDims.rows >= 24 && finalDims.rows < 34);
        
        executor.shutdown();
    }
    
    @Test
    public void testConcurrentReadsDuringTransaction() throws InterruptedException {
        final int readerCount = 5;
        final int readOperations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
        CountDownLatch readersLatch = new CountDownLatch(readerCount);
        CountDownLatch writerLatch = new CountDownLatch(1);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicReference<Exception> error = new AtomicReference<>();
        
        // Start reader threads
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < readOperations; j++) {
                        stateManager.readState(state -> {
                            // Read various state properties
                            int cols = state.getColumns();
                            int rows = state.getRows();
                            int windowBase = state.getWindowBase();
                            
                            // Verify consistency
                            assertTrue("Columns should be positive", cols > 0);
                            assertTrue("Rows should be positive", rows > 0);
                            assertTrue("WindowBase should be non-negative", windowBase >= 0);
                            
                            return null;
                        });
                        readSuccessCount.incrementAndGet();
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    error.set(e);
                } finally {
                    readersLatch.countDown();
                }
            });
        }
        
        // Start a writer thread that makes periodic changes
        executor.submit(() -> {
            try {
                for (int i = 0; i < 50; i++) {
                    stateManager.executeTransaction(state -> {
                        state.setTerminalSize(80 + i, 24 + i);
                        state.setWindowBase(i);
                        Thread.sleep(5);
                    });
                    Thread.sleep(10);
                }
            } catch (Exception e) {
                error.set(e);
            } finally {
                writerLatch.countDown();
            }
        });
        
        assertTrue("All readers should complete", 
                  readersLatch.await(30, TimeUnit.SECONDS));
        assertTrue("Writer should complete", 
                  writerLatch.await(5, TimeUnit.SECONDS));
        
        assertNull("No exceptions should occur", error.get());
        assertEquals("All read operations should succeed", 
                    readerCount * readOperations, readSuccessCount.get());
        
        executor.shutdown();
    }
    
    @Test
    public void testMutableStateValidation() {
        // Test valid state changes
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 100, 30, 10f, 20f, false);
            state.setScrollState(5, 10, 50, 1000, 100);
            state.setSelection(0, 0, 10, 20, true);
            state.setCursorPosition(5, 10);
            
            assertTrue("State should be valid", state.validate());
        });
        
        // Test that invalid dimensions are caught
        try {
            stateManager.executeTransaction(state -> {
                state.setDimensions(0, 0, 0, 0, 0f, 0f, false);
            });
            fail("Should have thrown StateException for invalid dimensions");
        } catch (TerminalStateManager.StateException e) {
            // Expected
        }
    }
    
    @Test
    public void testImmutableStateView() {
        // Set up some state
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
            state.setScrollState(5, 10, 50, 1000, 100);
            state.setCursorPosition(15, 20);
        });
        
        // Read through immutable view
        stateManager.readState(state -> {
            assertEquals(80, state.getColumns());
            assertEquals(24, state.getRows());
            assertEquals(10f, state.getCharWidth(), 0.01f);
            assertEquals(25f, state.getCharHeight(), 0.01f);
            assertEquals(800, state.getPixelWidth());
            assertEquals(600, state.getPixelHeight());
            assertFalse(state.isForcedSize());
            
            assertEquals(5, state.getWindowBase());
            assertEquals(10, state.getScreenBase());
            assertEquals(50, state.getBufferSize());
            assertEquals(1000, state.getMaxBufferSize());
            assertTrue(state.isScrolledBack());
            
            assertEquals(15, state.getCursorColumn());
            assertEquals(20, state.getCursorRow());
            assertTrue(state.isCursorVisible());
            
            return null;
        });
    }
}