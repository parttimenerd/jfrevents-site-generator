#!/usr/bin/python3

import shutil
import os
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



def cli():
    commands = {
        "clean_gen": clean_gen,
        "build_generator": build_generator,
        "build_site": build_site,
        "all": lambda: [build_site()]
    }
    if len(sys.argv) == 1:
        print("Please provide a command")
        print("Available commands: " + ", ".join(commands.keys()))
        return
    for arg in sys.argv[1:]:
        commands[arg]()


if __name__ == '__main__':
    cli()
