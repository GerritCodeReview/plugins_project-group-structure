workspace(name = "project_group_structure")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "59fdc31ca3111af970637105d1c7bd7dbbdd29e1",
    #    local_path = "/home/<user>/projects/bazlets",
)

#Snapshot Plugin API
#load(
#    "@com_googlesource_gerrit_bazlets//:gerrit_api_maven_local.bzl",
#    "gerrit_api_maven_local",
#)

# Load snapshot Plugin API
#gerrit_api_maven_local()

# Release Plugin API
load(
    "@com_googlesource_gerrit_bazlets//:gerrit_api.bzl",
    "gerrit_api",
)

# Load release Plugin API
gerrit_api()
