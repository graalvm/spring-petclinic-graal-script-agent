# Agent instructions

* Read `README.md` for this project's Script Agent demo behavior and run instructions.
* Read `README_ORIG.md` for the original Spring PetClinic project instructions and baseline project behavior. Some information there may not apply to this demo, especially persistent database setup instructions.

## General instructions

* Run the `spring-javaformat` goal after changes to Java files to make sure the Java formatting is correct. Without it, the application won't run using the standard `./mvnw spring-boot:run`.
* All files created by an agent in this project should be added to git automatically as part of the task, without asking for approval first, except when explicitly stated otherwise. The same rule applies for renames and moves. Files that match `.gitignore` should not be added.

## Updating from upstream

* Treat local `fork` as the mirror of `upstream/main` and keep project-specific work on local `main`.
* Before rebasing `main`, fetch both remotes and inspect the graph/divergence: `git fetch upstream`, `git fetch origin`, `git log --oneline --graph --decorate --max-count=25 --all`, `git rev-list --left-right --count fork...upstream/main`, `git rev-list --left-right --count main...upstream/main`.
* Before rewriting `main`, create a safety backup branch such as `git branch backup/main-before-upstream-sync-YYYYMMDD main`.
* Update local `fork` to the latest upstream head with `git branch -f fork upstream/main`.
* Rebase local `main` onto `fork` with `git rebase fork` and resolve conflicts carefully without dropping project-specific coverage or newer upstream changes.
* If `git rebase --continue` tries to open an editor in a non-interactive shell, continue with `GIT_EDITOR=true git rebase --continue`.
* After the rebase, run `./mvnw -q spring-javaformat:apply` and `./mvnw test`.
* Do not force-push automatically unless the user explicitly asks for it. If asked, use `git push --force-with-lease origin HEAD:main`.
* Updating `origin/fork` is optional and should be done only if the user explicitly wants the remote mirror branch updated too.
