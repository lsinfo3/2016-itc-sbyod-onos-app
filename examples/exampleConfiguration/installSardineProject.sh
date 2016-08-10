#!/bin/bash

# Script installing three virtual machines running the Sardine BYOD project.

echo "--------------------Installing Git Virtualbox and Vagrant--------------------"
# Installing git
sudo apt-get update
sudo apt-get install git -y

# Installing vagrant
sudo apt-get install virtualbox vagrant -y


echo "--------------------S-BYOD ONOS App--------------------"
mkdir onos
cd ./onos
# Cloning s-byod application git repo
git clone https://github.com/lsinfo3/2016-itc-sbyod-onos-app.git --progress
# Getting the configuration files
mv 2016-itc-sbyod-onos-app/examples/exampleConfiguration/* .
vagrant up
rm -r ./2016-itc-sbyod-app
cd ..

echo "--------------------S-BYOD Portal--------------------"
mkdir portal
cd ./portal
# Cloning s-byod portal git repo
git clone https://github.com/lsinfo3/2016-itc-sbyod-portal.git --progress
# Getting the configuration files
mv ./2016-itc-sbyod-portal/install.sh .
# vagrant up
rm -r 2016-itc-sbyod-app
cd ..

echo "--------------------Consul--------------------"
mkdir consul
cd ./consul
cd ..
