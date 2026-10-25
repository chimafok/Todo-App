package com.chimafok.todo.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chimafok.todo.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconAssetsTest {
    @Test fun applicationIconHasOpaqueDarkBackgroundAndNotificationUsesOnlyAlphaGlyph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.chimafok.todo.qa",context.packageName)
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue("App-info must receive a real adaptive icon",icon is AdaptiveIconDrawable)
        val adaptive = icon as AdaptiveIconDrawable
        val background = Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888)
        adaptive.background.setBounds(0,0,108,108)
        adaptive.background.draw(Canvas(background))
        for (x in 0..107) for (y in 0..107) assertEquals(0xFF151E30.toInt(),background.getPixel(x,y))
        val foreground = Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888)
        adaptive.foreground.setBounds(0,0,108,108)
        adaptive.foreground.draw(Canvas(foreground))
        var glyph = 0
        for(x in 0..107) for(y in 0..107) if(Color.alpha(foreground.getPixel(x,y))>0) {
            glyph++
            assertTrue("Glyph stays inside adaptive safe area", x in 21..86 && y in 21..86)
            assertNotEquals(Color.WHITE,foreground.getPixel(x,y))
        }
        assertTrue(glyph>0)
        val small = requireNotNull(context.getDrawable(R.drawable.ic_notification))
        val mask = Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888)
        small.setBounds(0,0,48,48); small.draw(Canvas(mask))
        for((x,y) in listOf(0 to 0,47 to 0,0 to 47,47 to 47)) assertEquals(0,Color.alpha(mask.getPixel(x,y)))
        var marks = 0
        for(x in 0..47) for(y in 0..47) {
            val pixel=mask.getPixel(x,y)
            if(Color.alpha(pixel)>0) { marks++;assertEquals(255,Color.red(pixel));assertEquals(255,Color.green(pixel));assertEquals(255,Color.blue(pixel)) }
        }
        assertTrue(marks>0)
        assertTrue(marks<48*48/2)
    }
}
