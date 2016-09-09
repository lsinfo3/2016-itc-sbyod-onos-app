#!/bin/bash

sleep 5s
consul agent -dev -config-dir . -advertise 10.1.0.3 -ui -client 0.0.0.0
