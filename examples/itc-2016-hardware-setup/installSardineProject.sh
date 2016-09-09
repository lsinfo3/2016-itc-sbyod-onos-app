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
cp 2016-itc-sbyod-onos-app/examples/itc-2016-hardware-setup/onos-app/* .
vagrant up
# remove old files
rm -r ./2016-itc-sbyod-onos-app
cd ..

echo "--------------------S-BYOD Portal--------------------"
mkdir portal
cd ./portal
# Cloning s-byod portal git repo
git clone https://github.com/lsinfo3/2016-itc-sbyod-portal.git --progress
# Getting the configuration files
cp ./2016-itc-sbyod-portal/Vagrantfile ./2016-itc-sbyod-portal/install.sh .
vagrant up
# remove old files
rm -r 2016-itc-sbyod-app
cd ..

echo "--------------------Consul--------------------"
mkdir consul
cd ./consul
# Cloning s-byod application git repo
git clone https://github.com/lsinfo3/2016-itc-sbyod-onos-app.git --progress
# Getting the Vagrant files
cp 2016-itc-sbyod-onos-app/examples/itc-2016-hardware-setup/consul/* .
vagrant up
# remove old files
rm -r ./2016-itc-sbyod-onos-app
cd ..
