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

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RenderSnapshot
 */
@RunWith(MockitoJUnitRunner.class)
public class RenderSnapshotTest {
    
    @Mock
    private VDUBuffer mockBuffer;
    
    private TerminalStateManager stateManager;
    private CoordinateMapper coordinateMapper;
    private SelectionManager selectionManager;
    private RenderSnapshot.Factory factory;
    
    @Before
    public void setup() {
        stateManager = new TerminalStateManager();
        coordinateMapper = new CoordinateMapper(stateManager);
        selectionManager = new SelectionManager(stateManager, coordinateMapper, mockBuffer);
        factory = new RenderSnapshot.Factory(stateManager, selectionManager, coordinateMapper);
        
        // Set up default terminal dimensions
        stateManager.executeTransaction(state -> {
            state.setDimensions(800, 600, 80, 24, 10f, 25f, false);
        });
    }
    
    @Test
    public void testBuilderBasic() {
        TerminalStateManager.TerminalDimensions dims = stateManager.getDimensions();
        TerminalStateManager.ScrollState scroll = stateManager.getScrollState();
        TerminalStateManager.RenderState render = stateManager.getRenderState();
        
        RenderSnapshot.CursorInfo cursor = new RenderSnapshot.CursorInfo(
            new CoordinateMapper.CharPoint(10, 5), true, 
            RenderSnapshot.CursorStyle.BLOCK, false);
        
        RenderSnapshot snapshot = new RenderSnapshot.Builder()
            .setDimensions(dims)
            .setScrollState(scroll)
            .setRenderState(render)
            .setCursor(cursor)
            .build();
        
        assertEquals("Dimensions should match", dims, snapshot.getDimensions());
        assertEquals("Scroll state should match", scroll, snapshot.getScrollState());
        assertEquals("Render state should match", render, snapshot.getRenderState());
        assertEquals("Cursor should match", cursor, snapshot.getCursor());
        assertNull("Selection should be null", snapshot.getSelection());
        assertTrue("Timestamp should be recent", 
                  System.currentTimeMillis() - snapshot.getTimestamp() < 1000);
    }
    
    @Test
    public void testBuilderWithChangedSegments() {
        RenderSnapshot.Builder builder = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 10, 20, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED))
            .addChangedSegment(new RenderSnapshot.LineSegment(
                8, 0, 15, RenderSnapshot.LineSegment.SegmentType.SELECTION_CHANGED));
        
        RenderSnapshot snapshot = builder.build();
        List<RenderSnapshot.LineSegment> segments = snapshot.getChangedSegments();
        
        assertEquals("Should have 2 segments", 2, segments.size());
        assertEquals("Changed line count should be 2", 2, snapshot.getChangedLineCount());
        assertTrue("Should have changes for line 5", snapshot.isLineChanged(5));
        assertTrue("Should have changes for line 8", snapshot.isLineChanged(8));
        assertFalse("Should not have changes for line 3", snapshot.isLineChanged(3));
    }
    
    @Test
    public void testBuilderAddChangedLine() {
        RenderSnapshot.Builder builder = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .addChangedLine(10);
        
        RenderSnapshot snapshot = builder.build();
        List<RenderSnapshot.LineSegment> segments = snapshot.getChangedSegments();
        
        assertEquals("Should have 1 segment", 1, segments.size());
        
        RenderSnapshot.LineSegment segment = segments.get(0);
        assertEquals("Line should be 10", 10, segment.line);
        assertEquals("Should start at column 0", 0, segment.startColumn);
        assertEquals("Should end at last column", 79, segment.endColumn);
        assertEquals("Should be TEXT_CHANGED type", 
                    RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED, segment.type);
    }
    
    @Test
    public void testSegmentOverlapping() {
        RenderSnapshot.LineSegment segment1 = new RenderSnapshot.LineSegment(
            5, 10, 20, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED);
        RenderSnapshot.LineSegment segment2 = new RenderSnapshot.LineSegment(
            5, 15, 25, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED);
        RenderSnapshot.LineSegment segment3 = new RenderSnapshot.LineSegment(
            6, 10, 20, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED);
        
        assertTrue("Overlapping segments on same line should overlap", 
                  segment1.overlaps(segment2));
        assertFalse("Segments on different lines should not overlap", 
                   segment1.overlaps(segment3));
        
        RenderSnapshot.LineSegment merged = segment1.merge(segment2);
        assertEquals("Merged line should be same", 5, merged.line);
        assertEquals("Merged start should be minimum", 10, merged.startColumn);
        assertEquals("Merged end should be maximum", 25, merged.endColumn);
    }
    
    @Test
    public void testSegmentMerging() {
        RenderSnapshot.Builder builder = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 10, 20, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED))
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 15, 25, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED))
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 30, 40, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED));
        
        RenderSnapshot snapshot = builder.build();
        List<RenderSnapshot.LineSegment> segments = snapshot.getChangedSegments();
        
        // Should merge overlapping segments
        assertEquals("Should have merged overlapping segments", 2, segments.size());
        
        RenderSnapshot.LineSegment firstSegment = segments.get(0);
        assertEquals("First merged segment should span 10-25", 10, firstSegment.startColumn);
        assertEquals("First merged segment should span 10-25", 25, firstSegment.endColumn);
        
        RenderSnapshot.LineSegment secondSegment = segments.get(1);
        assertEquals("Second segment should be 30-40", 30, secondSegment.startColumn);
        assertEquals("Second segment should be 30-40", 40, secondSegment.endColumn);
    }
    
    @Test
    public void testCursorInfo() {
        CoordinateMapper.CharPoint position = new CoordinateMapper.CharPoint(15, 8);
        RenderSnapshot.CursorInfo cursor = new RenderSnapshot.CursorInfo(
            position, true, RenderSnapshot.CursorStyle.UNDERLINE, true);
        
        assertEquals("Position should match", position, cursor.position);
        assertTrue("Should be visible", cursor.visible);
        assertEquals("Style should match", RenderSnapshot.CursorStyle.UNDERLINE, cursor.style);
        assertTrue("Should be blinking", cursor.blinking);
    }
    
    @Test
    public void testSnapshotQueries() {
        // Create snapshot with selection and cursor
        SelectionManager.SelectionArea selection = new SelectionManager.SelectionArea(
            new CoordinateMapper.BufferPoint(10, 5),
            new CoordinateMapper.BufferPoint(15, 8));
        
        RenderSnapshot.CursorInfo cursor = new RenderSnapshot.CursorInfo(
            new CoordinateMapper.CharPoint(10, 5), true, 
            RenderSnapshot.CursorStyle.BLOCK, false);
        
        TerminalStateManager.RenderState renderState = new TerminalStateManager.RenderState(
            true, true, 10, 5);
        
        RenderSnapshot snapshot = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .setRenderState(renderState)
            .setSelection(selection)
            .setCursor(cursor)
            .addChangedLine(5)
            .build();
        
        assertTrue("Should need full redraw", snapshot.needsFullRedraw());
        assertTrue("Should draw cursor", snapshot.shouldDrawCursor());
        assertTrue("Should have selection", snapshot.hasSelection());
        assertTrue("Cursor should be at position", snapshot.isCursorAt(10, 5));
        assertFalse("Cursor should not be at other position", snapshot.isCursorAt(5, 10));
    }
    
    @Test
    public void testGetSegmentsForLine() {
        RenderSnapshot snapshot = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 0, 10, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED))
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 20, 30, RenderSnapshot.LineSegment.SegmentType.SELECTION_CHANGED))
            .addChangedSegment(new RenderSnapshot.LineSegment(
                8, 0, 40, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED))
            .build();
        
        List<RenderSnapshot.LineSegment> line5Segments = snapshot.getSegmentsForLine(5);
        assertEquals("Line 5 should have 2 segments", 2, line5Segments.size());
        
        List<RenderSnapshot.LineSegment> line8Segments = snapshot.getSegmentsForLine(8);
        assertEquals("Line 8 should have 1 segment", 1, line8Segments.size());
        
        List<RenderSnapshot.LineSegment> line10Segments = snapshot.getSegmentsForLine(10);
        assertEquals("Line 10 should have 0 segments", 0, line10Segments.size());
    }
    
    @Test
    public void testCharacterCount() {
        RenderSnapshot snapshot = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 0, 10, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED)) // 11 chars
            .addChangedSegment(new RenderSnapshot.LineSegment(
                5, 20, 25, RenderSnapshot.LineSegment.SegmentType.SELECTION_CHANGED)) // 6 chars
            .build();
        
        assertEquals("Changed character count should be correct", 17, 
                    snapshot.getChangedCharacterCount());
    }
    
    @Test
    public void testCellInfo() {
        RenderSnapshot.CellInfo cell = new RenderSnapshot.CellInfo(
            'A', 0xFF0000, 0x000000, true, false, true, false, true);
        
        assertEquals("Character should match", 'A', cell.character);
        assertEquals("Foreground color should match", 0xFF0000, cell.foregroundColor);
        assertEquals("Background color should match", 0x000000, cell.backgroundColor);
        assertTrue("Should be bold", cell.bold);
        assertFalse("Should not be italic", cell.italic);
        assertTrue("Should be underlined", cell.underline);
        assertFalse("Should not blink", cell.blink);
        assertTrue("Should be selected", cell.selected);
    }
    
    @Test
    public void testFactoryCreateSnapshot() {
        // Set up some state
        stateManager.executeTransaction(state -> {
            state.setRenderState(true, true, 15, 10);
        });
        
        RenderSnapshot snapshot = factory.createSnapshot();
        
        assertNotNull("Snapshot should be created", snapshot);
        assertEquals("Dimensions should match", 
                    stateManager.getDimensions(), snapshot.getDimensions());
        assertTrue("Should need full redraw", snapshot.needsFullRedraw());
        
        // Should include all lines for full redraw
        assertEquals("Should have 24 changed lines", 24, snapshot.getChangedLineCount());
    }
    
    @Test
    public void testFactoryIncrementalSnapshot() {
        RenderSnapshot snapshot = factory.createIncrementalSnapshot(5, 10);
        
        assertNotNull("Snapshot should be created", snapshot);
        assertEquals("Should have 6 changed lines", 6, snapshot.getChangedLineCount());
        
        // Check that only specified lines are included
        for (int line = 5; line <= 10; line++) {
            assertTrue("Line " + line + " should be changed", snapshot.isLineChanged(line));
        }
        assertFalse("Line 4 should not be changed", snapshot.isLineChanged(4));
        assertFalse("Line 11 should not be changed", snapshot.isLineChanged(11));
    }
    
    @Test
    public void testRenderStats() {
        RenderSnapshot snapshot = new RenderSnapshot.Builder()
            .setDimensions(stateManager.getDimensions())
            .setRenderState(new TerminalStateManager.RenderState(true, true, 10, 5))
            .addChangedLine(5)
            .addChangedLine(8)
            .build();
        
        RenderSnapshot.RenderStats stats = snapshot.getStats();
        
        assertEquals("Changed lines should be correct", 2, stats.changedLines);
        assertTrue("Changed characters should be positive", stats.changedCharacters > 0);
        assertEquals("Segment count should be correct", 2, stats.segmentCount);
        assertTrue("Should indicate full redraw", stats.fullRedraw);
        assertFalse("Should not have selection", stats.hasSelection);
        assertTrue("Should have cursor", stats.hasCursor);
        
        assertTrue("Update ratio should be reasonable", 
                  stats.getUpdateRatio() > 0 && stats.getUpdateRatio() <= 1.0);
    }
    
    @Test
    public void testSegmentLength() {
        RenderSnapshot.LineSegment segment = new RenderSnapshot.LineSegment(
            5, 10, 20, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED);
        
        assertEquals("Segment length should be correct", 11, segment.getLength());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testMergeNonOverlappingSegments() {
        RenderSnapshot.LineSegment segment1 = new RenderSnapshot.LineSegment(
            5, 10, 15, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED);
        RenderSnapshot.LineSegment segment2 = new RenderSnapshot.LineSegment(
            5, 20, 25, RenderSnapshot.LineSegment.SegmentType.TEXT_CHANGED);
        
        segment1.merge(segment2); // Should throw exception
    }
}