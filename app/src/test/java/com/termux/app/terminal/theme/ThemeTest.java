package com.termux.app.terminal.theme;

import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ThemeTest {

    @Test public void darkPreset_isDark() {
        Theme t = new Theme(ThemePreset.get(4));
        assertTrue(t.isDark());
        assertEquals(0xffe6e6e6, t.color(Theme.TEXT));
    }

    @Test public void translucent_defaultsToDark() {
        Theme t = new Theme(ThemePreset.get(12));
        assertTrue("全透明应兜底判深色", t.isDark());
        assertEquals(0x00000000, t.color(Theme.BG));
    }

    @Test public void scrim_notClobbered() {
        Theme t = new Theme(ThemePreset.get(0));
        assertEquals("皮肤 scrim 不应被覆盖", 0xe6000000, t.color(Theme.SCRIM));
    }

    @Test public void pressed_keepsAlpha() {
        Theme t = new Theme(ThemePreset.get(12));
        int p = t.color(Theme.SURFACE, Theme.STATE_PRESSED);
        assertTrue("半透明面板按下不应变实", Color.alpha(p) < 0xff);
    }

    @Test public void everyPreset_noCrash_uniqueName() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < ThemePreset.count(); i++) {
            assertTrue("预设重名 index=" + i, names.add(ThemePreset.get(i).name));
            assertNotNull(new Theme(ThemePreset.get(i)).color(Theme.SURFACE));
        }
    }

    @Test public void release_isIdempotent() {
        Theme t = new Theme(ThemePreset.get(0));
        t.release();
        t.release();
    }

    @Test public void cache_returnsCopy() {
        Theme t = new Theme(ThemePreset.get(0));
        assertNotSame(t.page(null), t.page(null));
    }
}
