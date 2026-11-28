package com.hisn.muslim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.DeflaterOutputStream

class ApkPatchTest {
    private fun le(v: Long) = ByteArray(8) { i -> ((v ushr (8 * i)) and 0xFF).toByte() }
    private fun off(v: Long): ByteArray {
        val b = le(kotlin.math.abs(v)); if (v < 0) b[7] = (b[7].toInt() or 0x80).toByte(); return b
    }
    private fun z(b: ByteArray): ByteArray { val o = ByteArrayOutputStream(); DeflaterOutputStream(o).use { it.write(b) }; return o.toByteArray() }

    /** old = 0..199؛ new = أول ١٠٠ بايت من old بعد +1، ثم ٥ بايت جديدة، ثم ٥٠ بايت من old بداية من ١٥٠ */
    @Test fun appliesHandBuiltPatch() {
        val old = ByteArray(200) { it.toByte() }
        val ctrl = ByteArrayOutputStream().apply {
            write(off(100)); write(off(5)); write(off(50))   // add 100 من old[0..], extra 5, ثم oldPos += 50 => ١٥٠
            write(off(50)); write(off(0)); write(off(-150))  // add 50 من old[150..] ، oldPos -= 150
        }.toByteArray()
        val diff = ByteArray(150) { if (it < 100) 1 else 0 }
        val extra = byteArrayOf(9, 8, 7, 6, 5)
        val expected = ByteArray(155)
        for (i in 0 until 100) expected[i] = (i + 1).toByte()
        for (i in 0 until 5) expected[100 + i] = extra[i]
        for (i in 0 until 50) expected[105 + i] = (150 + i).toByte()
        val cz = z(ctrl); val dz = z(diff); val ez = z(extra)
        val patch = ByteArrayOutputStream().apply {
            write("HSNPT001".toByteArray()); write(le(155)); write(le(cz.size.toLong())); write(le(dz.size.toLong())); write(le(ez.size.toLong()))
            write(cz); write(dz); write(ez)
        }.toByteArray()
        val dir = createTempDir()
        val of = File(dir, "old"); of.writeBytes(old)
        val pf = File(dir, "p"); pf.writeBytes(patch)
        val out = File(dir, "new")
        ApkPatch.apply(of, pf, out)
        assertArrayEquals(expected, out.readBytes())
        assertEquals(64, ApkPatch.sha256(out).length)
        dir.deleteRecursively()
    }
}
