load("//tools/bzl:junit.bzl", "junit_tests")
load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
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

junit_tests(
    name = "project_group_structure_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    tags = ["project-group-structure"],
    deps = [
        ":project-group-structure__plugin_test_deps",
    ],
)
java_library(
    name = "project-group-structure__plugin_test_deps",
    visibility = ["//visibility:public"],
    testonly = 1,
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":project-group-structure__plugin",
    ],
)
