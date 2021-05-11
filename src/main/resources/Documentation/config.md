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

Note: default access rights configuration is bypassed for projects created by admins.

Also, this plugin offers a way to restrict the new names of the projects to match an optionally
configured regex in the gerrit.config. For example:

```
[plugin "@PLUGIN@"]
  nameRegex = [a-z0-9/]+

```

In this example, the regex will limit the project created to non-capital letters, numbers
and slashes. The regex must accept slash (/) to not disturb the functionality of the plugin.
If the regex doesn't accept / or accepts spaces, it will be ignored and replaced with a default
non-empty wildcard (.+) regex.
