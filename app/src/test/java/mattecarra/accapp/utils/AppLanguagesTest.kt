package mattecarra.accapp.utils

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguagesTest {
    @Test fun systemSelectionIsEmptyEvenAfterChangingTheProcessLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRENCH)
            assertEquals("", AppLanguages.normalizeTag("def"))
            assertEquals("", AppLanguages.normalizeTag(null))
            assertEquals("", AppLanguages.normalizeTag(""))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun legacyPickerCodesResolveToRealLanguages() {
        assertEquals("el", AppLanguages.normalizeTag("gr"))
        assertEquals("id", AppLanguages.normalizeTag("in"))
        assertEquals("el-GR", AppLanguages.normalizeTag("gr_GR"))
        assertEquals("id-ID", AppLanguages.normalizeTag("in-ID"))
    }

    @Test fun regionalTranslationsKeepTheirRegion() {
        assertEquals("pt-BR", AppLanguages.normalizeTag("pt_BR"))
        assertEquals("pt-PT", AppLanguages.normalizeTag("pt-pt"))
        assertEquals("zh-TW", AppLanguages.normalizeTag("zh-TW"))
        assertEquals("en", AppLanguages.normalizeTag(" en "))
    }
}
