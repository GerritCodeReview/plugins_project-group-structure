include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/java_sources.bucklet')

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

TEST_DEPS = GERRIT_TESTS + GERRIT_PLUGIN_API + [
  ':project-group-structure__plugin',
]

gerrit_plugin(
  name = 'project-group-structure',
  srcs = SOURCES,
  resources = RESOURCES,
  manifest_entries = [
    'Gerrit-PluginName: project-group-structure',
    'Gerrit-ApiType: plugin',
    'Gerrit-Module: com.ericsson.gerrit.plugins.projectgroupstructure.Module',
    'Implementation-Title: project-group-structure plugin',
    'Implementation-URL: https://gerrit-review.googlesource.com/#/admin/projects/plugins/project-group-structure',
    'Implementation-Vendor: Ericsson',
  ],
)

java_test(
  name = 'project-group-structure-tests',
  srcs = glob(['src/test/java/**/*.java']),
  labels = ['project-group-structure'],
  deps = TEST_DEPS,
)

java_sources(
  name = 'project-group-structure-sources',
  srcs = SOURCES + RESOURCES,
)

# this is required for bucklets/tools/eclipse/project.py to work
java_library(
  name = 'classpath',
  deps = TEST_DEPS,
)

