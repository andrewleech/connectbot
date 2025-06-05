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

package org.connectbot;

import android.content.Context;
import android.view.MotionEvent;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.terminal.CoordinateMapper;
import org.connectbot.service.terminal.TerminalStateManager;
import org.connectbot.util.TerminalViewPager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TerminalView touch handling and coordinate mapping integration
 */
@RunWith(MockitoJUnitRunner.class)
public class TerminalViewTouchTest {
    
    @Mock
    private Context mockContext;
    
    @Mock
    private TerminalViewPager mockViewPager;
    
    @Mock
    private MotionEvent mockEvent;
    
    private TerminalBridge bridge;
    private TerminalView terminalView;
    
    @Before
    public void setup() {
        // Create a test bridge
        bridge = new TerminalBridge();
        
        // Initialize terminal dimensions
        bridge.getStateManager().executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
        });
        
        // Note: In a real test environment, we would need proper Android context
        // This test focuses on the coordinate mapping logic
    }
    
    @Test
    public void testCoordinateMapperIntegration() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        assertNotNull("CoordinateMapper should be available", mapper);
        
        // Test pixel to character conversion
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(105f, 130f);
        CoordinateMapper.CharPoint charPoint = mapper.pixelToCharacter(pixel);
        
        assertEquals("Column should be correctly calculated", 10, charPoint.column);
        assertEquals("Row should be correctly calculated", 5, charPoint.row);
    }
    
    @Test
    public void testBoundsValidation() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Test coordinates within bounds
        CoordinateMapper.CharPoint validPoint = new CoordinateMapper.CharPoint(50, 12);
        assertTrue("Valid coordinates should pass validation", 
                  mapper.isValidCharacter(validPoint));
        
        // Test coordinates outside bounds
        CoordinateMapper.CharPoint invalidPoint = new CoordinateMapper.CharPoint(100, 30);
        assertFalse("Invalid coordinates should fail validation", 
                   mapper.isValidCharacter(invalidPoint));
    }
    
    @Test
    public void testCoordinateClamping() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        TerminalStateManager.TerminalDimensions dims = bridge.getStateManager().getDimensions();
        
        // Test negative coordinates get clamped to 0
        CoordinateMapper.PixelPoint negativePixel = new CoordinateMapper.PixelPoint(-10f, -20f);
        CoordinateMapper.CharPoint clampedPoint = mapper.pixelToCharacter(negativePixel);
        
        assertEquals("Negative column should be clamped to 0", 0, clampedPoint.column);
        assertEquals("Negative row should be clamped to 0", 0, clampedPoint.row);
        
        // Test oversized coordinates get clamped to max
        CoordinateMapper.PixelPoint oversizedPixel = new CoordinateMapper.PixelPoint(1000f, 1000f);
        CoordinateMapper.CharPoint maxPoint = mapper.pixelToCharacter(oversizedPixel);
        
        assertEquals("Oversized column should be clamped to max", 
                    dims.columns - 1, maxPoint.column);
        assertEquals("Oversized row should be clamped to max", 
                    dims.rows - 1, maxPoint.row);
    }
    
    @Test
    public void testScrollOffsetHandling() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Set window base to simulate scroll offset
        bridge.getStateManager().executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        // Test character to buffer conversion with scroll offset
        CoordinateMapper.CharPoint charPoint = new CoordinateMapper.CharPoint(5, 5);
        CoordinateMapper.BufferPoint bufferPoint = mapper.characterToBuffer(charPoint);
        
        assertEquals("Buffer line should include scroll offset", 15, bufferPoint.line);
        assertEquals("Buffer column should remain same", 5, bufferPoint.column);
        
        // Test buffer to character conversion
        CoordinateMapper.BufferPoint testBuffer = new CoordinateMapper.BufferPoint(20, 8);
        CoordinateMapper.CharPoint resultChar = mapper.bufferToCharacter(testBuffer);
        
        assertNotNull("Visible buffer point should convert to character", resultChar);
        assertEquals("Character column should match", 8, resultChar.column);
        assertEquals("Character row should account for scroll", 10, resultChar.row);
    }
    
    @Test
    public void testPixelToBufferConversion() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Set scroll offset
        bridge.getStateManager().executeTransaction(state -> {
            state.setWindowBase(5);
        });
        
        // Test direct pixel to buffer conversion
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(105f, 130f);
        CoordinateMapper.BufferPoint buffer = mapper.pixelToBuffer(pixel);
        
        assertEquals("Buffer column should match pixel conversion", 10, buffer.column);
        assertEquals("Buffer line should include scroll offset", 10, buffer.line); // 5 + 5
    }
    
    @Test
    public void testCharacterBounds() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        CoordinateMapper.CharPoint charPoint = new CoordinateMapper.CharPoint(10, 5);
        CoordinateMapper.PixelBounds bounds = mapper.getCharacterBounds(charPoint);
        
        assertEquals("Left boundary should be correct", 100f, bounds.left, 0.01f);
        assertEquals("Top boundary should be correct", 125f, bounds.top, 0.01f);
        assertEquals("Right boundary should be correct", 110f, bounds.right, 0.01f);
        assertEquals("Bottom boundary should be correct", 150f, bounds.bottom, 0.01f);
        
        assertEquals("Width should be char width", 10f, bounds.width(), 0.01f);
        assertEquals("Height should be char height", 25f, bounds.height(), 0.01f);
    }
    
    @Test
    public void testTerminalSizeCalculation() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Test terminal size calculation from pixel dimensions
        CoordinateMapper.CharPoint size = mapper.calculateTerminalSize(800, 600);
        
        assertEquals("Columns should be calculated correctly", 80, size.column);
        assertEquals("Rows should be calculated correctly", 24, size.row);
        
        // Test character size calculation
        CoordinateMapper.PixelPoint charSize = mapper.calculateCharacterSize(800, 600, 80, 24);
        
        assertEquals("Character width should be calculated correctly", 10f, charSize.x, 0.01f);
        assertEquals("Character height should be calculated correctly", 25f, charSize.y, 0.01f);
    }
    
    @Test
    public void testStateManagerConsistency() {
        TerminalStateManager stateManager = bridge.getStateManager();
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Change state and verify coordinate mapping stays consistent
        stateManager.executeTransaction(state -> {
            state.setDimensions(1000, 800, 100, 32, 10f, 25f, false);
        });
        
        // Test that coordinate mapping reflects new state
        CoordinateMapper.CharPoint size = mapper.calculateTerminalSize(1000, 800);
        assertEquals("Columns should reflect new state", 100, size.column);
        assertEquals("Rows should reflect new state", 32, size.row);
        
        // Verify state manager consistency
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        assertEquals("State manager should have consistent columns", 100, dims.columns);
        assertEquals("State manager should have consistent rows", 32, dims.rows);
    }
    
    @Test
    public void testBufferVisibility() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Set up scroll state
        bridge.getStateManager().executeTransaction(state -> {
            state.setScrollState(10, 0, 50, 1000, 30); // windowBase=10, 24 rows visible
        });
        
        // Test visible buffer positions
        assertTrue("Buffer line 15 should be visible", 
                  mapper.isVisible(new CoordinateMapper.BufferPoint(15, 5)));
        assertTrue("Buffer line 33 should be visible", 
                  mapper.isVisible(new CoordinateMapper.BufferPoint(33, 5)));
        
        // Test non-visible buffer positions
        assertFalse("Buffer line 5 should not be visible", 
                   mapper.isVisible(new CoordinateMapper.BufferPoint(5, 5)));
        assertFalse("Buffer line 40 should not be visible", 
                   mapper.isVisible(new CoordinateMapper.BufferPoint(40, 5)));
    }
    
    @Test
    public void testInputHandlerIntegration() {
        // Verify that TerminalBridge provides InputHandler
        assertNotNull("InputHandler should be available", bridge.getInputHandler());
        
        // Test basic input processing
        bridge.getInputHandler().processKey(65, 
            org.connectbot.service.terminal.InputContext.DEFAULT_KEYBOARD);
        
        // Verify state is accessible
        assertNotNull("Input handler stats should be available", 
                     bridge.getInputHandler().getStats());
    }
}