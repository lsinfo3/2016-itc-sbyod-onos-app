## Examples
The examples inside this folder demonstrate the potential of the S-BYOD application.

### ITC 2016 Mininet Topology
The **itc-2016-mininet-topology** folder contains a [Vagrant] topology similar to the Setup of the ITC 2016.
Setup the topology by executing `$ vagrant up`.
In contrast to the **exampleConfiguration** folder the network is not realized with hardware
switches but in software by using a [Mininet] topology.

### ITC 2016 Hardware Setup
The **itc-2016-hardware-setup** folder contains the [Vagrant] topology of the Setup shown at the ITC 2016.
By executing the **installSardineProject.sh** Python file, three folders are created.
One for the S-BYOD ONOS application, one for the S-BYOD Portal and one for Consul.
Each folder contains a Vagrant file starting a virtual machine with the pre-configured system.

### Mininet Topologies
Insided the **mininetTopos** folder two different [Mininet] topologies can be found.

The **mnTree.py** Python file builds a topology with one switch connected to
three nodes.

The **mnTreeD2F2.py** Python file builds a topology of three switches, connected to four nodes.
One switch is only connected to the two other switches, which are each connected to two nodes.

[Mininet]: <http://mininet.org/>
[Vagrant]: <https://www.vagrantup.com/>