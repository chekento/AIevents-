package cloud.kosch.aievents

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCatalogTest {
    @Test fun provider_catalog_stays_large() {
        assertTrue("Provider catalog must contain at least 200 entries", ProviderCatalog.providers.size >= 200)
    }

    @Test fun zuno_is_present() {
        assertNotNull(ProviderCatalog.providers.firstOrNull { it.id == "zuno" || it.name.equals("Zuno", true) })
    }

    @Test fun mlops_and_llmops_are_first_class_categories() {
        assertTrue(ProviderCatalog.providers.any { it.category == ProviderCategory.MLOPS })
        assertTrue(ProviderCatalog.providers.any { it.category == ProviderCategory.LLMOPS })
    }

    @Test fun major_provider_ecosystems_are_present() {
        val ids = ProviderCatalog.providers.map { it.id }.toSet()
        assertTrue("openai" in ids)
        assertTrue("anthropic" in ids)
        assertTrue("google-ai" in ids)
        assertTrue("microsoft-ai" in ids)
        assertTrue("meta-ai" in ids)
        assertTrue("mistral" in ids)
        assertTrue("cohere" in ids)
        assertTrue("deepseek" in ids)
    }
}
