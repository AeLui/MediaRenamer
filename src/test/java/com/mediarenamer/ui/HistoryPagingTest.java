package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pages of the history window. */
class HistoryPagingTest {

    @Test
    void pageCount() {
        assertEquals(1, HistoryDialog.pageCount(0, 50), "an empty history still has one (empty) page");
        assertEquals(1, HistoryDialog.pageCount(50, 50));
        assertEquals(2, HistoryDialog.pageCount(51, 50));
        assertEquals(3, HistoryDialog.pageCount(101, 50));
    }

    @Test
    void pageBounds() {
        assertArrayEquals(new int[] { 0, 50 }, HistoryDialog.pageBounds(0, 120, 50));
        assertArrayEquals(new int[] { 100, 120 }, HistoryDialog.pageBounds(2, 120, 50));
        assertArrayEquals(new int[] { 0, 0 }, HistoryDialog.pageBounds(0, 0, 50));
    }
}
