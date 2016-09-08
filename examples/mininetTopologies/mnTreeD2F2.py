#!/usr/bin/python

# Simple mininet topo for testing ONOS functionality
# One controller with three nodes

from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topolib import TreeTopo
from mininet.cli import CLI
from mininet.log import setLogLevel, info

if __name__=='__main__':
  setLogLevel('info')
  #info('*** Creating tree topo with depth=2 and fanout=2\n')
  treeTopo = TreeTopo(depth=2,fanout=2)

  #info('*** Creating network\n')
  #net = Mininet(topo=treeTopo, autoSetMacs=True, build=False, listenPort=6633)

  #info('*** Adding remote controller\n')
  #net.addController(name='onos', controller=RemoteController, ip='172.0.0.1')
  net = Mininet(topo=treeTopo, controller=lambda name: RemoteController(name, defaulartarIP='127.0.0.1'), autoSetMacs=True, listenPort=6633)
  #net.build()
  net.start()
  #for h in net.hosts:
   # h.cmd('dhclient')
  CLI(net)
  net.stop()
