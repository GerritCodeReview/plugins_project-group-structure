workspace(name = "project_group_structure")

load("//:bazlets.bzl", "load_bazlets")

load_bazlets(
    commit = "d9abef15db0f934bfe9adcb40b0c475807fd12bf",
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
