##!/usr/bin/python

"""
BYOD example topology

"""

from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topolib import TreeTopo
from mininet.cli import CLI
from mininet.log import setLogLevel, info
import time

def sbyodTestingNetwork():


    "Create an empty network with remote controller"
    info('*** Creating Mininet object\n')
    net = Mininet( controller=RemoteController, autoSetMacs=True )

    info( '*** Adding controller\n' )
    net.addController( 'c0', controller=RemoteController, ip="172.16.37.11", port=6633 )

    info( '*** Adding hosts\n' )
    h1 = net.addHost( 'h1' )
    h2 = net.addHost( 'h2' )
    portalHost = net.addHost( 'h3', ip='10.1.0.2' )
    #gatewayHost = net.addHost( 'h4' ip='10.1.0.1')
    HostList = (h1,h2,portalHost,gatewayHost)

    info( '*** Adding switches\n' )
    s1 = net.addSwitch( 's1' )
    s2 = net.addSwitch( 's2' )
    s3 = net.addSwitch( 's3' )
    SwitchList = (s1,s2,s3)

    info( '*** Creating switch to switch links\n' )
    net.addLink('s1', 's2')
    net.addLink('s1', 's3')
    net.addLink('s2', 's3')

    info( '*** Creating host to switch links\n' )
    net.addLink('h1', 's2')
    net.addLink('h2', 's3')
    #portalHost to switch s2
    net.addLink('h3', 's2')
    #gatewayHost to switch s1
    #net.addLink('h4', 's1')


    info( '*** Adding hardware interface to switch s1 (gateway)\n' )
    physicalIntf = Intf( 'eth1', node=s1 )

    info( '*** Starting network\n')
    net.start()

    info( '*** Running DHClient\n' )
    #h1.cmd('dhclient ' + h1.defaultIntf().name)
    h1.cmd('dhclient')
    h2.cmd('dhclient')

    info( '*** Running CLI\n' )
    CLI( net )

    info( '*** Stopping network' )
    net.stop()

if __name__ == '__main__':
    setLogLevel( 'info' )
    sbyodTestingNetwork()
