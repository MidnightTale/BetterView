import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    alias(libs.plugins.paperweight.userdev)
}

dependencies {
    implementation(projects.paperCommon)
    paperweight.paperDevBundle(libs.versions.paper.v1213)
}

paperweight.reobfArtifactConfiguration = ReobfArtifactConfiguration.MOJANG_PRODUCTION
