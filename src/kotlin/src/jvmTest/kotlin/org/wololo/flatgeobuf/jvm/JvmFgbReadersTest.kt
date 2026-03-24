package org.wololo.flatgeobuf.jvm

import org.wololo.flatgeobuf.FgbReader
import org.wololo.flatgeobuf.FlatGeobuf
import org.wololo.flatgeobuf.PropertyValue
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmFgbReadersTest {
    @Test
    fun opensReaderFromPath() {
        val reader = FgbReader.open(countriesPath())

        val names = reader.selectAll()
            .mapNotNull { (it.properties["name"] as? PropertyValue.StringValue)?.value }
            .take(3)
            .toList()

        assertEquals(3, names.size)
    }

    @Test
    fun readsHeaderFromInputStream() {
        countriesPath().inputStream().use { input ->
            val header = FlatGeobuf.readHeader(input)
            assertEquals(179L, header.featuresCount)
            assertTrue(header.hasIndex)
        }
    }

    @Test
    fun readsFeaturesSequentiallyFromInputStream() {
        SequentialFgbReader.open(countriesPath()).use { reader ->
            val features = reader.selectAll().toList()
            assertEquals(179, features.size)
            val firstName = (features.first().properties["name"] as PropertyValue.StringValue).value
            assertTrue(firstName.isNotBlank())
        }
    }

    private fun countriesPath(): Path = Path.of("..", "..", "test", "data", "countries.fgb").normalize()
}
