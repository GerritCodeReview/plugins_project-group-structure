load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)

gerrit_plugin(
    name = "project-group-structure",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: project-group-structure",
        "Gerrit-Module: com.ericsson.gerrit.plugins.projectgroupstructure.Module",
        "Implementation-Title: project-group-structure plugin",
        "Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/project-group-structure",
        "Implementation-Vendor: Ericsson",
    ],
    resources = glob(["src/main/resources/**/*"]),
)

gerrit_plugin_tests(
    name = "project-group-structure_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["project-group-structure"],
    deps = [":project-group-structure__plugin"],
)
