import os
import datetime

with open("VERSIONS.md", "r") as inFile:
    modrinthFile = open("modrinth-changelog.md", "w")
    githubFile = open("github-changelog.md", "w")
    for line in inFile.readlines():
        if line.startswith("\n"):
            break
        elif line.startswith("## "):
            pass
        elif line.startswith("### "):
            modrinthFile.write(line)
            githubFile.write(line)
        else:
            modrinthFile.write(line)
            githubFile.write(line)

with open("gradle.properties", "r") as inFile:
    with open(os.getenv('GITHUB_ENV'), "a") as envFile:
        for line in inFile.readlines():
            if line.startswith("minecraft_version="):
                githubFile.write(f"\nFor Minecraft Version `{line[18:].strip()}`.")
                envFile.write(f"MINECRAFT_VERSION={line[18:].strip()}\n")
            elif line.startswith("mod_version = "):
                envFile.write(f"NAME=Version v{line[14:].strip()} - {datetime.date.today()}\n")
                envFile.write(f"VERSION={line[14:].strip()}\n")