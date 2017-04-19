include_defs('//bucklets/gerrit_plugin.bucklet')
include_defs('//bucklets/java_sources.bucklet')
include_defs('//bucklets/maven_jar.bucklet')

SOURCES = glob(['src/main/java/**/*.java'])
RESOURCES = glob(['src/main/resources/**/*'])

TEST_DEPS = GERRIT_TESTS + GERRIT_PLUGIN_API + [
  ':project-group-structure__plugin',
  # bazlets include those 3 bouncycastle jars in plugin API so this is temporary
  # until this plugin is built with bazel.
  # see https://gerrit-review.googlesource.com/#/c/102670/ for more info.
  ':bouncycastle_bcprov',
  ':bouncycastle_bcpg',
  ':bouncycastle_bcpkix',
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

BC_VERS = '1.56'

maven_jar(
  name = 'bouncycastle_bcprov',
  id = 'org.bouncycastle:bcprov-jdk15on:' + BC_VERS,
  sha1 = 'a153c6f9744a3e9dd6feab5e210e1c9861362ec7',
)

maven_jar(
  name = 'bouncycastle_bcpg',
  id = 'org.bouncycastle:bcpg-jdk15on:' + BC_VERS,
  sha1 = '9c3f2e7072c8cc1152079b5c25291a9f462631f1',
)

maven_jar(
  name = 'bouncycastle_bcpkix',
  id = 'org.bouncycastle:bcpkix-jdk15on:' + BC_VERS,
  sha1 = '4648af70268b6fdb24674fb1fd7c1fcc73db1231',
)
