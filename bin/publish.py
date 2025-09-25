#!/usr/bin/python3

import shutil
import os
import tempfile
from pathlib import Path
import sys

os.chdir(Path(__file__).parent.parent)

SITE_FOLDER = "site"


def clean_gen():
    shutil.rmtree("target", ignore_errors=True)
    shutil.rmtree(SITE_FOLDER, ignore_errors=True)


def build_generator():
    if os.path.exists("target"):
        return
    os.system("mvn dependency:resolve -U")
    os.system("mvn clean package assembly:single")


def build_site():
    build_generator()
    os.system(f"java -jar target/jfrevents-site-generator-full.jar {SITE_FOLDER}")


def create_remote_pr():
    if not os.path.exists(SITE_FOLDER):
        build_site()
    with tempfile.TemporaryDirectory() as tmp:
        os.system(f"""
        cd {tmp}
        echo "Cloning repo..."
        gh repo clone https://github.tools.sap/SapMachine/SapMachineIOPage repo -- --branch jfrevents
        cd repo
        echo "Removing old jfrevents..."
        rm -fr jfrevents
        echo "Copying new jfrevents..."
        cp -r {os.path.abspath(SITE_FOLDER)} jfrevents
        echo "Creating new commit..."
        git add jfrevents
        git commit -m "Update jfrevents"
        echo "Pushing changes..."
        git push origin jfrevents
        echo "Creating PR..."
        gh pr create --title "Update jfrevents" --body "Automated update of jfrevents site content."
        """)


def cli():
    commands = {
        "clean_gen": clean_gen,
        "build_generator": build_generator,
        "build_site": build_site,
        "create_remote_pr": create_remote_pr,
        "all": lambda: [clean_gen(), build_site(), create_remote_pr()]
    }
    if len(sys.argv) == 1:
        print("Please provide a command")
        print("Available commands: " + ", ".join(commands.keys()))
        return
    for arg in sys.argv[1:]:
        commands[arg]()


if __name__ == '__main__':
    cli()
