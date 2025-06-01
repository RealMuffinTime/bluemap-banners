import os

version = ""
modrinthFile = open("modrinth-changelog.md", "w")
githubFile = open("github-changelog.md", "w")
envFile = open(os.getenv('GITHUB_ENV'), "a")

with open("CHANGELOG.md", "r") as inFile:
    for line in inFile.readlines():
        if line.startswith("\n"):
            break
        elif line.startswith("## "):
            version = line.split("[")[1].split("]")[0]
            envFile.write(f"\nNAME=Version {version} - {line.split(' ')[-1].strip()}")
            envFile.write(f"\nVERSION={version.strip('v')}")
        elif line.startswith("### "):
            modrinthFile.write(line)
            githubFile.write(line)
        else:
            modrinthFile.write(line)
            githubFile.write(line)

with open("gradle.properties", "r") as inFile:
    for line in inFile.readlines():
        if line.startswith("minecraft_version="):
            githubFile.write(f"\nFor Minecraft Version `{line[18:].strip()}`.")
            envFile.write(f"\nMINECRAFT_VERSION={line[18:].strip()}")