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

