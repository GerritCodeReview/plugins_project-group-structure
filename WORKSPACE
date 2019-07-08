workspace(name = "project_group_structure")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "f555b5f11dbbb2d74b2a0bfb32d4f0b0b5dbca14",
    #local_path = "/home/<user>/projects/bazlets",
)

# Snapshot Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
    "gerrit_api_maven_local",
)

# Load snapshot Plugin API
gerrit_api_maven_local()

# Release Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
#    "gerrit_api",
#)

# Load release Plugin API
#gerrit_api()
