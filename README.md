# S-BYOD ONOS Application
This Application creates personalized virtual networks for each device of a user.
They are extended on demand, which is done once the user authenticates, activates
access to additional applications, or as soon as applications scale out.
The virtual networks are adapted while the user is roaming with his device through the network.

The authentication can be realized with the [S-BYOD Portal] providing the code for
the Web-Portal, where BYOD services are enabled and disabled.

The [Consul] service discovery application is supported by the S-BYOD application,
allowing the integration of discovered network services into the available services
of the BYOD network.

#### Note
The S-BYOD Application is an extension of the [ONOS] project.
Therefore a running ONOS instances is a pre-condition to start this application.
The installation and startup is explained in a step-by-step tutorial found at
the project specific [wiki](https://wiki.onosproject.org/display/ONOS/Installing+and+Running+ONOS) .

### Installation (Linux)

The process of installing and running the S-BYOD application is explained in the following,
assuming a freshly installed ONOS version 1.5.1 available on the system.

#### ONOS Controller
Before starting ONOS via
```sh
$ ok clean
```
the environment variables specified by the
**bash_profile** script during installation are modified by entering
```sh
$ export ONOS APPS=drivers,openflow,proxyarp,mobility
```
This defines the applications ONOS is starting with and ensures that no other 
forwarding application like **org.onosproject.fwd** is running. A quick view into the ONOS
log with
```sh
onos> log:tail
```
to check a proper start-up can be very useful. All network
devices like switches have to be connected now, as the S-BYOD application only
supports the discovery of hosts but switches not yet.

#### Captive Portal
The [S-BYOD Portal] is installed according to the installation guide found at the corresponding
git repository. According to this, one has to install Meteor via
```sh
$ curl https://install.meteor.com/ | sh
```
clone the repository with
```sh
$ git clone https://github.com/lsinfo3/2016-itc-sbyod-portal.git
```
create a configuration file and start the portal by entering
```sh
$ meteor --settings settings.json
```
The portal page can be accessed in the browser at **https://{portal-ip}:3000** , where
the **{portal-ip}** is the IP address belonging to the Meteor application.

#### S-BYOD Application
By cloning this Git repository one obtains the S-BYOD application for ONOS. The
```sh
$ mvn clean install
```
command in the application’s source folder creates an **ONOS Application aRchive
(OAR)** file. To launch the compiled S-BYOD app, the onos-app shell tool
```sh
$ onos-app {onos-ip} install! onos-byod.oar
```
is used, where the **{onos-ip}** is the IP address the ONOS instance is accessible on.
Finally the Meteor portal address has to be configured in the ONOS REST user
interface, which can be accessed at **https://{onos-ip}:8181/onos/v1/docs/#!/portal**.
The standard user name and password for the ONOS web-ui is ”karaf”. Now the
S-BYOD application is running and should redirect every request to the Meteor
portal, where further connections can be established.


[ONOS]: <http://onosproject.org/>
[S-BYOD Portal]: <https://github.com/lsinfo3/2016-itc-sbyod-portal>
[Consul]: <https://www.consul.io/>