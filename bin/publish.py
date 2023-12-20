#!/usr/bin/python3

"""
This script publishes the created sites to sapmachine.io

It assumes access to the sapmachine.io repository via ssh.
"""
import shutil
import os
from pathlib import Path
import sys

os.chdir(Path(__file__).parent.parent)

SAPMACHINE_FOLDER = "/tmp/sapmachine-gh-pages-clone"
os.makedirs(SAPMACHINE_FOLDER, exist_ok=True)
SITE_FOLDER = "site"


def clean():
    shutil.rmtree(SAPMACHINE_FOLDER)
    clean_gen()


def clean_gen():
    shutil.rmtree("target", ignore_errors=True)
    shutil.rmtree(SITE_FOLDER, ignore_errors=True)


def clone():
    if not os.path.exists(SAPMACHINE_FOLDER):
        os.system(f"git clone git@github.com:SAP/SapMachine.git --branch gh-pages --single-branch "
                  f"--depth 1 {SAPMACHINE_FOLDER}")
    else:
        os.system(f"cd {SAPMACHINE_FOLDER}; git pull --rebase")


def build_generator():
    if os.path.exists("target"):
        return
    os.system("mvn dependency:resolve -U")
    os.system("mvn clean package assembly:single")


def build_site():
    build_generator()
    os.system(f"java -jar target/jfrevents-site-generator-full.jar {SITE_FOLDER}")


def copy_site():
    clone()
    if not os.path.exists(SITE_FOLDER):
        build_site()
    site = Path(SITE_FOLDER)
    dest = Path(SAPMACHINE_FOLDER) / "jfrevents"
    shutil.rmtree(dest, ignore_errors=True)
    shutil.copytree(site, dest, dirs_exist_ok=True)


def publish():
    copy_site()
    os.system(f"""
    cd {SAPMACHINE_FOLDER}
    git config user.name "JFR Events Bot"
    git config user.email "Johannes Bechberger <johannes.bechberger@sap.com>"
    git add jfrevents
    git commit -m "Update site"
    git pull --rebase
    git push origin gh-pages
    """)


def cli():
    commands = {
        "clean": clean,
        "clean_gen": clean_gen,
        "clone": clone,
        "build_generator": build_generator,
        "build_site": build_site,
        "copy_site": copy_site,
        "publish": publish,
        "all": lambda: [clean(), clone(), build_site(), copy_site(), publish()]
    }
    if len(sys.argv) == 1:
        print("Please provide a command")
        print("Available commands: " + ", ".join(commands.keys()))
        return
    for arg in sys.argv[1:]:
        commands[arg]()


if __name__ == '__main__':
    cli()
