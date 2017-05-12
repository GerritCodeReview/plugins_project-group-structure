The only configuration required is to grant "Create-group" and "Create-project"
global permissions to "Registered Users" group.

Delegating group
----------------
If it is requested to create projects under a parent project by non-owner users,
we can specify a delegating
[group reference](@URL@Documentation/dev-plugins.html#configuring-groups)
in _project.config_ of the parent project.

```
[plugin "project-group-structure"]
	delegateProjectCreationTo = Group[group_name / group_uuid]
```

The UUID of a group can be found on "General" tab of the group's page.

If creating-project is delegated to built-in groups, e.g. "Registered Users"
group, then the value is as following:

```
[plugin "project-group-structure"]
	delegateProjectCreationTo = Group[Registered Users / global:Registered-Users]
```