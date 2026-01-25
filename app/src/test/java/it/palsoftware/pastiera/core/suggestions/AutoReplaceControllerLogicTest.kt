package it.palsoftware.pastiera.core.suggestions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutoReplaceControllerLogicTest {

    @Test
    fun testSplitApostropheWord() {
        // Italienische Elisionen
        // "l'amico" -> prefix="l'", root="amico"
        val split1 = AutoReplaceController.splitApostropheWord("l'amico")
        assertNotNull("l'amico sollte gesplittet werden", split1)
        assertEquals("l'", split1!!.prefix)
        assertEquals("amico", split1.root)

        // Zu kurz (root < 3)
        val split2 = AutoReplaceController.splitApostropheWord("l'a")
        assertNull("Root zu kurz, sollte null sein", split2)

        // Kein Apostroph
        val split3 = AutoReplaceController.splitApostropheWord("hallo")
        assertNull(split3)
        
        // Typographischer Apostroph
        val split4 = AutoReplaceController.splitApostropheWord("l’amico")
        assertNotNull("l’amico sollte gesplittet werden", split4)
        assertEquals("l'", split4!!.prefix)
    }

    @Test
    fun testIsAccentOnlyVariant() {
        // "perche" vs "perché" -> true
        val res1 = AutoReplaceController.isAccentOnlyVariant("perche", "perché")
        assertEquals(true, res1)

        // "hallo" vs "halle" -> false (anderer Buchstabe)
        val res2 = AutoReplaceController.isAccentOnlyVariant("hallo", "halle")
        assertEquals(false, res2)
        
        // Identisch -> false (laut Implementierung)
        val res3 = AutoReplaceController.isAccentOnlyVariant("hallo", "hallo")
        assertEquals(false, res3)
    }

    @Test
    fun testStripAccents() {
        assertEquals("perche", AutoReplaceController.stripAccents("perché"))
        assertEquals("a", AutoReplaceController.stripAccents("á"))
        assertEquals("hallo", AutoReplaceController.stripAccents("hallo"))
    }
}
