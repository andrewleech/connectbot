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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import de.mud.terminal.VDUBuffer;

import static org.junit.Assert.*;

/**
 * Thread safety tests for VDUBuffer
 */
@RunWith(MockitoJUnitRunner.class)
public class VDUBufferThreadSafetyTest {
    private VDUBuffer buffer;
    private static final int BUFFER_WIDTH = 80;
    private static final int BUFFER_HEIGHT = 24;
    
    @Before
    public void setup() {
        // Enable thread safety for tests
        VDUBuffer.enableThreadSafety(true);
        buffer = new VDUBuffer(BUFFER_WIDTH, BUFFER_HEIGHT);
    }
    
    @After
    public void tearDown() {
        // Disable thread safety after tests
        VDUBuffer.enableThreadSafety(false);
    }
    
    @Test
    public void testThreadSafetyEnabled() {
        assertTrue("Thread safety should be enabled for tests", 
                  VDUBuffer.isThreadSafetyEnabled());
    }
    
    @Test
    public void testConcurrentReadsAndWrites() throws InterruptedException {
        final int threadCount = 10;
        final int operationsPerThread = 100;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger errors = new AtomicInteger(0);
        final AtomicReference<Exception> lastError = new AtomicReference<>();
        
        // Fill buffer with initial data
        for (int row = 0; row < BUFFER_HEIGHT; row++) {
            for (int col = 0; col < BUFFER_WIDTH; col++) {
                buffer.putChar(col, row, (char)('A' + (row * col) % 26));
            }
        }
        
        // Create mixed reader and writer threads
        for (int i = 0; i < threadCount; i++) {
            final boolean isWriter = (i % 2 == 0);
            final int threadId = i;
            
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        int row = ThreadLocalRandom.current().nextInt(BUFFER_HEIGHT);
                        int col = ThreadLocalRandom.current().nextInt(BUFFER_WIDTH);
                        
                        if (isWriter) {
                            // Writer operations
                            char ch = (char)('0' + threadId);
                            buffer.putChar(col, row, ch);
                            buffer.setCursorPosition(col, row);
                        } else {
                            // Reader operations
                            char ch = buffer.getChar(col, row);
                            long attrs = buffer.getAttributes(col, row);
                            int cursorCol = buffer.getCursorColumn();
                            int cursorRow = buffer.getCursorRow();
                            int columns = buffer.getColumns();
                            int rows = buffer.getRows();
                            
                            // Verify read consistency
                            assertTrue("Character should be valid", ch >= 0);
                            assertTrue("Attributes should be valid", attrs >= 0);
                            assertTrue("Cursor column should be valid", 
                                     cursorCol >= 0 && cursorCol < BUFFER_WIDTH);
                            assertTrue("Cursor row should be valid", 
                                     cursorRow >= 0 && cursorRow < BUFFER_HEIGHT);
                            assertEquals("Columns should be consistent", BUFFER_WIDTH, columns);
                            assertEquals("Rows should be consistent", BUFFER_HEIGHT, rows);
                        }
                        
                        // Small delay to increase chance of race conditions
                        if (j % 10 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    lastError.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all threads to complete
        assertTrue("All threads should complete within timeout", 
                  latch.await(30, TimeUnit.SECONDS));
        
        executor.shutdown();
        
        // Verify no errors occurred
        if (errors.get() > 0) {
            fail("Errors occurred during concurrent access: " + 
                 errors.get() + " errors, last: " + lastError.get());
        }
        
        // Verify buffer is still in a valid state
        assertEquals("Buffer width should be unchanged", BUFFER_WIDTH, buffer.getColumns());
        assertEquals("Buffer height should be unchanged", BUFFER_HEIGHT, buffer.getRows());
        assertTrue("Cursor position should be valid", 
                  buffer.getCursorColumn() >= 0 && buffer.getCursorColumn() < BUFFER_WIDTH);
        assertTrue("Cursor position should be valid", 
                  buffer.getCursorRow() >= 0 && buffer.getCursorRow() < BUFFER_HEIGHT);
    }
    
    @Test
    public void testConcurrentScrollOperations() throws InterruptedException {
        final int threadCount = 5;
        final int operationsPerThread = 50;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger errors = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Perform various scroll operations
                        int windowBase = ThreadLocalRandom.current().nextInt(10);
                        buffer.setWindowBase(windowBase);
                        
                        int currentBase = buffer.getWindowBase();
                        assertTrue("Window base should be valid", currentBase >= 0);
                        
                        // Test cursor visibility
                        buffer.showCursor(j % 2 == 0);
                        boolean visible = buffer.isCursorVisible();
                        assertEquals("Cursor visibility should match setting", j % 2 == 0, visible);
                        
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(20, TimeUnit.SECONDS));
        executor.shutdown();
        
        assertEquals("No errors should occur", 0, errors.get());
    }
    
    @Test
    public void testConcurrentCharacterOperations() throws InterruptedException {
        final int writerCount = 3;
        final int readerCount = 7;
        final int operationsPerThread = 200;
        final ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);
        final CountDownLatch writersLatch = new CountDownLatch(writerCount);
        final CountDownLatch readersLatch = new CountDownLatch(readerCount);
        final AtomicInteger errors = new AtomicInteger(0);
        
        // Start writer threads
        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        int row = ThreadLocalRandom.current().nextInt(BUFFER_HEIGHT);
                        int col = ThreadLocalRandom.current().nextInt(BUFFER_WIDTH);
                        char ch = (char)('A' + writerId);
                        
                        buffer.putChar(col, row, ch, VDUBuffer.NORMAL);
                        
                        if (j % 20 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    writersLatch.countDown();
                }
            });
        }
        
        // Start reader threads
        for (int i = 0; i < readerCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        int row = ThreadLocalRandom.current().nextInt(BUFFER_HEIGHT);
                        int col = ThreadLocalRandom.current().nextInt(BUFFER_WIDTH);
                        
                        char ch = buffer.getChar(col, row);
                        long attrs = buffer.getAttributes(col, row);
                        
                        // Verify data consistency
                        assertTrue("Character should be printable or space", 
                                  ch == ' ' || (ch >= 'A' && ch <= 'Z'));
                        assertTrue("Attributes should be valid", attrs >= 0);
                        
                        if (j % 50 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    readersLatch.countDown();
                }
            });
        }
        
        assertTrue("All writers should complete", writersLatch.await(20, TimeUnit.SECONDS));
        assertTrue("All readers should complete", readersLatch.await(20, TimeUnit.SECONDS));
        
        executor.shutdown();
        
        assertEquals("No errors should occur", 0, errors.get());
    }
    
    @Test
    public void testThreadSafetyDisabled() {
        // Test that disabling thread safety still works
        VDUBuffer.enableThreadSafety(false);
        VDUBuffer testBuffer = new VDUBuffer(40, 12);
        
        // Basic operations should still work
        testBuffer.putChar(5, 5, 'X');
        assertEquals('X', testBuffer.getChar(5, 5));
        
        testBuffer.setCursorPosition(10, 8);
        assertEquals(10, testBuffer.getCursorColumn());
        assertEquals(8, testBuffer.getCursorRow());
        
        // Re-enable for cleanup
        VDUBuffer.enableThreadSafety(true);
    }
    
    @Test
    public void testPerformanceWithThreadSafety() {
        // Simple performance test to ensure thread safety doesn't severely impact performance
        final int iterations = 1000;
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            int row = i % BUFFER_HEIGHT;
            int col = i % BUFFER_WIDTH;
            
            buffer.putChar(col, row, (char)('A' + i % 26));
            char ch = buffer.getChar(col, row);
            assertEquals((char)('A' + i % 26), ch);
        }
        
        long duration = System.nanoTime() - startTime;
        double avgTimePerOp = duration / (double)(iterations * 2); // 2 ops per iteration
        
        // Allow up to 10 microseconds per operation (very generous)
        assertTrue("Operations should be reasonably fast even with thread safety", 
                  avgTimePerOp < 10_000); // 10 microseconds in nanoseconds
    }
}