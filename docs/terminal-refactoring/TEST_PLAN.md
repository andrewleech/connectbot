# Test Plan: Terminal Architecture Refactoring

**Version**: 1.0  
**Date**: January 2025  
**Project**: ConnectBot Terminal Refactoring

## 1. Test Strategy Overview

### 1.1 Objectives
- Verify thread safety of all buffer operations
- Validate coordinate system accuracy
- Ensure backward compatibility
- Confirm performance requirements
- Test gesture functionality

### 1.2 Test Levels
1. **Unit Tests**: Individual component validation
2. **Integration Tests**: Component interaction verification
3. **System Tests**: End-to-end functionality
4. **Performance Tests**: Speed and resource usage
5. **Stress Tests**: Concurrent operation handling

### 1.3 Test Environment
- **Devices**: Pixel 4, Samsung Galaxy S21, OnePlus 9
- **Android Versions**: API 21, 24, 28, 31, 34
- **Orientations**: Portrait and Landscape
- **Keyboards**: Gboard, SwiftKey, Samsung Keyboard

## 2. Unit Test Specifications

### 2.1 CoordinateMapper Tests

```java
package org.connectbot.service.terminal;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class CoordinateMapperTest {
    private CoordinateMapper mapper;
    private TestStateManager stateManager;
    
    @Before
    public void setup() {
        stateManager = new TestStateManager();
        mapper = new CoordinateMapper(stateManager);
    }
    
    @Test
    public void testPixelToCharacterBasic() {
        // Given: 10px char width, 20px char height
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        
        // When: Convert center of character cell
        CharPoint result = mapper.pixelToCharacter(new PixelPoint(25f, 30f));
        
        // Then: Should map to cell (2, 1)
        assertEquals(2, result.column);
        assertEquals(1, result.row);
    }
    
    @Test
    public void testPixelToCharacterBoundary() {
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        
        // Test exact boundary
        CharPoint result = mapper.pixelToCharacter(new PixelPoint(30f, 40f));
        assertEquals(3, result.column);
        assertEquals(2, result.row);
        
        // Test just before boundary
        result = mapper.pixelToCharacter(new PixelPoint(29.9f, 39.9f));
        assertEquals(2, result.column);
        assertEquals(1, result.row);
    }
    
    @Test
    public void testNegativeCoordinates() {
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        
        CharPoint result = mapper.pixelToCharacter(new PixelPoint(-10f, -20f));
        assertEquals(0, result.column);
        assertEquals(0, result.row);
    }
    
    @Test
    public void testOutOfBoundsCoordinates() {
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        
        CharPoint result = mapper.pixelToCharacter(new PixelPoint(1000f, 1000f));
        assertEquals(79, result.column);
        assertEquals(23, result.row);
    }
    
    @Test
    public void testCharacterToBuffer() {
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        stateManager.setWindowBase(10);
        
        BufferPoint result = mapper.characterToBuffer(new CharPoint(5, 5));
        assertEquals(15, result.line);  // 5 + windowBase(10)
        assertEquals(5, result.column);
    }
    
    @Test
    public void testBufferToPixelVisible() {
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        stateManager.setWindowBase(10);
        
        // Line 15 should be visible at row 5
        PixelPoint result = mapper.bufferToPixel(new BufferPoint(15, 5));
        assertNotNull(result);
        assertEquals(50f, result.x, 0.01f);  // column 5 * 10px
        assertEquals(100f, result.y, 0.01f); // row 5 * 20px
    }
    
    @Test
    public void testBufferToPixelNotVisible() {
        stateManager.setDimensions(800, 480, 80, 24, 10f, 20f);
        stateManager.setWindowBase(10);
        
        // Line 5 is above visible window
        PixelPoint result = mapper.bufferToPixel(new BufferPoint(5, 5));
        assertNull(result);
        
        // Line 35 is below visible window
        result = mapper.bufferToPixel(new BufferPoint(35, 5));
        assertNull(result);
    }
}
```

### 2.2 TerminalStateManager Tests

```java
@RunWith(MockitoJUnitRunner.class)
public class TerminalStateManagerTest {
    private TerminalStateManager stateManager;
    
    @Before
    public void setup() {
        stateManager = new TerminalStateManager();
    }
    
    @Test
    public void testTransactionSuccess() {
        stateManager.executeTransaction(state -> {
            state.setDimensions(100, 50);
            state.setScrollPosition(10);
        });
        
        TerminalDimensions dims = stateManager.getDimensions();
        assertEquals(100, dims.columns);
        assertEquals(50, dims.rows);
        
        ScrollState scroll = stateManager.getScrollState();
        assertEquals(10, scroll.windowBase);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testTransactionRollback() {
        stateManager.executeTransaction(state -> {
            state.setDimensions(-1, 50); // Invalid
        });
    }
    
    @Test
    public void testConcurrentTransactions() throws Exception {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        stateManager.executeTransaction(state -> {
                            // Simulate work
                            state.setDimensions(80 + threadId, 24 + threadId);
                            Thread.sleep(1);
                            state.setScrollPosition(j);
                        });
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    fail("Transaction failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(threadCount * operationsPerThread, successCount.get());
        
        // Verify final state is consistent
        TerminalDimensions finalDims = stateManager.getDimensions();
        assertTrue(finalDims.columns >= 80 && finalDims.columns < 90);
        assertTrue(finalDims.rows >= 24 && finalDims.rows < 34);
    }
}
```

### 2.3 SynchronizedBufferAccess Tests

```java
public class SynchronizedBufferAccessTest {
    private SynchronizedBufferAccess bufferAccess;
    private VDUBuffer buffer;
    
    @Before
    public void setup() {
        buffer = new VDUBuffer(80, 24);
        bufferAccess = new SynchronizedBufferAccess(buffer);
    }
    
    @Test
    public void testReadOperation() {
        // Write test data
        bufferAccess.writeToBuffer(buf -> {
            buf.putChar(10, 5, 'A');
            buf.putChar(11, 5, 'B');
        });
        
        // Read and verify
        Character result = bufferAccess.readFromBuffer(buf -> 
            buf.getChar(10, 5)
        );
        
        assertEquals('A', result.charValue());
    }
    
    @Test
    public void testConcurrentReadWrite() throws Exception {
        int iterations = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);
        AtomicInteger errors = new AtomicInteger(0);
        
        // 10 writer threads
        for (int i = 0; i < 10; i++) {
            final int writerId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        bufferAccess.writeToBuffer(buf -> {
                            int col = j % 80;
                            int row = j % 24;
                            buf.putChar(col, row, (char)('A' + writerId));
                        });
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 10 reader threads
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        bufferAccess.readFromBuffer(buf -> {
                            int col = j % 80;
                            int row = j % 24;
                            return buf.getChar(col, row);
                        });
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
    }
}
```

## 3. Integration Test Specifications

### 3.1 Gesture Integration Tests

```java
@RunWith(AndroidJUnit4.class)
public class GestureIntegrationTest {
    @Rule
    public ActivityTestRule<ConsoleActivity> activityRule = 
        new ActivityTestRule<>(ConsoleActivity.class);
    
    private TerminalView terminalView;
    private TerminalBridge bridge;
    
    @Before
    public void setup() {
        terminalView = activityRule.getActivity().getCurrentTerminalView();
        bridge = terminalView.bridge;
    }
    
    @Test
    public void testArrowGestureNoScrollReset() {
        // Arrange: Scroll up in buffer
        instrumentation.runOnMainSync(() -> {
            bridge.buffer.setWindowBase(bridge.buffer.getScreenBase() - 10);
        });
        
        int scrollPosBefore = bridge.buffer.getWindowBase();
        
        // Act: Perform right arrow gesture
        onView(withId(R.id.console_flip))
            .perform(swipeRight());
        
        // Assert: Scroll position unchanged
        int scrollPosAfter = bridge.buffer.getWindowBase();
        assertEquals(scrollPosBefore, scrollPosAfter);
    }
    
    @Test
    public void testGestureCoordinateAccuracy() {
        // Get terminal dimensions
        int charWidth = bridge.charWidth;
        int charHeight = bridge.charHeight;
        
        // Perform tap at specific character position
        float targetX = charWidth * 10.5f;  // Middle of column 10
        float targetY = charHeight * 5.5f;  // Middle of row 5
        
        onView(withId(R.id.console_flip))
            .perform(clickXY(targetX, targetY));
        
        // Verify correct character position detected
        assertEquals(10, lastTappedColumn);
        assertEquals(5, lastTappedRow);
    }
}
```

### 3.2 Resize Integration Tests

```java
@Test
public void testResizeDuringScrollback() {
    // Arrange: Fill buffer and scroll up
    fillBufferWithLines(100);
    scrollToLine(50);
    
    // Act: Rotate device
    instrumentation.setInTouchMode(false);
    activityRule.getActivity().setRequestedOrientation(
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    );
    
    // Wait for resize
    waitForCondition(() -> bridge.bitmap.getWidth() > bridge.bitmap.getHeight());
    
    // Assert: Content preserved and position maintained
    String visibleContent = getVisibleContent();
    assertTrue(visibleContent.contains("Line 50"));
    assertTrue(visibleContent.contains("Line 51"));
}

@Test
public void testKeyboardShowHide() {
    // Record initial dimensions
    int initialRows = bridge.getRows();
    
    // Show keyboard
    onView(withId(R.id.console_flip)).perform(click());
    showSoftKeyboard();
    
    // Wait for resize
    waitForCondition(() -> bridge.getRows() < initialRows);
    
    // Verify terminal resized
    assertTrue(bridge.getRows() < initialRows);
    assertEquals(bridge.getColumns(), 80); // Columns unchanged
    
    // Hide keyboard
    hideSoftKeyboard();
    
    // Verify dimensions restored
    waitForCondition(() -> bridge.getRows() == initialRows);
    assertEquals(initialRows, bridge.getRows());
}
```

## 4. System Test Specifications

### 4.1 End-to-End Scenarios

```java
@Test
public void testCompleteUserSession() {
    // 1. Connect to host
    connectToHost("test.example.com");
    waitForPrompt();
    
    // 2. Execute commands with output
    typeCommand("ls -la");
    waitForOutput("total");
    
    // 3. Scroll up to review
    scrollUp(10);
    
    // 4. Use arrow gestures to navigate
    performArrowGesture(Direction.UP);
    performArrowGesture(Direction.UP);
    
    // 5. Select and copy text
    longPressAt(10, 5);
    dragTo(30, 8);
    
    String copiedText = getClipboardContent();
    assertFalse(copiedText.isEmpty());
    
    // 6. Rotate device
    rotateToLandscape();
    
    // 7. Verify session intact
    assertTrue(isConnected());
    typeCommand("echo 'still working'");
    waitForOutput("still working");
}
```

### 4.2 Multi-Window Scenarios

```java
@Test
public void testMultipleTerminals() {
    // Create 3 terminal sessions
    TerminalBridge[] bridges = new TerminalBridge[3];
    
    for (int i = 0; i < 3; i++) {
        createNewTerminal();
        bridges[i] = getCurrentBridge();
        typeCommand("echo Terminal " + i);
    }
    
    // Switch between terminals rapidly
    for (int i = 0; i < 30; i++) {
        int target = i % 3;
        switchToTerminal(target);
        
        // Verify correct terminal
        String content = getVisibleContent();
        assertTrue(content.contains("Terminal " + target));
    }
    
    // Verify all terminals still responsive
    for (int i = 0; i < 3; i++) {
        switchToTerminal(i);
        typeCommand("date");
        waitForOutput("202");  // Year in output
    }
}
```

## 5. Performance Test Specifications

### 5.1 Scroll Performance

```java
@Test
public void testScrollPerformance() {
    // Fill buffer with complex content
    for (int i = 0; i < 1000; i++) {
        typeCommand("echo " + generateComplexLine(80));
    }
    
    // Measure scroll performance
    FrameMetricsAggregator aggregator = new FrameMetricsAggregator();
    aggregator.add(activityRule.getActivity());
    
    // Perform rapid scrolling
    long startTime = SystemClock.elapsedRealtime();
    
    for (int i = 0; i < 100; i++) {
        scrollUp(5);
        SystemClock.sleep(16); // One frame
    }
    
    long duration = SystemClock.elapsedRealtime() - startTime;
    
    // Analyze results
    SparseIntArray totalDurations = aggregator.getMetrics()[
        FrameMetricsAggregator.TOTAL_DURATION_INDEX
    ];
    
    int slowFrames = countSlowFrames(totalDurations, 17); // >17ms
    int totalFrames = getTotalFrames(totalDurations);
    
    // Assert performance requirements
    assertTrue("Less than 5% slow frames", 
        slowFrames < totalFrames * 0.05);
    assertTrue("Average frame time < 16ms", 
        duration / 100 < 16);
}
```

### 5.2 Memory Usage

```java
@Test
public void testMemoryUsage() {
    // Get baseline memory
    long baselineMemory = getMemoryUsage();
    
    // Create large scrollback buffer
    bridge.buffer.setBufferSize(10000);
    
    // Fill with content
    for (int i = 0; i < 5000; i++) {
        bridge.buffer.putString(generateLine(80));
        bridge.buffer.insertLine(bridge.buffer.getCursorRow());
    }
    
    // Measure memory after fill
    long filledMemory = getMemoryUsage();
    long increase = filledMemory - baselineMemory;
    
    // Expected: ~80 chars * 2 bytes * 5000 lines = 800KB
    // Allow 2x for overhead
    assertTrue("Memory increase reasonable", increase < 2 * 1024 * 1024);
    
    // Perform operations
    for (int i = 0; i < 100; i++) {
        scrollToRandom();
        selectRandomRegion();
        copySelection();
    }
    
    // Check for leaks
    System.gc();
    SystemClock.sleep(100);
    long finalMemory = getMemoryUsage();
    
    // Should not grow significantly
    assertTrue("No memory leak", finalMemory < filledMemory * 1.1);
}
```

## 6. Stress Test Specifications

### 6.1 Concurrent Operations

```java
@Test
public void testConcurrentStress() {
    final int DURATION_MS = 30000;  // 30 seconds
    final AtomicBoolean running = new AtomicBoolean(true);
    final AtomicInteger errors = new AtomicInteger(0);
    
    // Network input thread
    Thread networkThread = new Thread(() -> {
        while (running.get()) {
            try {
                bridge.receive(generateRandomData(100));
                Thread.sleep(10);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
    });
    
    // User input thread
    Thread inputThread = new Thread(() -> {
        while (running.get()) {
            try {
                typeRandomKeys(5);
                Thread.sleep(50);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
    });
    
    // Scroll thread
    Thread scrollThread = new Thread(() -> {
        while (running.get()) {
            try {
                scrollToRandom();
                Thread.sleep(20);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
    });
    
    // Resize thread
    Thread resizeThread = new Thread(() -> {
        while (running.get()) {
            try {
                randomResize();
                Thread.sleep(200);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
    });
    
    // Start all threads
    networkThread.start();
    inputThread.start();
    scrollThread.start();
    resizeThread.start();
    
    // Run for duration
    SystemClock.sleep(DURATION_MS);
    running.set(false);
    
    // Wait for completion
    joinThread(networkThread);
    joinThread(inputThread);
    joinThread(scrollThread);
    joinThread(resizeThread);
    
    // Verify no errors
    assertEquals(0, errors.get());
    
    // Verify terminal still functional
    typeCommand("echo test");
    waitForOutput("test");
}
```

## 7. Test Data and Utilities

### 7.1 Test Data Generation

```java
public class TestDataGenerator {
    private static final String COMPLEX_CHARS = 
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
    
    public static String generateComplexLine(int length) {
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        
        for (int i = 0; i < length; i++) {
            if (random.nextFloat() < 0.1) {
                // 10% chance of escape sequence
                sb.append("\u001b[").append(random.nextInt(8)).append("m");
                i += 4;
            } else {
                sb.append(COMPLEX_CHARS.charAt(
                    random.nextInt(COMPLEX_CHARS.length())));
            }
        }
        
        return sb.toString();
    }
    
    public static byte[] generateBinaryData(int size) {
        byte[] data = new byte[size];
        new Random().nextBytes(data);
        
        // Ensure some control characters
        for (int i = 0; i < size / 10; i++) {
            int pos = i * 10;
            data[pos] = (byte)'\r';
            data[pos + 1] = (byte)'\n';
        }
        
        return data;
    }
}
```

### 7.2 Test Utilities

```java
public class TerminalTestUtils {
    public static void waitForCondition(BooleanSupplier condition) {
        long timeout = SystemClock.elapsedRealtime() + 5000;
        
        while (!condition.getAsBoolean()) {
            if (SystemClock.elapsedRealtime() > timeout) {
                fail("Condition not met within timeout");
            }
            SystemClock.sleep(50);
        }
    }
    
    public static String getVisibleContent(TerminalBridge bridge) {
        return bridge.bufferAccess.readFromBuffer(buffer -> {
            StringBuilder content = new StringBuilder();
            int windowBase = buffer.getWindowBase();
            
            for (int row = 0; row < buffer.getRows(); row++) {
                for (int col = 0; col < buffer.getColumns(); col++) {
                    content.append(buffer.getChar(col, windowBase + row));
                }
                content.append('\n');
            }
            
            return content.toString();
        });
    }
    
    public static void scrollToLine(TerminalBridge bridge, int line) {
        bridge.buffer.setWindowBase(line);
        bridge.redraw();
    }
}
```

## 8. Test Execution Plan

### 8.1 Continuous Integration

```yaml
# .github/workflows/terminal-tests.yml
name: Terminal Architecture Tests

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Unit Tests
        run: ./gradlew test
      
  integration-tests:
    runs-on: macos-latest
    strategy:
      matrix:
        api-level: [21, 24, 28, 31]
    steps:
      - uses: actions/checkout@v2
      - name: Run Integration Tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          script: ./gradlew connectedAndroidTest
```

### 8.2 Manual Test Procedures

1. **Gesture Testing**
   - Test on physical devices
   - Various finger sizes
   - Different swipe speeds
   - Multi-touch scenarios

2. **Performance Testing**
   - Profile on low-end devices
   - Test with large scrollback
   - Measure battery impact

3. **Compatibility Testing**
   - Test with various SSH servers
   - Different terminal types
   - Character encoding edge cases

## 9. Defect Management

### 9.1 Bug Classification

- **P0 - Critical**: Crashes, data loss, security issues
- **P1 - High**: Major functionality broken
- **P2 - Medium**: Minor functionality issues
- **P3 - Low**: Cosmetic issues

### 9.2 Bug Report Template

```markdown
**Title**: [Component] Brief description

**Environment**:
- Device: 
- Android Version: 
- ConnectBot Version: 
- Terminal Type: 

**Steps to Reproduce**:
1. 
2. 
3. 

**Expected Result**:

**Actual Result**:

**Logs**: [Attach logcat output]

**Screenshot/Video**: [If applicable]
```

## 10. Test Metrics

### 10.1 Coverage Targets
- Unit Test Coverage: >90%
- Integration Test Coverage: >80%
- Critical Path Coverage: 100%

### 10.2 Quality Gates
- All tests must pass
- No P0/P1 bugs
- Performance benchmarks met
- Memory leaks: 0

### 10.3 Test Report Format

```markdown
# Test Execution Report

**Date**: 
**Build**: 
**Tester**: 

## Summary
- Total Tests: 
- Passed: 
- Failed: 
- Skipped: 

## Failed Tests
[List with failure reasons]

## Performance Results
- Scroll FPS: 
- Memory Usage: 
- CPU Usage: 

## Recommendations
[Next steps based on results]
```

---

**Document Version**: 1.0  
**Last Updated**: January 2025  
**Approval**: Pending