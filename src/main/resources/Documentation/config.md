# Config
The only configuration required is to grant `Create-group` and `Create-project`
global permissions to `Registered Users` group.

## Default Access Rights

This plugin can set default access rights for newly created root projects if configured.

Default access rights are read from `<review_site>/data/@PLUGIN@/project.config`.
The format of that file is the same as regular project.config except that group, in addition to
be a group name, can be set to token `${owner}` instead which will be replaced by the group owning
the project.

Example of default access rights config file:

```
[access "refs/*"]
  read = group ${owner}
  read = group existing group
[access "refs/heads/*"]
  create = group ${owner}
  push = group ${owner}
  label-Code-Review = -2..+2 group ${owner}
  submit = group ${owner}

```
An optional configuration can be defined in the @PLUGIN@.config to enforce a naming rule
to follow a defined regex.

Example of @PLUGIN@.config file:

```
[project-group-structure]
  regex = [a-zA-Z_0-9*-/]*

```

In this example, the regex will limit the project created to match alpha-numeric characters
and all the mathematical signs. The Regex must accept slash (/) to not disturb the
functionality of the plugin. If the regex doesn't accept /, it will be neglected.
Also, if the regex allows spaces, it will be neglected.
