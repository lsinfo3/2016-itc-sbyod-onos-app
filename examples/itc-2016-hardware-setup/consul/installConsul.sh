#!/bin/bash

echo "----------Start Provisioning----------"

echo "----------Updating Repositories----------"
#update and install
sudo apt-get update
# sudo apt-get install -y git
sudo apt-get install -y unzip

echo "----------Adding Default Route----------"
#add default route
sudo route add default gw 172.16.37.1 eth1
sudo route del default gw 10.0.2.2
# add default route at startup
echo "# add default route for eth1" >> /home/vagrant/.profile
echo "sudo route add default gw 172.16.37.1 eth1" >> /home/vagrant/.profile
echo "sudo route del default gw 10.0.2.2" >> /home/vagrant/.profile

echo "----------Getting and Installing Consul----------"
# get the consul code from the website
wget https://releases.hashicorp.com/consul/0.6.4/consul_0.6.4_linux_amd64.zip
# unzip the file
unzip consul_0.6.4_linux_amd64.zip
# move it to somewhere on the path
sudo mv consul /usr/local/bin/
# remove the zip file
rm consul_0.6.4_linux_amd64.zip

echo "----------Creating Consul Config----------"
# create config folder for consul
mkdir /home/vagrant/consul
ln -s /vagrant/runConsul.sh /home/vagrant/consul/runConsul.sh
chmod +x /home/vagrant/consul/runConsul.sh
# move config files to folder
ln -s /vagrant/consul-config.json /home/vagrant/consul/consul-config.json
ln -s /vagrant/services.json /home/vagrant/consul/services.json

echo "----------Adding Autostart of Consul at Startup----------"
# edit rc.local to autostart the consul service
sudo sed -i "s|# By default this script does nothing.|(cd /home/vagrant/consul; sudo sh '/home/vagrant/consul/runConsul.sh')|" /etc/rc.local
