#!/usr/bin/python

# Simple mininet topo for testing ONOS functionality
# One controller with three nodes

from mininet.net import Mininet
from mininet.node import RemoteController
from mininet.topolib import TreeTopo
from mininet.cli import CLI
from mininet.log import setLogLevel

if __name__=='__main__':
  setLogLevel('info')
  treeTopo = TreeTopo(depth=1,fanout=3)
  net = Mininet(topo=treeTopo, controller=lambda name: RemoteController(name, defaultIP='127.0.0.1'), autoSetMacs=True, listenPort=6633)
  net.start()
  CLI(net)
  net.stop()
