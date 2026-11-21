package groma.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RubanCentreTest {
    @Test fun centrePrevuEstRouge() = assertTrue(RubanCentre.alerte(false, null))
    @Test fun centreTelephoneEstRouge() = assertTrue(RubanCentre.alerte(true, "telephone"))
    @Test fun centreRtkEstNormal() = assertFalse(RubanCentre.alerte(true, "rtk"))
}
