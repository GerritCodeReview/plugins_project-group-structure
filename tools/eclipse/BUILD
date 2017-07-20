load("//tools/bzl:classpath.bzl", "classpath_collector")

java_library(
    name = "classpath",
    testonly = 1,
    runtime_deps = [
        "//:project-group-structure__plugin_test_deps",
    ],
)

classpath_collector(
    name = "main_classpath_collect",
    testonly = 1,
    deps = [
        "//:project-group-structure__plugin_test_deps",
    ],
)
