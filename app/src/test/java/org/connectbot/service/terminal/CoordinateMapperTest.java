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

import static org.junit.Assert.*;

/**
 * Unit tests for CoordinateMapper
 */
@RunWith(MockitoJUnitRunner.class)
public class CoordinateMapperTest {
    private CoordinateMapper mapper;
    private TerminalStateManager stateManager;
    
    @Before
    public void setup() {
        stateManager = new TerminalStateManager();
        mapper = new CoordinateMapper(stateManager);
        
        // Set up default terminal dimensions for testing
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
            state.setScrollState(0, 0, 24, 1000, 0);
        });
    }
    
    @Test
    public void testPixelToCharacterBasic() {
        // Given: 10px char width, 25px char height
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(105f, 130f);
        
        // When: Convert to character coordinates
        CoordinateMapper.CharPoint result = mapper.pixelToCharacter(pixel);
        
        // Then: Should map to cell (10, 5)
        assertEquals(10, result.column);
        assertEquals(5, result.row);
    }
    
    @Test
    public void testPixelToCharacterBoundary() {
        // Test exact boundary
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(30f, 50f);
        CoordinateMapper.CharPoint result = mapper.pixelToCharacter(pixel);
        assertEquals(3, result.column);
        assertEquals(2, result.row);
        
        // Test just before boundary
        pixel = new CoordinateMapper.PixelPoint(29.9f, 49.9f);
        result = mapper.pixelToCharacter(pixel);
        assertEquals(2, result.column);
        assertEquals(1, result.row);
    }
    
    @Test
    public void testPixelToCharacterBoundsChecking() {
        // Test negative coordinates
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(-10f, -20f);
        CoordinateMapper.CharPoint result = mapper.pixelToCharacter(pixel);
        assertEquals(0, result.column);
        assertEquals(0, result.row);
        
        // Test out of bounds coordinates
        pixel = new CoordinateMapper.PixelPoint(1000f, 1000f);
        result = mapper.pixelToCharacter(pixel);
        assertEquals(79, result.column); // Max column (80-1)
        assertEquals(23, result.row);    // Max row (24-1)
    }
    
    @Test
    public void testCharacterToBuffer() {
        // Given: windowBase is 10
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        CoordinateMapper.CharPoint charPoint = new CoordinateMapper.CharPoint(5, 5);
        
        // When: Convert to buffer coordinates
        CoordinateMapper.BufferPoint result = mapper.characterToBuffer(charPoint);
        
        // Then: Should account for window offset
        assertEquals(15, result.line);  // 5 + windowBase(10)
        assertEquals(5, result.column);
    }
    
    @Test
    public void testBufferToCharacterVisible() {
        // Given: windowBase is 10
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        // When: Buffer line 15 should be visible at character row 5
        CoordinateMapper.BufferPoint bufferPoint = new CoordinateMapper.BufferPoint(15, 5);
        CoordinateMapper.CharPoint result = mapper.bufferToCharacter(bufferPoint);
        
        // Then: Should be visible
        assertNotNull(result);
        assertEquals(5, result.column);
        assertEquals(5, result.row);
    }
    
    @Test
    public void testBufferToCharacterNotVisible() {
        // Given: windowBase is 10
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        // When: Buffer line 5 is above visible window
        CoordinateMapper.BufferPoint bufferPoint = new CoordinateMapper.BufferPoint(5, 5);
        CoordinateMapper.CharPoint result = mapper.bufferToCharacter(bufferPoint);
        
        // Then: Should not be visible
        assertNull(result);
        
        // When: Buffer line 35 is below visible window
        bufferPoint = new CoordinateMapper.BufferPoint(35, 5);
        result = mapper.bufferToCharacter(bufferPoint);
        
        // Then: Should not be visible
        assertNull(result);
    }
    
    @Test
    public void testCharacterToPixel() {
        CoordinateMapper.CharPoint charPoint = new CoordinateMapper.CharPoint(10, 5);
        CoordinateMapper.PixelPoint result = mapper.characterToPixel(charPoint);
        
        assertEquals(100f, result.x, 0.01f); // column 10 * 10px
        assertEquals(125f, result.y, 0.01f); // row 5 * 25px
    }
    
    @Test
    public void testBufferToPixel() {
        // Given: windowBase is 10
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        // When: Buffer point (15, 5) should be visible
        CoordinateMapper.BufferPoint bufferPoint = new CoordinateMapper.BufferPoint(15, 5);
        CoordinateMapper.PixelPoint result = mapper.bufferToPixel(bufferPoint);
        
        // Then: Should convert to pixel coordinates
        assertNotNull(result);
        assertEquals(50f, result.x, 0.01f);  // column 5 * 10px
        assertEquals(125f, result.y, 0.01f); // row 5 * 25px
    }
    
    @Test
    public void testPixelToBuffer() {
        // Given: windowBase is 10
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(105f, 130f);
        CoordinateMapper.BufferPoint result = mapper.pixelToBuffer(pixel);
        
        assertEquals(15, result.line);   // row 5 + windowBase 10
        assertEquals(10, result.column); // column 10
    }
    
    @Test
    public void testIsVisible() {
        // Given: windowBase is 10, 24 rows visible
        stateManager.executeTransaction(state -> {
            state.setWindowBase(10);
        });
        
        // Visible buffer lines: 10-33 (inclusive)
        assertTrue(mapper.isVisible(new CoordinateMapper.BufferPoint(10, 5)));
        assertTrue(mapper.isVisible(new CoordinateMapper.BufferPoint(20, 5)));
        assertTrue(mapper.isVisible(new CoordinateMapper.BufferPoint(33, 5)));
        
        // Not visible
        assertFalse(mapper.isVisible(new CoordinateMapper.BufferPoint(9, 5)));
        assertFalse(mapper.isVisible(new CoordinateMapper.BufferPoint(34, 5)));
    }
    
    @Test
    public void testIsValidCharacter() {
        assertTrue(mapper.isValidCharacter(new CoordinateMapper.CharPoint(0, 0)));
        assertTrue(mapper.isValidCharacter(new CoordinateMapper.CharPoint(79, 23)));
        
        assertFalse(mapper.isValidCharacter(new CoordinateMapper.CharPoint(-1, 0)));
        assertFalse(mapper.isValidCharacter(new CoordinateMapper.CharPoint(0, -1)));
        assertFalse(mapper.isValidCharacter(new CoordinateMapper.CharPoint(80, 0)));
        assertFalse(mapper.isValidCharacter(new CoordinateMapper.CharPoint(0, 24)));
    }
    
    @Test
    public void testGetCharacterBounds() {
        CoordinateMapper.CharPoint charPoint = new CoordinateMapper.CharPoint(10, 5);
        CoordinateMapper.PixelBounds bounds = mapper.getCharacterBounds(charPoint);
        
        assertEquals(100f, bounds.left, 0.01f);   // column 10 * 10px
        assertEquals(125f, bounds.top, 0.01f);    // row 5 * 25px
        assertEquals(110f, bounds.right, 0.01f);  // left + charWidth
        assertEquals(150f, bounds.bottom, 0.01f); // top + charHeight
        
        assertEquals(10f, bounds.width(), 0.01f);
        assertEquals(25f, bounds.height(), 0.01f);
    }
    
    @Test
    public void testCalculateTerminalSize() {
        CoordinateMapper.CharPoint size = mapper.calculateTerminalSize(800, 600);
        
        assertEquals(80, size.column); // 800 / 10
        assertEquals(24, size.row);    // 600 / 25
    }
    
    @Test
    public void testCalculateCharacterSize() {
        CoordinateMapper.PixelPoint size = mapper.calculateCharacterSize(800, 600, 80, 24);
        
        assertEquals(10f, size.x, 0.01f); // 800 / 80
        assertEquals(25f, size.y, 0.01f); // 600 / 24
    }
    
    @Test
    public void testCoordinateObjectEquality() {
        // Test PixelPoint equality
        CoordinateMapper.PixelPoint p1 = new CoordinateMapper.PixelPoint(10f, 20f);
        CoordinateMapper.PixelPoint p2 = new CoordinateMapper.PixelPoint(10f, 20f);
        CoordinateMapper.PixelPoint p3 = new CoordinateMapper.PixelPoint(15f, 25f);
        
        assertEquals(p1, p2);
        assertNotEquals(p1, p3);
        assertEquals(p1.hashCode(), p2.hashCode());
        
        // Test CharPoint equality
        CoordinateMapper.CharPoint c1 = new CoordinateMapper.CharPoint(5, 10);
        CoordinateMapper.CharPoint c2 = new CoordinateMapper.CharPoint(5, 10);
        CoordinateMapper.CharPoint c3 = new CoordinateMapper.CharPoint(6, 11);
        
        assertEquals(c1, c2);
        assertNotEquals(c1, c3);
        assertEquals(c1.hashCode(), c2.hashCode());
        
        // Test BufferPoint equality
        CoordinateMapper.BufferPoint b1 = new CoordinateMapper.BufferPoint(15, 20);
        CoordinateMapper.BufferPoint b2 = new CoordinateMapper.BufferPoint(15, 20);
        CoordinateMapper.BufferPoint b3 = new CoordinateMapper.BufferPoint(16, 21);
        
        assertEquals(b1, b2);
        assertNotEquals(b1, b3);
        assertEquals(b1.hashCode(), b2.hashCode());
    }
}