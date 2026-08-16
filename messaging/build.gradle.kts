plugins {
    alias(local.plugins.siloverse.library.release)
    // Applied by the modules, declared here apply-false: the release plugin
    // above already puts the whole conventions jar on the children's classpath,
    // and a child's versioned plugin request can only be satisfied if the
    // version is pinned somewhere on the parent chain.
    alias(local.plugins.siloverse.jvm.library) apply false
    alias(local.plugins.siloverse.platform) apply false
}

// Single source of the library version. Only the plugin's release task rewrites
// this line: a bare x.y.z exists exactly on the release commit it tags; every
// other commit carries the next -SNAPSHOT. The release machinery that lived
// here was extracted to siloverse-build (io.github.siloverse.library-release),
// which also spreads group/version to the modules.
group = "io.github.siloverse"
version = "1.0.6"
