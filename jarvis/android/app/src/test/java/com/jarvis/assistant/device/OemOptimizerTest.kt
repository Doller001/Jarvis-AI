package com.jarvis.assistant.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OemOptimizerTest {

    @Test
    fun `oem detection returns a valid brand enum without crashing on null build values`() {
        val oem = OemOptimizer.detectOem()
        assertNotNull(oem)
        assertTrue(oem in OemBrand.values())
    }

    @Test
    fun `detects Xiaomi and Redmi devices accurately`() {
        assertEquals(OemBrand.XIAOMI_HYPEROS, OemOptimizer.detectOem(overrideManufacturer = "Xiaomi", overrideBrand = "POCO"))
        assertEquals(OemBrand.XIAOMI_HYPEROS, OemOptimizer.detectOem(overrideManufacturer = "Redmi", overrideBrand = "Redmi"))
    }

    @Test
    fun `detects Samsung devices accurately`() {
        assertEquals(OemBrand.SAMSUNG_ONEUI, OemOptimizer.detectOem(overrideManufacturer = "samsung", overrideBrand = "samsung"))
    }

    @Test
    fun `detects Oppo and Realme devices accurately`() {
        assertEquals(OemBrand.OPPO_REALME_COLOROS, OemOptimizer.detectOem(overrideManufacturer = "Realme", overrideBrand = "realme"))
        assertEquals(OemBrand.OPPO_REALME_COLOROS, OemOptimizer.detectOem(overrideManufacturer = "OPPO", overrideBrand = "oppo"))
    }

    @Test
    fun `detects Vivo and iQOO devices accurately`() {
        assertEquals(OemBrand.VIVO_FUNTOUCH, OemOptimizer.detectOem(overrideManufacturer = "Vivo", overrideBrand = "vivo"))
        assertEquals(OemBrand.VIVO_FUNTOUCH, OemOptimizer.detectOem(overrideManufacturer = "iQOO", overrideBrand = "iQOO"))
    }

    @Test
    fun `detects Google Pixel and stock devices accurately`() {
        assertEquals(OemBrand.STOCK_ANDROID, OemOptimizer.detectOem(overrideManufacturer = "Google", overrideBrand = "google"))
    }
}
