package com.chaman.downloader

import com.chaman.link.LinkCore
import com.chaman.link.ParamSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCoreTest {
    private val spec = mapOf(
        "url" to ParamSpec("string", required = true),
        "n" to ParamSpec("number"),
        "flag" to ParamSpec("boolean"),
        "mode" to ParamSpec("enum", values = listOf("a", "b"))
    )

    @Test
    fun validParams() {
        val p = mapOf<String, Any?>("url" to "https://x.com/v", "n" to 3, "flag" to true, "mode" to "a")
        assertNull(LinkCore.validateParams(spec) { p[it] })
    }

    @Test
    fun onlyRequiredIsEnough() {
        val p = mapOf<String, Any?>("url" to "https://x.com/v")
        assertNull(LinkCore.validateParams(spec) { p[it] })
    }

    @Test
    fun missingRequired() {
        val p = mapOf<String, Any?>("n" to 1)
        assertEquals("missing_param: url", LinkCore.validateParams(spec) { p[it] })
    }

    @Test
    fun wrongTypes() {
        val a = mapOf<String, Any?>("url" to 5)
        assertTrue(LinkCore.validateParams(spec) { a[it] }!!.startsWith("bad_param: url"))
        val b = mapOf<String, Any?>("url" to "u", "mode" to "zzz")
        assertTrue(LinkCore.validateParams(spec) { b[it] }!!.startsWith("bad_param: mode"))
        val c = mapOf<String, Any?>("url" to "u", "flag" to "yes")
        assertTrue(LinkCore.validateParams(spec) { c[it] }!!.startsWith("bad_param: flag"))
    }
}
