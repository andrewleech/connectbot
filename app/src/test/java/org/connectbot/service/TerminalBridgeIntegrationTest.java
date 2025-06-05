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

import org.connectbot.service.terminal.TerminalStateManager;
import org.connectbot.service.terminal.CoordinateMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

/**
 * Integration tests for TerminalBridge state management
 */
@RunWith(MockitoJUnitRunner.class)
public class TerminalBridgeIntegrationTest {
    private TerminalBridge bridge;
    
    @Before
    public void setup() {
        // Use test constructor
        bridge = new TerminalBridge();
    }
    
    @Test
    public void testStateManagerInitialization() {
        TerminalStateManager stateManager = bridge.getStateManager();
        assertNotNull("StateManager should be initialized", stateManager);
        
        // Test initial state
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        assertEquals(80, dims.columns);
        assertEquals(24, dims.rows);
    }
    
    @Test
    public void testCoordinateMapperInitialization() {
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        assertNotNull("CoordinateMapper should be initialized", mapper);
        
        // Test basic coordinate mapping
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(105f, 130f);
        CoordinateMapper.CharPoint result = mapper.pixelToCharacter(pixel);
        
        // With default 10px char width, 20px char height
        assertEquals(10, result.column);
        assertEquals(6, result.row); // 130 / 20 = 6.5, truncated to 6
    }
    
    @Test
    public void testStateManagerIntegration() {
        TerminalStateManager stateManager = bridge.getStateManager();
        
        // Test state transaction
        stateManager.executeTransaction(state -> {
            state.setTerminalSize(100, 30);
            state.setWindowBase(5);
        });
        
        // Verify changes
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        assertEquals(100, dims.columns);
        assertEquals(30, dims.rows);
        
        TerminalStateManager.ScrollState scroll = stateManager.getScrollState();
        assertEquals(5, scroll.windowBase);
    }
    
    @Test
    public void testCoordinateMapperStateSync() {
        TerminalStateManager stateManager = bridge.getStateManager();
        CoordinateMapper mapper = bridge.getCoordinateMapper();
        
        // Change state through state manager
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
            state.setWindowBase(10);
        });
        
        // Test coordinate mapping with new state
        CoordinateMapper.PixelPoint pixel = new CoordinateMapper.PixelPoint(105f, 130f);
        CoordinateMapper.CharPoint charPoint = mapper.pixelToCharacter(pixel);
        CoordinateMapper.BufferPoint bufferPoint = mapper.pixelToBuffer(pixel);
        
        assertEquals(10, charPoint.column);
        assertEquals(5, charPoint.row); // 130 / 25 = 5.2, truncated to 5
        
        assertEquals(10, bufferPoint.column);
        assertEquals(15, bufferPoint.line); // 5 + windowBase(10)
    }
    
    @Test
    public void testStateValidation() {
        TerminalStateManager stateManager = bridge.getStateManager();
        
        // Test valid state transaction
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
            assertTrue("Valid state should pass validation", state.validate());
        });
        
        // Test that invalid state is rejected
        try {
            stateManager.executeTransaction(state -> {
                state.setDimensions(-1, -1, -1, -1, -1f, -1f, false);
            });
            fail("Should have thrown StateException for invalid dimensions");
        } catch (TerminalStateManager.StateException e) {
            // Expected
        }
    }
}