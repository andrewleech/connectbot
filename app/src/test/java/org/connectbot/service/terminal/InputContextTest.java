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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

/**
 * Unit tests for InputContext
 */
@RunWith(MockitoJUnitRunner.class)
public class InputContextTest {
    
    @Test
    public void testDefaultKeyboardContext() {
        InputContext context = InputContext.DEFAULT_KEYBOARD;
        
        assertEquals(InputContext.InputSource.USER_KEYBOARD, context.getSource());
        assertEquals(InputContext.Priority.NORMAL, context.getPriority());
        assertFalse(context.shouldPreserveScrollPosition());
        assertFalse(context.shouldSkipEcho());
        assertFalse(context.shouldSkipBuffering());
        assertFalse(context.shouldBypassFilters());
        assertTrue(context.isUserInitiated());
        assertFalse(context.isAutomated());
        assertTrue(context.shouldTriggerScrollReset());
    }
    
    @Test
    public void testGestureArrowContext() {
        InputContext context = InputContext.GESTURE_ARROW;
        
        assertEquals(InputContext.InputSource.USER_GESTURE, context.getSource());
        assertEquals(InputContext.Priority.NORMAL, context.getPriority());
        assertTrue(context.shouldPreserveScrollPosition());
        assertFalse(context.shouldSkipEcho());
        assertFalse(context.shouldSkipBuffering());
        assertFalse(context.shouldBypassFilters());
        assertTrue(context.isUserInitiated());
        assertFalse(context.isAutomated());
        assertFalse(context.shouldTriggerScrollReset());
    }
    
    @Test
    public void testProgrammaticContext() {
        InputContext context = InputContext.PROGRAMMATIC_KEY;
        
        assertEquals(InputContext.InputSource.PROGRAMMATIC, context.getSource());
        assertEquals(InputContext.Priority.NORMAL, context.getPriority());
        assertTrue(context.shouldPreserveScrollPosition());
        assertTrue(context.shouldSkipEcho());
        assertFalse(context.shouldSkipBuffering());
        assertFalse(context.shouldBypassFilters());
        assertFalse(context.isUserInitiated());
        assertTrue(context.isAutomated());
        assertFalse(context.shouldTriggerScrollReset());
    }
    
    @Test
    public void testAccessibilityContext() {
        InputContext context = InputContext.ACCESSIBILITY;
        
        assertEquals(InputContext.InputSource.ACCESSIBILITY, context.getSource());
        assertEquals(InputContext.Priority.HIGH, context.getPriority());
        assertFalse(context.shouldPreserveScrollPosition());
        assertFalse(context.shouldSkipEcho());
        assertFalse(context.shouldSkipBuffering());
        assertFalse(context.shouldBypassFilters());
        assertFalse(context.isUserInitiated());
        assertFalse(context.isAutomated());
        assertFalse(context.shouldTriggerScrollReset());
    }
    
    @Test
    public void testBuilderPattern() {
        InputContext context = new InputContext.Builder()
            .withSource(InputContext.InputSource.USER_GESTURE)
            .withPriority(InputContext.Priority.HIGH)
            .preserveScrollPosition(true)
            .skipEcho(true)
            .skipBuffering(true)
            .bypassFilters(true)
            .withDescription("Test context")
            .build();
        
        assertEquals(InputContext.InputSource.USER_GESTURE, context.getSource());
        assertEquals(InputContext.Priority.HIGH, context.getPriority());
        assertTrue(context.shouldPreserveScrollPosition());
        assertTrue(context.shouldSkipEcho());
        assertTrue(context.shouldSkipBuffering());
        assertTrue(context.shouldBypassFilters());
        assertEquals("Test context", context.getDescription());
    }
    
    @Test
    public void testFactoryMethods() {
        // Test gestureInput factory
        InputContext gestureContext = InputContext.gestureInput("swipe-right");
        assertEquals(InputContext.InputSource.USER_GESTURE, gestureContext.getSource());
        assertTrue(gestureContext.shouldPreserveScrollPosition());
        assertEquals("Gesture: swipe-right", gestureContext.getDescription());
        
        // Test programmaticInput factory
        InputContext programmaticContext = InputContext.programmaticInput("auto-complete");
        assertEquals(InputContext.InputSource.PROGRAMMATIC, programmaticContext.getSource());
        assertTrue(programmaticContext.shouldPreserveScrollPosition());
        assertTrue(programmaticContext.shouldSkipEcho());
        assertEquals("Programmatic: auto-complete", programmaticContext.getDescription());
        
        // Test keyboardInput factory
        InputContext keyboardContext = InputContext.keyboardInput(true);
        assertEquals(InputContext.InputSource.USER_KEYBOARD, keyboardContext.getSource());
        assertTrue(keyboardContext.shouldPreserveScrollPosition());
        assertEquals("Keyboard input", keyboardContext.getDescription());
        
        InputContext normalKeyboardContext = InputContext.keyboardInput(false);
        assertEquals(InputContext.InputSource.USER_KEYBOARD, normalKeyboardContext.getSource());
        assertFalse(normalKeyboardContext.shouldPreserveScrollPosition());
    }
    
    @Test
    public void testTimestamp() {
        long beforeCreation = System.currentTimeMillis();
        InputContext context = new InputContext.Builder().build();
        long afterCreation = System.currentTimeMillis();
        
        assertTrue("Timestamp should be recent", 
                  context.getTimestamp() >= beforeCreation && 
                  context.getTimestamp() <= afterCreation);
    }
    
    @Test
    public void testInputSourceClassification() {
        // User-initiated sources
        assertTrue(InputContext.DEFAULT_KEYBOARD.isUserInitiated());
        assertTrue(InputContext.GESTURE_ARROW.isUserInitiated());
        assertTrue(InputContext.PASTE.isUserInitiated());
        
        // Automated sources
        assertTrue(InputContext.PROGRAMMATIC_KEY.isAutomated());
        
        // Other sources
        assertFalse(InputContext.ACCESSIBILITY.isUserInitiated());
        assertFalse(InputContext.ACCESSIBILITY.isAutomated());
    }
    
    @Test
    public void testScrollResetBehavior() {
        // Should trigger scroll reset
        assertTrue(InputContext.DEFAULT_KEYBOARD.shouldTriggerScrollReset());
        assertTrue(InputContext.PASTE.shouldTriggerScrollReset());
        
        // Should not trigger scroll reset
        assertFalse(InputContext.GESTURE_ARROW.shouldTriggerScrollReset());
        assertFalse(InputContext.PROGRAMMATIC_KEY.shouldTriggerScrollReset());
        assertFalse(InputContext.ACCESSIBILITY.shouldTriggerScrollReset());
    }
    
    @Test
    public void testEquality() {
        InputContext context1 = new InputContext.Builder()
            .withSource(InputContext.InputSource.USER_KEYBOARD)
            .withPriority(InputContext.Priority.NORMAL)
            .preserveScrollPosition(false)
            .withDescription("test")
            .build();
            
        InputContext context2 = new InputContext.Builder()
            .withSource(InputContext.InputSource.USER_KEYBOARD)
            .withPriority(InputContext.Priority.NORMAL)
            .preserveScrollPosition(false)
            .withDescription("test")
            .build();
            
        InputContext context3 = new InputContext.Builder()
            .withSource(InputContext.InputSource.USER_GESTURE)
            .withPriority(InputContext.Priority.NORMAL)
            .preserveScrollPosition(false)
            .withDescription("test")
            .build();
        
        assertEquals(context1, context2);
        assertEquals(context1.hashCode(), context2.hashCode());
        assertNotEquals(context1, context3);
        assertNotEquals(context1.hashCode(), context3.hashCode());
    }
    
    @Test
    public void testToString() {
        InputContext context = new InputContext.Builder()
            .withSource(InputContext.InputSource.USER_GESTURE)
            .withPriority(InputContext.Priority.HIGH)
            .preserveScrollPosition(true)
            .skipEcho(true)
            .withDescription("test gesture")
            .build();
        
        String str = context.toString();
        assertTrue("ToString should contain source", str.contains("USER_GESTURE"));
        assertTrue("ToString should contain priority", str.contains("HIGH"));
        assertTrue("ToString should contain preserve flag", str.contains("preserveScroll=true"));
        assertTrue("ToString should contain echo flag", str.contains("skipEcho=true"));
        assertTrue("ToString should contain description", str.contains("test gesture"));
    }
    
    @Test
    public void testBuilderDefaults() {
        InputContext context = new InputContext.Builder().build();
        
        assertEquals(InputContext.InputSource.USER_KEYBOARD, context.getSource());
        assertEquals(InputContext.Priority.NORMAL, context.getPriority());
        assertFalse(context.shouldPreserveScrollPosition());
        assertFalse(context.shouldSkipEcho());
        assertFalse(context.shouldSkipBuffering());
        assertFalse(context.shouldBypassFilters());
        assertEquals("", context.getDescription());
    }
    
    @Test
    public void testBuilderNullDescription() {
        InputContext context = new InputContext.Builder()
            .withDescription(null)
            .build();
        
        assertEquals("", context.getDescription());
    }
}