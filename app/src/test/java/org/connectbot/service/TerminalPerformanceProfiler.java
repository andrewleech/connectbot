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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Performance profiling and benchmarking for the terminal architecture.
 * This class provides comprehensive performance testing to ensure the new
 * architecture meets performance requirements and identifies bottlenecks.
 */
@RunWith(MockitoJUnitRunner.class)
public class TerminalPerformanceProfiler {
    
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
        stateManager = new TerminalStateManager();
        coordinateMapper = new CoordinateMapper(stateManager);
        inputHandler = new InputHandler(stateManager, mockBuffer);
        selectionManager = new SelectionManager(stateManager, coordinateMapper, mockBuffer);
        gestureHandler = new GestureHandler(stateManager, coordinateMapper, inputHandler);
        snapshotFactory = new RenderSnapshot.Factory(stateManager, selectionManager, coordinateMapper);
        
        // Set up terminal dimensions
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
        });
        
        // Mock buffer for consistent results
        when(mockBuffer.getChar(anyInt(), anyInt())).thenReturn('A');
    }
    
    @Test
    public void benchmarkStateTransactions() {
        System.out.println("\n=== State Transaction Performance ===");
        
        PerformanceResult result = benchmark("State Transactions", 10000, () -> {
            stateManager.executeTransaction(state -> {
                state.setWindowBase((int)(Math.random() * 100));
                state.setRenderState(true, true, 10, 5);
            });
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("State transactions should be fast", result.averageNanos < 50_000); // 50μs
        assertTrue("99th percentile should be reasonable", result.p99Nanos < 200_000); // 200μs
    }
    
    @Test
    public void benchmarkCoordinateMapping() {
        System.out.println("\n=== Coordinate Mapping Performance ===");
        
        PerformanceResult result = benchmark("Coordinate Mapping", 50000, () -> {
            float x = (float)(Math.random() * 800);
            float y = (float)(Math.random() * 600);
            CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(x, y);
            
            coordinateMapper.pixelToCharacter(pixel);
            coordinateMapper.pixelToBuffer(pixel);
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("Coordinate mapping should be very fast", result.averageNanos < 5_000); // 5μs
        assertTrue("99th percentile should be low", result.p99Nanos < 20_000); // 20μs
    }
    
    @Test
    public void benchmarkInputProcessing() {
        System.out.println("\n=== Input Processing Performance ===");
        
        PerformanceResult result = benchmark("Input Processing", 10000, () -> {
            int keyCode = 65 + (int)(Math.random() * 26);
            inputHandler.processKey(keyCode, InputContext.DEFAULT_KEYBOARD);
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("Input processing should be fast", result.averageNanos < 20_000); // 20μs
        assertTrue("99th percentile should be reasonable", result.p99Nanos < 100_000); // 100μs
    }
    
    @Test
    public void benchmarkSelectionOperations() {
        System.out.println("\n=== Selection Operations Performance ===");
        
        PerformanceResult result = benchmark("Selection Operations", 5000, () -> {
            CoordinateMapper.PixelPoint start = new CoordinateMapper.PixelPoint(
                (float)(Math.random() * 400), (float)(Math.random() * 300));
            CoordinateMapper.PixelPoint end = new CoordinateMapper.PixelPoint(
                start.x + 100, start.y + 50);
            
            selectionManager.startSelection(start);
            selectionManager.updateSelection(end);
            selectionManager.getSelectedText();
            selectionManager.clearSelection();
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("Selection operations should be reasonable", result.averageNanos < 100_000); // 100μs
        assertTrue("99th percentile should be acceptable", result.p99Nanos < 500_000); // 500μs
    }
    
    @Test
    public void benchmarkRenderSnapshotCreation() {
        System.out.println("\n=== Render Snapshot Creation Performance ===");
        
        PerformanceResult result = benchmark("Render Snapshot Creation", 1000, () -> {
            snapshotFactory.createSnapshot();
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("Snapshot creation should be reasonable", result.averageNanos < 200_000); // 200μs
        assertTrue("99th percentile should be acceptable", result.p99Nanos < 1_000_000); // 1ms
    }
    
    @Test
    public void benchmarkConcurrentAccess() {
        System.out.println("\n=== Concurrent Access Performance ===");
        
        final int threadCount = 4;
        final int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        switch (threadId % 4) {
                            case 0:
                                // State operations
                                stateManager.executeTransaction(state -> {
                                    state.setWindowBase(j);
                                });
                                break;
                            case 1:
                                // Coordinate mapping
                                CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(
                                    j % 400, j % 300);
                                coordinateMapper.pixelToCharacter(pixel);
                                break;
                            case 2:
                                // Input processing
                                inputHandler.processKey(65 + (j % 26), InputContext.DEFAULT_KEYBOARD);
                                break;
                            case 3:
                                // Selection operations
                                if (j % 10 == 0) {
                                    CoordinateMapper.PixelPoint start = new CoordinateMapper.PixelPoint(j, j);
                                    selectionManager.startSelection(start);
                                    selectionManager.clearSelection();
                                }
                                break;
                        }
                        totalOperations.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Continue
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // Start all threads
        
        try {
            assertTrue("All threads should complete", 
                      completionLatch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double durationSeconds = duration / 1_000_000_000.0;
        double opsPerSecond = totalOperations.get() / durationSeconds;
        
        System.out.printf("Concurrent Performance: %.0f ops/sec with %d threads%n", 
                         opsPerSecond, threadCount);
        
        // Performance assertions
        assertTrue("Should handle reasonable concurrent throughput", opsPerSecond > 10000);
        
        executor.shutdown();
    }
    
    @Test
    public void benchmarkMemoryUsage() {
        System.out.println("\n=== Memory Usage Analysis ===");
        
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection
        
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create many objects to test memory usage
        List<Object> objects = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            // Create state snapshots
            TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
            objects.add(dims);
            
            // Create coordinate points
            CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(i, i);
            objects.add(pixel);
            
            // Create selection areas
            SelectionManager.SelectionArea area = new SelectionManager.SelectionArea(
                new CoordinateMapper.BufferPoint(i, i),
                new CoordinateMapper.BufferPoint(i + 10, i + 5)
            );
            objects.add(area);
            
            // Create render snapshots occasionally
            if (i % 100 == 0) {
                RenderSnapshot snapshot = snapshotFactory.createSnapshot();
                objects.add(snapshot);
            }
        }
        
        long peakMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = peakMemory - initialMemory;
        
        // Clear objects and measure again
        objects.clear();
        runtime.gc();
        Thread.yield(); // Give GC a chance
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryReclaimed = peakMemory - finalMemory;
        
        System.out.printf("Memory Analysis:%n");
        System.out.printf("  Initial: %,d bytes%n", initialMemory);
        System.out.printf("  Peak: %,d bytes%n", peakMemory);
        System.out.printf("  Increase: %,d bytes%n", memoryIncrease);
        System.out.printf("  Final: %,d bytes%n", finalMemory);
        System.out.printf("  Reclaimed: %,d bytes (%.1f%%)%n", 
                         memoryReclaimed, (double)memoryReclaimed / memoryIncrease * 100);
        
        // Memory assertions
        assertTrue("Memory increase should be reasonable", memoryIncrease < 50 * 1024 * 1024); // 50MB
        assertTrue("Most memory should be reclaimed", memoryReclaimed > memoryIncrease * 0.7); // 70%
    }
    
    @Test
    public void benchmarkScrollingPerformance() {
        System.out.println("\n=== Scrolling Performance ===");
        
        // Setup selection to test coordinate stability
        selectionManager.startSelection(new CoordinateMapper.PixelPoint(100f, 100f));
        selectionManager.updateSelection(new CoordinateMapper.PixelPoint(200f, 150f));
        
        PerformanceResult result = benchmark("Scrolling with Selection", 1000, () -> {
            int windowBase = (int)(Math.random() * 100);
            stateManager.executeTransaction(state -> {
                state.setWindowBase(windowBase);
            });
            
            // Verify selection still works
            SelectionManager.SelectionArea area = selectionManager.getCurrentSelectionArea();
            if (area != null) {
                // Test coordinate mapping consistency
                CoordinateMapper.BufferPoint point = area.topLeft;
                coordinateMapper.isVisible(point);
            }
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("Scrolling should be fast", result.averageNanos < 50_000); // 50μs
        assertTrue("Scrolling 99th percentile should be reasonable", result.p99Nanos < 200_000); // 200μs
        
        selectionManager.clearSelection();
    }
    
    @Test
    public void benchmarkComplexInteractions() {
        System.out.println("\n=== Complex Interaction Performance ===");
        
        PerformanceResult result = benchmark("Complex Interactions", 500, () -> {
            // Simulate complex user interaction
            
            // 1. State change (scroll)
            stateManager.executeTransaction(state -> {
                state.setWindowBase((int)(Math.random() * 50));
            });
            
            // 2. Coordinate mapping
            float x = (float)(Math.random() * 800);
            float y = (float)(Math.random() * 600);
            CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(x, y);
            CoordinateMapper.CharPoint charPoint = coordinateMapper.pixelToCharacter(pixel);
            
            // 3. Selection operation
            selectionManager.startSelection(pixel);
            selectionManager.updateSelection(new CoordinateMapper.PixelPoint(x + 50, y + 25));
            
            // 4. Input processing
            inputHandler.processKey(65, InputContext.DEFAULT_KEYBOARD);
            
            // 5. Render snapshot
            RenderSnapshot snapshot = snapshotFactory.createIncrementalSnapshot(
                charPoint.row, charPoint.row + 2);
            
            // 6. Clean up
            selectionManager.clearSelection();
        });
        
        printResults(result);
        
        // Performance assertions
        assertTrue("Complex interactions should be reasonable", result.averageNanos < 500_000); // 500μs
        assertTrue("Complex interactions 99th percentile", result.p99Nanos < 2_000_000); // 2ms
    }
    
    /**
     * Performance benchmark utility
     */
    private PerformanceResult benchmark(String name, int iterations, Runnable operation) {
        List<Long> times = new ArrayList<>();
        
        // Warmup
        for (int i = 0; i < Math.min(iterations / 10, 100); i++) {
            operation.run();
        }
        
        // Actual benchmark
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            operation.run();
            long end = System.nanoTime();
            times.add(end - start);
        }
        
        return new PerformanceResult(name, times);
    }
    
    /**
     * Print performance results
     */
    private void printResults(PerformanceResult result) {
        System.out.printf("%s Results:%n", result.name);
        System.out.printf("  Iterations: %,d%n", result.iterations);
        System.out.printf("  Average: %.2f μs%n", result.averageNanos / 1000.0);
        System.out.printf("  Median: %.2f μs%n", result.medianNanos / 1000.0);
        System.out.printf("  Min: %.2f μs%n", result.minNanos / 1000.0);
        System.out.printf("  Max: %.2f μs%n", result.maxNanos / 1000.0);
        System.out.printf("  95th percentile: %.2f μs%n", result.p95Nanos / 1000.0);
        System.out.printf("  99th percentile: %.2f μs%n", result.p99Nanos / 1000.0);
        System.out.printf("  Ops/sec: %,.0f%n", 1_000_000_000.0 / result.averageNanos);
    }
    
    /**
     * Performance result data
     */
    private static class PerformanceResult {
        final String name;
        final int iterations;
        final long averageNanos;
        final long medianNanos;
        final long minNanos;
        final long maxNanos;
        final long p95Nanos;
        final long p99Nanos;
        
        PerformanceResult(String name, List<Long> times) {
            this.name = name;
            this.iterations = times.size();
            
            Collections.sort(times);
            
            this.minNanos = times.get(0);
            this.maxNanos = times.get(times.size() - 1);
            this.medianNanos = times.get(times.size() / 2);
            this.p95Nanos = times.get((int)(times.size() * 0.95));
            this.p99Nanos = times.get((int)(times.size() * 0.99));
            
            long sum = 0;
            for (long time : times) {
                sum += time;
            }
            this.averageNanos = sum / times.size();
        }
    }
}