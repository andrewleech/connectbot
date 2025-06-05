# Technical Design Document: Terminal Architecture Refactoring

**Version**: 1.0  
**Date**: January 2025  
**Status**: Under Review

## 1. Introduction

### 1.1 Purpose
This document provides detailed technical specifications for refactoring ConnectBot's terminal rendering architecture to address thread safety, coordinate system synchronization, and state management issues.

### 1.2 Scope
The refactoring covers:
- Terminal buffer access synchronization
- Coordinate system unification
- State management implementation
- Input context handling
- Rendering pipeline optimization

### 1.3 Definitions
- **Buffer Coordinates**: Absolute position in terminal scrollback buffer
- **Character Coordinates**: Position in visible terminal grid
- **Pixel Coordinates**: Screen pixel position
- **Window Base**: Current scroll position in buffer
- **Screen Base**: Position of active terminal content

## 2. System Architecture

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────┐
│                Application Layer                 │
├─────────────────────────────────────────────────┤
│          TerminalStateManager                   │
│  ┌────────────┬────────────┬────────────┐      │
│  │Coordinate  │   Input    │  Selection │      │
│  │  Mapper    │  Context   │  Manager   │      │
│  └────────────┴────────────┴────────────┘      │
├─────────────────────────────────────────────────┤
│         SynchronizedBufferAccess                │
├─────────────────────────────────────────────────┤
│  ┌────────────┬────────────┬────────────┐      │
│  │TerminalView│  Terminal  │ VDUBuffer/ │      │
│  │            │   Bridge   │   vt320    │      │
│  └────────────┴────────────┴────────────┘      │
└─────────────────────────────────────────────────┘
```

### 2.2 Class Hierarchy

```
TerminalStateManager
├── TerminalDimensions
├── ScrollState
├── SelectionState
└── RenderState

CoordinateMapper
├── PixelPoint
├── CharPoint
└── BufferPoint

InputContext
├── InputSource (enum)
└── Builder

SynchronizedBufferAccess
├── BufferReader<T>
└── BufferWriter
```

## 3. Detailed Design

### 3.1 TerminalStateManager

#### Purpose
Centralized state management with atomic updates and thread safety.

#### Design Pattern
- **Pattern**: State Manager with Transactional Updates
- **Thread Safety**: ReadWriteLock for concurrent read, exclusive write

#### Implementation

```java
package org.connectbot.service.terminal;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * Immutable dimensions state
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
    }
    
    /**
     * Transaction interface for atomic updates
     */
    public interface StateTransaction {
        void execute(MutableTerminalState state) throws StateException;
    }
    
    /**
     * Execute atomic state update
     */
    public void executeTransaction(StateTransaction transaction) {
        stateLock.writeLock().lock();
        try {
            MutableTerminalState mutable = createMutableState();
            transaction.execute(mutable);
            
            if (!mutable.validate()) {
                throw new IllegalStateException("Invalid state after transaction");
            }
            
            commitState(mutable);
            notifyListeners();
            
        } finally {
            stateLock.writeLock().unlock();
        }
    }
    
    /**
     * Read state with shared lock
     */
    public <T> T readState(StateReader<T> reader) {
        stateLock.readLock().lock();
        try {
            return reader.read(new ImmutableStateView(this));
        } finally {
            stateLock.readLock().unlock();
        }
    }
}
```

### 3.2 CoordinateMapper

#### Purpose
Unified coordinate transformation with validation and bounds checking.

#### Design Principles
- **Immutable coordinate objects**
- **Fail-safe conversions** (always return valid coordinates)
- **Thread-safe operations**

#### Implementation

```java
package org.connectbot.service.terminal;

public class CoordinateMapper {
    private final TerminalStateManager stateManager;
    
    /**
     * Immutable coordinate types
     */
    public static class PixelPoint {
        public final float x, y;
        
        public PixelPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
    
    public static class CharPoint {
        public final int column, row;
        
        public CharPoint(int column, int row) {
            this.column = column;
            this.row = row;
        }
    }
    
    public static class BufferPoint {
        public final int line, column;
        
        public BufferPoint(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }
    
    /**
     * Convert pixel coordinates to character coordinates
     * Always returns valid coordinates within terminal bounds
     */
    public CharPoint pixelToCharacter(PixelPoint pixel) {
        return stateManager.readState(state -> {
            if (state.charWidth <= 0 || state.charHeight <= 0) {
                return new CharPoint(0, 0);
            }
            
            int col = (int)(pixel.x / state.charWidth);
            int row = (int)(pixel.y / state.charHeight);
            
            // Bounds checking
            col = Math.max(0, Math.min(col, state.columns - 1));
            row = Math.max(0, Math.min(row, state.rows - 1));
            
            return new CharPoint(col, row);
        });
    }
    
    /**
     * Convert character coordinates to buffer coordinates
     * Accounts for scroll position (windowBase)
     */
    public BufferPoint characterToBuffer(CharPoint charPoint) {
        return stateManager.readState(state -> {
            int bufferLine = charPoint.row + state.windowBase;
            
            // Ensure within buffer bounds
            bufferLine = Math.max(0, Math.min(bufferLine, state.bufferSize - 1));
            
            return new BufferPoint(bufferLine, charPoint.column);
        });
    }
    
    /**
     * Convert buffer coordinates to pixel coordinates
     * Returns null if coordinates not visible
     */
    public PixelPoint bufferToPixel(BufferPoint bufferPoint) {
        return stateManager.readState(state -> {
            // Check if buffer position is visible
            int relativeLine = bufferPoint.line - state.windowBase;
            
            if (relativeLine < 0 || relativeLine >= state.rows) {
                return null; // Not visible
            }
            
            float x = bufferPoint.column * state.charWidth;
            float y = relativeLine * state.charHeight;
            
            return new PixelPoint(x, y);
        });
    }
}
```

### 3.3 InputContext

#### Purpose
Differentiate input sources to enable context-aware processing.

#### Use Cases
- User keyboard input → trigger scroll reset
- Gesture input → preserve scroll position
- Programmatic input → skip echo
- Accessibility input → special handling

#### Implementation

```java
package org.connectbot.service.terminal;

public class InputContext {
    public enum InputSource {
        USER_KEYBOARD,    // Physical or soft keyboard
        USER_GESTURE,     // Touch gestures
        PROGRAMMATIC,     // Generated by app
        ACCESSIBILITY,    // Screen reader
        EXTERNAL_APP      // Input from other apps
    }
    
    private final InputSource source;
    private final boolean preserveScrollPosition;
    private final boolean skipEcho;
    private final boolean skipBuffering;
    private final long timestamp;
    
    private InputContext(Builder builder) {
        this.source = builder.source;
        this.preserveScrollPosition = builder.preserveScrollPosition;
        this.skipEcho = builder.skipEcho;
        this.skipBuffering = builder.skipBuffering;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
    public InputSource getSource() { return source; }
    public boolean shouldPreserveScrollPosition() { return preserveScrollPosition; }
    public boolean shouldSkipEcho() { return skipEcho; }
    public boolean shouldSkipBuffering() { return skipBuffering; }
    public long getTimestamp() { return timestamp; }
    
    /**
     * Builder pattern for flexible context creation
     */
    public static class Builder {
        private InputSource source = InputSource.USER_KEYBOARD;
        private boolean preserveScrollPosition = false;
        private boolean skipEcho = false;
        private boolean skipBuffering = false;
        
        public Builder withSource(InputSource source) {
            this.source = source;
            return this;
        }
        
        public Builder preserveScrollPosition(boolean preserve) {
            this.preserveScrollPosition = preserve;
            return this;
        }
        
        public Builder skipEcho(boolean skip) {
            this.skipEcho = skip;
            return this;
        }
        
        public Builder skipBuffering(boolean skip) {
            this.skipBuffering = skip;
            return this;
        }
        
        public InputContext build() {
            return new InputContext(this);
        }
    }
    
    // Predefined contexts for common scenarios
    public static final InputContext DEFAULT_KEYBOARD = new Builder()
        .withSource(InputSource.USER_KEYBOARD)
        .build();
        
    public static final InputContext GESTURE_ARROW = new Builder()
        .withSource(InputSource.USER_GESTURE)
        .preserveScrollPosition(true)
        .build();
}
```

### 3.4 SynchronizedBufferAccess

#### Purpose
Thread-safe access to VDUBuffer with minimal lock contention.

#### Design Decisions
- **ReadWriteLock**: Multiple concurrent readers, exclusive writers
- **Functional interfaces**: Clean API for buffer operations
- **No buffer exposure**: All access through controlled methods

#### Implementation

```java
package org.connectbot.service.terminal;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import de.mud.terminal.VDUBuffer;

public class SynchronizedBufferAccess {
    private final ReadWriteLock bufferLock = new ReentrantReadWriteLock();
    private final VDUBuffer buffer;
    
    public SynchronizedBufferAccess(VDUBuffer buffer) {
        this.buffer = buffer;
    }
    
    /**
     * Functional interface for buffer read operations
     */
    @FunctionalInterface
    public interface BufferReader<T> {
        T read(VDUBuffer buffer) throws Exception;
    }
    
    /**
     * Functional interface for buffer write operations
     */
    @FunctionalInterface
    public interface BufferWriter {
        void write(VDUBuffer buffer) throws Exception;
    }
    
    /**
     * Execute read operation with shared lock
     */
    public <T> T readFromBuffer(BufferReader<T> reader) {
        bufferLock.readLock().lock();
        try {
            return reader.read(buffer);
        } catch (Exception e) {
            throw new BufferAccessException("Read operation failed", e);
        } finally {
            bufferLock.readLock().unlock();
        }
    }
    
    /**
     * Execute write operation with exclusive lock
     */
    public void writeToBuffer(BufferWriter writer) {
        bufferLock.writeLock().lock();
        try {
            writer.write(buffer);
        } catch (Exception e) {
            throw new BufferAccessException("Write operation failed", e);
        } finally {
            bufferLock.writeLock().unlock();
        }
    }
    
    /**
     * Bulk read operation for efficiency
     */
    public String readLines(int startLine, int count) {
        return readFromBuffer(buffer -> {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < count && startLine + i < buffer.getRows(); i++) {
                for (int j = 0; j < buffer.getColumns(); j++) {
                    result.append(buffer.getChar(j, startLine + i));
                }
                result.append('\n');
            }
            return result.toString();
        });
    }
    
    /**
     * Get buffer dimensions safely
     */
    public TerminalDimensions getDimensions() {
        return readFromBuffer(buffer -> 
            new TerminalDimensions(
                buffer.getColumns(),
                buffer.getRows(),
                buffer.getWindowBase(),
                buffer.getScreenBase()
            )
        );
    }
}
```

## 4. Integration Points

### 4.1 TerminalBridge Integration

```java
public class TerminalBridge implements VDUDisplay {
    private final TerminalStateManager stateManager;
    private final CoordinateMapper coordinateMapper;
    private final SynchronizedBufferAccess bufferAccess;
    private final InputHandler inputHandler;
    
    // Refactored parentChanged method
    public void parentChanged(TerminalView parent) {
        stateManager.executeTransaction(state -> {
            // All related updates in single transaction
            state.updateParent(parent);
            state.calculateDimensions();
            state.prepareBitmap();
            state.resizeBuffer();
            state.notifyTransport();
        });
    }
    
    // New method for input handling
    public void handleInput(KeyEvent event, InputContext context) {
        inputHandler.processKeyEvent(event, context);
    }
}
```

### 4.2 TerminalView Integration

```java
public class TerminalView extends FrameLayout {
    private final GestureHandler gestureHandler;
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // All coordinate conversion through mapper
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(
            event.getX(), event.getY()
        );
        
        return gestureHandler.handleTouch(event, pixel);
    }
}
```

## 5. Migration Strategy

### 5.1 Feature Flags

```java
public class TerminalFeatureFlags {
    public static final String USE_NEW_COORDINATE_SYSTEM = "terminal.new_coordinates";
    public static final String USE_SYNCHRONIZED_BUFFER = "terminal.sync_buffer";
    public static final String USE_INPUT_CONTEXT = "terminal.input_context";
    
    private static boolean isEnabled(String flag) {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(flag, false);
    }
}
```

### 5.2 Compatibility Layer

```java
public class TerminalCompatibilityBridge {
    public static int[] convertCoordinates(float x, float y, TerminalBridge bridge) {
        if (TerminalFeatureFlags.isEnabled(USE_NEW_COORDINATE_SYSTEM)) {
            // New system
            CharPoint point = bridge.coordinateMapper.pixelToCharacter(
                new PixelPoint(x, y)
            );
            return new int[] { point.column, point.row };
        } else {
            // Legacy system
            int col = (int)(x / bridge.charWidth);
            int row = (int)(y / bridge.charHeight);
            return new int[] { col, row };
        }
    }
}
```

## 6. Testing Considerations

### 6.1 Unit Test Structure

```java
@RunWith(MockitoJUnitRunner.class)
public class CoordinateMapperTest {
    @Mock TerminalStateManager stateManager;
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        // Concurrent access test
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(1000);
        
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                // Perform coordinate conversion
                mapper.pixelToCharacter(new PixelPoint(100, 200));
                latch.countDown();
            });
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
```

### 6.2 Integration Test Scenarios

1. **Resize During Active Session**
   - Verify atomic state updates
   - Check bitmap/buffer consistency
   - Validate cursor position

2. **Gesture Input During Scroll**
   - Verify scroll position preserved
   - Check coordinate accuracy
   - Validate input delivery

3. **Concurrent Operations**
   - Network input + user input
   - Resize + rendering
   - Selection + scrolling

## 7. Performance Considerations

### 7.1 Lock Contention
- ReadWriteLock minimizes contention
- Fine-grained locking where possible
- Lock-free algorithms for hot paths

### 7.2 Memory Management
- Immutable objects reduce garbage
- Object pooling for coordinates
- Efficient buffer copying

### 7.3 Optimization Targets
- 60fps scrolling performance
- <5ms coordinate conversion
- <1ms lock acquisition (p99)

## 8. Security Considerations

### 8.1 Input Validation
- Bounds checking on all coordinates
- Input sanitization for commands
- Rate limiting for gesture input

### 8.2 Thread Safety
- No data races possible
- No deadlock scenarios
- Proper exception handling

## 9. Future Enhancements

### 9.1 Potential Extensions
- Multi-window support
- Custom coordinate systems
- Advanced gesture recognition
- Plugin architecture

### 9.2 API Stability
- Interfaces designed for extension
- Backward compatibility maintained
- Versioned API contracts

---

**Document Version**: 1.0  
**Review Status**: Under Review  
**Approval**: Pending