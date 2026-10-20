package cn.walkpaint

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FogPoiVisibilityTest {
    @Test fun existingRealFogPixelsAllowExploredPoiButDenyHiddenPoi() {
        val painter=TrailPainter()
        val p=TrackPoint(0.0,0.0,0,5f)
        val image=painter.update(listOf(listOf(p)),0xff557755.toInt(),p,ViewBounds(-.001,-.001,.001,.001))!!
        val frame=painter.frame()!!
        assertTrue(FogPoiVisibility.visible(image,Matrix(),frame.pixelX(0.0),frame.pixelY(0.0),true))
        assertFalse(FogPoiVisibility.visible(image,Matrix(),frame.pixelX(.001),frame.pixelY(.001),true))
        assertFalse(FogPoiVisibility.visible(image,Matrix(),frame.pixelX(0.0),frame.pixelY(0.0),false))
    }
    @Test fun sameDrawMatrixSupportsPanScaleAndRotationWithoutCoordinateReconversion() {
        val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888).apply {eraseColor(0x54557755)}
        val transform=Matrix().apply {setScale(2f,3f);postRotate(30f);postTranslate(100f,200f)}
        val screen=floatArrayOf(10f,10f);transform.mapPoints(screen)
        assertTrue(FogPoiVisibility.visible(bitmap,transform,screen[0],screen[1],true))
        assertFalse(FogPoiVisibility.visible(bitmap,transform,-1000f,-1000f,true))
        assertFalse(FogPoiVisibility.visible(bitmap,Matrix().apply {setScale(0f,0f)},10f,10f,true))
    }
    @Test fun featherEdgesMissingImagesAndOutOfBoundsFailClosed() {
        val bitmap=Bitmap.createBitmap(20,20,Bitmap.Config.ARGB_8888).apply {eraseColor(0x54557755)}
        bitmap.setPixel(11,10,0xff557755.toInt())
        assertFalse(FogPoiVisibility.visible(bitmap,Matrix(),10f,10f,true))
        assertFalse(FogPoiVisibility.visible(bitmap,Matrix(),0f,0f,true))
        assertFalse(FogPoiVisibility.visible(null,Matrix(),10f,10f,true))
        assertFalse(FogPoiVisibility.visible(bitmap,Matrix(),Float.NaN,10f,true))
    }
}
