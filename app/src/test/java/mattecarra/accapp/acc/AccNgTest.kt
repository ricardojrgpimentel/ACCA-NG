package mattecarra.accapp.acc

import org.junit.Assert.assertEquals
import org.junit.Test

class AccNgTest {
    @Test fun onlyOwnBranchAndReleaseTagsCanBeInstalled() {
        assertEquals("https://github.com/ricardojrgpimentel/ACC-NG/archive/main.tar.gz", AccNg.archiveUrl("main"))
        assertEquals("https://github.com/ricardojrgpimentel/ACC-NG/archive/v1.0.0-ng.tar.gz", AccNg.archiveUrl("v1.0.0-ng"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun originalTagsAreNotOfferedAsNgReleases() { AccNg.archiveUrl("v2023.10.16") }

    @Test(expected = IllegalArgumentException::class)
    fun inheritedUpstreamBranchIsNotAnNgVersion() { AccNg.archiveUrl("dev") }

    @Test(expected = IllegalArgumentException::class)
    fun archiveReferenceCannotChangeRepository() { AccNg.archiveUrl("../../VR-25/acc") }
}
