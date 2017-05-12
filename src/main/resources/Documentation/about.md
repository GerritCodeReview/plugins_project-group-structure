This plugin enforce a project group structure and restrict project creation
within this structure to project group owners only.

To start creating a project group structure, simply create a root project, i.e.
a project which inherits rights from `All-Projects`. The root project name
cannot contains slashes, e.g. `some-organization` and must be created with
option `Only serve as parent for other projects`.

Ownership of the root project is given automatically to the user who created it
by adding him to a group named `<root-project-name>-admins`, e.g.
`some-organization-admins` which is granted owner right on `refs/*` references.

From this point on, only the root project owners can create projects within that
structure. They can do it by creating projects that inherits rights from their
root project and the project names must start with root project name, e.g.
`some-organization/some-project`.

Delegating group
----------------
Project creation can also be delegated to non-owner users by configuring
`delegateProjectCreationTo` in the `project.config` of
`refs/meta/config` branch of the parent project.

The value of `delegateProjectCreationTo` must be set to a
[group reference](@URL@Documentation/dev-plugins.html#configuring-groups).

`project.config` file
```
[plugin "@PLUGIN@"]
delegateProjectCreationTo = group group_name
```

`groups` file
```
group_uuid		group_name
```

The UUID of a group can be found on the "General" tab of the group's page.

If creating-project is delegated to built-in groups, e.g. "Registered Users"
group, then the value is as following:

`project.config` file
```
[plugin "@PLUGIN@"]
delegateProjectCreationTo = group Registered Users
```

`groups` file
```
global:Registered-Users		Registered Users
```

A way to edit `project.config` and `groups` file is from Gerrit UI.
For example, to delegate project creation under `orgA` root project to
`orgA-project-creators` group:

- From main menu, click `People` -> `List Groups`
- Type `orgA-project-creators` as the filter then click on
`orgA-project-creators` group
- Copy the group UUID (example: 3d2bef3b667a577f2dd5232e0848c526efd11b1f)
- From main menu, click `Projects` -> `List`
- Type `orgA` as the filter then click on `orgA` project
- Click `Edit Config` button
- Add the following then click `Save` -> `Close`:
	```
	[plugin "@PLUGIN@"]
	delegateProjectCreationTo = orgA-project-creators
	```
- Click `Add...` button then type and open `groups` file
- Add the following then click `Save` -> `Close`:
	```
	3d2bef3b667a577f2dd5232e0848c526efd11b1f	orgA-project-creators
	```
- Click `Publish` button, review, vote and submit the change to apply new
configuration
