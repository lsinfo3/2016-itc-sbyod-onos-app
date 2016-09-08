#!/bin/bash

echo "----------Start Provisioning----------"
# update repository
sudo apt-get update

# install git
echo "----------Installing GIT----------"
sudo apt-get install -y git

# make necessary folders
cd $HOME
mkdir ./Downloads ./Applications
cd ./Downloads

# install karaf and maven
echo "----------Installing KARAF and MAVEN----------"
wget http://archive.apache.org/dist/karaf/3.0.5/apache-karaf-3.0.5.tar.gz
wget http://archive.apache.org/dist/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
tar -zxvf apache-karaf-3.0.5.tar.gz -C ../Applications/
tar -zxvf apache-maven-3.3.9-bin.tar.gz -C ../Applications/

# install Oracle Java 8
echo "----------Installing Java8----------"
sudo sudo apt-get install software-properties-common -y
sudo add-apt-repository ppa:webupd8team/java -y
sudo apt-get update
# silence java installation
echo debconf shared/accepted-oracle-license-v1-1 select true | \
  sudo debconf-set-selections
echo debconf shared/accepted-oracle-license-v1-1 seen true | \
  sudo debconf-set-selections
sudo apt-get install oracle-java8-installer oracle-java8-set-default -y
export JAVA_HOME=/usr/lib/jvm/java-8-oracle

# installing onos 1.5.1
echo "----------Installing ONOS----------"
cd $HOME
# git config http.postBuffer 524288000
git clone https://gerrit.onosproject.org/onos --progress
if [ -d $HOME/onos ]
  then
    cd ./onos
    git checkout 1.5.1
    export ONOS_ROOT=$HOME/onos
      sed 's/export ONOS_WEB_USER=onos/export ONOS_WEB_USER=karaf/' ./tools/dev/bash_profile \
      | sed 's/export ONOS_WEB_PASS=rocks/export ONOS_WEB_PASS=karaf/' > ./bash_profile_bac
    mv ./bash_profile_bac ./tools/dev/bash_profile

    # source the environment variables
    source ./tools/dev/bash_profile
    mvn clean install -DskipTests

    echo "# Load the environment varibles of ONOS" >> /home/vagrant/.profile
    echo "source /home/vagrant/onos/tools/dev/bash_profile" >> /home/vagrant/.profile
    echo "export ONOS_APPS=drivers,openflow,proxyarp,dhcp" >> /home/vagrant/.profile

  else
    echo "ONOS folder not found. ONOS not installed."
fi

echo "----------Installing Sardine-BYOD Application----------------"
cd $HOME
git clone https://github.com/lsinfo3/2016-itc-sbyod-onos-app.git --progress
if [ -d $HOME/2016-itc-sbyod-onos-app ]
  then
    cd $HOME/2016-itc-sbyod-onos-app
    mvn clean install -DskipTests

    mkdir $HOME/Applications/config
    cp $HOME/2016-itc-sbyod-onos-app/examples/sbyodMininet/network-cfg.json $HOME/Applications/config/network-cfg.json
  else
    echo "S-BYOD folder not found. S-BYOD application not installed."
fi

echo "----------Installing Mininet----------------"
cd $HOME
git clone git://github.com/mininet/mininet
cd ./mininet
git checkout -b 2.2.1 2.2.1
cd ..
./mininet/util/install.sh -a
cp $HOME/2016-itc-sbyod-onos-app/examples/sbyodMininet/mininetSBYOD.py $HOME/
cp $HOME/2016-itc-sbyod-onos-app/examples/sbyodMininet/pythonRedirect.py $HOME/


echo "----------Startup Instructions----------------"

if [ -f $HOME/2016-itc-sbyod-onos-app/target/sbyod-1.0-SNAPSHOT.oar ]
  then
    echo "S-BYOD application created successful."
    echo "Check the 'network-cfg.json' file inside the $HOME/Applications/config/ folder before starting ONOS."
    echo "1. Start ONOS by executing 'onos-karaf clean'"
    echo "2. Start the Mininet topology by executing the 'mininetSBYOD.py' python script."
    echo "3. Install the S-BYOD application by executing 'onos-app <ONOS-IP> install! $HOME/2016-itc-sbyod-onos-app/target/sbyod-1.0-SNAPSHOT.oar'"
fi

echo "----------End Provisioning--------------------"
